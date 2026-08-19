package com.carddemo.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.file.transform.PassThroughLineAggregator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class CbimportJobConfiguration {
    @Bean
    public Job cbimportJob(JobRepository repository, Step cbimportStep) {
        return new JobBuilder("cbimportJob", repository)
                .incrementer(new RunIdIncrementer()).start(cbimportStep).build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<String> cbimportReader(
            BatchJobService service,
            @Value("#{jobParameters['inputFile']}") String input) {
        Path path = input == null || input.isBlank() ? service.output("EXPORT.DATA") : Path.of(input);
        return new FlatFileItemReaderBuilder<String>()
                .name("cbimportReader").resource(new FileSystemResource(path))
                .lineMapper((line, lineNumber) -> line).build();
    }

    @Bean
    @StepScope
    public ItemProcessor<String, String> cbimportProcessor(BatchJobService service) {
        return new ItemProcessor<>() {
            private long record;

            @Override
            public String process(String item) {
                BatchJobService.ImportResult result = service.importRecord(item, ++record);
                return result.error() == null ? null
                        : "RECORD " + result.recordNumber() + ": " + result.error();
            }
        };
    }

    @Bean
    @StepScope
    public FlatFileItemWriter<String> cbimportWriter(BatchJobService service) {
        return new FlatFileItemWriterBuilder<String>()
                .name("cbimportWriter")
                .resource(new FileSystemResource(service.output("CBIMPORT.errors")))
                .lineAggregator(new PassThroughLineAggregator<>())
                .shouldDeleteIfExists(true)
                .build();
    }

    @Bean
    public Step cbimportStep(JobRepository repository, PlatformTransactionManager transactionManager,
                             FlatFileItemReader<String> cbimportReader,
                             ItemProcessor<String, String> cbimportProcessor,
                             FlatFileItemWriter<String> cbimportWriter) {
        return new StepBuilder("cbimportStep", repository)
                .<String, String>chunk(50, transactionManager)
                .reader(cbimportReader).processor(cbimportProcessor).writer(cbimportWriter).build();
    }
}
