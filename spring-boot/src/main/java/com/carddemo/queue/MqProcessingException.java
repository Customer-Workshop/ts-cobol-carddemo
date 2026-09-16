package com.carddemo.queue;

/**
 * Thrown by a consumer to terminate its drain loop — the Java equivalent of the
 * COBOL programs' 8000-TERMINATION (close queues, EXEC CICS RETURN) after an
 * unrecoverable error path such as 'INP MQGET ERR:', 'MQPUT ERR' or
 * 'ERROR WHILE READING ACCTFILE'.
 */
public class MqProcessingException extends RuntimeException {

    public MqProcessingException(String detail) {
        super(detail);
    }

    public MqProcessingException(String detail, Throwable cause) {
        super(detail, cause);
    }
}
