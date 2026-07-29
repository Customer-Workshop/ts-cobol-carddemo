package com.carddemo.cbact04c.io;

import com.carddemo.cbact04c.domain.Records.Account;
import com.carddemo.cbact04c.domain.Records.DiscGroup;
import com.carddemo.cbact04c.domain.Records.DiscKey;
import com.carddemo.cbact04c.domain.Records.TranCat;
import com.carddemo.cbact04c.domain.Records.Transaction;
import com.carddemo.cbact04c.domain.Records.Xref;
import com.carddemo.cbact04c.util.CobolField;
import com.carddemo.cbact04c.util.ZonedDecimal;

public final class RecordCodecs {

    private RecordCodecs() {
    }

    public static TranCat decodeTranCat(String record) {
        String padded = FixedWidth.pad(record, 50);
        return new TranCat(
                FixedWidth.at(padded, 0, 11),
                FixedWidth.at(padded, 11, 2),
                FixedWidth.at(padded, 13, 4),
                ZonedDecimal.parse(FixedWidth.at(padded, 17, 11)),
                padded);
    }

    public static Xref decodeXref(String record) {
        String padded = FixedWidth.pad(record, 50);
        return new Xref(
                FixedWidth.at(padded, 0, 16),
                FixedWidth.at(padded, 16, 9),
                FixedWidth.at(padded, 25, 11),
                padded);
    }

    public static String encodeTranCat(TranCat record) {
        StringBuilder encoded = new StringBuilder(record.raw());
        put(encoded, 0, CobolField.digits(record.acctId(), 11));
        put(encoded, 11, CobolField.text(record.typeCd(), 2));
        put(encoded, 13, CobolField.digits(record.catCd(), 4));
        put(encoded, 17, ZonedDecimal.format(record.balance(), 9));
        return encoded.toString();
    }

    public static String encodeXref(Xref record) {
        StringBuilder encoded = new StringBuilder(record.raw());
        put(encoded, 0, CobolField.text(record.cardNum(), 16));
        put(encoded, 16, CobolField.digits(record.custId(), 9));
        put(encoded, 25, CobolField.digits(record.acctId(), 11));
        return encoded.toString();
    }

    public static DiscGroup decodeDiscGroup(String record) {
        String padded = FixedWidth.pad(record, 50);
        DiscKey key = new DiscKey(
                FixedWidth.at(padded, 0, 10),
                FixedWidth.at(padded, 10, 2),
                FixedWidth.at(padded, 12, 4));
        return new DiscGroup(key, ZonedDecimal.parse(FixedWidth.at(padded, 16, 6)), padded);
    }

    public static String encodeDiscGroup(DiscGroup record) {
        StringBuilder encoded = new StringBuilder(record.raw());
        put(encoded, 0, CobolField.text(record.key().groupId(), 10));
        put(encoded, 10, CobolField.text(record.key().typeCd(), 2));
        put(encoded, 12, CobolField.digits(record.key().catCd(), 4));
        put(encoded, 16, ZonedDecimal.format(record.rate(), 4));
        return encoded.toString();
    }

    public static Account decodeAccount(String record) {
        String padded = FixedWidth.pad(record, 300);
        Account account = new Account();
        account.raw = padded;
        account.id = FixedWidth.at(padded, 0, 11);
        account.status = FixedWidth.at(padded, 11, 1);
        account.balance = ZonedDecimal.parse(FixedWidth.at(padded, 12, 12));
        account.creditLimit = ZonedDecimal.parse(FixedWidth.at(padded, 24, 12));
        account.cashCreditLimit = ZonedDecimal.parse(FixedWidth.at(padded, 36, 12));
        account.openDate = FixedWidth.at(padded, 48, 10);
        account.expirationDate = FixedWidth.at(padded, 58, 10);
        account.reissueDate = FixedWidth.at(padded, 68, 10);
        account.currentCredit = ZonedDecimal.parse(FixedWidth.at(padded, 78, 12));
        account.currentDebit = ZonedDecimal.parse(FixedWidth.at(padded, 90, 12));
        account.zip = FixedWidth.at(padded, 102, 10);
        account.groupId = FixedWidth.at(padded, 112, 10);
        return account;
    }

    public static String encodeAccount(Account account) {
        StringBuilder record = new StringBuilder(
                account.raw == null ? FixedWidth.pad("", 300) : account.raw);
        put(record, 0, CobolField.digits(account.id, 11));
        put(record, 11, CobolField.text(account.status, 1));
        put(record, 12, ZonedDecimal.format(account.balance, 10));
        put(record, 24, ZonedDecimal.format(account.creditLimit, 10));
        put(record, 36, ZonedDecimal.format(account.cashCreditLimit, 10));
        put(record, 48, CobolField.text(account.openDate, 10));
        put(record, 58, CobolField.text(account.expirationDate, 10));
        put(record, 68, CobolField.text(account.reissueDate, 10));
        put(record, 78, ZonedDecimal.format(account.currentCredit, 10));
        put(record, 90, ZonedDecimal.format(account.currentDebit, 10));
        put(record, 102, CobolField.text(account.zip, 10));
        put(record, 112, CobolField.text(account.groupId, 10));
        return record.toString();
    }

    public static String encodeTransaction(Transaction transaction) {
        StringBuilder record = new StringBuilder(350);
        append(record, CobolField.text(transaction.id, 16));
        append(record, CobolField.text(transaction.typeCd, 2));
        append(record, CobolField.digits(transaction.catCd, 4));
        append(record, CobolField.text(transaction.source, 10));
        append(record, CobolField.text(transaction.description, 100));
        append(record, ZonedDecimal.format(transaction.amount, 9));
        append(record, CobolField.digits(transaction.merchantId, 9));
        append(record, CobolField.text(transaction.merchantName, 50));
        append(record, CobolField.text(transaction.merchantCity, 50));
        append(record, CobolField.text(transaction.merchantZip, 10));
        append(record, CobolField.text(transaction.cardNum, 16));
        append(record, CobolField.text(transaction.origTs, 26));
        append(record, CobolField.text(transaction.procTs, 26));
        append(record, CobolField.text("", 20));
        return FixedWidth.pad(record.toString(), 350);
    }

    private static void put(StringBuilder record, int offset, String value) {
        record.replace(offset, offset + value.length(), value);
    }

    private static void append(StringBuilder record, String value) {
        record.append(value);
    }
}
