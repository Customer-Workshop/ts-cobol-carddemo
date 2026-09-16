package com.carddemo.batch;

import com.carddemo.model.Card;
import com.carddemo.repository.CardRepository;
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
 * READCARD (CBACT02C): sequential verify pass over the cards table; each
 * CARD-RECORD is displayed once to the job log (the in-read DISPLAY is
 * commented out in the source, CBACT02C.cbl:96, so the dump is single).
 */
@Configuration
public class Cbact02JobConfiguration {

    @Bean
    public Job readcardJob(JobRepository repository, Step readcardStep) {
        return new JobBuilder("readcardJob", repository)
                .incrementer(new RunIdIncrementer())
                .listener(new Abend999JobListener())
                .start(readcardStep).build();
    }

    @Bean
    @StepScope
    public RepositoryItemReader<Card> readcardReader(CardRepository repository) {
        Map<String, Sort.Direction> sorts = new LinkedHashMap<>();
        sorts.put("cardNumber", Sort.Direction.ASC);
        return new RepositoryItemReaderBuilder<Card>()
                .name("readcardReader").repository(repository)
                .methodName("findAll").pageSize(50)
                .sorts(sorts).build();
    }

    @Bean
    public ItemProcessor<Card, Card> readcardProcessor() {
        return card -> card;
    }

    @Bean
    public ItemWriter<Card> readcardWriter() {
        return chunk -> chunk.getItems()
                .forEach(card -> ReadVerifySupport.SYSOUT.info("{}", ReadVerifySupport.cardImage(card)));
    }

    @Bean
    public Step readcardStep(JobRepository repository, PlatformTransactionManager transactionManager,
                             RepositoryItemReader<Card> readcardReader,
                             ItemProcessor<Card, Card> readcardProcessor,
                             ItemWriter<Card> readcardWriter) {
        return new StepBuilder("readcardStep", repository)
                .<Card, Card>chunk(1, transactionManager)
                .reader(readcardReader).processor(readcardProcessor).writer(readcardWriter)
                .listener(new ReadVerifyStepListener("CBACT02C")).build();
    }
}
