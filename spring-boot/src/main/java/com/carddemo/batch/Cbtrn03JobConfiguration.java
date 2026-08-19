package com.carddemo.batch;

import com.carddemo.model.Transaction;
import com.carddemo.repository.TransactionRepository;
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
import org.springframework.batch.item.file.transform.PassThroughLineAggregator;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.Map;

@Configuration
public class Cbtrn03JobConfiguration {
    @Bean
    public Job cbtrn03Job(JobRepository repository, Step cbtrn03Step) {
        return new JobBuilder("cbtrn03Job", repository)
                .incrementer(new RunIdIncrementer()).start(cbtrn03Step).build();
    }

    @Bean
    @StepScope
    public RepositoryItemReader<Transaction> cbtrn03Reader(
            TransactionRepository transactions,
            @Value("#{jobParameters['startDate']}") String start,
            @Value("#{jobParameters['endDate']}") String end) {
        return new RepositoryItemReaderBuilder<Transaction>()
                .name("cbtrn03Reader").repository(transactions)
                .methodName("findByTranProcessTimestampBetween")
                .arguments(LocalDateTime.parse(start + "T00:00:00"),
                        LocalDateTime.parse(end + "T23:59:59.999999999"))
                .pageSize(20)
                .sorts(Map.of("tranCardNumber", Sort.Direction.ASC, "tranId", Sort.Direction.ASC))
                .build();
    }

    @Bean
    public ItemProcessor<Transaction, BatchJobService.ReportLine> cbtrn03Processor(
            BatchJobService service) {
        return service::reportLine;
    }

    @Bean
    @StepScope
    public FlatFileItemWriter<BatchJobService.ReportLine> cbtrn03Writer(
            BatchJobService service) {
        ReportLineAggregator aggregator = new ReportLineAggregator();
        return new FlatFileItemWriterBuilder<BatchJobService.ReportLine>()
                .name("cbtrn03Writer")
                .resource(new FileSystemResource(service.output("cbtrn03-report.txt")))
                .lineAggregator(aggregator)
                .headerCallback(writer -> writer.write(
                        "DALYREPT                             Daily Transaction Report"))
                .footerCallback(writer -> writer.write(aggregator.footer()))
                .shouldDeleteIfExists(true)
                .build();
    }

    @Bean
    public Step cbtrn03Step(JobRepository repository, PlatformTransactionManager transactionManager,
                            RepositoryItemReader<Transaction> cbtrn03Reader,
                            ItemProcessor<Transaction, BatchJobService.ReportLine> cbtrn03Processor,
                            FlatFileItemWriter<BatchJobService.ReportLine> cbtrn03Writer) {
        return new StepBuilder("cbtrn03Step", repository)
                .<Transaction, BatchJobService.ReportLine>chunk(20, transactionManager)
                .reader(cbtrn03Reader).processor(cbtrn03Processor).writer(cbtrn03Writer).build();
    }
}
