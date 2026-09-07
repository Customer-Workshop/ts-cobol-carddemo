package com.carddemo.api;

import jakarta.validation.constraints.Size;

/**
 * The OPTION field of map COMEN1A (app/bms/COMEN01.bms:145-149, LENGTH=2, UNPROT) as typed.
 * Normalisation and validation happen in {@code MenuService} in the COBOL order
 * (COMEN01C.cbl:117-134); the @Size bound replaces the fixed 2-byte 3270 field (Q-14).
 */
public record MenuSelectRequest(
        @Size(max = 2, message = CobolMessages.OPTION_TOO_LONG) String option) {
}
