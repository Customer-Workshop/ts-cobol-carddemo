package com.carddemo.data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Reads the display-format COBOL fields used by the CardDemo ASCII exports.
 */
public final class CobolFieldReader {

    private CobolFieldReader() {
    }

    public static String text(String record, int offset, int length) {
        String value = field(record, offset, length).stripTrailing();
        return value.isBlank() ? null : value;
    }

    public static long unsignedLong(String record, int offset, int length) {
        String value = text(record, offset, length);
        if (value == null) {
            return 0L;
        }
        if (!value.matches("\\d+")) {
            throw new IllegalArgumentException("Invalid unsigned COBOL number: " + value);
        }
        return Long.parseLong(value);
    }

    public static BigDecimal signedDecimal(String record, int offset, int digits, int scale) {
        String value = field(record, offset, digits + scale);
        if (value.isBlank()) {
            return null;
        }
        return signedDecimal(value, scale);
    }

    public static BigDecimal signedDecimal(String value, int scale) {
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        char signCharacter = trimmed.charAt(trimmed.length() - 1);
        int sign = 1;
        char lastDigit = signCharacter;
        if (signCharacter == '{') {
            lastDigit = '0';
        } else if (signCharacter == '}') {
            sign = -1;
            lastDigit = '0';
        } else if (signCharacter >= 'A' && signCharacter <= 'I') {
            lastDigit = (char) ('1' + signCharacter - 'A');
        } else if (signCharacter >= 'J' && signCharacter <= 'R') {
            sign = -1;
            lastDigit = (char) ('1' + signCharacter - 'J');
        }
        if (!Character.isDigit(lastDigit) || !trimmed.substring(0, trimmed.length() - 1).matches("\\d*")) {
            throw new IllegalArgumentException("Invalid signed COBOL number: " + value);
        }
        String digits = trimmed.substring(0, trimmed.length() - 1) + lastDigit;
        BigDecimal result = new BigDecimal(digits).setScale(scale, RoundingMode.UNNECESSARY);
        return sign < 0 ? result.negate() : result;
    }

    private static String field(String record, int offset, int length) {
        if (offset < 0 || length < 0) {
            throw new IllegalArgumentException("Offset and length must be non-negative");
        }
        if (record == null || offset >= record.length()) {
            return " ".repeat(length);
        }
        int end = Math.min(record.length(), offset + length);
        return record.substring(offset, end) + " ".repeat(offset + length - end);
    }
}
