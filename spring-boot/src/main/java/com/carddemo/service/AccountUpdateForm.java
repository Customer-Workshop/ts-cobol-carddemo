package com.carddemo.service;

/**
 * The CACTUPA input map as raw screen strings: the ACUP-NEW-* details plus the
 * ACCTSID search key. Values are exactly what was typed — blank/`*`
 * normalization (1100-RECEIVE-MAP, deviation D5) happens in
 * {@link AccountUpdateEditRules}.
 */
public record AccountUpdateForm(
        String acctId,
        String activeStatus,
        String openYear, String openMon, String openDay,
        String creditLimit,
        String expYear, String expMon, String expDay,
        String cashCreditLimit,
        String risYear, String risMon, String risDay,
        String currentBalance,
        String accountGroup,
        String currentCycleCredit,
        String currentCycleDebit,
        String custId,
        String ssn1, String ssn2, String ssn3,
        String dobYear, String dobMon, String dobDay,
        String ficoScore,
        String firstName, String middleName, String lastName,
        String addrLine1, String addrLine2,
        String city, String state, String zip, String country,
        String phone1a, String phone1b, String phone1c,
        String phone2a, String phone2b, String phone2c,
        String governmentId, String eftAccountId, String priCardHolder) {

    public AccountUpdateForm {
        acctId = nvl(acctId);
        activeStatus = nvl(activeStatus);
        openYear = nvl(openYear);
        openMon = nvl(openMon);
        openDay = nvl(openDay);
        creditLimit = nvl(creditLimit);
        expYear = nvl(expYear);
        expMon = nvl(expMon);
        expDay = nvl(expDay);
        cashCreditLimit = nvl(cashCreditLimit);
        risYear = nvl(risYear);
        risMon = nvl(risMon);
        risDay = nvl(risDay);
        currentBalance = nvl(currentBalance);
        accountGroup = nvl(accountGroup);
        currentCycleCredit = nvl(currentCycleCredit);
        currentCycleDebit = nvl(currentCycleDebit);
        custId = nvl(custId);
        ssn1 = nvl(ssn1);
        ssn2 = nvl(ssn2);
        ssn3 = nvl(ssn3);
        dobYear = nvl(dobYear);
        dobMon = nvl(dobMon);
        dobDay = nvl(dobDay);
        ficoScore = nvl(ficoScore);
        firstName = nvl(firstName);
        middleName = nvl(middleName);
        lastName = nvl(lastName);
        addrLine1 = nvl(addrLine1);
        addrLine2 = nvl(addrLine2);
        city = nvl(city);
        state = nvl(state);
        zip = nvl(zip);
        country = nvl(country);
        phone1a = nvl(phone1a);
        phone1b = nvl(phone1b);
        phone1c = nvl(phone1c);
        phone2a = nvl(phone2a);
        phone2b = nvl(phone2b);
        phone2c = nvl(phone2c);
        governmentId = nvl(governmentId);
        eftAccountId = nvl(eftAccountId);
        priCardHolder = nvl(priCardHolder);
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    public static AccountUpdateForm blank() {
        return new AccountUpdateForm(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }
}
