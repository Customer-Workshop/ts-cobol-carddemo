package com.carddemo.batch;

import com.carddemo.repository.PendingAuthDetailRepository;
import com.carddemo.repository.PendingAuthSummaryRepository;
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
 * S-20 wave-2 jobs: CBPAUP0J purge (pendingAuthPurgeJob), LOADPADB
 * (pendingAuthImportJob) and UNLDPADB/UNLDGSAM (pendingAuthExportJob). SYSIN
 * and DD wiring arrive as job parameters (S20-B7/B8): purge takes
 * expiryDays/chkpFreq/chkpDispFreq/debug, the file jobs take rootFile and
 * childFile defaulting to the PAUTDB datasets under the batch output dir —
 * the JCL loads and unloads the same DSNs.
 */
@Configuration
public class PendingAuthJobsConfiguration {

    @Bean
    public Job pendingAuthPurgeJob(JobRepository repository, Step pendingAuthPurgeStep) {
        return new JobBuilder("pendingAuthPurgeJob", repository)
                .incrementer(new RunIdIncrementer()).start(pendingAuthPurgeStep).build();
    }

    @Bean
    @StepScope
    public PendingAuthPurgeTasklet pendingAuthPurgeTasklet(
            @Value("#{jobParameters['expiryDays']}") String expiryDays,
            @Value("#{jobParameters['chkpFreq']}") String chkpFreq,
            @Value("#{jobParameters['chkpDispFreq']}") String chkpDispFreq,
            @Value("#{jobParameters['debug']}") String debug,
            PendingAuthPurgeService service) {
        return new PendingAuthPurgeTasklet(expiryDays, chkpFreq, chkpDispFreq, debug, service);
    }

    @Bean
    public Step pendingAuthPurgeStep(JobRepository repository,
                                     PlatformTransactionManager transactionManager,
                                     PendingAuthPurgeTasklet pendingAuthPurgeTasklet) {
        return new StepBuilder("pendingAuthPurgeStep", repository)
                .tasklet(pendingAuthPurgeTasklet, transactionManager).build();
    }

    @Bean
    public Job pendingAuthImportJob(JobRepository repository, Step pendingAuthImportStep) {
        return new JobBuilder("pendingAuthImportJob", repository)
                .incrementer(new RunIdIncrementer()).start(pendingAuthImportStep).build();
    }

    @Bean
    @StepScope
    public PendingAuthImportTasklet pendingAuthImportTasklet(
            @Value("#{jobParameters['rootFile']}") String rootFile,
            @Value("#{jobParameters['childFile']}") String childFile,
            PendingAuthSummaryRepository summaries,
            PendingAuthDetailRepository details,
            BatchJobService service) {
        return new PendingAuthImportTasklet(fileOrDefault(rootFile, service, "PAUTDB.ROOT.FILEO"),
                fileOrDefault(childFile, service, "PAUTDB.CHILD.FILEO"), summaries, details);
    }

    @Bean
    public Step pendingAuthImportStep(JobRepository repository,
                                      PlatformTransactionManager transactionManager,
                                      PendingAuthImportTasklet pendingAuthImportTasklet) {
        return new StepBuilder("pendingAuthImportStep", repository)
                .tasklet(pendingAuthImportTasklet, transactionManager).build();
    }

    @Bean
    public Job pendingAuthExportJob(JobRepository repository, Step pendingAuthExportStep) {
        return new JobBuilder("pendingAuthExportJob", repository)
                .incrementer(new RunIdIncrementer()).start(pendingAuthExportStep).build();
    }

    @Bean
    @StepScope
    public PendingAuthExportTasklet pendingAuthExportTasklet(
            @Value("#{jobParameters['rootFile']}") String rootFile,
            @Value("#{jobParameters['childFile']}") String childFile,
            PendingAuthSummaryRepository summaries,
            PendingAuthDetailRepository details,
            BatchJobService service) {
        return new PendingAuthExportTasklet(fileOrDefault(rootFile, service, "PAUTDB.ROOT.FILEO"),
                fileOrDefault(childFile, service, "PAUTDB.CHILD.FILEO"), summaries, details);
    }

    @Bean
    public Step pendingAuthExportStep(JobRepository repository,
                                      PlatformTransactionManager transactionManager,
                                      PendingAuthExportTasklet pendingAuthExportTasklet) {
        return new StepBuilder("pendingAuthExportStep", repository)
                .tasklet(pendingAuthExportTasklet, transactionManager).build();
    }

    private static Path fileOrDefault(String param, BatchJobService service, String dataset) {
        return param == null || param.isBlank() ? service.output(dataset) : Path.of(param);
    }
}
