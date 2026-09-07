package com.carddemo.security;

import jakarta.servlet.http.HttpSession;

import java.io.Serializable;
import java.util.Optional;

/**
 * B-0027 / D-0030: server-side replacement for the CICS COMMAREA (app/cpy/COCOM01Y.cpy:19-44).
 * Only CDEMO-USER-ID (X(8)) and CDEMO-USER-TYPE ('A' admin / 'U' user, COCOM01Y.cpy:25-28) survive
 * in the session; screen-to-screen context (account/customer/card ids, last map) travels in DTOs.
 */
public record SessionContext(String userId, UserType userType) implements Serializable {

    public static final String ATTRIBUTE = SessionContext.class.getName();

    public enum UserType {
        ADMIN('A'),
        USER('U');

        private final char legacyCode;

        UserType(char legacyCode) {
            this.legacyCode = legacyCode;
        }

        /** The SEC-USR-TYPE byte (app/cpy/CSUSR01Y.cpy:22). */
        public char legacyCode() {
            return legacyCode;
        }

        public static UserType fromLegacyCode(String code) {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("SEC-USR-TYPE is required");
            }
            return switch (code.trim().charAt(0)) {
                case 'A' -> ADMIN;
                case 'U' -> USER;
                default -> throw new IllegalArgumentException("Unknown SEC-USR-TYPE");
            };
        }
    }

    public SessionContext {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (userId.length() > 8) {
            throw new IllegalArgumentException("userId exceeds CDEMO-USER-ID X(8)");
        }
        if (userType == null) {
            throw new IllegalArgumentException("userType is required");
        }
    }

    public boolean isAdmin() {
        return userType == UserType.ADMIN;
    }

    public void store(HttpSession session) {
        session.setAttribute(ATTRIBUTE, this);
    }

    public static Optional<SessionContext> from(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }
        Object value = session.getAttribute(ATTRIBUTE);
        return value instanceof SessionContext context ? Optional.of(context) : Optional.empty();
    }
}
