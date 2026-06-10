package com.carddemo.batch;

import com.carddemo.model.Account;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

/**
 * Replaces CBACT01C.cbl — reads account file sequentially, writes to multiple output formats.
 */
@Configuration
public class AccountFileProcessingJobConfig {

    @Bean
    public JpaPagingItemReader<Account> accountReader(EntityManagerFactory emf) {
        return new JpaPagingItemReaderBuilder<Account>()
                .name("accountReader")
                .entityManagerFactory(emf)
                .queryString("SELECT a FROM Account a ORDER BY a.acctId")
                .pageSize(100)
                .build();
    }

    @Bean
    public FlatFileItemWriter<Account> accountFlatFileWriter() {
        return new FlatFileItemWriterBuilder<Account>()
                .name("accountFlatFileWriter")
                .resource(new FileSystemResource("output/accounts-out.csv"))
                .delimited()
                .delimiter(",")
                .names("acctId", "acctActiveStatus", "acctCurrBal", "acctCreditLimit",
                        "acctCashCreditLimit", "acctOpenDate", "acctExpirationDate",
                        "acctReissueDate", "acctCurrCycCredit", "acctCurrCycDebit",
                        "acctGroupId")
                .build();
    }

    @Bean
    public FlatFileItemWriter<Account> accountSummaryWriter() {
        return new FlatFileItemWriterBuilder<Account>()
                .name("accountSummaryWriter")
                .resource(new FileSystemResource("output/accounts-summary.csv"))
                .delimited()
                .delimiter(",")
                .names("acctId", "acctActiveStatus", "acctCurrBal", "acctCurrCycDebit")
                .build();
    }

    @Bean
    public CompositeItemWriter<Account> accountCompositeWriter(
            FlatFileItemWriter<Account> accountFlatFileWriter,
            FlatFileItemWriter<Account> accountSummaryWriter) {
        CompositeItemWriter<Account> writer = new CompositeItemWriter<>();
        writer.setDelegates(List.of(accountFlatFileWriter, accountSummaryWriter));
        return writer;
    }

    @Bean
    public Step accountFileProcessingStep(
            JobRepository jobRepository,
            PlatformTransactionManager txManager,
            JpaPagingItemReader<Account> accountReader,
            CompositeItemWriter<Account> accountCompositeWriter) {
        return new StepBuilder("accountFileProcessingStep", jobRepository)
                .<Account, Account>chunk(100, txManager)
                .reader(accountReader)
                .writer(accountCompositeWriter)
                .build();
    }

    @Bean
    public Job accountFileProcessingJob(
            JobRepository jobRepository,
            Step accountFileProcessingStep) {
        return new JobBuilder("accountFileProcessingJob", jobRepository)
                .start(accountFileProcessingStep)
                .build();
    }
}
