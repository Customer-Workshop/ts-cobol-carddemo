package com.carddemo.batch;

import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import com.carddemo.repository.TransactionRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
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
import java.util.Map;

@Configuration
public class CbexportJobConfiguration {
    @Bean
    public Job cbexportJob(JobRepository repository, Step cbexportStep) {
        return new JobBuilder("cbexportJob", repository)
                .incrementer(new RunIdIncrementer()).start(cbexportStep).build();
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
    public ItemProcessor<Object, String> cbexportProcessor(BatchJobService service) {
        return new ItemProcessor<>() {
            private long sequence;

            @Override
            public String process(Object item) {
                return service.exportRecord(item, ++sequence);
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
                             FlatFileItemWriter<String> cbexportWriter) {
        return new StepBuilder("cbexportStep", repository)
                .<Object, String>chunk(50, transactionManager)
                .reader(cbexportReader).processor(cbexportProcessor).writer(cbexportWriter).build();
    }

    private static RepositoryItemReader<Object> repositoryReader(
            String name, PagingAndSortingRepository<?, ?> repository, String sort) {
        return new RepositoryItemReaderBuilder<Object>()
                .name(name).repository(repository).methodName("findAll")
                .pageSize(50).sorts(Map.of(sort, Sort.Direction.ASC)).build();
    }
}
