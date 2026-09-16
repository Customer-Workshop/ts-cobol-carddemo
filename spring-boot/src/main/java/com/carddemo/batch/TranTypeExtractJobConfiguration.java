package com.carddemo.batch;

import com.carddemo.repository.TransactionCategoryRepository;
import com.carddemo.repository.TransactionTypeRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * TRANEXTR job (app/app-transaction-type-db2/jcl/TRANEXTR.jcl): the DSNTIAUL
 * extracts of TRANSACTION_TYPE and TRAN_CATEGORY_TYPE as flat 60-char files
 * under the batch output directory.
 */
@Configuration
public class TranTypeExtractJobConfiguration {
    @Bean
    public Job tranTypeExtractJob(JobRepository repository, Step tranTypeExtractStep) {
        return new JobBuilder("tranTypeExtractJob", repository)
                .incrementer(new RunIdIncrementer()).start(tranTypeExtractStep).build();
    }

    @Bean
    public TranTypeExtractTasklet tranTypeExtractTasklet(
            TransactionTypeRepository types, TransactionCategoryRepository categories,
            BatchJobService service) {
        return new TranTypeExtractTasklet(types, categories, service);
    }

    @Bean
    public Step tranTypeExtractStep(JobRepository repository,
                                    PlatformTransactionManager transactionManager,
                                    TranTypeExtractTasklet tranTypeExtractTasklet) {
        return new StepBuilder("tranTypeExtractStep", repository)
                .tasklet(tranTypeExtractTasklet, transactionManager).build();
    }
}
