namespace CardDemo.Domain;

/// <summary>
/// Estate date conventions: dates travel as PIC X(10) 'YYYY-MM-DD'
/// strings compared lexicographically (verified empirically in
/// CBTRN03C's window filter); timestamps as PIC X(26)
/// 'YYYY-MM-DD HH.MM.SS.NNNNNN' (DB2 style with dot separators).
/// No day-serial epoch is used by the wave-0 modules.
/// </summary>
public static class LegacyDate
{
    /// <summary>Lexicographic comparison, exactly as COBOL compares PIC X(10) dates.</summary>
    public static int Compare(string left, string right) =>
        string.CompareOrdinal(left, right);

    public static string ToTimestamp(DateTime value) =>
        value.ToString("yyyy-MM-dd HH.mm.ss.ffffff", System.Globalization.CultureInfo.InvariantCulture);
}
