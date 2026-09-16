package com.carddemo.service;

import com.carddemo.api.AccountUpdateRequest;
import com.carddemo.api.CobolApiException;
import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.Customer;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import com.carddemo.service.AccountUpdateScreen.State;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * COACTUPC state-machine parity at the service seam: xref-first lookup
 * (FR-S03-04..07), validate/save transitions (08, 23, 25-29, 31) and the
 * REST PUT gate (03). Repo behavior is stubbed; row-shape checks live in
 * AccountUpdateIntegrationTest.
 */
class AccountUpdateServiceTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final CardXrefRepository xrefRepository = mock(CardXrefRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final javax.sql.DataSource dataSource = mock(javax.sql.DataSource.class);
    private final java.sql.Connection connection = mock(java.sql.Connection.class);
    private final java.sql.PreparedStatement statement =
            mock(java.sql.PreparedStatement.class);
    private final java.sql.ResultSet resultSet = mock(java.sql.ResultSet.class);
    private final AccountUpdateService service = new AccountUpdateService(
            accountRepository, xrefRepository, customerRepository,
            new AccountUpdateEditRules(), dataSource);

    private static Account account(long id) {
        Account a = new Account();
        a.setAcctId(id);
        a.setAcctActiveStatus("Y");
        a.setAcctCurrBal(new BigDecimal("194.00"));
        a.setAcctCreditLimit(new BigDecimal("2020.00"));
        a.setAcctCashCreditLimit(new BigDecimal("1020.00"));
        a.setAcctOpenDate(LocalDate.of(2020, 1, 1));
        a.setAcctExpirationDate(LocalDate.of(2025, 1, 1));
        a.setAcctReissueDate(LocalDate.of(2025, 1, 1));
        a.setAcctCurrCycCredit(BigDecimal.ZERO);
        a.setAcctCurrCycDebit(BigDecimal.ZERO);
        a.setAcctGroupId("02108");
        return a;
    }

    private static Customer customer(long id) {
        Customer c = new Customer();
        c.setCustId(id);
        c.setCustFirstName("Ada");
        c.setCustMiddleName("");
        c.setCustLastName("Lovelace");
        c.setCustAddrLine1("1 Main");
        c.setCustAddrLine2("");
        c.setCustAddrLine3("Boston");
        c.setCustAddrStateCode("MA");
        c.setCustAddrZip("10100     ");
        c.setCustAddrCountryCode("USA");
        c.setCustPhoneNum1("(201)555-1212");
        c.setCustPhoneNum2("");
        c.setCustSsn(123456789L);
        c.setCustGovernmentIssuedId("GOV");
        c.setCustDob(LocalDate.of(1950, 12, 10));
        c.setCustEftAccountId("1234567890");
        c.setCustPrimaryCardHolderIndicator("Y");
        c.setCustFicoCreditScore(800);
        return c;
    }

    private static CardXref xref(long acctId, long custId) {
        CardXref x = new CardXref();
        x.setXrefCardNumber("1111222233334444");
        x.setXrefCustId(custId);
        x.setXrefAcctId(acctId);
        return x;
    }

    private void seedLookup() {
        when(xrefRepository.findByXrefAcctId(1L))
                .thenReturn(List.of(xref(1L, 1L)));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(1)));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1)));
    }

    private void seedLocks() throws java.sql.SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(accountRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(account(1)));
        when(customerRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(customer(1)));
    }

    private AccountUpdateForm matchingForm(AccountUpdateSnapshot original) {
        AccountUpdateForm f = original.displayForm();
        // The seeded legacy values fail the ladder (zip MA02, phone "555",
        // eft "EFT") — substitute values that pass, as a real update would.
        return new AccountUpdateForm(f.acctId(), f.activeStatus(),
                f.openYear(), f.openMon(), f.openDay(), f.creditLimit(),
                f.expYear(), f.expMon(), f.expDay(), f.cashCreditLimit(),
                f.risYear(), f.risMon(), f.risDay(), f.currentBalance(),
                f.accountGroup(), f.currentCycleCredit(), f.currentCycleDebit(),
                f.custId(), f.ssn1(), f.ssn2(), f.ssn3(),
                f.dobYear(), f.dobMon(), f.dobDay(), "801",
                f.firstName(), f.middleName(), f.lastName(),
                f.addrLine1(), f.addrLine2(), f.city(), "MA", "10100",
                f.country(), "201", "555", "1212", "", "", "",
                f.governmentId(), "1234567890", f.priCardHolder());
    }

    @Test
    void searchEditRejectsBlankStarAndNonElevenDigitIds_frS0302_frS0303() {
        // cbl:1783-1815 — blank (spaces or `*`) is NO-SEARCH-CRITERIA;
        // anything else not an 11-digit nonzero number is the invalid-id
        // message (the `*` edit is a padded compare — "**" doesn't match).
        for (String input : new String[] {"", "   ", "*", "*   "}) {
            AccountUpdateScreen s = service.lookup(input);
            assertThat(s.state()).isEqualTo(State.SEARCH);
            assertThat(s.errorMessage()).isEqualTo("No input received");
            assertThat(s.accountFieldRed()).isTrue();
        }
        for (String input : new String[] {"1", "abc", "123456789012",
                "00000000000", "**   "}) {
            AccountUpdateScreen s = service.lookup(input);
            assertThat(s.state()).isEqualTo(State.SEARCH);
            assertThat(s.errorMessage()).isEqualTo(
                    "Account Number if supplied must be a 11 digit Non-Zero Number");
        }
    }

    @Test
    void lookupReadsXrefThenAccountThenCustomer_frS0307() {
        seedLookup();
        AccountUpdateScreen s = service.lookup("00000000001");
        assertThat(s.state()).isEqualTo(State.DETAILS);
        assertThat(s.infoMessage())
                .isEqualTo("Update account details presented above.");
        assertThat(s.original().accountId()).isEqualTo(1L);
        assertThat(s.original().customerId()).isEqualTo(1L);
        assertThat(s.fields().ficoScore()).isEqualTo("800");
        assertThat(s.fields().custId()).isEqualTo("000000001");
    }

    @Test
    void lookupStopsAtTheFirstMissingRecord_frS0304_frS0305_frS0306() {
        when(xrefRepository.findByXrefAcctId(2L)).thenReturn(List.of());
        assertThat(service.lookup("00000000002").errorMessage()).isEqualTo(
                "Account:00000000002 not found in Cross ref file.  "
                        + "Resp:000000013  Reas:0000");

        when(xrefRepository.findByXrefAcctId(3L))
                .thenReturn(List.of(xref(3L, 1L)));
        when(accountRepository.findById(3L)).thenReturn(Optional.empty());
        assertThat(service.lookup("00000000003").errorMessage()).isEqualTo(
                "Account:00000000003 not found in Acct Master file."
                        + "Resp:000000013  Reas:0000");

        when(xrefRepository.findByXrefAcctId(4L))
                .thenReturn(List.of(xref(4L, 999L)));
        when(accountRepository.findById(4L))
                .thenReturn(Optional.of(account(4)));
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());
        assertThat(service.lookup("00000000004").errorMessage()).isEqualTo(
                "CustId:000000999 not found in customer master."
                        + "Resp: 000000013  REAS:0000000");
    }

    @Test
    void validateNoChangeKeepsDetails_frS0308() {
        seedLookup();
        AccountUpdateScreen fetched = service.lookup("00000000001");
        // Echo the display derivations — equals the snapshot per 1205.
        AccountUpdateScreen s = service.validate(fetched.original(),
                fetched.original().displayForm());
        assertThat(s.state()).isEqualTo(State.DETAILS);
        assertThat(s.errorMessage())
                .isEqualTo("No change detected with respect to values fetched.");
    }

    @Test
    void validateRunsLadderToConfirmOrEditError_frS0309_frS0323_frS0324() {
        seedLookup();
        AccountUpdateSnapshot original =
                service.lookup("00000000001").original();
        AccountUpdateScreen ok = service.validate(original, matchingForm(original));
        assertThat(ok.state()).isEqualTo(State.CONFIRM);
        assertThat(ok.infoMessage()).isEqualTo("Changes validated.Press F5 to save");
        assertThat(ok.f5Lit()).isTrue();
        assertThat(ok.f12Lit()).isTrue();

        AccountUpdateForm bad = new AccountUpdateForm("00000000001", "X",
                "2020", "01", "01", "2020.00", "2025", "01", "01",
                "1020.00", "2025", "01", "01", "194.00", "02108",
                "0.00", "0.00", "000000001", "123", "45", "6789",
                "1950", "12", "10", "800", "Ada", "", "Lovelace",
                "1 Main", "", "Boston", "MA", "10100", "USA",
                "201", "555", "1212", "", "", "", "GOV", "1234567890", "Y");
        AccountUpdateScreen failed = service.validate(original, bad);
        assertThat(failed.state()).isEqualTo(State.EDIT_ERROR);
        assertThat(failed.errorMessage())
                .isEqualTo("Account Status must be Y or N.");
        assertThat(failed.flags()).containsKey("acsttus");
    }

    @Test
    void saveCommitsAndAppliesTheWriteMapping_frS0325_frS0334()
            throws Exception {
        seedLocks();
        Account account = account(1);
        Customer customer = customer(1);
        AccountUpdateSnapshot original =
                AccountUpdateSnapshot.of(account, customer);
        AccountUpdateForm form = matchingForm(original);
        AccountUpdateScreen s = service.save(original, form);
        assertThat(s.state()).isEqualTo(State.DONE);
        assertThat(s.infoMessage())
                .isEqualTo("Changes committed to database");
        // cbl:3945-4059 — account fields plus the customer side mapping.
        ArgumentCaptor<Account> acct = ArgumentCaptor.forClass(Account.class);
        org.mockito.Mockito.verify(accountRepository).save(acct.capture());
        assertThat(acct.getValue().getAcctActiveStatus()).isEqualTo("Y");
        ArgumentCaptor<Customer> cust = ArgumentCaptor.forClass(Customer.class);
        org.mockito.Mockito.verify(customerRepository).saveAndFlush(cust.capture());
        Customer written = cust.getValue();
        assertThat(written.getCustFicoCreditScore()).isEqualTo(801);
        assertThat(written.getCustPhoneNum1()).isEqualTo("(201)555-1212");
        assertThat(written.getCustPhoneNum2()).isEqualTo("()-");
        assertThat(written.getCustAddrZip()).isEqualTo("10100");
        assertThat(written.getCustSsn()).isEqualTo(123456789L);
    }

    private static AccountUpdateScreen failedScreen(Runnable call) {
        Throwable thrown = org.assertj.core.api.Assertions
                .catchThrowable(call::run);
        assertThat(thrown).isInstanceOf(
                AccountUpdateService.ScreenRollbackException.class);
        return ((AccountUpdateService.ScreenRollbackException) thrown).screen();
    }

    @Test
    void saveLockFailuresReportAndKeepValues_frS0326_frS0327()
            throws Exception {
        AccountUpdateSnapshot original =
                AccountUpdateSnapshot.of(account(1), customer(1));
        AccountUpdateForm form = matchingForm(original);
        // Contention on the account row fails the first probe.
        seedLocks();
        org.mockito.Mockito.doThrow(new java.sql.SQLException("locked"))
                .when(statement).executeQuery();
        AccountUpdateScreen s = service.save(original, form);
        assertThat(s.state()).isEqualTo(State.FAILED);
        assertThat(s.errorMessage())
                .isEqualTo("Could not lock account record for update");
        assertThat(s.infoMessage())
                .isEqualTo("Changes unsuccessful. Please try again");
        assertThat(s.accountIdEditable()).isTrue();

        // Account probe passes; the customer row is the contended one.
        org.mockito.Mockito.doReturn(resultSet)
                .doThrow(new java.sql.SQLException("locked"))
                .when(statement).executeQuery();
        s = service.save(original, form);
        assertThat(s.state()).isEqualTo(State.FAILED);
        assertThat(s.errorMessage())
                .isEqualTo("Could not lock customer record for update");
    }

    @Test
    void saveSnapshotMismatchRollsBackToDetails_frS0328() throws Exception {
        seedLocks();
        AccountUpdateSnapshot original =
                AccountUpdateSnapshot.of(account(1), customer(1));
        AccountUpdateForm form = matchingForm(original);
        Account mutated = account(1);
        mutated.setAcctCurrBal(new BigDecimal("500.00"));
        when(accountRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(mutated));
        when(customerRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(customer(1)));
        AccountUpdateScreen s = service.save(original, form);
        assertThat(s.state()).isEqualTo(State.DETAILS);
        assertThat(s.errorMessage())
                .isEqualTo("Record changed by some one else. Please review");
    }

    @Test
    void saveWriteFailureRollsBackAndReports_frS0329() throws Exception {
        seedLocks();
        AccountUpdateSnapshot original =
                AccountUpdateSnapshot.of(account(1), customer(1));
        AccountUpdateForm form = matchingForm(original);
        when(accountRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(account(1)));
        when(customerRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(customer(1)));
        when(customerRepository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DataAccessResourceFailureException("ioerr"));
        // The write failure must cross the transactional boundary for the
        // rollback, so it surfaces as the carrier exception (D1).
        AccountUpdateScreen s =
                failedScreen(() -> service.save(original, form));
        assertThat(s.state()).isEqualTo(State.FAILED);
        assertThat(s.errorMessage()).isEqualTo("Update of record failed");
        assertThat(s.infoMessage())
                .isEqualTo("Changes unsuccessful. Please try again");
    }

    @Test
    void cancelRereadsTheRecord_frS0331() {
        seedLookup();
        AccountUpdateSnapshot original =
                service.lookup("00000000001").original();
        AccountUpdateScreen s = service.cancel(original);
        assertThat(s.state()).isEqualTo(State.DETAILS);
        assertThat(s.errorMessage()).isNull();
    }

    @Test
    void putPathEditsAndSnapshotGate_frS0303() {
        assertThatThrownBy(() -> service.update("abc",
                new AccountUpdateRequest(null, null)))
                .isInstanceOfSatisfying(CobolApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo(
                            "Account Number if supplied must be a 11 digit Non-Zero Number");
                });
        assertThatThrownBy(() -> service.update("1",
                new AccountUpdateRequest(null, null)))
                .isInstanceOfSatisfying(CobolApiException.class, e ->
                        assertThat(e.getMessage()).isEqualTo(
                                "Original values must be supplied for update."));
    }
}
