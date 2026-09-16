package com.carddemo.api;

/**
 * The CTRTUPA map as rendered (COTRTUPC.bms): search-key and description
 * fields with their editability (3300-SETUP-SCREEN-ATTRS), the centered info
 * line, the error line, and the cursor field.
 */
public record TranTypeMaintScreen(
        String trtypcd, String trtydsc,
        boolean codeEditable, boolean descEditable,
        String infoMessage, String errorMessage, String cursorField) {
}
