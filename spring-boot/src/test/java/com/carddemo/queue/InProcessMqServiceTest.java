package com.carddemo.queue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Seam lifecycle tests for the in-process MQ boundary (S22-B1/B3): put/poll round trip,
 * bounded wait = MQRC-NO-MSG-AVAILABLE, drain-and-exit consumers, put retriggering, and
 * the request/reply contract S-20 will use.
 */
class InProcessMqServiceTest {

    @Test
    void putPollRoundTripPreservesEnvelope() {
        InProcessMqService mq = new InProcessMqService(10);
        Message sent = new Message("msg-1", "corr-1", "carddemo.reply.acct",
                Message.FORMAT_STRING, "INQA00000000001");
        assertSame(sent, mq.put(MqQueues.REQUEST_ACCT, sent));

        Message got = mq.poll(MqQueues.REQUEST_ACCT, 1000);
        assertNotNull(got);
        assertEquals("msg-1", got.msgId());
        assertEquals("corr-1", got.correlId());
        assertEquals("carddemo.reply.acct", got.replyTo());
        assertEquals(Message.FORMAT_STRING, got.format());
        assertEquals("INQA00000000001", got.payload());
    }

    @Test
    void putAssignsMsgIdWhenAbsentLikeAQueueManager() {
        InProcessMqService mq = new InProcessMqService(10);
        Message effective = mq.put("q", Message.of("body"));
        assertNotNull(effective.msgId());
        assertEquals(24, effective.msgId().length());
        assertEquals(effective.msgId(), mq.poll("q", 10).msgId());
    }

    @Test
    void emptyPollWaitsThenReturnsNullForNoMsgAvailable() {
        InProcessMqService mq = new InProcessMqService(10);
        long started = System.nanoTime();
        assertNull(mq.poll("nothing-here", 80));
        assertTrue(System.nanoTime() - started >= 80_000_000L, "poll should honour the wait interval");
    }

    @Test
    void openIsIdempotentAndCloseIsANoOp() {
        InProcessMqService mq = new InProcessMqService(10);
        mq.open("a");
        mq.open("a");
        mq.close("a");
        mq.close("a");
        assertEquals(0, mq.depth("a"));
    }

    @Test
    void drainHandlesEveryMessageThenExitsOnEmptyPoll() {
        InProcessMqService mq = new InProcessMqService(10);
        mq.put("q", Message.of("one"));
        mq.put("q", Message.of("two"));
        List<String> handled = new ArrayList<>();
        mq.drain("q", m -> handled.add(m.payload()), 20);
        assertEquals(List.of("one", "two"), handled);
    }

    @Test
    void registeredConsumerDrainsThenExitsAndPutRetriggers() throws Exception {
        InProcessMqService mq = new InProcessMqService(30);
        CountDownLatch first = new CountDownLatch(1);
        CountDownLatch second = new CountDownLatch(1);
        Thread drain = mq.registerConsumer("trig", m -> {
            first.countDown();
            second.countDown();
        });
        mq.put("trig", Message.of("req-1"));
        assertTrue(first.await(5, TimeUnit.SECONDS));
        drain.join(10_000);                       // drain exits after one empty poll
        assertFalse(drain.isAlive());

        mq.put("trig", Message.of("req-2"));      // trigger-monitor re-launch
        assertTrue(second.await(5, TimeUnit.SECONDS));
    }

    @Test
    void handlerFailureTerminatesTheDrainButQueueKeepsWorking() throws Exception {
        InProcessMqService mq = new InProcessMqService(30);
        Thread drain = mq.registerConsumer("trig", m -> {
            throw new MqProcessingException("terminate");
        });
        mq.put("trig", Message.of("boom"));
        drain.join(10_000);
        assertFalse(drain.isAlive());
        assertEquals(0, mq.depth("trig"));        // message was consumed (at-most-once)
    }

    @Test
    void requestReplyContractSupportsReplyToRoutingForS20() throws Exception {
        InProcessMqService mq = new InProcessMqService(30);
        mq.registerConsumer("carddemo.pauth.request",
                request -> mq.put(request.replyTo(), new Message(
                        request.msgId(), request.correlId(), null,
                        Message.FORMAT_STRING, "reply:" + request.payload())));

        Message request = mq.put("carddemo.pauth.request",
                new Message(null, "corr-7", "carddemo.pauth.reply", null, "csv-body"));
        Message reply = mq.poll("carddemo.pauth.reply", 10_000);

        assertNotNull(reply, "reply should arrive on the request's replyTo queue");
        assertEquals(request.msgId(), reply.msgId());
        assertEquals("corr-7", reply.correlId());
        assertEquals("reply:csv-body", reply.payload());
    }
}
