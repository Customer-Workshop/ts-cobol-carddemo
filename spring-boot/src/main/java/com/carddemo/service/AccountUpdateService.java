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
import java.util.Locale;
import java.util.Objects;

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
    public AccountViewResponse update(String rawAccountId, AccountUpdateRequest request) {
        long accountId = validateAccount(rawAccountId);
        Account account = accountRepository.findById(accountId).orElseThrow(
                () -> notFound(CobolMessages.accountNotFound("%011d".formatted(accountId))));
        CardXref xref = cardXrefRepository.findByXrefAcctId(accountId).stream().findFirst()
                .orElseThrow(() -> notFound(CobolMessages.xrefNotFound("%011d".formatted(accountId))));
        Customer customer = customerRepository.findById(xref.getXrefCustId()).orElseThrow(
                () -> notFound(CobolMessages.customerNotFound(String.valueOf(xref.getXrefCustId()))));

        validate(request);
        AccountUpdateRequest.AccountSnapshot original = request.original();
        if (original == null || !complete(original)) {
            throw bad(CobolMessages.SNAPSHOT_REQUIRED);
        }
        if (!matches(original, account, customer)) {
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
        account.setAcctGroupId(trim(request.accountGroup()));
        accountRepository.save(account);

        customer.setCustSsn(Long.parseLong(request.ssn().replace("-", "")));
        customer.setCustDob(request.dateOfBirth());
        customer.setCustFicoCreditScore(request.ficoScore());
        customer.setCustFirstName(trim(request.firstName()));
        customer.setCustMiddleName(blankToNull(request.middleName()));
        customer.setCustLastName(trim(request.lastName()));
        customer.setCustAddrLine1(trim(request.addressLine1()));
        customer.setCustAddrLine2(blankToNull(request.addressLine2()));
        customer.setCustAddrLine3(trim(request.addressLine3()));
        customer.setCustAddrStateCode(upper(request.stateCode()));
        customer.setCustAddrZip(trim(request.zip()));
        customer.setCustAddrCountryCode(upper(request.countryCode()));
        customer.setCustPhoneNum1(normalizePhone(request.phoneNumber1()));
        customer.setCustPhoneNum2(normalizePhone(request.phoneNumber2()));
        customer.setCustGovernmentIssuedId(blankToNull(request.governmentIssuedId()));
        customer.setCustEftAccountId(trim(request.eftAccountId()));
        customer.setCustPrimaryCardHolderIndicator(normalizeStatus(
                request.primaryCardHolderIndicator()));
        customerRepository.save(customer);
        return accountViewService.view(Long.toString(accountId));
    }

    private void validate(AccountUpdateRequest request) {
        requireYesNo("Account Status", request.activeStatus(), CobolMessages.ACCOUNT_STATUS_INVALID);
        requireMoney("Credit Limit", request.creditLimit());
        requireMoney("Cash Credit Limit", request.cashCreditLimit());
        requireMoney("Current Balance", request.currentBalance());
        requireMoney("Current Cycle Credit Limit", request.currentCycleCredit());
        requireMoney("Current Cycle Debit Limit", request.currentCycleDebit());
        requireDate("Open Date", request.openDate());
        requireDate("Expiry Date", request.expirationDate());
        requireDate("Reissue Date", request.reissueDate());
        requireDate("Date of Birth", request.dateOfBirth());
        if (request.ficoScore() == null || request.ficoScore() < 300 || request.ficoScore() > 850) {
            throw bad(CobolMessages.FICO_INVALID);
        }
        requireAlpha("First Name", request.firstName(), true);
        requireAlpha("Middle Name", request.middleName(), false);
        requireAlpha("Last Name", request.lastName(), true);
        requireRequired("Address Line 1", request.addressLine1());
        requireAlpha("State", request.stateCode(), true);
        requireNumeric("Zip", request.zip(), true);
        requireAlpha("City", request.addressLine3(), true);
        requireAlpha("Country", request.countryCode(), true);
        requirePhone("Phone Number 1", request.phoneNumber1(), 1);
        requirePhone("Phone Number 2", request.phoneNumber2(), 2);
        requireNumeric("EFT Account Id", request.eftAccountId(), true);
        requireYesNo("Primary Card Holder", request.primaryCardHolderIndicator(),
                CobolMessages.PRIMARY_CARD_HOLDER_INVALID);
        if (request.ssn() == null || !request.ssn().replace("-", "").matches("\\d{9}")) {
            throw bad(CobolMessages.SSN_INVALID);
        }
    }

    private boolean matches(AccountUpdateRequest.AccountSnapshot s, Account a, Customer c) {
        return same(s.activeStatus(), a.getAcctActiveStatus())
                && same(s.currentBalance(), a.getAcctCurrBal())
                && same(s.creditLimit(), a.getAcctCreditLimit())
                && same(s.cashCreditLimit(), a.getAcctCashCreditLimit())
                && same(s.openDate(), a.getAcctOpenDate())
                && same(s.expirationDate(), a.getAcctExpirationDate())
                && same(s.reissueDate(), a.getAcctReissueDate())
                && same(s.currentCycleCredit(), a.getAcctCurrCycCredit())
                && same(s.currentCycleDebit(), a.getAcctCurrCycDebit())
                && sameLower(s.accountGroup(), a.getAcctGroupId())
                && same(s.customerId(), c.getCustId())
                && same(Long.parseLong(s.ssn().replace("-", "")), c.getCustSsn())
                && same(s.dateOfBirth(), c.getCustDob())
                && same(s.ficoScore(), c.getCustFicoCreditScore())
                && sameUpper(s.firstName(), c.getCustFirstName())
                && sameUpper(s.middleName(), c.getCustMiddleName())
                && sameUpper(s.lastName(), c.getCustLastName())
                && sameUpper(s.addressLine1(), c.getCustAddrLine1())
                && sameUpper(s.addressLine2(), c.getCustAddrLine2())
                && sameUpper(s.addressLine3(), c.getCustAddrLine3())
                && sameUpper(s.stateCode(), c.getCustAddrStateCode())
                && same(s.zip(), c.getCustAddrZip())
                && sameUpper(s.countryCode(), c.getCustAddrCountryCode())
                && same(s.phoneNumber1(), c.getCustPhoneNum1())
                && same(s.phoneNumber2(), c.getCustPhoneNum2())
                && sameUpper(s.governmentIssuedId(), c.getCustGovernmentIssuedId())
                && same(s.eftAccountId(), c.getCustEftAccountId())
                && same(s.primaryCardHolderIndicator(), c.getCustPrimaryCardHolderIndicator());
    }

    private boolean complete(AccountUpdateRequest.AccountSnapshot s) {
        return s.activeStatus() != null && s.currentBalance() != null
                && s.creditLimit() != null && s.cashCreditLimit() != null
                && s.openDate() != null && s.expirationDate() != null && s.reissueDate() != null
                && s.currentCycleCredit() != null && s.currentCycleDebit() != null
                && s.accountGroup() != null && s.customerId() != null && s.ssn() != null
                && s.dateOfBirth() != null && s.ficoScore() != null && s.firstName() != null
                && s.middleName() != null && s.lastName() != null && s.addressLine1() != null
                && s.addressLine2() != null && s.addressLine3() != null && s.stateCode() != null
                && s.zip() != null && s.countryCode() != null && s.phoneNumber1() != null
                && s.phoneNumber2() != null && s.governmentIssuedId() != null
                && s.eftAccountId() != null && s.primaryCardHolderIndicator() != null;
    }

    private boolean sameRequest(AccountUpdateRequest r, Account a, Customer c) {
        AccountUpdateRequest.AccountSnapshot current = new AccountUpdateRequest.AccountSnapshot(
                a.getAcctActiveStatus(), a.getAcctCurrBal(), a.getAcctCreditLimit(),
                a.getAcctCashCreditLimit(), a.getAcctOpenDate(), a.getAcctExpirationDate(),
                a.getAcctReissueDate(), a.getAcctCurrCycCredit(), a.getAcctCurrCycDebit(),
                a.getAcctGroupId(), c.getCustId(), formatSsn(c.getCustSsn()), c.getCustDob(),
                c.getCustFicoCreditScore(), c.getCustFirstName(), c.getCustMiddleName(),
                c.getCustLastName(), c.getCustAddrLine1(), c.getCustAddrLine2(),
                c.getCustAddrLine3(), c.getCustAddrStateCode(), c.getCustAddrZip(),
                c.getCustAddrCountryCode(), c.getCustPhoneNum1(), c.getCustPhoneNum2(),
                c.getCustGovernmentIssuedId(), c.getCustEftAccountId(),
                c.getCustPrimaryCardHolderIndicator());
        return matchesRequest(r, current);
    }

    private boolean matchesRequest(AccountUpdateRequest r,
                                   AccountUpdateRequest.AccountSnapshot c) {
        return same(r.activeStatus(), c.activeStatus())
                && same(r.currentBalance(), c.currentBalance())
                && same(r.creditLimit(), c.creditLimit())
                && same(r.cashCreditLimit(), c.cashCreditLimit())
                && same(r.openDate(), c.openDate())
                && same(r.expirationDate(), c.expirationDate())
                && same(r.reissueDate(), c.reissueDate())
                && same(r.currentCycleCredit(), c.currentCycleCredit())
                && same(r.currentCycleDebit(), c.currentCycleDebit())
                && same(r.accountGroup(), c.accountGroup())
                && same(r.customerId(), c.customerId())
                && same(r.ssn(), c.ssn())
                && same(r.dateOfBirth(), c.dateOfBirth())
                && same(r.ficoScore(), c.ficoScore())
                && same(r.firstName(), c.firstName())
                && same(r.middleName(), c.middleName())
                && same(r.lastName(), c.lastName())
                && same(r.addressLine1(), c.addressLine1())
                && same(r.addressLine2(), c.addressLine2())
                && same(r.addressLine3(), c.addressLine3())
                && same(r.stateCode(), c.stateCode())
                && same(r.zip(), c.zip())
                && same(r.countryCode(), c.countryCode())
                && same(normalizePhone(r.phoneNumber1()), c.phoneNumber1())
                && same(normalizePhone(r.phoneNumber2()), c.phoneNumber2())
                && same(r.governmentIssuedId(), c.governmentIssuedId())
                && same(r.eftAccountId(), c.eftAccountId())
                && same(r.primaryCardHolderIndicator(), c.primaryCardHolderIndicator());
    }

    private long validateAccount(String raw) {
        if (raw == null || !raw.matches("\\d{1,11}") || raw.chars().allMatch(c -> c == '0')) {
            throw bad(CobolMessages.ACCOUNT_NUMBER_INVALID);
        }
        return Long.parseLong(raw);
    }

    private void requireYesNo(String field, String value, String invalidMessage) {
        if (value == null || value.isBlank()) throw required(field);
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("Y") && !normalized.equals("N")) throw bad(invalidMessage);
    }

    private void requireMoney(String field, BigDecimal value) {
        if (value == null) throw required(field);
    }

    private void requireDate(String field, Object value) {
        if (value == null) throw required(field);
    }

    private void requireRequired(String field, String value) {
        if (value == null || value.isBlank()) throw required(field);
    }

    private void requireAlpha(String field, String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw required(field);
        } else if (!value.trim().matches("[A-Za-z ]+")) {
            throw bad(CobolMessages.fieldAlpha(field));
        }
    }

    private void requireNumeric(String field, String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw required(field);
        } else if (!value.trim().matches("\\d+")) {
            throw bad(CobolMessages.fieldNumeric(field));
        }
    }

    private void requirePhone(String field, String value, int number) {
        if (value == null || value.isBlank()) throw required(field + " area code");
        String digits = value.replaceAll("\\D", "");
        if (!digits.matches("\\d{10}") || digits.startsWith("000")) {
            throw bad(CobolMessages.phoneInvalid(field, number));
        }
    }

    private String normalizePhone(String value) {
        if (value == null || value.isBlank()) return null;
        String digits = value.replaceAll("\\D", "");
        return digits.length() == 10
                ? "(%s)%s-%s".formatted(digits.substring(0, 3), digits.substring(3, 6),
                digits.substring(6))
                : value.trim();
    }

    private String normalizeStatus(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String formatSsn(Long ssn) {
        return "%09d".formatted(ssn);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean same(Object left, Object right) {
        if (left instanceof BigDecimal leftDecimal && right instanceof BigDecimal rightDecimal) {
            return leftDecimal.compareTo(rightDecimal) == 0;
        }
        return Objects.equals(left, right);
    }

    private boolean sameUpper(String left, String right) {
        return Objects.equals(upper(left), upper(right));
    }

    private boolean sameLower(String left, String right) {
        return left == null ? right == null : left.trim().equalsIgnoreCase(right == null ? null : right.trim());
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

    private CobolApiException notFound(String message) {
        return new CobolApiException(HttpStatus.NOT_FOUND, message);
    }
}
