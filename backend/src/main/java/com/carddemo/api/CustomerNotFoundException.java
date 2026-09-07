package com.carddemo.api;

import org.springframework.http.HttpStatus;

/**
 * E-11 branch of 9400-GETCUSTDATA-BYCUST (COACTVWC.cbl:836-857): the xref and the account master
 * record were read, only the customer is missing, so 1200-SETUP-SCREEN-VARS still paints the account
 * block under the error message (:471-472, :493). The block travels with the exception because the
 * screen shows both at once (FR §7, A-ACV-1).
 */
public class CustomerNotFoundException extends CobolApiException {

    private final String accountNumber;
    private final AccountBlock account;

    public CustomerNotFoundException(String message, String accountNumber, AccountBlock account) {
        super(HttpStatus.NOT_FOUND, message);
        this.accountNumber = accountNumber;
        this.account = account;
    }

    public String accountNumber() {
        return accountNumber;
    }

    public AccountBlock account() {
        return account;
    }
}
