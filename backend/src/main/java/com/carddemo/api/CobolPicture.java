package com.carddemo.api;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * PICTURE-accurate rendering of the fields map {@code CACTVWA} shows (app/bms/COACTVW.bms).
 * The BMS {@code PICOUT} clauses and the COBOL {@code STRING}/{@code MOVE} statements are the
 * specification; nothing here rounds, localises or prettifies beyond what the picture does.
 */
public final class CobolPicture {

    /** Integer digit positions of {@code +ZZZ,ZZZ,ZZZ.99} (COACTVW.bms:120, :141, :162, :174, :195). */
    private static final int MONEY_INTEGER_DIGITS = 9;

    private CobolPicture() {
    }

    /**
     * {@code PICOUT='+ZZZ,ZZZ,ZZZ.99'} (15 bytes): fixed leading sign, zero suppression of the nine
     * integer positions with the group separators inside the suppressed run blanked, two unsuppressed
     * decimal positions. A value wider than nine integer digits loses the high-order digits, as a
     * COBOL {@code MOVE} of {@code S9(10)V99} into this field does.
     */
    public static String money(BigDecimal value) {
        BigDecimal scaled = (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.DOWN);
        char sign = scaled.signum() < 0 ? '-' : '+';
        BigDecimal absolute = scaled.abs();
        String digits = absolute.unscaledValue().toString();
        digits = "0".repeat(Math.max(0, MONEY_INTEGER_DIGITS + 2 - digits.length())) + digits;
        digits = digits.substring(digits.length() - (MONEY_INTEGER_DIGITS + 2));

        String integerDigits = digits.substring(0, MONEY_INTEGER_DIGITS);
        String decimalDigits = digits.substring(MONEY_INTEGER_DIGITS);

        StringBuilder edited = new StringBuilder(15).append(sign);
        boolean significant = false;
        for (int i = 0; i < MONEY_INTEGER_DIGITS; i++) {
            if (i == 3 || i == 6) {
                edited.append(significant ? ',' : ' ');
            }
            char digit = integerDigits.charAt(i);
            significant = significant || digit != '0';
            edited.append(significant ? digit : ' ');
        }
        return edited.append('.').append(decimalDigits).toString();
    }

    /**
     * {@code STRING CUST-SSN(1:3) '-' CUST-SSN(4:2) '-' CUST-SSN(6:4)} over {@code PIC 9(09)}
     * (COACTVWC.cbl:496-504): the stored number is always nine digits on the screen.
     */
    public static String ssn(Long ssn) {
        if (ssn == null) {
            return "";
        }
        String digits = digits(ssn, 9);
        return digits.substring(0, 3) + '-' + digits.substring(3, 5) + '-' + digits.substring(5, 9);
    }

    /** A {@code PIC 9(n)} key as the screen carries it: zero-padded, never grouped. */
    public static String digits(Long value, int width) {
        if (value == null) {
            return "";
        }
        String text = Long.toString(Math.abs(value));
        if (text.length() >= width) {
            return text.substring(text.length() - width);
        }
        return "0".repeat(width - text.length()) + text;
    }

    /**
     * The account and customer date fields are {@code X(10)} text the program moves unvalidated
     * (COACTVWC.cbl:487-489, :507); the wave-1 Q-10 gate stored them as {@code DATE}, so the screen
     * text is the ISO rendering of the stored value (D-0037).
     */
    public static String date(java.time.LocalDate value) {
        return value == null ? "" : value.toString();
    }

    /** {@code PIC X(n)} display: trailing pad blanks are not part of the value the screen shows. */
    public static String text(String value) {
        if (value == null) {
            return "";
        }
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(0, end);
    }
}
