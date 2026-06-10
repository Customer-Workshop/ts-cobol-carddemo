package com.carddemo.service;

import com.carddemo.model.Account;
import com.carddemo.model.Card;
import com.carddemo.model.CardXref;
import com.carddemo.model.Customer;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Replaces COACTVWC (Account View) and COACTUPC (Account Update) COBOL programs.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final CardXrefRepository cardXrefRepository;
    private final CustomerRepository customerRepository;
    private final CardRepository cardRepository;

    /**
     * Mirrors COACTVWC: read account, look up xref → customer + cards.
     */
    public Map<String, Object> getAccountDetails(Long acctId) {
        Account account = accountRepository.findById(acctId)
                .orElseThrow(() -> new AccountNotFoundException(acctId));

        List<CardXref> xrefs = cardXrefRepository.findByXrefAcctId(acctId);

        Optional<Customer> customer = xrefs.stream()
                .findFirst()
                .flatMap(x -> customerRepository.findById(x.getXrefCustId()));

        List<Card> cards = cardRepository.findByCardAcctId(acctId);

        return Map.of(
                "account", account,
                "customer", customer.orElse(null) == null ? Map.of() : customer.get(),
                "cards", cards,
                "crossReferences", xrefs
        );
    }

    /**
     * Mirrors COACTUPC: validate then update.
     * Uses @Version optimistic locking instead of CICS READ UPDATE / REWRITE.
     */
    @Transactional
    public Account updateAccount(Long acctId, Account updates) {
        Account existing = accountRepository.findById(acctId)
                .orElseThrow(() -> new AccountNotFoundException(acctId));

        if (updates.getAcctActiveStatus() != null) {
            validateActiveStatus(updates.getAcctActiveStatus());
            existing.setAcctActiveStatus(updates.getAcctActiveStatus());
        }
        if (updates.getAcctCurrBal() != null) {
            existing.setAcctCurrBal(updates.getAcctCurrBal());
        }
        if (updates.getAcctCreditLimit() != null) {
            validatePositiveAmount(updates.getAcctCreditLimit(), "creditLimit");
            existing.setAcctCreditLimit(updates.getAcctCreditLimit());
        }
        if (updates.getAcctCashCreditLimit() != null) {
            validatePositiveAmount(updates.getAcctCashCreditLimit(), "cashCreditLimit");
            existing.setAcctCashCreditLimit(updates.getAcctCashCreditLimit());
        }
        if (updates.getAcctOpenDate() != null) {
            validateDateFormat(updates.getAcctOpenDate(), "openDate");
            existing.setAcctOpenDate(updates.getAcctOpenDate());
        }
        if (updates.getAcctExpirationDate() != null) {
            validateDateFormat(updates.getAcctExpirationDate(), "expirationDate");
            existing.setAcctExpirationDate(updates.getAcctExpirationDate());
        }
        if (updates.getAcctReissueDate() != null) {
            validateDateFormat(updates.getAcctReissueDate(), "reissueDate");
            existing.setAcctReissueDate(updates.getAcctReissueDate());
        }
        if (updates.getAcctCurrCycCredit() != null) {
            existing.setAcctCurrCycCredit(updates.getAcctCurrCycCredit());
        }
        if (updates.getAcctCurrCycDebit() != null) {
            existing.setAcctCurrCycDebit(updates.getAcctCurrCycDebit());
        }
        if (updates.getAcctAddrZip() != null) {
            existing.setAcctAddrZip(updates.getAcctAddrZip());
        }
        if (updates.getAcctGroupId() != null) {
            existing.setAcctGroupId(updates.getAcctGroupId());
        }

        return accountRepository.save(existing);
    }

    private void validateActiveStatus(String status) {
        if (!"Y".equals(status) && !"N".equals(status)) {
            throw new IllegalArgumentException(
                    "acctActiveStatus must be 'Y' or 'N', got: " + status);
        }
    }

    private void validatePositiveAmount(BigDecimal amount, String field) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }

    private void validateDateFormat(String date, String field) {
        if (date.length() != 10 || date.charAt(4) != '-' || date.charAt(7) != '-') {
            throw new IllegalArgumentException(
                    field + " must be in YYYY-MM-DD format, got: " + date);
        }
        try {
            int year = Integer.parseInt(date.substring(0, 4));
            int month = Integer.parseInt(date.substring(5, 7));
            int day = Integer.parseInt(date.substring(8, 10));
            if (year < 1900 || year > 2999 || month < 1 || month > 12 || day < 1 || day > 31) {
                throw new IllegalArgumentException(
                        field + " contains invalid date components: " + date);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    field + " must be in YYYY-MM-DD format, got: " + date);
        }
    }

    public static class AccountNotFoundException extends RuntimeException {
        public AccountNotFoundException(Long acctId) {
            super("Account not found: " + acctId);
        }
    }
}
