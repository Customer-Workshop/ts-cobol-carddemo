package com.carddemo.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * CBPAUP0J/CBPAUP0C port (S-20 wave 2): PRM-INFO arrives as the {@code expiryDays},
 * {@code chkpFreq}, {@code chkpDispFreq} and {@code debug} job parameters (SYSIN
 * '00,00001,00001,Y' maps positionally: P-EXPIRY-DAYS 9(2), P-CHKP-FREQ X(5),
 * P-CHKP-DIS-FREQ X(5), P-DEBUG-FLAG X(1)). Defaults follow 1000-INITIALIZE:
 * non-numeric expiry → 5, blank/zero/non-numeric chkpFreq → 5, chkpDispFreq → 10,
 * debug ≠ 'Y' → 'N'. Each checkpoint interval (procCnt &gt; chkpFreq → batch of
 * chkpFreq+1 roots) commits in {@link PendingAuthPurgeService#purgeBatch}; the
 * in-loop CHKP logs 'CHKP SUCCESS' every chkpDispFreq checkpoints; any failure is
 * the 9999-ABEND RC16 → FAILED. The trailing 9000-TAKE-CHECKPOINT after the walk
 * is the final batch's commit.
 */
public class PendingAuthPurgeTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(PendingAuthPurgeTasklet.class);

    private final String expiryDaysParam;
    private final String chkpFreqParam;
    private final String chkpDispFreqParam;
    private final String debugParam;
    private final PendingAuthPurgeService service;

    public PendingAuthPurgeTasklet(String expiryDays, String chkpFreq,
                                   String chkpDispFreq, String debug,
                                   PendingAuthPurgeService service) {
        this.expiryDaysParam = expiryDays;
        this.chkpFreqParam = chkpFreq;
        this.chkpDispFreqParam = chkpDispFreq;
        this.debugParam = debug;
        this.service = service;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext)
            throws Exception {
        int expiryDays = parseNumericOr(expiryDaysParam, 5);
        int chkpFreq = parseFreqOr(chkpFreqParam, 5);
        int chkpDispFreq = parseFreqOr(chkpDispFreqParam, 10);
        boolean debug = "Y".equals(debugParam == null ? "" : debugParam.trim());
        LocalDate now = LocalDate.now();
        int currentYyddd = (now.getYear() % 100) * 1000 + now.getDayOfYear();
        log.info("STARTING PROGRAM CBPAUP0C::");
        log.info("*-------------------------------------*");
        log.info("CBPAUP0C PARM RECEIVED :{},{},{},{}",
                expiryDaysParam, chkpFreqParam, chkpDispFreqParam, debugParam);
        log.info("TODAYS DATE            :{}", currentYyddd);

        List<Long> acctIds = service.summaryIds();
        List<List<Long>> batches = new ArrayList<>();
        for (int i = 0; i < acctIds.size(); i += chkpFreq + 1) {
            batches.add(acctIds.subList(i, Math.min(i + chkpFreq + 1, acctIds.size())));
        }
        long sumRead = 0;
        long sumDeleted = 0;
        long dtlRead = 0;
        long dtlDeleted = 0;
        int chkpSeq = 0;
        int chkpSinceDisplay = 0;
        try {
            for (List<Long> batch : batches) {
                PendingAuthPurgeService.BatchOutcome outcome =
                        service.purgeBatch(batch, expiryDays, currentYyddd, debug);
                sumRead += batch.size();
                sumDeleted += outcome.summaryDeleted();
                dtlRead += outcome.dtlRead();
                dtlDeleted += outcome.dtlDeleted();
                // 9000-TAKE-CHECKPOINT: the batch commit just landed; WK-CHKPT-ID
                // is 'RMAD' + counter and progress is restart state (S20-B7).
                String chkpId = "RMAD" + "%04d".formatted(++chkpSeq);
                ExecutionContext context = executionContext(chunkContext);
                if (context != null) {
                    context.put("s20.chkpId", chkpId);
                    if (outcome.lastAcctId() != null) {
                        context.put("s20.lastAcctId", outcome.lastAcctId());
                    }
                    context.put("s20.summaryRead", sumRead);
                }
                if (++chkpSinceDisplay >= chkpDispFreq) {
                    chkpSinceDisplay = 0;
                    log.info("CHKP SUCCESS: AUTH COUNT - {}, APP ID - {}",
                            sumRead, outcome.lastAcctId());
                }
            }
        } catch (JobExecutionException | RuntimeException failure) {
            log.error("CBPAUP0C ABENDING ...", failure);
            throw new JobExecutionException("RC=16 " + failure.getMessage(), failure);
        }
        log.info(" ");
        log.info("*-------------------------------------*");
        log.info("# TOTAL SUMMARY READ  :{}", sumRead);
        log.info("# SUMMARY REC DELETED :{}", sumDeleted);
        log.info("# TOTAL DETAILS READ  :{}", dtlRead);
        log.info("# DETAILS REC DELETED :{}", dtlDeleted);
        log.info("*-------------------------------------*");
        log.info(" ");
        return RepeatStatus.FINISHED;
    }

    private static ExecutionContext executionContext(ChunkContext chunkContext) {
        return chunkContext == null ? null : chunkContext.getStepContext().getStepExecution()
                .getJobExecution().getExecutionContext();
    }

    /** P-EXPIRY-DAYS 9(2): only an all-numeric field overrides the default 5. */
    private static int parseNumericOr(String value, int fallback) {
        if (value == null || value.isBlank() || !value.trim().matches("\\d+")) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException failure) {
            return fallback;
        }
    }

    /** P-CHKP-FREQ / P-CHKP-DIS-FREQ X(5): spaces, zero or non-numeric → default. */
    private static int parseFreqOr(String value, int fallback) {
        int parsed = parseNumericOr(value, -1);
        return parsed <= 0 ? fallback : parsed;
    }
}
