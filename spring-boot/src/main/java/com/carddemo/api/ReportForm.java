package com.carddemo.api;

/**
 * CORPT0A input map as posted by the report-request screen (CORPT00C.cbl:
 * 258-303): the three X(1) report-type flags, the six custom date fields
 * and the Y/N confirm. {@link #normalized()} ports the NUMVAL-C pass that
 * runs between the blank edits and the numeric/range edits (:305-327):
 * digit content is re-stored zero-padded to the field width (' 7' -> '07'),
 * while non-numeric input is left as typed — NUMVAL-C is unspecified on
 * garbage, so the numeric edit that follows reports it.
 */
public record ReportForm(
        String monthly,
        String yearly,
        String custom,
        String sdtmm,
        String sdtdd,
        String sdtyyyy,
        String edtmm,
        String edtdd,
        String edtyyyy,
        String confirm) {

    public ReportForm normalized() {
        return new ReportForm(monthly, yearly, custom,
                numvalc(sdtmm, 2), numvalc(sdtdd, 2), numvalc(sdtyyyy, 4),
                numvalc(edtmm, 2), numvalc(edtdd, 2), numvalc(edtyyyy, 4),
                confirm);
    }

    private static String numvalc(String raw, int width) {
        String text = raw == null ? "" : raw.trim();
        if (!text.matches("\\d+")) {
            return text;
        }
        try {
            return String.format("%0" + width + "d", Integer.parseInt(text));
        } catch (NumberFormatException exception) {
            return text;
        }
    }
}
