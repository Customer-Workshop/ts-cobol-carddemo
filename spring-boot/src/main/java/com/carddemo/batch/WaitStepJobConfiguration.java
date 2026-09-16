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

/**
 * WAITSTEP job (app/jcl/WAITSTEP.jcl): the COBSWAIT → MVSWAIT wait between the
 * last write step and file reopen in the daily scheduler chains. Kept as a
 * standalone launchable job so runbook order stays visible; S-15/S-16 wave
 * children inject the same tasklet into their own job flows.
 */
@Configuration
public class WaitStepJobConfiguration {
    @Bean
    public Job waitStepJob(JobRepository repository, Step waitStep) {
        return new JobBuilder("waitStepJob", repository)
                .incrementer(new RunIdIncrementer()).start(waitStep).build();
    }

    @Bean
    @StepScope
    public WaitStepTasklet waitStepTasklet(
            @Value("#{jobParameters['waitCentiseconds']}") String waitCentiseconds) {
        long centiseconds = waitCentiseconds == null
                ? WaitStepTasklet.DEFAULT_WAIT_CENTISECONDS
                : WaitStepTasklet.centiseconds(waitCentiseconds);
        return new WaitStepTasklet(centiseconds);
    }

    @Bean
    public Step waitStep(JobRepository repository, PlatformTransactionManager transactionManager,
                         WaitStepTasklet waitStepTasklet) {
        return new StepBuilder("waitStep", repository)
                .tasklet(waitStepTasklet, transactionManager).build();
    }
}
