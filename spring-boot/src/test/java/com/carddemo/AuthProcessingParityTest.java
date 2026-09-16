package com.carddemo;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.Customer;
import com.carddemo.model.PendingAuthDetail;
import com.carddemo.model.PendingAuthSummary;
import com.carddemo.queue.InProcessMqService;
import com.carddemo.queue.Message;
import com.carddemo.queue.MqProcessingException;
import com.carddemo.queue.MqQueues;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import com.carddemo.repository.PendingAuthDetailRepository;
import com.carddemo.repository.PendingAuthSummaryRepository;
import com.carddemo.service.AuthProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FR-S20-01..08 parity matrix for COPAUA0C (S20_functional_requirement.md).
 * Consumers are disabled so {@code put} only queues; each test drives the
 * service's synchronous {@code processMessage}/{@code processRun} — one CP00
 * invocation. Fixture: acct 1 / cust 1 / card 1111222233334444, credit limit
 * 2020.00, balance 194.00 (available 1826.00).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "carddemo.seed.enabled=false",
        "carddemo.mq.consumers.enabled=false",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:s20authparity;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AuthProcessingParityTest {

    private static final String CARD = "1111222233334444";

    @Autowired private AuthProcessingService service;
    @Autowired private InProcessMqService mq;
    @Autowired private CardXrefRepository xrefs;
    @Autowired private AccountRepository accounts;
    @Autowired private CustomerRepository customers;
    @Autowired private PendingAuthSummaryRepository summaries;
    @Autowired private PendingAuthDetailRepository details;

    @BeforeEach
    void fixture() {
        details.deleteAll();
        summaries.deleteAll();
        accounts.deleteAll();
        xrefs.deleteAll();
        customers.deleteAll();
        drainReplies();
        Customer customer = new Customer();
        customer.setCustId(1L);
        customers.save(customer);
        Account account = new Account();
        account.setAcctId(1L);
        account.setAcctActiveStatus("Y");
        account.setAcctCreditLimit(new BigDecimal("2020.00"));
        account.setAcctCurrBal(new BigDecimal("194.00"));
        account.setAcctCashCreditLimit(new BigDecimal("1020.00"));
        accounts.save(account);
        CardXref xref = new CardXref();
        xref.setXrefCardNumber(CARD);
        xref.setXrefCustId(1L);
        xref.setXrefAcctId(1L);
        xrefs.save(xref);
    }

    private void drainReplies() {
        while (mq.poll(MqQueues.REPLY_PAUTH, 0) != null) {
            // drop leftovers
        }
        while (mq.poll(MqQueues.REQUEST_PAUTH, 0) != null) {
            // drop leftovers
        }
    }

    /** The CCPAURQY 18-field CSV in copybook order. */
    private static String request(String card, String amount, String tranId) {
        return String.join(",", "240301", "143512", card, "0500", "2512", "PAUT00",
                "MQSRC", "000500", amount, "5411", "840", "05", "M00000123",
                "AMAZON MKTPLACE", "SEATTLE", "WA", "98101", tranId);
    }

    private static Message inbound(String payload, String correlId) {
        return new Message(null, correlId, MqQueues.REPLY_PAUTH, Message.FORMAT_STRING, payload);
    }

    private Message reply() {
        Message reply = mq.poll(MqQueues.REPLY_PAUTH, 10_000);
        assertNotNull(reply, "expected a reply on " + MqQueues.REPLY_PAUTH);
        return reply;
    }

    @Test
    void approvedRequestRepliesAndPersists_frS20_01_03_04_05_06() {
        service.processMessage(inbound(request(CARD, "175.50", "T00000000000401"), "CORREL-42"));

        Message reply = reply();
        assertEquals("CORREL-42", reply.correlId());
        assertNotNull(reply.msgId());           // MQMI-NONE → a fresh msg id is assigned
        String[] fields = reply.payload().split(",", -1);
        assertEquals(7, fields.length);         // 6 fields + trailing comma
        assertEquals(CARD, fields[0]);
        assertEquals("T00000000000401", fields[1].trim());
        assertEquals("143512", fields[2]);      // PA-RL-AUTH-ID-CODE = request auth TIME
        assertEquals("00", fields[3]);
        assertEquals("0000", fields[4]);
        assertEquals("175.50", fields[5].trim()); // -zzzzzzzzz9.99 rendering

        PendingAuthSummary summary = summaries.findById(1L).orElseThrow();
        assertEquals(1L, summary.getCustId());
        assertEquals(1, summary.getApprovedAuthCnt());
        assertEquals(0, summary.getDeclinedAuthAmt().signum());
        assertEquals(0, summary.getApprovedAuthAmt().compareTo(new BigDecimal("175.50")));
        assertEquals(0, summary.getCreditBalance().compareTo(new BigDecimal("175.50")));
        assertEquals(0, summary.getCashBalance().signum());
        assertEquals(0, summary.getCreditLimit().compareTo(new BigDecimal("2020.00")));
        assertEquals(0, summary.getCashLimit().compareTo(new BigDecimal("1020.00")));

        List<PendingAuthDetail> rows = details
                .findByIdAcctIdOrderByIdAuthDate9cAscIdAuthTime9cAsc(
                        1L, org.springframework.data.domain.Pageable.unpaged());
        assertEquals(1, rows.size());
        PendingAuthDetail detail = rows.get(0);
        assertEquals("240301", detail.getAuthOrigDate());
        assertEquals("143512", detail.getAuthOrigTime());
        assertEquals(CARD, detail.getCardNum().trim());
        assertEquals("143512", detail.getAuthIdCode().trim());
        assertEquals("00", detail.getAuthRespCode());
        assertEquals("0000", detail.getAuthRespReason());
        assertEquals(0, detail.getApprovedAmt().compareTo(new BigDecimal("175.50")));
        assertEquals(0, detail.getTransactionAmt().compareTo(new BigDecimal("175.50")));
        assertEquals("P", detail.getMatchStatus());
        assertEquals(" ", detail.getAuthFraud());
        assertEquals(Integer.valueOf(500), detail.getProcessingCode());
        assertEquals(Integer.valueOf(5), detail.getPosEntryMode());
        assertTrue(detail.getId().getAuthDate9c() >= 0 && detail.getId().getAuthDate9c() <= 99999);
        assertTrue(detail.getId().getAuthTime9c() >= 0 && detail.getId().getAuthTime9c() <= 999999999);
    }

    @Test
    void declineAgainstSummaryLimits_frS20_03_05() {
        PendingAuthSummary existing = new PendingAuthSummary();
        existing.setAcctId(1L);
        existing.setCustId(1L);
        existing.setCreditLimit(new BigDecimal("100.00"));   // summary limits win over acct
        existing.setCashLimit(BigDecimal.ZERO);
        existing.setCreditBalance(new BigDecimal("90.00"));
        existing.setApprovedAuthCnt(3);
        existing.setDeclinedAuthCnt(0);
        existing.setApprovedAuthAmt(new BigDecimal("10.00"));
        existing.setDeclinedAuthAmt(BigDecimal.ZERO);
        summaries.save(existing);

        service.processMessage(inbound(request(CARD, "50.00", "T1"), "C1"));

        Message reply = reply();
        String[] fields = reply.payload().split(",", -1);
        assertEquals("05", fields[3]);
        assertEquals("4100", fields[4]);
        assertEquals("0.00", fields[5].trim());

        PendingAuthSummary summary = summaries.findById(1L).orElseThrow();
        assertEquals(3, summary.getApprovedAuthCnt());
        assertEquals(1, summary.getDeclinedAuthCnt());
        assertEquals(0, summary.getDeclinedAuthAmt().compareTo(new BigDecimal("50.00")));
        PendingAuthDetail detail = details
                .findByIdAcctIdOrderByIdAuthDate9cAscIdAuthTime9cAsc(
                        1L, org.springframework.data.domain.Pageable.unpaged()).get(0);
        assertEquals("D", detail.getMatchStatus());
        assertEquals("05", detail.getAuthRespCode());
        assertEquals("4100", detail.getAuthRespReason());
    }

    @Test
    void declineAgainstAccountLimitsWhenNoSummary_frS20_03() {
        service.processMessage(inbound(request(CARD, "2000.00", "T2"), "C2"));
        String[] fields = reply().payload().split(",", -1);
        assertEquals("05", fields[3]);
        assertEquals("4100", fields[4]);
    }

    @Test
    void unknownCardDeclines3100AndPersistsNothing_frS20_02_03() {
        service.processMessage(inbound(request("9999888877776666", "10.00", "T3"), "C3"));
        String[] fields = reply().payload().split(",", -1);
        assertEquals("05", fields[3]);
        assertEquals("3100", fields[4]);
        assertTrue(summaries.findAll().isEmpty());
        assertTrue(details.findAll().isEmpty());
    }

    @Test
    void missingAcctDeclines3100ButStillPersists_frS20_02_03() {
        CardXref xref = new CardXref();
        xref.setXrefCardNumber("2222333344445555");
        xref.setXrefCustId(1L);
        xref.setXrefAcctId(999L);            // no ACCTDAT row
        xrefs.save(xref);

        service.processMessage(inbound(request("2222333344445555", "10.00", "T4"), "C4"));
        String[] fields = reply().payload().split(",", -1);
        assertEquals("05", fields[3]);
        assertEquals("3100", fields[4]);
        // CARD-FOUND-XREF still drives 8000-WRITE-AUTH-TO-DB.
        assertTrue(summaries.findById(999L).isPresent());
        assertEquals(1, details.findAll().size());
    }

    @Test
    void missingCustStillDecidesByLimits_frS20_02_03() {
        // The source's cust-miss flag feeds only the reason EVALUATE; an
        // in-limit request is still approved (flagged in the FR deviations).
        customers.deleteAll();
        service.processMessage(inbound(request(CARD, "10.00", "T5"), "C5"));
        String[] fields = reply().payload().split(",", -1);
        assertEquals("00", fields[3]);
        assertEquals("0000", fields[4]);
    }

    @Test
    void requestWithoutReplyQueueLogsM004AndTerminates_frS20_04_07() {
        Logger logger = (Logger) LoggerFactory.getLogger(AuthProcessingService.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            Message noReplyTo = new Message(null, "C6", null, Message.FORMAT_STRING,
                    request(CARD, "10.00", "T6"));
            assertThrows(MqProcessingException.class, () -> service.processMessage(noReplyTo));
            assertTrue(events.list.stream().anyMatch(e ->
                    e.getFormattedMessage().contains("M004")
                            && e.getFormattedMessage().contains("FAILED TO PUT ON REPLY MQ")));
            assertTrue(details.findAll().isEmpty());   // reply precedes persist
        } finally {
            logger.detachAppender(events);
        }
    }

    @Test
    void unknownCardWarningIsStructuredLog_frS20_07() {
        Logger logger = (Logger) LoggerFactory.getLogger(AuthProcessingService.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            service.processMessage(inbound(request("0000000000000009", "10.00", "T7"), "C7"));
            ILoggingEvent warning = events.list.stream()
                    .filter(e -> e.getFormattedMessage().contains("A001")).findFirst().orElseThrow();
            String record = warning.getFormattedMessage();
            assertTrue(record.contains("CARD NOT FOUND IN XREF"));
            assertTrue(record.contains("COPAUA0C"));
            assertTrue(record.contains("CP00"));
            assertTrue(record.contains("0000000000000009"));   // ERR-EVENT-KEY
            reply();   // the decline reply still went out
        } finally {
            logger.detachAppender(events);
        }
    }

    @Test
    void malformedCsvLogsAndSkipsWithoutReply_frS20_01() {
        Logger logger = (Logger) LoggerFactory.getLogger(AuthProcessingService.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            service.processMessage(inbound("not,a,csv", "C8"));
            assertTrue(events.list.stream().anyMatch(e ->
                    e.getFormattedMessage().contains("REQUEST CSV PARSE FAILED")));
            assertNull(mq.poll(MqQueues.REPLY_PAUTH, 0));
            assertTrue(details.findAll().isEmpty());
        } finally {
            logger.detachAppender(events);
        }
    }

    @Test
    void signedAmountTokenParsesViaNumval_frS20_01() {
        service.processMessage(inbound(request(CARD, "+0000000175.50", "T8"), "C9"));
        String[] fields = reply().payload().split(",", -1);
        assertEquals("00", fields[3]);
        assertEquals("175.50", fields[5].trim());
    }

    @Test
    void runBoundedAtFiveHundredMessages_frS20_08() {
        for (int i = 0; i < 501; i++) {
            mq.put(MqQueues.REQUEST_PAUTH,
                    inbound(request(CARD, "1.00", "T" + i), "CORR" + i));
        }
        int processed = service.processRun();
        assertEquals(500, processed);
        assertEquals(1, mq.depth(MqQueues.REQUEST_PAUTH));
        assertEquals(500, mq.depth(MqQueues.REPLY_PAUTH));
        // The leftover request belongs to the next CP00 invocation.
        assertEquals(1, service.processRun());
        assertEquals(0, mq.depth(MqQueues.REQUEST_PAUTH));
    }

    @Test
    void nonCriticalMidRunContinuesToNextMessage_frS20_08() {
        // A parse failure is log-only — the run keeps going (no error reply path).
        mq.put(MqQueues.REQUEST_PAUTH, inbound(request(CARD, "1.00", "TA"), "CA"));
        mq.put(MqQueues.REQUEST_PAUTH, inbound("garbage", "CB"));
        mq.put(MqQueues.REQUEST_PAUTH, inbound(request(CARD, "2.00", "TC"), "CC"));
        int processed = service.processRun();
        assertEquals(3, processed);
        assertEquals(2, mq.depth(MqQueues.REPLY_PAUTH));
    }
}
