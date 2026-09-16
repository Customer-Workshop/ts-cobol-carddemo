package com.carddemo.service;

import com.carddemo.model.Account;
import com.carddemo.model.Customer;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The ACUP-OLD-* commarea snapshot in record terms: canonical stored values,
 * not screen derivations (stored zip keeps all 10 chars, phones keep the
 * X(15) stored string, dates stay dates). It rides the request on every
 * stateful round-trip (S03-B3) and is what 9500-STORE-FETCHED-DATA built.
 */
public record AccountUpdateSnapshot(
        Long accountId,
        String activeStatus,
        BigDecimal currentBalance,
        BigDecimal creditLimit,
        BigDecimal cashCreditLimit,
        LocalDate openDate,
        LocalDate expirationDate,
        LocalDate reissueDate,
        BigDecimal currentCycleCredit,
        BigDecimal currentCycleDebit,
        String accountGroup,
        Long customerId,
        Long ssn,
        LocalDate dateOfBirth,
        Integer ficoScore,
        String firstName,
        String middleName,
        String lastName,
        String addressLine1,
        String addressLine2,
        String addressLine3,
        String stateCode,
        String zip,
        String countryCode,
        String phoneNumber1,
        String phoneNumber2,
        String governmentIssuedId,
        String eftAccountId,
        String primaryCardHolderIndicator) {

    public static AccountUpdateSnapshot of(Account account, Customer customer) {
        return new AccountUpdateSnapshot(
                account.getAcctId(), account.getAcctActiveStatus(),
                account.getAcctCurrBal(), account.getAcctCreditLimit(),
                account.getAcctCashCreditLimit(), account.getAcctOpenDate(),
                account.getAcctExpirationDate(), account.getAcctReissueDate(),
                account.getAcctCurrCycCredit(), account.getAcctCurrCycDebit(),
                account.getAcctGroupId(),
                customer.getCustId(), customer.getCustSsn(), customer.getCustDob(),
                customer.getCustFicoCreditScore(), customer.getCustFirstName(),
                customer.getCustMiddleName(), customer.getCustLastName(),
                customer.getCustAddrLine1(), customer.getCustAddrLine2(),
                customer.getCustAddrLine3(), customer.getCustAddrStateCode(),
                customer.getCustAddrZip(), customer.getCustAddrCountryCode(),
                customer.getCustPhoneNum1(), customer.getCustPhoneNum2(),
                customer.getCustGovernmentIssuedId(), customer.getCustEftAccountId(),
                customer.getCustPrimaryCardHolderIndicator());
    }

    /**
     * 3202-SHOW-ORIGINAL-VALUES / 3203 (COACTUPC.cbl:2787-2952): the snapshot
     * rendered back into screen fields — money via +ZZZ,ZZZ,ZZZ.99, dates as
     * y/m/d parts, SSN as 3/2/4, phones as (2:3)/(6:3)/(10:4) part slices,
     * zip truncated to its first 5 chars.
     */
    public AccountUpdateForm displayForm() {
        String[] dateParts = new String[9];
        fillDateParts(dateParts, 0, openDate);
        fillDateParts(dateParts, 3, expirationDate);
        fillDateParts(dateParts, 6, reissueDate);
        String[] dob = new String[3];
        fillDateParts(dob, 0, dateOfBirth);
        String ssnDigits = ssn == null ? "" : "%09d".formatted(ssn);
        String ssn1 = ssnDigits.length() == 9 ? ssnDigits.substring(0, 3) : "";
        String ssn2 = ssnDigits.length() == 9 ? ssnDigits.substring(3, 5) : "";
        String ssn3 = ssnDigits.length() == 9 ? ssnDigits.substring(5) : "";
        String[] p1 = phoneParts(phoneNumber1);
        String[] p2 = phoneParts(phoneNumber2);
        String storedZip = zip == null ? "" : zip;
        return new AccountUpdateForm(
                accountId == null ? "" : "%011d".formatted(accountId),
                nvl(activeStatus),
                dateParts[0], dateParts[1], dateParts[2],
                CobolFormat.editSignedAmount(creditLimit),
                dateParts[3], dateParts[4], dateParts[5],
                CobolFormat.editSignedAmount(cashCreditLimit),
                dateParts[6], dateParts[7], dateParts[8],
                CobolFormat.editSignedAmount(currentBalance),
                nvl(accountGroup),
                CobolFormat.editSignedAmount(currentCycleCredit),
                CobolFormat.editSignedAmount(currentCycleDebit),
                customerId == null ? "" : "%09d".formatted(customerId),
                ssn1, ssn2, ssn3,
                dob[0], dob[1], dob[2],
                ficoScore == null ? "" : "%03d".formatted(ficoScore),
                nvl(firstName), nvl(middleName), nvl(lastName),
                nvl(addressLine1), nvl(addressLine2),
                nvl(addressLine3), nvl(stateCode),
                storedZip.length() > 5 ? storedZip.substring(0, 5) : storedZip,
                nvl(countryCode),
                p1[0], p1[1], p1[2],
                p2[0], p2[1], p2[2],
                nvl(governmentIssuedId), nvl(eftAccountId),
                nvl(primaryCardHolderIndicator));
    }

    private static void fillDateParts(String[] parts, int offset, LocalDate date) {
        if (date == null) {
            parts[offset] = "";
            parts[offset + 1] = "";
            parts[offset + 2] = "";
        } else {
            parts[offset] = "%04d".formatted(date.getYear());
            parts[offset + 1] = "%02d".formatted(date.getMonthValue());
            parts[offset + 2] = "%02d".formatted(date.getDayOfMonth());
        }
    }

    // ACSPH*A/B/C hold stored positions (2:3), (6:3), (10:4) of the X(15) field
    // (cbl:2846-2857) — "(201)555-1234" -> "201"/"555"/"1234".
    static String[] phoneParts(String stored) {
        String padded = String.format("%-15s", stored == null ? "" : stored);
        return new String[] {
                padded.substring(1, 4),
                padded.substring(5, 8),
                padded.substring(9, 13)};
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
