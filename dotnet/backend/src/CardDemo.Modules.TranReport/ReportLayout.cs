using System.Globalization;

namespace CardDemo.Modules.TranReport;

/// <summary>
/// 133-byte report line images from copybook CVTRA07Y. Each builder
/// returns the group item's content; the writer pads to the full
/// FD-REPTFILE-REC width (group-to-PIC-X(133) MOVE semantics).
/// </summary>
public static class ReportLayout
{
    public const int LineWidth = 133;

    public static string NameHeader(string startDate, string endDate) =>
        "DALYREPT".PadRight(38) +
        "Daily Transaction Report".PadRight(41) +
        "Date Range: " + startDate + " to " + endDate;

    public static string ColumnHeader { get; } =
        "Transaction ID".PadRight(17) +
        "Account ID".PadRight(12) +
        "Transaction Type".PadRight(19) +
        "Tran Category".PadRight(35) +
        "Tran Source".PadRight(14) +
        " " +
        "        Amount".PadRight(16);

    public static string DashedLine { get; } = new('-', LineWidth);

    public static string Detail(
        string tranId,
        long accountId,
        string typeCode,
        string typeDescription,
        int categoryCode,
        string categoryDescription,
        string source,
        decimal amount) =>
        tranId +
        " " +
        accountId.ToString("D11", CultureInfo.InvariantCulture) +
        " " +
        typeCode +
        "-" +
        Fit(typeDescription, 15) +
        " " +
        categoryCode.ToString("D4", CultureInfo.InvariantCulture) +
        "-" +
        Fit(categoryDescription, 29) +
        " " +
        Fit(source, 10) +
        "    " +
        EditedPic.MinusEdited(amount) +
        "  ";

    public static string PageTotal(decimal total) =>
        "Page Total".PadRight(11) + new string('.', 86) + EditedPic.PlusEdited(total);

    public static string AccountTotal(decimal total) =>
        "Account Total".PadRight(13) + new string('.', 84) + EditedPic.PlusEdited(total);

    public static string GrandTotal(decimal total) =>
        "Grand Total".PadRight(11) + new string('.', 86) + EditedPic.PlusEdited(total);

    /// <summary>Alphanumeric MOVE into a shorter PIC X(n): left-aligned, truncated or space padded.</summary>
    private static string Fit(string value, int width) =>
        value.Length >= width ? value[..width] : value.PadRight(width);
}
