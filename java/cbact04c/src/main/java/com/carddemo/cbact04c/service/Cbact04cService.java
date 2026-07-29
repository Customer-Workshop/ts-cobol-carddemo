package com.carddemo.cbact04c.service;

import com.carddemo.cbact04c.domain.Records.Account;
import com.carddemo.cbact04c.domain.Records.DiscGroup;
import com.carddemo.cbact04c.domain.Records.DiscKey;
import com.carddemo.cbact04c.domain.Records.TranCat;
import com.carddemo.cbact04c.domain.Records.Transaction;
import com.carddemo.cbact04c.domain.Records.Xref;
import com.carddemo.cbact04c.io.FileGateways;
import com.carddemo.cbact04c.io.FileGateways.AccountGateway;
import com.carddemo.cbact04c.io.FileGateways.TransactionGateway;
import com.carddemo.cbact04c.io.RecordCodecs;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class Cbact04cService {

    private static final String INITIAL_ACCOUNT = "           ";
    private static final DateTimeFormatter DB2_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SS");

    private final Clock clock;

    private Account currentAccount;
    private Xref currentXref;
    private DiscGroup disclosureGroup;
    private BigDecimal totalInterest = BigDecimal.ZERO.setScale(2);
    private int transactionSuffix;
    private int recordCount;

    public Cbact04cService(Clock clock) {
        this.clock = clock;
    }

    public BatchResult run(BatchJob job) {
        try {
            display("START OF EXECUTION OF PROGRAM CBACT04C");
            currentParmDate = job.parmDate();

            List<TranCat> transactionCategories = loadTransactionCategories(job.tcatbal());
            Map<String, Xref> xrefs = FileGateways.readXrefs(job.xref());
            Map<DiscKey, DiscGroup> disclosureGroups =
                    FileGateways.readDisclosureGroups(job.discgrp());
            AccountGateway accounts = FileGateways.openAccounts(job.account());

            String lastAccount = INITIAL_ACCOUNT;
            try (TransactionGateway transactions =
                         new TransactionGateway(job.transact())) {
                Iterator<TranCat> nextRecord = getNextTranCatBal(transactionCategories);
                while (nextRecord.hasNext()) {
                    TranCat transactionCategory = nextRecord.next();
                    recordCount++;
                    display(transactionCategory.raw());

                    if (!transactionCategory.acctId().equals(lastAccount)) {
                        if (currentAccount != null) {
                            updateAccount(accounts);
                        }
                        totalInterest = BigDecimal.ZERO.setScale(2);
                        lastAccount = transactionCategory.acctId();
                        currentAccount = getAcctData(accounts, transactionCategory.acctId());
                        currentXref = getXrefData(xrefs, transactionCategory.acctId());
                    }

                    disclosureGroup = getInterestRate(
                            disclosureGroups,
                            currentAccount.groupId,
                            transactionCategory.typeCd(),
                            transactionCategory.catCd());
                    if (disclosureGroup.rate().compareTo(BigDecimal.ZERO) != 0) {
                        computeInterest(transactionCategory, transactions);
                        computeFees();
                    }
                }

                if (job.finalUpdateAtEof() && currentAccount != null) {
                    updateAccount(accounts);
                }
            }

            display("END OF EXECUTION OF PROGRAM CBACT04C");
            return new BatchResult(recordCount, transactionSuffix);
        } catch (AbendException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AbendException("ABENDING PROGRAM", exception);
        } finally {
            resetState();
        }
    }

    public Iterator<TranCat> getNextTranCatBal(List<TranCat> records) {
        return records.stream()
                .sorted(Comparator.comparing(TranCat::acctId)
                        .thenComparing(TranCat::typeCd)
                        .thenComparing(TranCat::catCd))
                .iterator();
    }

    public void updateAccount(AccountGateway accounts) throws IOException {
        currentAccount.balance = currentAccount.balance.add(totalInterest);
        currentAccount.currentCredit = BigDecimal.ZERO.setScale(2);
        currentAccount.currentDebit = BigDecimal.ZERO.setScale(2);
        accounts.rewrite(currentAccount);
    }

    public Account getAcctData(AccountGateway accounts, String accountId) {
        Account account = accounts.find(accountId);
        if (account == null) {
            display("ACCOUNT NOT FOUND: " + accountId);
            abend("ERROR READING ACCOUNT FILE");
        }
        return account;
    }

    public Xref getXrefData(Map<String, Xref> xrefs, String accountId) {
        Xref xref = xrefs.get(accountId);
        if (xref == null) {
            display("ACCOUNT NOT FOUND: " + accountId);
            abend("ERROR READING XREF FILE");
        }
        return xref;
    }

    public DiscGroup getInterestRate(
            Map<DiscKey, DiscGroup> disclosureGroups,
            String accountGroupId,
            String typeCode,
            String categoryCode) {
        DiscKey requestedKey = new DiscKey(accountGroupId, typeCode, categoryCode);
        DiscGroup found = disclosureGroups.get(requestedKey);
        if (found != null) {
            disclosureGroup = found;
            return found;
        }

        display("DISCLOSURE GROUP RECORD MISSING");
        display("TRY WITH DEFAULT GROUP CODE");
        DiscKey defaultKey = new DiscKey("DEFAULT   ", typeCode, categoryCode);
        DiscGroup defaultGroup = disclosureGroups.get(defaultKey);
        if (defaultGroup == null) {
            abend("ERROR READING DEFAULT DISCLOSURE GROUP");
        }
        disclosureGroup = defaultGroup;
        return defaultGroup;
    }

    public BigDecimal computeInterest(
            TranCat transactionCategory,
            TransactionGateway transactions) throws IOException {
        BigDecimal monthlyInterest = transactionCategory.balance()
                .multiply(disclosureGroup.rate())
                .divide(BigDecimal.valueOf(1200), 20, RoundingMode.DOWN)
                .setScale(2, RoundingMode.DOWN);
        totalInterest = totalInterest.add(monthlyInterest);
        writeTransaction(transactionCategory, monthlyInterest, transactions);
        return monthlyInterest;
    }

    /**
     * 1400-COMPUTE-FEES is "To be implemented" in the original COBOL program.
     */
    public void computeFees() {
        // Intentionally empty: 1400-COMPUTE-FEES is "To be implemented".
    }

    public String formatTimestamp() {
        return LocalDateTime.now(clock).format(DB2_TIMESTAMP) + "0000";
    }

    private List<TranCat> loadTransactionCategories(Path path) throws IOException {
        return FileGateways.readLines(path).stream()
                .map(RecordCodecs::decodeTranCat)
                .toList();
    }

    private void writeTransaction(
            TranCat transactionCategory,
            BigDecimal amount,
            TransactionGateway transactions) throws IOException {
        transactionSuffix++;
        Transaction transaction = new Transaction();
        transaction.id = transactionId(transactionSuffix);
        transaction.typeCd = "01";
        transaction.catCd = "05";
        transaction.source = "System";
        transaction.description = "Int. for a/c " + currentAccount.id;
        transaction.amount = amount;
        transaction.merchantId = "0";
        transaction.merchantName = "";
        transaction.merchantCity = "";
        transaction.merchantZip = "";
        transaction.cardNum = currentXref.cardNum();
        transaction.origTs = formatTimestamp();
        transaction.procTs = transaction.origTs;
        transactions.write(RecordCodecs.encodeTransaction(transaction));
    }

    private String transactionId(int suffix) {
        return currentParmDate + String.format("%06d", suffix);
    }

    private String currentParmDate;

    private void resetState() {
        currentAccount = null;
        currentXref = null;
        disclosureGroup = null;
        totalInterest = BigDecimal.ZERO.setScale(2);
        transactionSuffix = 0;
        recordCount = 0;
        currentParmDate = null;
    }

    private void abend(String message) {
        display(message);
        display("ABENDING PROGRAM");
        throw new AbendException(message);
    }

    private void display(String message) {
        System.out.println(message);
    }
}
