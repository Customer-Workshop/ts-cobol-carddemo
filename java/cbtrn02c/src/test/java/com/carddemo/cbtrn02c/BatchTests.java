package com.carddemo.cbtrn02c;

import com.carddemo.cbtrn02c.domain.AccountRecord;
import com.carddemo.cbtrn02c.domain.CardXrefRecord;
import com.carddemo.cbtrn02c.domain.DalyTranRecord;
import com.carddemo.cbtrn02c.domain.FixedWidth;
import com.carddemo.cbtrn02c.domain.TranCatBalRecord;
import com.carddemo.cbtrn02c.domain.TranRecord;
import com.carddemo.cbtrn02c.repo.BatchFiles;
import com.carddemo.cbtrn02c.repo.IndexedFile;
import com.carddemo.cbtrn02c.repo.SequentialInputFile;
import com.carddemo.cbtrn02c.repo.SequentialOutputFile;
import com.carddemo.cbtrn02c.service.TransactionPosterService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchTests {
    private static final String CARD = "CARD000000000001";
    private static final String ACCOUNT_ID = "00000000001";

    @Test
    void signedNumbersRoundTripNegativeNine() {
        BigDecimal amount = new BigDecimal("-1.99");
        String formatted = FixedWidth.signedNumber(amount, 11);

        assertEquals('R', formatted.charAt(10));
        assertEquals(amount, FixedWidth.parseSignedNumber(formatted, 11));
    }

    @Test
    void tranRecordFieldsUseExactOffsets() {
        TranRecord record = transaction("TRANSACTION-ID1", new BigDecimal("12.34"));
        record.procTs = "PROC-TIMESTAMP-00000000000";
        String formatted = record.format();

        assertEquals(350, formatted.length());
        assertField(formatted, 0, 16, record.id);
        assertField(formatted, 16, 18, record.typeCd);
        assertField(formatted, 18, 22, record.catCd);
        assertField(formatted, 22, 32, record.source);
        assertField(formatted, 32, 132, record.desc);
        assertField(formatted, 132, 143, FixedWidth.signedNumber(record.amt, 11));
        assertField(formatted, 143, 152, record.merchantId);
        assertField(formatted, 152, 202, record.merchantName);
        assertField(formatted, 202, 252, record.merchantCity);
        assertField(formatted, 252, 262, record.merchantZip);
        assertField(formatted, 262, 278, record.cardNum);
        assertField(formatted, 278, 304, record.origTs);
        assertField(formatted, 304, 330, record.procTs);
        assertField(formatted, 330, 350, record.filler);
    }

    @Test
    void dailyTranRecordFieldsUseExactOffsets() {
        DalyTranRecord record = dailyTransaction("DAILY-TRAN-ID01", new BigDecimal("-1.99"));
        String formatted = record.format();

        assertEquals(350, formatted.length());
        assertField(formatted, 0, 16, record.id);
        assertField(formatted, 16, 18, record.typeCd);
        assertField(formatted, 18, 22, record.catCd);
        assertField(formatted, 22, 32, record.source);
        assertField(formatted, 32, 132, record.desc);
        assertField(formatted, 132, 143, FixedWidth.signedNumber(record.amt, 11));
        assertField(formatted, 143, 152, record.merchantId);
        assertField(formatted, 152, 202, record.merchantName);
        assertField(formatted, 202, 252, record.merchantCity);
        assertField(formatted, 252, 262, record.merchantZip);
        assertField(formatted, 262, 278, record.cardNum);
        assertField(formatted, 278, 304, record.origTs);
        assertField(formatted, 304, 330, record.procTs);
        assertField(formatted, 330, 350, record.filler);
    }

    @Test
    void cardXrefFieldsUseExactOffsets() {
        CardXrefRecord record = new CardXrefRecord();
        record.cardNum = CARD;
        record.custId = "123456789";
        record.acctId = ACCOUNT_ID;
        record.filler = "XREF-FILLER";
        String formatted = record.format();

        assertEquals(50, formatted.length());
        assertField(formatted, 0, 16, record.cardNum);
        assertField(formatted, 16, 25, record.custId);
        assertField(formatted, 25, 36, record.acctId);
        assertField(formatted, 36, 50, record.filler);
    }

    @Test
    void accountFieldsUseExactOffsets() {
        AccountRecord record = account(ACCOUNT_ID, new BigDecimal("1000"), "2025-12-31");
        record.activeStatus = "A";
        record.currBal = new BigDecimal("12.34");
        record.cashCreditLimit = new BigDecimal("50.00");
        record.openDate = "2020-01-01";
        record.reissueDate = "2024-01-01";
        record.currCycCredit = new BigDecimal("20.00");
        record.currCycDebit = new BigDecimal("-3.00");
        record.addrZip = "1234567890";
        record.groupId = "GROUP-001";
        String formatted = record.format();

        assertEquals(300, formatted.length());
        assertField(formatted, 0, 11, record.acctId);
        assertField(formatted, 11, 12, record.activeStatus);
        assertField(formatted, 12, 24, FixedWidth.signedNumber(record.currBal, 12));
        assertField(formatted, 24, 36, FixedWidth.signedNumber(record.creditLimit, 12));
        assertField(formatted, 36, 48, FixedWidth.signedNumber(record.cashCreditLimit, 12));
        assertField(formatted, 48, 58, record.openDate);
        assertField(formatted, 58, 68, record.expirationDate);
        assertField(formatted, 68, 78, record.reissueDate);
        assertField(formatted, 78, 90, FixedWidth.signedNumber(record.currCycCredit, 12));
        assertField(formatted, 90, 102, FixedWidth.signedNumber(record.currCycDebit, 12));
        assertField(formatted, 102, 112, record.addrZip);
        assertField(formatted, 112, 122, record.groupId);
        assertField(formatted, 122, 300, record.filler);
    }

    @Test
    void categoryBalanceFieldsUseExactOffsets() {
        TranCatBalRecord record = categoryBalance(new BigDecimal("-1.99"));
        record.filler = "CATEGORY-FILLER";
        String formatted = record.format();

        assertEquals(50, formatted.length());
        assertField(formatted, 0, 11, record.acctId);
        assertField(formatted, 11, 13, record.typeCd);
        assertField(formatted, 13, 17, record.catCd);
        assertField(formatted, 17, 28, FixedWidth.signedNumber(record.tranCatBal, 11));
        assertField(formatted, 28, 50, record.filler);
    }

    @Test
    void validPostUpdatesTransactionAccountAndCategoryBalance() throws Exception {
        DalyTranRecord daily = dailyTransaction("TRANSACTION-001", new BigDecimal("12.34"));
        BatchFiles files = batch(List.of(daily), List.of(xref(CARD, ACCOUNT_ID)),
                List.of(account(ACCOUNT_ID, new BigDecimal("1000"), "2025-12-31")),
                List.of());

        TransactionPosterService.Result result = run(files);

        assertEquals(new TransactionPosterService.Result(1, 0, 0), result);
        assertEquals(1, files.transactions.size());
        assertEquals(new BigDecimal("12.34"),
                files.transactions.read(daily.id).orElseThrow().amt);
        assertEquals(new BigDecimal("12.34"),
                files.accounts.read(ACCOUNT_ID).orElseThrow().currBal);
        assertEquals(new BigDecimal("12.34"),
                files.categoryBalances.read(ACCOUNT_ID + "PU0001")
                        .orElseThrow().tranCatBal);
    }

    @Test
    void invalidCardProducesReason100() throws Exception {
        BatchFiles files = batch(
                List.of(dailyTransaction("TRANSACTION-001", BigDecimal.ONE)),
                List.of(),
                List.of(),
                List.of());

        run(files);

        assertReject(files, "0100", "INVALID CARD NUMBER FOUND");
    }

    @Test
    void missingAccountProducesReason101() throws Exception {
        BatchFiles files = batch(
                List.of(dailyTransaction("TRANSACTION-001", BigDecimal.ONE)),
                List.of(xref(CARD, "00000000099")),
                List.of(),
                List.of());

        run(files);

        assertReject(files, "0101", "ACCOUNT RECORD NOT FOUND");
    }

    @Test
    void overlimitOnlyProducesReason102() throws Exception {
        BatchFiles files = batch(
                List.of(dailyTransaction("TRANSACTION-001", new BigDecimal("10.00"))),
                List.of(xref(CARD, ACCOUNT_ID)),
                List.of(account(ACCOUNT_ID, new BigDecimal("5.00"), "2025-12-31")),
                List.of());

        run(files);

        assertReject(files, "0102", "OVERLIMIT TRANSACTION");
    }

    @Test
    void expiredOnlyProducesReason103() throws Exception {
        BatchFiles files = batch(
                List.of(dailyTransaction("TRANSACTION-001", BigDecimal.ONE)),
                List.of(xref(CARD, ACCOUNT_ID)),
                List.of(account(ACCOUNT_ID, new BigDecimal("1000"), "2020-01-01")),
                List.of());

        run(files);

        assertReject(files, "0103", "TRANSACTION RECEIVED AFTER ACCT EXPIRATION");
    }

    @Test
    void expirationOverwritesEarlierOverlimitReason() throws Exception {
        BatchFiles files = batch(
                List.of(dailyTransaction("TRANSACTION-001", new BigDecimal("10.00"))),
                List.of(xref(CARD, ACCOUNT_ID)),
                List.of(account(ACCOUNT_ID, new BigDecimal("5.00"), "2020-01-01")),
                List.of());

        run(files);

        assertReject(files, "0103", "TRANSACTION RECEIVED AFTER ACCT EXPIRATION");
    }

    @Test
    void categoryBalanceCreatesThenRewritesExistingKey() throws Exception {
        DalyTranRecord first = dailyTransaction("TRANSACTION-001", new BigDecimal("10.00"));
        DalyTranRecord second = dailyTransaction("TRANSACTION-002", new BigDecimal("7.50"));
        BatchFiles files = batch(
                List.of(first, second),
                List.of(xref(CARD, ACCOUNT_ID)),
                List.of(account(ACCOUNT_ID, new BigDecimal("1000"), "2025-12-31")),
                List.of());

        TransactionPosterService.Result result = run(files);

        assertEquals(2, result.processed());
        assertEquals(new BigDecimal("17.50"),
                files.categoryBalances.read(ACCOUNT_ID + "PU0001")
                        .orElseThrow().tranCatBal);
    }

    @Test
    void accountTracksCreditAndDebitSeparately() throws Exception {
        DalyTranRecord credit = dailyTransaction("TRANSACTION-001", new BigDecimal("10.00"));
        DalyTranRecord debit = dailyTransaction("TRANSACTION-002", new BigDecimal("-3.00"));
        BatchFiles files = batch(
                List.of(credit, debit),
                List.of(xref(CARD, ACCOUNT_ID)),
                List.of(account(ACCOUNT_ID, new BigDecimal("1000"), "2025-12-31")),
                List.of());

        run(files);

        AccountRecord updated = files.accounts.read(ACCOUNT_ID).orElseThrow();
        assertEquals(new BigDecimal("10.00"), updated.currCycCredit);
        assertEquals(new BigDecimal("-3.00"), updated.currCycDebit);
        assertEquals(new BigDecimal("7.00"), updated.currBal);
    }

    @Test
    void rejectRecordIsExactly430BytesWithPaddedTrailer() throws Exception {
        DalyTranRecord daily = dailyTransaction("TRANSACTION-001", BigDecimal.ONE);
        BatchFiles files = batch(List.of(daily), List.of(), List.of(), List.of());

        run(files);

        String reject = files.rejects.records().get(0);
        assertEquals(430, reject.length());
        assertEquals(daily.format(), reject.substring(0, 350));
        assertEquals("0100", reject.substring(350, 354));
        assertEquals(76, reject.substring(354, 430).length());
        assertTrue(reject.substring(354).startsWith("INVALID CARD NUMBER FOUND"));
        assertEquals(' ', reject.charAt(429));
    }

    @Test
    void exitCodeIsFourWhenThereAreRejects() throws Exception {
        BatchFiles files = batch(
                List.of(dailyTransaction("TRANSACTION-001", BigDecimal.ONE)),
                List.of(), List.of(), List.of());

        TransactionPosterService.Result result = run(files);

        assertEquals(4, result.exitCode());
    }

    @Test
    void exitCodeIsZeroWhenThereAreNoRejects() throws Exception {
        BatchFiles files = batch(
                List.of(dailyTransaction("TRANSACTION-001", BigDecimal.ONE)),
                List.of(xref(CARD, ACCOUNT_ID)),
                List.of(account(ACCOUNT_ID, new BigDecimal("1000"), "2025-12-31")),
                List.of());

        TransactionPosterService.Result result = run(files);

        assertEquals(0, result.exitCode());
    }

    @Test
    void eofAndCountersReflectEveryDailyTransaction() throws Exception {
        BatchFiles files = batch(
                List.of(
                        dailyTransaction("TRANSACTION-001", BigDecimal.ONE),
                        dailyTransaction("TRANSACTION-002", BigDecimal.ONE)),
                List.of(), List.of(), List.of());

        TransactionPosterService.Result result = run(files);

        assertEquals(2, result.processed());
        assertEquals(2, result.rejected());
        assertTrue(files.dailyTransactions.eof());
    }

    @Test
    void indexedFileEnforcesWriteAndRewriteSemantics() {
        IndexedFile<String, String> file = new IndexedFile<>();

        file.write("A", "one");
        assertEquals("one", file.read("A").orElseThrow());
        assertThrows(IllegalStateException.class, () -> file.write("A", "again"));
        assertThrows(IllegalStateException.class, () -> file.rewrite("B", "missing"));
        file.rewrite("A", "updated");
        assertEquals("updated", file.read("A").orElseThrow());
    }

    @Test
    void sequentialGatewaysReadUntilEofAndAppendWrites() {
        SequentialInputFile<String> input =
                new SequentialInputFile<>(List.of("first", "second"));
        SequentialOutputFile<String> output = new SequentialOutputFile<>();

        assertEquals("first", input.readNext());
        assertEquals("second", input.readNext());
        assertTrue(input.eof());
        assertEquals(null, input.readNext());
        output.write("one");
        output.write("two");
        assertEquals(List.of("one", "two"), output.records());
    }

    private static TransactionPosterService.Result run(BatchFiles files) {
        return new TransactionPosterService().run(files);
    }

    private static void assertReject(
            BatchFiles files,
            String reason,
            String description) {
        String reject = files.rejects.records().get(0);
        assertEquals(reason, reject.substring(350, 354));
        assertTrue(reject.substring(354, 430).startsWith(description));
    }

    private static void assertField(
            String formatted,
            int start,
            int end,
            String expected) {
        assertEquals(
                FixedWidth.text(expected, end - start),
                formatted.substring(start, end));
    }

    private static BatchFiles batch(
            List<DalyTranRecord> dailyTransactions,
            List<CardXrefRecord> xrefs,
            List<AccountRecord> accounts,
            List<TranCatBalRecord> categoryBalances) throws Exception {
        Path directory = Files.createTempDirectory("cbtrn02c-test");
        FixedFilesForTests.write(directory.resolve("DALYTRAN"), dailyTransactions,
                DalyTranRecord::format);
        FixedFilesForTests.write(directory.resolve("XREF"), xrefs,
                CardXrefRecord::format);
        FixedFilesForTests.write(directory.resolve("ACCOUNT"), accounts,
                AccountRecord::format);
        FixedFilesForTests.write(directory.resolve("TCATBAL"), categoryBalances,
                TranCatBalRecord::format);
        return new BatchFiles(directory);
    }

    private static DalyTranRecord dailyTransaction(String id, BigDecimal amount) {
        DalyTranRecord record = new DalyTranRecord();
        copyTransactionFields(record, transaction(id, amount));
        return record;
    }

    private static TranRecord transaction(String id, BigDecimal amount) {
        TranRecord record = new TranRecord();
        record.id = FixedWidth.text(id, 16);
        record.typeCd = "PU";
        record.catCd = "0001";
        record.source = "TEST";
        record.desc = "A transaction description";
        record.amt = amount;
        record.merchantId = "123456789";
        record.merchantName = "Merchant";
        record.merchantCity = "City";
        record.merchantZip = "1234567890";
        record.cardNum = CARD;
        record.origTs = "2024-01-02-00.00.00.000000";
        record.procTs = "2024-01-02-00.00.00.000000";
        record.filler = "FILLER";
        return record;
    }

    private static void copyTransactionFields(
            DalyTranRecord target,
            TranRecord source) {
        target.id = source.id;
        target.typeCd = source.typeCd;
        target.catCd = source.catCd;
        target.source = source.source;
        target.desc = source.desc;
        target.amt = source.amt;
        target.merchantId = source.merchantId;
        target.merchantName = source.merchantName;
        target.merchantCity = source.merchantCity;
        target.merchantZip = source.merchantZip;
        target.cardNum = source.cardNum;
        target.origTs = source.origTs;
        target.procTs = source.procTs;
        target.filler = source.filler;
    }

    private static CardXrefRecord xref(String cardNumber, String accountId) {
        CardXrefRecord record = new CardXrefRecord();
        record.cardNum = cardNumber;
        record.custId = "123456789";
        record.acctId = accountId;
        return record;
    }

    private static AccountRecord account(
            String accountId,
            BigDecimal creditLimit,
            String expirationDate) {
        AccountRecord record = new AccountRecord();
        record.acctId = accountId;
        record.activeStatus = "A";
        record.creditLimit = creditLimit;
        record.expirationDate = expirationDate;
        return record;
    }

    private static TranCatBalRecord categoryBalance(BigDecimal amount) {
        TranCatBalRecord record = new TranCatBalRecord();
        record.acctId = ACCOUNT_ID;
        record.typeCd = "PU";
        record.catCd = "0001";
        record.tranCatBal = amount;
        return record;
    }

    private static final class FixedFilesForTests {
        private static <T> void write(
                Path path,
                List<T> records,
                java.util.function.Function<T, String> formatter)
                throws Exception {
            Files.writeString(
                    path,
                    records.stream()
                            .map(formatter)
                            .reduce("", (left, right) ->
                                    left.isEmpty() ? right + "\n" : left + right + "\n"));
        }
    }
}
