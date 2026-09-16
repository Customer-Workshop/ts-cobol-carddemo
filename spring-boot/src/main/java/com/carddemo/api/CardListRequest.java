package com.carddemo.api;

import java.util.List;

/**
 * One AID press against the card list: the 3270 key, the screen inputs
 * (filters and per-row select codes), and the echoed paging COMMAREA.
 * {@code pageState == null} is the fresh-entry path (entry from the menu or
 * any other program — COCRDLIC.cbl:315-343).
 */
public record CardListRequest(
        String aid,
        String accountFilter,
        String cardFilter,
        List<String> selections,
        CardListPageState pageState) {
}
