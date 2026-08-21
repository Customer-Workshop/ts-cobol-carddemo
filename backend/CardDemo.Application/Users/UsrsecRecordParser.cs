namespace CardDemo.Application.Users;

/// <summary>
/// Slices 80-byte fixed USRSEC records per app/cpy/CSUSR01Y.cpy:
/// SEC-USR-ID X(8), SEC-USR-FNAME X(20), SEC-USR-LNAME X(20), SEC-USR-PWD X(8),
/// SEC-USR-TYPE X(1), SEC-USR-FILLER X(23). Trailing spaces are trimmed per field.
/// </summary>
public static class UsrsecRecordParser
{
    public const int RecordLength = 80;

    public static UsrsecRecord Parse(string line)
    {
        ArgumentNullException.ThrowIfNull(line);
        if (line.Length > RecordLength)
        {
            throw new FormatException($"USRSEC record exceeds {RecordLength} bytes: {line.Length}.");
        }
        var record = line.PadRight(RecordLength);
        return new UsrsecRecord(
            UserId: record[..8].TrimEnd(),
            FirstName: record[8..28].TrimEnd(),
            LastName: record[28..48].TrimEnd(),
            Password: record[48..56].TrimEnd(),
            UserType: record[56]);
    }
}
