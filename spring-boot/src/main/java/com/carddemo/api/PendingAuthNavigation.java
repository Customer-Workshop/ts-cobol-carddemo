package com.carddemo.api;

/**
 * S19-B2 hand-off payload: the 'S'-row XCTL target (COPAUS1C) with the
 * account id and selected PAUT9CTS key (COPAUS0C.cbl:315-335).
 */
public record PendingAuthNavigation(String program, Long accountId, String authKey) {
}
