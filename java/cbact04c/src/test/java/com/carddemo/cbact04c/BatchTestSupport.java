package com.carddemo.cbact04c;

import com.carddemo.cbact04c.domain.Records.Account;
import com.carddemo.cbact04c.domain.Records.DiscGroup;
import com.carddemo.cbact04c.domain.Records.DiscKey;
import com.carddemo.cbact04c.domain.Records.TranCat;
import com.carddemo.cbact04c.domain.Records.Xref;
import com.carddemo.cbact04c.io.RecordCodecs;
import com.carddemo.cbact04c.service.BatchJob;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class BatchTestSupport {

    private BatchTestSupport() {
    }

    static BatchJob writeJob(
            Path directory,
            List<TranCat> categories,
            List<Account> accounts,
            List<DiscGroup> groups,
            List<Xref> xrefs,
            boolean finalUpdateAtEof) throws IOException {
        Files.createDirectories(directory);
        Path tcatbal = write(directory.resolve("tcatbal.txt"),
                categories.stream().map(RecordCodecs::encodeTranCat).toList());
        Path account = write(directory.resolve("acctdata.txt"),
                accounts.stream().map(RecordCodecs::encodeAccount).toList());
        Path discgrp = write(directory.resolve("discgrp.txt"),
                groups.stream().map(RecordCodecs::encodeDiscGroup).toList());
        Path xref = write(directory.resolve("cardxref.txt"),
                xrefs.stream().map(RecordCodecs::encodeXref).toList());
        return new BatchJob(
                tcatbal,
                xref,
                discgrp,
                account,
                directory.resolve("transact.txt"),
                "2025-05-01",
                finalUpdateAtEof);
    }

    static TranCat category(String accountId, String type, String category, String balance) {
        return new TranCat(
                accountId,
                type,
                category,
                new BigDecimal(balance),
                " ".repeat(50));
    }

    static Account account(String accountId, String groupId, String balance) {
        Account account = new Account();
        account.raw = " ".repeat(300);
        account.id = accountId;
        account.status = "Y";
        account.balance = new BigDecimal(balance);
        account.creditLimit = new BigDecimal("1000.00");
        account.cashCreditLimit = new BigDecimal("500.00");
        account.openDate = "2020-01-01";
        account.expirationDate = "2030-01-01";
        account.reissueDate = "2030-01-01";
        account.currentCredit = new BigDecimal("12.34");
        account.currentDebit = new BigDecimal("56.78");
        account.zip = "1234567890";
        account.groupId = groupId;
        return account;
    }

    static DiscGroup group(String groupId, String type, String category, String rate) {
        return new DiscGroup(
                new DiscKey(groupId, type, category),
                new BigDecimal(rate),
                " ".repeat(50));
    }

    static Xref xref(String accountId, String cardNumber) {
        return new Xref(cardNumber, "000000001", accountId, " ".repeat(50));
    }

    static List<String> lines(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.US_ASCII);
    }

    private static Path write(Path file, List<String> lines) throws IOException {
        Files.write(file, lines, StandardCharsets.US_ASCII);
        return file;
    }
}
