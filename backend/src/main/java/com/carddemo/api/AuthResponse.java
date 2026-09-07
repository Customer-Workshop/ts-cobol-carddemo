package com.carddemo.api;

/**
 * Successful sign-on (COSGN00C.cbl:221-239). {@code userType} is the SEC-USR-TYPE byte
 * ('A' / 'U'); {@code landingTarget} is the B-0009 routing point that replaces the XCTL to
 * COMEN01C / COADM01C — always {@code /menu} in S-01 (Q-01, D-0024).
 */
public record AuthResponse(String userId, String userType, String landingTarget) {
}
