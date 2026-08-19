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
public class Cbtrn02JobConfiguration {
    @Bean
    public Job cbtrn02Job(JobRepository repository, Step cbtrn02Step) {
        return new JobBuilder("cbtrn02Job", repository)
                .incrementer(new RunIdIncrementer()).start(cbtrn02Step).build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<String> cbtrn02LinesReader(
            @Value("#{jobParameters['dailyFile']}") String file,
            @Value("${carddemo.seed.data-dir:../app/data}") String dataDirectory) {
        Path path = file == null || file.isBlank()
                ? Path.of(dataDirectory, "ASCII", "dailytran.txt") : Path.of(file);
        return new FlatFileItemReaderBuilder<String>()
                .name("cbtrn02Reader").resource(new FileSystemResource(path))
                .lineMapper((line, lineNumber) -> line)
                .build();
    }

    @Bean
    @StepScope
    public ItemStreamReader<DailyTransactionRecord> cbtrn02Reader(
            @org.springframework.beans.factory.annotation.Qualifier("cbtrn02LinesReader")
            FlatFileItemReader<String> lines) {
        return new DailyTransactionReader(lines);
    }

    @Bean
    @StepScope
    public FlatFileItemWriter<String> cbtrn02Writer(BatchJobService service) {
        return new FlatFileItemWriterBuilder<String>()
                .name("cbtrn02Writer")
                .resource(new FileSystemResource(service.output("cbtrn02-rejects.txt")))
                .lineAggregator(new PassThroughLineAggregator<>())
                .shouldDeleteIfExists(true)
                .build();
    }

    @Bean
    public ItemProcessor<DailyTransactionRecord, String> cbtrn02Processor(
            BatchJobService service) {
        return record -> {
            BatchJobService.PostResult result = service.postDaily(record);
            return result.reject();
        };
    }

    @Bean
    public Step cbtrn02Step(JobRepository repository, PlatformTransactionManager transactionManager,
                            ItemStreamReader<DailyTransactionRecord> cbtrn02Reader,
                            ItemProcessor<DailyTransactionRecord, String> cbtrn02Processor,
                            FlatFileItemWriter<String> cbtrn02Writer) {
        return new StepBuilder("cbtrn02Step", repository)
                .<DailyTransactionRecord, String>chunk(20, transactionManager)
                .reader(cbtrn02Reader).processor(cbtrn02Processor).writer(cbtrn02Writer).build();
    }
}
