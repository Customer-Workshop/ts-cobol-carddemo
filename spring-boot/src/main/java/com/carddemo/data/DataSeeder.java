package com.carddemo.data;

import com.carddemo.model.Account;
import com.carddemo.model.Card;
import com.carddemo.model.CardXref;
import com.carddemo.model.Customer;
import com.carddemo.model.DisclosureGroup;
import com.carddemo.model.SecurityUser;
import com.carddemo.model.Transaction;
import com.carddemo.model.TransactionCategory;
import com.carddemo.model.TransactionCategoryBalance;
import com.carddemo.model.TransactionType;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import com.carddemo.repository.DisclosureGroupRepository;
import com.carddemo.repository.SecurityUserRepository;
import com.carddemo.repository.TransactionCategoryBalanceRepository;
import com.carddemo.repository.TransactionCategoryRepository;
import com.carddemo.repository.TransactionRepository;
import com.carddemo.repository.TransactionTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "carddemo.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    private final String dataDir;
    private final String usrsecCharset;
    private final boolean acctdataGroupIdInZipSlot;
    private final boolean force;
    private final ResourceLoader resourceLoader;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final CardRepository cardRepository;
    private final CardXrefRepository cardXrefRepository;
    private final TransactionRepository transactionRepository;
    private final DisclosureGroupRepository disclosureGroupRepository;
    private final TransactionTypeRepository transactionTypeRepository;
    private final TransactionCategoryRepository transactionCategoryRepository;
    private final TransactionCategoryBalanceRepository transactionCategoryBalanceRepository;
    private final SecurityUserRepository securityUserRepository;

    public DataSeeder(
            org.springframework.core.env.Environment environment,
            ResourceLoader resourceLoader,
            AccountRepository accountRepository,
            CustomerRepository customerRepository,
            CardRepository cardRepository,
            CardXrefRepository cardXrefRepository,
            TransactionRepository transactionRepository,
            DisclosureGroupRepository disclosureGroupRepository,
            TransactionTypeRepository transactionTypeRepository,
            TransactionCategoryRepository transactionCategoryRepository,
            TransactionCategoryBalanceRepository transactionCategoryBalanceRepository,
            SecurityUserRepository securityUserRepository) {
        this.dataDir = environment.getProperty("carddemo.seed.data-dir", "../app/data");
        this.usrsecCharset = environment.getProperty("carddemo.seed.usrsec-charset", "IBM037");
        this.acctdataGroupIdInZipSlot = environment.getProperty(
                "carddemo.seed.acctdata-group-id-in-zip-slot", Boolean.class, true);
        this.force = environment.getProperty("carddemo.seed.force", Boolean.class, false);
        this.resourceLoader = resourceLoader;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.cardRepository = cardRepository;
        this.cardXrefRepository = cardXrefRepository;
        this.transactionRepository = transactionRepository;
        this.disclosureGroupRepository = disclosureGroupRepository;
        this.transactionTypeRepository = transactionTypeRepository;
        this.transactionCategoryRepository = transactionCategoryRepository;
        this.transactionCategoryBalanceRepository = transactionCategoryBalanceRepository;
        this.securityUserRepository = securityUserRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (!force && accountRepository.count() > 0) {
            log.info("Skipping CardDemo data seed because account data is already present");
            return;
        }
        accountRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
        cardRepository.deleteAllInBatch();
        cardXrefRepository.deleteAllInBatch();
        transactionRepository.deleteAllInBatch();
        disclosureGroupRepository.deleteAllInBatch();
        transactionTypeRepository.deleteAllInBatch();
        transactionCategoryRepository.deleteAllInBatch();
        transactionCategoryBalanceRepository.deleteAllInBatch();
        securityUserRepository.deleteAllInBatch();

        List<String> accountsData = readLines("ASCII/acctdata.txt", 300);
        List<String> customersData = readLines("ASCII/custdata.txt", 500);
        List<String> cardsData = readLines("ASCII/carddata.txt", 150);
        List<String> cardXrefsData = readLines("ASCII/cardxref.txt", 50);
        List<String> transactionsData = readLines("ASCII/dailytran.txt", 350);
        List<String> disclosureGroupsData = readLines("ASCII/discgrp.txt", 50);
        List<String> balancesData = readLines("ASCII/tcatbal.txt", 50);
        List<String> categoriesData = readLines("ASCII/trancatg.txt", 60);
        List<String> typesData = readLines("ASCII/trantype.txt", 60);
        List<Account> accounts = parseAccounts(accountsData, "acctdata.txt");
        List<Customer> customers = parseCustomers(customersData, "custdata.txt");
        List<Card> cards = parseCards(cardsData, "carddata.txt");
        List<CardXref> cardXrefs = parseCardXrefs(cardXrefsData, "cardxref.txt");
        List<Transaction> transactions = parseTransactions(transactionsData, "dailytran.txt");
        List<DisclosureGroup> disclosureGroups = parseDisclosureGroups(disclosureGroupsData, "discgrp.txt");
        List<TransactionCategoryBalance> balances = parseBalances(balancesData, "tcatbal.txt");
        List<TransactionCategory> categories = parseCategories(categoriesData, "trancatg.txt");
        List<TransactionType> types = parseTypes(typesData, "trantype.txt");
        List<SecurityUser> users = parseUsers(readBytes("EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS"));

        accountRepository.saveAll(accounts);
        customerRepository.saveAll(customers);
        cardRepository.saveAll(cards);
        cardXrefRepository.saveAll(cardXrefs);
        transactionRepository.saveAll(transactions);
        disclosureGroupRepository.saveAll(disclosureGroups);
        transactionCategoryBalanceRepository.saveAll(balances);
        transactionCategoryRepository.saveAll(categories);
        transactionTypeRepository.saveAll(types);
        securityUserRepository.saveAll(users);

        log.info("Seeded CardDemo data: accounts={}, customers={}, cards={}, cardXrefs={}, transactions={}, "
                        + "disclosureGroups={}, transactionCategoryBalances={}, transactionCategories={}, transactionTypes={}, users={}",
                accounts.size(), customers.size(), cards.size(), cardXrefs.size(), transactions.size(),
                disclosureGroups.size(), balances.size(), categories.size(), types.size(), users.size());
    }

    private List<String> readLines(String relativePath, int recordLength) throws IOException {
        try (InputStream input = open(relativePath)) {
            return CobolFieldReader.splitRecords(
                    new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), recordLength);
        }
    }

    private byte[] readBytes(String relativePath) throws IOException {
        try (InputStream input = open(relativePath)) {
            return input.readAllBytes();
        }
    }

    private InputStream open(String relativePath) throws IOException {
        Resource resource;
        if (dataDir.startsWith("classpath:")) {
            resource = resourceLoader.getResource(dataDir + "/" + relativePath);
        } else {
            resource = resourceLoader.getResource("file:" + Path.of(dataDir, relativePath).toAbsolutePath());
        }
        if (!resource.exists()) {
            throw new IOException("Seed data file does not exist: " + resource.getDescription());
        }
        return resource.getInputStream();
    }

    private List<Account> parseAccounts(List<String> lines, String sourceName) {
        List<Account> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int recordNumber = i + 1;
            Account value = new Account();
            value.setAcctId(CobolFieldReader.requiredUnsignedLong(line, 0, 11, sourceName, recordNumber));
            value.setAcctActiveStatus(CobolFieldReader.text(line, 11, 1));
            value.setAcctCurrBal(CobolFieldReader.signedDecimal(line, 12, 10, 2));
            value.setAcctCreditLimit(CobolFieldReader.signedDecimal(line, 24, 10, 2));
            value.setAcctCashCreditLimit(CobolFieldReader.signedDecimal(line, 36, 10, 2));
            value.setAcctOpenDate(date(line, 48, 10));
            value.setAcctExpirationDate(date(line, 58, 10));
            value.setAcctReissueDate(date(line, 68, 10));
            value.setAcctCurrCycCredit(CobolFieldReader.signedDecimal(line, 78, 10, 2));
            value.setAcctCurrCycDebit(CobolFieldReader.signedDecimal(line, 90, 10, 2));
            String accountZip = CobolFieldReader.text(line, 102, 10);
            String accountGroup = CobolFieldReader.text(line, 112, 10);
            if (acctdataGroupIdInZipSlot) {
                // The supplied acctdata generator writes ACCT-GROUP-ID at offset 102
                // (the copybook's ZIP slot); false preserves strict copybook offsets.
                accountGroup = accountZip;
                accountZip = null;
            }
            value.setAcctAddrZip(accountZip);
            value.setAcctGroupId(accountGroup);
            result.add(value);
        }
        return result;
    }

    private List<Customer> parseCustomers(List<String> lines, String sourceName) {
        List<Customer> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int recordNumber = i + 1;
            Customer value = new Customer();
            value.setCustId(CobolFieldReader.requiredUnsignedLong(line, 0, 9, sourceName, recordNumber));
            value.setCustFirstName(CobolFieldReader.text(line, 9, 25));
            value.setCustMiddleName(CobolFieldReader.text(line, 34, 25));
            value.setCustLastName(CobolFieldReader.text(line, 59, 25));
            value.setCustAddrLine1(CobolFieldReader.text(line, 84, 50));
            value.setCustAddrLine2(CobolFieldReader.text(line, 134, 50));
            value.setCustAddrLine3(CobolFieldReader.text(line, 184, 50));
            value.setCustAddrStateCode(CobolFieldReader.text(line, 234, 2));
            value.setCustAddrCountryCode(CobolFieldReader.text(line, 236, 3));
            value.setCustAddrZip(CobolFieldReader.text(line, 239, 10));
            value.setCustPhoneNum1(CobolFieldReader.text(line, 249, 15));
            value.setCustPhoneNum2(CobolFieldReader.text(line, 264, 15));
            value.setCustSsn(CobolFieldReader.optionalUnsignedLong(line, 279, 9));
            value.setCustGovernmentIssuedId(CobolFieldReader.text(line, 288, 20));
            value.setCustDob(date(line, 308, 10));
            value.setCustEftAccountId(CobolFieldReader.text(line, 318, 10));
            value.setCustPrimaryCardHolderIndicator(CobolFieldReader.text(line, 328, 1));
            Long fico = CobolFieldReader.optionalUnsignedLong(line, 329, 3);
            value.setCustFicoCreditScore(fico == null ? null : fico.intValue());
            result.add(value);
        }
        return result;
    }

    private List<Card> parseCards(List<String> lines, String sourceName) {
        List<Card> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int recordNumber = i + 1;
            Card value = new Card();
            value.setCardNumber(CobolFieldReader.requiredText(line, 0, 16, sourceName, recordNumber));
            value.setCardAcctId(CobolFieldReader.optionalUnsignedLong(line, 16, 11));
            Long cvv = CobolFieldReader.optionalUnsignedLong(line, 27, 3);
            value.setCardCvvCode(cvv == null ? null : cvv.intValue());
            value.setCardEmbossedName(CobolFieldReader.text(line, 30, 50));
            value.setCardExpirationDate(date(line, 80, 10));
            value.setCardActiveStatus(CobolFieldReader.text(line, 90, 1));
            result.add(value);
        }
        return result;
    }

    private List<CardXref> parseCardXrefs(List<String> lines, String sourceName) {
        List<CardXref> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int recordNumber = i + 1;
            CardXref value = new CardXref();
            value.setXrefCardNumber(CobolFieldReader.requiredText(line, 0, 16, sourceName, recordNumber));
            value.setXrefCustId(CobolFieldReader.optionalUnsignedLong(line, 16, 9));
            value.setXrefAcctId(CobolFieldReader.optionalUnsignedLong(line, 25, 11));
            result.add(value);
        }
        return result;
    }

    private List<Transaction> parseTransactions(List<String> lines, String sourceName) {
        List<Transaction> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int recordNumber = i + 1;
            Transaction value = new Transaction();
            value.setTranId(CobolFieldReader.requiredText(line, 0, 16, sourceName, recordNumber));
            value.setTranTypeCode(CobolFieldReader.text(line, 16, 2));
            value.setTranCategoryCode((int) CobolFieldReader.requiredUnsignedLong(
                    line, 18, 4, sourceName, recordNumber));
            value.setTranSource(CobolFieldReader.text(line, 22, 10));
            value.setTranDescription(CobolFieldReader.text(line, 32, 100));
            value.setTranAmount(CobolFieldReader.signedDecimal(line, 132, 9, 2));
            value.setTranMerchantId(CobolFieldReader.optionalUnsignedLong(line, 143, 9));
            value.setTranMerchantName(CobolFieldReader.text(line, 152, 50));
            value.setTranMerchantCity(CobolFieldReader.text(line, 202, 50));
            value.setTranMerchantZip(CobolFieldReader.text(line, 252, 10));
            value.setTranCardNumber(CobolFieldReader.text(line, 262, 16));
            value.setTranOriginTimestamp(timestamp(line, 278, 26));
            value.setTranProcessTimestamp(timestamp(line, 304, 26));
            result.add(value);
        }
        return result;
    }

    private List<DisclosureGroup> parseDisclosureGroups(List<String> lines, String sourceName) {
        List<DisclosureGroup> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int recordNumber = i + 1;
            DisclosureGroup value = new DisclosureGroup();
            DisclosureGroup.Id id = new DisclosureGroup.Id();
            id.setAcctGroupId(CobolFieldReader.requiredText(line, 0, 10, sourceName, recordNumber));
            id.setTranTypeCode(CobolFieldReader.requiredText(line, 10, 2, sourceName, recordNumber));
            id.setTranCategoryCode((int) CobolFieldReader.requiredUnsignedLong(line, 12, 4, sourceName, recordNumber));
            value.setId(id);
            value.setInterestRate(CobolFieldReader.signedDecimal(line, 16, 4, 2));
            result.add(value);
        }
        return result;
    }

    private List<TransactionCategoryBalance> parseBalances(List<String> lines, String sourceName) {
        List<TransactionCategoryBalance> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int recordNumber = i + 1;
            TransactionCategoryBalance value = new TransactionCategoryBalance();
            TransactionCategoryBalance.Id id = new TransactionCategoryBalance.Id();
            id.setAcctId(CobolFieldReader.requiredUnsignedLong(line, 0, 11, sourceName, recordNumber));
            id.setTypeCode(CobolFieldReader.requiredText(line, 11, 2, sourceName, recordNumber));
            id.setCategoryCode((int) CobolFieldReader.requiredUnsignedLong(line, 13, 4, sourceName, recordNumber));
            value.setId(id);
            value.setBalance(CobolFieldReader.signedDecimal(line, 17, 9, 2));
            result.add(value);
        }
        return result;
    }

    private List<TransactionCategory> parseCategories(List<String> lines, String sourceName) {
        List<TransactionCategory> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int recordNumber = i + 1;
            TransactionCategory value = new TransactionCategory();
            TransactionCategory.Id id = new TransactionCategory.Id();
            id.setTranTypeCode(CobolFieldReader.requiredText(line, 0, 2, sourceName, recordNumber));
            id.setTranCategoryCode((int) CobolFieldReader.requiredUnsignedLong(line, 2, 4, sourceName, recordNumber));
            value.setId(id);
            value.setDescription(CobolFieldReader.text(line, 6, 50));
            result.add(value);
        }
        return result;
    }

    private List<TransactionType> parseTypes(List<String> lines, String sourceName) {
        List<TransactionType> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int recordNumber = i + 1;
            TransactionType value = new TransactionType();
            value.setTranType(CobolFieldReader.requiredText(line, 0, 2, sourceName, recordNumber));
            value.setDescription(CobolFieldReader.text(line, 2, 50));
            result.add(value);
        }
        return result;
    }

    private List<SecurityUser> parseUsers(byte[] bytes) {
        String text = new String(bytes, Charset.forName(usrsecCharset));
        List<SecurityUser> result = new ArrayList<>();
        for (int offset = 0; offset < text.length(); offset += 80) {
            int end = Math.min(text.length(), offset + 80);
            String line = text.substring(offset, end);
            if (line.length() < 80) {
                line += " ".repeat(80 - line.length());
            }
            SecurityUser value = new SecurityUser();
            value.setUserId(CobolFieldReader.requiredText(line, 0, 8, "USRSEC", offset / 80 + 1));
            value.setFirstName(CobolFieldReader.text(line, 8, 20));
            value.setLastName(CobolFieldReader.text(line, 28, 20));
            value.setPassword(CobolFieldReader.text(line, 48, 8));
            value.setUserType(CobolFieldReader.text(line, 56, 1));
            result.add(value);
        }
        return result;
    }

    private LocalDate date(String line, int offset, int length) {
        String value = CobolFieldReader.text(line, offset, length);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private LocalDateTime timestamp(String line, int offset, int length) {
        String value = CobolFieldReader.text(line, offset, length);
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, TIMESTAMP_FORMAT);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }
}
