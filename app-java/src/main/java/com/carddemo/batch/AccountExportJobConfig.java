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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Replaces CBEXPORT.cbl — exports accounts (record type 'A') to flat file
 * with branch ID, region code, timestamp, and sequence number.
 */
@Configuration
public class AccountExportJobConfig {

    @Bean
    public JpaPagingItemReader<Account> exportAccountReader(EntityManagerFactory emf) {
        return new JpaPagingItemReaderBuilder<Account>()
                .name("exportAccountReader")
                .entityManagerFactory(emf)
                .queryString("SELECT a FROM Account a ORDER BY a.acctId")
                .pageSize(100)
                .build();
    }

    @Bean
    public FlatFileItemWriter<Account> accountExportWriter() {
        AtomicLong seq = new AtomicLong(0);
        return new FlatFileItemWriterBuilder<Account>()
                .name("accountExportWriter")
                .resource(new FileSystemResource("output/account-export.dat"))
                .lineAggregator(account -> String.join("|",
                        "A",
                        LocalDateTime.now().format(
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SS")),
                        String.valueOf(seq.incrementAndGet()),
                        "0001",   // branch ID
                        "US-E",   // region code
                        String.valueOf(account.getAcctId()),
                        account.getAcctActiveStatus(),
                        account.getAcctCurrBal().toPlainString(),
                        account.getAcctCreditLimit().toPlainString(),
                        account.getAcctCashCreditLimit().toPlainString(),
                        account.getAcctOpenDate(),
                        account.getAcctExpirationDate(),
                        account.getAcctReissueDate(),
                        account.getAcctCurrCycCredit().toPlainString(),
                        account.getAcctCurrCycDebit().toPlainString(),
                        account.getAcctAddrZip(),
                        account.getAcctGroupId()))
                .build();
    }

    @Bean
    public Step accountExportStep(
            JobRepository jobRepository,
            PlatformTransactionManager txManager,
            JpaPagingItemReader<Account> exportAccountReader,
            FlatFileItemWriter<Account> accountExportWriter) {
        return new StepBuilder("accountExportStep", jobRepository)
                .<Account, Account>chunk(100, txManager)
                .reader(exportAccountReader)
                .writer(accountExportWriter)
                .build();
    }

    @Bean
    public Job accountExportJob(
            JobRepository jobRepository,
            Step accountExportStep) {
        return new JobBuilder("accountExportJob", jobRepository)
                .start(accountExportStep)
                .build();
    }
}
