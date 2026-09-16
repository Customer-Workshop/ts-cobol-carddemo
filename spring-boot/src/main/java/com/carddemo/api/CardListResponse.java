package com.carddemo.api;

/**
 * Outcome of one AID press (COCRDLIC.cbl:418-583):
 * {@code "page"} — redisplay {@link #screen};
 * {@code "navigate"} — S04-B1 hand-off in {@link #navigation};
 * {@code "exit"} — PF3, back to the main menu.
 * The updated COMMAREA in {@link #pageState} is always present so the client
 * can echo it on the next press.
 */
public record CardListResponse(
        String outcome,
        CardListNavigation navigation,
        CardListScreenView screen,
        CardListPageState pageState) {
}
