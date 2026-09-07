package com.carddemo.api;

import jakarta.validation.constraints.Size;

/**
 * COSGN00C sign-on input: USERIDI / PASSWDI of map COSGN0A (app/bms/COSGN00.bms:156-181, both
 * LENGTH=8). Presence/blank checks are made in {@code AuthService} in the COBOL paragraph order
 * (COSGN00C.cbl:117-127); the @Size bound replaces the fixed 8-byte field the 3270 enforced (Q-14).
 */
public record AuthRequest(
        @Size(max = 8, message = CobolMessages.FIELD_TOO_LONG) String userId,
        @Size(max = 8, message = CobolMessages.FIELD_TOO_LONG) String password) {
}
