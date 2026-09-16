package com.carddemo.api;

/**
 * TTUP-UPDATE-SCREEN-DATA (COTRTUPC.cbl:294-335) plus the caller context:
 * the 15-state change-action byte, the fetched originals (TTUP-OLD-DETAILS),
 * the in-flight edits (TTUP-NEW-DETAILS), whether the program is re-entered,
 * and the program it was transferred from (CDEMO-FROM-PROGRAM, for the PF3
 * return target).
 */
public record TranTypeMaintState(
        String action,
        String oldType, String oldDesc,
        String newType, String newDesc,
        boolean reenter,
        String fromProgram) {

    // TTUP-CHANGE-ACTION 88-level values (COTRTUPC.cbl:296-327).
    public static final String NOT_FETCHED = " ";
    public static final String INVALID_SEARCH = "K";
    public static final String NOT_FOUND = "X";
    public static final String SHOW = "S";
    public static final String CREATE = "R";
    public static final String CONFIRM_DELETE = "9";
    public static final String START_DELETE = "8";
    public static final String DELETE_DONE = "7";
    public static final String DELETE_FAILED = "6";
    public static final String CHANGES_NOT_OK = "E";
    public static final String OK_NOT_CONFIRMED = "N";
    public static final String LOCK_ERROR = "L";
    public static final String FAILED = "F";
    public static final String DONE = "C";
    public static final String BACKED_OUT = "B";

    public static TranTypeMaintState fresh(String fromProgram) {
        return new TranTypeMaintState(NOT_FETCHED, null, null, null, null, false, fromProgram);
    }
}
