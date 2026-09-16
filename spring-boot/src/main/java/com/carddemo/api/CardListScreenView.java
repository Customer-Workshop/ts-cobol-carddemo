package com.carddemo.api;

import java.util.List;

/**
 * The CCRDLIA screen as data: PAGENO, the filter echoes with their error
 * colour, seven row slots, the cursor target, and the INFOMSG/ERRMSG lines
 * (COCRDLIC.cbl:624-932).
 */
public record CardListScreenView(
        int screenNumber,
        String accountFilter,
        String cardFilter,
        boolean accountFilterInvalid,
        boolean cardFilterInvalid,
        List<CardListRowView> rows,
        String cursorField,
        String infoMessage,
        String errorMessage) {
}
