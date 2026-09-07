package com.carddemo.data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Fixed-width COBOL record field extraction for the app/data extracts (adapted from the reference module).
 * Offsets are 0-based byte positions of the copybook fields; the ASCII extracts carry zoned-decimal
 * overpunch signs in the last digit of signed fields ('{' +0, '}' -0, 'A'-'I' +1..+9, 'J'-'R' -1..-9).
 */
public final class CobolFieldReader {

    private CobolFieldReader() {
    }

    /** PIC X(n): the field with trailing spaces removed; {@code null} when blank. */
    public static String text(String record, int offset, int length) {
        String raw = slice(record, offset, length);
        int end = raw.length();
        while (end > 0 && raw.charAt(end - 1) == ' ') {
            end--;
        }
        return end == 0 ? null : raw.substring(0, end);
    }

    /** PIC X(n) exactly as stored, right-padded with spaces to {@code length}. */
    public static String rawText(String record, int offset, int length) {
        return slice(record, offset, length);
    }

    public static String requiredText(String record, int offset, int length, String sourceName, int recordNumber) {
        String value = text(record, offset, length);
        if (value == null) {
            throw new ImportFormatException(sourceName, recordNumber, offset, "required text field is blank");
        }
        return value;
    }

    /** PIC 9(n) unsigned; {@code null} when the field is blank. */
    public static Long optionalUnsignedLong(String record, int offset, int length) {
        String value = text(record, offset, length);
        if (value == null) {
            return null;
        }
        String digits = value.trim();
        if (!isDigits(digits)) {
            return null;
        }
        return Long.parseLong(digits);
    }

    public static long requiredUnsignedLong(String record, int offset, int length, String sourceName, int recordNumber) {
        String value = text(record, offset, length);
        if (value == null || !isDigits(value.trim())) {
            throw new ImportFormatException(sourceName, recordNumber, offset, "required numeric field is not numeric");
        }
        return Long.parseLong(value.trim());
    }

    /** PIC S9(digits-scale)V9(scale) zoned decimal with overpunched sign in the last digit. */
    public static BigDecimal signedDecimal(String record, int offset, int digits, int scale) {
        return signedDecimal(slice(record, offset, digits), scale);
    }

    public static BigDecimal signedDecimal(String value, int scale) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String field = value.trim();
        char last = field.charAt(field.length() - 1);
        boolean negative = false;
        char lastDigit;
        if (last == '{') {
            lastDigit = '0';
        } else if (last == '}') {
            lastDigit = '0';
            negative = true;
        } else if (last >= 'A' && last <= 'I') {
            lastDigit = (char) ('1' + (last - 'A'));
        } else if (last >= 'J' && last <= 'R') {
            lastDigit = (char) ('1' + (last - 'J'));
            negative = true;
        } else if (Character.isDigit(last)) {
            lastDigit = last;
        } else {
            throw new IllegalArgumentException("Invalid zoned-decimal sign character");
        }
        String digits = field.substring(0, field.length() - 1) + lastDigit;
        if (!isDigits(digits)) {
            throw new IllegalArgumentException("Invalid zoned-decimal digits");
        }
        BigInteger unscaled = new BigInteger(digits);
        if (negative) {
            unscaled = unscaled.negate();
        }
        return new BigDecimal(unscaled, scale);
    }

    /** Splits a fixed-length dataset image (no record separators) into records, padding a short tail. */
    public static List<String> splitRecords(String contents, int recordLength) {
        List<String> records = new ArrayList<>();
        for (int offset = 0; offset < contents.length(); offset += recordLength) {
            int end = Math.min(contents.length(), offset + recordLength);
            String record = contents.substring(offset, end);
            if (record.length() < recordLength) {
                record = record + " ".repeat(recordLength - record.length());
            }
            records.add(record);
        }
        return records;
    }

    private static String slice(String record, int offset, int length) {
        if (record == null) {
            return " ".repeat(length);
        }
        if (offset >= record.length()) {
            return " ".repeat(length);
        }
        int end = Math.min(record.length(), offset + length);
        String raw = record.substring(offset, end);
        if (raw.length() < length) {
            raw = raw + " ".repeat(length - raw.length());
        }
        return raw;
    }

    private static boolean isDigits(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
