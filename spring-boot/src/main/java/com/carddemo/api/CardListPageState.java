package com.carddemo.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * WS-THIS-PROGCOMMAREA equivalent (COCRDLIC.cbl:229-260): the paging state the
 * client echoes back on every AID press so the server stays stateless beyond
 * the sign-on session. {@code null} card numbers stand in for the COBOL
 * space-filled anchors; {@code rows} always has exactly seven slots, empty
 * slots {@code null} (LOW-VALUES).
 */
public record CardListPageState(
        String firstCardNumber,
        String lastCardNumber,
        int screenNumber,
        boolean lastPageShown,
        boolean nextPageExists,
        List<CardListRow> rows) {

    public static final int ROW_COUNT = 7;

    public static CardListPageState fresh() {
        return new CardListPageState(null, null, 1, false, false,
                new ArrayList<>(Collections.nCopies(ROW_COUNT, null)));
    }
}
