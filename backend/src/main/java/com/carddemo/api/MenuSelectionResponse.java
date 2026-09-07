package com.carddemo.api;

/**
 * Outcome of PROCESS-ENTER-KEY (COMEN01C.cbl:115-191) as one payload.
 *
 * <p>{@code option} is the normalised two-character echo the COBOL moves to OPTIONO (:125), so the
 * screen shows {@code 00} for a blank field and {@code 0A} for {@code A}. A dispatch (:184-187)
 * carries {@code program}, {@code endpoint}, {@code route} and {@code implemented=true}; an
 * excluded option (Q-12 / B-0032) carries its {@code program} with {@code implemented=false} and
 * the target-only text; an invalid option (:127-134) is returned with HTTP 400, the verbatim E-08
 * text and the echo, so the screen can restore the field as the legacy SEND MAP did.
 */
public record MenuSelectionResponse(
        String option,
        String program,
        String endpoint,
        String route,
        boolean implemented,
        String message) {
}
