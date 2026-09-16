package com.carddemo.batch;

import com.carddemo.model.Account;
import com.carddemo.repository.AccountRepository;
import com.carddemo.util.DateEditService;
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
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.file.transform.PassThroughLineAggregator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * READACCT (CBACT01C): sequential verify pass over the accounts table with
 * three flat-file outputs — ACCTDATA.PSCOMP (FB 107), ACCTDATA.ARRYPS
 * (FB 110), ACCTDATA.VBPS (VB, 12- and 39-byte records) — plus the SYSOUT
 * record dump on the carddemo.sysout log channel.
 */
@Configuration
public class Cbact01JobConfiguration {

    private static final String BYTE_ENCODING = "ISO-8859-1";

    @Bean
    public Job readacctJob(JobRepository repository, Step readacctStep) {
        return new JobBuilder("readacctJob", repository)
                .incrementer(new RunIdIncrementer())
                .listener(new Abend999JobListener())
                .start(readacctStep).build();
    }

    @Bean
    @StepScope
    public RepositoryItemReader<Account> readacctReader(AccountRepository repository) {
        Map<String, Sort.Direction> sorts = new LinkedHashMap<>();
        sorts.put("acctId", Sort.Direction.ASC);
        return new RepositoryItemReaderBuilder<Account>()
                .name("readacctReader").repository(repository)
                .methodName("findAll").pageSize(50)
                .sorts(sorts).build();
    }

    @Bean
    @StepScope
    public ItemProcessor<Account, ReadacctOutputs> readacctProcessor(DateEditService dates) {
        return new ItemProcessor<>() {
            private final ReadVerifySupport.PscompRecordBuilder pscomp =
                    new ReadVerifySupport.PscompRecordBuilder();

            @Override
            public ReadacctOutputs process(Account account) {
                ReadVerifySupport.logLabelledAccount(account);
                String vb1 = ReadVerifySupport.vb1Record(account);
                String vb2 = ReadVerifySupport.vb2Record(account);
                ReadVerifySupport.SYSOUT.info("VBRC-REC1:{}", vb1);
                ReadVerifySupport.SYSOUT.info("VBRC-REC2:{}", vb2);
                ReadVerifySupport.SYSOUT.info("{}", ReadVerifySupport.accountImage(account));
                return new ReadacctOutputs(
                        pscomp.build(account, dates),
                        ReadVerifySupport.arrypsRecord(account),
                        vb1, vb2);
            }
        };
    }

    @Bean
    @StepScope
    public ReadacctWriter readacctWriter(BatchJobService service) {
        return new ReadacctWriter(
                flatWriter("readacctPscompWriter", service.output("ACCTDATA.PSCOMP")),
                flatWriter("readacctArrypsWriter", service.output("ACCTDATA.ARRYPS")),
                flatWriter("readacctVbpsWriter", service.output("ACCTDATA.VBPS")));
    }

    @Bean
    public Step readacctStep(JobRepository repository, PlatformTransactionManager transactionManager,
                             RepositoryItemReader<Account> readacctReader,
                             ItemProcessor<Account, ReadacctOutputs> readacctProcessor,
                             ItemWriter<ReadacctOutputs> readacctWriter) {
        return new StepBuilder("readacctStep", repository)
                .<Account, ReadacctOutputs>chunk(1, transactionManager)
                .reader(readacctReader).processor(readacctProcessor).writer(readacctWriter)
                .listener(new ReadVerifyStepListener("CBACT01C")).build();
    }

    private static FlatFileItemWriter<String> flatWriter(String name, java.nio.file.Path file) {
        return new FlatFileItemWriterBuilder<String>()
                .name(name)
                .resource(new FileSystemResource(file))
                .encoding(BYTE_ENCODING)
                .lineAggregator(new PassThroughLineAggregator<>())
                .shouldDeleteIfExists(true)
                .build();
    }
}
