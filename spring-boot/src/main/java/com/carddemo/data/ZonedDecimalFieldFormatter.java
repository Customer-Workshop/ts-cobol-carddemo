package com.carddemo.data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Write-side companion to {@link CobolFieldReader}: formats Java values back
 * into the COBOL field images used by the CardDemo datasets.
 *
 * Numeric output uses the same `-fsign=EBCDIC` overpunch convention the
 * reader consumes: positive last digit -> '{' (0) or 'A'-'I' (1-9),
 * negative -> '}' (0) or 'J'-'R' (1-9). COMP-3 fields are emitted as raw
 * packed bytes carried in a String of chars 0x00-0xFF — write them with an
 * ISO-8859-1 writer so each char is one byte.
 */
public final class ZonedDecimalFieldFormatter {

    private ZonedDecimalFieldFormatter() {
    }

    /** PIC X(length): space-padded/truncated text; null -> all spaces. */
    public static String text(String value, int length) {
        String text = value == null ? "" : value;
        if (text.length() >= length) {
            return text.substring(0, length);
        }
        return text + " ".repeat(length - text.length());
    }

    /** PIC 9(digits) DISPLAY: zero-padded digits; null -> all spaces. */
    public static String digits(Number value, int digits) {
        if (value == null) {
            return " ".repeat(digits);
        }
        String text = value.toString();
        if (!text.matches("\\d+")) {
            throw new IllegalArgumentException("Unsigned COBOL field needs digits: " + value);
        }
        if (text.length() > digits) {
            throw new IllegalArgumentException("Value " + value + " exceeds PIC 9(" + digits + ")");
        }
        return "0".repeat(digits - text.length()) + text;
    }

    /**
     * PIC S9(integerDigits)V(scale) DISPLAY (zoned decimal): digits+scale
     * chars with the sign overpunched on the last digit. null -> all spaces
     * (the field content a blank record area would hold).
     */
    public static String zoned(BigDecimal value, int integerDigits, int scale) {
        int width = integerDigits + scale;
        if (value == null) {
            return " ".repeat(width);
        }
        BigInteger unscaled = value.setScale(scale, RoundingMode.DOWN).unscaledValue();
        boolean negative = unscaled.signum() < 0;
        String digits = unscaled.abs().toString();
        if (digits.length() > width) {
            throw new IllegalArgumentException(
                    "Value " + value + " exceeds PIC S9(" + integerDigits + ")V" + scale);
        }
        digits = "0".repeat(width - digits.length()) + digits;
        char last = digits.charAt(digits.length() - 1);
        char overpunch;
        if (!negative) {
            overpunch = last == '0' ? '{' : (char) ('A' + last - '1');
        } else {
            overpunch = last == '0' ? '}' : (char) ('J' + last - '1');
        }
        return digits.substring(0, digits.length() - 1) + overpunch;
    }

    /**
     * PIC S9(integerDigits)V(scale) COMP-3 (packed decimal): returned as a
     * String of chars 0x00-0xFF, one char per byte; sign nibble 0xC positive,
     * 0xD negative. null -> packed zero.
     */
    public static String packed(BigDecimal value, int integerDigits, int scale) {
        int digits = integerDigits + scale;
        BigInteger unscaled = (value == null ? BigDecimal.ZERO : value)
                .setScale(scale, RoundingMode.DOWN).unscaledValue();
        boolean negative = unscaled.signum() < 0;
        String text = unscaled.abs().toString();
        if (text.length() > digits) {
            throw new IllegalArgumentException(
                    "Value " + value + " exceeds PIC S9(" + integerDigits + ")V" + scale + " COMP-3");
        }
        String hex = "0".repeat(digits - text.length()) + text + (negative ? "D" : "C");
        if (hex.length() % 2 == 1) {
            hex = "0" + hex;
        }
        StringBuilder packed = new StringBuilder(hex.length() / 2);
        for (int i = 0; i < hex.length(); i += 2) {
            packed.append((char) Integer.parseInt(hex.substring(i, i + 2), 16));
        }
        return packed.toString();
    }

    /** X(10) date image 'YYYY-MM-DD'; null -> 10 spaces. */
    public static String date10(LocalDate value) {
        if (value == null) {
            return " ".repeat(10);
        }
        return "%04d-%02d-%02d".formatted(value.getYear(), value.getMonthValue(), value.getDayOfMonth());
    }
}
