package com.carddemo.queue;

/**
 * Renders the fixed-width {@code MQ-ERR-DISPLAY} block that the demo programs put on
 * the error queue from 9000-ERROR (COACCT01.cbl:58-67,:501-536; CODATE01.cbl:405-440).
 * Layout: paragraph X(25) + X(2) + return-message X(25) + X(2) + condition 9(2) + X(2)
 * + reason 9(5) + X(2) + queue-name X(48) = 113 characters.
 * <p>
 * In the source only the RETRIEVE failure fills the paragraph slot; every other error
 * path leaves it spaces, and overlong return messages truncate to X(25)
 * (e.g. 'ERROR WHILE READING ACCTFILE' lands as 'ERROR WHILE READING ACCTFI').
 * Both quirks are preserved here.
 */
public final class MqErrorRecord {

    /** Width of the rendered block. */
    public static final int LENGTH = 113;

    private MqErrorRecord() {
    }

    public static String build(String paragraph, String returnMessage,
                               int conditionCode, int reasonCode, String queueName) {
        return slot(paragraph, 25) + "  "
                + slot(returnMessage, 25) + "  "
                + digits(conditionCode, 2) + "  "
                + digits(reasonCode, 5) + "  "
                + slot(queueName, 48);
    }

    private static String slot(String value, int width) {
        String text = value == null ? "" : value;
        if (text.length() >= width) {
            return text.substring(0, width);
        }
        return text + " ".repeat(width - text.length());
    }

    private static String digits(int value, int width) {
        String text = String.valueOf(value);
        if (text.length() >= width) {
            return text.substring(text.length() - width);
        }
        return "0".repeat(width - text.length()) + text;
    }
}
