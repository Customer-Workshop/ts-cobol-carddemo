package com.carddemo.cbtrn02c.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FixedWidth {
    private FixedWidth() {
    }

    public static String text(String value, int width) {
        String safeValue = value == null ? "" : value;
        return (safeValue + " ".repeat(width)).substring(0, width);
    }

    public static String unsignedNumber(String value, int width) {
        String safeValue = value == null ? "" : value.trim();
        String padded = "0".repeat(width) + safeValue;
        return padded.substring(Math.max(0, padded.length() - width));
    }

    public static String signedNumber(BigDecimal value, int digits) {
        long cents = value.movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();
        boolean negative = cents < 0;
        String raw = Long.toString(Math.abs(cents));
        String padded = "0".repeat(digits) + raw;
        padded = padded.substring(Math.max(0, padded.length() - digits));

        int lastDigit = padded.charAt(digits - 1) - '0';
        char overpunch;
        if (negative) {
            overpunch = lastDigit == 0 ? '}' : (char) ('I' + lastDigit);
        } else {
            overpunch = lastDigit == 0 ? '{' : (char) ('@' + lastDigit);
        }
        return padded.substring(0, digits - 1) + overpunch;
    }

    public static BigDecimal parseSignedNumber(String value, int digits) {
        require(value, digits);
        char lastCharacter = value.charAt(digits - 1);
        boolean negative = false;
        int lastDigit;

        if (lastCharacter == '}') {
            negative = true;
            lastDigit = 0;
        } else if (lastCharacter >= 'J' && lastCharacter <= 'R') {
            negative = true;
            lastDigit = lastCharacter - 'I';
        } else if (lastCharacter == '{') {
            lastDigit = 0;
        } else if (lastCharacter >= 'A' && lastCharacter <= 'I') {
            lastDigit = lastCharacter - '@';
        } else if (Character.isDigit(lastCharacter)) {
            lastDigit = lastCharacter - '0';
        } else {
            throw new IllegalArgumentException("Invalid signed numeric overpunch: " + lastCharacter);
        }

        String digitsOnly = value.substring(0, digits - 1) + lastDigit;
        BigDecimal parsed = new BigDecimal(digitsOnly).movePointLeft(2);
        return negative ? parsed.negate() : parsed;
    }

    public static void require(String value, int expectedLength) {
        if (value == null || value.length() != expectedLength) {
            throw new IllegalArgumentException(
                    "Expected " + expectedLength + " bytes, got "
                            + (value == null ? "null" : value.length()));
        }
    }
}
