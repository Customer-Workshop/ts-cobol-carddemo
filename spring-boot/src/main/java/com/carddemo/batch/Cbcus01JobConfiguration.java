package com.carddemo.batch;

import com.carddemo.model.Customer;
import com.carddemo.repository.CustomerRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * READCUST (CBCUS01C): sequential verify pass over the customers table.
 * Parity quirk preserved: CUSTOMER-RECORD is displayed twice per row — once
 * inside the read paragraph and once in the main loop (CBCUS01C.cbl:78,:96).
 */
@Configuration
public class Cbcus01JobConfiguration {

    @Bean
    public Job readcustJob(JobRepository repository, Step readcustStep) {
        return new JobBuilder("readcustJob", repository)
                .incrementer(new RunIdIncrementer())
                .listener(new Abend999JobListener())
                .start(readcustStep).build();
    }

    @Bean
    @StepScope
    public RepositoryItemReader<Customer> readcustReader(CustomerRepository repository) {
        Map<String, Sort.Direction> sorts = new LinkedHashMap<>();
        sorts.put("custId", Sort.Direction.ASC);
        return new RepositoryItemReaderBuilder<Customer>()
                .name("readcustReader").repository(repository)
                .methodName("findAll").pageSize(50)
                .sorts(sorts).build();
    }

    @Bean
    public ItemProcessor<Customer, Customer> readcustProcessor() {
        return customer -> {
            ReadVerifySupport.SYSOUT.info("{}", ReadVerifySupport.customerImage(customer));
            return customer;
        };
    }

    @Bean
    public ItemWriter<Customer> readcustWriter() {
        return chunk -> chunk.getItems()
                .forEach(customer -> ReadVerifySupport.SYSOUT.info("{}", ReadVerifySupport.customerImage(customer)));
    }

    @Bean
    public Step readcustStep(JobRepository repository, PlatformTransactionManager transactionManager,
                             RepositoryItemReader<Customer> readcustReader,
                             ItemProcessor<Customer, Customer> readcustProcessor,
                             ItemWriter<Customer> readcustWriter) {
        return new StepBuilder("readcustStep", repository)
                .<Customer, Customer>chunk(1, transactionManager)
                .reader(readcustReader).processor(readcustProcessor).writer(readcustWriter)
                .listener(new ReadVerifyStepListener("CBCUS01C")).build();
    }
}
