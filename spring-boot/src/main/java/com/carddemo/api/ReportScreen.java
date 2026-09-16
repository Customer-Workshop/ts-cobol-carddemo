package com.carddemo.api;

/**
 * CORPT0A screen state for the /reports web surface (CORPT00C.cbl): the
 * three X(1) report-type flags, the six MM/DD/YYYY date fields, the Y/N
 * confirm, the bottom-line message, its colour and the field the cursor
 * lands on. Field values are echoed on every redisplay because the map
 * fields are FSET (RECEIVE leaves them in the map) — the values here are
 * the raw posts before the NUMVAL-C pass and the normalized ones after it.
 */
public record ReportScreen(
        String monthly,
        String yearly,
        String custom,
        String sdtmm,
        String sdtdd,
        String sdtyyyy,
        String edtmm,
        String edtdd,
        String edtyyyy,
        String confirm,
        String message,
        String messageStyle,
        String cursorField) {

    /** First display and INITIALIZE-ALL-FIELDS (CORPT00C.cbl:179-181,
        :633-646): every field blanked, cursor on MONTHLY. */
    public static ReportScreen blank() {
        return new ReportScreen("", "", "", "", "", "", "", "", "", "",
                null, null, "monthly");
    }

    /** Redisplay with the given field values echoed back — the input
        values as RECEIVE left them (a raw form) or after the NUMVAL-C
        pass (a normalized form), plus the verbatim message and cursor. */
    public static ReportScreen preserved(ReportForm form, String message,
                                         String cursorField) {
        return new ReportScreen(
                trim(form.monthly()), trim(form.yearly()), trim(form.custom()),
                trim(form.sdtmm()), trim(form.sdtdd()), trim(form.sdtyyyy()),
                trim(form.edtmm()), trim(form.edtdd()), trim(form.edtyyyy()),
                trim(form.confirm()), message, null, cursorField);
    }

    /** The green submitted line (DFHGREEN, CORPT00C.cbl:445-456) after
        INITIALIZE-ALL-FIELDS, cursor back on MONTHLY. */
    public static ReportScreen submitted(String name) {
        return new ReportScreen("", "", "", "", "", "", "", "", "", "",
                CobolMessages.reportSubmitted(name), "info", "monthly");
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
