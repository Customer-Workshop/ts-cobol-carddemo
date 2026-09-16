package com.carddemo.api;

/**
 * One CTRTLIA screen row: the flag field (TRTSEL), type code (TRTTYP) and the
 * description shown. The COMMAREA echo keeps the fetched description, so the
 * row also carries origDesc for the hidden round-trip when the displayed text
 * is the user's uncommitted edit.
 */
public record TranTypeListRow(String select, String code, String desc, String origDesc,
                              boolean selectProtected, boolean selectError, boolean descError) {

    public static TranTypeListRow of(String code, String desc) {
        return new TranTypeListRow("", code, desc, desc, false, false, false);
    }
}
