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
import java.nio.file.Files;
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

        List<Account> accounts = parseAccounts(readLines("ASCII/acctdata.txt"));
        List<Customer> customers = parseCustomers(readLines("ASCII/custdata.txt"));
        List<Card> cards = parseCards(readLines("ASCII/carddata.txt"));
        List<CardXref> cardXrefs = parseCardXrefs(readLines("ASCII/cardxref.txt"));
        List<Transaction> transactions = parseTransactions(readLines("ASCII/dailytran.txt"));
        List<DisclosureGroup> disclosureGroups = parseDisclosureGroups(readLines("ASCII/discgrp.txt"));
        List<TransactionCategoryBalance> balances = parseBalances(readLines("ASCII/tcatbal.txt"));
        List<TransactionCategory> categories = parseCategories(readLines("ASCII/trancatg.txt"));
        List<TransactionType> types = parseTypes(readLines("ASCII/trantype.txt"));
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

    private List<String> readLines(String relativePath) throws IOException {
        try (InputStream input = open(relativePath)) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .lines().toList();
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

    private List<Account> parseAccounts(List<String> lines) {
        List<Account> result = new ArrayList<>();
        for (String line : lines) {
            Account value = new Account();
            value.setAcctId(CobolFieldReader.unsignedLong(line, 0, 11));
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
            if (accountGroup == null && accountZip != null && accountZip.matches("[A-Z].*")) {
                accountGroup = accountZip;
                accountZip = null;
            }
            value.setAcctAddrZip(accountZip);
            value.setAcctGroupId(accountGroup);
            result.add(value);
        }
        return result;
    }

    private List<Customer> parseCustomers(List<String> lines) {
        List<Customer> result = new ArrayList<>();
        for (String line : lines) {
            Customer value = new Customer();
            value.setCustId(CobolFieldReader.unsignedLong(line, 0, 9));
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
            value.setCustSsn(CobolFieldReader.unsignedLong(line, 279, 9));
            value.setCustGovernmentIssuedId(CobolFieldReader.text(line, 288, 20));
            value.setCustDob(date(line, 308, 10));
            value.setCustEftAccountId(CobolFieldReader.text(line, 318, 10));
            value.setCustPrimaryCardHolderIndicator(CobolFieldReader.text(line, 328, 1));
            value.setCustFicoCreditScore((int) CobolFieldReader.unsignedLong(line, 329, 3));
            result.add(value);
        }
        return result;
    }

    private List<Card> parseCards(List<String> lines) {
        List<Card> result = new ArrayList<>();
        for (String line : lines) {
            Card value = new Card();
            value.setCardNumber(CobolFieldReader.text(line, 0, 16));
            value.setCardAcctId(CobolFieldReader.unsignedLong(line, 16, 11));
            value.setCardCvvCode((int) CobolFieldReader.unsignedLong(line, 27, 3));
            value.setCardEmbossedName(CobolFieldReader.text(line, 30, 50));
            value.setCardExpirationDate(date(line, 80, 10));
            value.setCardActiveStatus(CobolFieldReader.text(line, 90, 1));
            result.add(value);
        }
        return result;
    }

    private List<CardXref> parseCardXrefs(List<String> lines) {
        List<CardXref> result = new ArrayList<>();
        for (String line : lines) {
            CardXref value = new CardXref();
            value.setXrefCardNumber(CobolFieldReader.text(line, 0, 16));
            value.setXrefCustId(CobolFieldReader.unsignedLong(line, 16, 9));
            value.setXrefAcctId(CobolFieldReader.unsignedLong(line, 25, 11));
            result.add(value);
        }
        return result;
    }

    private List<Transaction> parseTransactions(List<String> lines) {
        List<Transaction> result = new ArrayList<>();
        for (String line : lines) {
            Transaction value = new Transaction();
            value.setTranId(CobolFieldReader.text(line, 0, 16));
            value.setTranTypeCode(CobolFieldReader.text(line, 16, 2));
            value.setTranCategoryCode((int) CobolFieldReader.unsignedLong(line, 18, 4));
            value.setTranSource(CobolFieldReader.text(line, 22, 10));
            value.setTranDescription(CobolFieldReader.text(line, 32, 100));
            value.setTranAmount(CobolFieldReader.signedDecimal(line, 132, 9, 2));
            value.setTranMerchantId(CobolFieldReader.unsignedLong(line, 143, 9));
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

    private List<DisclosureGroup> parseDisclosureGroups(List<String> lines) {
        List<DisclosureGroup> result = new ArrayList<>();
        for (String line : lines) {
            DisclosureGroup value = new DisclosureGroup();
            DisclosureGroup.Id id = new DisclosureGroup.Id();
            id.setAcctGroupId(CobolFieldReader.text(line, 0, 10));
            id.setTranTypeCode(CobolFieldReader.text(line, 10, 2));
            id.setTranCategoryCode((int) CobolFieldReader.unsignedLong(line, 12, 4));
            value.setId(id);
            value.setInterestRate(CobolFieldReader.signedDecimal(line, 16, 4, 2));
            result.add(value);
        }
        return result;
    }

    private List<TransactionCategoryBalance> parseBalances(List<String> lines) {
        List<TransactionCategoryBalance> result = new ArrayList<>();
        for (String line : lines) {
            TransactionCategoryBalance value = new TransactionCategoryBalance();
            TransactionCategoryBalance.Id id = new TransactionCategoryBalance.Id();
            id.setAcctId(CobolFieldReader.unsignedLong(line, 0, 11));
            id.setTypeCode(CobolFieldReader.text(line, 11, 2));
            id.setCategoryCode((int) CobolFieldReader.unsignedLong(line, 13, 4));
            value.setId(id);
            value.setBalance(CobolFieldReader.signedDecimal(line, 17, 9, 2));
            result.add(value);
        }
        return result;
    }

    private List<TransactionCategory> parseCategories(List<String> lines) {
        List<TransactionCategory> result = new ArrayList<>();
        for (String line : lines) {
            TransactionCategory value = new TransactionCategory();
            TransactionCategory.Id id = new TransactionCategory.Id();
            id.setTranTypeCode(CobolFieldReader.text(line, 0, 2));
            id.setTranCategoryCode((int) CobolFieldReader.unsignedLong(line, 2, 4));
            value.setId(id);
            value.setDescription(CobolFieldReader.text(line, 6, 50));
            result.add(value);
        }
        return result;
    }

    private List<TransactionType> parseTypes(List<String> lines) {
        List<TransactionType> result = new ArrayList<>();
        for (String line : lines) {
            TransactionType value = new TransactionType();
            value.setTranType(CobolFieldReader.text(line, 0, 2));
            value.setDescription(CobolFieldReader.text(line, 2, 50));
            result.add(value);
        }
        return result;
    }

    private List<SecurityUser> parseUsers(byte[] bytes) {
        String text = isAsciiFixture(bytes)
                ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                : new String(bytes, Charset.forName("IBM037"));
        List<SecurityUser> result = new ArrayList<>();
        for (int offset = 0; offset < text.length(); offset += 80) {
            int end = Math.min(text.length(), offset + 80);
            String line = text.substring(offset, end);
            if (line.length() < 80) {
                line += " ".repeat(80 - line.length());
            }
            SecurityUser value = new SecurityUser();
            value.setUserId(CobolFieldReader.text(line, 0, 8));
            value.setFirstName(CobolFieldReader.text(line, 8, 20));
            value.setLastName(CobolFieldReader.text(line, 28, 20));
            value.setPassword(CobolFieldReader.text(line, 48, 8));
            value.setUserType(CobolFieldReader.text(line, 56, 1));
            result.add(value);
        }
        return result;
    }

    private boolean isAsciiFixture(byte[] bytes) {
        for (byte value : bytes) {
            int unsigned = value & 0xff;
            if (unsigned != '\r' && unsigned != '\n' && (unsigned < 0x20 || unsigned > 0x7e)) {
                return false;
            }
        }
        return true;
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
