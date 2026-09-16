package com.carddemo.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * In-process replacement for IBM MQ — the canonical queue seam for every CardDemo stream
 * that crosses an MQ boundary (B-007; S22-B1..B5 decided in
 * {@code functional/CARDDEMO/S22_vsam_mq_demo_analysis.md} §5). Owned by stream S-22;
 * consumed by S-20's authorization processing and any later MQ use.
 * <p>
 * <b>Contract (do not change without updating the plan doc — S-20 depends on it):</b>
 * <ul>
 *   <li>Queues are named by string (see {@link MqQueues}); each maps to an unbounded
 *       {@code BlockingQueue<Message>}. {@code open(name)} is MQOPEN — an idempotent
 *       queue lookup; {@code close(name)} is MQCLOSE — a no-op in-process.</li>
 *   <li>{@link #put} is MQPUT <i>and</i> MQPUT1 (open-once and one-shot collapse to the
 *       same call). A put with no {@code msgId} is assigned one, as a real queue manager
 *       does. Delivery is at-most-once demo semantics (S22-B5): the message is offered
 *       once; a poll either takes it or it stays.</li>
 *   <li>{@link #poll} is MQGET with MQGMO-WAIT: it waits up to the wait interval
 *       (default {@value #DEFAULT_GET_WAIT_MILLIS} ms = MQGMO-WAITINTERVAL 5000) and
 *       returns {@code null} on timeout — the MQRC-NO-MSG-AVAILABLE return.</li>
 *   <li>{@link #registerConsumer} is the MQTM trigger-monitor mapping (S22-B3): the
 *       handler runs on a drain loop — poll, handle, repeat — and the loop
 *       <b>exits after one empty 5s poll</b>, matching the COBOL task that drains its
 *       input queue then EXEC CICS RETURNs. If a later {@link #put} lands while no
 *       drain is live, a new drain is spawned (trigger-on-first semantics), so a
 *       requester can always put a request and get it processed.</li>
 *   <li>Reply routing is the consumer's business, not the seam's: S-22 consumers put to
 *       their hardcoded reply queues and ignore {@code replyTo} (preserved quirk);
 *       S-20-style consumers reply on {@code message.replyTo()} directly. Both work —
 *       the seam carries {@code replyTo} in the envelope and routes nothing itself.</li>
 *   <li>A handler that throws {@link MqProcessingException} (or any failure) terminates
 *       its drain loop — the COBOL 8000-TERMINATION equivalent.</li>
 * </ul>
 * S-20 usage: requesters {@code put("carddemo.pauth.request", msg)} with
 * {@code replyTo} set; the registered consumer reads via the drain loop and replies with
 * {@code put(msg.replyTo(), reply)} echoing {@code msgId}/{@code correlId}.
 */
@Service
public class InProcessMqService {

    /** MQGMO-WAITINTERVAL 5000 ms — the 5s GET wait in COACCT01.cbl:337 / CODATE01.cbl:286. */
    public static final long DEFAULT_GET_WAIT_MILLIS = 5000L;

    private static final Logger log = LoggerFactory.getLogger(InProcessMqService.class);
    private static final SecureRandom MSG_ID_RANDOM = new SecureRandom();

    private final long getWaitMillis;
    private final Map<String, BlockingQueue<Message>> queues = new ConcurrentHashMap<>();
    private final Map<String, Consumer<Message>> consumers = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private final Map<String, Thread> drainThreads = new ConcurrentHashMap<>();

    public InProcessMqService() {
        this(DEFAULT_GET_WAIT_MILLIS);
    }

    /** Creates a seam whose polls/drains use a different wait interval — for fast tests. */
    public InProcessMqService(long getWaitMillis) {
        this.getWaitMillis = getWaitMillis;
    }

    /** MQOPEN: resolves (creating if needed) the named queue. Idempotent. */
    public void open(String queueName) {
        queue(queueName);
    }

    /** MQCLOSE: no-op for an in-process queue. */
    public void close(String queueName) {
        // Handles are implicit in-process; closing never fails. (S22-B1)
    }

    /**
     * MQPUT/MQPUT1: enqueues the message. A null/blank {@code msgId} is replaced with a
     * generated 24-hex id and the effective message is returned. Throws {@link MqException}
     * on failure (queue name invalid). A put on a queue with a registered consumer whose
     * drain has exited spawns a fresh drain (MQ trigger-monitor, trigger-on-first).
     */
    public Message put(String queueName, Message message) {
        BlockingQueue<Message> target = queue(queueName);
        Objects.requireNonNull(message, "message");
        Message effective = message.msgId() == null || message.msgId().isBlank()
                ? message.withMsgId(newMsgId()) : message;
        synchronized (lock(queueName)) {
            target.offer(effective);
            if (consumers.containsKey(queueName) && !drainThreads.containsKey(queueName)) {
                launchDrainLocked(queueName);
            }
        }
        return effective;
    }

    /** MQGET with the default 5s wait; {@code null} = MQRC-NO-MSG-AVAILABLE. */
    public Message poll(String queueName) {
        return poll(queueName, getWaitMillis);
    }

    /** MQGET with an explicit wait interval; {@code null} = MQRC-NO-MSG-AVAILABLE. */
    public Message poll(String queueName, long waitMillis) {
        try {
            return queue(queueName).poll(Math.max(waitMillis, 0), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MqException(queueName, "MQGET interrupted", e);
        }
    }

    /**
     * MQTM RETRIEVE + trigger monitor: registers {@code handler} as the consumer of
     * {@code triggerQueue} and spawns its drain loop. The drain polls with the GET wait
     * interval and exits after one empty poll (S22-B3, drain-and-exit); {@link #put}
     * respawns it on demand. Registering twice replaces the handler. Returns the drain
     * thread so callers can join it.
     */
    public Thread registerConsumer(String triggerQueue, Consumer<Message> handler) {
        Objects.requireNonNull(handler, "handler");
        consumers.put(triggerQueue, handler);
        synchronized (lock(triggerQueue)) {
            Thread live = drainThreads.get(triggerQueue);
            if (live != null) {
                return live;
            }
            return launchDrainLocked(triggerQueue);
        }
    }

    /**
     * The drain loop, run synchronously in the caller's thread (used by the spawned drain
     * threads and directly by tests): handle messages until one empty poll.
     * A handler throwing {@link MqProcessingException} stops the loop — that is the
     * program's 8000-TERMINATION; any other handler failure is logged and also stops it.
     */
    public void drain(String queueName, Consumer<Message> handler) {
        drain(queueName, handler, getWaitMillis);
    }

    /** {@link #drain} with an explicit empty-poll wait. */
    public void drain(String queueName, Consumer<Message> handler, long waitMillis) {
        BlockingQueue<Message> source = queue(queueName);
        while (true) {
            Message message;
            try {
                message = source.poll(Math.max(waitMillis, 0), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (message == null) {
                synchronized (lock(queueName)) {
                    message = source.poll();
                    if (message == null) {
                        drainThreads.remove(queueName);
                        return;
                    }
                }
            }
            try {
                handler.accept(message);
            } catch (RuntimeException e) {
                if (!(e instanceof MqProcessingException)) {
                    log.error("Consumer on queue {} failed: {}", queueName, e.toString());
                }
                synchronized (lock(queueName)) {
                    drainThreads.remove(queueName);
                }
                return;
            }
        }
    }

    /** Current queue depth — diagnostic helper (MQINQ-equivalent). */
    public int depth(String queueName) {
        return queue(queueName).size();
    }

    private BlockingQueue<Message> queue(String queueName) {
        if (queueName == null || queueName.isBlank()) {
            throw new MqException(String.valueOf(queueName), "blank queue name");
        }
        return queues.computeIfAbsent(queueName, k -> new LinkedBlockingQueue<>());
    }

    private Object lock(String queueName) {
        return locks.computeIfAbsent(queueName, k -> new Object());
    }

    private Thread launchDrainLocked(String queueName) {
        Thread drain = new Thread(() -> drain(queueName, consumers.get(queueName)),
                "mq-drain-" + queueName);
        drain.setDaemon(true);
        drainThreads.put(queueName, drain);
        drain.start();
        return drain;
    }

    private static String newMsgId() {
        byte[] bytes = new byte[12]; // 24 hex chars, like a 24-byte MQMD-MSGID
        MSG_ID_RANDOM.nextBytes(bytes);
        StringBuilder id = new StringBuilder(24);
        for (byte b : bytes) {
            id.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return id.toString();
    }
}
