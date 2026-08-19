package com.carddemo;

import com.carddemo.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "carddemo.batch.output-dir=target/test-batch-output"
})
class BatchJobIntegrationTest {
    @Autowired
    private JobLauncherTestUtils jobs;

    @Autowired
    private Map<String, Job> jobBeans;

    @Autowired
    private AccountRepository accounts;

    @BeforeEach
    void cleanOutput() throws Exception {
        Path output = Path.of("target/test-batch-output");
        if (Files.exists(output)) {
            try (var paths = Files.list(output)) {
                paths.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                });
            }
        }
    }

    @Test
    void launchesEveryCobolBatchJob() throws Exception {
        launch("cbtrn01Job", params("dailyFile", "../app/data/ASCII/dailytran.txt"));
        launch("cbtrn02Job", params("dailyFile", "../app/data/ASCII/dailytran.txt"));
        launch("cbtrn03Job", new JobParametersBuilder()
                .addString("startDate", "2022-01-01")
                .addString("endDate", "2025-12-31")
                .addLong("run", 3L).toJobParameters());
        launch("cbact04Job", params("run", "4"));
        launch("cbstm03Job", params("run", "5"));
        launch("cbexportJob", params("run", "6"));
        launch("cbimportJob", params("run", "7"));

        Path output = Path.of("target/test-batch-output");
        assertTrue(Files.exists(output.resolve("cbtrn01-validation.txt")));
        assertTrue(Files.exists(output.resolve("cbtrn02-rejects.txt")));
        assertTrue(Files.exists(output.resolve("cbtrn03-report.txt")));
        assertTrue(Files.exists(output.resolve("STATEMNT.PS")));
        assertTrue(Files.exists(output.resolve("STATEMNT.HTML")));
        assertTrue(Files.exists(output.resolve("EXPORT.DATA")));
        assertTrue(Files.size(output.resolve("cbtrn02-rejects.txt")) > 0);
        assertTrue(accounts.findById(1L).orElseThrow().getAcctCurrBal().compareTo(
                java.math.BigDecimal.ZERO) > 0);
        assertTrue(Files.size(output.resolve("EXPORT.DATA")) % 501 == 0);
    }

    private void launch(String name, JobParameters parameters) throws Exception {
        jobs.setJob(jobBeans.get(name));
        JobExecution execution = jobs.launchJob(parameters);
        assertEquals("COMPLETED", execution.getStatus().name(), name);
    }

    private JobParameters params(String key, String value) {
        return new JobParametersBuilder().addString(key, value)
                .addLong("run", System.nanoTime()).toJobParameters();
    }
}
