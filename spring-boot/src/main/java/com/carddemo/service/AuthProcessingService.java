package com.carddemo.service;

import com.carddemo.data.CobolFieldFormatter;
import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.Customer;
import com.carddemo.model.PendingAuthDetail;
import com.carddemo.model.PendingAuthSummary;
import com.carddemo.queue.InProcessMqService;
import com.carddemo.queue.Message;
import com.carddemo.queue.MqException;
import com.carddemo.queue.MqProcessingException;
import com.carddemo.queue.MqQueues;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import com.carddemo.repository.PendingAuthDetailRepository;
import com.carddemo.repository.PendingAuthSummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Java port of COPAUA0C — the CP00 MQ-triggered authorization processor
 * (app/app-authorization-ims-db2-mq/cbl/COPAUA0C.cbl, stream S-20).
 * Drains {@link MqQueues#REQUEST_PAUTH} (the AWS.M2.CARDDEMO.PAUTH.REQUEST name the
 * MQTM trigger data carries), parses each 18-field CSV request (2100-EXTRACT),
 * resolves card→acct→cust→summary (5100/5200/5300/5500), decides (6000), replies
 * on the request's replyTo (7100), and persists summary+detail when the card was
 * found in XREF (8000).
 * <p>
 * Preserved behaviours (do not "fix"):
 * <ul>
 *   <li>PA-RL-AUTH-ID-CODE is filled from the request's auth <i>time</i> field
 *       (:666 — likely a defect in the source; the FR matrix pins it).</li>
 *   <li>The 3100 lookup-miss reason beats 4100 in the reason EVALUATE (:704-708).</li>
 *   <li>A missing ACCT/CUST still answers '05'/'3100'; a found summary still drives
 *       the limit test even when the ACCT read failed (:661-674).</li>
 *   <li>Amount display is {@code PIC -zzzzzzzzz9.99} and the reply CSV ends with a
 *       trailing ',' (STRING DELIMITED BY SIZE, :722-731).</li>
 *   <li>Nothing is persisted when the card is not in XREF (:461).</li>
 *   <li>5600-READ-PROFILE-DATA stays a no-op hook (:646-648) — the 4200/4300/5100/5200
 *       decline reasons are therefore unreachable today, kept for the extension
 *       point.</li>
 *   <li>Run bound is 500 processed messages per invocation (FR-S20-08). The source's
 *       {@code WS-MSG-PROCESSED > WS-REQSTS-PROCESS-LIMIT} check runs after the
 *       increment (:338) so it would process a 501st; the FR's "exactly 500"
 *       acceptance is implemented instead.</li>
 * </ul>
 */
@Service
public class AuthProcessingService {

    /** WS-REQSTS-PROCESS-LIMIT (COPAUA0C.cbl:40). */
    public static final int PROCESS_LIMIT = 500;
    /** WS-CICS-TRANID (:32) — ERR-APPLICATION on the CSSL record. */
    public static final String TRAN_ID = "CP00";
    /** WS-PGM-AUTH (:31) — ERR-PROGRAM on the CSSL record. */
    public static final String PROGRAM_ID = "COPAUA0C";

    private static final Logger log = LoggerFactory.getLogger(AuthProcessingService.class);

    private final InProcessMqService mq;
    private final CardXrefRepository xrefs;
    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final PendingAuthSummaryRepository summaries;
    private final PendingAuthDetailRepository details;
    private final TransactionTemplate tx;

    public AuthProcessingService(InProcessMqService mq, CardXrefRepository xrefs,
                                 AccountRepository accounts, CustomerRepository customers,
                                 PendingAuthSummaryRepository summaries,
                                 PendingAuthDetailRepository details,
                                 PlatformTransactionManager transactionManager) {
        this.mq = mq;
        this.xrefs = xrefs;
        this.accounts = accounts;
        this.customers = customers;
        this.summaries = summaries;
        this.details = details;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * 1000-INITIALIZE + MQTM registration: opens the request queue (1100, M001 on
     * failure) and registers this consumer so the trigger-started CP00 task exists
     * in-process (S22-B3). Each handler invocation is one bounded run of
     * {@link #PROCESS_LIMIT} messages.
     */
    public void register() {
        mq.open(MqQueues.REQUEST_PAUTH);
        mq.registerConsumer(MqQueues.REQUEST_PAUTH, this::processTriggeredRun);
    }

    /**
     * Trigger entry: the drain delivers the first message of a task; the handler
     * keeps polling until the queue drains or the 500-message process limit —
     * the 2000-MAIN-PROCESS loop inside one CP00 invocation. A task that ends at
     * the limit leaves the remaining messages queued; the seam retriggers the
     * consumer on the next put exactly as the trigger monitor would.
     */
    private void processTriggeredRun(Message first) {
        runLoop(first);
    }

    /**
     * One synchronous CP00 invocation (the whole PERFORM UNTIL loop): poll,
     * process, SYNCPOINT, repeat until NO-MSG-AVAILABLE or the process limit.
     * Returns the number of messages processed — the WS-MSG-PROCESSED counter.
     */
    public int processRun() {
        Message first = pollRequest();
        if (first == null) {
            return 0;
        }
        return runLoop(first);
    }

    private int runLoop(Message first) {
        int processed = 0;
        Message current = first;
        while (current != null && processed < PROCESS_LIMIT) {
            processMessage(current);
            processed++;
            current = processed < PROCESS_LIMIT ? pollRequest() : null;
        }
        return processed;
    }

    /** 3100-READ-REQUEST-MQ: MQGET with the 5s wait; M003 on a non-empty failure. */
    private Message pollRequest() {
        try {
            return mq.poll(MqQueues.REQUEST_PAUTH);
        } catch (MqException failure) {
            errorLog("C", "C", "M003", failure.getClass().getSimpleName(), "",
                    "FAILED TO READ REQUEST MQ", "");
            throw failure;
        }
    }

    /**
     * One message through 5000-PROCESS-AUTH: parse (2100), resolve (51xx-55xx),
     * decide (6000), reply (7100), persist when the card was found (8000).
     * The IMS writes run in their own transaction — the EXEC CICS SYNCPOINT
     * committed per message (:335).
     */
    public void processMessage(Message request) {
        String payload = request.payload() == null ? "" : request.payload();
        AuthRequest req = parse(payload);
        if (req == null) {
            // UNSTRING failure has no error-reply path in the source (§11.3):
            // log-only, no reply, the run continues.
            errorLog("W", "A", "A004", "", "", "REQUEST CSV PARSE FAILED",
                    abbreviate(payload, 20));
            return;
        }
        String cardKey = CobolFieldFormatter.pad(req.cardNum(), 16);
        CardXref xref;
        try {
            xref = xrefs.findById(cardKey).orElse(null);
        } catch (RuntimeException failure) {
            errorLog("C", "C", "C001", failure.getClass().getSimpleName(), "",
                    "FAILED TO READ XREF FILE", cardKey);
            throw new MqProcessingException("CCXREF read failed", failure);
        }
        boolean cardFound = xref != null;
        boolean acctFound = cardFound;
        boolean custFound = cardFound;
        Account account = null;
        Customer customer = null;
        PendingAuthSummary summary = null;
        if (!cardFound) {
            // :494-495 — CARD-NFOUND-XREF and NFOUND-ACCT-IN-MSTR are set together.
            errorLog("W", "A", "A001", "", "", "CARD NOT FOUND IN XREF", cardKey);
        } else {
            String acctKey = "%011d".formatted(xref.getXrefAcctId());
            try {
                account = accounts.findById(xref.getXrefAcctId()).orElse(null);
            } catch (RuntimeException failure) {
                errorLog("C", "C", "C002", failure.getClass().getSimpleName(), "",
                        "FAILED TO READ ACCT FILE", acctKey);
                throw new MqProcessingException("ACCTDAT read failed", failure);
            }
            if (account == null) {
                acctFound = false;
                errorLog("W", "A", "A002", "", "", "ACCT NOT FOUND IN XREF", acctKey);
            }
            String custKey = "%09d".formatted(xref.getXrefCustId());
            try {
                customer = customers.findById(xref.getXrefCustId()).orElse(null);
            } catch (RuntimeException failure) {
                errorLog("C", "C", "C003", failure.getClass().getSimpleName(), "",
                        "FAILED TO READ CUST FILE", custKey);
                throw new MqProcessingException("CUSTDAT read failed", failure);
            }
            if (customer == null) {
                custFound = false;
                errorLog("W", "A", "A003", "", "", "CUST NOT FOUND IN XREF", custKey);
            }
            try {
                summary = summaries.findById(xref.getXrefAcctId()).orElse(null);
            } catch (RuntimeException failure) {
                errorLog("C", "I", "I002", failure.getClass().getSimpleName(), "",
                        "IMS GET SUMMARY FAILED", cardKey);
                throw new MqProcessingException("PAUTSUM0 GU failed", failure);
            }
            // 5600-READ-PROFILE-DATA — CONTINUE stub preserved (:646-648).
        }
        Decision decision = decide(req, summary, account, cardFound, acctFound, custFound);
        sendReply(request, req, decision, cardKey);
        if (cardFound) {
            persist(xref, account, req, decision, cardKey);
        }
    }

    /** 2100-EXTRACT: UNSTRING ',' into the CCPAURQY 01 + NUMVAL on the amount. */
    private AuthRequest parse(String payload) {
        String[] fields = payload.split(",", -1);
        if (fields.length < 18) {
            return null;
        }
        // WS-TRANSACTION-AMT-AN is X(13) (:61) — a longer amount token truncates
        // before NUMVAL evaluates it.
        String amountText = fields[8].length() > 13 ? fields[8].substring(0, 13) : fields[8];
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountText.trim());
        } catch (NumberFormatException failure) {
            return null;
        }
        return new AuthRequest(fields[0], fields[1], fields[2], fields[3], fields[4],
                fields[5], fields[6], numeric(fields[7]), amount, fields[9], fields[10],
                numeric(fields[11]), fields[12], fields[13], fields[14], fields[15],
                fields[16], fields[17]);
    }

    /**
     * 6000-MAKE-DECISION: the summary's own limit minus balance wins over the
     * account row (:666-674); the reason EVALUATE order is preserved including
     * the profile-driven flags that the 5600 stub leaves unset.
     */
    private Decision decide(AuthRequest req, PendingAuthSummary summary, Account account,
                            boolean cardFound, boolean acctFound, boolean custFound) {
        boolean decline = false;
        boolean insufficientFund = false;
        if (summary != null) {
            BigDecimal available = nz(summary.getCreditLimit()).subtract(nz(summary.getCreditBalance()));
            if (req.transactionAmt().compareTo(available) > 0) {
                decline = true;
                insufficientFund = true;
            }
        } else if (acctFound) {
            BigDecimal available = nz(account.getAcctCreditLimit()).subtract(nz(account.getAcctCurrBal()));
            if (req.transactionAmt().compareTo(available) > 0) {
                decline = true;
                insufficientFund = true;
            }
        } else {
            decline = true;
        }
        String respCode;
        String reason;
        BigDecimal approvedAmt;
        if (decline) {
            respCode = "05";
            approvedAmt = BigDecimal.ZERO;
            if (!cardFound || !acctFound || !custFound) {
                reason = "3100";
            } else if (insufficientFund) {
                reason = "4100";
            } else {
                // 4200/4300/5100/5200 need profile data the 5600 stub never loads.
                reason = "9000";
            }
        } else {
            respCode = "00";
            approvedAmt = req.transactionAmt();
            reason = "0000";
        }
        return new Decision(!decline, respCode, reason, approvedAmt, req.authTime());
    }

    /** 7100-SEND-RESPONSE: MQPUT1 to the request's replyTo; M004 on failure. */
    private void sendReply(Message request, AuthRequest req, Decision decision, String cardKey) {
        String csv = CobolFieldFormatter.pad(req.cardNum(), 16)
                + "," + CobolFieldFormatter.pad(req.transactionId(), 15)
                + "," + CobolFieldFormatter.pad(decision.authIdCode(), 6)
                + "," + CobolFieldFormatter.pad(decision.respCode(), 2)
                + "," + CobolFieldFormatter.pad(decision.respReason(), 4)
                + "," + editedAmount(decision.approvedAmt()) + ",";
        // MQMI-NONE → fresh msgId on put; correlId and replyTo(spaces→null) per :752-756.
        Message reply = new Message(null, request.correlId(), null, Message.FORMAT_STRING, csv);
        try {
            mq.put(request.replyTo(), reply);
        } catch (MqException failure) {
            errorLog("C", "M", "M004", failure.getClass().getSimpleName(), "",
                    "FAILED TO PUT ON REPLY MQ", cardKey);
            throw new MqProcessingException("Reply MQPUT1 failed", failure);
        }
    }

    /**
     * 8000-WRITE-AUTH-TO-DB: 8400 summary REPL-or-ISRT + 8500 detail ISRT in one
     * transaction (the per-message SYNCPOINT). I003/I004 critical on failure.
     */
    private void persist(CardXref xref, Account account, AuthRequest req,
                         Decision decision, String cardKey) {
        try {
            tx.executeWithoutResult(status -> {
                PendingAuthSummary summary = summaries.findById(xref.getXrefAcctId())
                        .orElseGet(() -> {
                            PendingAuthSummary fresh = new PendingAuthSummary();
                            fresh.setAcctId(xref.getXrefAcctId());
                            fresh.setCustId(xref.getXrefCustId());
                            // INITIALIZE PENDING-AUTH-SUMMARY REPLACING NUMERIC
                            // DATA BY ZERO (:801-803)
                            fresh.setCreditLimit(BigDecimal.ZERO);
                            fresh.setCashLimit(BigDecimal.ZERO);
                            fresh.setCreditBalance(BigDecimal.ZERO);
                            fresh.setCashBalance(BigDecimal.ZERO);
                            fresh.setApprovedAuthCnt(0);
                            fresh.setDeclinedAuthCnt(0);
                            fresh.setApprovedAuthAmt(BigDecimal.ZERO);
                            fresh.setDeclinedAuthAmt(BigDecimal.ZERO);
                            return fresh;
                        });
                if (account != null) {
                    // The source moves the ACCTDAT record into PA-CREDIT-LIMIT /
                    // PA-CASH-LIMIT unconditionally (:810-812); when the acct read
                    // failed the record buffer is undefined, so the port leaves the
                    // existing (or zeroed) limits in place.
                    summary.setCreditLimit(account.getAcctCreditLimit());
                    summary.setCashLimit(account.getAcctCashCreditLimit());
                }
                if (decision.approved()) {
                    summary.setApprovedAuthCnt(nz(summary.getApprovedAuthCnt()) + 1);
                    summary.setApprovedAuthAmt(nz(summary.getApprovedAuthAmt()).add(decision.approvedAmt()));
                    summary.setCreditBalance(nz(summary.getCreditBalance()).add(decision.approvedAmt()));
                    summary.setCashBalance(BigDecimal.ZERO);
                } else {
                    summary.setDeclinedAuthCnt(nz(summary.getDeclinedAuthCnt()) + 1);
                    // The FR moves the current request amount (:821 names
                    // PA-TRANSACTION-AMT, which the source only fills in 8500).
                    summary.setDeclinedAuthAmt(nz(summary.getDeclinedAuthAmt()).add(req.transactionAmt()));
                }
                try {
                    summaries.save(summary);
                } catch (RuntimeException failure) {
                    throw new AuthWriteFailure("I003", failure);
                }
                try {
                    details.save(newDetail(xref, req, decision));
                } catch (RuntimeException failure) {
                    throw new AuthWriteFailure("I004", failure);
                }
            });
        } catch (AuthWriteFailure failure) {
            errorLog("C", "I", failure.location(), failure.getCause().getClass().getSimpleName(),
                    "", "I003".equals(failure.location())
                            ? "IMS UPDATE SUMRY FAILED" : "IMS INSERT DETL FAILED", cardKey);
            throw new MqProcessingException("IMS write failed at " + failure.location(), failure);
        }
    }

    /** 8500-INSERT-AUTH: complement key from now + request/reply fields + match. */
    private PendingAuthDetail newDetail(CardXref xref, AuthRequest req, Decision decision) {
        LocalDateTime now = LocalDateTime.now();
        int yyddd = (now.getYear() % 100) * 1000 + now.getDayOfYear();
        int hmsmm = now.getHour() * 10_000_000 + now.getMinute() * 100_000
                + now.getSecond() * 1_000 + now.getNano() / 1_000_000;
        PendingAuthDetail detail = new PendingAuthDetail();
        detail.setId(new PendingAuthDetail.Id(xref.getXrefAcctId(),
                99999 - yyddd, 999999999 - hmsmm));
        detail.setAuthOrigDate(CobolFieldFormatter.pad(req.authDate(), 6));
        detail.setAuthOrigTime(CobolFieldFormatter.pad(req.authTime(), 6));
        detail.setCardNum(CobolFieldFormatter.pad(req.cardNum(), 16));
        detail.setAuthType(CobolFieldFormatter.pad(req.authType(), 4));
        detail.setCardExpiryDate(CobolFieldFormatter.pad(req.cardExpiryDate(), 4));
        detail.setMessageType(CobolFieldFormatter.pad(req.messageType(), 6));
        detail.setMessageSource(CobolFieldFormatter.pad(req.messageSource(), 6));
        detail.setAuthIdCode(CobolFieldFormatter.pad(decision.authIdCode(), 6));
        detail.setAuthRespCode(CobolFieldFormatter.pad(decision.respCode(), 2));
        detail.setAuthRespReason(CobolFieldFormatter.pad(decision.respReason(), 4));
        detail.setProcessingCode(req.processingCode());
        detail.setTransactionAmt(req.transactionAmt());
        detail.setApprovedAmt(decision.approvedAmt());
        detail.setMerchantCategoryCode(CobolFieldFormatter.pad(req.merchantCategoryCode(), 4));
        detail.setAcqrCountryCode(CobolFieldFormatter.pad(req.acqrCountryCode(), 3));
        detail.setPosEntryMode(req.posEntryMode());
        detail.setMerchantId(CobolFieldFormatter.pad(req.merchantId(), 15));
        detail.setMerchantName(CobolFieldFormatter.pad(req.merchantName(), 22));
        detail.setMerchantCity(CobolFieldFormatter.pad(req.merchantCity(), 13));
        detail.setMerchantState(CobolFieldFormatter.pad(req.merchantState(), 2));
        detail.setMerchantZip(CobolFieldFormatter.pad(req.merchantZip(), 9));
        detail.setTransactionId(CobolFieldFormatter.pad(req.transactionId(), 15));
        detail.setMatchStatus(decision.approved() ? "P" : "D");
        detail.setAuthFraud(" ");
        detail.setFraudRptDate("        ");
        return detail;
    }

    /**
     * 9500-LOG-ERROR: the CCPAUERY CSSL record as a structured log line —
     * date YYMMDD, time HHMMSS, application CP00, program COPAUA0C, location,
     * level (L/I/W/C), subsystem (A/C/I/D/M/F), code-1/2, message, event-key.
     */
    private void errorLog(String level, String subsystem, String location,
                          String code1, String code2, String message, String eventKey) {
        LocalDateTime now = LocalDateTime.now();
        String record = String.format(Locale.ROOT, "%02d%02d%02d%02d%02d%02d",
                now.getYear() % 100, now.getMonthValue(), now.getDayOfMonth(),
                now.getHour(), now.getMinute(), now.getSecond())
                + CobolFieldFormatter.pad(TRAN_ID, 8)
                + CobolFieldFormatter.pad(PROGRAM_ID, 8)
                + CobolFieldFormatter.pad(location, 4)
                + CobolFieldFormatter.pad(level, 1)
                + CobolFieldFormatter.pad(subsystem, 1)
                + CobolFieldFormatter.pad(code1, 9)
                + CobolFieldFormatter.pad(code2, 9)
                + CobolFieldFormatter.pad(message, 50)
                + CobolFieldFormatter.pad(eventKey, 20);
        switch (level) {
            case "C" -> log.error("CSSL {}", record);
            case "W" -> log.warn("CSSL {}", record);
            case "I" -> log.info("CSSL {}", record);
            default -> log.debug("CSSL {}", record);
        }
    }

    /** WS-APPROVED-AMT-DIS PIC -zzzzzzzzz9.99 — zero-suppressed, sign floats. */
    private static String editedAmount(BigDecimal value) {
        return String.format(Locale.ROOT, "%14.2f", nz(value));
    }

    /** Numeric display receiving fields: blank/non-numeric content lands as zero. */
    private static Integer numeric(String field) {
        String text = field == null ? "" : field.trim();
        if (text.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException failure) {
            return 0;
        }
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static String abbreviate(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length);
    }

    private record AuthRequest(String authDate, String authTime, String cardNum,
                               String authType, String cardExpiryDate, String messageType,
                               String messageSource, Integer processingCode,
                               BigDecimal transactionAmt, String merchantCategoryCode,
                               String acqrCountryCode, Integer posEntryMode,
                               String merchantId, String merchantName, String merchantCity,
                               String merchantState, String merchantZip, String transactionId) {
    }

    private record Decision(boolean approved, String respCode, String respReason,
                            BigDecimal approvedAmt, String authIdCode) {
    }

    private static final class AuthWriteFailure extends RuntimeException {
        private final String location;

        AuthWriteFailure(String location, Throwable cause) {
            super(cause);
            this.location = location;
        }

        String location() {
            return location;
        }
    }
}
