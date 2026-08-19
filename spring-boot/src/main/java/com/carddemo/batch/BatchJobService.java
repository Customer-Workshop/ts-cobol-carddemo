package com.carddemo.batch;

import com.carddemo.model.Account;
import com.carddemo.model.Card;
import com.carddemo.model.CardXref;
import com.carddemo.model.Customer;
import com.carddemo.model.DisclosureGroup;
import com.carddemo.model.Transaction;
import com.carddemo.model.TransactionCategory;
import com.carddemo.model.TransactionCategoryBalance;
import com.carddemo.repository.*;
import com.carddemo.service.TransactionIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BatchJobService {
    private final AccountRepository accountRepository;
    private final CardRepository cardRepository;
    private final CardXrefRepository xrefRepository;
    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionCategoryBalanceRepository balanceRepository;
    private final DisclosureGroupRepository disclosureRepository;
    private final TransactionTypeRepository typeRepository;
    private final TransactionCategoryRepository categoryRepository;
    private final TransactionIdGenerator idGenerator;
    private final Path defaultDailyFile;
    private final Path outputDirectory;

    public BatchJobService(
            AccountRepository accountRepository, CardRepository cardRepository,
            CardXrefRepository xrefRepository, CustomerRepository customerRepository,
            TransactionRepository transactionRepository,
            TransactionCategoryBalanceRepository balanceRepository,
            DisclosureGroupRepository disclosureRepository,
            TransactionTypeRepository typeRepository,
            TransactionCategoryRepository categoryRepository,
            TransactionIdGenerator idGenerator,
            @Value("${carddemo.seed.data-dir:../app/data}") String dataDirectory,
            @Value("${carddemo.batch.output-dir:target/carddemo-batch}") String outputDirectory) {
        this.accountRepository = accountRepository;
        this.cardRepository = cardRepository;
        this.xrefRepository = xrefRepository;
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
        this.balanceRepository = balanceRepository;
        this.disclosureRepository = disclosureRepository;
        this.typeRepository = typeRepository;
        this.categoryRepository = categoryRepository;
        this.idGenerator = idGenerator;
        this.defaultDailyFile = Path.of(dataDirectory, "ASCII", "dailytran.txt");
        this.outputDirectory = Path.of(outputDirectory);
    }

    public Tasklet validateDailyTransactions() {
        return (contribution, context) -> {
            List<String> errors = new ArrayList<>();
            for (DailyTransactionRecord record : daily(context.getStepContext().getJobParameters())) {
                Optional<CardXref> xref = xrefRepository.findById(record.cardNumber());
                if (xref.isEmpty()) {
                    errors.add("CARD NUMBER " + record.cardNumber() + " " + record.id());
                } else if (accountRepository.findById(xref.get().getXrefAcctId()).isEmpty()) {
                    errors.add("ACCOUNT " + xref.get().getXrefAcctId() + " NOT FOUND");
                }
            }
            write("cbtrn01-validation.txt", errors);
            return RepeatStatus.FINISHED;
        };
    }

    @Transactional
    public Tasklet postDailyTransactions() {
        return (contribution, context) -> {
            List<String> rejects = new ArrayList<>();
            for (DailyTransactionRecord record : daily(context.getStepContext().getJobParameters())) {
                Validation failure = validate(record);
                if (failure != null) {
                    rejects.add(BatchFileSupport.pad(record.raw(), 350)
                            + BatchFileSupport.pad("%04d%s".formatted(
                                    failure.reason(), failure.description()), 80));
                    continue;
                }
                CardXref xref = xrefRepository.findById(record.cardNumber()).orElseThrow();
                Account account = accountRepository.findById(xref.getXrefAcctId()).orElseThrow();
                TransactionCategoryBalance.Id balanceId = new TransactionCategoryBalance.Id();
                balanceId.setAcctId(account.getAcctId());
                balanceId.setTypeCode(record.typeCode());
                balanceId.setCategoryCode(record.categoryCode());
                TransactionCategoryBalance balance = balanceRepository.findById(balanceId)
                        .orElseGet(() -> {
                            TransactionCategoryBalance created = new TransactionCategoryBalance();
                            created.setId(balanceId);
                            created.setBalance(BigDecimal.ZERO);
                            return created;
                        });
                balance.setBalance(zero(balance.getBalance()).add(record.amount()));
                balanceRepository.save(balance);
                account.setAcctCurrBal(zero(account.getAcctCurrBal()).add(record.amount()));
                if (record.amount().signum() >= 0) {
                    account.setAcctCurrCycCredit(zero(account.getAcctCurrCycCredit())
                            .add(record.amount()));
                } else {
                    account.setAcctCurrCycDebit(zero(account.getAcctCurrCycDebit())
                            .add(record.amount()));
                }
                accountRepository.save(account);
                transactionRepository.save(toTransaction(record));
            }
            write("cbtrn02-rejects.txt", rejects);
            return RepeatStatus.FINISHED;
        };
    }

    public Tasklet transactionReport() {
        return (contribution, context) -> {
            Map<String, Object> parameters = context.getStepContext().getJobParameters();
            LocalDate start = LocalDate.parse(stringParameter(parameters, "startDate"));
            LocalDate end = LocalDate.parse(stringParameter(parameters, "endDate"));
            List<Transaction> transactions = transactionRepository
                    .findByTranProcessTimestampBetweenOrderByTranProcessTimestampAscTranIdAsc(
                            start.atStartOfDay(), end.plusDays(1).atStartOfDay().minusNanos(1));
            StringBuilder output = new StringBuilder("TRANSACTION DETAIL REPORT\n");
            BigDecimal grand = BigDecimal.ZERO;
            Long currentAccount = null;
            BigDecimal accountTotal = BigDecimal.ZERO;
            for (Transaction transaction : transactions) {
                CardXref xref = xrefRepository.findById(transaction.getTranCardNumber()).orElse(null);
                Long account = xref == null ? null : xref.getXrefAcctId();
                if (currentAccount != null && !currentAccount.equals(account)) {
                    output.append("ACCOUNT TOTAL: ").append(accountTotal).append('\n');
                    accountTotal = BigDecimal.ZERO;
                }
                currentAccount = account;
                String type = typeRepository.findById(transaction.getTranTypeCode())
                        .map(value -> value.getDescription()).orElse("");
                TransactionCategory.Id key = new TransactionCategory.Id();
                key.setTranTypeCode(transaction.getTranTypeCode());
                key.setTranCategoryCode(transaction.getTranCategoryCode());
                String category = categoryRepository.findById(key)
                        .map(value -> value.getDescription()).orElse("");
                output.append(transaction.getTranId()).append('|').append(account).append('|')
                        .append(type).append('|').append(category).append('|')
                        .append(transaction.getTranAmount()).append('\n');
                accountTotal = accountTotal.add(zero(transaction.getTranAmount()));
                grand = grand.add(zero(transaction.getTranAmount()));
            }
            if (currentAccount != null) {
                output.append("ACCOUNT TOTAL: ").append(accountTotal).append('\n');
            }
            output.append("GRAND TOTAL: ").append(grand).append('\n');
            write("cbtrn03-report.txt", output.toString());
            return RepeatStatus.FINISHED;
        };
    }

    @Transactional
    public Tasklet interestCalculation() {
        return (contribution, context) -> {
            for (TransactionCategoryBalance balance : balanceRepository.findAll()) {
                Account account = accountRepository.findById(balance.getId().getAcctId()).orElse(null);
                if (account == null) {
                    continue;
                }
                DisclosureGroup.Id key = new DisclosureGroup.Id();
                key.setAcctGroupId(account.getAcctGroupId());
                key.setTranTypeCode(balance.getId().getTypeCode());
                key.setTranCategoryCode(balance.getId().getCategoryCode());
                DisclosureGroup disclosure = disclosureRepository.findById(key).orElseGet(() -> {
                    DisclosureGroup.Id fallback = new DisclosureGroup.Id();
                    fallback.setAcctGroupId("DEFAULT");
                    fallback.setTranTypeCode(balance.getId().getTypeCode());
                    fallback.setTranCategoryCode(balance.getId().getCategoryCode());
                    return disclosureRepository.findById(fallback).orElse(null);
                });
                if (disclosure == null) {
                    continue;
                }
                BigDecimal interest = zero(balance.getBalance())
                        .multiply(zero(disclosure.getInterestRate()))
                        .divide(BigDecimal.valueOf(1200), 2, java.math.RoundingMode.HALF_UP);
                account.setAcctCurrBal(zero(account.getAcctCurrBal()).add(interest));
                account.setAcctCurrCycCredit(BigDecimal.ZERO);
                account.setAcctCurrCycDebit(BigDecimal.ZERO);
                accountRepository.save(account);
                Card card = xrefRepository.findByXrefAcctId(account.getAcctId()).stream()
                        .findFirst().flatMap(xref -> cardRepository.findById(xref.getXrefCardNumber()))
                        .orElse(null);
                if (card != null) {
                    Transaction transaction = new Transaction();
                    transaction.setTranId(idGenerator.nextId());
                    transaction.setTranTypeCode("01");
                    transaction.setTranCategoryCode(5);
                    transaction.setTranSource("System");
                    transaction.setTranDescription("Int. for a/c " + account.getAcctId());
                    transaction.setTranAmount(interest);
                    transaction.setTranMerchantId(0L);
                    transaction.setTranCardNumber(card.getCardNumber());
                    transaction.setTranOriginTimestamp(LocalDateTime.now());
                    transaction.setTranProcessTimestamp(transaction.getTranOriginTimestamp());
                    transactionRepository.save(transaction);
                }
            }
            return RepeatStatus.FINISHED;
        };
    }

    public Tasklet statements() {
        return (contribution, context) -> {
            StringBuilder plain = new StringBuilder();
            StringBuilder html = new StringBuilder("""
                    <!DOCTYPE html><html lang="en"><head><meta charset="utf-8"><title>HTML Table Layout</title></head><body style="margin:0px;">
                    """);
            for (CardXref xref : xrefRepository.findAll().stream()
                    .sorted(Comparator.comparing(CardXref::getXrefCardNumber)).toList()) {
                Account account = accountRepository.findById(xref.getXrefAcctId()).orElse(null);
                Customer customer = customerRepository.findById(xref.getXrefCustId()).orElse(null);
                if (account == null || customer == null) continue;
                List<Transaction> transactions = transactionRepository
                        .findByTranCardNumberOrderByTranIdAsc(xref.getXrefCardNumber());
                plain.append("Account ID         : ").append(account.getAcctId()).append('\n')
                        .append("Current Balance    : ").append(account.getAcctCurrBal()).append('\n')
                        .append("FICO Score         : ").append(customer.getCustFicoCreditScore()).append('\n')
                        .append("TRANSACTION SUMMARY\nTran ID|Tran Details|Tran Amount\n");
                html.append("<h2>Account ID: ").append(account.getAcctId())
                        .append("</h2><table><tr><th>Tran ID</th><th>Tran Details</th><th>Tran Amount</th></tr>");
                BigDecimal total = BigDecimal.ZERO;
                for (Transaction transaction : transactions) {
                    plain.append(transaction.getTranId()).append('|')
                            .append(transaction.getTranDescription()).append('|')
                            .append(transaction.getTranAmount()).append('\n');
                    html.append("<tr><td>").append(transaction.getTranId()).append("</td><td>")
                            .append(transaction.getTranDescription()).append("</td><td>")
                            .append(transaction.getTranAmount()).append("</td></tr>");
                    total = total.add(zero(transaction.getTranAmount()));
                }
                plain.append("Total EXP: ").append(total).append('\n')
                        .append("END OF STATEMENT\n");
                html.append("</table><p>Total EXP: ").append(total)
                        .append("</p><hr/>");
            }
            html.append("</body></html>");
            write("STATEMNT.PS", plain.toString());
            write("STATEMNT.HTML", html.toString());
            return RepeatStatus.FINISHED;
        };
    }

    public Tasklet exportData() {
        return (contribution, context) -> {
            List<String> records = new ArrayList<>();
            customerRepository.findAll().forEach(value -> records.add(exportRecord("C", value.getCustId(),
                    value.getCustFirstName(), value.getCustLastName())));
            accountRepository.findAll().forEach(value -> records.add(exportRecord("A", value.getAcctId(),
                    value.getAcctActiveStatus(), value.getAcctCurrBal())));
            cardRepository.findAll().forEach(value -> records.add(exportRecord("D", value.getCardNumber(),
                    value.getCardAcctId(), value.getCardActiveStatus())));
            xrefRepository.findAll().forEach(value -> records.add(exportRecord("X", value.getXrefCardNumber(),
                    value.getXrefCustId(), value.getXrefAcctId())));
            transactionRepository.findAll().forEach(value -> records.add(exportRecord("T", value.getTranId(),
                    value.getTranCardNumber(), value.getTranAmount())));
            write("EXPORT.DATA", records);
            return RepeatStatus.FINISHED;
        };
    }

    @Transactional
    public Tasklet importData() {
        return (contribution, context) -> {
            Path file = Path.of(stringParameter(context.getStepContext().getJobParameters(),
                    "inputFile", outputDirectory.resolve("EXPORT.DATA").toString()));
            if (!Files.exists(file)) return RepeatStatus.FINISHED;
            for (String line : Files.readAllLines(file)) {
                String[] fields = line.trim().split("\\|", -1);
                if (fields.length < 4) continue;
                switch (fields[0]) {
                    case "C" -> {
                        Customer customer = new Customer();
                        customer.setCustId(Long.valueOf(fields[1]));
                        customer.setCustFirstName(fields[2]);
                        customer.setCustLastName(fields[3]);
                        customerRepository.save(customer);
                    }
                    case "A" -> {
                        Account account = new Account();
                        account.setAcctId(Long.valueOf(fields[1]));
                        account.setAcctActiveStatus(fields[2]);
                        account.setAcctCurrBal(new BigDecimal(fields[3]));
                        accountRepository.save(account);
                    }
                    case "D" -> {
                        Card card = new Card();
                        card.setCardNumber(fields[1]);
                        card.setCardAcctId(Long.valueOf(fields[2]));
                        card.setCardActiveStatus(fields[3]);
                        cardRepository.save(card);
                    }
                    case "X" -> {
                        CardXref xref = new CardXref();
                        xref.setXrefCardNumber(fields[1]);
                        xref.setXrefCustId(Long.valueOf(fields[2]));
                        xref.setXrefAcctId(Long.valueOf(fields[3]));
                        xrefRepository.save(xref);
                    }
                    case "T" -> {
                        Transaction transaction = new Transaction();
                        transaction.setTranId(fields[1]);
                        transaction.setTranCardNumber(fields[2]);
                        transaction.setTranAmount(new BigDecimal(fields[3]));
                        transactionRepository.save(transaction);
                    }
                    default -> {
                    }
                }
            }
            write("CBIMPORT.errors", List.of());
            return RepeatStatus.FINISHED;
        };
    }

    private Validation validate(DailyTransactionRecord record) {
        CardXref xref = xrefRepository.findById(record.cardNumber()).orElse(null);
        if (xref == null) return new Validation(100, "INVALID CARD NUMBER FOUND");
        Account account = accountRepository.findById(xref.getXrefAcctId()).orElse(null);
        if (account == null) return new Validation(101, "ACCOUNT RECORD NOT FOUND");
        BigDecimal current = zero(account.getAcctCurrCycCredit())
                .subtract(zero(account.getAcctCurrCycDebit())).add(record.amount());
        if (zero(account.getAcctCreditLimit()).compareTo(current) < 0) {
            return new Validation(102, "OVERLIMIT TRANSACTION");
        }
        if (account.getAcctExpirationDate() != null && record.originTimestamp() != null
                && account.getAcctExpirationDate().isBefore(record.originTimestamp().toLocalDate())) {
            return new Validation(103, "TRANSACTION RECEIVED AFTER ACCT EXPIRATION");
        }
        return null;
    }

    private Transaction toTransaction(DailyTransactionRecord record) {
        Transaction value = new Transaction();
        value.setTranId(record.id());
        value.setTranTypeCode(record.typeCode());
        value.setTranCategoryCode(record.categoryCode());
        value.setTranSource(record.source());
        value.setTranDescription(record.description());
        value.setTranAmount(record.amount());
        value.setTranMerchantId(record.merchantId());
        value.setTranMerchantName(record.merchantName());
        value.setTranMerchantCity(record.merchantCity());
        value.setTranMerchantZip(record.merchantZip());
        value.setTranCardNumber(record.cardNumber());
        value.setTranOriginTimestamp(record.originTimestamp());
        value.setTranProcessTimestamp(LocalDateTime.now());
        return value;
    }

    private List<DailyTransactionRecord> daily(Map<String, Object> parameters) throws IOException {
        String file = stringParameter(parameters, "dailyFile", defaultDailyFile.toString());
        return BatchFileSupport.dailyTransactions(Path.of(file));
    }

    private String stringParameter(Map<String, Object> parameters, String key) {
        return stringParameter(parameters, key, null);
    }

    private String stringParameter(Map<String, Object> parameters, String key, String fallback) {
        Object value = parameters.get(key);
        return value == null ? fallback : value.toString();
    }

    private String stringParameter(JobParameters parameters, String key) {
        return parameters.getString(key);
    }

    private String stringParameter(JobParameters parameters, String key, String fallback) {
        String value = parameters.getString(key);
        return value == null ? fallback : value;
    }

    private void write(String name, List<String> lines) throws IOException {
        write(name, String.join(System.lineSeparator(), lines)
                + (lines.isEmpty() ? "" : System.lineSeparator()));
    }

    private void write(String name, String content) throws IOException {
        Files.createDirectories(outputDirectory);
        Files.writeString(outputDirectory.resolve(name), content);
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String exportRecord(String type, Object id, Object first, Object second) {
        return BatchFileSupport.pad(type + "|" + id + "|" + first + "|" + second, 500);
    }

    private record Validation(int reason, String description) {
    }
}
