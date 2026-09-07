package com.carddemo.service;

import org.springframework.stereotype.Service;

import java.text.ParsePosition;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;

/**
 * Port of CSUTLDTC (app/cbl/CSUTLDTC.cbl), boundary B-0014.
 *
 * <p>The COBOL delegates the actual check to the Language Environment service CEEDAYS
 * (CSUTLDTC.cbl:116-120) and maps its 12-byte feedback token to a severity, message number and
 * 15-character reason text (CSUTLDTC.cbl:60-73, :122-149). CEEDAYS is not available off-host
 * (boundary B-0015, deferred with re-entry condition); it is substituted by {@code java.time}
 * with {@link ResolverStyle#STRICT}. Feedback mapping status (FR §5, D-0028):
 * <ul>
 *   <li>{@code 0000 Date is valid} and {@code 2513 Unsupp. Range} are contractual (callers branch on them);</li>
 *   <li>{@code 2507, 2508, 2517, 2518, 2520} are INFERRED from distinguishable {@code java.time} failures;</li>
 *   <li>{@code 2509 Invalid Era} and {@code 2521 YearInEra is 0} are Japanese-era conditions, unreachable
 *       with Gregorian masks (FR A-DTC-2) and therefore never produced;</li>
 *   <li>anything else falls back to {@code Date is invalid} as the COBOL WHEN OTHER does (CSUTLDTC.cbl:146-147).</li>
 * </ul>
 * Pure function: no I/O, no state, never throws for an invalid date; only a {@code null} or over-long
 * argument is a programming error.
 */
@Service
public class DateValidationService {

    /** LS-DATE / LS-DATE-FORMAT are PIC X(10) (CSUTLDTC.cbl:84-85). */
    static final int LINKAGE_LENGTH = 10;

    /** Lilian day 1 = 15 October 1582; CEEDAYS supports up to 31 December 9999 (INFERRED, FR A-DTC-3). */
    static final LocalDate LILIAN_EPOCH = LocalDate.of(1582, 10, 15);
    static final LocalDate LILIAN_MAX = LocalDate.of(9999, 12, 31);

    /** Message number used with the WHEN OTHER text when no feedback token is classified (service-defined). */
    static final String UNCLASSIFIED_MESSAGE_NUMBER = "9999";

    private static final int SEVERITY_OK = 0;
    private static final int SEVERITY_ERROR = 3;

    /** Feedback catalogue, CSUTLDTC.cbl:61-73 (constants) and :128-147 (texts, 15 characters each). */
    enum Feedback {
        VALID("0000", "Date is valid  "),
        INSUFFICIENT_DATA("2507", "Insufficient   "),
        BAD_DATE_VALUE("2508", "Datevalue error"),
        INVALID_ERA("2509", "Invalid Era    "),
        UNSUPPORTED_RANGE("2513", "Unsupp. Range  "),
        INVALID_MONTH("2517", "Invalid month  "),
        BAD_PICTURE_STRING("2518", "Bad Pic String "),
        NON_NUMERIC_DATA("2520", "Nonnumeric data"),
        YEAR_IN_ERA_ZERO("2521", "YearInEra is 0 "),
        OTHER(UNCLASSIFIED_MESSAGE_NUMBER, "Date is invalid");

        final String messageNumber;
        final String reasonText;

        Feedback(String messageNumber, String reasonText) {
            this.messageNumber = messageNumber;
            this.reasonText = reasonText;
        }

        int severity() {
            return this == VALID ? SEVERITY_OK : SEVERITY_ERROR;
        }
    }

    public DateValidationResult validate(DateValidationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        String date = requireLinkage(request.date(), "date");
        String mask = requireLinkage(request.mask(), "mask");

        Feedback feedback = classify(stripTrailingSpaces(date), stripTrailingSpaces(mask));
        return new DateValidationResult(
                feedback.severity(), feedback.messageNumber, feedback.reasonText, format(feedback, date, mask));
    }

    private static String requireLinkage(String value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        if (value.length() > LINKAGE_LENGTH) {
            throw new IllegalArgumentException(name + " exceeds PIC X(" + LINKAGE_LENGTH + ")");
        }
        return value;
    }

    /** CEEDAYS substitute (B-0015). */
    private static Feedback classify(String date, String mask) {
        DateTimeFormatter formatter;
        try {
            formatter = CeedaysPicture.toFormatter(mask);
        } catch (IllegalArgumentException badPicture) {
            return Feedback.BAD_PICTURE_STRING;
        }
        if (date.isEmpty() || date.length() < CeedaysPicture.minimumLength(mask)) {
            return Feedback.INSUFFICIENT_DATA;
        }

        ParsePosition position = new ParsePosition(0);
        TemporalAccessor raw = formatter.parseUnresolved(date, position);
        if (raw == null || position.getErrorIndex() >= 0) {
            int at = position.getErrorIndex() >= 0 ? position.getErrorIndex() : 0;
            if (at < date.length() && !Character.isDigit(date.charAt(at))) {
                return Feedback.NON_NUMERIC_DATA;
            }
            return Feedback.OTHER;
        }
        if (position.getIndex() != date.length()) {
            return Feedback.OTHER;
        }
        if (raw.isSupported(ChronoField.MONTH_OF_YEAR)) {
            long month = raw.getLong(ChronoField.MONTH_OF_YEAR);
            if (month < 1 || month > 12) {
                return Feedback.INVALID_MONTH;
            }
        }

        LocalDate parsed;
        try {
            parsed = LocalDate.parse(date, formatter);
        } catch (DateTimeException impossibleDate) {
            return Feedback.BAD_DATE_VALUE;
        }
        if (parsed.isBefore(LILIAN_EPOCH) || parsed.isAfter(LILIAN_MAX)) {
            return Feedback.UNSUPPORTED_RANGE;
        }
        return Feedback.VALID;
    }

    /**
     * WS-MESSAGE layout, CSUTLDTC.cbl:42-57. Bytes 46-55 carry the date exactly as passed:
     * deliberate deviation DV-04 — the COBOL overwrites WS-DATE with the VSTRING group at :122.
     */
    private static String format(Feedback feedback, String date, String mask) {
        StringBuilder out = new StringBuilder(80);
        out.append(String.format("%04d", feedback.severity()));
        out.append(padRight("Mesg Code:", 11));
        out.append(padRight(feedback.messageNumber, 4));
        out.append(' ');
        out.append(padRight(feedback.reasonText, 15));
        out.append(' ');
        out.append(padRight("TstDate:", 9));
        out.append(padRight(date, 10));
        out.append(' ');
        out.append(padRight("Mask used:", 10));
        out.append(padRight(mask, 10));
        out.append(' ');
        out.append("   ");
        return out.toString();
    }

    private static String padRight(String value, int width) {
        if (value.length() >= width) {
            return value.substring(0, width);
        }
        return value + " ".repeat(width - value.length());
    }

    private static String stripTrailingSpaces(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(0, end);
    }

    /**
     * Translation of a CEEDAYS picture string (YYYY, YY, MM, DD, DDD, MMM/Mmm plus separators) into a
     * strict {@link DateTimeFormatter}. Any other token is a bad picture string (INFERRED -> 2518).
     */
    static final class CeedaysPicture {

        private CeedaysPicture() {
        }

        static DateTimeFormatter toFormatter(String mask) {
            if (mask.isEmpty()) {
                throw new IllegalArgumentException("empty picture");
            }
            DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder().parseCaseInsensitive();
            boolean hasYear = false;
            boolean hasMonthOrDayOfYear = false;
            boolean hasDay = false;
            int i = 0;
            while (i < mask.length()) {
                char c = mask.charAt(i);
                if (mask.startsWith("YYYY", i)) {
                    builder.appendValue(ChronoField.YEAR, 4);
                    hasYear = true;
                    i += 4;
                } else if (mask.startsWith("YY", i)) {
                    builder.appendValueReduced(ChronoField.YEAR, 2, 2, 1900);
                    hasYear = true;
                    i += 2;
                } else if (mask.startsWith("MMM", i) || mask.startsWith("Mmm", i) || mask.startsWith("mmm", i)) {
                    builder.appendPattern("MMM");
                    hasMonthOrDayOfYear = true;
                    i += 3;
                } else if (mask.startsWith("MM", i)) {
                    builder.appendValue(ChronoField.MONTH_OF_YEAR, 2);
                    hasMonthOrDayOfYear = true;
                    i += 2;
                } else if (mask.startsWith("DDD", i)) {
                    builder.appendValue(ChronoField.DAY_OF_YEAR, 3);
                    hasMonthOrDayOfYear = true;
                    hasDay = true;
                    i += 3;
                } else if (mask.startsWith("DD", i)) {
                    builder.appendValue(ChronoField.DAY_OF_MONTH, 2);
                    hasDay = true;
                    i += 2;
                } else if (c == '-' || c == '/' || c == '.' || c == ',' || c == ' ' || c == ':') {
                    builder.appendLiteral(c);
                    i++;
                } else {
                    throw new IllegalArgumentException("unsupported picture token at " + i);
                }
            }
            if (!hasYear || !hasMonthOrDayOfYear || !hasDay) {
                throw new IllegalArgumentException("picture does not describe a full date");
            }
            return builder.toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT);
        }

        /** Character count the picture needs; shorter input is "Insufficient" (INFERRED -> 2507). */
        static int minimumLength(String mask) {
            return mask.length();
        }
    }
}
