package com.carddemo.service;

import com.carddemo.api.AccountUpdateRequest;
import com.carddemo.api.CobolApiException;
import com.carddemo.api.CobolMessages;
import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.Customer;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import com.carddemo.service.AccountUpdateScreen.Flag;
import com.carddemo.service.AccountUpdateScreen.State;
import jakarta.persistence.LockTimeoutException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * COACTUPC — the six-state account-update program (ACUP-CHANGE-ACTION,
 * cbl:654-668). The screen is stateless: the fetched snapshot rides each
 * request in place of WS-THIS-PROGCOMMAREA (S03-B3), so save re-runs the
 * compare and the edit ladder before touching the database.
 */
@Service
public class AccountUpdateService {
    private final AccountRepository accountRepository;
    private final CardXrefRepository cardXrefRepository;
    private final CustomerRepository customerRepository;
    private final AccountUpdateEditRules editRules;
    private final DataSource dataSource;

    public AccountUpdateService(AccountRepository accountRepository,
                                CardXrefRepository cardXrefRepository,
                                CustomerRepository customerRepository,
                                AccountUpdateEditRules editRules,
                                DataSource dataSource) {
        this.accountRepository = accountRepository;
        this.cardXrefRepository = cardXrefRepository;
        this.customerRepository = customerRepository;
        this.editRules = editRules;
        this.dataSource = dataSource;
    }

    public AccountUpdateScreen initialScreen() {
        return search("", false, null);
    }

    /** ENTER while in Search — 1210-EDIT-ACCOUNT then 9000-READ-ACCT. */
    public AccountUpdateScreen lookup(String rawAcctId) {
        String id = AccountUpdateEditRules.norm(rawAcctId);
        if (id.isEmpty()) {
            return search(id, true, CobolMessages.NO_INPUT_RECEIVED);
        }
        if (!id.matches("\\d{11}") || Long.parseLong(id) == 0) {
            return search(id, true, CobolMessages.ACCOUNT_UPDATE_ID_INVALID);
        }
        long accountId = Long.parseLong(id);
        CardXref xref;
        try {
            xref = cardXrefRepository.findByXrefAcctId(accountId).stream()
                    .findFirst().orElse(null);
        } catch (DataAccessException e) {
            return search(id, true, CobolMessages.fileError("CXACAIX"));
        }
        if (xref == null) {
            return search(id, true, CobolMessages.xrefNotFound(id));
        }
        Account account;
        try {
            account = accountRepository.findById(accountId).orElse(null);
        } catch (DataAccessException e) {
            return search(id, true, CobolMessages.fileError("ACCTDAT"));
        }
        if (account == null) {
            return search(id, true, CobolMessages.accountNotFound(id));
        }
        Customer customer;
        try {
            customer = customerRepository.findById(xref.getXrefCustId())
                    .orElse(null);
        } catch (DataAccessException e) {
            return search(id, true, CobolMessages.fileError("CUSTDAT"));
        }
        if (customer == null) {
            return search(id, true, CobolMessages.customerNotFound(
                    "%09d".formatted(xref.getXrefCustId())));
        }
        AccountUpdateSnapshot original = AccountUpdateSnapshot.of(account, customer);
        return details(original, null, "acsttus");
    }

    /** ENTER while showing details/edits — 1205 compare then the ladder. */
    public AccountUpdateScreen validate(AccountUpdateSnapshot original,
                                        AccountUpdateForm form) {
        requireSnapshot(original);
        if (!editRules.changed(original, form)) {
            return details(original, CobolMessages.NO_CHANGES_DETECTED, "acsttus");
        }
        AccountUpdateEditRules.Result edits = editRules.validate(form);
        if (!edits.valid()) {
            return editError(original, form, edits);
        }
        return confirm(original, form);
    }

    /** PF12 — re-read and redisplay (FR-S03-31/32). */
    public AccountUpdateScreen cancel(AccountUpdateSnapshot original) {
        requireSnapshot(original);
        return lookup("%011d".formatted(original.accountId()));
    }

    /**
     * Carries the FAILED screen across the transactional boundary so the
     * proxy rolls the transaction back — the SYNCPOINT ROLLBACK after a
     * failed REWRITE (D1). Callers unwrap it for the screen.
     */
    public static class ScreenRollbackException extends RuntimeException {
        private final AccountUpdateScreen screen;

        public ScreenRollbackException(AccountUpdateScreen screen) {
            this.screen = screen;
        }

        public AccountUpdateScreen screen() {
            return screen;
        }
    }

    /** PF5 on the confirm screen — 9600-WRITE-PROCESSING. */
    @Transactional
    public AccountUpdateScreen save(AccountUpdateSnapshot original,
                                    AccountUpdateForm form) {
        requireSnapshot(original);
        // S03-B3: the snapshot is user-supplied, so rerun the gate and ladder
        // exactly as 1000-PROCESS-INPUTS does before 9600 is reached.
        if (!editRules.changed(original, form)) {
            return details(original, CobolMessages.NO_CHANGES_DETECTED, "acsttus");
        }
        AccountUpdateEditRules.Result edits = editRules.validate(form);
        if (!edits.valid()) {
            return editError(original, form, edits);
        }

        // READ UPDATE on each file, in source order. The probe runs on a
        // throwaway connection first: engines like H2 mark a timed-out
        // connection unusable, which would fail even the rollback — the
        // JPA FOR UPDATE below remains the real lock for the write.
        if (!lockProbeOk("SELECT acct_id FROM accounts"
                + " WHERE acct_id = ? FOR UPDATE", original.accountId())) {
            return failed(original, form, CobolMessages.COULD_NOT_LOCK_ACCOUNT);
        }
        if (!lockProbeOk("SELECT cust_id FROM customers"
                + " WHERE cust_id = ? FOR UPDATE", original.customerId())) {
            return failed(original, form, CobolMessages.COULD_NOT_LOCK_CUSTOMER);
        }
        Account account;
        try {
            account = accountRepository.findByIdForUpdate(original.accountId())
                    .orElse(null);
        } catch (RuntimeException e) {
            throw new ScreenRollbackException(
                    failed(original, form, CobolMessages.COULD_NOT_LOCK_ACCOUNT));
        }
        // A NOTFND on READ UPDATE is reported as a lock failure (cbl:3923-3927).
        if (account == null) {
            throw new ScreenRollbackException(
                    failed(original, form, CobolMessages.COULD_NOT_LOCK_ACCOUNT));
        }
        Customer customer;
        try {
            customer = customerRepository.findByIdForUpdate(original.customerId())
                    .orElse(null);
        } catch (RuntimeException e) {
            throw new ScreenRollbackException(
                    failed(original, form, CobolMessages.COULD_NOT_LOCK_CUSTOMER));
        }
        if (customer == null) {
            throw new ScreenRollbackException(
                    failed(original, form, CobolMessages.COULD_NOT_LOCK_CUSTOMER));
        }
        // 9700 compare-miss: nothing was written, so the read-only
        // transaction can commit — the lock release is the rollback's work.
        if (!matchesAccount(original, account) || !matchesCustomer(original, customer)) {
            return details(original, CobolMessages.RECORD_CHANGED, "acsttus");
        }
        try {
            apply(form, account, customer);
            accountRepository.save(account);
            customerRepository.saveAndFlush(customer);
        } catch (RuntimeException e) {
            throw new ScreenRollbackException(
                    failed(original, form, CobolMessages.UPDATE_FAILED));
        }
        return done(original, form);
    }

    /**
     * REST PUT — the path id routes; the snapshot supplies CC-ACCT-ID.
     * Transactional here (not just on {@link #save}): the call below is a
     * self-invocation that bypasses the proxy.
     */
    @Transactional
    public AccountUpdateScreen update(String rawAccountId,
                                      AccountUpdateRequest request) {
        String id = rawAccountId == null ? "" : rawAccountId;
        if (!id.matches("\\d{1,11}") || Long.parseLong(id) == 0) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST,
                    CobolMessages.ACCOUNT_UPDATE_ID_INVALID);
        }
        return save(request.original(), request.updated());
    }

    // ---------------------------------------------------------------

    /**
     * Redisplay of the current state for an unaccepted AID — the screen
     * keeps its fields, flags, and snapshot; only the error line changes
     * (1200-EDIT-MAP-INPUTS invalid-key path, cbl:2955-2982 info per state).
     */
    public AccountUpdateScreen invalidAid(State state, AccountUpdateForm form,
                                          AccountUpdateSnapshot original,
                                          String error) {
        String info = switch (state == null ? State.SEARCH : state) {
            case SEARCH -> CobolMessages.ACCOUNT_UPDATE_PROMPT;
            case DETAILS, EDIT_ERROR -> CobolMessages.ACCOUNT_UPDATE_DETAILS;
            case CONFIRM -> CobolMessages.ACCOUNT_UPDATE_CONFIRM;
            case DONE -> CobolMessages.ACCOUNT_UPDATE_COMMITTED;
            case FAILED -> CobolMessages.ACCOUNT_UPDATE_UNSUCCESSFUL;
        };
        return new AccountUpdateScreen(state == null ? State.SEARCH : state,
                form == null ? "" : form.acctId(), false, info, error,
                form == null ? AccountUpdateForm.blank() : form, original,
                Map.of(), "acsttus");
    }

    /**
     * FOR UPDATE contention probe on an auto-released pooled connection.
     * A blocked read fails here (conn discarded) rather than inside the
     * write transaction, where the broken connection would also kill the
     * SYNCPOINT ROLLBACK. Returns false when the row cannot be locked.
     */
    private boolean lockProbeOk(String sql, long id) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                }
            }
            conn.rollback();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void requireSnapshot(AccountUpdateSnapshot original) {
        if (original == null || original.accountId() == null) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST,
                    CobolMessages.SNAPSHOT_REQUIRED);
        }
    }

    private AccountUpdateScreen search(String echo, boolean accountRed,
                                       String error) {
        return new AccountUpdateScreen(State.SEARCH, echo, accountRed,
                CobolMessages.ACCOUNT_UPDATE_PROMPT, error,
                AccountUpdateForm.blank(), null, Map.of(), "acctsid");
    }

    private AccountUpdateScreen details(AccountUpdateSnapshot original,
                                        String error, String cursorField) {
        return new AccountUpdateScreen(State.DETAILS,
                "%011d".formatted(original.accountId()), false,
                CobolMessages.ACCOUNT_UPDATE_DETAILS, error,
                original.displayForm(), original, Map.of(), cursorField);
    }

    private AccountUpdateScreen editError(AccountUpdateSnapshot original,
                                          AccountUpdateForm form,
                                          AccountUpdateEditRules.Result edits) {
        return new AccountUpdateScreen(State.EDIT_ERROR, form.acctId(), false,
                CobolMessages.ACCOUNT_UPDATE_DETAILS, edits.firstMessage(),
                form, original, edits.flags(), edits.firstInvalidField());
    }

    private AccountUpdateScreen confirm(AccountUpdateSnapshot original,
                                        AccountUpdateForm form) {
        return new AccountUpdateScreen(State.CONFIRM, form.acctId(), false,
                CobolMessages.ACCOUNT_UPDATE_CONFIRM, null,
                form, original, Map.of(), "acsttus");
    }

    private AccountUpdateScreen done(AccountUpdateSnapshot original,
                                     AccountUpdateForm form) {
        return new AccountUpdateScreen(State.DONE, form.acctId(), false,
                CobolMessages.ACCOUNT_UPDATE_COMMITTED, null,
                form, original, Map.of(), "acsttus");
    }

    private AccountUpdateScreen failed(AccountUpdateSnapshot original,
                                       AccountUpdateForm form, String error) {
        return new AccountUpdateScreen(State.FAILED, form.acctId(), false,
                CobolMessages.ACCOUNT_UPDATE_UNSUCCESSFUL, error,
                form, original, Map.of(), "acctsid");
    }


    // 9700-CHECK-CHANGE-IN-REC: freshly locked rows vs the fetched snapshot.
    private boolean matchesAccount(AccountUpdateSnapshot s, Account a) {
        return Objects.equals(s.activeStatus(), a.getAcctActiveStatus())
                && moneyEq(s.currentBalance(), a.getAcctCurrBal())
                && moneyEq(s.creditLimit(), a.getAcctCreditLimit())
                && moneyEq(s.cashCreditLimit(), a.getAcctCashCreditLimit())
                && Objects.equals(s.openDate(), a.getAcctOpenDate())
                && Objects.equals(s.expirationDate(), a.getAcctExpirationDate())
                && Objects.equals(s.reissueDate(), a.getAcctReissueDate())
                && moneyEq(s.currentCycleCredit(), a.getAcctCurrCycCredit())
                && moneyEq(s.currentCycleDebit(), a.getAcctCurrCycDebit())
                && lowerEq(s.accountGroup(), a.getAcctGroupId());
    }

    private boolean matchesCustomer(AccountUpdateSnapshot s, Customer c) {
        return Objects.equals(s.customerId(), c.getCustId())
                && upStoredEq(s.firstName(), c.getCustFirstName())
                && upStoredEq(s.middleName(), c.getCustMiddleName())
                && upStoredEq(s.lastName(), c.getCustLastName())
                && upStoredEq(s.addressLine1(), c.getCustAddrLine1())
                && upStoredEq(s.addressLine2(), c.getCustAddrLine2())
                && upStoredEq(s.addressLine3(), c.getCustAddrLine3())
                && upStoredEq(s.stateCode(), c.getCustAddrStateCode())
                && upStoredEq(s.countryCode(), c.getCustAddrCountryCode())
                && xEq(s.zip(), c.getCustAddrZip())
                && xEq(s.phoneNumber1(), c.getCustPhoneNum1())
                && xEq(s.phoneNumber2(), c.getCustPhoneNum2())
                && Objects.equals(s.ssn(), c.getCustSsn())
                && upStoredEq(s.governmentIssuedId(), c.getCustGovernmentIssuedId())
                && Objects.equals(s.dateOfBirth(), c.getCustDob())
                && xEq(s.eftAccountId(), c.getCustEftAccountId())
                && Objects.equals(s.primaryCardHolderIndicator(),
                        c.getCustPrimaryCardHolderIndicator())
                && Objects.equals(s.ficoScore(), c.getCustFicoCreditScore());
    }

    private boolean moneyEq(BigDecimal snapshot, BigDecimal stored) {
        if (snapshot == null || stored == null) {
            return snapshot == null && stored == null;
        }
        return snapshot.compareTo(stored) == 0;
    }

    private boolean lowerEq(String snapshot, String stored) {
        return snapshot == null ? stored == null
                : snapshot.trim().equalsIgnoreCase(stored == null ? null : stored.trim());
    }

    private boolean upStoredEq(String snapshot, String stored) {
        String left = snapshot == null ? "" : snapshot.trim().toUpperCase();
        String right = stored == null ? "" : stored.trim().toUpperCase();
        return left.equals(right);
    }

    private boolean xEq(String snapshot, String stored) {
        String left = snapshot == null ? "" : rtrim(snapshot);
        String right = stored == null ? "" : rtrim(stored);
        return left.equals(right);
    }

    private static String rtrim(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(0, end);
    }

    // 9630-WRITE-TO-ACCT / 9650-WRITE-TO-CUST (cbl:3945-4059). Every value
    // moves through its fixed screen width, the same as ACUP-NEW-* fields.
    private void apply(AccountUpdateForm f, Account account, Customer customer) {
        account.setAcctActiveStatus(take(f.activeStatus(), 1));
        account.setAcctCurrBal(
                AccountUpdateEditRules.numvalC(take(f.currentBalance(), 15)));
        account.setAcctCreditLimit(
                AccountUpdateEditRules.numvalC(take(f.creditLimit(), 15)));
        account.setAcctCashCreditLimit(
                AccountUpdateEditRules.numvalC(take(f.cashCreditLimit(), 15)));
        account.setAcctOpenDate(date(f.openYear(), f.openMon(), f.openDay()));
        account.setAcctExpirationDate(date(f.expYear(), f.expMon(), f.expDay()));
        account.setAcctReissueDate(date(f.risYear(), f.risMon(), f.risDay()));
        account.setAcctCurrCycCredit(
                AccountUpdateEditRules.numvalC(take(f.currentCycleCredit(), 15)));
        account.setAcctCurrCycDebit(
                AccountUpdateEditRules.numvalC(take(f.currentCycleDebit(), 15)));
        account.setAcctGroupId(rtrim(take(f.accountGroup(), 10)));
        customer.setCustSsn(Long.parseLong(take(f.ssn1(), 3) + take(f.ssn2(), 2)
                + take(f.ssn3(), 4)));
        customer.setCustDob(date(f.dobYear(), f.dobMon(), f.dobDay()));
        customer.setCustFicoCreditScore(
                Integer.parseInt(take(f.ficoScore(), 3)));
        customer.setCustFirstName(rtrim(take(f.firstName(), 25)));
        customer.setCustMiddleName(rtrim(take(f.middleName(), 25)));
        customer.setCustLastName(rtrim(take(f.lastName(), 25)));
        customer.setCustAddrLine1(rtrim(take(f.addrLine1(), 50)));
        customer.setCustAddrLine2(rtrim(take(f.addrLine2(), 50)));
        customer.setCustAddrLine3(rtrim(take(f.city(), 50)));
        customer.setCustAddrStateCode(rtrim(take(f.state(), 2)));
        customer.setCustAddrCountryCode(rtrim(take(f.country(), 3)));
        customer.setCustAddrZip(rtrim(take(f.zip(), 5)));
        customer.setCustPhoneNum1(phone(take(f.phone1a(), 3), take(f.phone1b(), 3),
                take(f.phone1c(), 4)));
        customer.setCustPhoneNum2(phone(take(f.phone2a(), 3), take(f.phone2b(), 3),
                take(f.phone2c(), 4)));
        customer.setCustGovernmentIssuedId(rtrim(take(f.governmentId(), 20)));
        customer.setCustEftAccountId(rtrim(take(f.eftAccountId(), 10)));
        customer.setCustPrimaryCardHolderIndicator(take(f.priCardHolder(), 1));
    }

    private static String take(String value, int len) {
        String v = AccountUpdateEditRules.norm(value);
        return v.length() > len ? v.substring(0, len) : v;
    }

    private static LocalDate date(String y, String m, String d) {
        return LocalDate.of(Integer.parseInt(y), Integer.parseInt(m),
                Integer.parseInt(d));
    }

    // '(' + A + ')' + B + '-' + C — a fully blank number stores "()-"
    // exactly as the STRING statements do (FR-S03-30/34).
    private static String phone(String a, String b, String c) {
        return "(" + a + ")" + b + "-" + c;
    }
}
