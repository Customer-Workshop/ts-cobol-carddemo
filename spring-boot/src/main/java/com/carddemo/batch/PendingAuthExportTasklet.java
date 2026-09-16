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
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * PAUDBUNL + DBUNLDGS port (UNLDPADB.jcl/UNLDGSAM.jcl, S-20 wave 2): the GN
 * walk of PAUTSUM0 writes one root record per summary to OUTFIL1 and, under
 * each parent, the GNP children's 'key|child' records to OUTFIL2 — the
 * documented {@link PendingAuthCsv} layouts replace the 100-byte and 206-byte
 * fixed records (S20-B8). The QSAM (PAUDBUNL) and GSAM (DBUNLDGS) programs
 * collapse into this one implementation; the layout-equality test covers the
 * DBUNLDGS FRs. The {@code PA-ACCT-ID IS NUMERIC} guard is preserved as a
 * comment — a JPA key is always numeric.
 */
public class PendingAuthExportTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(PendingAuthExportTasklet.class);

    private final Path rootFile;
    private final Path childFile;
    private final PendingAuthSummaryRepository summaries;
    private final PendingAuthDetailRepository details;

    public PendingAuthExportTasklet(Path rootFile, Path childFile,
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
        long roots = 0;
        long children = 0;
        try {
            Files.createDirectories(rootFile.toAbsolutePath().getParent());
            Files.createDirectories(childFile.toAbsolutePath().getParent());
        } catch (IOException failure) {
            throw new JobExecutionException("ERROR IN OPENING OPFILE: " + failure.getMessage(), failure);
        }
        try (BufferedWriter rootOut = Files.newBufferedWriter(rootFile);
             BufferedWriter childOut = Files.newBufferedWriter(childFile)) {
            List<PendingAuthSummary> all = summaries.findAll(Sort.by("acctId"));
            for (PendingAuthSummary summary : all) {
                // PAUDBUNL.cbl:232 — OPFIL1 written only when PA-ACCT-ID numeric;
                // always true for a Long key.
                rootOut.write(PendingAuthCsv.summaryLine(summary));
                rootOut.newLine();
                roots++;
                List<PendingAuthDetail> kids = details
                        .findByIdAcctIdOrderByIdAuthDate9cAscIdAuthTime9cAsc(
                                summary.getAcctId(), Pageable.unpaged());
                for (PendingAuthDetail detail : kids) {
                    childOut.write(PendingAuthCsv.detailLine(detail));
                    childOut.newLine();
                    children++;
                }
            }
        } catch (IOException failure) {
            throw new JobExecutionException("ERROR WRITING OPFILE: " + failure.getMessage(), failure);
        }
        ExecutionContext context = chunkContext.getStepContext().getStepExecution()
                .getJobExecution().getExecutionContext();
        context.put("s20.exportRoots", roots);
        context.put("s20.exportChildren", children);
        log.info("*-------------------------------------*");
        log.info("PAUDBUNL ROOTS WRITTEN  :{}", roots);
        log.info("PAUDBUNL DETAILS WRITTEN:{}", children);
        log.info("*-------------------------------------*");
        return RepeatStatus.FINISHED;
    }
}
