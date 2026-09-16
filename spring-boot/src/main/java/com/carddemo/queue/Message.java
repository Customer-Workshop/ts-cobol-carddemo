package com.carddemo.queue;

/**
 * In-process MQ message envelope ({@code Message{msgId, correlId, replyTo, format, payload}}),
 * the Java equivalent of MQMD + buffer (S22-B1). {@code msgId} and {@code correlId} are the
 * 24-byte MQMD fields carried as strings; {@code replyTo} mirrors MQMD-REPLYTOQ;
 * {@code format} mirrors MQMD-FORMAT (only {@link #FORMAT_STRING} is used by CardDemo).
 */
public record Message(String msgId, String correlId, String replyTo, String format, String payload) {

    /** MQFMT-STRING ('MQSTR   ' in the MQMD). */
    public static final String FORMAT_STRING = "MQSTR";

    /** Convenience factory for a format-STRING message with no ids or reply routing. */
    public static Message of(String payload) {
        return new Message(null, null, null, FORMAT_STRING, payload);
    }

    public Message withMsgId(String id) {
        return new Message(id, correlId, replyTo, format, payload);
    }
}
