package com.carddemo;

import com.carddemo.batch.PendingAuthCsv;
import com.carddemo.model.PendingAuthDetail;
import com.carddemo.model.PendingAuthSummary;
import com.carddemo.repository.PendingAuthDetailRepository;
import com.carddemo.repository.PendingAuthSummaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * FR-S20-12 parity matrix for PAUDBUNL (QSAM) — the single implementation
 * also covers DBUNLDGS (GSAM) because both programs emit identical records;
 * the round-trip into PAUDBLOD's layout is the coverage.
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
        "carddemo.seed.enabled=false",
        "carddemo.mq.consumers.enabled=false",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:s20export;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "carddemo.batch.output-dir=target/s20-batch-output"
})
class PendingAuthExportJobIT {

    @Autowired private JobLauncherTestUtils jobs;
    @Autowired private Map<String, Job> jobBeans;
    @Autowired private PendingAuthSummaryRepository summaries;
    @Autowired private PendingAuthDetailRepository details;

    @TempDir Path dir;

    @BeforeEach
    void clean() {
        details.deleteAll();
        summaries.deleteAll();
        jobs.setJob(jobBeans.get("pendingAuthExportJob"));
    }

    private void seed() {
        for (long acctId : List.of(20L, 10L, 30L)) {      // out of order on purpose
            PendingAuthSummary s = new PendingAuthSummary();
            s.setAcctId(acctId);
            s.setCustId(acctId + 1);
            s.setApprovedAuthCnt(1);
            summaries.save(s);
        }
        detail(10L, 95000, 111, "00");
        detail(10L, 94000, 222, "05");
        detail(20L, 93000, 333, "00");
    }

    private void detail(long acctId, int date9c, int time9c, String respCode) {
        PendingAuthDetail d = new PendingAuthDetail();
        d.setId(new PendingAuthDetail.Id(acctId, date9c, time9c));
        d.setCardNum("1111222233334444");
        d.setAuthRespCode(respCode);
        d.setAuthRespReason(respCode.equals("00") ? "0000" : "4100");
        d.setTransactionAmt(new BigDecimal("10.00"));
        d.setApprovedAmt(respCode.equals("00") ? new BigDecimal("10.00") : BigDecimal.ZERO);
        d.setTransactionId("T" + time9c);
        details.save(d);
    }

    @Test
    void exportsRootsInGnOrderAndChildrenUnderEachParent_frS20_12() throws Exception {
        seed();
        Path rootFile = dir.resolve("PAUTDB.ROOT.FILEO");
        Path childFile = dir.resolve("PAUTDB.CHILD.FILEO");
        JobExecution execution = jobs.launchJob(new JobParametersBuilder()
                .addLong("run", System.nanoTime())
                .addString("rootFile", rootFile.toString())
                .addString("childFile", childFile.toString())
                .toJobParameters());
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        List<String> roots = Files.readAllLines(rootFile);
        assertEquals(3, roots.size());
        // GN order = ascending acct id, not insert order
        assertEquals(10L, PendingAuthCsv.parseSummary(roots.get(0)).getAcctId());
        assertEquals(20L, PendingAuthCsv.parseSummary(roots.get(1)).getAcctId());
        assertEquals(30L, PendingAuthCsv.parseSummary(roots.get(2)).getAcctId());

        List<String> children = Files.readAllLines(childFile);
        assertEquals(3, children.size());
        PendingAuthDetail c0 = PendingAuthCsv.parseDetail(children.get(0));
        PendingAuthDetail c1 = PendingAuthCsv.parseDetail(children.get(1));
        PendingAuthDetail c2 = PendingAuthCsv.parseDetail(children.get(2));
        // GNP children rise under each parent in complement-asc order
        assertEquals(10L, c0.getId().getAcctId());
        assertEquals(94000, c0.getId().getAuthDate9c());   // lower complement = newer first
        assertEquals(95000, c1.getId().getAuthDate9c());
        assertEquals(20L, c2.getId().getAcctId());
    }

    @Test
    void exportedFilesReloadThroughImportLayout_frS20_11_12() throws Exception {
        seed();
        Path rootFile = dir.resolve("PAUTDB.ROOT.FILEO");
        Path childFile = dir.resolve("PAUTDB.CHILD.FILEO");
        jobs.launchJob(new JobParametersBuilder()
                .addLong("run", System.nanoTime())
                .addString("rootFile", rootFile.toString())
                .addString("childFile", childFile.toString())
                .toJobParameters());
        // Both datasets must parse cleanly as PAUDBLOD input records.
        List<String> roots = Files.readAllLines(rootFile);
        List<String> children = Files.readAllLines(childFile);
        assertEquals(3, roots.stream().map(PendingAuthCsv::parseSummary).count());
        assertEquals(3, children.stream().map(PendingAuthCsv::parseDetail).count());
    }

    @Test
    void defaultOutputTargetsPautdbDatasets_frS20_12() throws Exception {
        seed();
        JobExecution execution = jobs.launchJob(new JobParametersBuilder()
                .addLong("run", System.nanoTime()).toJobParameters());
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        Path defaultOut = Path.of("target/s20-batch-output/PAUTDB.ROOT.FILEO");
        assertEquals(3, Files.readAllLines(defaultOut).size());
        Path defaultChild = Path.of("target/s20-batch-output/PAUTDB.CHILD.FILEO");
        assertEquals(3, Files.readAllLines(defaultChild).size());
    }

    @Test
    void emptyDatabaseWritesEmptyFiles_frS20_12() throws Exception {
        Path rootFile = dir.resolve("empty-root");
        Path childFile = dir.resolve("empty-child");
        JobExecution execution = jobs.launchJob(new JobParametersBuilder()
                .addLong("run", System.nanoTime())
                .addString("rootFile", rootFile.toString())
                .addString("childFile", childFile.toString())
                .toJobParameters());
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertEquals(List.of(), Files.readAllLines(rootFile));
    }

    @Test
    void unwritableOutputFails_frS20_12() throws Exception {
        seed();
        JobExecution execution = jobs.launchJob(new JobParametersBuilder()
                .addLong("run", System.nanoTime())
                .addString("rootFile", dir.toString())   // a directory, not a file
                .addString("childFile", dir.resolve("y").toString())
                .toJobParameters());
        assertEquals(BatchStatus.FAILED, execution.getStatus());
    }
}
