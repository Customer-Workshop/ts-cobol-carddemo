namespace CardDemo.Application.LegacyData;

/// <summary>
/// Reads fixed-width records from the app/data/ASCII exports: one record per line, CR/LF
/// terminators stripped (some exports are CRLF), blank lines skipped.
/// </summary>
public static class LegacyDataSeedSource
{
    public static IReadOnlyList<string> ReadRecords(string path) =>
        File.ReadAllLines(path)
            .Select(l => l.TrimEnd('\r'))
            .Where(l => l.Length > 0)
            .ToList();
}
