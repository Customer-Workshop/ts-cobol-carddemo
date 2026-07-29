package com.carddemo.cbact04c.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Encodes the DISPLAY/zoned decimal fields used by the CardDemo ASCII exports.
 */
public final class ZonedDecimal {

    private ZonedDecimal() {
    }

    public static BigDecimal parse(String value) {
        if (value == null || value.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }

        char last = value.charAt(value.length() - 1);
        int sign = 1;
        int digit;
        if (last == '{') {
            digit = 0;
        } else if (last >= 'A' && last <= 'I') {
            digit = last - 'A' + 1;
        } else if (last == '}') {
            sign = -1;
            digit = 0;
        } else if (last >= 'J' && last <= 'R') {
            sign = -1;
            digit = last - 'J' + 1;
        } else if (Character.isDigit(last)) {
            digit = last - '0';
        } else {
            throw new IllegalArgumentException("Invalid zoned decimal: " + value);
        }

        String digits = value.substring(0, value.length() - 1) + digit;
        return new BigDecimal(digits)
                .movePointLeft(2)
                .multiply(BigDecimal.valueOf(sign))
                .setScale(2);
    }

    public static String format(BigDecimal value, int integerDigits) {
        BigDecimal scaled = value.setScale(2, RoundingMode.DOWN);
        boolean negative = scaled.signum() < 0;
        String digits = scaled.abs().movePointRight(2).toBigInteger().toString();
        int fieldLength = integerDigits + 2;
        if (digits.length() > fieldLength) {
            throw new ArithmeticException(
                    "Value " + value + " exceeds PIC S9(" + integerDigits + ")V99");
        }

        digits = "0".repeat(fieldLength - digits.length()) + digits;
        int lastIndex = digits.length() - 1;
        int lastDigit = digits.charAt(lastIndex) - '0';
        char overpunch;
        if (negative) {
            overpunch = lastDigit == 0 ? '}' : (char) ('I' + lastDigit);
        } else {
            overpunch = lastDigit == 0 ? '{' : (char) ('@' + lastDigit);
        }
        return digits.substring(0, lastIndex) + overpunch;
    }
}
