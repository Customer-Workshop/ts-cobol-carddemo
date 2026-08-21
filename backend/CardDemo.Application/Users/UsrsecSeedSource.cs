namespace CardDemo.Application.Users;

/// <summary>
/// Reads USRSEC seed records from repo sources: a plain ASCII fixed-width file
/// (one 80-column record per line) or the in-stream SYSUT1 data of app/jcl/DUSRSECJ.jcl.
/// </summary>
public static class UsrsecSeedSource
{
    public static IReadOnlyList<string> ReadRecords(string path)
    {
        var lines = File.ReadAllLines(path);
        return Path.GetExtension(path).Equals(".jcl", StringComparison.OrdinalIgnoreCase)
            ? ExtractJclInstreamRecords(lines)
            : lines.Where(l => l.Length > 0).ToList();
    }

    public static IReadOnlyList<string> ExtractJclInstreamRecords(IEnumerable<string> jclLines)
    {
        var records = new List<string>();
        var inStream = false;
        foreach (var line in jclLines)
        {
            if (inStream)
            {
                if (line.StartsWith("/*", StringComparison.Ordinal))
                {
                    break;
                }
                records.Add(line);
            }
            else if (line.StartsWith("//SYSUT1", StringComparison.Ordinal) && line.TrimEnd().EndsWith("DD *", StringComparison.Ordinal))
            {
                inStream = true;
            }
        }
        return records;
    }
}
