package com.carddemo.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * COBOL PICTURE-accurate display editors used by the screen-state services.
 */
public final class CobolFormat {

    private CobolFormat() {
    }

    // PICOUT '+ZZZ,ZZZ,ZZZ.99' — 15 characters: leading sign (+/-), nine
    // zero-suppressed integer digits whose suppressed commas also blank,
    // fixed decimal point, two fraction digits (ACRDLIM, ACSHLIM, ACURBAL,
    // ACRCYCR, ACRCYDB in COACTVW.bms; FR-S02-08).
    public static String editSignedAmount(BigDecimal value) {
        if (value == null) {
            return "";
        }
        char sign = value.signum() < 0 ? '-' : '+';
        BigDecimal magnitude = value.abs();
        // The edit picture has only nine integer positions: the leading
        // digits of an S9(10)V99 source are dropped on overflow.
        long integer = magnitude.setScale(0, RoundingMode.DOWN).longValue() % 1_000_000_000L;
        int cents = magnitude.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        String digits = "%09d".formatted(integer);
        StringBuilder suppressed = new StringBuilder(11);
        boolean significant = false;
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && i % 3 == 0) {
                suppressed.append(significant ? ',' : ' ');
            }
            char digit = digits.charAt(i);
            if (!significant && digit == '0') {
                suppressed.append(' ');
            } else {
                significant = true;
                suppressed.append(digit);
            }
        }
        return String.valueOf(sign) + suppressed + '.' + "%02d".formatted(cents);
    }

    // X(n) -> X(m) MOVE with m < n: leftmost truncation to the map width.
    public static String truncate(String value, int width) {
        if (value == null) {
            return "";
        }
        return value.length() <= width ? value : value.substring(0, width);
    }

    // CUST-SSN X(9) -> nnn-nn-nnnn (COACTVWC.cbl:496-504; FR-S02-09).
    public static String ssn(Long value) {
        if (value == null) {
            return "";
        }
        String padded = "%09d".formatted(value);
        return padded.substring(0, 3) + '-' + padded.substring(3, 5) + '-' + padded.substring(5);
    }
}
