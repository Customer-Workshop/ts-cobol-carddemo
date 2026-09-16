package com.carddemo;

import com.carddemo.batch.BatchJobService;
import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.Transaction;
import com.carddemo.model.TransactionCategoryBalance;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.TransactionCategoryBalanceRepository;
import com.carddemo.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobParametersBuilder;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CBTRN02C/POSTTRAN + COBSWAIT/WAITSTEP job-level parity — FR-S14-02..18.
 * Expected behavior is derived from app/cbl/CBTRN02C.cbl and the JCL, not from
 * the Java implementation.
 */
@SpringBootTest
@SpringBatchTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:s14posting;DB_CLOSE_DELAY=-1",
        "carddemo.seed.enabled=true",
        "carddemo.seed.data-dir=classpath:seed",
        "carddemo.seed.acctdata-group-id-in-zip-slot=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "carddemo.batch.output-dir=target/test-batch-s14"})
class DailyPostingJobIntegrationTest {
    private static final String GOOD_CARD = "1111222233334444";
    private static final String BAD_CARD = "9999888877776666";
    private static final String ORPHAN_CARD = "8888999900001111";
    private static final long ORPHAN_ACCT = 99999999999L;
    private static final Path OUTPUT = Path.of("target/test-batch-s14");

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
    MockMvc mockMvc;
    @TempDir
    Path inputDir;

    // --- test helpers ---------------------------------------------------------

    /** Writes a 350-char DALYTRAN record (CVTRA06Y offsets). */
    private String record(String id, String type, String cat, String amount,
                          String card, String originTimestamp) {
        StringBuilder value = new StringBuilder(" ".repeat(350));
        put(value, 0, id);
        put(value, 16, type);
        put(value, 18, cat);
        put(value, 22, "POS TERM");
        put(value, 32, "Test purchase");
        put(value, 132, amount);
        put(value, 143, "000000001");
        put(value, 152, "Merchant");
        put(value, 202, "Boston");
        put(value, 252, "02108");
        put(value, 262, card);
        put(value, 278, originTimestamp);
        put(value, 304, originTimestamp);
        return value.toString();
    }

    private void put(StringBuilder record, int offset, String text) {
        for (int i = 0; i < text.length(); i++) {
            record.setCharAt(offset + i, text.charAt(i));
        }
    }

    private Path feed(String... lines) throws Exception {
        Path file = inputDir.resolve("dalytran-" + System.nanoTime() + ".txt");
        Files.writeString(file, String.join("\n", List.of(lines)) + "\n", StandardCharsets.ISO_8859_1);
        return file;
    }

    // Never return JobExecution from a test-class method: @SpringBatchTest's
    // job-scope listener treats it as an execution factory and invokes it.
    private ExitStatus runJob(String jobName, JobParametersBuilder params) throws Exception {
        jobs.setJob(jobBeans.get(jobName));
        return jobs.launchJob(params.addLong("run.nano", System.nanoTime()).toJobParameters())
                .getExitStatus();
    }

    private JobParametersBuilder params(String key, String value) {
        return new JobParametersBuilder().addString(key, value);
    }

    private List<String> rejects() throws Exception {
        Path file = OUTPUT.resolve("cbtrn02-rejects.txt");
        assertTrue(Files.exists(file), "DALYREJS flat file must exist after the run");
        return Files.readAllLines(file, StandardCharsets.ISO_8859_1);
    }

    private void assertRejectLine(String line, String reason, String description) {
        assertEquals(430, line.length(), "reject trailer must be 430 bytes (350 + 4 + 76)");
        assertEquals(reason, line.substring(350, 354));
        assertEquals(description, line.substring(354, 430).trim());
    }

    // --- FR-S14-05/06/08: posting path ----------------------------------------

    @Test
    void postsValidDailyTransactionUpdatingBalancesAndWritingTranRecord() throws Exception {
        // CBTRN02C.cbl:212-219 (post branch) → 2700-UPDATE-TCATBAL :513-544,
        // 2800-UPDATE-ACCOUNT-REC :545-559, 2900-WRITE-TRANSACTION-FILE :565-573.
        Account before = accounts.findById(1L).orElseThrow();
        TransactionCategoryBalance.Id key = new TransactionCategoryBalance.Id();
        key.setAcctId(1L);
        key.setTypeCode("01");
        key.setCategoryCode(1);
        BigDecimal tcatBefore = balances.findById(key).orElseThrow().getBalance();

        // S9(09)V99 overpunch: digits "00000010000" = 100.00, trailing '{' = +0.
        Path feed = feed(record("0000000000000101", "01", "0001", "0000001000{",
                GOOD_CARD, "2022-06-10 19:27:53.000000"));

        ExitStatus execution = runJob("cbtrn02Job", params("dailyFile", feed.toString()));

        assertEquals("COMPLETED", execution.getExitCode());
        assertEquals("", execution.getExitDescription());
        assertTrue(rejects().isEmpty());

        Transaction tran = transactions.findById("0000000000000101").orElseThrow();
        assertEquals("01", tran.getTranTypeCode());
        assertEquals(1, tran.getTranCategoryCode());
        assertEquals(0, new BigDecimal("100.00").compareTo(tran.getTranAmount()));
        assertEquals(GOOD_CARD, tran.getTranCardNumber());
        assertNotNull(tran.getTranProcessTimestamp()); // DB2 CURRENT TIMESTAMP :692-705

        assertEquals(0, tcatBefore.add(new BigDecimal("100.00"))
                .compareTo(balances.findById(key).orElseThrow().getBalance()));

        Account account = accounts.findById(1L).orElseThrow();
        assertEquals(0, before.getAcctCurrBal().add(new BigDecimal("100.00"))
                .compareTo(account.getAcctCurrBal()));
        assertEquals(0, before.getAcctCurrCycCredit().add(new BigDecimal("100.00"))
                .compareTo(account.getAcctCurrCycCredit()));
        assertEquals(0, before.getAcctCurrCycDebit()
                .compareTo(account.getAcctCurrCycDebit()));
    }

    @Test
    void negativeAmountFlowsToCycleDebitOnly() throws Exception {
        // CBTRN02C.cbl:558-569 — credit-card-payment branch: negative amounts add
        // to ACCT-CURR-CYC-DEBIT, not credit. -25.00 overpunch '}' trailer.
        Account before = accounts.findById(1L).orElseThrow();
        Path feed = feed(record("0000000000000102", "01", "0001", "0000000250}",
                GOOD_CARD, "2022-06-10 19:27:53.000000"));

        ExitStatus execution = runJob("cbtrn02Job", params("dailyFile", feed.toString()));

        assertEquals("COMPLETED", execution.getExitCode());
        Account account = accounts.findById(1L).orElseThrow();
        assertEquals(0, before.getAcctCurrBal().subtract(new BigDecimal("25.00"))
                .compareTo(account.getAcctCurrBal()));
        assertEquals(0, before.getAcctCurrCycDebit().subtract(new BigDecimal("25.00"))
                .compareTo(account.getAcctCurrCycDebit()));
        assertEquals(0, before.getAcctCurrCycCredit()
                .compareTo(account.getAcctCurrCycCredit()));
    }

    @Test
    void createsMissingCategoryBalanceRowBeforePosting() throws Exception {
        // CBTRN02C.cbl:513-524 — TCATBAL read NOT FOUND 23 writes a fresh zero row.
        Path feed = feed(record("0000000000000103", "02", "0007", "0000000005{",
                GOOD_CARD, "2022-06-10 19:27:53.000000"));

        runJob("cbtrn02Job", params("dailyFile", feed.toString()));

        TransactionCategoryBalance.Id key = new TransactionCategoryBalance.Id();
        key.setAcctId(1L);
        key.setTypeCode("02");
        key.setCategoryCode(7);
        assertEquals(0, new BigDecimal("0.50").compareTo(
                balances.findById(key).orElseThrow().getBalance()));
    }

    // --- FR-S14-07/09/10: reject path + RC=4 contract -------------------------

    @Test
    void rejectsCardNotInXrefWithReason100AndSurfacesRejectsCount() throws Exception {
        // CBTRN02C.cbl:380-385 (100 INVALID CARD NUMBER FOUND) + :229-231 (RC=4).
        Path feed = feed(record("0000000000000201", "01", "0001", "0000000001{",
                BAD_CARD, "2022-06-10 19:27:53.000000"));

        ExitStatus execution = runJob("cbtrn02Job", params("dailyFile", feed.toString()));

        assertEquals("COMPLETED", execution.getExitCode()); // RC 4 stays non-blocking
        assertEquals("REJECTS:1", execution.getExitDescription());
        List<String> rejects = rejects();
        assertEquals(1, rejects.size());
        assertRejectLine(rejects.get(0), "0100", "INVALID CARD NUMBER FOUND");
        assertTrue(rejects.get(0).substring(0, 350).startsWith("0000000000000201"));
        assertTrue(transactions.findById("0000000000000201").isEmpty());
    }

    @Test
    void rejectsWhenXrefPointsAtMissingAccountWithReason101() throws Exception {
        // CBTRN02C.cbl:387-392 — ACCTFILE read NOT FOUND after a successful XREF.
        CardXref orphan = new CardXref();
        orphan.setXrefCardNumber(ORPHAN_CARD);
        orphan.setXrefCustId(1L);
        orphan.setXrefAcctId(ORPHAN_ACCT);
        xrefs.save(orphan);

        Path feed = feed(record("0000000000000202", "01", "0001", "0000000001{",
                ORPHAN_CARD, "2022-06-10 19:27:53.000000"));

        ExitStatus execution = runJob("cbtrn02Job", params("dailyFile", feed.toString()));

        assertEquals("REJECTS:1", execution.getExitDescription());
        List<String> rejects = rejects();
        assertEquals(1, rejects.size());
        assertRejectLine(rejects.get(0), "0101", "ACCOUNT RECORD NOT FOUND");
    }

    @Test
    void rejectsOverlimitAndExpiredTransactionWithReason103LastWriteWins() throws Exception {
        // CBTRN02C.cbl:407-419 — 102 fires when LIMIT < projected cycle balance,
        // then 103 overwrites it when the account is expired (no ELSE chain).
        Account before = accounts.findById(1L).orElseThrow();
        // +2100.00 projection (0-0+2100 > 2020 limit) AND expired -> 103 wins.
        Path feed = feed(record("0000000000000203", "01", "0001", "0000021000{",
                GOOD_CARD, "2026-01-01 00:00:00.000000"));

        ExitStatus execution = runJob("cbtrn02Job", params("dailyFile", feed.toString()));

        assertEquals("REJECTS:1", execution.getExitDescription());
        List<String> rejects = rejects();
        assertEquals(1, rejects.size());
        assertRejectLine(rejects.get(0), "0103", "TRANSACTION RECEIVED AFTER ACCT EXPIRATION");
        assertTrue(transactions.findById("0000000000000203").isEmpty());
        assertEquals(0, before.getAcctCurrCycCredit()
                .compareTo(accounts.findById(1L).orElseThrow().getAcctCurrCycCredit()));
    }

    @Test
    void countsEveryRejectInExitDescription() throws Exception {
        Path feed = feed(
                record("0000000000000210", "01", "0001", "0000000001{",
                        BAD_CARD, "2022-06-10 19:27:53.000000"),
                record("0000000000000211", "01", "0001", "0000000001{",
                        BAD_CARD, "2022-06-10 19:27:53.000000"));

        ExitStatus execution = runJob("cbtrn02Job", params("dailyFile", feed.toString()));

        assertEquals("REJECTS:2", execution.getExitDescription());
        assertEquals(2, rejects().size());
    }

    // --- FR-S14-17: rerun semantics -------------------------------------------

    @Test
    void rerunDoublePostsBalancesAndOverwritesTheTranRow() throws Exception {
        // FR-S14-17: posting is non-idempotent by design. Legacy abends on the
        // duplicate TRANFILE write (VSAM status 22 → CEE3ABD :692-711); target
        // upserts the row and re-applies the balances — documented deviation.
        // Keep the rerun deterministic: the COBOL projection CYC-CREDIT-CYC-DEBIT+AMT
        // must stay under the limit for both runs.
        Account reset = accounts.findById(1L).orElseThrow();
        reset.setAcctCurrCycCredit(BigDecimal.ZERO);
        reset.setAcctCurrCycDebit(BigDecimal.ZERO);
        accounts.save(reset);

        Path feed = feed(record("0000000000000301", "01", "0001", "0000001000{",
                GOOD_CARD, "2022-06-10 19:27:53.000000"));

        runJob("cbtrn02Job", params("dailyFile", feed.toString()));
        BigDecimal balanceAfterFirst = accounts.findById(1L).orElseThrow().getAcctCurrBal();

        ExitStatus second = runJob("cbtrn02Job", params("dailyFile", feed.toString()));

        assertEquals("COMPLETED", second.getExitCode());
        Account account = accounts.findById(1L).orElseThrow();
        assertEquals(0, balanceAfterFirst.add(new BigDecimal("100.00"))
                .compareTo(account.getAcctCurrBal()));
        assertEquals("0000000000000301",
                transactions.findById("0000000000000301").orElseThrow().getTranId());
    }

    // --- FR-S14-18: absent / empty feed ----------------------------------------

    @Test
    void missingDailyTranFileIsAnEmptyFeedNotAnError() throws Exception {
        // S14-B7: absent drop completes zero-work rather than failing the chain.
        ExitStatus execution = runJob("cbtrn02Job",
                params("dailyFile", inputDir.resolve("does-not-exist.txt").toString()));

        assertEquals("COMPLETED", execution.getExitCode());
        assertEquals("", execution.getExitDescription());
        assertTrue(rejects().isEmpty());
    }

    @Test
    void emptyFeedCompletesWithZeroWork() throws Exception {
        Path feed = inputDir.resolve("empty.txt");
        Files.writeString(feed, "");

        ExitStatus execution = runJob("cbtrn02Job", params("dailyFile", feed.toString()));

        assertEquals("COMPLETED", execution.getExitCode());
        assertTrue(rejects().isEmpty());
    }

    // --- FR-S14-14/15: wait step + documented job order ------------------------

    @Test
    void waitStepJobHonorsTheCentisecondParm() throws Exception {
        // FR-S14-14 with a testable duration: waitCentiseconds=50 → ≥500ms.
        long start = System.nanoTime();
        ExitStatus execution = runJob("waitStepJob", params("waitCentiseconds", "50"));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertEquals("COMPLETED", execution.getExitCode());
        assertTrue(elapsedMillis >= 500, "waitStep ran only " + elapsedMillis + "ms");
    }

    @Test
    void launchesTheDocumentedChainInOrderThroughTheAdminEndpoint() throws Exception {
        // FR-S14-15: the CA-7 SCHID 030 chain maps to cbtrn02Job → waitStepJob.
        // cbtrn01Job is an orphan and must not appear in the chain.
        assertEquals(List.of("cbtrn02Job", "waitStepJob"), BatchJobService.DAILY_POSTING_CHAIN);
        assertFalse(BatchJobService.DAILY_POSTING_CHAIN.contains("cbtrn01Job"));

        MockHttpSession admin = signon("ADMIN001", "PASSWORD", "/api/admin/menu");
        Path feed = feed(record("0000000000000401", "01", "0001", "0000000001{",
                BAD_CARD, "2022-06-10 19:27:53.000000"));

        mockMvc.perform(post("/api/admin/jobs/cbtrn02Job")
                        .param("dailyFile", feed.toString()).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.exitDescription").value("REJECTS:1"));

        mockMvc.perform(post("/api/admin/jobs/waitStepJob")
                        .param("waitCentiseconds", "10").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.exitCode").value("COMPLETED"));
    }

    // --- FR-S14-16: CBTRN01C orphan --------------------------------------------

    @Test
    void orphanCbtrn01JobDiagnosesWithoutPosting() throws Exception {
        // CBTRN01C is validate-only: diagnostics for bad cards, no balance
        // movement, no TRANFILE write (CBTRN01C.cbl:140-239).
        Path feed = feed(record("0000000000000501", "01", "0001", "0000010000{",
                BAD_CARD, "2022-06-10 19:27:53.000000"));

        ExitStatus execution = runJob("cbtrn01Job", params("dailyFile", feed.toString()));

        assertEquals("COMPLETED", execution.getExitCode());
        Path diagnostics = OUTPUT.resolve("cbtrn01-validation.txt");
        assertTrue(Files.exists(diagnostics));
        List<String> lines = Files.readAllLines(diagnostics, StandardCharsets.ISO_8859_1);
        assertTrue(lines.stream().anyMatch(line ->
                line.contains("CARD NUMBER 9999888877776666 COULD NOT BE VERIFIED.")
                        && line.contains("SKIPPING TRANSACTION ID-0000000000000501")));
        assertTrue(transactions.findById("0000000000000501").isEmpty());
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
