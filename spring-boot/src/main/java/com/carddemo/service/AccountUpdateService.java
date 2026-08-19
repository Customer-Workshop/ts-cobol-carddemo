package com.carddemo.service;

import com.carddemo.api.AccountUpdateRequest;
import com.carddemo.api.AccountViewResponse;
import com.carddemo.api.CobolApiException;
import com.carddemo.api.CobolMessages;
import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.Customer;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class AccountUpdateService {
    private final AccountRepository accountRepository;
    private final CardXrefRepository cardXrefRepository;
    private final CustomerRepository customerRepository;
    private final AccountViewService accountViewService;

    public AccountUpdateService(AccountRepository accountRepository,
                                CardXrefRepository cardXrefRepository,
                                CustomerRepository customerRepository,
                                AccountViewService accountViewService) {
        this.accountRepository = accountRepository;
        this.cardXrefRepository = cardXrefRepository;
        this.customerRepository = customerRepository;
        this.accountViewService = accountViewService;
    }

    @Transactional
    public AccountViewResponse update(AccountUpdateRequest request) {
        long accountId = validateAccount(request.accountId());
        Account account = accountRepository.findById(accountId).orElseThrow(
                () -> new CobolApiException(HttpStatus.NOT_FOUND,
                        CobolMessages.accountNotFound("%011d".formatted(accountId))));
        CardXref xref = cardXrefRepository.findByXrefAcctId(accountId).stream().findFirst()
                .orElseThrow(() -> new CobolApiException(HttpStatus.NOT_FOUND,
                        CobolMessages.xrefNotFound("%011d".formatted(accountId))));
        Customer customer = customerRepository.findById(xref.getXrefCustId()).orElseThrow(
                () -> new CobolApiException(HttpStatus.NOT_FOUND,
                        CobolMessages.customerNotFound(String.valueOf(xref.getXrefCustId()))));

        validate(request);
        if (request.customerId() != null && !request.customerId().equals(customer.getCustId())) {
            throw bad("Customer Id must match the associated customer.");
        }
        if (request.expectedCurrentBalance() != null
                && !same(request.expectedCurrentBalance(), account.getAcctCurrBal())) {
            throw changed();
        }
        if (request.expectedFirstName() != null
                && !sameText(request.expectedFirstName(), customer.getCustFirstName())) {
            throw changed();
        }
        if (sameRequest(request, account, customer)) {
            throw bad(CobolMessages.NO_CHANGES_DETECTED);
        }

        account.setAcctActiveStatus(normalizeStatus(request.activeStatus()));
        account.setAcctCurrBal(request.currentBalance());
        account.setAcctCreditLimit(request.creditLimit());
        account.setAcctCashCreditLimit(request.cashCreditLimit());
        account.setAcctOpenDate(request.openDate());
        account.setAcctExpirationDate(request.expirationDate());
        account.setAcctReissueDate(request.reissueDate());
        account.setAcctCurrCycCredit(request.currentCycleCredit());
        account.setAcctCurrCycDebit(request.currentCycleDebit());
        if (request.accountGroup() != null) {
            account.setAcctGroupId(request.accountGroup().trim());
        }
        accountRepository.save(account);

        customer.setCustSsn(Long.parseLong(request.ssn().replace("-", "")));
        customer.setCustDob(request.dateOfBirth());
        customer.setCustFicoCreditScore(request.ficoScore());
        customer.setCustFirstName(request.firstName().trim());
        customer.setCustMiddleName(blankToNull(request.middleName()));
        customer.setCustLastName(request.lastName().trim());
        customer.setCustAddrLine1(request.addressLine1().trim());
        customer.setCustAddrLine2(blankToNull(request.addressLine2()));
        customer.setCustAddrLine3(request.addressLine3().trim());
        customer.setCustAddrStateCode(request.stateCode().trim().toUpperCase(Locale.ROOT));
        customer.setCustAddrZip(request.zip().trim());
        customer.setCustAddrCountryCode(request.countryCode().trim().toUpperCase(Locale.ROOT));
        customer.setCustPhoneNum1(normalizePhone(request.phoneNumber1()));
        customer.setCustPhoneNum2(normalizePhone(request.phoneNumber2()));
        customer.setCustGovernmentIssuedId(blankToNull(request.governmentIssuedId()));
        customer.setCustEftAccountId(request.eftAccountId().trim());
        customer.setCustPrimaryCardHolderIndicator(normalizeStatus(request.primaryCardHolderIndicator()));
        customerRepository.save(customer);

        return accountViewService.view("%d".formatted(accountId));
    }

    private void validate(AccountUpdateRequest request) {
        requireYesNo("Account Status", request.activeStatus());
        requireMoney("Credit Limit", request.creditLimit());
        requireMoney("Cash Credit Limit", request.cashCreditLimit());
        requireMoney("Current Balance", request.currentBalance());
        requireMoney("Current Cycle Credit Limit", request.currentCycleCredit());
        requireMoney("Current Cycle Debit Limit", request.currentCycleDebit());
        if (request.openDate() == null) throw required("Open Date");
        if (request.expirationDate() == null) throw required("Expiry Date");
        if (request.reissueDate() == null) throw required("Reissue Date");
        if (request.dateOfBirth() == null) throw required("Date of Birth");
        if (request.ficoScore() == null || request.ficoScore() < 300 || request.ficoScore() > 850) {
            throw bad("FICO Score is not valid");
        }
        requireAlpha("First Name", request.firstName(), true);
        requireAlpha("Middle Name", request.middleName(), false);
        requireAlpha("Last Name", request.lastName(), true);
        requireRequired("Address Line 1", request.addressLine1());
        requireAlpha("State", request.stateCode(), true);
        if (!request.stateCode().trim().matches("[A-Za-z]{2}")) {
            throw bad("State is not valid");
        }
        requireNumeric("Zip", request.zip(), true);
        if (!request.zip().trim().matches("\\d{5}")) throw bad("Zip is not valid");
        requireAlpha("City", request.addressLine3(), true);
        requireAlpha("Country", request.countryCode(), true);
        validatePhone("Phone Number 1", request.phoneNumber1());
        validatePhone("Phone Number 2", request.phoneNumber2());
        requireNumeric("EFT Account Id", request.eftAccountId(), true);
        if (request.eftAccountId().trim().matches("0+")) throw bad("EFT Account Id must not be zero.");
        requireYesNo("Primary Card Holder", request.primaryCardHolderIndicator());
        if (request.ssn() == null || !request.ssn().replace("-", "").matches("\\d{9}")) {
            throw bad("SSN must be a 9 digit number");
        }
    }

    private boolean sameRequest(AccountUpdateRequest r, Account a, Customer c) {
        return sameText(r.activeStatus(), a.getAcctActiveStatus())
                && same(r.currentBalance(), a.getAcctCurrBal())
                && same(r.creditLimit(), a.getAcctCreditLimit())
                && same(r.cashCreditLimit(), a.getAcctCashCreditLimit())
                && same(r.openDate(), a.getAcctOpenDate())
                && same(r.expirationDate(), a.getAcctExpirationDate())
                && same(r.reissueDate(), a.getAcctReissueDate())
                && same(r.currentCycleCredit(), a.getAcctCurrCycCredit())
                && same(r.currentCycleDebit(), a.getAcctCurrCycDebit())
                && sameText(r.accountGroup(), a.getAcctGroupId())
                && same(Long.parseLong(r.ssn().replace("-", "")), c.getCustSsn())
                && same(r.dateOfBirth(), c.getCustDob())
                && same(r.ficoScore(), c.getCustFicoCreditScore())
                && sameText(r.firstName(), c.getCustFirstName())
                && sameText(r.middleName(), c.getCustMiddleName())
                && sameText(r.lastName(), c.getCustLastName())
                && sameText(r.addressLine1(), c.getCustAddrLine1())
                && sameText(r.addressLine2(), c.getCustAddrLine2())
                && sameText(r.addressLine3(), c.getCustAddrLine3())
                && sameText(r.stateCode(), c.getCustAddrStateCode())
                && sameText(r.zip(), c.getCustAddrZip())
                && sameText(r.countryCode(), c.getCustAddrCountryCode())
                && sameText(normalizePhone(r.phoneNumber1()), c.getCustPhoneNum1())
                && sameText(normalizePhone(r.phoneNumber2()), c.getCustPhoneNum2())
                && sameText(r.governmentIssuedId(), c.getCustGovernmentIssuedId())
                && sameText(r.eftAccountId(), c.getCustEftAccountId())
                && sameText(r.primaryCardHolderIndicator(), c.getCustPrimaryCardHolderIndicator());
    }

    private long validateAccount(String raw) {
        if (raw == null || !raw.matches("\\d{1,11}") || raw.chars().allMatch(c -> c == '0')) {
            throw bad(CobolMessages.ACCOUNT_NUMBER_INVALID);
        }
        return Long.parseLong(raw);
    }

    private void requireYesNo(String field, String value) {
        if (value == null || value.isBlank()) throw required(field);
        String v = value.trim().toUpperCase(Locale.ROOT);
        if (!v.equals("Y") && !v.equals("N")) throw bad(field + " must be Y or N.");
    }

    private void requireMoney(String field, BigDecimal value) {
        if (value == null) throw required(field);
    }

    private void requireRequired(String field, String value) {
        if (value == null || value.isBlank()) throw required(field);
    }

    private void requireAlpha(String field, String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw required(field);
            return;
        }
        if (!value.trim().matches("[A-Za-z ]+")) throw bad(field + " can have alphabets only.");
    }

    private void requireNumeric(String field, String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw required(field);
            return;
        }
        if (!value.trim().matches("\\d+")) throw bad(field + " must be all numeric.");
    }

    private void validatePhone(String field, String value) {
        if (value == null || value.isBlank()) return;
        String digits = value.replaceAll("\\D", "");
        if (!digits.matches("\\d{10}") || digits.startsWith("000")) {
            throw bad(field + ": Phone number must be a 10 digit number.");
        }
    }

    private String normalizePhone(String value) {
        if (value == null || value.isBlank()) return null;
        String digits = value.replaceAll("\\D", "");
        return digits.length() == 10
                ? "(%s)%s-%s".formatted(digits.substring(0, 3), digits.substring(3, 6), digits.substring(6))
                : value.trim();
    }

    private String normalizeStatus(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean same(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private boolean sameText(String left, String right) {
        return left == null ? right == null : left.trim().equalsIgnoreCase(right == null ? "" : right.trim());
    }

    private CobolApiException required(String field) {
        return bad(field + CobolMessages.FIELD_REQUIRED_SUFFIX);
    }

    private CobolApiException changed() {
        return new CobolApiException(HttpStatus.CONFLICT, CobolMessages.RECORD_CHANGED);
    }

    private CobolApiException bad(String message) {
        return new CobolApiException(HttpStatus.BAD_REQUEST, message);
    }
}
