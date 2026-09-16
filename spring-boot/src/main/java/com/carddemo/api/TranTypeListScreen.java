package com.carddemo.api;

import java.util.List;

/**
 * The CTRTLIA map as rendered: filters (TRTYPE/TRDESC), seven rows, info and
 * error lines, and the field the cursor lands on (2400-SETUP-SCREEN-ATTRS).
 */
public record TranTypeListScreen(
        String typeFilter, String descFilter,
        boolean typeFilterInvalid, boolean descFilterInvalid, boolean filtersProtected,
        int screenNumber,
        List<TranTypeListRow> rows,
        String infoMessage, String errorMessage, String cursorField) {
}
