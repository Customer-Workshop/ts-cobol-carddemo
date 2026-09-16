package com.carddemo;

import com.carddemo.model.PendingAuthSummary;
import com.carddemo.queue.InProcessMqService;
import com.carddemo.queue.Message;
import com.carddemo.queue.MqQueues;
import com.carddemo.repository.PendingAuthSummaryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live round-trip for FR-S20-01/04: with consumers enabled, {@code put} on the
 * PAUTH request queue wakes the registered AuthProcessingService drain (the
 * CP00 trigger monitor), which applies the auth and replies on the named
 * reply queue with the request's correlId.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:s20authqueue;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "carddemo.seed.data-dir=classpath:seed"
})
class AuthProcessingQueueIT {

    @Autowired private InProcessMqService mq;
    @Autowired private PendingAuthSummaryRepository summaries;

    @Test
    void requestTriggersConsumerRunAndReply_frS20_01_04() throws Exception {
        Thread.sleep(100); // MqTriggerListener fires before the context settles
        String request = String.join(",", "240301", "143512", "1111222233334444",
                "0500", "2512", "PAUT00", "MQSRC", "000500", "25.00", "5411",
                "840", "05", "M00000123", "AMAZON MKTPLACE", "SEATTLE", "WA",
                "98101", "T-QUEUE-1");
        Message inbound = new Message(null, "CORR-IT", MqQueues.REPLY_PAUTH,
                Message.FORMAT_STRING, request);
        mq.put(MqQueues.REQUEST_PAUTH, inbound);

        Message reply = mq.poll(MqQueues.REPLY_PAUTH, 15_000);
        assertNotNull(reply, "expected async reply on " + MqQueues.REPLY_PAUTH);
        assertEquals("CORR-IT", reply.correlId());
        String[] fields = reply.payload().split(",", -1);
        assertEquals("1111222233334444", fields[0]);
        assertEquals("T-QUEUE-1", fields[1].trim());
        // card exists in the ASCII seed → decision and persist both happened
        assertEquals("00", fields[3]);
        assertTrue(summaries.findAll().stream().map(PendingAuthSummary::getAcctId)
                .anyMatch(id -> id != null && id == 1L));
        assertEquals(0, mq.depth(MqQueues.REQUEST_PAUTH));
        assertNull(mq.poll(MqQueues.REPLY_PAUTH, 0));
    }
}
