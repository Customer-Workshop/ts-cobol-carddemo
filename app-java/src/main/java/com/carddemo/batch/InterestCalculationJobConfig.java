package com.carddemo.batch;

import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.DisclosureGroup;
import com.carddemo.model.Transaction;
import com.carddemo.model.TransactionCategoryBalance;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.DisclosureGroupRepository;
import com.carddemo.repository.TransactionCategoryBalanceRepository;
import com.carddemo.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Replaces CBACT04C.cbl — monthly interest calculation per transaction category.
 * <pre>
 *   monthlyInt = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200
 *   ACCT-CURR-BAL += totalInt
 *   ACCT-CURR-CYC-CREDIT = 0, ACCT-CURR-CYC-DEBIT = 0
 * </pre>
 */
@Configuration
@RequiredArgsConstructor
public class InterestCalculationJobConfig {

    private static final BigDecimal DIVISOR = new BigDecimal("1200");
    private static final String DEFAULT_GROUP = "DEFAULT";

    private final TransactionCategoryBalanceRepository tcatRepo;
    private final DisclosureGroupRepository disclosureRepo;
    private final AccountRepository accountRepo;
    private final CardXrefRepository xrefRepo;
    private final TransactionRepository transactionRepo;

    @Bean
    public Tasklet interestCalculationTasklet() {
        return (contribution, chunkContext) -> {
            List<Account> accounts = accountRepo.findAll();
            AtomicLong txSuffix = new AtomicLong(0);
            String procTs = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SS0000"));
            String parmDate = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            for (Account account : accounts) {
                List<TransactionCategoryBalance> catBals =
                        tcatRepo.findByAcctIdOrderByTranTypeCdAscTranCatCdAsc(account.getAcctId());
                if (catBals.isEmpty()) {
                    continue;
                }

                BigDecimal totalInt = BigDecimal.ZERO;

                // Look up a card number for this account (for the interest transaction record)
                List<CardXref> xrefs = xrefRepo.findByXrefAcctId(account.getAcctId());
                String cardNum = xrefs.isEmpty() ? "" : xrefs.get(0).getXrefCardNum();

                for (TransactionCategoryBalance tcat : catBals) {
                    // 1200-GET-INTEREST-RATE: look up by group + type + cat
                    Optional<DisclosureGroup> disc = disclosureRepo
                            .findByAcctGroupIdAndTranTypeCdAndTranCatCd(
                                    account.getAcctGroupId() != null
                                            ? account.getAcctGroupId().trim()
                                            : DEFAULT_GROUP,
                                    tcat.getTranTypeCd(), tcat.getTranCatCd());
                    if (disc.isEmpty()) {
                        disc = disclosureRepo.findByAcctGroupIdAndTranTypeCdAndTranCatCd(
                                DEFAULT_GROUP, tcat.getTranTypeCd(), tcat.getTranCatCd());
                    }
                    if (disc.isEmpty() || disc.get().getIntRate().signum() == 0) {
                        continue;
                    }

                    // 1300-COMPUTE-INTEREST: (catBal * intRate) / 1200
                    BigDecimal monthlyInt = tcat.getTranCatBal()
                            .multiply(disc.get().getIntRate())
                            .divide(DIVISOR, 2, RoundingMode.HALF_UP);

                    totalInt = totalInt.add(monthlyInt);

                    // 1300-B-WRITE-TX: write interest transaction
                    long suffix = txSuffix.incrementAndGet();
                    Transaction intTx = Transaction.builder()
                            .tranId(parmDate + String.format("%06d", suffix))
                            .tranTypeCd("01")
                            .tranCatCd(5)
                            .tranSource("System")
                            .tranDesc("Int. for a/c " + account.getAcctId())
                            .tranAmt(monthlyInt)
                            .tranMerchantId(0L)
                            .tranMerchantName("")
                            .tranMerchantCity("")
                            .tranMerchantZip("")
                            .tranCardNum(cardNum)
                            .tranOrigTs(procTs)
                            .tranProcTs(procTs)
                            .build();
                    transactionRepo.save(intTx);
                }

                // 1050-UPDATE-ACCOUNT: add interest and reset cycle fields
                if (totalInt.signum() != 0) {
                    account.setAcctCurrBal(account.getAcctCurrBal().add(totalInt));
                }
                account.setAcctCurrCycCredit(BigDecimal.ZERO);
                account.setAcctCurrCycDebit(BigDecimal.ZERO);
                accountRepo.save(account);
            }
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step interestCalculationStep(
            JobRepository jobRepository,
            PlatformTransactionManager txManager) {
        return new StepBuilder("interestCalculationStep", jobRepository)
                .tasklet(interestCalculationTasklet(), txManager)
                .build();
    }

    @Bean
    public Job interestCalculationJob(
            JobRepository jobRepository,
            Step interestCalculationStep) {
        return new JobBuilder("interestCalculationJob", jobRepository)
                .start(interestCalculationStep)
                .build();
    }
}
