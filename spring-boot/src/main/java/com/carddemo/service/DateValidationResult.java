package com.carddemo.service;

import java.time.LocalDate;

/**
 * CSUTLDTC caller contract (app/cbl/CSUTLDTC.cbl:42-98): severity is PIC 9(4)
 * and doubles as the COBOL RETURN-CODE (:97-98); messageNumber is the low
 * half-word of the CEEDAYS feedback token; verdict is the 15-byte WS-RESULT
 * text; resultText is the exact 80-byte LS-RESULT layout.
 *
 * <p>valid mirrors severity {@code "0000"} (88 FC-INVALID-DATE, :62).
 * parsed is the materialised date for callers that persist it; null when the
 * verdict carries no usable date. Note it is also null for year 0000, which
 * the 2513 path cannot represent downstream (S09 deviation D-2).
 */
public record DateValidationResult(
        String severity,
        String messageNumber,
        String verdict,
        String resultText,
        boolean valid,
        int returnCode,
        LocalDate parsed) {
}
