package com.carddemo.api;

import java.time.Instant;

/**
 * {@link ErrorResponse} for E-11 (COACTVWC.cbl:845-856): the verbatim message plus the account block
 * the legacy screen keeps visible, and the echoed account number of ACCTSIDO (:462).
 */
public record AccountViewErrorResponse(
        String message,
        int status,
        Instant timestamp,
        String accountNumber,
        AccountBlock account) {
}
