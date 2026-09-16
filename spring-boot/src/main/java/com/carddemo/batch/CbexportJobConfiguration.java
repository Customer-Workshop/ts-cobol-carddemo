package com.carddemo.batch;

import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import com.carddemo.repository.TransactionRepository;
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
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.file.transform.PassThroughLineAggregator;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class CbexportJobConfiguration {
    @Bean
    public Job cbexportJob(JobRepository repository, Step cbexportStep) {
        return new JobBuilder("cbexportJob", repository)
                .incrementer(new RunIdIncrementer())
                // S18-B4 — a failed run reports the 999 abend code like the
                // sibling jobs (9999-ABEND-PROGRAM -> CEE3ABD).
                .listener(new Abend999JobListener())
                .start(cbexportStep).build();
    }

    @Bean
    @StepScope
    public RepositoryItemReader<Object> cbexportCustomersReader(CustomerRepository repository) {
        return repositoryReader("cbexportCustomersReader", repository, "custId");
    }

    @Bean
    @StepScope
    public RepositoryItemReader<Object> cbexportAccountsReader(AccountRepository repository) {
        return repositoryReader("cbexportAccountsReader", repository, "acctId");
    }

    @Bean
    @StepScope
    public RepositoryItemReader<Object> cbexportXrefsReader(CardXrefRepository repository) {
        return repositoryReader("cbexportXrefsReader", repository, "xrefCardNumber");
    }

    @Bean
    @StepScope
    public RepositoryItemReader<Object> cbexportTransactionsReader(TransactionRepository repository) {
        return repositoryReader("cbexportTransactionsReader", repository, "tranId");
    }

    @Bean
    @StepScope
    public RepositoryItemReader<Object> cbexportCardsReader(CardRepository repository) {
        return repositoryReader("cbexportCardsReader", repository, "cardNumber");
    }

    @Bean
    @StepScope
    public ItemStreamReader<Object> cbexportReader(
            @Qualifier("cbexportCustomersReader") RepositoryItemReader<Object> customers,
            @Qualifier("cbexportAccountsReader") RepositoryItemReader<Object> accounts,
            @Qualifier("cbexportXrefsReader") RepositoryItemReader<Object> xrefs,
            @Qualifier("cbexportTransactionsReader") RepositoryItemReader<Object> transactions,
            @Qualifier("cbexportCardsReader") RepositoryItemReader<Object> cards) {
        return new RepositorySequenceReader(List.of(customers, accounts, xrefs, transactions, cards));
    }

    @Bean
    @StepScope
    public ImportExportStats cbexportStats() {
        return new ImportExportStats();
    }

    @Bean
    @StepScope
    public ItemProcessor<Object, String> cbexportProcessor(BatchJobService service,
            @Qualifier("cbexportStats") ImportExportStats stats) {
        return new ItemProcessor<>() {
            private long sequence;

            @Override
            public String process(Object item) {
                String record = service.exportRecord(item, ++sequence);
                stats.countType(record.substring(0, 1));
                stats.countRecord();
                return record;
            }
        };
    }

    @Bean
    @StepScope
    public StepExecutionListener cbexportStatsListener(BatchJobService service,
            @Qualifier("cbexportStats") ImportExportStats stats) {
        return new StepExecutionListener() {
            @Override
            public void beforeStep(StepExecution stepExecution) {
            }

            @Override
            public ExitStatus afterStep(StepExecution stepExecution) {
                if (stepExecution.getStatus() == BatchStatus.COMPLETED) {
                    service.logExportStats(stats);
                }
                return stepExecution.getExitStatus();
            }
        };
    }

    @Bean
    @StepScope
    public FlatFileItemWriter<String> cbexportWriter(BatchJobService service) {
        return new FlatFileItemWriterBuilder<String>()
                .name("cbexportWriter")
                .resource(new FileSystemResource(service.output("EXPORT.DATA")))
                .lineAggregator(new PassThroughLineAggregator<>())
                .shouldDeleteIfExists(true)
                .build();
    }

    @Bean
    public Step cbexportStep(JobRepository repository, PlatformTransactionManager transactionManager,
                             ItemStreamReader<Object> cbexportReader,
                             ItemProcessor<Object, String> cbexportProcessor,
                             FlatFileItemWriter<String> cbexportWriter,
                             @Qualifier("cbexportStatsListener") StepExecutionListener cbexportStatsListener) {
        return new StepBuilder("cbexportStep", repository)
                .<Object, String>chunk(50, transactionManager)
                .reader(cbexportReader).processor(cbexportProcessor).writer(cbexportWriter)
                .listener(cbexportStatsListener).build();
    }

    private static RepositoryItemReader<Object> repositoryReader(
            String name, PagingAndSortingRepository<?, ?> repository, String sort) {
        Map<String, Sort.Direction> sorts = new LinkedHashMap<>();
        sorts.put(sort, Sort.Direction.ASC);
        return new RepositoryItemReaderBuilder<Object>()
                .name(name).repository(repository).methodName("findAll")
                .pageSize(50).sorts(sorts).build();
    }
}
