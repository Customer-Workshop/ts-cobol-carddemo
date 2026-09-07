package com.carddemo.api;

import java.time.Instant;

/**
 * {@link ErrorResponse} for the invalid-option branch of PROCESS-ENTER-KEY (COMEN01C.cbl:127-134),
 * carrying in addition the normalised two-character echo the legacy leaves in OPTIONO (:125) so the
 * re-shown menu screen can display it (FR §5 B-0010: "400 E-08 with the normalised echo").
 */
public record MenuErrorResponse(String message, int status, Instant timestamp, String option) {
}
