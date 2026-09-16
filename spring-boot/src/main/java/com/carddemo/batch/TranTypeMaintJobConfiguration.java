package com.carddemo.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Path;

/**
 * MNTTRDB2 job (app/app-transaction-type-db2/jcl/MNTTRDB2.jcl): the IKJEFT01
 * COBTUPDT apply for INPFILE. INPFILE arrives as the {@code inputFile} job
 * parameter, defaulting to the seed ASCII file alongside the other datasets.
 */
@Configuration
public class TranTypeMaintJobConfiguration {
    @Bean
    public Job tranTypeMaintJob(JobRepository repository, Step tranTypeMaintStep) {
        return new JobBuilder("tranTypeMaintJob", repository)
                .incrementer(new RunIdIncrementer()).start(tranTypeMaintStep).build();
    }

    @Bean
    @StepScope
    public TranTypeMaintTasklet tranTypeMaintTasklet(
            @Value("#{jobParameters['inputFile']}") String inputFile,
            @Value("${carddemo.seed.data-dir:../app/data}") String dataDirectory,
            BatchJobService service) {
        Path input = inputFile == null || inputFile.isBlank()
                ? Path.of(dataDirectory, "ASCII", "trantype-update.txt")
                : Path.of(inputFile);
        return new TranTypeMaintTasklet(input, service);
    }

    @Bean
    public Step tranTypeMaintStep(JobRepository repository,
                                  PlatformTransactionManager transactionManager,
                                  TranTypeMaintTasklet tranTypeMaintTasklet) {
        return new StepBuilder("tranTypeMaintStep", repository)
                .tasklet(tranTypeMaintTasklet, transactionManager).build();
    }
}
