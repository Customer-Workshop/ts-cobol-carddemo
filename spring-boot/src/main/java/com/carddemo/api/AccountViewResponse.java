package com.carddemo.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountViewResponse(
        Long accountId, String activeStatus, BigDecimal currentBalance, BigDecimal creditLimit,
        BigDecimal cashCreditLimit, LocalDate openDate, LocalDate expirationDate, LocalDate reissueDate,
        BigDecimal currentCycleCredit, BigDecimal currentCycleDebit, String accountGroup,
        Long customerId, String ssn, LocalDate dateOfBirth, Integer ficoScore,
        String firstName, String middleName, String lastName, String addressLine1,
        String addressLine2, String addressLine3, String stateCode, String zip,
        String countryCode, String phoneNumber1, String phoneNumber2, String governmentIssuedId,
        String eftAccountId, String primaryCardHolderIndicator) {
}
