package com.carddemo.batch;

import com.carddemo.model.TransactionCategory;
import com.carddemo.model.TransactionType;
import com.carddemo.repository.TransactionCategoryRepository;
import com.carddemo.repository.TransactionTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * TRANEXTR (S21-B6): the two DSNTIAUL unloads land as fixed 60-char flat
 * files — TRANTYPE.PS (type || desc(50) || '0'x8, ordered by type) and
 * TRANCATG.PS (type || category(4, zero-padded) || data(50) || '0'x4,
 * ordered by type then category).
 */
public class TranTypeExtractTasklet implements Tasklet {
    private static final Logger log = LoggerFactory.getLogger(TranTypeExtractTasklet.class);

    private final TransactionTypeRepository types;
    private final TransactionCategoryRepository categories;
    private final BatchJobService service;

    public TranTypeExtractTasklet(TransactionTypeRepository types,
                                  TransactionCategoryRepository categories,
                                  BatchJobService service) {
        this.types = types;
        this.categories = categories;
        this.service = service;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext)
            throws Exception {
        List<String> typeLines = new ArrayList<>();
        for (TransactionType type : types.findAllByOrderByTranType()) {
            typeLines.add(service.tranTypeExtractLine(type));
        }
        List<String> categoryLines = new ArrayList<>();
        for (TransactionCategory category
                : categories.findAllByOrderByIdTranTypeCodeAscIdTranCategoryCodeAsc()) {
            categoryLines.add(service.tranCategoryExtractLine(category));
        }
        Path typeOut = service.output("TRANTYPE.PS");
        Path catOut = service.output("TRANCATG.PS");
        Files.createDirectories(typeOut.getParent());
        Files.createDirectories(catOut.getParent());
        Files.write(typeOut, typeLines);
        Files.write(catOut, categoryLines);
        log.info("TRANEXTR: {} types -> {}, {} categories -> {}",
                typeLines.size(), typeOut, categoryLines.size(), catOut);
        return RepeatStatus.FINISHED;
    }
}
