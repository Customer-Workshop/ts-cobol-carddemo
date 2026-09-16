package com.carddemo.service;

import com.carddemo.data.CobolFieldFormatter;
import com.carddemo.model.Account;
import com.carddemo.queue.Message;
import com.carddemo.queue.MqErrorRecord;
import com.carddemo.queue.MqException;
import com.carddemo.queue.MqProcessingException;
import com.carddemo.queue.MqQueues;
import com.carddemo.queue.InProcessMqService;
import com.carddemo.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * Java port of COACCT01 — the CDRA trigger-started MQ consumer for account inquiries
 * (stream S-22). Drains {@link MqQueues#REQUEST_ACCT}, reads the account by id for
 * requests with function 'INQA' and key &gt; 0, and replies on the program's hardcoded
 * reply queue {@link MqQueues#REPLY_ACCT}; protocol failures land on
 * {@link MqQueues#ERROR}.
 * <p>
 * Preserved quirks (do not "fix"; cites in {@code app/app-vsam-mq/cbl/COACCT01.cbl}):
 * <ul>
 *   <li>The reply queue is hardcoded 'CARD.DEMO.REPLY.ACCT' (:198); the request's
 *       MQMD-REPLYTOQ is captured into SAVE-REPLY2Q but never used (:371). The Java
 *       port likewise ignores {@code request.replyTo()}.</li>
 *   <li>Replies echo the request's MSGID and CORRELID (:469-470) rather than receiving
 *       a fresh message id.</li>
 *   <li>Reply and error payloads are the full X(1000) buffer, space-padded
 *       (:107,:396-397); overlong error text truncates to X(25) (:61).</li>
 *   <li>The invalid-parameters reply has no space between the key and 'FUNCTION : '
 *       (:449-455).</li>
 * </ul>
 */
@Service
public class AcctInquiryConsumer {

    /** The only function code the program honours (COACCT01.cbl:393). */
    public static final String FUNCTION_INQA = "INQA";
    private static final int BUFFER_LENGTH = 1000;      // MQ-BUFFER / REPLY-MESSAGE X(1000)
    private static final DateTimeFormatter ACCT_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final Logger log = LoggerFactory.getLogger(AcctInquiryConsumer.class);

    private final InProcessMqService mq;
    private final AccountRepository accountRepository;

    public AcctInquiryConsumer(InProcessMqService mq, AccountRepository accountRepository) {
        this.mq = mq;
        this.accountRepository = accountRepository;
    }

    /** Opens the program's three queues and registers this consumer (MQOPEN x3 + trigger). */
    public void register() {
        mq.open(MqQueues.ERROR);
        mq.open(MqQueues.REQUEST_ACCT);
        mq.open(MqQueues.REPLY_ACCT);
        mq.registerConsumer(MqQueues.REQUEST_ACCT, this::processRequest);
    }

    /**
     * 4000-PROCESS-REQUEST-REPLY (COACCT01.cbl:390-457): validate function/key, read the
     * account, put the reply. Request layout is WS-FUNC X(4) + WS-KEY 9(11) (:109-112).
     */
    public void processRequest(Message request) {
        String payload = request.payload() == null ? "" : request.payload();
        String func = CobolFieldFormatter.pad(payload.substring(0, Math.min(4, payload.length())), 4);
        String key = payload.length() > 4
                ? CobolFieldFormatter.pad(payload.substring(4, Math.min(15, payload.length())), 11)
                : " ".repeat(11);
        if (FUNCTION_INQA.equals(func) && key.matches("\\d{11}") && Long.parseLong(key) > 0) {
            Account account;
            try {
                account = accountRepository.findById(Long.parseLong(key)).orElse(null);
            } catch (RuntimeException e) {
                // EXEC CICS READ ACCTDAT, WHEN OTHER (:437-445)
                putError(request, "ERROR WHILE READING ACCTFILE", MqQueues.REQUEST_ACCT);
                throw new MqProcessingException("ACCTDAT read failed", e);
            }
            if (account != null) {
                putReply(request, accountResponse(account));
            } else {
                // DFHRESP(NOTFND) (:428-435)
                putReply(request, "INVALID REQUEST PARAMETERS ACCT ID : " + key);
            }
        } else {
            // func != 'INQA' or key <= 0 (:448-456) — no space between key and 'FUNCTION'
            putReply(request, "INVALID REQUEST PARAMETERS ACCT ID : " + key + "FUNCTION : " + func);
        }
    }

    /**
     * The 252-char WS-ACCT-RESPONSE record (:130-169): fixed labels + zero-padded id,
     * zoned-decimal money fields with the sign overpunched into the last digit, and the
     * X(10) text fields verbatim.
     */
    private String accountResponse(Account account) {
        String acctId = String.valueOf(account.getAcctId());
        acctId = acctId.length() >= 11 ? acctId.substring(acctId.length() - 11)
                : "0".repeat(11 - acctId.length()) + acctId;
        return "ACCOUNT ID : " + acctId
                + "ACCOUNT STATUS : " + CobolFieldFormatter.pad(account.getAcctActiveStatus(), 1)
                + "BALANCE : " + CobolFieldFormatter.signedDecimal(account.getAcctCurrBal(), 10, 2)
                + "CREDIT LIMIT : " + CobolFieldFormatter.signedDecimal(account.getAcctCreditLimit(), 10, 2)
                + "CASH LIMIT : " + CobolFieldFormatter.signedDecimal(account.getAcctCashCreditLimit(), 10, 2)
                + "OPEN DATE : " + date10(account.getAcctOpenDate())
                + "EXPR DATE : " + date10(account.getAcctExpirationDate())
                + "REIS DATE : " + date10(account.getAcctReissueDate())
                + "CREDIT BAL : " + CobolFieldFormatter.signedDecimal(account.getAcctCurrCycCredit(), 10, 2)
                + "DEBIT BAL : " + CobolFieldFormatter.signedDecimal(account.getAcctCurrCycDebit(), 10, 2)
                + "GROUP ID : " + CobolFieldFormatter.pad(account.getAcctGroupId(), 10);
    }

    private static String date10(java.time.LocalDate date) {
        return date == null ? " ".repeat(10) : CobolFieldFormatter.pad(ACCT_DATE.format(date), 10);
    }

    /**
     * 4100-PUT-REPLY (:462-498): MQPUT on the hardcoded reply queue with the request's
     * msgId/correlId echoed and format MQFMT-STRING; the full padded buffer is the payload.
     */
    private void putReply(Message request, String replyText) {
        Message reply = new Message(request.msgId(), request.correlId(), null,
                Message.FORMAT_STRING, CobolFieldFormatter.pad(replyText, BUFFER_LENGTH));
        try {
            mq.put(MqQueues.REPLY_ACCT, reply);
        } catch (MqException e) {
            // (:403-408): the queue slot holds REPLY-QUEUE-NAME, not the input queue
            putError(request, "MQPUT ERR", MqQueues.REPLY_ACCT);
            throw new MqProcessingException("Reply MQPUT failed on " + MqQueues.REPLY_ACCT, e);
        }
    }

    /**
     * 9000-ERROR (:501-536): put the MQ-ERR-DISPLAY block on CARD.DEMO.ERROR; when that
     * put itself fails the COBOL only DISPLAYs it — demoted to a log line.
     */
    private void putError(Message request, String returnMessage, String failingQueue) {
        String block = MqErrorRecord.build("", returnMessage, 2, 0, failingQueue);
        try {
            mq.put(MqQueues.ERROR, new Message(request.msgId(), request.correlId(), null,
                    Message.FORMAT_STRING, CobolFieldFormatter.pad(block, BUFFER_LENGTH)));
        } catch (MqException e) {
            log.error("MQ-ERR-DISPLAY (error-queue put failed): {}", block);
        }
    }
}
