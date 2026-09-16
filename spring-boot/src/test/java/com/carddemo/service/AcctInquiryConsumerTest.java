package com.carddemo.service;

import com.carddemo.model.Account;
import com.carddemo.queue.InProcessMqService;
import com.carddemo.queue.Message;
import com.carddemo.queue.MqException;
import com.carddemo.queue.MqProcessingException;
import com.carddemo.queue.MqQueues;
import com.carddemo.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * COACCT01 request/reply tests (FR-S22-03..05, 07, 08; program FRs COACCT01-03..09).
 * Expectations derive from the COBOL trace, not from the new code: reply layout
 * WS-ACCT-RESPONSE (:130-169), INVALID text (:429-456), error block (:58-67,:501-536).
 */
class AcctInquiryConsumerTest {

    private InProcessMqService mq;
    private AccountRepository accountRepository;
    private AcctInquiryConsumer consumer;

    @BeforeEach
    void setUp() {
        mq = new InProcessMqService(10);
        accountRepository = mock(AccountRepository.class);
        consumer = new AcctInquiryConsumer(mq, accountRepository);
    }

    private static Account accountOne() {
        Account account = new Account();
        account.setAcctId(1L);
        account.setAcctActiveStatus("Y");
        account.setAcctCurrBal(new BigDecimal("194.00"));
        account.setAcctCreditLimit(new BigDecimal("2020.00"));
        account.setAcctCashCreditLimit(new BigDecimal("1020.00"));
        account.setAcctOpenDate(LocalDate.of(2020, 1, 1));
        account.setAcctExpirationDate(LocalDate.of(2025, 1, 1));
        account.setAcctReissueDate(LocalDate.of(2025, 1, 1));
        account.setAcctCurrCycCredit(BigDecimal.ZERO.setScale(2));
        account.setAcctCurrCycDebit(BigDecimal.ZERO.setScale(2));
        account.setAcctGroupId("02108");
        return account;
    }

    private static Message request(String payload) {
        return new Message("req-msg-1", "req-corr-1", "carddemo.requester.reply",
                Message.FORMAT_STRING, payload);
    }

    @Test
    void inqaWithKnownAccountRepliesLabeledRecordOnHardcodedQueue() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(accountOne()));
        consumer.processRequest(request("INQA00000000001"));

        Message reply = mq.poll(MqQueues.REPLY_ACCT, 0);
        assertNotNull(reply, "reply should land on carddemo.reply.acct");
        String expectedRecord =
                "ACCOUNT ID : 00000000001ACCOUNT STATUS : YBALANCE : 00000001940{"
                + "CREDIT LIMIT : 00000020200{CASH LIMIT : 00000010200{"
                + "OPEN DATE : 2020-01-01EXPR DATE : 2025-01-01REIS DATE : 2025-01-01"
                + "CREDIT BAL : 00000000000{DEBIT BAL : 00000000000{GROUP ID : 02108     ";
        assertEquals(expectedRecord + " ".repeat(1000 - expectedRecord.length()), reply.payload());
        // 4100-PUT-REPLY: request MSGID/CORRELID echoed, format MQFMT-STRING (:469-471)
        assertEquals("req-msg-1", reply.msgId());
        assertEquals("req-corr-1", reply.correlId());
        assertEquals(Message.FORMAT_STRING, reply.format());
    }

    @Test
    void replyIgnoresRequestReplyToQuirk() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(accountOne()));
        consumer.processRequest(request("INQA00000000001"));

        assertNull(mq.poll("carddemo.requester.reply", 0),
                "replyTo is captured-but-unused in the source (COACCT01.cbl:371) — nothing should land there");
        assertNotNull(mq.poll(MqQueues.REPLY_ACCT, 0));
    }

    @Test
    void unknownAccountRepliesInvalidParametersWithoutFunction() {
        when(accountRepository.findById(99999L)).thenReturn(Optional.empty());
        consumer.processRequest(request("INQA00000099999"));

        Message reply = mq.poll(MqQueues.REPLY_ACCT, 0);
        assertNotNull(reply);
        // NOTFND path (:429-435): no FUNCTION suffix
        assertTrue(reply.payload().startsWith("INVALID REQUEST PARAMETERS ACCT ID : 00000099999"));
        assertFalse(reply.payload().contains("FUNCTION"));
    }

    @Test
    void wrongFunctionRepliesInvalidParametersWithFunction() {
        consumer.processRequest(request("XXXX00000000001"));

        Message reply = mq.poll(MqQueues.REPLY_ACCT, 0);
        assertNotNull(reply);
        // (:448-456): no space between the 11-digit key and 'FUNCTION'
        assertTrue(reply.payload().startsWith(
                "INVALID REQUEST PARAMETERS ACCT ID : 00000000001FUNCTION : XXXX"));
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void zeroKeyRepliesInvalidParameters() {
        consumer.processRequest(request("INQA00000000000"));

        Message reply = mq.poll(MqQueues.REPLY_ACCT, 0);
        assertNotNull(reply);
        assertTrue(reply.payload().startsWith(
                "INVALID REQUEST PARAMETERS ACCT ID : 00000000000FUNCTION : INQA"));
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void nonNumericKeyRepliesInvalidParametersWithKeyVerbatim() {
        consumer.processRequest(request("INQAABCDEFGHIJK"));

        Message reply = mq.poll(MqQueues.REPLY_ACCT, 0);
        assertNotNull(reply);
        assertTrue(reply.payload().startsWith(
                "INVALID REQUEST PARAMETERS ACCT ID : ABCDEFGHIJKFUNCTION : INQA"));
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void readFailureWritesErrorQueueThenTerminates() {
        when(accountRepository.findById(1L)).thenThrow(new RuntimeException("store down"));

        assertThrows(MqProcessingException.class,
                () -> consumer.processRequest(request("INQA00000000001")));

        Message error = mq.poll(MqQueues.ERROR, 0);
        assertNotNull(error, "MQ-ERR-DISPLAY record should land on carddemo.error");
        String expectedBlock =
                "                         "   // MQ-ERROR-PARA X(25) spaces
                + "  ERROR WHILE READING ACCTF  02  00000  " // msg truncates to X(25)
                + "carddemo.request.acct" + " ".repeat(27);   // MQ-APPL-QUEUE-NAME X(48)
        assertEquals(expectedBlock + " ".repeat(1000 - expectedBlock.length()), error.payload());
        assertNull(mq.poll(MqQueues.REPLY_ACCT, 0), "no reply on a read failure");
    }

    @Test
    void replyPutFailureWritesMqputErrToErrorQueueThenTerminates() {
        InProcessMqService failingMq = mock(InProcessMqService.class);
        when(failingMq.put(eq(MqQueues.REPLY_ACCT), any()))
                .thenThrow(new MqException(MqQueues.REPLY_ACCT, "put failed"));
        when(failingMq.put(eq(MqQueues.ERROR), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        AcctInquiryConsumer failingConsumer = new AcctInquiryConsumer(failingMq, accountRepository);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(accountOne()));

        assertThrows(MqProcessingException.class,
                () -> failingConsumer.processRequest(request("INQA00000000001")));

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(failingMq).put(eq(MqQueues.ERROR), captor.capture());
        // 'MQPUT ERR' with REPLY-QUEUE-NAME in the queue slot (COACCT01.cbl:493-498)
        assertTrue(captor.getValue().payload().contains("MQPUT ERR"));
        assertTrue(captor.getValue().payload().contains(MqQueues.REPLY_ACCT));
    }

    @Test
    void registeredConsumerDrainsRequestAndReplies() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(accountOne()));
        consumer.register();                     // opens the three queues + registers the drain
        mq.put(MqQueues.REQUEST_ACCT, request("INQA00000000001"));

        Message reply = mq.poll(MqQueues.REPLY_ACCT, 10_000);
        assertNotNull(reply, "registered drain should process the request");
        assertEquals(0, mq.depth(MqQueues.REQUEST_ACCT));
    }
}
