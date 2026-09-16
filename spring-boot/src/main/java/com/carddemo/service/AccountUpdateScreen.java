package com.carddemo.service;

import java.util.Map;

/**
 * One rendering of the account-update screen: the six ACUP-CHANGE-ACTION
 * states, the values shown in each input, the CSSETATY flag map, and the
 * fetched snapshot that must be echoed back on the next round-trip.
 */
public record AccountUpdateScreen(
        State state,
        String accountEcho,
        boolean accountFieldRed,
        String infoMessage,
        String errorMessage,
        AccountUpdateForm fields,
        AccountUpdateSnapshot original,
        Map<String, Flag> flags,
        String cursorField) {

    /** ACUP-CHANGE-ACTION (COACTUPC.cbl:654-668). */
    public enum State {
        /** ACUP-DETAILS-NOT-FETCHED (LOW-VALUES/SPACES). */
        SEARCH,
        /** ACUP-SHOW-DETAILS 'S'. */
        DETAILS,
        /** ACUP-CHANGES-NOT-OK 'E'. */
        EDIT_ERROR,
        /** ACUP-CHANGES-OK-NOT-CONFIRMED 'N'. */
        CONFIRM,
        /** ACUP-CHANGES-OKAYED-AND-DONE 'C'. */
        DONE,
        /** ACUP-CHANGES-FAILED 'L'/'F'. */
        FAILED
    }

    /** The CSUTLDWY flag byte: LOW-VALUES omitted from the map entirely. */
    public enum Flag { NOT_OK, BLANK }

    /** ACCTSID editable in Search and in the failed state (3300-ATTRS). */
    public boolean accountIdEditable() {
        return state == State.SEARCH || state == State.FAILED;
    }

    /** Detail fields editable only while showing details or edit errors. */
    public boolean fieldsEditable() {
        return state == State.DETAILS || state == State.EDIT_ERROR;
    }

    /** F5=Save bright only on the confirm screen (cbl:3579-3581). */
    public boolean f5Lit() {
        return state == State.CONFIRM;
    }

    /** F12=Cancel bright when changes made and not done (cbl:3573-3577). */
    public boolean f12Lit() {
        return state == State.EDIT_ERROR || state == State.CONFIRM
                || state == State.FAILED;
    }

    public boolean fetched() {
        return state != State.SEARCH;
    }

    /** The typed value, or `*` when CSSETATY flagged the field blank. */
    public String value(String field, String typed) {
        return flags.get(field) == Flag.BLANK ? "*" : typed;
    }

    public boolean flagged(String field) {
        return flags.containsKey(field);
    }
}
