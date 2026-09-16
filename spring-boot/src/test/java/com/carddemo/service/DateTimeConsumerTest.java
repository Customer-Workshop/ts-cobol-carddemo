package com.carddemo.service;

import com.carddemo.queue.InProcessMqService;
import com.carddemo.queue.Message;
import com.carddemo.queue.MqException;
import com.carddemo.queue.MqProcessingException;
import com.carddemo.queue.MqQueues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CODATE01 request/reply tests (FR-S22-03..05, 07; program FRs CODATE01-03..07).
 * Expectations derive from the COBOL: 'SYSTEM DATE : '+MM-DD-YYYY+'SYSTEM TIME : '+HH:MM:SS
 * with no separating space (:355-356), ids echoed (:373-374), replyTo ignored (:147).
 */
class DateTimeConsumerTest {

    private static final Pattern REPLY_PATTERN = Pattern.compile(
            "^SYSTEM DATE : \\d{2}-\\d{2}-\\d{4}SYSTEM TIME : \\d{2}:\\d{2}:\\d{2} *$");

    private InProcessMqService mq;
    private DateTimeConsumer consumer;

    @BeforeEach
    void setUp() {
        mq = new InProcessMqService(10);
        consumer = new DateTimeConsumer(mq);
    }

    @Test
    void anyPayloadGetsFormattedDateTimeReply() {
        consumer.processRequest(new Message("d-msg", "d-corr", "carddemo.requester.reply",
                Message.FORMAT_STRING, "any request content"));

        Message reply = mq.poll(MqQueues.REPLY_DATE, 0);
        assertNotNull(reply);
        assertEquals(1000, reply.payload().length());
        assertTrue(REPLY_PATTERN.matcher(reply.payload()).matches(),
                "reply is 'SYSTEM DATE : '+MM-DD-YYYY+'SYSTEM TIME : '+HH:MM:SS (no space) + padding");
        assertEquals("d-msg", reply.msgId());
        assertEquals("d-corr", reply.correlId());
        assertEquals(Message.FORMAT_STRING, reply.format());
    }

    @Test
    void emptyPayloadStillReplies() {
        consumer.processRequest(new Message("d-msg", "d-corr", null, null, ""));

        Message reply = mq.poll(MqQueues.REPLY_DATE, 0);
        assertNotNull(reply);
        assertTrue(REPLY_PATTERN.matcher(reply.payload()).matches());
    }

    @Test
    void replyIgnoresRequestReplyToQuirk() {
        consumer.processRequest(new Message("d-msg", "d-corr", "carddemo.requester.reply",
                null, "x"));

        assertNull(mq.poll("carddemo.requester.reply", 0));
        assertNotNull(mq.poll(MqQueues.REPLY_DATE, 0));
    }

    @Test
    void replyPutFailureWritesMqputErrToErrorQueueThenTerminates() {
        InProcessMqService failingMq = mock(InProcessMqService.class);
        when(failingMq.put(eq(MqQueues.REPLY_DATE), any()))
                .thenThrow(new MqException(MqQueues.REPLY_DATE, "put failed"));
        when(failingMq.put(eq(MqQueues.ERROR), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        DateTimeConsumer failingConsumer = new DateTimeConsumer(failingMq);

        assertThrows(MqProcessingException.class,
                () -> failingConsumer.processRequest(new Message("d-msg", "d-corr", null, null, "x")));

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(failingMq).put(eq(MqQueues.ERROR), captor.capture());
        assertTrue(captor.getValue().payload().contains("MQPUT ERR"));
        assertTrue(captor.getValue().payload().contains(MqQueues.REPLY_DATE));
    }

    @Test
    void registeredConsumerDrainsRequestAndReplies() {
        consumer.register();
        mq.put(MqQueues.REQUEST_DATE,
                new Message("d-msg", "d-corr", null, null, "whatever"));

        Message reply = mq.poll(MqQueues.REPLY_DATE, 10_000);
        assertNotNull(reply);
        assertEquals(0, mq.depth(MqQueues.REQUEST_DATE));
    }
}
