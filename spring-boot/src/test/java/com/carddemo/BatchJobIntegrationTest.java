package com.carddemo;

import com.carddemo.model.Account;
import com.carddemo.model.DisclosureGroup;
import com.carddemo.model.TransactionCategoryBalance;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.DisclosureGroupRepository;
import com.carddemo.repository.TransactionCategoryBalanceRepository;
import com.carddemo.repository.TransactionRepository;
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
import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private TransactionCategoryBalanceRepository balances;

    @Autowired
    private CardXrefRepository xrefs;

    @Autowired
    private DisclosureGroupRepository disclosures;

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
        Files.createDirectories(output);
        String daily = Files.readString(Path.of("src/test/resources/seed/ASCII/dailytran.txt"));
        StringBuilder validDaily = new StringBuilder(daily);
        while (validDaily.length() < 350) validDaily.append(' ');
        validDaily.replace(262, 278, "1111222233334444");
        Files.writeString(output.resolve("dailytran.txt"), validDaily);
    }

    @Test
    void launchesEveryCobolBatchJob() throws Exception {
        String dailyFile = Path.of("target/test-batch-output/dailytran.txt").toString();
        launch("cbtrn01Job", params("dailyFile", dailyFile));
        launch("cbtrn02Job", params("dailyFile", dailyFile));
        launch("cbtrn03Job", new JobParametersBuilder()
                .addString("startDate", "2022-01-01")
                .addString("endDate", "2030-12-31")
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
        assertTrue(Files.size(output.resolve("cbtrn02-rejects.txt")) >= 0);
        assertTrue(transactions.findById("0000000000000001").orElseThrow()
                .getTranProcessTimestamp() != null);
        String report = Files.readString(output.resolve("cbtrn03-report.txt"));
        assertTrue(report.contains("0000000000000001"));
        assertTrue(report.contains("Account Total"));
        assertTrue(report.contains("Grand Total"));
        assertTrue(report.lines().anyMatch(line -> line.length() >= 100 && !line.contains("|")));
        assertTrue(accounts.findById(1L).orElseThrow().getAcctCurrBal().compareTo(
                java.math.BigDecimal.ZERO) > 0);
        assertTrue(Files.size(output.resolve("EXPORT.DATA")) % 501 == 0);
    }

    @Test
    void dailyReadersContinueAfterBlankLine() throws Exception {
        String record = validDailyRecord("0000000000000001");
        String second = validDailyRecord("0000000000000002");
        Path daily = Path.of("target/test-batch-output/mid-file-blank.txt");
        Files.writeString(daily, record + System.lineSeparator() + System.lineSeparator()
                + second + System.lineSeparator());

        launch("cbtrn02Job", params("dailyFile", daily.toString()));

        assertNotNull(transactions.findById("0000000000000001").orElseThrow()
                .getTranProcessTimestamp());
        assertNotNull(transactions.findById("0000000000000002").orElseThrow()
                .getTranProcessTimestamp());
    }

    @Test
    void interestWritesDistinctTransactionPerEligibleCategory() throws Exception {
        Account targetAccount = accounts.findById(1L).orElseThrow();
        TransactionCategoryBalance extraBalance = new TransactionCategoryBalance();
        TransactionCategoryBalance.Id extraBalanceId = new TransactionCategoryBalance.Id();
        extraBalanceId.setAcctId(targetAccount.getAcctId());
        extraBalanceId.setTypeCode("01");
        extraBalanceId.setCategoryCode(2);
        extraBalance.setId(extraBalanceId);
        extraBalance.setBalance(new BigDecimal("100.00"));
        balances.save(extraBalance);
        saveDisclosure("DEFAULT", "01", 2);

        TransactionCategoryBalance thirdBalance = new TransactionCategoryBalance();
        TransactionCategoryBalance.Id thirdBalanceId = new TransactionCategoryBalance.Id();
        thirdBalanceId.setAcctId(targetAccount.getAcctId());
        thirdBalanceId.setTypeCode("01");
        thirdBalanceId.setCategoryCode(3);
        thirdBalance.setId(thirdBalanceId);
        thirdBalance.setBalance(new BigDecimal("100.00"));
        balances.save(thirdBalance);
        saveDisclosure("DEFAULT", "01", 3);

        long before = transactions.findAll().stream()
                .filter(transaction -> "System".equals(transaction.getTranSource())
                        && Integer.valueOf(5).equals(transaction.getTranCategoryCode()))
                .count();
        long expected = balances.findAll().stream().filter(balance -> {
            Account balanceAccount = accounts.findById(balance.getId().getAcctId()).orElse(null);
            if (balanceAccount == null || xrefs.findByXrefAcctId(balanceAccount.getAcctId()).isEmpty()) {
                return false;
            }
            DisclosureGroup.Id specific = new DisclosureGroup.Id();
            specific.setAcctGroupId(balanceAccount.getAcctGroupId());
            specific.setTranTypeCode(balance.getId().getTypeCode());
            specific.setTranCategoryCode(balance.getId().getCategoryCode());
            if (disclosures.existsById(specific)) {
                return true;
            }
            DisclosureGroup.Id fallback = new DisclosureGroup.Id();
            fallback.setAcctGroupId("DEFAULT");
            fallback.setTranTypeCode(balance.getId().getTypeCode());
            fallback.setTranCategoryCode(balance.getId().getCategoryCode());
            return disclosures.existsById(fallback);
        }).count();

        launch("cbact04Job", params("run", "interest-" + System.nanoTime()));

        var interest = transactions.findAll().stream()
                .filter(transaction -> "System".equals(transaction.getTranSource())
                        && Integer.valueOf(5).equals(transaction.getTranCategoryCode()))
                .toList();
        assertTrue(expected > 1);
        assertEquals(expected, interest.size() - before);
        assertEquals(interest.size(), interest.stream().map(
                transaction -> transaction.getTranId()).distinct().count());
    }

    @Test
    void reportJobRejectsMissingDateParametersClearly() {
        jobs.setJob(jobBeans.get("cbtrn03Job"));
        var exception = org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.batch.core.JobParametersInvalidException.class,
                () -> jobs.launchJob(new JobParametersBuilder().addLong(
                        "run", System.nanoTime()).toJobParameters()));
        assertTrue(exception.getMessage().contains("requires non-blank startDate and endDate"));
    }

    private String validDailyRecord(String id) throws Exception {
        String record = Files.readString(Path.of("src/test/resources/seed/ASCII/dailytran.txt"));
        StringBuilder value = new StringBuilder(record);
        while (value.length() < 350) value.append(' ');
        value.replace(0, 16, id);
        value.replace(262, 278, "1111222233334444");
        return value.toString();
    }

    private void saveDisclosure(String group, String type, int category) {
        DisclosureGroup disclosure = new DisclosureGroup();
        DisclosureGroup.Id id = new DisclosureGroup.Id();
        id.setAcctGroupId(group);
        id.setTranTypeCode(type);
        id.setTranCategoryCode(category);
        disclosure.setId(id);
        disclosure.setInterestRate(new BigDecimal("12.00"));
        disclosures.save(disclosure);
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
