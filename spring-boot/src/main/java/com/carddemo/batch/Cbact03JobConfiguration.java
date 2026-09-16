package com.carddemo.batch;

import com.carddemo.model.CardXref;
import com.carddemo.repository.CardXrefRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * READXREF (CBACT03C): sequential verify pass over the card_xrefs table.
 * Parity quirk preserved: CARD-XREF-RECORD is displayed twice per row — once
 * inside the read paragraph (CBACT03C.cbl:96) and once in the main loop
 * (CBACT03C.cbl:78) — here mapped to processor and writer respectively.
 */
@Configuration
public class Cbact03JobConfiguration {

    @Bean
    public Job readxrefJob(JobRepository repository, Step readxrefStep) {
        return new JobBuilder("readxrefJob", repository)
                .incrementer(new RunIdIncrementer())
                .listener(new Abend999JobListener())
                .start(readxrefStep).build();
    }

    @Bean
    @StepScope
    public RepositoryItemReader<CardXref> readxrefReader(CardXrefRepository repository) {
        Map<String, Sort.Direction> sorts = new LinkedHashMap<>();
        sorts.put("xrefCardNumber", Sort.Direction.ASC);
        return new RepositoryItemReaderBuilder<CardXref>()
                .name("readxrefReader").repository(repository)
                .methodName("findAll").pageSize(50)
                .sorts(sorts).build();
    }

    @Bean
    public ItemProcessor<CardXref, CardXref> readxrefProcessor() {
        return xref -> {
            ReadVerifySupport.SYSOUT.info("{}", ReadVerifySupport.xrefImage(xref));
            return xref;
        };
    }

    @Bean
    public ItemWriter<CardXref> readxrefWriter() {
        return chunk -> chunk.getItems()
                .forEach(xref -> ReadVerifySupport.SYSOUT.info("{}", ReadVerifySupport.xrefImage(xref)));
    }

    @Bean
    public Step readxrefStep(JobRepository repository, PlatformTransactionManager transactionManager,
                             RepositoryItemReader<CardXref> readxrefReader,
                             ItemProcessor<CardXref, CardXref> readxrefProcessor,
                             ItemWriter<CardXref> readxrefWriter) {
        return new StepBuilder("readxrefStep", repository)
                .<CardXref, CardXref>chunk(1, transactionManager)
                .reader(readxrefReader).processor(readxrefProcessor).writer(readxrefWriter)
                .listener(new ReadVerifyStepListener("CBACT03C")).build();
    }
}
