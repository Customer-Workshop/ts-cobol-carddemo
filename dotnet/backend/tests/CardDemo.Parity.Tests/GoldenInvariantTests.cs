using CardDemo.Domain;
using CardDemo.Legacy.Decoders.Records;
using CardDemo.Persistence;

namespace CardDemo.Parity.Tests;

/// <summary>
/// Cross-golden invariants that make the goldens falsifiable from the
/// .NET side: keyed order of unloads, untouched-record preservation,
/// and the CBACT04C balance delta reconciling against the interest
/// transactions it wrote.
///
/// Bug-for-bug preserved (contract SEM-B01): CBACT04C's driver loop is
/// PERFORM UNTIL END-OF-FILE = 'Y'; the loop re-tests the condition
/// before the ELSE branch that would flush the final account, so the
/// LAST account holding category balances (account 8 in the seed) has
/// its interest transactions written but its account record is never
/// rewritten (no balance update, cycle fields not reset).
/// </summary>
public class GoldenInvariantTests
{
    private static List<AccountRecord> Accounts(string scenario, string file) =>
        FixedRecordFile.ReadLineSequential(GoldenPaths.Scenario(scenario, file), AccountRecord.Length)
            .Select(r => AccountRecord.Decode(r)).ToList();

    [Fact]
    public void Unloads_AreInAscendingKeyOrder()
    {
        var ids = Accounts("interest-calc", "ACCTFILE.post.unload").Select(a => a.AccountId).ToList();
        Assert.Equal(ids.OrderBy(x => x), ids);
        Assert.Equal(12, ids.Distinct().Count());
    }

    [Fact]
    public void UntouchedAccounts_AreByteIdenticalAcrossRun()
    {
        var seed = FixedRecordFile.ReadLineSequential(GoldenPaths.Scenario("data-roundtrip", "ACCTFILE.seed.unload"), AccountRecord.Length).ToList();
        var post = FixedRecordFile.ReadLineSequential(GoldenPaths.Scenario("interest-calc", "ACCTFILE.post.unload"), AccountRecord.Length).ToList();

        Assert.Equal(seed.Count, post.Count);
        int untouched = 0;
        for (int i = 0; i < seed.Count; i++)
        {
            var account = AccountRecord.Decode(seed[i]);
            if (!RewrittenAccounts.Contains(account.AccountId))
            {
                Assert.Equal(seed[i], post[i]);
                untouched++;
            }
            else
            {
                // Rewritten accounts always get their cycle fields reset.
                var updated = AccountRecord.Decode(post[i]);
                Assert.Equal(0m, updated.CurrentCycleCredit);
                Assert.Equal(0m, updated.CurrentCycleDebit);
            }
        }

        Assert.True(untouched > 0, "some accounts must remain untouched");
    }

    // Accounts 1-8 hold TCATBAL rows; account 8 is last in key order and
    // is skipped by the SEM-B01 final-flush bug. Accounts 9-12 have no
    // category balances at all (missed-REWRITE detectors).
    private static readonly long[] RewrittenAccounts = [1, 2, 3, 4, 5, 6, 7];

    [Fact]
    public void BalanceDeltas_ReconcileAgainstInterestTransactions()
    {
        var seed = Accounts("data-roundtrip", "ACCTFILE.seed.unload").ToDictionary(a => a.AccountId);
        var post = Accounts("interest-calc", "ACCTFILE.post.unload").ToDictionary(a => a.AccountId);
        var interestByAccount = FixedRecordFile.ReadFixed(GoldenPaths.Scenario("interest-calc", "TRANSACT.dat"), TransactionRecord.Length)
            .Select(r => TransactionRecord.Decode(r))
            .GroupBy(tx => long.Parse(tx.CardNumber[^2..], System.Globalization.CultureInfo.InvariantCulture))
            .ToDictionary(g => g.Key, g => g.Sum(tx => tx.Amount));

        foreach ((long id, var seedAcct) in seed)
        {
            decimal expectedDelta = RewrittenAccounts.Contains(id)
                ? interestByAccount.GetValueOrDefault(id, 0m)
                : 0m; // SEM-B01: last account's transactions are written but its balance is not.
            Assert.Equal(expectedDelta, post[id].CurrentBalance - seedAcct.CurrentBalance);
        }

        Assert.Contains(interestByAccount.Values, v => v != 0m);
        Assert.True(interestByAccount.ContainsKey(8), "account 8 must have transactions despite no rewrite (SEM-B01)");
    }

    [Fact]
    public void InterestAmounts_MatchLegacyMoneyFormula()
    {
        // Every CBACT04C interest transaction ('01'/cat 05, one per
        // seeded TCATBAL row with a non-zero rate) must equal
        // LegacyMoney.MonthlyInterest(category balance, rate) using the
        // WRSEED disclosure rates: GRPA000001 (accounts 1-4, 9-12) =
        // 14.99/19.99/21.50 by category, GRPMISSING (accounts 7-8)
        // falls back to DEFAULT = 25.00, GRPZERO001 (5-6) = 0 (no tx).
        // Rates are embedded here because DISCGRP has no golden unload;
        // see interest-calc/manifest.md.
        var tcat = FixedRecordFile.ReadLineSequential(GoldenPaths.Scenario("data-roundtrip", "TCATBAL.seed.unload"), TranCatBalRecord.Length)
            .Select(r => TranCatBalRecord.Decode(r))
            .ToDictionary(t => (t.AccountId, t.TypeCode, t.CategoryCode));
        var txs = FixedRecordFile.ReadFixed(GoldenPaths.Scenario("interest-calc", "TRANSACT.dat"), TransactionRecord.Length)
            .Select(r => TransactionRecord.Decode(r)).ToList();

        var remaining = tcat.Keys
            .Where(k => k.AccountId is not (5 or 6))
            .ToHashSet();
        foreach (var tx in txs)
        {
            Assert.Equal("01", tx.TypeCode);
            Assert.Equal(5, tx.CategoryCode);
            Assert.Equal("System", tx.Source.TrimEnd());
            long accountId = long.Parse(tx.CardNumber[^2..], System.Globalization.CultureInfo.InvariantCulture);
            var key = remaining.First(k => k.AccountId == accountId
                && LegacyMoney.MonthlyInterest(tcat[k].Balance, RateFor(accountId, k.TypeCode, k.CategoryCode)) == tx.Amount);
            remaining.Remove(key);
        }

        Assert.Empty(remaining);

        static decimal RateFor(long accountId, string typeCode, int categoryCode) =>
            accountId is 7 or 8
                ? 25.00m
                : (typeCode, categoryCode) switch
                {
                    ("01", 1) => 14.99m,
                    ("01", 2) => 19.99m,
                    ("02", 3) => 21.50m,
                    _ => throw new InvalidOperationException($"unexpected category {typeCode}/{categoryCode}"),
                };
    }
}
