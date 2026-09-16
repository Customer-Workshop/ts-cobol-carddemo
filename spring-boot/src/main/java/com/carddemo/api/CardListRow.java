package com.carddemo.api;

/**
 * One WS-SCREEN-ROWS slot (COCRDLIC.cbl:255-260): the row data carried in the
 * paging COMMAREA so re-displays (filter errors) show the same rows without a
 * re-read.
 */
public record CardListRow(Long accountId, String cardNumber, String activeStatus) {
}
