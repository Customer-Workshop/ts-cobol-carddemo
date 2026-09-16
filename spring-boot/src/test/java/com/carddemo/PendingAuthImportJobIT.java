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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FR-S20-11 parity matrix for PAUDBLOD: root ISRTs with 'II' tolerance,
 * child inserts gated on a present parent, non-numeric root keys skipped,
 * and RC16 on a missing parent. Inputs use the documented {@link PendingAuthCsv}
 * layouts that stand in for the INFIL1/INFIL2 fixed records (S20-B8).
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
        "carddemo.seed.enabled=false",
        "carddemo.mq.consumers.enabled=false",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:s20import;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "carddemo.batch.output-dir=target/s20-batch-output"
})
class PendingAuthImportJobIT {

    @Autowired private JobLauncherTestUtils jobs;
    @Autowired private Map<String, Job> jobBeans;
    @Autowired private PendingAuthSummaryRepository summaries;
    @Autowired private PendingAuthDetailRepository details;

    @TempDir Path dir;

    @BeforeEach
    void clean() {
        details.deleteAll();
        summaries.deleteAll();
        jobs.setJob(jobBeans.get("pendingAuthImportJob"));
    }

    private static String rootLine(long acctId, long custId) {
        PendingAuthSummary s = new PendingAuthSummary();
        s.setAcctId(acctId);
        s.setCustId(custId);
        s.setAuthStatus("A");
        s.setAccountStatus("ACTIVE");
        s.setCreditLimit(new BigDecimal("5000.00"));
        s.setCashLimit(new BigDecimal("1000.00"));
        s.setCreditBalance(new BigDecimal("120.00"));
        s.setCashBalance(BigDecimal.ZERO);
        s.setApprovedAuthCnt(2);
        s.setDeclinedAuthCnt(1);
        s.setApprovedAuthAmt(new BigDecimal("240.00"));
        s.setDeclinedAuthAmt(new BigDecimal("60.00"));
        return PendingAuthCsv.summaryLine(s);
    }

    private static String childLine(long acctId, int date9c, int time9c) {
        PendingAuthDetail d = new PendingAuthDetail();
        d.setId(new PendingAuthDetail.Id(acctId, date9c, time9c));
        d.setAuthOrigDate("240301");
        d.setAuthOrigTime("143512");
        d.setCardNum("1111222233334444");
        d.setAuthType("0500");
        d.setCardExpiryDate("2512");
        d.setMessageType("PAUT00");
        d.setMessageSource("MQSRC");
        d.setAuthIdCode("143512");
        d.setAuthRespCode("00");
        d.setAuthRespReason("0000");
        d.setProcessingCode(500);
        d.setTransactionAmt(new BigDecimal("175.50"));
        d.setApprovedAmt(new BigDecimal("175.50"));
        d.setMerchantCategoryCode("5411");
        d.setAcqrCountryCode("840");
        d.setPosEntryMode(5);
        d.setMerchantId("M00000123");
        d.setMerchantName("AMAZON MKTPLACE");
        d.setMerchantCity("SEATTLE");
        d.setMerchantState("WA");
        d.setMerchantZip("98101");
        d.setTransactionId("T-LOD-1");
        d.setMatchStatus("P");
        d.setAuthFraud(" ");
        d.setFraudRptDate("        ");
        return PendingAuthCsv.detailLine(d);
    }

    // NOTE: @SpringBatchTest treats any declared method returning JobExecution as a
    // job-execution factory — helpers return the status instead.
    private BatchStatus load(Path rootFile, Path childFile) throws Exception {
        JobParametersBuilder builder = new JobParametersBuilder()
                .addLong("run", System.nanoTime())
                .addString("rootFile", rootFile.toString())
                .addString("childFile", childFile.toString());
        return jobs.launchJob(builder.toJobParameters()).getStatus();
    }

    @Test
    void rootsAndChildrenInsert_frS20_11() throws Exception {
        Path roots = Files.write(dir.resolve("roots.txt"),
                List.of(rootLine(10L, 1L), rootLine(11L, 2L)));
        Path children = Files.write(dir.resolve("children.txt"),
                List.of(childLine(10L, 90001, 800001), childLine(10L, 90002, 800002)));
        assertEquals(BatchStatus.COMPLETED, load(roots, children));
        assertEquals(2, summaries.count());
        assertEquals(2, details.count());
        PendingAuthSummary loaded = summaries.findById(10L).orElseThrow();
        assertEquals(0, loaded.getApprovedAuthAmt().compareTo(new BigDecimal("240.00")));
    }

    @Test
    void duplicateRootIsTolerated_frS20_11() throws Exception {
        Path roots = Files.write(dir.resolve("roots.txt"),
                List.of(rootLine(10L, 1L), rootLine(10L, 9L))); // 'II' on 2nd
        Path children = Files.write(dir.resolve("children.txt"), List.of());
        assertEquals(BatchStatus.COMPLETED, load(roots, children));
        assertEquals(1, summaries.count());
        assertEquals(1L, summaries.findById(10L).orElseThrow().getCustId()); // first won
    }

    @Test
    void duplicateChildIsTolerated_frS20_11() throws Exception {
        Path roots = Files.write(dir.resolve("roots.txt"), List.of(rootLine(10L, 1L)));
        Path children = Files.write(dir.resolve("children.txt"),
                List.of(childLine(10L, 90001, 800001), childLine(10L, 90001, 800001)));
        assertEquals(BatchStatus.COMPLETED, load(roots, children));
        assertEquals(1, details.count());
    }

    @Test
    void nonNumericRootKeyIsSkipped_frS20_11() throws Exception {
        Path roots = Files.write(dir.resolve("roots.txt"), List.of(rootLine(10L, 1L)));
        Path children = Files.write(dir.resolve("children.txt"),
                List.of("XXXXXX|" + childLine(10L, 90001, 800001).split("\\|")[1],
                        childLine(10L, 90002, 800002)));
        assertEquals(BatchStatus.COMPLETED, load(roots, children));
        assertEquals(1, details.count());     // only the numeric-keyed child
    }

    @Test
    void childWithoutParentFailsRc16_frS20_11() throws Exception {
        Path roots = Files.write(dir.resolve("roots.txt"), List.of(rootLine(10L, 1L)));
        Path children = Files.write(dir.resolve("children.txt"),
                List.of(childLine(77L, 90001, 800001)));    // orphan
        assertEquals(BatchStatus.FAILED, load(roots, children));
    }

    @Test
    void missingInputFileFailsLikeMissingDd_frS20_11() throws Exception {
        assertEquals(BatchStatus.FAILED, load(dir.resolve("nope1"), dir.resolve("nope2")));
    }

    @Test
    void layoutIsRoundTripStable_frS20_11() {
        // The export path writes exactly what the import path reads.
        PendingAuthSummary roundTripped =
                PendingAuthCsv.parseSummary(PendingAuthCsv.summaryLine(parseReady()));
        assertEquals(10L, roundTripped.getAcctId());
        assertEquals("A", roundTripped.getAccountStatus());
        PendingAuthDetail detail = PendingAuthCsv.parseDetail(childLine(10L, 90001, 800001));
        assertEquals("AMAZON MKTPLACE", detail.getMerchantName());
        assertEquals(Integer.valueOf(5), detail.getPosEntryMode());
        assertEquals("        ", detail.getFraudRptDate());
    }

    private static PendingAuthSummary parseReady() {
        PendingAuthSummary s = new PendingAuthSummary();
        s.setAcctId(10L);
        s.setCustId(1L);
        s.setAccountStatus("A");
        return s;
    }
}
