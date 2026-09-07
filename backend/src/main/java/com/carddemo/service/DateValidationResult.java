package com.carddemo.service;

/**
 * CSUTLDTC output: RETURN-CODE = severity (app/cbl/CSUTLDTC.cbl:98) and the 80-byte LS-RESULT
 * built from WS-MESSAGE (CSUTLDTC.cbl:42-57, layout mirrored in app/cpy/CSUTLDWY.cpy:60-85).
 *
 * @param severity      WS-SEVERITY-N (0 valid, 3 invalid)
 * @param messageNumber WS-MSG-NO, 4 characters
 * @param reasonText    WS-RESULT, exactly 15 characters
 * @param formatted80   the whole WS-MESSAGE, exactly 80 characters
 */
public record DateValidationResult(int severity, String messageNumber, String reasonText, String formatted80) {

    public boolean isValid() {
        return severity == 0;
    }
}
