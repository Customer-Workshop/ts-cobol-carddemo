package com.carddemo.api;

/**
 * The CCRDUPA render state after one AID turn (3000-SEND-MAP,
 * COCRDUPC.cbl:1035-1317). Field values are the display echoes: the OLD
 * image in state S, the NEW image in E/N/C/L/F, {@code *} on blank-flagged
 * fields. The {@code Invalid} flags carry the DFHRED colouring and the
 * protection flags come from the change-action attribute rules
 * (cbl:1168-1208). {@code commarea} is the state to echo back on the next
 * turn.
 */
public record CardUpdateScreen(
        String changeAction,
        String accountId, boolean accountInvalid,
        String cardNumber, boolean cardInvalid,
        String embossedName, boolean nameInvalid,
        String activeStatus, boolean statusInvalid,
        String expiryMonth, boolean monthInvalid,
        String expiryYear, boolean yearInvalid,
        String expiryDay,
        String cursorField,
        String infoMessage,
        String errorMessage,
        CardUpdateCommarea commarea) {

    // cbl:1173-1180, :1200-1207 — search keys are editable on the search
    // screen and again on the failed states (the OTHER attribute branch).
    public boolean searchEditable() {
        return !detailsFetched() || "L".equals(changeAction) || "F".equals(changeAction);
    }

    // cbl:1181-1190 — detail fields are unprotected only while S/E.
    public boolean detailEditable() {
        return "S".equals(changeAction) || "E".equals(changeAction);
    }

    public boolean detailsFetched() {
        return changeAction != null && !changeAction.isBlank();
    }

    // F5=Save F12=Cancel is bright only while awaiting confirmation
    // (cbl:1315-1316).
    public boolean confirmPending() {
        return "N".equals(changeAction);
    }
}
