package com.carddemo.batch;

import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.Transaction;
import com.carddemo.model.TransactionCategoryBalance;
import com.carddemo.model.TransactionCategoryBalanceId;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.TransactionCategoryBalanceRepository;
import com.carddemo.repository.TransactionRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Replaces CBTRN02C.cbl — daily transaction posting with validation and balance updates.
 * <p>
 * Validation codes: 100=invalid card, 101=account not found, 102=over-limit, 103=expired.
 * Balance updates: ACCT-CURR-BAL += amt; amt >= 0 → cycCredit += amt; amt < 0 → cycDebit += amt.
 */
@Configuration
public class TransactionPostingJobConfig {

    public record PostingResult(Transaction transaction, boolean accepted,
                                int rejectCode, String rejectReason, Long acctId) {
    }

    @Bean
    public FlatFileItemReader<Transaction> dailyTransactionReader() {
        return new FlatFileItemReaderBuilder<Transaction>()
                .name("dailyTransactionReader")
                .resource(new FileSystemResource("input/daily-transactions.csv"))
                .delimited()
                .names("tranId", "tranTypeCd", "tranCatCd", "tranSource", "tranDesc",
                        "tranAmt", "tranMerchantId", "tranMerchantName",
                        "tranMerchantCity", "tranMerchantZip", "tranCardNum",
                        "tranOrigTs", "tranProcTs")
                .fieldSetMapper(new BeanWrapperFieldSetMapper<>() {{
                    setTargetType(Transaction.class);
                }})
                .build();
    }

    @Bean
    public ItemProcessor<Transaction, PostingResult> transactionPostingProcessor(
            CardXrefRepository xrefRepo,
            AccountRepository accountRepo) {
        return transaction -> {
            // 1500-A-LOOKUP-XREF
            Optional<CardXref> xref = xrefRepo.findById(
                    transaction.getTranCardNum() != null ? transaction.getTranCardNum().trim() : "");
            if (xref.isEmpty()) {
                return new PostingResult(transaction, false, 100,
                        "INVALID CARD NUMBER FOUND", null);
            }

            Long acctId = xref.get().getXrefAcctId();

            // 1500-B-LOOKUP-ACCT
            Optional<Account> acctOpt = accountRepo.findById(acctId);
            if (acctOpt.isEmpty()) {
                return new PostingResult(transaction, false, 101,
                        "ACCOUNT RECORD NOT FOUND", acctId);
            }

            Account account = acctOpt.get();

            // Over-limit: cycCredit - cycDebit + tranAmt <= creditLimit
            BigDecimal tempBal = account.getAcctCurrCycCredit()
                    .subtract(account.getAcctCurrCycDebit())
                    .add(transaction.getTranAmt());
            if (account.getAcctCreditLimit().compareTo(tempBal) < 0) {
                return new PostingResult(transaction, false, 102,
                        "OVERLIMIT TRANSACTION", acctId);
            }

            // Expiration: acctExpirationDate >= txDate (first 10 chars of orig timestamp)
            String txDate = transaction.getTranOrigTs() != null
                    ? transaction.getTranOrigTs().substring(0,
                    Math.min(10, transaction.getTranOrigTs().length()))
                    : "";
            if (account.getAcctExpirationDate() != null
                    && account.getAcctExpirationDate().compareTo(txDate) < 0) {
                return new PostingResult(transaction, false, 103,
                        "TRANSACTION RECEIVED AFTER ACCT EXPIRATION", acctId);
            }

            return new PostingResult(transaction, true, 0, null, acctId);
        };
    }

    @Bean
    public ItemWriter<PostingResult> transactionPostingWriter(
            AccountRepository accountRepo,
            TransactionRepository transactionRepo,
            TransactionCategoryBalanceRepository tcatRepo,
            CardXrefRepository xrefRepo) {
        return items -> {
            String procTs = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SS0000"));
            for (PostingResult r : items) {
                if (!r.accepted()) {
                    // rejected — skip (rejects would be written by a separate writer)
                    continue;
                }
                Transaction tx = r.transaction();
                tx.setTranProcTs(procTs);

                // 2800-UPDATE-ACCOUNT-REC: update balances
                Account account = accountRepo.findById(r.acctId()).orElseThrow();
                account.setAcctCurrBal(account.getAcctCurrBal().add(tx.getTranAmt()));
                if (tx.getTranAmt().signum() >= 0) {
                    account.setAcctCurrCycCredit(
                            account.getAcctCurrCycCredit().add(tx.getTranAmt()));
                } else {
                    account.setAcctCurrCycDebit(
                            account.getAcctCurrCycDebit().add(tx.getTranAmt()));
                }
                accountRepo.save(account);

                // 2700-UPDATE-TCATBAL: update or create transaction category balance
                TransactionCategoryBalanceId tcatId = new TransactionCategoryBalanceId(
                        r.acctId(), tx.getTranTypeCd(), tx.getTranCatCd());
                TransactionCategoryBalance tcat = tcatRepo.findById(tcatId)
                        .orElse(TransactionCategoryBalance.builder()
                                .acctId(r.acctId())
                                .tranTypeCd(tx.getTranTypeCd())
                                .tranCatCd(tx.getTranCatCd())
                                .tranCatBal(BigDecimal.ZERO)
                                .build());
                tcat.setTranCatBal(tcat.getTranCatBal().add(tx.getTranAmt()));
                tcatRepo.save(tcat);

                // 2900-WRITE-TRANSACTION-FILE
                transactionRepo.save(tx);
            }
        };
    }

    @Bean
    public Step transactionPostingStep(
            JobRepository jobRepository,
            PlatformTransactionManager txManager,
            FlatFileItemReader<Transaction> dailyTransactionReader,
            ItemProcessor<Transaction, PostingResult> transactionPostingProcessor,
            ItemWriter<PostingResult> transactionPostingWriter) {
        return new StepBuilder("transactionPostingStep", jobRepository)
                .<Transaction, PostingResult>chunk(50, txManager)
                .reader(dailyTransactionReader)
                .processor(transactionPostingProcessor)
                .writer(transactionPostingWriter)
                .build();
    }

    @Bean
    public Job transactionPostingJob(
            JobRepository jobRepository,
            Step transactionPostingStep) {
        return new JobBuilder("transactionPostingJob", jobRepository)
                .start(transactionPostingStep)
                .build();
    }
}
