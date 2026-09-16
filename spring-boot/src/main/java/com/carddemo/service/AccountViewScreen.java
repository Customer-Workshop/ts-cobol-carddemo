package com.carddemo.service;

/**
 * The CACTVWA screen state as a value object (1200-SETUP-SCREEN-VARS,
 * COACTVWC.cbl:460-535). One record covers the five display outcomes:
 * initial prompt, no-input, invalid filter, xref/account not-found, and
 * customer-side failure with the account block still shown. All block
 * fields are display-edited (amounts '+ZZZ,ZZZ,ZZZ.99', SSN nnn-nn-nnnn,
 * fields truncated to their BMS widths). A null block renders as the BMS
 * LOW-VALUES output: labels present, values blank.
 */
public record AccountViewScreen(
        String accountEcho,
        boolean accountFieldRed,
        String errorMessage,
        AccountBlock account,
        CustomerBlock customer) {

    // COACTVW.bms rows 5-10, moved when FOUND-ACCT-IN-MASTER or
    // FOUND-CUST-IN-MASTER (cbl:471-491).
    public record AccountBlock(
            String activeStatus,
            String openDate,
            String creditLimit,
            String expirationDate,
            String cashCreditLimit,
            String reissueDate,
            String currentBalance,
            String currentCycleCredit,
            String accountGroup,
            String currentCycleDebit) {
    }

    // COACTVW.bms rows 12-20, moved only when FOUND-CUST-IN-MASTER
    // (cbl:493-523). ACSCITY is fed from CUST-ADDR-LINE-3 (cbl:513).
    public record CustomerBlock(
            String customerId,
            String ssn,
            String dateOfBirth,
            String ficoScore,
            String firstName,
            String middleName,
            String lastName,
            String addressLine1,
            String addressLine2,
            String city,
            String stateCode,
            String zip,
            String countryCode,
            String phoneNumber1,
            String phoneNumber2,
            String governmentIssuedId,
            String eftAccountId,
            String primaryCardHolderIndicator) {
    }
}
