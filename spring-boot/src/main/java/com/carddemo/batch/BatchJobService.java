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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class BatchJobService {
    private final AccountRepository accounts;
    private final CardRepository cards;
    private final CardXrefRepository xrefs;
    private final CustomerRepository customers;
    private final TransactionRepository transactions;
    private final TransactionCategoryBalanceRepository balances;
    private final DisclosureGroupRepository disclosures;
    private final TransactionTypeRepository types;
    private final TransactionCategoryRepository categories;
    private final TransactionIdGenerator ids;
    private final Path outputDirectory;

    public BatchJobService(AccountRepository accounts, CardRepository cards,
                           CardXrefRepository xrefs, CustomerRepository customers,
                           TransactionRepository transactions,
                           TransactionCategoryBalanceRepository balances,
                           DisclosureGroupRepository disclosures,
                           TransactionTypeRepository types,
                           TransactionCategoryRepository categories,
                           TransactionIdGenerator ids,
                           @Value("${carddemo.batch.output-dir:target/carddemo-batch}") String outputDirectory) {
        this.accounts = accounts;
        this.cards = cards;
        this.xrefs = xrefs;
        this.customers = customers;
        this.transactions = transactions;
        this.balances = balances;
        this.disclosures = disclosures;
        this.types = types;
        this.categories = categories;
        this.ids = ids;
        this.outputDirectory = Path.of(outputDirectory);
    }

    public String validateDaily(DailyTransactionRecord record) {
        Optional<CardXref> xref = xrefs.findById(record.cardNumber());
        if (xref.isEmpty()) {
            return "CARD NUMBER " + record.cardNumber() + " COULD NOT BE VERIFIED. "
                    + "SKIPPING TRANSACTION ID-" + record.id();
        }
        if (accounts.findById(xref.get().getXrefAcctId()).isEmpty()) {
            return "ACCOUNT " + xref.get().getXrefAcctId() + " NOT FOUND";
        }
        return null;
    }

    public PostResult postDaily(DailyTransactionRecord record) {
        Validation failure = validate(record);
        if (failure != null) {
            return new PostResult(BatchFileSupport.pad(record.raw(), 350)
                    + BatchFileSupport.pad("%04d%s".formatted(failure.reason(), failure.description()), 80),
                    null);
        }
        CardXref xref = xrefs.findById(record.cardNumber()).orElseThrow();
        Account account = accounts.findById(xref.getXrefAcctId()).orElseThrow();
        TransactionCategoryBalance.Id key = new TransactionCategoryBalance.Id();
        key.setAcctId(account.getAcctId());
        key.setTypeCode(record.typeCode());
        key.setCategoryCode(record.categoryCode());
        TransactionCategoryBalance balance = balances.findById(key).orElseGet(() -> {
            TransactionCategoryBalance value = new TransactionCategoryBalance();
            value.setId(key);
            value.setBalance(BigDecimal.ZERO);
            return value;
        });
        balance.setBalance(zero(balance.getBalance()).add(record.amount()));
        balances.save(balance);
        account.setAcctCurrBal(zero(account.getAcctCurrBal()).add(record.amount()));
        if (record.amount().signum() >= 0) {
            account.setAcctCurrCycCredit(zero(account.getAcctCurrCycCredit()).add(record.amount()));
        } else {
            account.setAcctCurrCycDebit(zero(account.getAcctCurrCycDebit()).add(record.amount()));
        }
        accounts.save(account);
        transactions.save(toTransaction(record));
        return new PostResult(null, record);
    }

    public ReportLine reportLine(Transaction transaction) {
        CardXref xref = xrefs.findById(transaction.getTranCardNumber()).orElse(null);
        Long account = xref == null ? null : xref.getXrefAcctId();
        String type = types.findById(transaction.getTranTypeCode())
                .map(value -> value.getDescription()).orElse("");
        TransactionCategory.Id key = new TransactionCategory.Id();
        key.setTranTypeCode(transaction.getTranTypeCode());
        key.setTranCategoryCode(transaction.getTranCategoryCode());
        String category = categories.findById(key).map(value -> value.getDescription()).orElse("");
        return new ReportLine(transaction, account, type, category);
    }

    public InterestWork calculateInterest(Account account, List<TransactionCategoryBalance> group) {
        BigDecimal total = BigDecimal.ZERO;
        List<Transaction> interestTransactions = new ArrayList<>();
        Card card = xrefs.findByXrefAcctId(account.getAcctId()).stream().findFirst()
                .flatMap(xref -> cards.findById(xref.getXrefCardNumber())).orElse(null);
        for (TransactionCategoryBalance balance : group) {
            DisclosureGroup disclosure = disclosure(account, balance);
            if (disclosure == null) {
                continue;
            }
            BigDecimal interest = zero(balance.getBalance())
                    .multiply(zero(disclosure.getInterestRate()))
                    .divide(BigDecimal.valueOf(1200), 2, RoundingMode.HALF_UP);
            total = total.add(interest);
            if (card != null) {
                Transaction transaction = new Transaction();
                transaction.setTranId(ids.nextId());
                transaction.setTranTypeCode("01");
                transaction.setTranCategoryCode(5);
                transaction.setTranSource("System");
                transaction.setTranDescription("Int. for a/c " + account.getAcctId());
                transaction.setTranAmount(interest);
                transaction.setTranMerchantId(0L);
                transaction.setTranMerchantName("");
                transaction.setTranMerchantCity("");
                transaction.setTranMerchantZip("");
                transaction.setTranCardNumber(card.getCardNumber());
                transaction.setTranOriginTimestamp(LocalDateTime.now());
                transaction.setTranProcessTimestamp(transaction.getTranOriginTimestamp());
                interestTransactions.add(transaction);
            }
        }
        return new InterestWork(account, total, interestTransactions);
    }

    public void writeInterest(InterestWork work) {
        Account account = work.account();
        account.setAcctCurrBal(zero(account.getAcctCurrBal()).add(work.totalInterest()));
        account.setAcctCurrCycCredit(BigDecimal.ZERO);
        account.setAcctCurrCycDebit(BigDecimal.ZERO);
        accounts.save(account);
        transactions.saveAll(work.transactions());
    }

    public CardStatement statementFor(CardXref xref) {
        Card card = cards.findById(xref.getXrefCardNumber()).orElse(null);
        Account account = accounts.findById(xref.getXrefAcctId()).orElse(null);
        Customer customer = account == null ? null : customers.findById(xref.getXrefCustId()).orElse(null);
        List<Transaction> tx = transactions.findByTranCardNumberOrderByTranIdAsc(
                xref.getXrefCardNumber());
        return new CardStatement(card, account, customer, tx);
    }

    public String statementPlain(CardStatement statement) {
        if (statement.account() == null || statement.customer() == null) return "";
        StringBuilder out = new StringBuilder();
        out.append("Bank of XYZ\n")
                .append("410 Terry Ave N\n")
                .append("Seattle WA 99999\n")
                .append("Account ID         : ").append(statement.account().getAcctId()).append('\n')
                .append("Customer Name      : ").append(statement.customer().getCustFirstName()).append(' ')
                .append(statement.customer().getCustLastName()).append('\n')
                .append("Address            : ").append(statement.customer().getCustAddrLine1()).append('\n')
                .append("                   ").append(statement.customer().getCustAddrLine2()).append('\n')
                .append("                   ").append(statement.customer().getCustAddrLine3()).append('\n')
                .append("Current Balance    : ").append(statement.account().getAcctCurrBal()).append('\n')
                .append("FICO Score         : ").append(statement.customer().getCustFicoCreditScore()).append('\n')
                .append("-".repeat(80)).append('\n')
                .append("                   TRANSACTION SUMMARY\n")
                .append("-".repeat(80)).append('\n')
                .append("Tran ID         Tran Details                                      Tran Amount\n");
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction tx : statement.transactions()) {
            out.append("%-16s %-50s $%10.2f%n".formatted(tx.getTranId(),
                    trim(tx.getTranDescription(), 50), zero(tx.getTranAmount())));
            total = total.add(zero(tx.getTranAmount()));
        }
        return out.append("Total EXP: ").append(total).append('\n')
                .append("********************************END OF STATEMENT********************************\n")
                .toString();
    }

    public String statementHtml(CardStatement statement) {
        StringBuilder out = new StringBuilder("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <title>HTML Table Layout</title>
                </head>
                <body style="margin:0px;">
                <table  align="center" frame="box" style="width:70%; font:12px Segoe UI,sans-serif;">
                """);
        out.append("<tr><td colspan=\"3\"><h3>Statement for Account Number: ")
                .append(statement.account() == null ? "" : statement.account().getAcctId())
                .append("</h3></td></tr>");
        if (statement.customer() != null) {
            out.append("<tr><td colspan=\"3\"><p style=\"font-size:16px\">")
                    .append(statement.customer().getCustFirstName()).append(' ')
                    .append(statement.customer().getCustLastName()).append("</p></td></tr>");
        }
        out.append("<tr><td colspan=\"3\"><p style=\"font-size:16px\">Transaction Summary</p></td></tr>")
                .append("<tr><td>Tran ID</td><td>Tran Details</td><td>Amount</td></tr>");
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction tx : statement.transactions()) {
            out.append("<tr><td>").append(tx.getTranId()).append("</td><td>")
                    .append(tx.getTranDescription()).append("</td><td>")
                    .append(zero(tx.getTranAmount())).append("</td></tr>");
            total = total.add(zero(tx.getTranAmount()));
        }
        return out.append("<tr><td colspan=\"3\">Total EXP: ").append(total)
                .append("</td></tr><tr><td colspan=\"3\"><h3>End of Statement</h3></td></tr>")
                .append("</table></body></html>\n").toString();
    }

    public String exportRecord(Object value, long sequence) {
        if (value instanceof Customer customer) {
            return export("C", customer.getCustId(), customer.getCustFirstName(),
                    customer.getCustMiddleName(), customer.getCustLastName(),
                    customer.getCustAddrLine1(), customer.getCustAddrLine2(), customer.getCustAddrLine3(),
                    customer.getCustAddrStateCode(), customer.getCustAddrCountryCode(), customer.getCustAddrZip(),
                    customer.getCustPhoneNum1(), customer.getCustPhoneNum2(), customer.getCustSsn(),
                    customer.getCustGovernmentIssuedId(), customer.getCustDob(), customer.getCustEftAccountId(),
                    customer.getCustPrimaryCardHolderIndicator(), customer.getCustFicoCreditScore(), sequence);
        }
        if (value instanceof Account account) {
            return export("A", account.getAcctId(), account.getAcctActiveStatus(), account.getAcctCurrBal(),
                    account.getAcctCreditLimit(), account.getAcctCashCreditLimit(), account.getAcctOpenDate(),
                    account.getAcctExpirationDate(), account.getAcctReissueDate(), account.getAcctCurrCycCredit(),
                    account.getAcctCurrCycDebit(), account.getAcctAddrZip(), account.getAcctGroupId(), sequence);
        }
        if (value instanceof CardXref xref) {
            return export("X", xref.getXrefCardNumber(), xref.getXrefCustId(), xref.getXrefAcctId(), sequence);
        }
        if (value instanceof Transaction transaction) {
            return export("T", transaction.getTranId(), transaction.getTranTypeCode(),
                    transaction.getTranCategoryCode(), transaction.getTranSource(), transaction.getTranDescription(),
                    transaction.getTranAmount(), transaction.getTranMerchantId(), transaction.getTranMerchantName(),
                    transaction.getTranMerchantCity(), transaction.getTranMerchantZip(), transaction.getTranCardNumber(),
                    transaction.getTranOriginTimestamp(), transaction.getTranProcessTimestamp(), sequence);
        }
        if (value instanceof Card card) {
            return export("D", card.getCardNumber(), card.getCardAcctId(), card.getCardCvvCode(),
                    card.getCardEmbossedName(), card.getCardExpirationDate(), card.getCardActiveStatus(), sequence);
        }
        throw new IllegalArgumentException("Unsupported export value " + value.getClass().getName());
    }

    public ImportResult importRecord(String line, long recordNumber) {
        try {
            if (line.length() < 500) return new ImportResult(recordNumber, "RECORD IS SHORTER THAN 500 CHARACTERS");
            String type = line.substring(0, 1);
            String data = line.substring(45, 500);
            switch (type) {
                case "C" -> {
                    Customer value = new Customer();
                    int p = 0; value.setCustId(Long.parseLong(part(data, p, 9))); p += 9;
                    value.setCustFirstName(part(data, p, 25)); p += 25; value.setCustMiddleName(part(data, p, 25)); p += 25;
                    value.setCustLastName(part(data, p, 25)); p += 25; value.setCustAddrLine1(part(data, p, 50)); p += 50;
                    value.setCustAddrLine2(part(data, p, 50)); p += 50; value.setCustAddrLine3(part(data, p, 50)); p += 50;
                    value.setCustAddrStateCode(part(data, p, 2)); p += 2; value.setCustAddrCountryCode(part(data, p, 3)); p += 3;
                    value.setCustAddrZip(part(data, p, 10)); p += 10; value.setCustPhoneNum1(part(data, p, 15)); p += 15;
                    value.setCustPhoneNum2(part(data, p, 15)); p += 15; value.setCustSsn(Long.parseLong(part(data, p, 9))); p += 9;
                    value.setCustGovernmentIssuedId(part(data, p, 20)); p += 20; value.setCustDob(java.time.LocalDate.parse(part(data, p, 10))); p += 10;
                    value.setCustEftAccountId(part(data, p, 10)); p += 10; value.setCustPrimaryCardHolderIndicator(part(data, p, 1)); p++;
                    value.setCustFicoCreditScore(Integer.parseInt(part(data, p, 3))); customers.save(value);
                }
                case "A" -> {
                    Account value = new Account(); int p = 0; value.setAcctId(Long.parseLong(part(data, p, 11))); p += 11;
                    value.setAcctActiveStatus(part(data, p, 1)); p++; value.setAcctCurrBal(decimalText(part(data, p, 12))); p += 12;
                    value.setAcctCreditLimit(decimalText(part(data, p, 12))); p += 12; value.setAcctCashCreditLimit(decimalText(part(data, p, 12))); p += 12;
                    value.setAcctOpenDate(java.time.LocalDate.parse(part(data, p, 10))); p += 10;
                    value.setAcctExpirationDate(java.time.LocalDate.parse(part(data, p, 10))); p += 10;
                    value.setAcctReissueDate(java.time.LocalDate.parse(part(data, p, 10))); p += 10;
                    value.setAcctCurrCycCredit(decimalText(part(data, p, 12))); p += 12; value.setAcctCurrCycDebit(decimalText(part(data, p, 12))); p += 12;
                    value.setAcctAddrZip(part(data, p, 10)); p += 10; value.setAcctGroupId(part(data, p, 10)); accounts.save(value);
                }
                case "X" -> {
                    CardXref value = new CardXref(); value.setXrefCardNumber(part(data, 0, 16));
                    value.setXrefCustId(Long.parseLong(part(data, 16, 9))); value.setXrefAcctId(Long.parseLong(part(data, 25, 11)));
                    xrefs.save(value);
                }
                case "T" -> {
                    Transaction value = new Transaction(); int p = 0; value.setTranId(part(data, p, 16)); p += 16;
                    value.setTranTypeCode(part(data, p, 2)); p += 2; value.setTranCategoryCode(Integer.parseInt(part(data, p, 4))); p += 4;
                    value.setTranSource(part(data, p, 10)); p += 10; value.setTranDescription(part(data, p, 100)); p += 100;
                    value.setTranAmount(decimalText(part(data, p, 11))); p += 11; value.setTranMerchantId(Long.parseLong(part(data, p, 9))); p += 9;
                    value.setTranMerchantName(part(data, p, 50)); p += 50; value.setTranMerchantCity(part(data, p, 50)); p += 50;
                    value.setTranMerchantZip(part(data, p, 10)); p += 10; value.setTranCardNumber(part(data, p, 16)); p += 16;
                    value.setTranOriginTimestamp(parseTimestamp(part(data, p, 26))); p += 26;
                    value.setTranProcessTimestamp(parseTimestamp(part(data, p, 26)));
                    transactions.save(value);
                }
                case "D" -> {
                    Card value = new Card(); value.setCardNumber(part(data, 0, 16)); value.setCardAcctId(Long.parseLong(part(data, 16, 11)));
                    value.setCardCvvCode(Integer.parseInt(part(data, 27, 3))); value.setCardEmbossedName(part(data, 30, 50));
                    value.setCardExpirationDate(java.time.LocalDate.parse(part(data, 80, 10))); value.setCardActiveStatus(part(data, 90, 1)); cards.save(value);
                }
                default -> { return new ImportResult(recordNumber, "UNKNOWN RECORD TYPE " + type); }
            }
            return new ImportResult(recordNumber, null);
        } catch (RuntimeException exception) {
            return new ImportResult(recordNumber, "INVALID " + exception.getMessage());
        }
    }

    public Path output(String name) {
        return outputDirectory.resolve(name);
    }

    private DisclosureGroup disclosure(Account account, TransactionCategoryBalance balance) {
        DisclosureGroup.Id key = new DisclosureGroup.Id();
        key.setAcctGroupId(account.getAcctGroupId()); key.setTranTypeCode(balance.getId().getTypeCode());
        key.setTranCategoryCode(balance.getId().getCategoryCode());
        return disclosures.findById(key).orElseGet(() -> {
            DisclosureGroup.Id fallback = new DisclosureGroup.Id();
            fallback.setAcctGroupId("DEFAULT"); fallback.setTranTypeCode(balance.getId().getTypeCode());
            fallback.setTranCategoryCode(balance.getId().getCategoryCode());
            return disclosures.findById(fallback).orElse(null);
        });
    }

    private Validation validate(DailyTransactionRecord record) {
        CardXref xref = xrefs.findById(record.cardNumber()).orElse(null);
        if (xref == null) return new Validation(100, "INVALID CARD NUMBER FOUND");
        Account account = accounts.findById(xref.getXrefAcctId()).orElse(null);
        if (account == null) return new Validation(101, "ACCOUNT RECORD NOT FOUND");
        BigDecimal current = zero(account.getAcctCurrCycCredit()).subtract(zero(account.getAcctCurrCycDebit()))
                .add(record.amount());
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
        Transaction value = new Transaction(); value.setTranId(record.id()); value.setTranTypeCode(record.typeCode());
        value.setTranCategoryCode(record.categoryCode()); value.setTranSource(record.source());
        value.setTranDescription(record.description()); value.setTranAmount(record.amount());
        value.setTranMerchantId(record.merchantId()); value.setTranMerchantName(record.merchantName());
        value.setTranMerchantCity(record.merchantCity()); value.setTranMerchantZip(record.merchantZip());
        value.setTranCardNumber(record.cardNumber()); value.setTranOriginTimestamp(record.originTimestamp());
        value.setTranProcessTimestamp(LocalDateTime.now()); return value;
    }

    private static String export(String type, Object... fields) {
        long sequence = ((Number) fields[fields.length - 1]).longValue();
        Object[] values = java.util.Arrays.copyOf(fields, fields.length - 1);
        int[] widths = switch (type) {
            case "C" -> new int[]{9,25,25,25,50,50,50,2,3,10,15,15,9,20,10,10,1,3};
            case "A" -> new int[]{11,1,12,12,12,10,10,10,12,12,10,10};
            case "X" -> new int[]{16,9,11};
            case "T" -> new int[]{16,2,4,10,100,11,9,50,50,10,16,26,26};
            case "D" -> new int[]{16,11,3,50,10,1};
            default -> throw new IllegalArgumentException("Unsupported export type " + type);
        };
        StringBuilder data = new StringBuilder();
        for (int i = 0; i < widths.length; i++) data.append(fixed(values[i], widths[i]));
        return fixed(type, 1) + fixed(LocalDateTime.now().toString().replace('T', ' '), 26)
                + "%09d".formatted(sequence) + fixed("0001", 4) + fixed("NORTH", 5)
                + BatchFileSupport.pad(data.toString(), 455);
    }
    private static String fixed(Object value, int width) {
        String text = value == null ? "" : value.toString();
        if (text.length() > width) return text.substring(0, width);
        return text + " ".repeat(width - text.length());
    }
    private static String part(String value, int start, int width) {
        return value.substring(start, start + width).trim();
    }
    private static BigDecimal decimalText(String value) { return value.isBlank() ? null : new BigDecimal(value); }
    private static LocalDateTime parseTimestamp(String value) {
        return value.isBlank() ? null : LocalDateTime.parse(value.replace(' ', 'T').substring(0, 19));
    }
    private static String empty(String[] fields, int index) { return index >= fields.length || fields[index].isEmpty() ? null : fields[index]; }
    private static BigDecimal decimal(String[] f, int i) { return empty(f, i) == null ? null : new BigDecimal(f[i]); }
    private static Long longValue(String[] f, int i) { return empty(f, i) == null ? null : Long.valueOf(f[i]); }
    private static Integer intValue(String[] f, int i) { return empty(f, i) == null ? null : Integer.valueOf(f[i]); }
    private static java.time.LocalDate date(String[] f, int i) { return empty(f, i) == null ? null : java.time.LocalDate.parse(f[i]); }
    private static LocalDateTime timestamp(String[] f, int i) { return empty(f, i) == null ? null : LocalDateTime.parse(f[i]); }
    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static String trim(String value, int width) { String text = value == null ? "" : value; return text.length() <= width ? text : text.substring(0, width); }

    public record PostResult(String reject, DailyTransactionRecord posted) {}
    public record ReportLine(Transaction transaction, Long accountId, String type, String category) {}
    public record InterestWork(Account account, BigDecimal totalInterest, List<Transaction> transactions) {}
    public record CardStatement(Card card, Account account, Customer customer, List<Transaction> transactions) {}
    public record ImportResult(long recordNumber, String error) {}
    private record Validation(int reason, String description) {}
}
