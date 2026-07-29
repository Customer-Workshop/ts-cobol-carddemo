package com.carddemo.cbtrn02c.repo;

import com.carddemo.cbtrn02c.domain.AccountRecord;
import com.carddemo.cbtrn02c.domain.CardXrefRecord;
import com.carddemo.cbtrn02c.domain.DalyTranRecord;
import com.carddemo.cbtrn02c.domain.TranCatBalRecord;
import com.carddemo.cbtrn02c.domain.TranRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class BatchFiles {
    public final SequentialInputFile<DalyTranRecord> dailyTransactions;
    public final IndexedFile<String, TranRecord> transactions;
    public final IndexedFile<String, CardXrefRecord> cardXrefs;
    public final SequentialOutputFile<String> rejects;
    public final IndexedFile<String, AccountRecord> accounts;
    public final IndexedFile<String, TranCatBalRecord> categoryBalances;

    private final Path directory;

    public BatchFiles(Path directory) throws IOException {
        this.directory = directory;
        dailyTransactions = new SequentialInputFile<>(
                FixedFiles.readSequential(
                        directory.resolve("DALYTRAN"),
                        DalyTranRecord::parse));
        transactions = new IndexedFile<>(
                FixedFiles.readMap(
                        directory.resolve("TRANSACT"),
                        TranRecord::parse,
                        record -> record.id));
        cardXrefs = new IndexedFile<>(
                FixedFiles.readMap(
                        directory.resolve("XREF"),
                        CardXrefRecord::parse,
                        record -> record.cardNum));
        rejects = new SequentialOutputFile<>(
                Files.exists(directory.resolve("DALYREJS"))
                        ? Files.readAllLines(directory.resolve("DALYREJS"))
                        : new ArrayList<>());
        accounts = new IndexedFile<>(
                FixedFiles.readMap(
                        directory.resolve("ACCOUNT"),
                        AccountRecord::parse,
                        record -> record.acctId));
        categoryBalances = new IndexedFile<>(
                FixedFiles.readMap(
                        directory.resolve("TCATBAL"),
                        TranCatBalRecord::parse,
                        TranCatBalRecord::key));
    }

    public void save() throws IOException {
        FixedFiles.writeMap(
                directory.resolve("TRANSACT"),
                transactions.records(),
                TranRecord::format);
        FixedFiles.writeMap(
                directory.resolve("XREF"),
                cardXrefs.records(),
                CardXrefRecord::format);
        FixedFiles.writeSequential(
                directory.resolve("DALYREJS"),
                rejects.records(),
                value -> value);
        FixedFiles.writeMap(
                directory.resolve("ACCOUNT"),
                accounts.records(),
                AccountRecord::format);
        FixedFiles.writeMap(
                directory.resolve("TCATBAL"),
                categoryBalances.records(),
                TranCatBalRecord::format);
    }
}
