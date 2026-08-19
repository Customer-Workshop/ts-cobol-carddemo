package com.carddemo.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchJobConfiguration {
    @Bean
    public Job cbtrn01Job(JobRepository repository, Step cbtrn01Step) {
        return new JobBuilder("cbtrn01Job", repository).incrementer(new RunIdIncrementer())
                .start(cbtrn01Step).build();
    }

    @Bean
    public Step cbtrn01Step(JobRepository repository, PlatformTransactionManager tx,
                            BatchJobService service) {
        return step("cbtrn01Step", repository, tx, service.validateDailyTransactions());
    }

    @Bean
    public Job cbtrn02Job(JobRepository repository, Step cbtrn02Step) {
        return new JobBuilder("cbtrn02Job", repository).incrementer(new RunIdIncrementer())
                .start(cbtrn02Step).build();
    }

    @Bean
    public Step cbtrn02Step(JobRepository repository, PlatformTransactionManager tx,
                            BatchJobService service) {
        return step("cbtrn02Step", repository, tx, service.postDailyTransactions());
    }

    @Bean
    public Job cbtrn03Job(JobRepository repository, Step cbtrn03Step) {
        return new JobBuilder("cbtrn03Job", repository).incrementer(new RunIdIncrementer())
                .start(cbtrn03Step).build();
    }

    @Bean
    public Step cbtrn03Step(JobRepository repository, PlatformTransactionManager tx,
                            BatchJobService service) {
        return step("cbtrn03Step", repository, tx, service.transactionReport());
    }

    @Bean
    public Job cbact04Job(JobRepository repository, Step cbact04Step) {
        return new JobBuilder("cbact04Job", repository).incrementer(new RunIdIncrementer())
                .start(cbact04Step).build();
    }

    @Bean
    public Step cbact04Step(JobRepository repository, PlatformTransactionManager tx,
                            BatchJobService service) {
        return step("cbact04Step", repository, tx, service.interestCalculation());
    }

    @Bean
    public Job cbstm03Job(JobRepository repository, Step cbstm03Step) {
        return new JobBuilder("cbstm03Job", repository).incrementer(new RunIdIncrementer())
                .start(cbstm03Step).build();
    }

    @Bean
    public Step cbstm03Step(JobRepository repository, PlatformTransactionManager tx,
                            BatchJobService service) {
        return step("cbstm03Step", repository, tx, service.statements());
    }

    @Bean
    public Job cbexportJob(JobRepository repository, Step cbexportStep) {
        return new JobBuilder("cbexportJob", repository).incrementer(new RunIdIncrementer())
                .start(cbexportStep).build();
    }

    @Bean
    public Step cbexportStep(JobRepository repository, PlatformTransactionManager tx,
                             BatchJobService service) {
        return step("cbexportStep", repository, tx, service.exportData());
    }

    @Bean
    public Job cbimportJob(JobRepository repository, Step cbimportStep) {
        return new JobBuilder("cbimportJob", repository).incrementer(new RunIdIncrementer())
                .start(cbimportStep).build();
    }

    @Bean
    public Step cbimportStep(JobRepository repository, PlatformTransactionManager tx,
                             BatchJobService service) {
        return step("cbimportStep", repository, tx, service.importData());
    }

    private Step step(String name, JobRepository repository,
                      PlatformTransactionManager tx, Tasklet tasklet) {
        return new StepBuilder(name, repository).tasklet(tasklet, tx).build();
    }
}
