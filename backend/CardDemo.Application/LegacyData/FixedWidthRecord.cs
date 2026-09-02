using System.Globalization;

namespace CardDemo.Application.LegacyData;

/// <summary>
/// Sequential cursor over one fixed-width record (a copybook 01 level). Each call consumes the
/// next PIC field left to right, so parser code reads like the copybook it mirrors.
/// </summary>
public sealed class FixedWidthRecord
{
    private const string DateFormat = "yyyy-MM-dd";
    private const string TimestampFormat = "yyyy-MM-dd HH:mm:ss.ffffff";

    private readonly string _record;
    private readonly string _recordName;
    private int _position;

    public FixedWidthRecord(string line, int recordLength, string recordName)
    {
        ArgumentNullException.ThrowIfNull(line);
        if (line.Length > recordLength)
        {
            throw new FormatException($"{recordName} record exceeds {recordLength} bytes: {line.Length}.");
        }
        _record = line.PadRight(recordLength);
        _recordName = recordName;
    }

    /// <summary>PIC X(n): raw bytes with trailing spaces trimmed.</summary>
    public string Text(int width) => Raw(width).TrimEnd();

    /// <summary>PIC 9(n) used as a key or code: kept as the exact zero-padded digits.</summary>
    public string Digits(int width)
    {
        var raw = Raw(width);
        if (raw.Any(c => c is < '0' or > '9'))
        {
            throw new FormatException($"{_recordName}: numeric field at offset {_position - width} is not all digits: '{raw}'.");
        }
        return raw;
    }

    /// <summary>PIC 9(n) used arithmetically.</summary>
    public int UnsignedInt(int width) => ZonedDecimal.ParseUnsignedInt(Raw(width));

    /// <summary>PIC S9(n)V9(scale) zoned decimal with overpunched sign.</summary>
    public decimal Amount(int integerDigits, int scale) => ZonedDecimal.Parse(Raw(integerDigits + scale), scale);

    /// <summary>PIC X(10) yyyy-MM-dd; blank yields null.</summary>
    public DateOnly? Date()
    {
        var raw = Raw(10);
        if (string.IsNullOrWhiteSpace(raw))
        {
            return null;
        }
        if (!DateOnly.TryParseExact(raw, DateFormat, CultureInfo.InvariantCulture, DateTimeStyles.None, out var date))
        {
            throw new FormatException($"{_recordName}: invalid date '{raw}' at offset {_position - 10}.");
        }
        return date;
    }

    /// <summary>PIC X(26) yyyy-MM-dd HH:mm:ss.ffffff; blank yields null. Kind is Unspecified (no zone in the record).</summary>
    public DateTime? Timestamp()
    {
        var raw = Raw(26);
        if (string.IsNullOrWhiteSpace(raw))
        {
            return null;
        }
        if (!DateTime.TryParseExact(raw, TimestampFormat, CultureInfo.InvariantCulture, DateTimeStyles.None, out var timestamp))
        {
            throw new FormatException($"{_recordName}: invalid timestamp '{raw}' at offset {_position - 26}.");
        }
        return timestamp;
    }

    public void Filler(int width) => _position += width;

    private string Raw(int width)
    {
        if (_position + width > _record.Length)
        {
            throw new InvalidOperationException($"{_recordName}: field at offset {_position} width {width} exceeds record length {_record.Length}.");
        }
        var value = _record.Substring(_position, width);
        _position += width;
        return value;
    }
}
