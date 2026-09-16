package com.carddemo.service;

import com.carddemo.api.BillPaymentScreen;
import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.Transaction;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.TransactionRepository;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * COBIL00C processing (S-11): the FR-S11-01..15 edit/confirm/write matrix
 * plus the D1 blocking/atomic deviation, derived from COBIL00C.cbl and its
 * FR doc. Account, xref and transaction stores are mocked at the
 * S11-B1/B2/B3 seams; the tran-id generator and Clock run for real.
 * Test names carry the FR row each covers.
 */
class BillingServiceTest {

    private static final String ACCT = "00000000001";
    private static final String CARD = "1111222233334444";
    private static final BigDecimal BALANCE = new BigDecimal("1234.56");
    private static final Instant FIXED = Instant.parse("2024-02-03T04:05:06.789Z");

    private AccountRepository accounts;
    private CardXrefRepository xrefs;
    private TransactionRepository transactions;
    private EntityManager entityManager;
    private BillingService service;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        xrefs = mock(CardXrefRepository.class);
        transactions = mock(TransactionRepository.class);
        entityManager = mock(EntityManager.class);
        Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
        service = new BillingService(accounts, xrefs, entityManager,
                new TransactionIdGenerator(transactions), clock, immediateTx());
        when(accounts.findForUpdate(anyLong())).thenReturn(Optional.empty());
        stubAccount(BALANCE);
        stubXref();
    }

    // The service wraps its post-edit work in a real transaction; in unit
    // tests the boundary commits/rolls back a no-op status.
    private static PlatformTransactionManager immediateTx() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        return manager;
    }

    private Account stubAccount(BigDecimal balance) {
        Account account = new Account();
        account.setAcctId(1L);
        account.setAcctCurrBal(balance);
        when(accounts.findForUpdate(1L)).thenReturn(Optional.of(account));
        return account;
    }

    private void stubXref() {
        CardXref xref = new CardXref();
        xref.setXrefAcctId(1L);
        xref.setXrefCardNumber(CARD);
        when(xrefs.findByXrefAcctId(1L)).thenReturn(List.of(xref));
    }

    @Test
    void blankAccountIdRejectedBeforeAnyRead_frS1101() {
        BillPaymentScreen screen = service.enter("   ", "Y", "+0000001234.56");
        assertThat(screen.message()).isEqualTo("Acct ID can NOT be empty...");
        assertThat(screen.cursorField()).isEqualTo("accountId");
        assertThat(screen.currentBalance()).isEqualTo("+0000001234.56");
        verifyNoInteractions(accounts, xrefs, transactions);
    }

    @Test
    void invalidConfirmRejectedBeforeAnyRead_frS1102() {
        BillPaymentScreen screen = service.enter(ACCT, "Q", "+0000001234.56");
        assertThat(screen.message()).isEqualTo("Invalid value. Valid values are (Y/N)...");
        assertThat(screen.cursorField()).isEqualTo("confirmation");
        // No account lookup; the previously displayed balance is kept.
        assertThat(screen.currentBalance()).isEqualTo("+0000001234.56");
        verifyNoInteractions(accounts, xrefs, transactions);
    }

    @Test
    void declineNClearsScreenWithoutAnyRead_frS1103() {
        BillPaymentScreen screen = service.enter("99999999999", "n", "+0000001234.56");
        assertThat(screen.accountId()).isEmpty();
        assertThat(screen.currentBalance()).isEmpty();
        assertThat(screen.confirmation()).isEmpty();
        assertThat(screen.message()).isNull();
        assertThat(screen.cursorField()).isEqualTo("accountId");
        verifyNoInteractions(accounts, xrefs, transactions);
    }

    @Test
    void unknownAccountReadsAsNotFound_frS1104() {
        BillPaymentScreen screen = service.enter("99999999999", "", null);
        assertThat(screen.message()).isEqualTo("Account ID NOT found...");
        assertThat(screen.cursorField()).isEqualTo("accountId");
    }

    @Test
    void accountKeyComparedAsTyped_frS1104() {
        // R3: "123" is not the %011d rendering of acct 1 — it never reaches
        // the store, exactly like the padded VSAM key compare.
        BillPaymentScreen screen = service.enter("123", "", null);
        assertThat(screen.message()).isEqualTo("Account ID NOT found...");
        verify(accounts, never()).findForUpdate(anyLong());

        BillPaymentScreen alpha = service.enter("ABCDEFGHIJK", "", null);
        assertThat(alpha.message()).isEqualTo("Account ID NOT found...");
        verify(accounts, never()).findForUpdate(anyLong());
    }

    @Test
    void accountStoreErrorSurfaced_frS1105() {
        when(accounts.findForUpdate(1L))
                .thenThrow(new DataAccessException("store down") { });
        BillPaymentScreen screen = service.enter(ACCT, "", null);
        assertThat(screen.message()).isEqualTo("Unable to lookup Account...");
        assertThat(screen.cursorField()).isEqualTo("accountId");
    }

    @Test
    void balanceRendersSignedFourteenCharEdit_frS1106() {
        BillPaymentScreen screen = service.enter(ACCT, "", null);
        assertThat(screen.currentBalance()).isEqualTo("+0000001234.56")
                .hasSize(14);

        stubAccount(new BigDecimal("-50.00"));
        BillPaymentScreen negative = service.enter(ACCT, "", null);
        assertThat(negative.currentBalance()).isEqualTo("-0000000050.00");
    }

    @Test
    void nonPositiveBalanceHasNothingToPay_frS1107() {
        stubAccount(BigDecimal.ZERO);
        BillPaymentScreen screen = service.enter(ACCT, "Y", null);
        assertThat(screen.message()).isEqualTo("You have nothing to pay...");
        assertThat(screen.cursorField()).isEqualTo("accountId");
        assertThat(screen.currentBalance()).isEqualTo("+0000000000.00");
        verify(entityManager, never()).persist(any());

        stubAccount(new BigDecimal("-50.00"));
        BillPaymentScreen negative = service.enter(ACCT, "", null);
        assertThat(negative.message()).isEqualTo("You have nothing to pay...");
        assertThat(negative.currentBalance()).isEqualTo("-0000000050.00");
        verify(entityManager, never()).persist(any());
    }

    @Test
    void blankConfirmPromptsBeforeWriting_frS1108() {
        BillPaymentScreen screen = service.enter(ACCT, "", null);
        assertThat(screen.message()).isEqualTo("Confirm to make a bill payment...");
        assertThat(screen.cursorField()).isEqualTo("confirmation");
        assertThat(screen.currentBalance()).isEqualTo("+0000001234.56");
        verify(entityManager, never()).persist(any());
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void xrefMissReadsAsAccountNotFound_frS1109() {
        when(xrefs.findByXrefAcctId(1L)).thenReturn(List.of());
        BillPaymentScreen screen = service.enter(ACCT, "Y", null);
        assertThat(screen.message()).isEqualTo("Account ID NOT found...");
        assertThat(screen.cursorField()).isEqualTo("accountId");
        verify(entityManager, never()).persist(any());
    }

    @Test
    void xrefStoreErrorSurfaced_frS1109() {
        when(xrefs.findByXrefAcctId(1L))
                .thenThrow(new DataAccessException("store down") { });
        BillPaymentScreen screen = service.enter(ACCT, "Y", null);
        assertThat(screen.message()).isEqualTo("Unable to lookup XREF AIX file...");
        assertThat(screen.cursorField()).isEqualTo("accountId");
        verify(entityManager, never()).persist(any());
    }

    @Test
    void emptyTransactionFileAllocatesOne_frS1110() {
        BillPaymentScreen screen = service.enter(ACCT, "Y", null);
        assertThat(screen.transactionId()).isEqualTo("0000000000000001");
    }

    @Test
    void transactionIdIsHighestKeyPlusOne_frS1110() {
        Transaction last = new Transaction();
        last.setTranId("0000000000000123");
        when(transactions.findTopByOrderByTranIdDesc()).thenReturn(last);
        BillPaymentScreen screen = service.enter(ACCT, "Y", null);
        assertThat(screen.transactionId()).isEqualTo("0000000000000124");
    }

    @Test
    void transactionIdBrowseErrorSurfaced_frS1110() {
        when(transactions.findTopByOrderByTranIdDesc())
                .thenThrow(new DataAccessException("browse failed") { });
        BillPaymentScreen screen = service.enter(ACCT, "Y", null);
        assertThat(screen.message()).isEqualTo("Unable to lookup Transaction...");
        assertThat(screen.cursorField()).isEqualTo("accountId");
        verify(entityManager, never()).persist(any());
    }

    @Test
    void storedTransactionCarriesTheBillPaymentRecord_frS1111() {
        service.enter(ACCT, "Y", null);
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(entityManager).persist(captor.capture());
        Transaction stored = captor.getValue();
        assertThat(stored.getTranId()).isEqualTo("0000000000000001");
        assertThat(stored.getTranTypeCode()).isEqualTo("02");
        assertThat(stored.getTranCategoryCode()).isEqualTo(2);
        assertThat(stored.getTranSource()).isEqualTo("POS TERM");
        assertThat(stored.getTranDescription()).isEqualTo("BILL PAYMENT - ONLINE");
        assertThat(stored.getTranAmount()).isEqualByComparingTo("1234.56");
        assertThat(stored.getTranCardNumber()).isEqualTo(CARD);
        assertThat(stored.getTranMerchantId()).isEqualTo(999999999L);
        assertThat(stored.getTranMerchantName()).isEqualTo("BILL PAYMENT");
        assertThat(stored.getTranMerchantCity()).isEqualTo("N/A");
        assertThat(stored.getTranMerchantZip()).isEqualTo("N/A");
    }

    @Test
    void timestampsAreInjectedClockAtSecondPrecision_frS1111() {
        service.enter(ACCT, "Y", null);
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(entityManager).persist(captor.capture());
        LocalDateTime expected = LocalDateTime.of(2024, 2, 3, 4, 5, 6);
        assertThat(captor.getValue().getTranOriginTimestamp()).isEqualTo(expected);
        assertThat(captor.getValue().getTranProcessTimestamp()).isEqualTo(expected);
        assertThat(captor.getValue().getTranOriginTimestamp().getNano()).isZero();
    }

    @Test
    void tranAmtDropsTheHighOrderDigitAboveOneBillion_frS1111() {
        // S9(09)V99 move (COBIL00C.cbl:223): |balance| >= 1e9 keeps the
        // low-order digits; the residual stays on the account (R4).
        stubAccount(new BigDecimal("1234567890.12"));
        service.enter(ACCT, "Y", null);
        ArgumentCaptor<Transaction> tran = ArgumentCaptor.forClass(Transaction.class);
        verify(entityManager).persist(tran.capture());
        assertThat(tran.getValue().getTranAmount()).isEqualByComparingTo("234567890.12");
        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getAcctCurrBal()).isEqualByComparingTo("1000000000.00");
    }

    @Test
    void confirmedPaymentClearsFieldsAndShowsGreenSuccess_frS1112() {
        BillPaymentScreen screen = service.enter(ACCT, "y", null);
        assertThat(screen.message()).isEqualTo(
                "Payment successful.  Your Transaction ID is 0000000000000001.");
        assertThat(screen.messageStyle()).isEqualTo("info");
        assertThat(screen.accountId()).isEmpty();
        assertThat(screen.currentBalance()).isEmpty();
        assertThat(screen.confirmation()).isEmpty();
        assertThat(screen.cursorField()).isEqualTo("accountId");
        assertThat(screen.transactionId()).isEqualTo("0000000000000001");
    }

    @Test
    void duplicateTranIdAbortsBeforeTheBalanceUpdate_frS1113() {
        doThrow(new EntityExistsException("23505"))
                .when(entityManager).persist(any());
        BillPaymentScreen screen = service.enter(ACCT, "Y", null);
        assertThat(screen.message()).isEqualTo("Tran ID already exist...");
        assertThat(screen.cursorField()).isEqualTo("accountId");
        // D1: blocking + atomic — the account rewrite never runs.
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void genericWriteErrorSurfaced_frS1114() {
        doThrow(new PersistenceException("write failed"))
                .when(entityManager).persist(any());
        BillPaymentScreen screen = service.enter(ACCT, "Y", null);
        assertThat(screen.message()).isEqualTo("Unable to Add Bill pay Transaction...");
        assertThat(screen.cursorField()).isEqualTo("accountId");
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void balanceIsRewrittenMinusThePayment_frS1115() {
        service.enter(ACCT, "Y", null);
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accounts).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getAcctCurrBal()).isEqualByComparingTo("0.00");
    }

    @Test
    void accountUpdateErrorSurfaced_frS1115() {
        when(accounts.saveAndFlush(any()))
                .thenThrow(new DataAccessException("rewrite failed") { });
        BillPaymentScreen screen = service.enter(ACCT, "Y", null);
        assertThat(screen.message()).isEqualTo("Unable to Update Account...");
        assertThat(screen.cursorField()).isEqualTo("accountId");
    }
}
