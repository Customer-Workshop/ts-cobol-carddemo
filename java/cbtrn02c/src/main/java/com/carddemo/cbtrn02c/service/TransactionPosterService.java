package com.carddemo.cbtrn02c.service;

import com.carddemo.cbtrn02c.domain.AccountRecord;
import com.carddemo.cbtrn02c.domain.CardXrefRecord;
import com.carddemo.cbtrn02c.domain.DalyTranRecord;
import com.carddemo.cbtrn02c.domain.FixedWidth;
import com.carddemo.cbtrn02c.domain.TranCatBalRecord;
import com.carddemo.cbtrn02c.domain.TranRecord;
import com.carddemo.cbtrn02c.repo.BatchFiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TransactionPosterService {
    private static final DateTimeFormatter PROCESSING_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SS")
                    .withZone(ZoneId.systemDefault());

    public record Result(int processed, int rejected, int exitCode) {
    }

    public Result run(BatchFiles files) {
        int processed = 0;
        int rejected = 0;

        while (!files.dailyTransactions.eof()) {
            DalyTranRecord dailyTransaction = files.dailyTransactions.readNext();
            processed++;

            ValidationFailure failure = validate(dailyTransaction, files);
            if (failure.reasonCode() != 0) {
                rejected++;
                writeReject(dailyTransaction, failure, files);
            } else {
                post(dailyTransaction, failure.xref(), failure.account(), files);
            }
        }

        int exitCode = rejected > 0 ? 4 : 0;
        return new Result(processed, rejected, exitCode);
    }

    private ValidationFailure validate(
            DalyTranRecord dailyTransaction,
            BatchFiles files) {
        CardXrefRecord xref = files.cardXrefs
                .read(dailyTransaction.cardNum)
                .orElse(null);
        if (xref == null) {
            return new ValidationFailure(100, "INVALID CARD NUMBER FOUND", null, null);
        }

        AccountRecord account = files.accounts.read(xref.acctId).orElse(null);
        if (account == null) {
            return new ValidationFailure(101, "ACCOUNT RECORD NOT FOUND", xref, null);
        }

        int reasonCode = 0;
        String reasonDescription = "";
        BigDecimal temporaryBalance = account.currCycCredit
                .subtract(account.currCycDebit)
                .add(dailyTransaction.amt);

        if (account.creditLimit.compareTo(temporaryBalance) < 0) {
            reasonCode = 102;
            reasonDescription = "OVERLIMIT TRANSACTION";
        }
        if (account.expirationDate.compareTo(dailyTransaction.origTs.substring(0, 10)) < 0) {
            reasonCode = 103;
            reasonDescription = "TRANSACTION RECEIVED AFTER ACCT EXPIRATION";
        }
        return new ValidationFailure(reasonCode, reasonDescription, xref, account);
    }

    private void post(
            DalyTranRecord dailyTransaction,
            CardXrefRecord xref,
            AccountRecord account,
            BatchFiles files) {
        updateCategoryBalance(dailyTransaction, xref, files);
        updateAccount(dailyTransaction, account, files);

        TranRecord transaction = copyTransaction(dailyTransaction);
        transaction.procTs = PROCESSING_TIMESTAMP.format(Instant.now()) + "0000";
        files.transactions.write(transaction.id, transaction);
    }

    private void updateCategoryBalance(
            DalyTranRecord dailyTransaction,
            CardXrefRecord xref,
            BatchFiles files) {
        String key = xref.acctId + dailyTransaction.typeCd + dailyTransaction.catCd;
        TranCatBalRecord categoryBalance = files.categoryBalances.read(key).orElse(null);
        if (categoryBalance == null) {
            categoryBalance = new TranCatBalRecord();
            categoryBalance.acctId = xref.acctId;
            categoryBalance.typeCd = dailyTransaction.typeCd;
            categoryBalance.catCd = dailyTransaction.catCd;
            categoryBalance.tranCatBal = dailyTransaction.amt;
            files.categoryBalances.write(key, categoryBalance);
        } else {
            categoryBalance.tranCatBal = categoryBalance.tranCatBal.add(dailyTransaction.amt);
            files.categoryBalances.rewrite(key, categoryBalance);
        }
    }

    private void updateAccount(
            DalyTranRecord dailyTransaction,
            AccountRecord account,
            BatchFiles files) {
        account.currBal = account.currBal.add(dailyTransaction.amt);
        if (dailyTransaction.amt.signum() >= 0) {
            account.currCycCredit = account.currCycCredit.add(dailyTransaction.amt);
        } else {
            account.currCycDebit = account.currCycDebit.add(dailyTransaction.amt);
        }
        files.accounts.rewrite(account.acctId, account);
    }

    private TranRecord copyTransaction(DalyTranRecord dailyTransaction) {
        TranRecord transaction = new TranRecord();
        transaction.id = dailyTransaction.id;
        transaction.typeCd = dailyTransaction.typeCd;
        transaction.catCd = dailyTransaction.catCd;
        transaction.source = dailyTransaction.source;
        transaction.desc = dailyTransaction.desc;
        transaction.amt = dailyTransaction.amt;
        transaction.merchantId = dailyTransaction.merchantId;
        transaction.merchantName = dailyTransaction.merchantName;
        transaction.merchantCity = dailyTransaction.merchantCity;
        transaction.merchantZip = dailyTransaction.merchantZip;
        transaction.cardNum = dailyTransaction.cardNum;
        transaction.origTs = dailyTransaction.origTs;
        transaction.procTs = dailyTransaction.procTs;
        transaction.filler = dailyTransaction.filler;
        return transaction;
    }

    private void writeReject(
            DalyTranRecord dailyTransaction,
            ValidationFailure failure,
            BatchFiles files) {
        String trailer = FixedWidth.unsignedNumber(
                Integer.toString(failure.reasonCode()), 4)
                + FixedWidth.text(failure.description(), 76);
        files.rejects.write(dailyTransaction.format() + trailer);
    }

    private record ValidationFailure(
            int reasonCode,
            String description,
            CardXrefRecord xref,
            AccountRecord account) {
    }
}
