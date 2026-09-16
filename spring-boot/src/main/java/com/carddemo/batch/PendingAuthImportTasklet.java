package com.carddemo.batch;

import com.carddemo.model.PendingAuthDetail;
import com.carddemo.model.PendingAuthSummary;
import com.carddemo.repository.PendingAuthDetailRepository;
import com.carddemo.repository.PendingAuthSummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * PAUDBLOD port (LOADPADB.jcl, S-20 wave 2): INFILE1 root records → PAUTSUM0
 * ISRT, INFILE2 'key|child' records → GU parent + PAUTDTL1 ISRT. 'II'
 * duplicates are tolerated and counted on both files; a missing parent or any
 * other failure is the 9999-ABEND RC16 → FAILED (PAUDBLOD.cbl 2100/3100
 * paragraphs). Inputs are the documented CSV layouts of
 * {@link PendingAuthCsv}; the DD names default to the unload datasets
 * PAUTDB.ROOT.FILEO / PAUTDB.CHILD.FILEO under the batch output dir, matching
 * the JCL where INFILE1/2 and OUTFIL1/2 share the same DSNs.
 */
public class PendingAuthImportTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(PendingAuthImportTasklet.class);

    private final Path rootFile;
    private final Path childFile;
    private final PendingAuthSummaryRepository summaries;
    private final PendingAuthDetailRepository details;

    public PendingAuthImportTasklet(Path rootFile, Path childFile,
                                    PendingAuthSummaryRepository summaries,
                                    PendingAuthDetailRepository details) {
        this.rootFile = rootFile;
        this.childFile = childFile;
        this.summaries = summaries;
        this.details = details;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext)
            throws Exception {
        log.info("STARTING PAUDBLOD");
        List<String> rootLines = readAll(rootFile, "INFILE1");
        List<String> childLines = readAll(childFile, "INFILE2");

        long rootRead = 0;
        long rootInserted = 0;
        long rootDups = 0;
        for (String line : rootLines) {
            if (line.isBlank()) {
                continue;
            }
            rootRead++;
            PendingAuthSummary summary;
            try {
                summary = PendingAuthCsv.parseSummary(line);
            } catch (IllegalArgumentException failure) {
                throw new JobExecutionException("ROOT INSERT FAILED: " + failure.getMessage());
            }
            if (summaries.existsById(summary.getAcctId())) {
                rootDups++;
                log.debug("ROOT SEGMENT ALREADY IN DB {}", summary.getAcctId());
                continue;
            }
            try {
                summaries.save(summary);
            } catch (RuntimeException failure) {
                throw new JobExecutionException(
                        "ROOT INSERT FAILED acct " + summary.getAcctId(), failure);
            }
            rootInserted++;
            log.debug("ROOT INSERT SUCCESS {}", summary.getAcctId());
        }

        long childRead = 0;
        long childInserted = 0;
        long childDups = 0;
        for (String line : childLines) {
            if (line.isBlank()) {
                continue;
            }
            childRead++;
            PendingAuthDetail detail;
            try {
                detail = PendingAuthCsv.parseDetail(line);
            } catch (IllegalArgumentException failure) {
                throw new JobExecutionException("INSERT CALL FAIL FOR CHILD: " + failure.getMessage());
            }
            if (detail == null) {
                continue;   // ROOT-SEG-KEY not numeric — record skipped (:275)
            }
            Long acctId = detail.getId().getAcctId();
            if (!summaries.existsById(acctId)) {
                // GU on the parent root misses → 9999-ABEND (:303-308).
                throw new JobExecutionException("ROOT GU CALL FAIL:GE acct " + acctId);
            }
            if (details.existsById(detail.getId())) {
                childDups++;
                log.debug("CHILD SEGMENT ALREADY IN DB {}", detail.getId());
                continue;
            }
            try {
                details.save(detail);
            } catch (RuntimeException failure) {
                throw new JobExecutionException(
                        "INSERT CALL FAIL FOR CHILD acct " + acctId, failure);
            }
            childInserted++;
            log.debug("CHILD SEGMENT INSERTED SUCCESS {}", detail.getId());
        }

        log.info("*-------------------------------------*");
        log.info("PAUDBLOD ROOT READ    :{}", rootRead);
        log.info("PAUDBLOD ROOT INSERTED:{}", rootInserted);
        log.info("PAUDBLOD ROOT DUPS    :{}", rootDups);
        log.info("PAUDBLOD DTL READ     :{}", childRead);
        log.info("PAUDBLOD DTL INSERTED :{}", childInserted);
        log.info("PAUDBLOD DTL DUPS     :{}", childDups);
        log.info("*-------------------------------------*");
        return RepeatStatus.FINISHED;
    }

    private static List<String> readAll(Path file, String ddName) throws JobExecutionException {
        try {
            return Files.readAllLines(file);
        } catch (IOException failure) {
            throw new JobExecutionException(
                    "ERROR IN OPENING " + ddName + ": " + failure.getMessage(), failure);
        }
    }
}
