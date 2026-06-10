package com.carddemo.batch;

import com.carddemo.model.Account;
import com.carddemo.model.DisclosureGroup;
import com.carddemo.model.TransactionCategoryBalance;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.DisclosureGroupRepository;
import com.carddemo.repository.TransactionCategoryBalanceRepository;
import com.carddemo.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBatchTest
@SpringBootTest
class InterestCalculationJobTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier("interestCalculationJob")
    private Job interestCalculationJob;

    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void interestCalculationJob_completesSuccessfully() throws Exception {
        jobLauncherTestUtils.setJob(interestCalculationJob);

        // Capture pre-run state
        Account before = accountRepository.findById(1L).orElseThrow();
        BigDecimal balBefore = before.getAcctCurrBal();
        long txCountBefore = transactionRepository.count();

        JobExecution execution = jobLauncherTestUtils.launchJob();

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        // Account 1 has tcat balance for GROUP01/01/5001 with rate 18.99
        // Interest = (45.50 * 18.99) / 1200 = 0.72 (rounded)
        Account after = accountRepository.findById(1L).orElseThrow();
        assertTrue(after.getAcctCurrBal().compareTo(balBefore) >= 0,
                "Balance should increase or stay same after interest");
        assertEquals(0, after.getAcctCurrCycCredit().signum(),
                "Cycle credit should be reset to 0");
        assertEquals(0, after.getAcctCurrCycDebit().signum(),
                "Cycle debit should be reset to 0");

        // Interest transactions should have been created
        assertTrue(transactionRepository.count() > txCountBefore,
                "Interest transactions should be written");
    }
}
