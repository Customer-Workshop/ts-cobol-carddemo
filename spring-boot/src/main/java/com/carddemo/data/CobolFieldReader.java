package com.carddemo.data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

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

    public static Long optionalUnsignedLong(String record, int offset, int length) {
        String value = text(record, offset, length);
        if (value == null) {
            return null;
        }
        if (!value.matches("\\d+")) {
            throw new IllegalArgumentException("Invalid unsigned COBOL number: " + value);
        }
        return Long.parseLong(value);
    }

    public static long unsignedLong(String record, int offset, int length) {
        Long value = optionalUnsignedLong(record, offset, length);
        if (value == null) {
            throw new IllegalArgumentException("Blank unsigned COBOL number");
        }
        return value;
    }

    public static long requiredUnsignedLong(String record, int offset, int length,
                                            String sourceName, int recordNumber) {
        String value = text(record, offset, length);
        if (value == null) {
            throw invalidRequiredNumber(sourceName, recordNumber, "blank");
        }
        if (!value.matches("\\d+")) {
            throw invalidRequiredNumber(sourceName, recordNumber, "invalid value '" + value + "'");
        }
        return Long.parseLong(value);
    }

    public static String requiredText(String record, int offset, int length,
                                      String sourceName, int recordNumber) {
        String value = text(record, offset, length);
        if (value == null) {
            throw new IllegalArgumentException("Missing required text field in " + sourceName
                    + " record " + recordNumber);
        }
        return value;
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
        BigDecimal result = new BigDecimal(new BigInteger(digits), scale);
        return sign < 0 ? result.negate() : result;
    }

    public static List<String> splitRecords(String contents, int recordLength) {
        if (contents == null || recordLength <= 0) {
            throw new IllegalArgumentException("Contents and record length must be valid");
        }
        if (contents.indexOf('\n') < 0 && contents.indexOf('\r') < 0) {
            if (contents.length() % recordLength != 0) {
                throw new IllegalArgumentException("Fixed-width content length " + contents.length()
                        + " is not a multiple of record length " + recordLength);
            }
            List<String> records = new ArrayList<>();
            for (int offset = 0; offset < contents.length(); offset += recordLength) {
                records.add(contents.substring(offset, offset + recordLength));
            }
            return records;
        }
        return contents.lines().toList();
    }

    private static IllegalArgumentException invalidRequiredNumber(
            String sourceName, int recordNumber, String reason) {
        return new IllegalArgumentException("Required numeric field in " + sourceName
                + " record " + recordNumber + " is " + reason);
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
