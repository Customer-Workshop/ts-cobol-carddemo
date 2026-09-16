package com.carddemo;

import com.carddemo.model.CardXref;
import com.carddemo.model.Transaction;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.TransactionRepository;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TRANREPT/CBTRN03C parity (S-10): the REPORTING step must emit the
 * CVTRA07Y 133-column report the COBOL program writes — headers with the
 * first in-range row, a page break whenever the written-record counter is
 * a multiple of 20, account totals only on a card-number break, the EOF
 * double-add of the last record's amount into the page (and grand)
 * total, and a 0-byte report when nothing falls in range (CBTRN03C.cbl:
 * 170-213). The fixture below is hand-derived from the copybook.
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:cbtrn03parity;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "carddemo.batch.output-dir=target/test-s10-batch"
})
class Cbtrn03ReportParityTest {

    private static final Path OUTPUT = Path.of("target/test-s10-batch");
    private static final String CARD1 = "1111222233334444";
    private static final String CARD2 = "9999888877776666";
    private static final String RULE = "-".repeat(133);

    @Autowired private JobLauncherTestUtils jobs;
    @Autowired private Job cbtrn03Job;
    @Autowired private TransactionRepository transactions;
    @Autowired private CardXrefRepository xrefs;

    @BeforeEach
    void cleanOutputAndTransactions() throws Exception {
        if (Files.exists(OUTPUT)) {
            try (var paths = Files.list(OUTPUT)) {
                for (Path path : paths.toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
        Files.createDirectories(OUTPUT);
        transactions.deleteAll();
        if (!xrefs.existsById(CARD2)) {
            CardXref card2 = new CardXref();
            card2.setXrefCardNumber(CARD2);
            card2.setXrefCustId(1L);
            card2.setXrefAcctId(2L);
            xrefs.save(card2);
        }
    }

    @Test
    void reportMatchesTheCvtra07yLayout_frS1016_frS1017_frS1018() throws Exception {
        // 18 rows on card 1, 12 on card 2 — txn i carries amount i.00.
        // One out-of-range row on card 1 proves the REPRO filter.
        List<Transaction> rows = new ArrayList<>();
        for (int i = 1; i <= 18; i++) {
            rows.add(transaction(i, CARD1, "2022-06-15T12:00:00"));
        }
        for (int i = 19; i <= 30; i++) {
            rows.add(transaction(i, CARD2, "2022-06-15T12:00:00"));
        }
        rows.add(transaction(31, CARD1, "2030-01-01T00:00:00"));
        transactions.saveAll(rows);

        JobExecution execution = jobs.getJobLauncher().run(cbtrn03Job, parameters("2022-01-01", "2022-12-31"));
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        Path report = OUTPUT.resolve("cbtrn03-report.txt");
        assertTrue(Files.exists(report));
        List<String> actual = Files.readAllLines(report);
        List<String> expected = expectedReport();
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), actual.get(i), "line " + (i + 1));
        }

        String text = Files.readString(report);
        // The out-of-range row never reaches the report (S10-B8).
        assertFalse(text.contains("0000000000000031"));
        // Card 2's account total is never written: totals print on a card
        // break only — the last card's total is swallowed at EOF.
        assertEquals(1, text.split("Account Total", -1).length - 1);
    }

    @Test
    void rangeWithNoRowsWritesAnEmptyReport_frS1017() throws Exception {
        transactions.save(transaction(1, CARD1, "2022-06-15T12:00:00"));

        JobExecution execution = jobs.getJobLauncher().run(cbtrn03Job, parameters("2030-01-01", "2030-12-31"));
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        // An out-of-range first record walks NEXT SENTENCE off the end of
        // the sweep: no totals, no headers — a 0-byte output dataset.
        assertEquals(0, Files.size(OUTPUT.resolve("cbtrn03-report.txt")));
    }

    @Test
    void missingXrefLookupAbendsTheStep_frS1019() throws Exception {
        transactions.save(transaction(1, "5555444433332222", "2022-06-15T12:00:00"));

        JobExecution execution = jobs.getJobLauncher().run(cbtrn03Job, parameters("2022-01-01", "2022-12-31"));
        assertEquals(BatchStatus.FAILED, execution.getStatus());
    }

    private JobParameters parameters(String start, String end) {
        return new JobParametersBuilder()
                .addString("startDate", start)
                .addString("endDate", end)
                .addLong("run", System.nanoTime())
                .toJobParameters();
    }

    private static Transaction transaction(int id, String card, String processed) {
        Transaction transaction = new Transaction();
        transaction.setTranId(String.format("%016d", id));
        transaction.setTranTypeCode("01");
        transaction.setTranCategoryCode(1);
        transaction.setTranSource("POS TERM");
        transaction.setTranDescription("Test transaction " + id);
        transaction.setTranAmount(new BigDecimal(id));
        transaction.setTranCardNumber(card);
        transaction.setTranProcessTimestamp(LocalDateTime.parse(processed));
        return transaction;
    }

    // Hand-derived from CVTRA07Y.cpy: every record padded to 133.
    private static List<String> expectedReport() {
        List<String> lines = new ArrayList<>();
        header(lines, "2022-01-01", "2022-12-31");
        for (int i = 1; i <= 16; i++) {
            lines.add(detail(i, "00000000001"));
        }
        lines.add(total("Page Total", 11, "+        136.00"));
        lines.add(RULE);
        header(lines, "2022-01-01", "2022-12-31");
        lines.add(detail(17, "00000000001"));
        lines.add(detail(18, "00000000001"));
        lines.add(total("Account Total", 13, "+        171.00"));
        lines.add(RULE);
        for (int i = 19; i <= 28; i++) {
            lines.add(detail(i, "00000000002"));
        }
        lines.add(total("Page Total", 11, "+        270.00"));
        lines.add(RULE);
        header(lines, "2022-01-01", "2022-12-31");
        lines.add(detail(29, "00000000002"));
        lines.add(detail(30, "00000000002"));
        // EOF: TRAN-AMT of the stale last record is added once more, so the
        // final page total is 59.00 + 30.00 = 89.00 — and no account total
        // follows the card-2 rows.
        lines.add(total("Page Total", 11, "+         89.00"));
        lines.add(RULE);
        lines.add(total("Grand Total", 11, "+        495.00"));
        return lines;
    }

    private static void header(List<String> lines, String start, String end) {
        lines.add(pad(pad("DALYREPT", 38) + pad("Daily Transaction Report", 41)
                + "Date Range: " + pad(start, 10) + " to " + pad(end, 10), 133));
        lines.add(pad("", 133));
        lines.add(pad(pad("Transaction ID", 17) + pad("Account ID", 12)
                + pad("Transaction Type", 19) + pad("Tran Category", 35)
                + pad("Tran Source", 14) + " " + pad("        Amount", 16), 133));
        lines.add(RULE);
    }

    private static String detail(int id, String account) {
        return pad(pad(String.format("%016d", id), 16) + " "
                + pad(account, 11) + " "
                + "01-" + pad("Purchase", 15) + " "
                + "0001-" + pad("Regular", 29) + " "
                + pad("POS TERM", 10) + "    "
                + amount(id) + "  ", 133);
    }

    private static String total(String label, int labelWidth, String amount) {
        return pad(pad(label, labelWidth) + ".".repeat(97 - labelWidth)
                + amount, 133);
    }

    // -ZZZ,ZZZ,ZZZ.ZZ for the small whole-dollar fixture amounts:
    // fixed sign column, leading-zero suppression, decimals shown.
    private static String amount(int id) {
        String significant = String.format("%09d", id).replaceFirst("^0+", "");
        return " " + " ".repeat(11 - significant.length()) + significant + ".00";
    }

    private static String pad(String value, int width) {
        if (value.length() > width) {
            return value.substring(0, width);
        }
        return value + " ".repeat(width - value.length());
    }
}
