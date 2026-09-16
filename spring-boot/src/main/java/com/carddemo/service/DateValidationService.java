package com.carddemo.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Port of CSUTLDTC (app/cbl/CSUTLDTC.cbl), the shared date-validity utility
 * that wraps the LE callable service CEEDAYS. It checks a PIC X(10) date
 * against a PIC X(10) picture string built from YYYY/MM/DD tokens and
 * returns the fixed 80-byte result plus a numeric severity that doubles as
 * the COBOL RETURN-CODE. Shared so the report stream (S-10) can call it.
 *
 * <p>Classification emulates CEEDAYS feedback order
 * (programs/CSUTLDTC_functional_requirement.md S7): unsupported picture
 * token -&gt; 2518, input shorter than the picture -&gt; 2507, any byte not
 * matching the picture -&gt; 2520, month -&gt; 2517, day -&gt; 2508, range -&gt;
 * 2513, else 0000. Callers still apply the COBOL contract: only severity
 * {@code "0000"} or message {@code "2513"} accept a date
 * (COTRN02C.cbl:370-379).
 */
@Service
public class DateValidationService {

    // CEEDAYS accepted range is the Lillian day window (FR S7); the lower
    // bound is the proleptic Gregorian reform date.
    private static final LocalDate LILLIAN_MIN = LocalDate.of(1582, 10, 15);

    private static final String SEVERITY_OK = "0000";
    private static final String SEVERITY_ERROR = "0003";

    // Verdict texts are the WS-RESULT literals, PIC X(15) (CSUTLDTC.cbl:128-148).
    private static final String V_VALID = "Date is valid";
    private static final String V_SHORT = "Insufficient";
    private static final String V_BAD = "Datevalue error";
    private static final String V_RANGE = "Unsupp. Range";
    private static final String V_MONTH = "Invalid month";
    private static final String V_MASK = "Bad Pic String";
    private static final String V_NONNUM = "Nonnumeric data";

    private sealed interface Token permits YearT, MonthT, DayT, Literal {
    }

    private record YearT() implements Token {
    }

    private record MonthT() implements Token {
    }

    private record DayT() implements Token {
    }

    private record Literal(char c) implements Token {
    }

    private static final Token YEAR_T = new YearT();
    private static final Token MONTH_T = new MonthT();
    private static final Token DAY_T = new DayT();

    /**
     * Validate {@code date} against {@code mask}; mirrors
     * {@code CALL 'CSUTLDTC' USING LS-DATE, LS-DATE-FORMAT, LS-RESULT}.
     */
    public DateValidationResult validate(String date, String mask) {
        String dateText = date == null ? "" : date.replace('\u0000', ' ');
        String maskText = mask == null ? "" : mask.replace('\u0000', ' ');

        List<Token> parts = parseMask(maskText);
        if (parts == null || !hasRequiredParts(parts)) {
            return result(SEVERITY_ERROR, "2518", V_MASK, dateText, maskText, null);
        }
        List<Token> tokens = expand(parts);

        int width = tokens.size();
        String effective = stripTrailingSpaces(dateText);
        if (effective.length() < width) {
            return result(SEVERITY_ERROR, "2507", V_SHORT, dateText, maskText, null);
        }

        // Walk every input byte against the picture (CEEDAYS 2520).
        for (int i = 0; i < effective.length(); i++) {
            char c = effective.charAt(i);
            if (i >= width || !matches(tokens.get(i), c)) {
                return result(SEVERITY_ERROR, "2520", V_NONNUM, dateText, maskText, null);
            }
        }

        int year = digitsAt(effective, tokens, YEAR_T);
        int month = digitsAt(effective, tokens, MONTH_T);
        int day = digitsAt(effective, tokens, DAY_T);

        if (month < 1 || month > 12) {
            return result(SEVERITY_ERROR, "2517", V_MONTH, dateText, maskText, null);
        }
        if (day < 1 || day > daysInMonth(year, month)) {
            return result(SEVERITY_ERROR, "2508", V_BAD, dateText, maskText, null);
        }

        LocalDate parsed = LocalDate.of(year, month, day);
        if (parsed.isBefore(LILLIAN_MIN)) {
            // Year 0000 lands here and cannot be represented downstream, so
            // callers see it without a materialised date (D-2).
            return result(SEVERITY_ERROR, "2513", V_RANGE, dateText, maskText,
                    year == 0 ? null : parsed);
        }
        return result(SEVERITY_OK, "0000", V_VALID, dateText, maskText, parsed);
    }

    /**
     * Picture string: YYYY, MM and DD tokens, any other non-letter byte is a
     * literal. A letter that does not start a known token is an unsupported
     * CEEDAYS picture token (FBS-BAD-PAT-2, FC-BAD-PIC-STRING -&gt; 2518).
     */
    private List<Token> parseMask(String mask) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < mask.length()) {
            if (mask.startsWith("YYYY", i)) {
                tokens.add(YEAR_T);
                i += 4;
            } else if (mask.startsWith("MM", i)) {
                tokens.add(MONTH_T);
                i += 2;
            } else if (mask.startsWith("DD", i)) {
                tokens.add(DAY_T);
                i += 2;
            } else if (!Character.isLetter(mask.charAt(i))) {
                tokens.add(new Literal(mask.charAt(i)));
                i++;
            } else {
                return null;
            }
        }
        return tokens;
    }

    private boolean hasRequiredParts(List<Token> tokens) {
        return tokens.stream().filter(t -> t == YEAR_T).count() == 1
                && tokens.stream().filter(t -> t == MONTH_T).count() == 1
                && tokens.stream().filter(t -> t == DAY_T).count() == 1;
    }

    /** One expectation per input position: YEAR_T x4, MONTH_T/DAY_T x2. */
    private List<Token> expand(List<Token> parts) {
        List<Token> tokens = new ArrayList<>();
        for (Token part : parts) {
            int width = part == YEAR_T ? 4 : part instanceof Literal ? 1 : 2;
            for (int i = 0; i < width; i++) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private boolean matches(Token token, char c) {
        if (token instanceof Literal literal) {
            return c == literal.c();
        }
        return Character.isDigit(c);
    }

    private int digitsAt(String date, List<Token> tokens, Token kind) {
        int value = 0;
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i) == kind) {
                value = value * 10 + (date.charAt(i) - '0');
            }
        }
        return value;
    }

    private int daysInMonth(int year, int month) {
        return LocalDate.of(year, month, 1).lengthOfMonth();
    }

    private DateValidationResult result(String severity, String messageNumber,
                                        String verdict, String date, String mask,
                                        LocalDate parsed) {
        boolean valid = SEVERITY_OK.equals(severity);
        return new DateValidationResult(severity, messageNumber,
                pad(verdict, 15),
                resultText(severity, messageNumber, verdict, date, mask),
                valid, Integer.parseInt(severity), parsed);
    }

    // LS-RESULT, PIC X(80): severity | 'Mesg Code: ' | msg | verdict |
    // 'TstDate: ' | date | 'Mask used:' | mask | spaces (CSUTLDTC.cbl:47-57).
    private String resultText(String severity, String messageNumber, String verdict,
                              String date, String mask) {
        return severity + "Mesg Code: " + messageNumber + " "
                + pad(verdict, 15) + " "
                + "TstDate: " + pad(truncate(date, 10), 10) + " "
                + "Mask used:" + pad(truncate(mask, 10), 10)
                + "    ";
    }

    private static String stripTrailingSpaces(String text) {
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == ' ') {
            end--;
        }
        return text.substring(0, end);
    }

    private static String truncate(String text, int width) {
        return text.length() > width ? text.substring(0, width) : text;
    }

    private static String pad(String text, int width) {
        StringBuilder sb = new StringBuilder(truncate(text, width));
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
