package com.carddemo.api;

/**
 * A fully populated map {@code CACTVWA} (COACTVWC.cbl:1200-SETUP-SCREEN-VARS, :455-534):
 * header fields, the echoed account number, the constant info line and both display blocks.
 * {@code ERRMSG} is empty on this path, so no error field exists here (E-04..E-12 are error bodies).
 */
public record AccountViewResponse(
        ScreenHeaderResponse header,
        String accountNumber,
        String infoMessage,
        AccountBlock account,
        CustomerBlock customer) {
}
