using System.Globalization;
using CardDemo.Domain.Transactions;

namespace CardDemo.Application.Transactions;

/// <summary>
/// Explicit mapper TRAN-RECORD → COTRN1A output fields (app/cbl/COTRN01C.cbl:176-190).
/// Map lengths come from app/bms/COTRN01.bms; the amount goes through the
/// WS-TRAN-AMT edit picture +99999999.99 (:49) and the X(26) timestamps are cut to
/// the X(10) date fields (FR-S08-09..11).
/// </summary>
public static class TransactionViewMapper
{
    public const int DescriptionLength = 60;
    public const int MerchantNameLength = 30;
    public const int MerchantCityLength = 25;

    private const decimal AmountModulus = 100_000_000m;

    public static TransactionViewDetail ToDetail(Transaction transaction) =>
        new(
            transaction.TransactionId,
            transaction.CardNumber,
            transaction.TypeCode,
            transaction.CategoryCode,
            transaction.Source,
            Fit(transaction.Description, DescriptionLength),
            FormatAmount(transaction.Amount),
            FormatDate(transaction.OriginalTimestamp),
            FormatDate(transaction.ProcessedTimestamp),
            transaction.MerchantId,
            Fit(transaction.MerchantName, MerchantNameLength),
            Fit(transaction.MerchantCity, MerchantCityLength),
            transaction.MerchantZip);

    /// <summary>PIC +99999999.99: fixed sign, 8 integer digits (high-order digit dropped), 2 decimals.</summary>
    public static string FormatAmount(decimal amount)
    {
        var sign = amount < 0 ? '-' : '+';
        var magnitude = Math.Abs(decimal.Truncate(amount * 100) / 100) % AmountModulus;
        return sign + magnitude.ToString("00000000.00", CultureInfo.InvariantCulture);
    }

    /// <summary>First 10 characters of the yyyy-MM-dd-hh.mm.ss.ffffff timestamp; blank when absent.</summary>
    public static string FormatDate(DateTime? timestamp) =>
        timestamp?.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture) ?? string.Empty;

    private static string Fit(string value, int length) =>
        value.Length <= length ? value : value[..length];
}
