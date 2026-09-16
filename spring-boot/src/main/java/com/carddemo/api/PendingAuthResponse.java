package com.carddemo.api;

/**
 * Outcome of one AID press on the list screen:
 * {@code "page"} — redisplay {@link #screen};
 * {@code "navigate"} — S19-B2 hand-off in {@link #navigation};
 * {@code "exit"} — PF3, back to the caller (main menu).
 * The updated COMMAREA in {@link #pageState} is always present so the
 * client can echo it on the next press.
 */
public record PendingAuthResponse(
        String outcome,
        PendingAuthNavigation navigation,
        PendingAuthScreenView screen,
        PendingAuthPageState pageState) {
}
