package com.carddemo.util;

import org.springframework.stereotype.Service;

/**
 * Port of the COBDATFT assembler date-edit subroutine (app/asm/COBDATFT.asm),
 * invoked by COBOL via `CALL 'COBDATFT' USING CODATECN-REC`
 * (app/cbl/CBACT01C.cbl:231). Parameter block layout is CODATECN
 * (app/cpy/CODATECN.cpy): inType CL1, inDate CL20, outType CL1, outDate CL20,
 * errorMessage CL38.
 *
 * The routine writes only the leading bytes of the 20-byte output field and
 * always returns R15=0, so callers detect failure only through the error
 * field — and CBACT01C never checks it, so bad input yields a quietly
 * untouched output area. Branch structure is preserved verbatim: outType is
 * compared only against the one "wrong" code, so an unrecognized outType
 * still produces output (asm lines 38-39, 49-50). The dash-position check on
 * the type-'2' path is commented out in the source and is not enforced here
 * either (asm lines 47-48).
 */
@Service
public class DateEditService {

    public static final String INVALID_INPUT = "INVALID INPUT";
    private static final int FIELD_LENGTH = 20;

    /** Mirrors CODATECN-IN-REC: the CALL's input parameters. */
    public record DateEditRequest(char inType, String inDate, char outType) {
    }

    /**
     * Mirrors CODATECN-OUT-REC: outDate is always the full 20-byte field
     * (reformatted bytes in the leading positions, untouched bytes as
     * spaces); errorMessage is INVALID_INPUT or null.
     */
    public record DateEditResult(String outDate, String errorMessage) {
        /** Leading meaningful bytes of the output field, trimmed. */
        public String date() {
            return outDate.trim();
        }
    }

    public DateEditResult edit(DateEditRequest request) {
        String in = field(request.inDate());
        char[] out = " ".repeat(FIELD_LENGTH).toCharArray();
        switch (request.inType()) {
            case '1' -> {
                // VALIDIN1: YYYYMMDD in -> YYYY-MM-DD out (10 bytes written)
                if (in.charAt(4) == '-' || request.outType() == '2') {
                    return error(out);
                }
                System.arraycopy(in.toCharArray(), 0, out, 0, 4);
                out[4] = '-';
                out[5] = in.charAt(4);
                out[6] = in.charAt(5);
                out[7] = '-';
                out[8] = in.charAt(6);
                out[9] = in.charAt(7);
            }
            case '2' -> {
                // VALIDIN2: YYYY-MM-DD in -> YYYYMMDD out (8 bytes written)
                if (request.outType() == '1') {
                    return error(out);
                }
                System.arraycopy(in.toCharArray(), 0, out, 0, 4);
                out[4] = in.charAt(5);
                out[5] = in.charAt(6);
                out[6] = in.charAt(8);
                out[7] = in.charAt(9);
            }
            default -> {
                // falls through to GOTOERR
                return error(out);
            }
        }
        return new DateEditResult(new String(out), null);
    }

    private static DateEditResult error(char[] untouchedOut) {
        return new DateEditResult(new String(untouchedOut), INVALID_INPUT);
    }

    private static String field(String value) {
        String text = value == null ? "" : value;
        if (text.length() >= FIELD_LENGTH) {
            return text.substring(0, FIELD_LENGTH);
        }
        return text + " ".repeat(FIELD_LENGTH - text.length());
    }
}
