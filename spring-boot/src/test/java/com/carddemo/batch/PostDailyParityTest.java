package com.carddemo.batch;

import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.Transaction;
import com.carddemo.model.TransactionCategoryBalance;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import com.carddemo.repository.DisclosureGroupRepository;
import com.carddemo.repository.TransactionCategoryBalanceRepository;
import com.carddemo.repository.TransactionCategoryRepository;
import com.carddemo.repository.TransactionRepository;
import com.carddemo.repository.TransactionTypeRepository;
import com.carddemo.service.TransactionIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level CBTRN02C posting branches — each cites its COBOL derivation.
 * Job-level coverage of the same requirements lives in DailyPostingJobIntegrationTest.
 */
class PostDailyParityTest {

    private AccountRepository accounts;
    private CardXrefRepository xrefs;
    private TransactionRepository transactions;
    private TransactionCategoryBalanceRepository balances;
    private BatchJobService service;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        xrefs = mock(CardXrefRepository.class);
        transactions = mock(TransactionRepository.class);
        balances = mock(TransactionCategoryBalanceRepository.class);
        service = new BatchJobService(accounts, mock(CardRepository.class), xrefs,
                mock(CustomerRepository.class), transactions, balances,
                mock(DisclosureGroupRepository.class), mock(TransactionTypeRepository.class),
                mock(TransactionCategoryRepository.class), mock(TransactionIdGenerator.class),
                "target/test-post-daily");
    }

    private DailyTransactionRecord record(String card, BigDecimal amount, LocalDateTime origin) {
        return new DailyTransactionRecord("0000000000000009", "01", 1, "POS TERM",
                "Test purchase", amount, 1L, "Merchant", "Boston", "02108",
                card, origin, null, " ".repeat(350));
    }

    private CardXref xref(String card, long acctId) {
        CardXref value = new CardXref();
        value.setXrefCardNumber(card);
        value.setXrefCustId(1L);
        value.setXrefAcctId(acctId);
        return value;
    }

    private Account account(long acctId, BigDecimal creditLimit) {
        Account value = new Account();
        value.setAcctId(acctId);
        value.setAcctCurrBal(new BigDecimal("100.00"));
        value.setAcctCreditLimit(creditLimit);
        value.setAcctCurrCycCredit(BigDecimal.ZERO);
        value.setAcctCurrCycDebit(BigDecimal.ZERO);
        value.setAcctExpirationDate(LocalDate.of(2099, 1, 1));
        return value;
    }

    @Test
    void overlimitBoundaryAtExactCreditLimitPosts() {
        // CBTRN02C.cbl:407-412 — ACCT-CREDIT-LIMIT >= WS-TEMP-BAL posts; only a
        // projection strictly greater than the limit rejects.
        Account account = account(1L, new BigDecimal("100.00"));
        when(xrefs.findById("1111222233334444")).thenReturn(Optional.of(xref("1111222233334444", 1L)));
        when(accounts.findById(1L)).thenReturn(Optional.of(account));
        when(accounts.existsById(1L)).thenReturn(true);
        when(balances.findById(any(TransactionCategoryBalance.Id.class)))
                .thenReturn(Optional.empty());

        BatchJobService.PostResult result =
                service.postDaily(record("1111222233334444", new BigDecimal("100.00"),
                        LocalDateTime.of(2022, 6, 10, 0, 0)));

        assertNull(result.reject());
        assertNotNull(result.posted());
    }

    @Test
    void blankAccountExpiryRejects103LikeTheCobolStringCompare() {
        // CBTRN02C.cbl:414-419 — X(10) string compare: a blank ACCT-EXPIRAION-DATE
        // sorts before any transaction date, so a missing expiry rejects.
        Account account = account(1L, new BigDecimal("1000.00"));
        account.setAcctExpirationDate(null);
        when(xrefs.findById("1111222233334444")).thenReturn(Optional.of(xref("1111222233334444", 1L)));
        when(accounts.findById(1L)).thenReturn(Optional.of(account));

        BatchJobService.PostResult result =
                service.postDaily(record("1111222233334444", BigDecimal.ONE,
                        LocalDateTime.of(2022, 6, 10, 0, 0)));

        assertNotNull(result.reject());
        assertTrue(result.reject().contains("0103TRANSACTION RECEIVED AFTER ACCT EXPIRATION"));
    }

    @Test
    void accountRewriteDeadEndStillPostsWithoutEmittingAReject() {
        // FR-S14-09, faithful to source: reason 109 is set inside the post path
        // *after* the validate/reject branch already chose posting
        // (CBTRN02C.cbl:211-215 chooses post; :554-559 sets 109) — it is never
        // written to DALYREJS, never counted as a reject, and the TRANFILE write
        // still runs. A vanished account row takes the same dead end in target.
        Account account = account(1L, new BigDecimal("1000.00"));
        when(xrefs.findById("1111222233334444")).thenReturn(Optional.of(xref("1111222233334444", 1L)));
        when(accounts.findById(1L)).thenReturn(Optional.of(account));
        when(accounts.existsById(1L)).thenReturn(false);
        when(balances.findById(any(TransactionCategoryBalance.Id.class)))
                .thenReturn(Optional.empty());

        DailyTransactionRecord record = record("1111222233334444", BigDecimal.ONE,
                LocalDateTime.of(2022, 6, 10, 0, 0));
        BatchJobService.PostResult result = service.postDaily(record);

        assertNull(result.reject(), "reason 109 never reaches the reject file");
        assertNotNull(result.posted());
        verify(accounts, never()).save(account);
        verify(transactions).save(any(Transaction.class));
    }
}
