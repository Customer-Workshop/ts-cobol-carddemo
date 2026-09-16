package com.carddemo.batch;

import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.DisclosureGroup;
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
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit-level CBACT04C branches — each cites its COBOL derivation. Job-level
 * coverage of the same requirements lives in InterestCalcJobIntegrationTest.
 */
class InterestCalcParityTest {

    private AccountRepository accounts;
    private CardXrefRepository xrefs;
    private DisclosureGroupRepository disclosures;
    private TransactionRepository transactions;
    private TransactionIdGenerator ids;
    private BatchJobService service;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        xrefs = mock(CardXrefRepository.class);
        disclosures = mock(DisclosureGroupRepository.class);
        transactions = mock(TransactionRepository.class);
        ids = mock(TransactionIdGenerator.class);
        service = new BatchJobService(accounts, mock(CardRepository.class), xrefs,
                mock(CustomerRepository.class), transactions, balances(),
                disclosures, mock(TransactionTypeRepository.class),
                mock(TransactionCategoryRepository.class), ids, "target/test-interest-calc");
    }

    private TransactionCategoryBalanceRepository balances() {
        return mock(TransactionCategoryBalanceRepository.class);
    }

    private Account account(long acctId, String group) {
        Account value = new Account();
        value.setAcctId(acctId);
        value.setAcctGroupId(group);
        return value;
    }

    private TransactionCategoryBalance balance(long acctId, String type, int cat, String amount) {
        TransactionCategoryBalance value = new TransactionCategoryBalance();
        TransactionCategoryBalance.Id id = new TransactionCategoryBalance.Id();
        id.setAcctId(acctId);
        id.setTypeCode(type);
        id.setCategoryCode(cat);
        value.setId(id);
        value.setBalance(new BigDecimal(amount));
        return value;
    }

    private DisclosureGroup rate(String group, String type, int cat, String amount) {
        DisclosureGroup value = new DisclosureGroup();
        DisclosureGroup.Id id = new DisclosureGroup.Id();
        id.setAcctGroupId(group);
        id.setTranTypeCode(type);
        id.setTranCategoryCode(cat);
        value.setId(id);
        value.setInterestRate(new BigDecimal(amount));
        return value;
    }

    private DisclosureGroup.Id key(String group, String type, int cat) {
        DisclosureGroup.Id id = new DisclosureGroup.Id();
        id.setAcctGroupId(group);
        id.setTranTypeCode(type);
        id.setTranCategoryCode(cat);
        return id;
    }

    private void stubCard(long acctId, String card) {
        CardXref xref = new CardXref();
        xref.setXrefCardNumber(card);
        xref.setXrefAcctId(acctId);
        when(xrefs.findByXrefAcctId(acctId)).thenReturn(List.of(xref));
    }

    // FR-S15-06 — COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE)/1200
    // has no ROUNDED clause (CBACT04C.cbl:464-465), so 100.05 × 1.5 / 1200 =
    // 0.1250625 truncates to 0.12 where HALF_UP would give 0.13.
    @Test
    void interestTruncatesAtTwoDecimalsLikeCobolCompute() {
        Account account = account(1L, "A000000000");
        stubCard(1L, "1111222233334444");
        when(disclosures.findById(key("A000000000", "01", 1)))
                .thenReturn(Optional.of(rate("A000000000", "01", 1, "1.50")));

        BatchJobService.InterestWork work = service.calculateInterest(account,
                List.of(balance(1L, "01", 1, "100.05")),
                new InterestTransactionIds("2022071800", ids)::next);

        assertEquals(0, new BigDecimal("0.12")
                .compareTo(work.transactions().get(0).getTranAmount()));
        assertEquals(0, new BigDecimal("0.12").compareTo(work.totalInterest()));
    }

    // FR-S15-06 acceptance cell: bal 1000.00 × rate 18.00 / 1200 = 15.00.
    @Test
    void interestAtTheDocumentedCellIs15For1000At18() {
        Account account = account(1L, "A000000000");
        stubCard(1L, "1111222233334444");
        when(disclosures.findById(key("A000000000", "01", 1)))
                .thenReturn(Optional.of(rate("A000000000", "01", 1, "18.00")));

        BatchJobService.InterestWork work = service.calculateInterest(account,
                List.of(balance(1L, "01", 1, "1000.00")),
                new InterestTransactionIds("2022071800", ids)::next);

        assertEquals(0, new BigDecimal("15.00")
                .compareTo(work.transactions().get(0).getTranAmount()));
    }

    // FR-S15-03/04 — 1200-GET-INTEREST-RATE (:415-444): a group-key miss
    // (status 23) retries with DIS-ACCT-GROUP-ID = 'DEFAULT'.
    @Test
    void groupRateMissFallsBackToDefaultGroup() {
        Account account = account(1L, "G1");
        stubCard(1L, "1111222233334444");
        when(disclosures.findById(key("DEFAULT", "01", 1)))
                .thenReturn(Optional.of(rate("DEFAULT", "01", 1, "18.00")));

        BatchJobService.InterestWork work = service.calculateInterest(account,
                List.of(balance(1L, "01", 1, "1000.00")),
                new InterestTransactionIds("2022071800", ids)::next);

        assertEquals(0, new BigDecimal("15.00")
                .compareTo(work.transactions().get(0).getTranAmount()));
    }

    // FR-S15-05 — 'ERROR READING DEFAULT DISCLOSURE GROUP' then abend (:455).
    @Test
    void missingGroupAndDefaultRateAbends() {
        Account account = account(1L, "G1");
        stubCard(1L, "1111222233334444");

        InterestAbendException exception = assertThrows(InterestAbendException.class,
                () -> service.calculateInterest(account,
                        List.of(balance(1L, "01", 1, "1000.00")),
                        new InterestTransactionIds("2022071800", ids)::next));

        assertTrue(exception.getMessage().contains("ERROR READING DEFAULT DISCLOSURE GROUP"));
    }

    // FR-S15-05a — 1110-GET-XREF-DATA INVALID KEY (:395-411): a card-less
    // account abends instead of silently skipping its interest rows.
    @Test
    void missingXrefAbendsBeforeAnyRateLookup() {
        Account account = account(1L, "A000000000");
        when(xrefs.findByXrefAcctId(1L)).thenReturn(List.of());
        when(disclosures.findById(key("A000000000", "01", 1)))
                .thenReturn(Optional.of(rate("A000000000", "01", 1, "18.00")));

        InterestAbendException exception = assertThrows(InterestAbendException.class,
                () -> service.calculateInterest(account,
                        List.of(balance(1L, "01", 1, "1000.00")),
                        new InterestTransactionIds("2022071800", ids)::next));

        assertTrue(exception.getMessage().contains("ACCOUNT NOT FOUND: 1"));
    }

    // FR-S15-07 — DIS-INT-RATE = 0 skips the compute+write branch (:214-216):
    // no transaction row, no accumulation.
    @Test
    void zeroRateCategoryWritesNoTransaction() {
        Account account = account(1L, "A000000000");
        stubCard(1L, "1111222233334444");
        when(disclosures.findById(key("A000000000", "01", 1)))
                .thenReturn(Optional.of(rate("A000000000", "01", 1, "0.00")));

        BatchJobService.InterestWork work = service.calculateInterest(account,
                List.of(balance(1L, "01", 1, "1000.00")),
                new InterestTransactionIds("2022071800", ids)::next);

        assertTrue(work.transactions().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(work.totalInterest()));
    }

    // FR-S15-08 — STRING PARM-DATE, WS-TRANID-SUFFIX DELIMITED BY SIZE
    // (:473-480): ids are the 10-char parm plus a 9(06) counter restarting at
    // 1 each run.
    @Test
    void parmDateIdsIncrementTheSuffixAcrossTheRun() {
        InterestTransactionIds tranIds = new InterestTransactionIds("2022071800", ids);

        assertEquals("2022071800000001", tranIds.next());
        assertEquals("2022071800000002", tranIds.next());
        assertEquals("2022071800000003", tranIds.next());
    }

    @Test
    void absentParmFallsBackToTheIdGenerator() {
        when(ids.nextId()).thenReturn("0000000000000042");
        when(ids.nextIdAfter("0000000000000042")).thenReturn("0000000000000043");
        InterestTransactionIds tranIds = new InterestTransactionIds(null, ids);

        assertEquals("0000000000000042", tranIds.next());
        assertEquals("0000000000000043", tranIds.next());
    }

    // FR-S15-11 — a repository failure on the sweep propagates to step FAILED;
    // nothing is silently swallowed (S15-B7: exception -> step FAILED).
    @Test
    void repositoryFailurePropagatesAsStepFailure() {
        when(xrefs.findByXrefAcctId(1L))
                .thenThrow(new DataAccessResourceFailureException("XREFFILE read error"));

        assertThrows(DataAccessResourceFailureException.class,
                () -> service.calculateInterest(account(1L, "A000000000"),
                        List.of(balance(1L, "01", 1, "1000.00")),
                        new InterestTransactionIds("2022071800", ids)::next));
    }

    // FR-S15-11 — 1100-GET-ACCT-DATA INVALID KEY (:373-390): a tcatbal group
    // whose account row vanished abends at the account break.
    @Test
    void missingAccountAbendsAtTheGroupBoundary() throws Exception {
        ItemStreamReader<TransactionCategoryBalance> delegate =
                new ItemStreamReader<>() {
                    private final Queue<TransactionCategoryBalance> rows =
                            new ArrayDeque<>(List.of(balance(7L, "01", 1, "50.00")));

                    @Override
                    public TransactionCategoryBalance read() {
                        return rows.poll();
                    }

                    @Override
                    public void open(ExecutionContext context) {
                    }

                    @Override
                    public void update(ExecutionContext context) {
                    }

                    @Override
                    public void close() {
                    }
                };
        AccountInterestReader reader =
                new AccountInterestReader(delegate, accounts, service, "2022071800", ids);
        when(accounts.findById(7L)).thenReturn(Optional.empty());

        reader.open(new ExecutionContext());
        InterestAbendException exception =
                assertThrows(InterestAbendException.class, reader::read);
        assertTrue(exception.getMessage().contains("ACCOUNT NOT FOUND: 7"));
    }
}
