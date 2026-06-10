package com.carddemo.batch;

import com.carddemo.model.Account;
import com.carddemo.repository.AccountRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;

/**
 * Replaces CBIMPORT.cbl — reads pipe-delimited export file (record type 'A')
 * and upserts into the accounts table.
 */
@Configuration
public class AccountImportJobConfig {

    @Bean
    public FlatFileItemReader<Account> accountImportReader() {
        return new FlatFileItemReaderBuilder<Account>()
                .name("accountImportReader")
                .resource(new FileSystemResource("input/account-import.dat"))
                .delimited()
                .delimiter("|")
                .names("recType", "timestamp", "seq", "branchId", "regionCode",
                        "acctId", "acctActiveStatus", "acctCurrBal", "acctCreditLimit",
                        "acctCashCreditLimit", "acctOpenDate", "acctExpirationDate",
                        "acctReissueDate", "acctCurrCycCredit", "acctCurrCycDebit",
                        "acctAddrZip", "acctGroupId")
                .fieldSetMapper(importFieldSetMapper())
                .build();
    }

    private FieldSetMapper<Account> importFieldSetMapper() {
        return (FieldSet fs) -> Account.builder()
                .acctId(fs.readLong("acctId"))
                .acctActiveStatus(fs.readString("acctActiveStatus"))
                .acctCurrBal(new BigDecimal(fs.readString("acctCurrBal")))
                .acctCreditLimit(new BigDecimal(fs.readString("acctCreditLimit")))
                .acctCashCreditLimit(new BigDecimal(fs.readString("acctCashCreditLimit")))
                .acctOpenDate(fs.readString("acctOpenDate"))
                .acctExpirationDate(fs.readString("acctExpirationDate"))
                .acctReissueDate(fs.readString("acctReissueDate"))
                .acctCurrCycCredit(new BigDecimal(fs.readString("acctCurrCycCredit")))
                .acctCurrCycDebit(new BigDecimal(fs.readString("acctCurrCycDebit")))
                .acctAddrZip(fs.readString("acctAddrZip"))
                .acctGroupId(fs.readString("acctGroupId"))
                .build();
    }

    @Bean
    public ItemWriter<Account> accountImportWriter(AccountRepository accountRepo) {
        return items -> {
            for (Account imported : items) {
                accountRepo.findById(imported.getAcctId()).ifPresentOrElse(
                        existing -> {
                            existing.setAcctActiveStatus(imported.getAcctActiveStatus());
                            existing.setAcctCurrBal(imported.getAcctCurrBal());
                            existing.setAcctCreditLimit(imported.getAcctCreditLimit());
                            existing.setAcctCashCreditLimit(imported.getAcctCashCreditLimit());
                            existing.setAcctOpenDate(imported.getAcctOpenDate());
                            existing.setAcctExpirationDate(imported.getAcctExpirationDate());
                            existing.setAcctReissueDate(imported.getAcctReissueDate());
                            existing.setAcctCurrCycCredit(imported.getAcctCurrCycCredit());
                            existing.setAcctCurrCycDebit(imported.getAcctCurrCycDebit());
                            existing.setAcctAddrZip(imported.getAcctAddrZip());
                            existing.setAcctGroupId(imported.getAcctGroupId());
                            accountRepo.save(existing);
                        },
                        () -> {
                            imported.setVersion(0L);
                            accountRepo.save(imported);
                        });
            }
        };
    }

    @Bean
    public Step accountImportStep(
            JobRepository jobRepository,
            PlatformTransactionManager txManager,
            FlatFileItemReader<Account> accountImportReader,
            ItemWriter<Account> accountImportWriter) {
        return new StepBuilder("accountImportStep", jobRepository)
                .<Account, Account>chunk(100, txManager)
                .reader(accountImportReader)
                .writer(accountImportWriter)
                .build();
    }

    @Bean
    public Job accountImportJob(
            JobRepository jobRepository,
            Step accountImportStep) {
        return new JobBuilder("accountImportJob", jobRepository)
                .start(accountImportStep)
                .build();
    }
}
