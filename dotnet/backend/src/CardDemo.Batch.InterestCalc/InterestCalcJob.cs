using System.Globalization;
using CardDemo.Domain;
using CardDemo.Legacy.Decoders.Records;

namespace CardDemo.Batch.InterestCalc;

/// <summary>
/// Inputs for one CBACT04C run. Record lists carry the decoded keyed-
/// order unload images: TCATBALF drives the loop sequentially; ACCTFILE
/// is read randomly by account id; XREFFILE is read by the alternate
/// key (first record per account id in primary card-number order);
/// DISCGRP is read by (group id, type, category) with the status-23
/// DEFAULT-group fallback.
/// </summary>
public sealed record InterestCalcInput(
    IReadOnlyList<TranCatBalRecord> CategoryBalances,
    IReadOnlyList<AccountRecord> Accounts,
    IReadOnlyList<DisclosureGroupRecord> DisclosureGroups,
    IReadOnlyList<CardXrefRecord> CardXrefs,
    string ParmDate,
    Func<DateTime> Clock);

/// <summary>
/// Outputs of one run: the post-run ACCTFILE image in keyed order, the
/// TCATBALF image (byte-identical to the input: CBACT04C opens it
/// INPUT-only), and the TRANSACT output transactions in write order.
/// </summary>
public sealed record InterestCalcResult(
    IReadOnlyList<AccountRecord> PostAccounts,
    IReadOnlyList<TranCatBalRecord> PostCategoryBalances,
    IReadOnlyList<TransactionRecord> Transactions);

/// <summary>
/// Port of CBACT04C (the INTCALC.jcl monthly interest job): for each
/// TCATBAL row, look up the disclosure-group rate, post
/// <c>LegacyMoney.MonthlyInterest(balance, rate)</c> as a '01'/cat-05
/// transaction when the rate is non-zero, and on account break REWRITE
/// the previous account with the accumulated interest and its cycle
/// credit/debit reset.
///
/// Bug-for-bug (contract SEM-B01): the driving loop is
/// <c>PERFORM UNTIL END-OF-FILE = 'Y'</c>, which re-tests the condition
/// before the EOF ELSE branch that would flush the final account, so
/// the last account's interest transactions are written but its record
/// is never rewritten.
/// </summary>
public static class InterestCalcJob
{
    private const string DefaultGroupId = "DEFAULT   "; // PIC X(10)

    public static InterestCalcResult Run(InterestCalcInput input)
    {
        ArgumentNullException.ThrowIfNull(input);

        var accounts = input.Accounts.ToDictionary(a => a.AccountId);
        var accountOrder = input.Accounts.Select(a => a.AccountId).ToList();
        var rateTable = input.DisclosureGroups.ToDictionary(d => (d.GroupId, d.TypeCode, d.CategoryCode));
        var xrefByAccount = FirstXrefPerAccount(input.CardXrefs);

        var transactions = new List<TransactionRecord>();
        AccountRecord? account = null;
        CardXrefRecord? xref = null;
        long lastAccountId = -1;
        bool firstTime = true;
        decimal totalInterest = 0m;
        int tranIdSuffix = 0;

        foreach (var row in input.CategoryBalances)
        {
            if (row.AccountId != lastAccountId)
            {
                if (!firstTime)
                {
                    RewriteAccount(accounts, lastAccountId, totalInterest); // 1050-UPDATE-ACCOUNT
                }
                else
                {
                    firstTime = false;
                }

                totalInterest = 0m;
                lastAccountId = row.AccountId;
                account = accounts.TryGetValue(row.AccountId, out var found) // 1100-GET-ACCT-DATA
                    ? found
                    : throw new InvalidOperationException($"ACCOUNT NOT FOUND: {row.AccountId} (CBACT04C abends, file status 23).");
                xref = xrefByAccount.TryGetValue(row.AccountId, out var foundXref) // 1110-GET-XREF-DATA
                    ? foundXref
                    : throw new InvalidOperationException($"XREF NOT FOUND: {row.AccountId} (CBACT04C abends, file status 23).");
            }

            decimal rate = GetInterestRate(rateTable, account!.GroupId, row.TypeCode, row.CategoryCode); // 1200-GET-INTEREST-RATE
            if (rate != 0m)
            {
                decimal monthlyInterest = LegacyMoney.MonthlyInterest(row.Balance, rate); // 1300-COMPUTE-INTEREST
                totalInterest += monthlyInterest;
                tranIdSuffix++;
                transactions.Add(BuildInterestTransaction( // 1300-B-WRITE-TX
                    input.ParmDate, tranIdSuffix, account, xref!, monthlyInterest, input.Clock()));
            }
        }

        // SEM-B01: the final account is intentionally NOT rewritten here.
        var postAccounts = accountOrder.Select(id => accounts[id]).ToList();
        return new InterestCalcResult(postAccounts, input.CategoryBalances, transactions);
    }

    /// <summary>
    /// READ XREF-FILE KEY IS FD-XREF-ACCT-ID (alternate key): returns
    /// the first record for each account id in primary-key order.
    /// </summary>
    private static Dictionary<long, CardXrefRecord> FirstXrefPerAccount(IReadOnlyList<CardXrefRecord> xrefs)
    {
        var byAccount = new Dictionary<long, CardXrefRecord>();
        foreach (var xref in xrefs)
        {
            byAccount.TryAdd(xref.AccountId, xref);
        }

        return byAccount;
    }

    private static decimal GetInterestRate(
        Dictionary<(string GroupId, string TypeCode, int CategoryCode), DisclosureGroupRecord> rateTable,
        string groupId,
        string typeCode,
        int categoryCode)
    {
        if (rateTable.TryGetValue((groupId, typeCode, categoryCode), out var group))
        {
            return group.InterestRate;
        }

        // File status 23: retry with the DEFAULT group (1200-A-GET-DEFAULT-INT-RATE).
        return rateTable.TryGetValue((DefaultGroupId, typeCode, categoryCode), out var defaultGroup)
            ? defaultGroup.InterestRate
            : throw new InvalidOperationException(
                $"DISCLOSURE GROUP RECORD MISSING for DEFAULT/{typeCode}/{categoryCode} (CBACT04C abends).");
    }

    private static void RewriteAccount(Dictionary<long, AccountRecord> accounts, long accountId, decimal totalInterest)
    {
        var account = accounts[accountId];
        accounts[accountId] = account with
        {
            CurrentBalance = account.CurrentBalance + totalInterest,
            CurrentCycleCredit = 0m,
            CurrentCycleDebit = 0m,
        };
    }

    private static TransactionRecord BuildInterestTransaction(
        string parmDate,
        int tranIdSuffix,
        AccountRecord account,
        CardXrefRecord xref,
        decimal amount,
        DateTime now)
    {
        string timestamp = Db2Timestamp(now); // Z-GET-DB2-FORMAT-TIMESTAMP
        return new TransactionRecord(
            TranId: parmDate + tranIdSuffix.ToString("D6", CultureInfo.InvariantCulture),
            TypeCode: "01",
            CategoryCode: 5,
            Source: "System",
            Description: "Int. for a/c " + account.AccountId.ToString("D11", CultureInfo.InvariantCulture),
            Amount: amount,
            MerchantId: 0,
            MerchantName: "",
            MerchantCity: "",
            MerchantZip: "",
            CardNumber: xref.CardNumber,
            OriginTimestamp: timestamp,
            ProcessTimestamp: timestamp,
            Filler: "");
    }

    /// <summary>
    /// FUNCTION CURRENT-DATE reshaped to the DB2-style X(26) timestamp:
    /// YYYY-MM-DD-HH.MM.SS.mm0000 (hundredths + literal '0000').
    /// </summary>
    private static string Db2Timestamp(DateTime now) =>
        now.ToString("yyyy-MM-dd-HH.mm.ss.ff", CultureInfo.InvariantCulture) + "0000";
}
