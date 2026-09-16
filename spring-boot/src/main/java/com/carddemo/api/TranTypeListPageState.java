package com.carddemo.api;

import java.util.List;

/**
 * WS-THIS-PROGCOMMAREA for COTRTLIC (COTRTLIC.cbl:377-418): the paging keys,
 * screen number, last-page flag, next-page indicator, the last submitted
 * filters, the flagged-row subscript, the pending update/delete flags, and
 * the row echo (WS-CA-ROW-TR-CODE/DESC-OUT). Round-trips whole through the
 * client; the server stays stateless.
 */
public record TranTypeListPageState(
        String firstCode, String lastCode, int screenNumber,
        boolean lastPageShown, boolean nextPageExists,
        int rowSelected, boolean deletePending, boolean updatePending,
        String typeFilter, String descFilter,
        List<TranTypeListRow> rows) {

    public static final int ROW_COUNT = 7;

    public static TranTypeListPageState fresh() {
        return new TranTypeListPageState(null, null, 0, false, true, 0, false, false,
                null, null, List.of());
    }

    public TranTypeListPageState withRows(List<TranTypeListRow> newRows) {
        return new TranTypeListPageState(firstCode, lastCode, screenNumber, lastPageShown,
                nextPageExists, rowSelected, deletePending, updatePending, typeFilter,
                descFilter, newRows);
    }
}
