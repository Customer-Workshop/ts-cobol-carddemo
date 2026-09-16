package com.carddemo.api;

/**
 * The ported WS-THIS-PROGCOMMAREA (COCRDUPC.cbl:274-313): the change-action
 * state plus the OLD card image round-tripped on every AID press. The OLD
 * account/card ids are the keys the search ran with — the account is never
 * matched against the stored card (cbl:1379-1380). CVV is deliberately
 * absent: the browser never receives it, so the concurrency compare skips
 * it (deviation D2).
 */
public record CardUpdateCommarea(
        String changeAction,
        String accountId,
        String cardNumber,
        String embossedName,
        String activeStatus,
        String expiryYear,
        String expiryMonth,
        String expiryDay) {

    // CCUP-DETAILS-NOT-FETCHED is LOW-VALUES/SPACES (cbl:278-280) — a blank
    // change-action is the search screen.
    public static CardUpdateCommarea fresh() {
        return new CardUpdateCommarea("", null, null, null, null, null, null, null);
    }

    public boolean detailsFetched() {
        return changeAction != null && !changeAction.isBlank();
    }
}
