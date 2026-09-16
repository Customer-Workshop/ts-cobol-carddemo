package com.carddemo.api;

/**
 * S04-B1 hand-off payload: the XCTL target (COCRDSLC for 'S', COCRDUPC for
 * 'U') plus the selected row's account id and card number, resolved through
 * the route registry by the caller (COCRDLIC.cbl:517-569).
 */
public record CardListNavigation(String program, String action, Long accountId, String cardNumber) {
}
