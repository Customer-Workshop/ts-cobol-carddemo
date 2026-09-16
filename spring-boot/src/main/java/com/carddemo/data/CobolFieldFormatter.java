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
     * Renders a trailing-sign edited numeric field — the {@code 9(i).<s digits>-}
     * and {@code Z(i).<s digits>-} pictures used in report/statement layouts
     * (e.g. CBSTM03A's ST-CURR-BAL 9(9).99- and ST-TRANAMT/ST-TOTAL-TRAMT
     * Z(9).99-). Integer digits are zero-filled ({@code 9}) or zero-suppressed
     * to spaces ({@code Z}), the decimal point is fixed, and the sign prints as
     * a trailing '-' for negatives, a space otherwise. High-order overflow keeps
     * the low-order digits (COBOL numeric-move truncation); extra fraction
     * digits truncate toward zero.
     */
    public static String trailingSign(BigDecimal value, int integerDigits, int scale,
                                      boolean zeroSuppress) {
        BigDecimal scaled = (value == null ? BigDecimal.ZERO : value)
                .setScale(scale, RoundingMode.DOWN);
        boolean negative = scaled.signum() < 0;
        String digits = scaled.unscaledValue().abs().toString();
        int width = integerDigits + scale;
        if (digits.length() > width) {
            digits = digits.substring(digits.length() - width);
        } else {
            digits = "0".repeat(width - digits.length()) + digits;
        }
        String integer = digits.substring(0, integerDigits);
        if (zeroSuppress) {
            int leading = 0;
            while (leading < integer.length() && integer.charAt(leading) == '0') {
                leading++;
            }
            integer = " ".repeat(leading) + integer.substring(leading);
        }
        return integer + "." + digits.substring(integerDigits) + (negative ? "-" : " ");
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
