package com.carddemo;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.carddemo.batch.PendingAuthPurgeService;
import com.carddemo.batch.PendingAuthPurgeTasklet;
import com.carddemo.model.PendingAuthDetail;
import com.carddemo.model.PendingAuthSummary;
import com.carddemo.repository.PendingAuthDetailRepository;
import com.carddemo.repository.PendingAuthSummaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FR-S20-09/10 parity matrix for CBPAUP0J/CBPAUP0C: SYSIN parm defaults,
 * the YYDDD expiry compare, checkpoint batching/display, the doubled
 * approved-count delete guard, counter non-persistence, and RC16 failure.
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
        "carddemo.seed.enabled=false",
        "carddemo.mq.consumers.enabled=false",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:s20purge;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "carddemo.batch.output-dir=target/s20-batch-output"
})
class PendingAuthPurgeJobIT {

    @Autowired private JobLauncherTestUtils jobs;
    @Autowired private Map<String, Job> jobBeans;
    @Autowired private PendingAuthSummaryRepository summaries;
    @Autowired private PendingAuthDetailRepository details;
    @Autowired private PendingAuthPurgeService purgeService;

    @BeforeEach
    void clean() {
        details.deleteAll();
        summaries.deleteAll();
        jobs.setJob(jobBeans.get("pendingAuthPurgeJob"));
    }

    private static int yyddd(LocalDate date) {
        return (date.getYear() % 100) * 1000 + date.getDayOfYear();
    }

    /** date9c complement for a detail authored {@code daysAgo} days back. */
    private static int date9cDaysAgo(int daysAgo) {
        return 99999 - yyddd(LocalDate.now().minusDays(daysAgo));
    }

    private PendingAuthSummary summary(long acctId, int apprCnt, int declCnt, String apprAmt) {
        PendingAuthSummary s = new PendingAuthSummary();
        s.setAcctId(acctId);
        s.setCustId(acctId);
        s.setApprovedAuthCnt(apprCnt);
        s.setDeclinedAuthCnt(declCnt);
        s.setApprovedAuthAmt(new BigDecimal(apprAmt));
        s.setDeclinedAuthAmt(BigDecimal.ZERO);
        s.setCreditLimit(BigDecimal.ZERO);
        s.setCashLimit(BigDecimal.ZERO);
        s.setCreditBalance(BigDecimal.ZERO);
        s.setCashBalance(BigDecimal.ZERO);
        return summaries.save(s);
    }

    private int timeSeq = 100001;

    private PendingAuthDetail detail(long acctId, int date9c, String respCode, String amount) {
        PendingAuthDetail d = new PendingAuthDetail();
        d.setId(new PendingAuthDetail.Id(acctId, date9c, timeSeq++));
        d.setAuthRespCode(respCode);
        d.setTransactionAmt(new BigDecimal(amount));
        d.setApprovedAmt(new BigDecimal(amount));
        return details.save(d);
    }

    // NOTE: @SpringBatchTest treats any declared method returning JobExecution as a
    // job-execution factory — helpers must return the status and launch inline
    // where the execution itself is needed.
    private BatchStatus purge(Map<String, String> params) throws Exception {
        JobParametersBuilder builder = new JobParametersBuilder()
                .addLong("run", System.nanoTime());
        params.forEach(builder::addString);
        return jobs.launchJob(builder.toJobParameters()).getStatus();
    }

    @Test
    void expiredDetailDeletedAndEmptySummaryDropped_frS20_09() throws Exception {
        summary(1L, 1, 0, "100.00");
        detail(1L, date9cDaysAgo(30), "00", "100.00");   // approved, expired
        summary(2L, 1, 0, "10.00");
        detail(2L, date9cDaysAgo(1), "00", "10.00");     // approved, recent

        BatchStatus status = purge(Map.of("expiryDays", "5", "debug", "N"));

        assertEquals(BatchStatus.COMPLETED, status);
        assertTrue(summaries.findById(1L).isEmpty());     // 1-1=0 → guard fired
        assertTrue(summaries.findById(2L).isPresent());
        assertEquals(1, details.count());                 // only the recent detail left
    }

    @Test
    void survivingSummaryKeepsStoredCounters_frS20_09() throws Exception {
        // Decrements live only in the segment buffer — nothing is REPL'd.
        summary(1L, 2, 1, "200.00");
        detail(1L, date9cDaysAgo(30), "05", "50.00");     // declined, expired
        detail(1L, date9cDaysAgo(30), "00", "75.00");     // approved, expired
        detail(1L, date9cDaysAgo(1), "00", "80.00");      // recent, survives

        purge(Map.of("expiryDays", "5"));

        PendingAuthSummary s = summaries.findById(1L).orElseThrow();
        assertEquals(2, s.getApprovedAuthCnt());          // decrement NOT persisted
        assertEquals(1, s.getDeclinedAuthCnt());
        assertEquals(0, s.getApprovedAuthAmt().compareTo(new BigDecimal("200.00")));
        assertEquals(1, details.count());
    }

    @Test
    void doubleApprovedGuardIgnoresDeclinedCount_frS20_09() throws Exception {
        // PA-APPROVED-AUTH-CNT is tested twice verbatim — a summary with a
        // positive declined count still deletes once approved hits zero.
        summary(1L, 0, 5, "0.00");                        // no details at all

        purge(Map.of("expiryDays", "5"));

        assertTrue(summaries.findById(1L).isEmpty());
    }

    @Test
    void expiryBoundaryIsInclusive_frS20_09() throws Exception {
        summary(1L, 3, 0, "30.00");
        detail(1L, date9cDaysAgo(5), "00", "10.00");      // exactly 5 → deleted
        detail(1L, date9cDaysAgo(4), "00", "10.00");      // inside window → kept

        purge(Map.of("expiryDays", "5"));

        assertEquals(1, details.count());
        assertTrue(summaries.findById(1L).isPresent());   // appr 3-1=2 > 0
    }

    @Test
    void blankAndNonNumericParmsFallBackToDefaults_frS20_09() throws Exception {
        summary(1L, 2, 0, "20.00");
        detail(1L, date9cDaysAgo(6), "00", "10.00");      // > default 5 → deleted
        detail(1L, date9cDaysAgo(4), "00", "10.00");      // ≤ default 5 → kept

        purge(Map.of("expiryDays", "XX"));                // non-numeric → 5

        assertEquals(1, details.count());
        assertTrue(summaries.findById(1L).isPresent());
    }

    @Test
    void checkpointIdAndDisplayCadence_frS20_10() throws Exception {
        for (long id = 1; id <= 7; id++) {
            summary(id, 9, 0, "1.00");
        }
        Logger logger = (Logger) LoggerFactory.getLogger(PendingAuthPurgeTasklet.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            // chkpFreq 2 → batches of 3: [3,3,1] → 3 checkpoints
            JobExecution execution = jobs.launchJob(new JobParametersBuilder()
                    .addLong("run", System.nanoTime())
                    .addString("chkpFreq", "2")
                    .addString("chkpDispFreq", "2")
                    .addString("expiryDays", "999")
                    .toJobParameters());
            assertEquals(BatchStatus.COMPLETED, execution.getStatus());
            long chkpLogs = events.list.stream()
                    .filter(e -> e.getFormattedMessage().startsWith("CHKP SUCCESS: AUTH COUNT"))
                    .count();
            assertEquals(1, chkpLogs);                    // displayed every 2nd chkp
            assertEquals("RMAD0003", execution.getExecutionContext().get("s20.chkpId"));
            assertEquals(7L, execution.getExecutionContext().get("s20.summaryRead"));
        } finally {
            logger.detachAppender(events);
        }
    }

    @Test
    void deleteFailureEndsJobAsRc16_frS20_09() {
        // Any DLET/read failure → 9999-ABEND → the step throws → FAILED.
        PendingAuthPurgeService failing = new PendingAuthPurgeService(null, null) {
            @Override
            public List<Long> summaryIds() {
                return List.of(1L);
            }

            @Override
            public BatchOutcome purgeBatch(List<Long> acctIds, int expiryDays,
                                           int currentYyddd, boolean debug)
                    throws JobExecutionException {
                throw new JobExecutionException("AUTH DETAIL DELETE FAILED");
            }
        };
        PendingAuthPurgeTasklet tasklet =
                new PendingAuthPurgeTasklet("5", "5", "10", "N", failing);
        JobExecutionException failure = assertThrows(JobExecutionException.class,
                () -> tasklet.execute(null, null));
        assertTrue(failure.getMessage().contains("RC=16"));
    }

    @Test
    void debugFlagLogsAndRejectNonY_frS20_10() throws Exception {
        summary(1L, 1, 0, "5.00");
        detail(1L, date9cDaysAgo(30), "00", "5.00");
        Logger logger = (Logger) LoggerFactory.getLogger(PendingAuthPurgeService.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            purge(Map.of("debug", "X"));                  // anything but 'Y' → off
            assertTrue(events.list.stream()
                    .noneMatch(e -> e.getFormattedMessage().contains("DEBUG: AUTH")));
            // seed a fresh pair to exercise debug lines
            summary(2L, 1, 0, "5.00");
            detail(2L, date9cDaysAgo(30), "00", "5.00");
            purge(Map.of("debug", "Y"));
            assertTrue(events.list.stream()
                    .anyMatch(e -> e.getFormattedMessage().contains("DEBUG: AUTH DTL DLET")));
        } finally {
            logger.detachAppender(events);
        }
    }
}
