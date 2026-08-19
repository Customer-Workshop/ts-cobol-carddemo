package com.carddemo.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.file.transform.PassThroughLineAggregator;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Path;

@Configuration
public class Cbtrn01JobConfiguration {
    @Bean
    public Job cbtrn01Job(JobRepository repository, Step cbtrn01Step) {
        return new JobBuilder("cbtrn01Job", repository)
                .incrementer(new RunIdIncrementer()).start(cbtrn01Step).build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<String> cbtrn01LinesReader(
            @Value("#{jobParameters['dailyFile']}") String file,
            @Value("${carddemo.seed.data-dir:../app/data}") String dataDirectory) {
        Path path = file == null || file.isBlank()
                ? Path.of(dataDirectory, "ASCII", "dailytran.txt") : Path.of(file);
        return new FlatFileItemReaderBuilder<String>()
                .name("cbtrn01Reader").resource(new FileSystemResource(path))
                .lineMapper((line, lineNumber) -> line)
                .build();
    }

    @Bean
    @StepScope
    public ItemStreamReader<DailyTransactionRecord> cbtrn01Reader(
            @org.springframework.beans.factory.annotation.Qualifier("cbtrn01LinesReader")
            FlatFileItemReader<String> lines) {
        return new DailyTransactionReader(lines);
    }

    @Bean
    @StepScope
    public FlatFileItemWriter<String> cbtrn01Writer(BatchJobService service) {
        return new FlatFileItemWriterBuilder<String>()
                .name("cbtrn01Writer")
                .resource(new FileSystemResource(service.output("cbtrn01-validation.txt")))
                .lineAggregator(new PassThroughLineAggregator<>())
                .shouldDeleteIfExists(true)
                .build();
    }

    @Bean
    public ItemProcessor<DailyTransactionRecord, String> cbtrn01Processor(
            BatchJobService service) {
        return service::validateDaily;
    }

    @Bean
    public Step cbtrn01Step(JobRepository repository, PlatformTransactionManager transactionManager,
                            ItemStreamReader<DailyTransactionRecord> cbtrn01Reader,
                            ItemProcessor<DailyTransactionRecord, String> cbtrn01Processor,
                            FlatFileItemWriter<String> cbtrn01Writer) {
        return new StepBuilder("cbtrn01Step", repository)
                .<DailyTransactionRecord, String>chunk(20, transactionManager)
                .reader(cbtrn01Reader).processor(cbtrn01Processor).writer(cbtrn01Writer).build();
    }
}
