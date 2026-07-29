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

    public Cbact04cService(Clock clock) {
        this.clock = clock;
    }

    public BatchResult run(BatchJob job) {
        RunState state = new RunState(job.parmDate());
        try {
            display("START OF EXECUTION OF PROGRAM CBACT04C");

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
                    state.recordCount++;
                    display(transactionCategory.raw());

                    if (!transactionCategory.acctId().equals(lastAccount)) {
                        if (state.currentAccount != null) {
                            updateAccount(state, accounts);
                        }
                        state.totalInterest = BigDecimal.ZERO.setScale(2);
                        lastAccount = transactionCategory.acctId();
                        state.currentAccount = getAcctData(
                                accounts, transactionCategory.acctId());
                        state.currentXref = getXrefData(
                                xrefs, transactionCategory.acctId());
                    }

                    state.disclosureGroup = getInterestRate(
                            disclosureGroups,
                            state.currentAccount.groupId,
                            transactionCategory.typeCd(),
                            transactionCategory.catCd());
                    if (state.disclosureGroup.rate().compareTo(BigDecimal.ZERO) != 0) {
                        computeInterest(state, transactionCategory, transactions);
                        computeFees();
                    }
                }

                if (job.finalUpdateAtEof() && state.currentAccount != null) {
                    updateAccount(state, accounts);
                }
            }

            display("END OF EXECUTION OF PROGRAM CBACT04C");
            return new BatchResult(state.recordCount, state.transactionSuffix);
        } catch (AbendException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AbendException("ABENDING PROGRAM", exception);
        }
    }

    public Iterator<TranCat> getNextTranCatBal(List<TranCat> records) {
        return records.stream()
                .sorted(Comparator.comparing(TranCat::acctId)
                        .thenComparing(TranCat::typeCd)
                        .thenComparing(TranCat::catCd))
                .iterator();
    }

    public void updateAccount(RunState state, AccountGateway accounts) throws IOException {
        state.currentAccount.balance =
                state.currentAccount.balance.add(state.totalInterest);
        state.currentAccount.currentCredit = BigDecimal.ZERO.setScale(2);
        state.currentAccount.currentDebit = BigDecimal.ZERO.setScale(2);
        accounts.rewrite(state.currentAccount);
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
            return found;
        }

        display("DISCLOSURE GROUP RECORD MISSING");
        display("TRY WITH DEFAULT GROUP CODE");
        DiscKey defaultKey = new DiscKey("DEFAULT   ", typeCode, categoryCode);
        DiscGroup defaultGroup = disclosureGroups.get(defaultKey);
        if (defaultGroup == null) {
            abend("ERROR READING DEFAULT DISCLOSURE GROUP");
        }
        return defaultGroup;
    }

    public BigDecimal computeInterest(
            RunState state,
            TranCat transactionCategory,
            TransactionGateway transactions) throws IOException {
        BigDecimal monthlyInterest = transactionCategory.balance()
                .multiply(state.disclosureGroup.rate())
                .divide(BigDecimal.valueOf(1200), 20, RoundingMode.DOWN)
                .setScale(2, RoundingMode.DOWN);
        state.totalInterest = state.totalInterest.add(monthlyInterest);
        writeTransaction(state, monthlyInterest, transactions);
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
            RunState state,
            BigDecimal amount,
            TransactionGateway transactions) throws IOException {
        state.transactionSuffix++;
        Transaction transaction = new Transaction();
        transaction.id = state.parmDate
                + String.format("%06d", state.transactionSuffix);
        transaction.typeCd = "01";
        transaction.catCd = "05";
        transaction.source = "System";
        transaction.description = "Int. for a/c " + state.currentAccount.id;
        transaction.amount = amount;
        transaction.merchantId = "0";
        transaction.merchantName = "";
        transaction.merchantCity = "";
        transaction.merchantZip = "";
        transaction.cardNum = state.currentXref.cardNum();
        transaction.origTs = formatTimestamp();
        transaction.procTs = transaction.origTs;
        transactions.write(RecordCodecs.encodeTransaction(transaction));
    }

    private void abend(String message) {
        display(message);
        display("ABENDING PROGRAM");
        throw new AbendException(message);
    }

    private void display(String message) {
        System.out.println(message);
    }

    private static final class RunState {

        private final String parmDate;
        private Account currentAccount;
        private Xref currentXref;
        private DiscGroup disclosureGroup;
        private BigDecimal totalInterest = BigDecimal.ZERO.setScale(2);
        private int transactionSuffix;
        private int recordCount;

        private RunState(String parmDate) {
            this.parmDate = parmDate;
        }
    }
}
