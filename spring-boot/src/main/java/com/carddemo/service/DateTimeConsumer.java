package com.carddemo.service;

import com.carddemo.data.CobolFieldFormatter;
import com.carddemo.queue.Message;
import com.carddemo.queue.MqErrorRecord;
import com.carddemo.queue.MqException;
import com.carddemo.queue.MqProcessingException;
import com.carddemo.queue.MqQueues;
import com.carddemo.queue.InProcessMqService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Java port of CODATE01 — the CDRD trigger-started MQ consumer for the date/time echo
 * demo (stream S-22). Drains {@link MqQueues#REQUEST_DATE}; the request payload is
 * ignored entirely (:339-361) and every message gets the same formatted reply on the
 * hardcoded reply queue {@link MqQueues#REPLY_DATE}; failures land on
 * {@link MqQueues#ERROR}.
 * <p>
 * Preserved quirks (do not "fix"; cites in {@code app/app-vsam-mq/cbl/CODATE01.cbl}):
 * reply queue hardcoded 'CARD.DEMO.REPLY.DATE' (:147, request replyTo unused); reply
 * echoes request msgId/correlId (:373-374); 'SYSTEM DATE : ' and 'SYSTEM TIME : '
 * run together with no space (:355-356).
 */
@Service
public class DateTimeConsumer {

    /** FORMATTIME MMDDYYYY(DATESEP '-') (:347-350). */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM-dd-yyyy");
    /** FORMATTIME TIME(TIMESEP ':') (:351-352). */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int BUFFER_LENGTH = 1000;      // REPLY-MESSAGE X(1000)

    private static final Logger log = LoggerFactory.getLogger(DateTimeConsumer.class);

    private final InProcessMqService mq;

    public DateTimeConsumer(InProcessMqService mq) {
        this.mq = mq;
    }

    /** Opens the program's three queues and registers this consumer (MQOPEN x3 + trigger). */
    public void register() {
        mq.open(MqQueues.ERROR);
        mq.open(MqQueues.REQUEST_DATE);
        mq.open(MqQueues.REPLY_DATE);
        mq.registerConsumer(MqQueues.REQUEST_DATE, this::processRequest);
    }

    /**
     * 4000-PROCESS-REQUEST-REPLY (:339-361): ASKTIME + FORMATTIME then STRING the reply.
     * Request content is not inspected.
     */
    public void processRequest(Message request) {
        LocalDateTime now = LocalDateTime.now();
        String replyText = "SYSTEM DATE : " + DATE_FMT.format(now)
                + "SYSTEM TIME : " + TIME_FMT.format(now);
        putReply(request, replyText);
    }

    /** 4100-PUT-REPLY (:366-390): MQPUT on the hardcoded reply queue, ids echoed. */
    private void putReply(Message request, String replyText) {
        Message reply = new Message(request.msgId(), request.correlId(), null,
                Message.FORMAT_STRING, CobolFieldFormatter.pad(replyText, BUFFER_LENGTH));
        try {
            mq.put(MqQueues.REPLY_DATE, reply);
        } catch (MqException e) {
            // (:399-402): the queue slot holds REPLY-QUEUE-NAME, not the input queue
            putError(request, "MQPUT ERR", MqQueues.REPLY_DATE);
            throw new MqProcessingException("Reply MQPUT failed on " + MqQueues.REPLY_DATE, e);
        }
    }

    /** 9000-ERROR (:405-440): MQ-ERR-DISPLAY block on CARD.DEMO.ERROR. */
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
