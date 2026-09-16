package com.carddemo;

import com.carddemo.model.TransactionCategory;
import com.carddemo.model.TransactionType;
import com.carddemo.repository.TransactionCategoryRepository;
import com.carddemo.repository.TransactionTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MNTTRDB2/COBTUPDT + TRANEXTR parity: the maint apply tasklet reads 53-byte
 * INPFILE records and the extract tasklet emits the two 60-char DSNTIAUL
 * layouts. Test names carry the FR-S21 row they cover.
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:trantypejobit;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "carddemo.batch.output-dir=target/s21-batch-output"
})
class TranTypeMaintJobIT {

    private static final Path OUTPUT = Path.of("target/s21-batch-output");

    @Autowired private JobLauncherTestUtils jobs;
    @Autowired private Map<String, Job> jobBeans;
    @Autowired private TransactionTypeRepository types;
    @Autowired private TransactionCategoryRepository categories;

    @BeforeEach
    void seedAndClean() throws Exception {
        categories.deleteAll();
        types.deleteAll();
        saveType("01", "TYPE1 DESC");
        saveType("03", "TYPE3 DESC");
        saveType("05", "TYPE5 DESC");

        TransactionCategory category = new TransactionCategory();
        TransactionCategory.Id id = new TransactionCategory.Id();
        id.setTranTypeCode("05");
        id.setTranCategoryCode(7);
        category.setId(id);
        category.setDescription("TYPE5 CAT DESC");
        categories.save(category);

        Files.createDirectories(OUTPUT);
    }

    private void saveType(String code, String desc) {
        TransactionType type = new TransactionType();
        type.setTranType(code);
        type.setDescription(desc);
        types.save(type);
    }

    private String record(String action, String code, String desc) {
        String rec = action + code + desc;
        return rec + " ".repeat(Math.max(0, 53 - rec.length()));
    }

    private JobParameters params(Path inputFile) {
        return new JobParametersBuilder()
                .addString("inputFile",
                        inputFile == null ? "" : inputFile.toString())
                .addLong("run", System.nanoTime()).toJobParameters();
    }

    @Test
    void maintJobAppliesAddUpdateDeleteAndSkipsComments_frS2113() throws Exception {
        Path input = OUTPUT.resolve("trantype-update.txt");
        Files.writeString(input, String.join(System.lineSeparator(),
                record("A", "09", "NEW TYPE DESC"),
                record("U", "01", "TYPE1 RENAMED"),
                record("D", "03", ""),
                "*comment line is skipped") + System.lineSeparator());

        jobs.setJob(jobBeans.get("tranTypeMaintJob"));
        var execution = jobs.launchJob(params(input));
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertEquals("NEW TYPE DESC", types.findById("09").orElseThrow().getDescription());
        assertEquals("TYPE1 RENAMED", types.findById("01").orElseThrow().getDescription());
        assertTrue(types.findById("03").isEmpty());
    }

    @Test
    void maintJobFailsWithRc4OnBadRecord_frS2113() throws Exception {
        Path input = OUTPUT.resolve("trantype-bad.txt");
        Files.writeString(input, String.join(System.lineSeparator(),
                record("X", "04", "BAD ACTION"),
                record("U", "01", "STILL APPLIED")) + System.lineSeparator());

        jobs.setJob(jobBeans.get("tranTypeMaintJob"));
        var execution = jobs.launchJob(params(input));
        assertEquals(BatchStatus.FAILED, execution.getStatus());
        String thrown = execution.getAllFailureExceptions().toString();
        assertTrue(thrown.contains("ERROR: TYPE NOT VALID"), thrown);
        // COBOL keeps applying after the RC4 — the update still lands.
        assertEquals("STILL APPLIED", types.findById("01").orElseThrow().getDescription());
    }

    @Test
    void maintJobFailsOnUpdateMiss_frS2113() throws Exception {
        Path input = OUTPUT.resolve("trantype-miss.txt");
        Files.writeString(input, record("U", "99", "NO SUCH TYPE")
                + System.lineSeparator());

        jobs.setJob(jobBeans.get("tranTypeMaintJob"));
        var execution = jobs.launchJob(params(input));
        assertEquals(BatchStatus.FAILED, execution.getStatus());
        assertTrue(execution.getAllFailureExceptions().toString()
                .contains("No records found."));
    }

    @Test
    void extractEmitsGoldenSixtyCharLayouts_frS2114() throws Exception {
        jobs.setJob(jobBeans.get("tranTypeExtractJob"));
        var execution = jobs.launchJob(new JobParametersBuilder()
                .addLong("run", System.nanoTime()).toJobParameters());
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        List<String> typeLines = Files.readAllLines(OUTPUT.resolve("TRANTYPE.PS"));
        assertEquals(3, typeLines.size());
        // TR_TYPE || desc-pad50 || '0'x8 — 60 chars, ordered by type.
        assertEquals("01" + "TYPE1 DESC" + " ".repeat(40) + "00000000",
                typeLines.get(0));
        assertEquals("03" + "TYPE3 DESC" + " ".repeat(40) + "00000000",
                typeLines.get(1));
        assertEquals("05" + "TYPE5 DESC" + " ".repeat(40) + "00000000",
                typeLines.get(2));
        assertEquals(60, typeLines.get(0).length());

        List<String> catLines = Files.readAllLines(OUTPUT.resolve("TRANCATG.PS"));
        assertEquals(1, catLines.size());
        // TRC_TYPE_CODE || cat-%04d || data-pad50 || '0'x4.
        assertEquals("05" + "0007" + "TYPE5 CAT DESC" + " ".repeat(36) + "0000",
                catLines.get(0));
        assertEquals(60, catLines.get(0).length());
    }
}
