package com.carddemo.service;

/**
 * The CCRDSLA screen state as a value object (1200-SETUP-SCREEN-VARS /
 * 1300-SETUP-SCREEN-ATTRS, COCRDSLC.cbl:457-557). One record covers every
 * display outcome: initial prompt, per-field blank (`*` red on re-entry) /
 * invalid (cleared red), both-blank `No input received`, found, not-found
 * (both red, entered values kept), file-error, and the card-list entry
 * (inputs protected, keys echoed zero-padded). A null card block renders
 * as the BMS LOW-VALUES output: labels present, values blank.
 */
public record CardViewScreen(
        String accountEcho,
        String cardEcho,
        boolean accountFieldRed,
        boolean cardFieldRed,
        boolean inputsProtected,
        String cursorField,
        String infoMessage,
        String errorMessage,
        CardBlock card) {

    // COCRDSL.bms rows 11-15, moved when FOUND-CARDS-FOR-ACCOUNT
    // (cbl:474-484). The expiry slices come from CARD-EXPIRAION-DATE X(10)
    // 'YYYY-MM-DD': year bytes 1-4, month bytes 6-7 (cbl:84-90); a missing
    // date renders blank.
    public record CardBlock(
            String embossedName,
            String expiryMonth,
            String expiryYear,
            String activeStatus) {
    }
}
