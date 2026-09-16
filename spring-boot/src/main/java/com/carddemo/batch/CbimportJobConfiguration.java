package com.carddemo.batch;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
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
import org.springframework.beans.factory.annotation.Qualifier;
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
                .incrementer(new RunIdIncrementer())
                // S18-B4 — a failed run reports the 999 abend code like the
                // sibling jobs (9999-ABEND-PROGRAM -> CEE3ABD).
                .listener(new Abend999JobListener())
                .start(cbimportStep).build();
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
    public ImportExportStats cbimportStats() {
        return new ImportExportStats();
    }

    @Bean
    @StepScope
    public ItemProcessor<String, String> cbimportProcessor(BatchJobService service,
            @Qualifier("cbimportStats") ImportExportStats stats) {
        return new ItemProcessor<>() {
            private long record;

            @Override
            public String process(String item) {
                stats.countRecord();
                BatchJobService.ImportResult result = service.importRecord(item, ++record);
                if (result.error() == null) {
                    stats.countType(result.type());
                    return null;
                }
                stats.countError(result.unknownType());
                return service.importErrorRecord(item, result.recordNumber(), result.error());
            }
        };
    }

    @Bean
    @StepScope
    public StepExecutionListener cbimportStatsListener(BatchJobService service,
            @Qualifier("cbimportStats") ImportExportStats stats) {
        return new StepExecutionListener() {
            @Override
            public void beforeStep(StepExecution stepExecution) {
            }

            @Override
            public ExitStatus afterStep(StepExecution stepExecution) {
                if (stepExecution.getStatus() == BatchStatus.COMPLETED) {
                    service.logImportValidation();
                    service.logImportStats(stats);
                }
                return stepExecution.getExitStatus();
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
                             FlatFileItemWriter<String> cbimportWriter,
                             @Qualifier("cbimportStatsListener") StepExecutionListener cbimportStatsListener) {
        return new StepBuilder("cbimportStep", repository)
                .<String, String>chunk(50, transactionManager)
                .reader(cbimportReader).processor(cbimportProcessor).writer(cbimportWriter)
                .listener(cbimportStatsListener).build();
    }
}
