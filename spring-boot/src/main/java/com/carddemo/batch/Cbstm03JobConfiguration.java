package com.carddemo.batch;

import com.carddemo.model.CardXref;
import com.carddemo.repository.CardXrefRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Map;

@Configuration
public class Cbstm03JobConfiguration {
    @Bean
    public Job cbstm03Job(JobRepository repository, Step cbstm03Step) {
        return new JobBuilder("cbstm03Job", repository)
                .incrementer(new RunIdIncrementer()).start(cbstm03Step).build();
    }

    @Bean
    @StepScope
    public RepositoryItemReader<CardXref> cbstm03Reader(CardXrefRepository xrefs) {
        return new RepositoryItemReaderBuilder<CardXref>()
                .name("cbstm03Reader").repository(xrefs).methodName("findAll")
                .pageSize(20).sorts(Map.of("xrefCardNumber", Sort.Direction.ASC)).build();
    }

    @Bean
    public ItemProcessor<CardXref, BatchJobService.CardStatement> cbstm03Processor(
            BatchJobService service) {
        return service::statementFor;
    }

    @Bean
    @StepScope
    public FlatFileItemWriter<BatchJobService.CardStatement> cbstm03PlainWriter(
            BatchJobService service) {
        return new FlatFileItemWriterBuilder<BatchJobService.CardStatement>()
                .name("cbstm03PlainWriter")
                .resource(new FileSystemResource(service.output("STATEMNT.PS")))
                .lineAggregator(service::statementPlain)
                .shouldDeleteIfExists(true)
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemWriter<BatchJobService.CardStatement> cbstm03HtmlWriter(
            BatchJobService service) {
        return new FlatFileItemWriterBuilder<BatchJobService.CardStatement>()
                .name("cbstm03HtmlWriter")
                .resource(new FileSystemResource(service.output("STATEMNT.HTML")))
                .lineAggregator(service::statementHtml)
                .headerCallback(writer -> writer.write(""))
                .footerCallback(writer -> writer.write(""))
                .shouldDeleteIfExists(true)
                .build();
    }

    @Bean
    public DualStatementWriter cbstm03Writer(
            FlatFileItemWriter<BatchJobService.CardStatement> cbstm03PlainWriter,
            FlatFileItemWriter<BatchJobService.CardStatement> cbstm03HtmlWriter) {
        return new DualStatementWriter(cbstm03PlainWriter, cbstm03HtmlWriter);
    }

    @Bean
    public Step cbstm03Step(JobRepository repository, PlatformTransactionManager transactionManager,
                            RepositoryItemReader<CardXref> cbstm03Reader,
                            ItemProcessor<CardXref, BatchJobService.CardStatement> cbstm03Processor,
                            DualStatementWriter cbstm03Writer) {
        return new StepBuilder("cbstm03Step", repository)
                .<CardXref, BatchJobService.CardStatement>chunk(1, transactionManager)
                .reader(cbstm03Reader).processor(cbstm03Processor).writer(cbstm03Writer).build();
    }
}
