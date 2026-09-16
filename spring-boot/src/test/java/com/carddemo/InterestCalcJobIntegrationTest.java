package com.carddemo;

import com.carddemo.batch.BatchJobService;
import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.DisclosureGroup;
import com.carddemo.model.Transaction;
import com.carddemo.model.TransactionCategoryBalance;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.DisclosureGroupRepository;
import com.carddemo.repository.TransactionCategoryBalanceRepository;
import com.carddemo.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CBACT04C/INTCALC job-level parity — FR-S15-01..14.
 * Expected behavior is derived from app/cbl/CBACT04C.cbl, INTCALC.jcl, and the
 * Control-M MONTHLY chain, not from the Java implementation.
 */
@SpringBootTest
@SpringBatchTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:s15interest;DB_CLOSE_DELAY=-1",
        "carddemo.seed.enabled=true",
        "carddemo.seed.data-dir=classpath:seed",
        "carddemo.seed.acctdata-group-id-in-zip-slot=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "carddemo.batch.output-dir=target/test-batch-s15"})
class InterestCalcJobIntegrationTest {
    private static final String PARM = "2022071800";
    private static final long ACCT_A = 91000000001L;
    private static final long ACCT_B = 91000000002L;
    private static final String CARD_A = "4444555566661111";
    private static final String CARD_B = "4444555566662222";

    @Autowired
    JobLauncherTestUtils jobs;
    @Autowired
    Map<String, Job> jobBeans;
    @Autowired
    AccountRepository accounts;
    @Autowired
    CardXrefRepository xrefs;
    @Autowired
    TransactionRepository transactions;
    @Autowired
    TransactionCategoryBalanceRepository balances;
    @Autowired
    DisclosureGroupRepository disclosures;
    @Autowired
    MockMvc mockMvc;

    @BeforeEach
    void cleanBatchTables() {
        // The seeded tcatbal/discgrp rows (group A000000000 / acct 1) are
        // swept by every run; clear them so each test owns its fixtures.
        // The seeded account/xref for acct 1 stay — an unvisited account
        // proves the sweep only touches tcatbal keys.
        balances.deleteAll();
        disclosures.deleteAll();
        transactions.deleteAll();
        xrefs.deleteAllById(List.of(CARD_A, CARD_B));
        accounts.deleteAllById(List.of(ACCT_A, ACCT_B));
    }

    // --- helpers --------------------------------------------------------------

    private void saveAccount(long acctId, String group, String bal,
                             String cycCredit, String cycDebit) {
        Account value = new Account();
        value.setAcctId(acctId);
        value.setAcctActiveStatus("Y");
        value.setAcctGroupId(group);
        value.setAcctCurrBal(new BigDecimal(bal));
        value.setAcctCurrCycCredit(new BigDecimal(cycCredit));
        value.setAcctCurrCycDebit(new BigDecimal(cycDebit));
        accounts.save(value);
    }

    private void saveXref(String card, long acctId) {
        CardXref value = new CardXref();
        value.setXrefCardNumber(card);
        value.setXrefCustId(1L);
        value.setXrefAcctId(acctId);
        xrefs.save(value);
    }

    private void saveBalance(long acctId, String type, int cat, String amount) {
        TransactionCategoryBalance value = new TransactionCategoryBalance();
        TransactionCategoryBalance.Id id = new TransactionCategoryBalance.Id();
        id.setAcctId(acctId);
        id.setTypeCode(type);
        id.setCategoryCode(cat);
        value.setId(id);
        value.setBalance(new BigDecimal(amount));
        balances.save(value);
    }

    private void saveRate(String group, String type, int cat, String amount) {
        DisclosureGroup value = new DisclosureGroup();
        DisclosureGroup.Id id = new DisclosureGroup.Id();
        id.setAcctGroupId(group);
        id.setTranTypeCode(type);
        id.setTranCategoryCode(cat);
        value.setId(id);
        value.setInterestRate(new BigDecimal(amount));
        disclosures.save(value);
    }

    private ExitStatus launch(String jobName, JobParametersBuilder params) throws Exception {
        jobs.setJob(jobBeans.get(jobName));
        return jobs.launchJob(params.addLong("run.nano", System.nanoTime()).toJobParameters())
                .getExitStatus();
    }

    private ExitStatus runInterest() throws Exception {
        return launch("cbact04Job", new JobParametersBuilder().addString("parmDate", PARM));
    }

    private List<Transaction> interestRows(String card) {
        return transactions.findByTranCardNumberOrderByTranIdAsc(card);
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }

    // --- FR-S15-01/02: sequential sweep + account-break updates ---------------

    @Test
    void sweepsTcatbalInKeyOrderUpdatingEachAccountOnce_frS1501_frS1502() throws Exception {
        saveAccount(ACCT_A, "G1", "1000.00", "5.00", "5.00");
        saveXref(CARD_A, ACCT_A);
        saveAccount(ACCT_B, "G1", "2000.00", "7.00", "7.00");
        saveXref(CARD_B, ACCT_B);
        saveRate("G1", "01", 1, "18.00");
        saveRate("G1", "01", 2, "12.00");
        // Inserted out of key order; the reader still sweeps acct/type/cat.
        saveBalance(ACCT_B, "01", 1, "500.00");
        saveBalance(ACCT_A, "01", 2, "400.00");
        saveBalance(ACCT_A, "01", 1, "600.00");

        ExitStatus exit = runInterest();

        assertEquals("COMPLETED", exit.getExitCode());
        // A: 600*18/1200 = 9.00 and 400*12/1200 = 4.00 -> +13.00, one update.
        assertMoney("1013.00", accounts.findById(ACCT_A).orElseThrow().getAcctCurrBal());
        // B: 500*18/1200 = 7.50 -> +7.50.
        assertMoney("2007.50", accounts.findById(ACCT_B).orElseThrow().getAcctCurrBal());
        // One interest row per accruing category, in sweep order.
        List<Transaction> rowsA = interestRows(CARD_A);
        List<Transaction> rowsB = interestRows(CARD_B);
        assertEquals(2, rowsA.size());
        assertEquals(1, rowsB.size());
        assertMoney("9.00", rowsA.get(0).getTranAmount());
        assertMoney("4.00", rowsA.get(1).getTranAmount());
        assertMoney("7.50", rowsB.get(0).getTranAmount());
    }

    // --- FR-S15-03/04: keyed rate lookup + DEFAULT fallback -------------------

    @Test
    void groupRateMissFallsBackToDefaultGroup_frS1503_frS1504() throws Exception {
        saveAccount(ACCT_A, "G-UNLISTED", "1000.00", "0.00", "0.00");
        saveXref(CARD_A, ACCT_A);
        saveBalance(ACCT_A, "01", 1, "1000.00");
        saveRate("DEFAULT", "01", 1, "18.00");

        ExitStatus exit = runInterest();

        assertEquals("COMPLETED", exit.getExitCode());
        List<Transaction> rows = interestRows(CARD_A);
        assertEquals(1, rows.size());
        assertMoney("15.00", rows.get(0).getTranAmount());
    }

    // --- FR-S15-05/05a: missing rate/xref data abends ---------------------------

    @Test
    void missingGroupAndDefaultRateAbends_frS1505() throws Exception {
        saveAccount(ACCT_A, "G-UNLISTED", "1000.00", "5.00", "5.00");
        saveXref(CARD_A, ACCT_A);
        saveBalance(ACCT_A, "01", 1, "1000.00");

        ExitStatus exit = runInterest();

        // CEE3ABD parity: FAILED step, job exit code 999.
        assertEquals("999", exit.getExitCode());
        assertTrue(interestRows(CARD_A).isEmpty());
        assertMoney("1000.00", accounts.findById(ACCT_A).orElseThrow().getAcctCurrBal());
    }

    @Test
    void missingXrefAbends_frS1505a() throws Exception {
        saveAccount(ACCT_A, "G1", "1000.00", "5.00", "5.00");
        saveBalance(ACCT_A, "01", 1, "1000.00");
        saveRate("G1", "01", 1, "18.00");

        ExitStatus exit = runInterest();
        // CEE3ABD parity: FAILED step, job exit code 999.
        assertEquals("999", exit.getExitCode());
        assertMoney("1000.00", accounts.findById(ACCT_A).orElseThrow().getAcctCurrBal());
    }

    // --- FR-S15-06/07: the interest formula -------------------------------------

    @Test
    void interestIsBalanceTimesRateOver1200Truncated_frS1506() throws Exception {
        saveAccount(ACCT_A, "G1", "0.00", "0.00", "0.00");
        saveXref(CARD_A, ACCT_A);
        // 100.05 x 1.50 / 1200 = 0.1250625 -> truncates to 0.12 (no ROUNDED).
        saveBalance(ACCT_A, "01", 1, "100.05");
        saveRate("G1", "01", 1, "1.50");
        // The documented cell: 1000.00 x 18.00 / 1200 = 15.00.
        saveBalance(ACCT_A, "01", 2, "1000.00");
        saveRate("G1", "01", 2, "18.00");

        ExitStatus exit = runInterest();

        assertEquals("COMPLETED", exit.getExitCode());
        List<Transaction> rows = interestRows(CARD_A);
        assertEquals(2, rows.size());
        assertMoney("0.12", rows.get(0).getTranAmount());
        assertMoney("15.00", rows.get(1).getTranAmount());
        assertMoney("15.12", accounts.findById(ACCT_A).orElseThrow().getAcctCurrBal());
    }

    @Test
    void zeroRateCategoryWritesNoTransaction_frS1507() throws Exception {
        saveAccount(ACCT_A, "G1", "1000.00", "5.00", "5.00");
        saveXref(CARD_A, ACCT_A);
        saveBalance(ACCT_A, "01", 1, "1000.00");
        saveRate("G1", "01", 1, "0.00");
        saveBalance(ACCT_A, "01", 2, "100.00");
        saveRate("G1", "01", 2, "12.00");

        ExitStatus exit = runInterest();

        assertEquals("COMPLETED", exit.getExitCode());
        List<Transaction> rows = interestRows(CARD_A);
        assertEquals(1, rows.size(), "DIS-INT-RATE = 0 writes no SYSTRAN row");
        assertMoney("1.00", rows.get(0).getTranAmount());
        // The account still takes its once-per-group update.
        assertMoney("1001.00", accounts.findById(ACCT_A).orElseThrow().getAcctCurrBal());
        assertMoney("0.00", accounts.findById(ACCT_A).orElseThrow().getAcctCurrCycCredit());
    }

    // --- FR-S15-08: the interest transaction record ----------------------------

    @Test
    void interestRowsCarryParmDateIdsAndLegacyFields_frS1508() throws Exception {
        saveAccount(ACCT_A, "G1", "1000.00", "0.00", "0.00");
        saveXref(CARD_A, ACCT_A);
        saveBalance(ACCT_A, "01", 1, "1000.00");
        saveBalance(ACCT_A, "01", 2, "100.00");
        saveRate("G1", "01", 1, "18.00");
        saveRate("G1", "01", 2, "12.00");

        ExitStatus exit = runInterest();

        assertEquals("COMPLETED", exit.getExitCode());
        List<Transaction> rows = interestRows(CARD_A);
        assertEquals(2, rows.size());
        assertEquals(List.of("2022071800000001", "2022071800000002"),
                rows.stream().map(Transaction::getTranId).toList());
        Transaction row = rows.get(0);
        assertEquals("01", row.getTranTypeCode());
        assertEquals(5, row.getTranCategoryCode());
        assertEquals("System", row.getTranSource());
        assertEquals("Int. for a/c " + ACCT_A, row.getTranDescription());
        assertEquals(CARD_A, row.getTranCardNumber());
        assertEquals(0L, row.getTranMerchantId());
        assertEquals("", row.getTranMerchantName());
        assertEquals("", row.getTranMerchantCity());
        assertEquals("", row.getTranMerchantZip());
        assertNotNull(row.getTranOriginTimestamp());
        assertEquals(row.getTranOriginTimestamp(), row.getTranProcessTimestamp());
    }

    // --- FR-S15-09/10: account update at break and EOF -------------------------

    @Test
    void balanceAndCycleResetPerAccountIncludingLastAtEof_frS1509_frS1510() throws Exception {
        saveAccount(ACCT_A, "G1", "1000.00", "11.00", "22.00");
        saveXref(CARD_A, ACCT_A);
        saveAccount(ACCT_B, "G1", "2000.00", "33.00", "44.00");
        saveXref(CARD_B, ACCT_B);
        saveRate("G1", "01", 1, "18.00");
        saveRate("G1", "01", 2, "12.00");
        saveRate("G1", "01", 3, "6.00");
        saveBalance(ACCT_A, "01", 1, "1000.00");
        saveBalance(ACCT_A, "01", 2, "1000.00");
        saveBalance(ACCT_A, "01", 3, "1000.00");
        saveBalance(ACCT_B, "01", 1, "400.00");

        ExitStatus exit = runInterest();

        assertEquals("COMPLETED", exit.getExitCode());
        // A: 15.00 + 10.00 + 5.00 = 30.00 -> 1030.00, cyc zeroed.
        Account a = accounts.findById(ACCT_A).orElseThrow();
        assertMoney("1030.00", a.getAcctCurrBal());
        assertMoney("0.00", a.getAcctCurrCycCredit());
        assertMoney("0.00", a.getAcctCurrCycDebit());
        // B is the last group: its update fires at EOF (1050-UPDATE-ACCOUNT).
        Account b = accounts.findById(ACCT_B).orElseThrow();
        assertMoney("2006.00", b.getAcctCurrBal());
        assertMoney("0.00", b.getAcctCurrCycCredit());
        assertMoney("0.00", b.getAcctCurrCycDebit());
        // Seeded acct 1 has no tcatbal rows -> never visited, cyc preserved.
        Account untouched = accounts.findById(1L).orElseThrow();
        assertMoney("194.00", untouched.getAcctCurrBal());
    }

    // --- FR-S15-11: data errors fail the step ----------------------------------

    @Test
    void missingAccountAbends_frS1511() throws Exception {
        saveBalance(99999999999L, "01", 1, "1000.00");

        ExitStatus exit = runInterest();
        // CEE3ABD parity: FAILED step, job exit code 999.
        assertEquals("999", exit.getExitCode());
    }

    // --- FR-S15-12: 1400-COMPUTE-FEES stays a dead stub -------------------------

    @Test
    void feeComputeRemainsAStubWithNoEffect_frS1512() throws Exception {
        saveAccount(ACCT_A, "G1", "1000.00", "0.00", "0.00");
        saveXref(CARD_A, ACCT_A);
        saveBalance(ACCT_A, "01", 1, "1000.00");
        saveRate("G1", "01", 1, "18.00");
        Account before = accounts.findById(ACCT_A).orElseThrow();
        BigDecimal creditLimit = before.getAcctCreditLimit();
        String group = before.getAcctGroupId();

        ExitStatus exit = runInterest();

        assertEquals("COMPLETED", exit.getExitCode());
        Account after = accounts.findById(ACCT_A).orElseThrow();
        // Only the balance moved; no fee machinery touched the account.
        assertEquals(creditLimit, after.getAcctCreditLimit());
        assertEquals(group, after.getAcctGroupId());
        assertEquals(1, interestRows(CARD_A).size());
    }

    // --- FR-S15-13: COMBTRAN eliminated — direct transactions insert -----------

    @Test
    void interestRowsInsertDirectlyAsQueryableTransactions_frS1513() throws Exception {
        saveAccount(ACCT_A, "G1", "1000.00", "0.00", "0.00");
        saveXref(CARD_A, ACCT_A);
        saveBalance(ACCT_A, "01", 1, "1000.00");
        saveRate("G1", "01", 1, "18.00");

        ExitStatus exit = runInterest();

        assertEquals("COMPLETED", exit.getExitCode());
        // No merge step: the interest row is a normal transactions row,
        // readable through the same repository S-07/S-10 screens use.
        List<Transaction> rows = interestRows(CARD_A);
        assertEquals(1, rows.size());
        assertEquals("01", rows.get(0).getTranTypeCode());
        assertEquals(5, rows.get(0).getTranCategoryCode());
    }

    // --- FR-S15-14: documented monthly chain order -----------------------------

    @Test
    void monthlyChainOrderIsDocumentedAndLaunchable_frS1514() throws Exception {
        // MONTHLY-InterestCalculation: CLOSEFIL->INTCALC->COMBTRAN->WAITSTEP->
        // OPENFIL maps to cbact04Job then waitStepJob (close/open are no-ops,
        // COMBTRAN eliminated by direct insert).
        assertEquals(List.of("cbact04Job", "waitStepJob"),
                BatchJobService.MONTHLY_INTEREST_CHAIN);

        saveAccount(ACCT_A, "G1", "1000.00", "0.00", "0.00");
        saveXref(CARD_A, ACCT_A);
        saveBalance(ACCT_A, "01", 1, "1000.00");
        saveRate("G1", "01", 1, "18.00");

        MockHttpSession admin = signon("ADMIN001", "PASSWORD", "/api/admin/menu");

        mockMvc.perform(post("/api/admin/jobs/cbact04Job")
                        .param("parmDate", PARM).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.exitCode").value("COMPLETED"));

        mockMvc.perform(post("/api/admin/jobs/waitStepJob")
                        .param("waitCentiseconds", "10").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    // --- S15-B4: parmDate contract ----------------------------------------------

    @Test
    void missingParmDateRejectsBeforeRun() throws Exception {
        // INTCALC.jcl always supplies PARM; a missing parm is a launch-time
        // rejection, not a runtime failure.
        jobs.setJob(jobBeans.get("cbact04Job"));
        JobParametersInvalidException exception = assertThrows(
                JobParametersInvalidException.class,
                () -> jobs.launchJob(new JobParametersBuilder()
                        .addLong("run.nano", System.nanoTime()).toJobParameters()));
        assertTrue(exception.getMessage().contains("parmDate"));
    }

    @Test
    void malformedParmDateRejectsBeforeRun() throws Exception {
        jobs.setJob(jobBeans.get("cbact04Job"));
        assertThrows(JobParametersInvalidException.class,
                () -> jobs.launchJob(new JobParametersBuilder()
                        .addString("parmDate", "2022-07-18")
                        .addLong("run.nano", System.nanoTime()).toJobParameters()));
        assertThrows(JobParametersInvalidException.class,
                () -> jobs.launchJob(new JobParametersBuilder()
                        .addString("parmDate", "2022134000") // month 13, hour 00
                        .addLong("run.nano", System.nanoTime()).toJobParameters()));
    }

    private MockHttpSession signon(String userId, String password, String landing) throws Exception {
        var result = mockMvc.perform(post("/api/auth/signon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.landingTarget").value(landing))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
