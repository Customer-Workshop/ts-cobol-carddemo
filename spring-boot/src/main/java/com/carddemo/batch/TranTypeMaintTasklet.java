package com.carddemo.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.core.JobExecutionException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * MNTTRDB2 / COBTUPDT port: sweeps INPFILE's 53-byte records and applies each
 * through {@link BatchJobService#applyTranTypeRecord(String)}. The COBOL sets
 * RETURN-CODE 4 and keeps reading after a bad record (9999-ABEND), so the
 * tasklet applies every record, collects the verbatim error texts, and fails
 * the step afterward — S21-B5 maps RC4 to a FAILED job.
 */
public class TranTypeMaintTasklet implements Tasklet {
    private static final Logger log = LoggerFactory.getLogger(TranTypeMaintTasklet.class);

    private final Path input;
    private final BatchJobService service;

    public TranTypeMaintTasklet(Path input, BatchJobService service) {
        this.input = input;
        this.service = service;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext)
            throws Exception {
        List<String> lines;
        try {
            lines = Files.readAllLines(input);
        } catch (IOException exception) {
            throw new JobExecutionException(
                    "Error opening input file: " + exception.getMessage());
        }
        List<String> errors = new ArrayList<>();
        for (String line : lines) {
            String error = service.applyTranTypeRecord(line);
            if (error != null) {
                log.warn("COBTUPDT record rejected: {} ({})", error, line);
                errors.add(error);
            }
        }
        if (!errors.isEmpty()) {
            throw new JobExecutionException(
                    "RC=4 " + String.join(" | ", errors));
        }
        return RepeatStatus.FINISHED;
    }
}
