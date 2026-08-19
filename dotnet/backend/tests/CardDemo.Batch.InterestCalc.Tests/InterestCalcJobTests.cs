using CardDemo.Legacy.Decoders.Records;

namespace CardDemo.Batch.InterestCalc.Tests;

/// <summary>
/// Micro-level semantics of the CBACT04C port on synthetic inputs:
/// DEFAULT-group fallback, zero-rate suppression, alternate-key xref
/// selection, and the SEM-B01 final-account no-rewrite bug.
/// </summary>
public class InterestCalcJobTests
{
    private static readonly DateTime FrozenClock = new(2025, 8, 1, 9, 0, 0, DateTimeKind.Unspecified);

    private static AccountRecord Account(long id, decimal balance, string groupId) =>
        new(
            AccountId: id,
            ActiveStatus: "Y",
            CurrentBalance: balance,
            CreditLimit: 1000m,
            CashCreditLimit: 500m,
            OpenDate: "2020-01-01",
            ExpirationDate: "2030-01-01",
            ReissueDate: "2025-01-01",
            CurrentCycleCredit: 11.11m,
            CurrentCycleDebit: 22.22m,
            AddressZip: "12345",
            GroupId: groupId,
            Filler: "");

    private static TranCatBalRecord CategoryBalance(long accountId, string typeCode, int categoryCode, decimal balance) =>
        new(AccountId: accountId, TypeCode: typeCode, CategoryCode: categoryCode, Balance: balance, Filler: "");

    private static DisclosureGroupRecord Disclosure(string groupId, string typeCode, int categoryCode, decimal rate) =>
        new(GroupId: groupId, TypeCode: typeCode, CategoryCode: categoryCode, InterestRate: rate, Filler: "");

    private static CardXrefRecord Xref(string cardNumber, long accountId) =>
        new(CardNumber: cardNumber, CustomerId: 1, AccountId: accountId, Filler: "");

    private static InterestCalcInput Input(
        IReadOnlyList<TranCatBalRecord> categoryBalances,
        IReadOnlyList<AccountRecord> accounts,
        IReadOnlyList<DisclosureGroupRecord> disclosureGroups,
        IReadOnlyList<CardXrefRecord> xrefs) =>
        new(categoryBalances, accounts, disclosureGroups, xrefs, "2025-07-31", () => FrozenClock);

    [Fact]
    public void MissingDisclosureGroup_FallsBackToDefaultGroupRate()
    {
        var result = InterestCalcJob.Run(Input(
            [CategoryBalance(1, "01", 1, 1200.00m)],
            [Account(1, 100.00m, "NOSUCHGRP ")],
            [Disclosure("DEFAULT   ", "01", 1, 12.00m)],
            [Xref("4000000000000001", 1)]));

        var transaction = Assert.Single(result.Transactions);
        Assert.Equal(12.00m, transaction.Amount);
    }

    [Fact]
    public void ZeroRate_WritesNoTransaction_ButAccountStillRewrittenOnBreak()
    {
        var result = InterestCalcJob.Run(Input(
            [CategoryBalance(1, "01", 1, 1200.00m), CategoryBalance(2, "01", 1, 1200.00m)],
            [Account(1, 100.00m, "GRPZERO   "), Account(2, 100.00m, "GRPZERO   ")],
            [Disclosure("GRPZERO   ", "01", 1, 0m)],
            [Xref("4000000000000001", 1), Xref("4000000000000002", 2)]));

        Assert.Empty(result.Transactions);
        var first = result.PostAccounts[0];
        Assert.Equal(100.00m, first.CurrentBalance);
        Assert.Equal(0m, first.CurrentCycleCredit);
        Assert.Equal(0m, first.CurrentCycleDebit);
    }

    [Fact]
    public void FinalAccount_TransactionsWritten_ButRecordNeverRewritten_SemB01()
    {
        var result = InterestCalcJob.Run(Input(
            [CategoryBalance(1, "01", 1, 1200.00m), CategoryBalance(2, "01", 1, 2400.00m)],
            [Account(1, 100.00m, "GRPA      "), Account(2, 200.00m, "GRPA      ")],
            [Disclosure("GRPA      ", "01", 1, 12.00m)],
            [Xref("4000000000000001", 1), Xref("4000000000000002", 2)]));

        Assert.Equal(2, result.Transactions.Count);

        var flushed = result.PostAccounts[0];
        Assert.Equal(112.00m, flushed.CurrentBalance);
        Assert.Equal(0m, flushed.CurrentCycleCredit);
        Assert.Equal(0m, flushed.CurrentCycleDebit);

        var final = result.PostAccounts[1];
        Assert.Equal(200.00m, final.CurrentBalance);
        Assert.Equal(11.11m, final.CurrentCycleCredit);
        Assert.Equal(22.22m, final.CurrentCycleDebit);
    }

    [Fact]
    public void XrefAlternateKeyRead_PicksFirstCardInPrimaryKeyOrder()
    {
        var result = InterestCalcJob.Run(Input(
            [CategoryBalance(1, "01", 1, 1200.00m)],
            [Account(1, 100.00m, "GRPA      ")],
            [Disclosure("GRPA      ", "01", 1, 12.00m)],
            [Xref("4000000000000001", 1), Xref("4999999999999999", 1)]));

        var transaction = Assert.Single(result.Transactions);
        Assert.Equal("4000000000000001", transaction.CardNumber);
    }

    [Fact]
    public void InterestTransaction_FieldsMatchCbact04cLayout()
    {
        var result = InterestCalcJob.Run(Input(
            [CategoryBalance(1, "01", 1, 1200.00m), CategoryBalance(1, "02", 2, 2400.00m)],
            [Account(1, 100.00m, "GRPA      ")],
            [Disclosure("GRPA      ", "01", 1, 12.00m), Disclosure("GRPA      ", "02", 2, 12.00m)],
            [Xref("4000000000000001", 1)]));

        Assert.Equal(2, result.Transactions.Count);
        var first = result.Transactions[0];
        Assert.Equal("2025-07-31000001", first.TranId);
        Assert.Equal("2025-07-31000002", result.Transactions[1].TranId);
        Assert.Equal("01", first.TypeCode);
        Assert.Equal(5, first.CategoryCode);
        Assert.Equal("System", first.Source);
        Assert.Equal("Int. for a/c 00000000001", first.Description.TrimEnd());
        Assert.Equal("2025-08-01-09.00.00.000000", first.OriginTimestamp);
        Assert.Equal(first.OriginTimestamp, first.ProcessTimestamp);
    }
}
