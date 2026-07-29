package com.carddemo.cbact04c.io;

import com.carddemo.cbact04c.domain.Records.Account;
import com.carddemo.cbact04c.domain.Records.DiscGroup;
import com.carddemo.cbact04c.domain.Records.DiscKey;
import com.carddemo.cbact04c.domain.Records.Xref;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class FileGateways {

    private FileGateways() {
    }

    public static List<String> readLines(Path path) throws IOException {
        return Files.readAllLines(path, StandardCharsets.US_ASCII);
    }

    public static Map<String, Xref> readXrefs(Path path) throws IOException {
        Map<String, Xref> recordsByAccount = new HashMap<>();
        for (String line : readLines(path)) {
            Xref xref = RecordCodecs.decodeXref(line);
            recordsByAccount.put(xref.acctId(), xref);
        }
        return recordsByAccount;
    }

    public static AccountGateway openAccounts(Path path) throws IOException {
        return new AccountGateway(path, readLines(path));
    }

    public static Map<DiscKey, DiscGroup> readDisclosureGroups(Path path) throws IOException {
        Map<DiscKey, DiscGroup> recordsByKey = new HashMap<>();
        for (String line : readLines(path)) {
            DiscGroup group = RecordCodecs.decodeDiscGroup(line);
            recordsByKey.put(group.key(), group);
        }
        return recordsByKey;
    }

    public static final class AccountGateway {

        private final Path path;
        private final List<String> rawRecords;
        private final Map<String, Account> recordsById = new TreeMap<>();

        private AccountGateway(Path path, List<String> rawRecords) {
            this.path = path;
            this.rawRecords = new ArrayList<>(rawRecords);
            for (String rawRecord : rawRecords) {
                Account account = RecordCodecs.decodeAccount(rawRecord);
                recordsById.put(account.id, account);
            }
        }

        public Account find(String accountId) {
            return recordsById.get(accountId);
        }

        public Collection<Account> records() {
            return recordsById.values();
        }

        public void rewrite(Account account) throws IOException {
            recordsById.put(account.id, account);
            for (int index = 0; index < rawRecords.size(); index++) {
                if (RecordCodecs.decodeAccount(rawRecords.get(index)).id.equals(account.id)) {
                    rawRecords.set(index, RecordCodecs.encodeAccount(account));
                    break;
                }
            }
            writeThrough();
        }

        private void writeThrough() throws IOException {
            Files.write(
                    path,
                    rawRecords,
                    StandardCharsets.US_ASCII,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        }
    }

    public static final class TransactionGateway implements AutoCloseable {

        private final BufferedWriter writer;
        private int count;

        public TransactionGateway(Path path) throws IOException {
            writer = Files.newBufferedWriter(
                    path,
                    StandardCharsets.US_ASCII,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        }

        public void write(String record) throws IOException {
            writer.write(record);
            writer.newLine();
            writer.flush();
            count++;
        }

        public int count() {
            return count;
        }

        @Override
        public void close() throws IOException {
            writer.close();
        }
    }
}
