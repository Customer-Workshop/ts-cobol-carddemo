package com.carddemo.data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Writes display-format COBOL fields — the inverse of {@link CobolFieldReader}.
 * Used when migrated code must emit the same bytes a COBOL program would have
 * produced (fixed-width reply records, report lines).
 */
public final class CobolFieldFormatter {

    private CobolFieldFormatter() {
    }

    /**
     * Renders a signed zoned-decimal {@code PIC S9(integerDigits)V(scale)} USAGE DISPLAY
     * field: {@code integerDigits + scale} digit characters with the sign overpunched
     * into the last digit — positive: '{' for 0, 'A'-'I' for 1-9; negative: '}' for 0,
     * 'J'-'R' for 1-9 (the EBCDIC convention also used in the ASCII seed exports, decoded
     * by {@link CobolFieldReader#signedDecimal}). A value wider than the picture keeps its
     * low-order digits, matching COBOL's truncation on numeric moves.
     */
    public static String signedDecimal(BigDecimal value, int integerDigits, int scale) {
        BigDecimal scaled = (value == null ? BigDecimal.ZERO : value)
                .setScale(scale, RoundingMode.UNNECESSARY);
        String digits = scaled.unscaledValue().abs().toString();
        int width = integerDigits + scale;
        if (digits.length() > width) {
            digits = digits.substring(digits.length() - width);
        } else if (digits.length() < width) {
            digits = "0".repeat(width - digits.length()) + digits;
        }
        int lastDigit = digits.charAt(width - 1) - '0';
        char overpunch;
        if (scaled.signum() < 0) {
            overpunch = lastDigit == 0 ? '}' : (char) ('J' + lastDigit - 1);
        } else {
            overpunch = lastDigit == 0 ? '{' : (char) ('A' + lastDigit - 1);
        }
        return digits.substring(0, width - 1) + overpunch;
    }

    /**
     * Renders an alphanumeric {@code PIC X(length)} field: left-justified, space-padded
     * on the right, truncated on the right when overlong (COBOL MOVE semantics).
     */
    public static String pad(String value, int length) {
        String text = value == null ? "" : value;
        if (text.length() >= length) {
            return text.substring(0, length);
        }
        return text + " ".repeat(length - text.length());
    }
}
