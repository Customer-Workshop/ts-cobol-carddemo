package com.carddemo;

import com.carddemo.batch.BatchJobService;
import com.carddemo.model.Transaction;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import com.carddemo.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@SpringBatchTest
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:s18parity;DB_CLOSE_DELAY=-1",
        "carddemo.batch.output-dir=target/test-s18-output"
})
class S18ExportImportParityIntegrationTest {
    private static final Path OUTPUT = Path.of("target/test-s18-output");
    private static final Pattern HEADER =
            Pattern.compile("^[CAXTD]\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{6}\\d{9}0001NORTH");

    @Autowired
    private JobLauncherTestUtils jobs;

    @Autowired
    private Map<String, Job> jobBeans;

    @Autowired
    private BatchJobService service;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private CardXrefRepository xrefs;

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private CardRepository cards;

    @BeforeEach
    void cleanOutput() throws Exception {
        Files.createDirectories(OUTPUT);
        try (var paths = Files.list(OUTPUT)) {
            for (Path path : paths.toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void exportWritesTypedSectionsWithUniformHeader(CapturedOutput output) throws Exception {
        seedTransaction("T000000000000001");
        assertEquals(BatchStatus.COMPLETED, runJob("cbexportJob"));

        List<String> lines = Files.readAllLines(OUTPUT.resolve("EXPORT.DATA"));
        assertEquals(customers.count() + accounts.count() + xrefs.count()
                + transactions.count() + cards.count(), lines.size());
        StringBuilder types = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            assertEquals(500, line.length(), "line " + i);
            assertTrue(HEADER.matcher(line).find(), "header contract on line " + i);
            assertEquals("%09d".formatted(i + 1), line.substring(27, 36), "sequence on line " + i);
            types.append(line.charAt(0));
        }
        // CBEXPORT.cbl section drivers: C -> A -> X -> T -> D, PK order within each.
        assertEquals("CAXTD", types.toString().replaceAll("(.)\\1*", "$1"));
        assertTrue(output.getOut().contains("CBEXPORT: Export completed"));
        assertTrue(output.getOut().contains(
                "CBEXPORT: Customers Exported: %09d".formatted(customers.count())));
        assertTrue(output.getOut().contains(
                "CBEXPORT: Transactions Exported: %09d".formatted(transactions.count())));
        assertTrue(output.getOut().contains(
                "CBEXPORT: Cards Exported: %09d".formatted(cards.count())));
        assertTrue(output.getOut().contains(
                "CBEXPORT: Total Records Exported: %09d".formatted(lines.size())));
    }

    @Test
    void importWrites132ColumnErrorRecords(CapturedOutput output) throws Exception {
        seedTransaction("T000000000000002");
        String customerRecord = service.exportRecord(customers.findAll().get(0), 1);
        StringBuilder unknown = new StringBuilder("Q" + " ".repeat(499));
        unknown.replace(27, 36, "000000042");
        String shortRecord = "T" + " ".repeat(299);
        Path input = OUTPUT.resolve("bad-input.data");
        Files.writeString(input, String.join(System.lineSeparator(),
                customerRecord, unknown.toString(), shortRecord) + System.lineSeparator());

        assertEquals(BatchStatus.COMPLETED, runJob("cbimportJob", "inputFile", input.toString()));

        // CBIMPORT.cbl:152-160 — ERROUT FB 132: timestamp(26)|type(1)|seq(7)|message(50)|pad.
        List<String> errors = Files.readAllLines(OUTPUT.resolve("CBIMPORT.errors"));
        assertEquals(2, errors.size());
        String first = errors.get(0);
        assertEquals(132, first.length());
        assertEquals('|', first.charAt(26));
        assertEquals('Q', first.charAt(27));
        assertEquals('|', first.charAt(28));
        assertEquals("0000042", first.substring(29, 36));
        assertEquals('|', first.charAt(36));
        assertEquals("Unknown record type encountered", first.substring(37, 87).trim());
        assertTrue(first.substring(87).isBlank());
        String second = errors.get(1);
        assertEquals(132, second.length());
        assertEquals('T', second.charAt(27));
        assertEquals("0000003", second.substring(29, 36));
        assertEquals("RECORD IS SHORTER THAN 500 CHARACTERS", second.substring(37, 87).trim());
        assertTrue(second.substring(87).isBlank());

        assertTrue(output.getOut().contains("CBIMPORT: Import validation completed"));
        assertTrue(output.getOut().contains("CBIMPORT: No validation errors detected"));
        assertTrue(output.getOut().contains("CBIMPORT: Total Records Read: 000000003"));
        assertTrue(output.getOut().contains("CBIMPORT: Customers Imported: 000000001"));
        assertTrue(output.getOut().contains("CBIMPORT: Errors Written: 000000002"));
        assertTrue(output.getOut().contains("CBIMPORT: Unknown Record Types: 000000001"));
    }

    @Test
    void exportThenImportRoundTripsAllTables() throws Exception {
        seedTransaction("T000000000000003");
        Path exportFile = OUTPUT.resolve("EXPORT.DATA");
        assertEquals(BatchStatus.COMPLETED, runJob("cbexportJob"));
        List<String> exported = Files.readAllLines(exportFile);
        assertTrue(exported.size() >= 5);

        customers.deleteAll();
        accounts.deleteAll();
        xrefs.deleteAll();
        transactions.deleteAll();
        cards.deleteAll();

        assertEquals(BatchStatus.COMPLETED, runJob("cbimportJob", "inputFile", exportFile.toString()));
        List<String> errors = Files.readAllLines(OUTPUT.resolve("CBIMPORT.errors"));
        assertTrue(errors.isEmpty(), "import produced error records: " + errors);

        assertEquals(BatchStatus.COMPLETED, runJob("cbexportJob"));
        List<String> reexported = Files.readAllLines(exportFile);
        assertEquals(exported.size(), reexported.size());
        for (int i = 0; i < exported.size(); i++) {
            // Header timestamp (chars 1-26) legitimately differs between runs.
            assertEquals(withoutTimestamp(exported.get(i)), withoutTimestamp(reexported.get(i)),
                    "round-trip record " + i);
        }
    }

    @Test
    void missingInputFileFailsTheStep() throws Exception {
        assertEquals(BatchStatus.FAILED, runJob("cbimportJob", "inputFile",
                OUTPUT.resolve("does-not-exist.data").toString()));
    }

    @Test
    void errorRecordPadsMessageAndTrailsSpaces() {
        String record = service.importErrorRecord("Q" + " ".repeat(499), 9, "Unknown record type encountered");
        assertEquals(132, record.length());
        // FUNCTION CURRENT-DATE: 16 digits + 5-char offset, right-padded to 26.
        assertTrue(record.substring(0, 26).matches("\\d{16}[+-]\\d{4} {5}"),
                "bad ERR-TIMESTAMP: '" + record.substring(0, 26) + "'");
        assertEquals('|', record.charAt(26));
        assertEquals("Q|", record.substring(27, 29));
        assertEquals("0000009|", record.substring(29, 37));
        assertEquals("Unknown record type encountered" + " ".repeat(19), record.substring(37, 87));
        assertEquals(" ".repeat(45), record.substring(87, 132));
    }

    private void seedTransaction(String id) {
        if (transactions.existsById(id)) {
            return;
        }
        Transaction transaction = new Transaction();
        transaction.setTranId(id);
        transaction.setTranTypeCode("01");
        transaction.setTranCategoryCode(1);
        transaction.setTranSource("POS TERM");
        transaction.setTranDescription("S18 parity transaction");
        transaction.setTranAmount(new BigDecimal("12.34"));
        transaction.setTranMerchantId(123L);
        transaction.setTranMerchantName("Merchant");
        transaction.setTranMerchantCity("Boston");
        transaction.setTranMerchantZip("02108");
        transaction.setTranCardNumber("1111222233334444");
        LocalDateTime timestamp = LocalDateTime.of(2026, 8, 20, 0, 0);
        transaction.setTranOriginTimestamp(timestamp);
        transaction.setTranProcessTimestamp(timestamp);
        transactions.save(transaction);
    }

    private String withoutTimestamp(String line) {
        return line.substring(0, 1) + line.substring(27);
    }

    private BatchStatus runJob(String name) throws Exception {
        return runJob(name, Map.of());
    }

    private BatchStatus runJob(String name, String key, String value) throws Exception {
        return runJob(name, Map.of(key, value));
    }

    private BatchStatus runJob(String name, Map<String, String> extras) throws Exception {
        jobs.setJob(jobBeans.get(name));
        JobParametersBuilder builder = new JobParametersBuilder()
                .addLong("run", System.nanoTime());
        extras.forEach(builder::addString);
        JobParameters parameters = builder.toJobParameters();
        return jobs.launchJob(parameters).getStatus();
    }
}
