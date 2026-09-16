package com.carddemo;

import com.carddemo.queue.InProcessMqService;
import com.carddemo.queue.Message;
import com.carddemo.queue.MqQueues;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end request/reply round trips through the registered COACCT01/CODATE01
 * consumers on the seeded H2 database (FR-S22-03..08). Exercises the full seam:
 * request put → trigger-started drain → repository read → reply put.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:carddemo-mq-it;DB_CLOSE_DELAY=-1",
        "carddemo.seed.data-dir=classpath:seed",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MqRequestReplyIntegrationTest {

    private static final Pattern DATE_REPLY = Pattern.compile(
            "^SYSTEM DATE : \\d{2}-\\d{2}-\\d{4}SYSTEM TIME : \\d{2}:\\d{2}:\\d{2} *$");

    @Autowired
    private InProcessMqService mq;

    @Test
    void acctInquiryRoundTripOnSeededAccountOne() {
        // Seed acct 1 (acctdata.txt): bal 194.00, credit 2020.00, cash 1020.00,
        // dates 2020-01-01/2025-01-01/2025-01-01, cyc 0/0, group '02108'.
        mq.put(MqQueues.REQUEST_ACCT, new Message("it-msg-1", "it-corr-1", null,
                Message.FORMAT_STRING, "INQA00000000001"));

        Message reply = mq.poll(MqQueues.REPLY_ACCT, 10_000);
        assertNotNull(reply, "reply should arrive on carddemo.reply.acct");
        String expectedRecord =
                "ACCOUNT ID : 00000000001ACCOUNT STATUS : YBALANCE : 00000001940{"
                + "CREDIT LIMIT : 00000020200{CASH LIMIT : 00000010200{"
                + "OPEN DATE : 2020-01-01EXPR DATE : 2025-01-01REIS DATE : 2025-01-01"
                + "CREDIT BAL : 00000000000{DEBIT BAL : 00000000000{GROUP ID : 02108     ";
        assertEquals(expectedRecord + " ".repeat(1000 - expectedRecord.length()), reply.payload());
        assertEquals("it-msg-1", reply.msgId());
        assertEquals("it-corr-1", reply.correlId());
        assertEquals(Message.FORMAT_STRING, reply.format());
    }

    @Test
    void replyStillGoesToHardcodedQueueWhenRequestSuppliesReplyTo() {
        // Parity quirk, pinned end-to-end: SAVE-REPLY2Q is captured but never used.
        mq.put(MqQueues.REQUEST_ACCT, new Message("it-msg-2", "it-corr-2",
                "carddemo.custom.reply", Message.FORMAT_STRING, "INQA00000000001"));

        Message reply = mq.poll(MqQueues.REPLY_ACCT, 10_000);
        assertNotNull(reply);
        assertTrue(reply.payload().startsWith("ACCOUNT ID : 00000000001"));
        assertNull(mq.poll("carddemo.custom.reply", 0),
                "nothing may be routed by the request's replyTo");
    }

    @Test
    void unknownAccountRepliesInvalidParameters() {
        mq.put(MqQueues.REQUEST_ACCT, new Message("it-msg-3", "it-corr-3", null,
                null, "INQA00000099999"));

        Message reply = mq.poll(MqQueues.REPLY_ACCT, 10_000);
        assertNotNull(reply);
        assertTrue(reply.payload().startsWith(
                "INVALID REQUEST PARAMETERS ACCT ID : 00000099999"));
        assertFalse(reply.payload().contains("FUNCTION"));
    }

    @Test
    void wrongFunctionRepliesInvalidParametersWithFunction() {
        mq.put(MqQueues.REQUEST_ACCT, new Message("it-msg-4", "it-corr-4", null,
                null, "XXXX00000000001"));

        Message reply = mq.poll(MqQueues.REPLY_ACCT, 10_000);
        assertNotNull(reply);
        assertTrue(reply.payload().startsWith(
                "INVALID REQUEST PARAMETERS ACCT ID : 00000000001FUNCTION : XXXX"));
    }

    @Test
    void dateRequestRoundTrip() {
        mq.put(MqQueues.REQUEST_DATE, new Message("it-msg-5", "it-corr-5", null,
                null, "payload ignored"));

        Message reply = mq.poll(MqQueues.REPLY_DATE, 10_000);
        assertNotNull(reply, "reply should arrive on carddemo.reply.date");
        assertTrue(DATE_REPLY.matcher(reply.payload()).matches());
        assertEquals("it-msg-5", reply.msgId());
        assertEquals("it-corr-5", reply.correlId());
    }
}
