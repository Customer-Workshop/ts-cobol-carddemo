package com.carddemo.batch;

import com.carddemo.model.TransactionCategoryBalance;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.TransactionCategoryBalanceRepository;
import com.carddemo.service.TransactionIdGenerator;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * INTCALC (CBACT04C): monthly interest sweep over transaction-category
 * balances — DISCGRP rate resolution with 'DEFAULT' fallback, interest
 * transactions inserted directly (COMBTRAN eliminated), account balances and
 * cycle totals updated at each account break. Keyed-read misses abend the job
 * with exit code 999, matching the legacy CEE3ABD contract.
 */
@Configuration
public class Cbact04JobConfiguration {
    @Bean
    public Job cbact04Job(JobRepository repository, Step cbact04Step) {
        return new JobBuilder("cbact04Job", repository)
                .incrementer(new RunIdIncrementer())
                .listener(new Abend999JobListener())
                .validator(this::validateParmDate)
                .start(cbact04Step).build();
    }

    // S15-B4: INTCALC supplies PARM='yyyymmddhh' (INTCALC.jcl:3) — a JES-side
    // PARM error surfaces before the job runs, so the equivalent here is a
    // parameter rejection rather than a runtime failure.
    private void validateParmDate(JobParameters parameters) throws JobParametersInvalidException {
        String parmDate = parameters == null ? null : parameters.getString("parmDate");
        if (parmDate == null || !parmDate.matches("\\d{10}")) {
            throw new JobParametersInvalidException(
                    "cbact04Job requires a parmDate parameter in yyyymmddhh format (INTCALC PARM)");
        }
        int month = Integer.parseInt(parmDate.substring(4, 6));
        int day = Integer.parseInt(parmDate.substring(6, 8));
        int hour = Integer.parseInt(parmDate.substring(8, 10));
        if (month < 1 || month > 12 || day < 1 || day > 31 || hour > 23) {
            throw new JobParametersInvalidException(
                    "cbact04Job parmDate must be a plausible yyyymmddhh date-hour: " + parmDate);
        }
    }

    @Bean
    @StepScope
    public RepositoryItemReader<TransactionCategoryBalance> cbact04BalanceReader(
            TransactionCategoryBalanceRepository balances) {
        Map<String, Sort.Direction> sorts = new LinkedHashMap<>();
        sorts.put("id.acctId", Sort.Direction.ASC);
        sorts.put("id.typeCode", Sort.Direction.ASC);
        sorts.put("id.categoryCode", Sort.Direction.ASC);
        return new RepositoryItemReaderBuilder<TransactionCategoryBalance>()
                .name("cbact04BalanceReader").repository(balances)
                .methodName("findAll").pageSize(20)
                .sorts(sorts)
                .build();
    }

    @Bean
    @StepScope
    public ItemStreamReader<BatchJobService.InterestWork> cbact04Reader(
            RepositoryItemReader<TransactionCategoryBalance> cbact04BalanceReader,
            AccountRepository accounts, BatchJobService service,
            TransactionIdGenerator ids,
            @Value("#{jobParameters['parmDate']}") String parmDate) {
        return new AccountInterestReader(cbact04BalanceReader, accounts, service,
                parmDate, ids);
    }

    @Bean
    public ItemProcessor<BatchJobService.InterestWork, BatchJobService.InterestWork>
    cbact04Processor() {
        return value -> value;
    }

    @Bean
    public ItemWriter<BatchJobService.InterestWork> cbact04Writer(BatchJobService service) {
        return chunk -> chunk.getItems().forEach(service::writeInterest);
    }

    @Bean
    public Step cbact04Step(JobRepository repository, PlatformTransactionManager transactionManager,
                            ItemStreamReader<BatchJobService.InterestWork> cbact04Reader,
                            @Qualifier("cbact04Processor")
                            ItemProcessor<BatchJobService.InterestWork, BatchJobService.InterestWork> processor,
                            ItemWriter<BatchJobService.InterestWork> cbact04Writer) {
        return new StepBuilder("cbact04Step", repository)
                .<BatchJobService.InterestWork, BatchJobService.InterestWork>chunk(1, transactionManager)
                .reader(cbact04Reader).processor(processor).writer(cbact04Writer).build();
    }
}
