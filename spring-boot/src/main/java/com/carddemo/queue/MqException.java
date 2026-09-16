package com.carddemo.queue;

/**
 * Failure of an in-process MQ operation (put/poll) — the MQCC/MQRC non-OK return
 * of the COBOL API calls, surfaced as a Java exception (S22-B1).
 */
public class MqException extends RuntimeException {

    private final String queueName;

    public MqException(String queueName, String detail) {
        super(detail + " (queue " + queueName + ")");
        this.queueName = queueName;
    }

    public MqException(String queueName, String detail, Throwable cause) {
        super(detail + " (queue " + queueName + ")", cause);
        this.queueName = queueName;
    }

    public String queueName() {
        return queueName;
    }
}
