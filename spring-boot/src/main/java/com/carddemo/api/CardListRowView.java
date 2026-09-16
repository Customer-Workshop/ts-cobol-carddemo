package com.carddemo.api;

/**
 * One rendered row of CCRDLIA: the COMMAREA row plus the per-row display
 * attributes the program maintains (select-code echo, error colour, protected
 * flag — COCRDLIC.cbl:748-832).
 */
public record CardListRowView(
        String select,
        Long accountId,
        String cardNumber,
        String activeStatus,
        boolean selectError,
        boolean selectProtected) {
}
