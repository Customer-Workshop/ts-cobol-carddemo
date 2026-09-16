package com.carddemo.api;

/**
 * One AID press on the card-update screen (CCRDUPA): the received map
 * fields plus the echoed {@link CardUpdateCommarea}. {@code aid} is the
 * 3270 attention key — ENTER, PF3, PF5 or PF12, with every other value
 * remapped to ENTER by the service (COCRDUPC.cbl:413-424). A {@code null}
 * commarea is a fresh entry from the menu; the list-entry seam
 * (GET /cards/update?acctId=&cardNum=, S06-B2) also starts fresh and
 * pre-fetches.
 */
public record CardUpdateForm(
        String aid,
        String accountId,
        String cardNumber,
        String embossedName,
        String activeStatus,
        String expiryMonth,
        String expiryYear,
        String expiryDay,
        CardUpdateCommarea commarea) {

    public static CardUpdateForm search(String accountId, String cardNumber) {
        return new CardUpdateForm("ENTER", accountId, cardNumber,
                null, null, null, null, null, CardUpdateCommarea.fresh());
    }
}
