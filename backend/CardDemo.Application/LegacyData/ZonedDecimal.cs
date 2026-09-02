using System.Globalization;

namespace CardDemo.Application.LegacyData;

/// <summary>
/// Decodes COBOL zoned-decimal (DISPLAY) numerics as they appear in app/data/ASCII.
/// The sign is overpunched on the last digit using the EBCDIC convention (GnuCOBOL -fsign=EBCDIC):
/// '{' = +0, 'A'..'I' = +1..+9, '}' = -0, 'J'..'R' = -1..-9; a plain digit is unsigned/positive.
/// </summary>
public static class ZonedDecimal
{
    /// <summary>Parses a PIC [S]9(n)V9(scale) field of exactly <paramref name="field"/>.Length digits.</summary>
    public static decimal Parse(string field, int scale)
    {
        ArgumentNullException.ThrowIfNull(field);
        if (field.Length == 0)
        {
            throw new FormatException("Zoned decimal field is empty.");
        }
        if (scale < 0 || scale > field.Length)
        {
            throw new ArgumentOutOfRangeException(nameof(scale), scale, "Scale must be between 0 and the field length.");
        }

        var (lastDigit, negative) = DecodeOverpunch(field[^1], field);
        var digits = field[..^1];
        foreach (var c in digits)
        {
            if (c is < '0' or > '9')
            {
                throw new FormatException($"Zoned decimal field '{field}' contains non-digit '{c}'.");
            }
        }

        var unscaled = decimal.Parse(digits + lastDigit, NumberStyles.None, CultureInfo.InvariantCulture);
        var value = unscaled / Pow10(scale);
        return negative ? -value : value;
    }

    /// <summary>Parses an unsigned PIC 9(n) field into an integer (leading zeros allowed).</summary>
    public static int ParseUnsignedInt(string field)
    {
        ArgumentNullException.ThrowIfNull(field);
        if (field.Length == 0 || field.Any(c => c is < '0' or > '9'))
        {
            throw new FormatException($"Unsigned numeric field '{field}' must contain only digits.");
        }
        return int.Parse(field, NumberStyles.None, CultureInfo.InvariantCulture);
    }

    private static (char Digit, bool Negative) DecodeOverpunch(char c, string field) => c switch
    {
        >= '0' and <= '9' => (c, false),
        '{' => ('0', false),
        >= 'A' and <= 'I' => ((char)('0' + (c - 'A' + 1)), false),
        '}' => ('0', true),
        >= 'J' and <= 'R' => ((char)('0' + (c - 'J' + 1)), true),
        _ => throw new FormatException($"Zoned decimal field '{field}' has invalid sign/overpunch character '{c}'.")
    };

    private static decimal Pow10(int scale)
    {
        decimal result = 1m;
        for (var i = 0; i < scale; i++)
        {
            result *= 10m;
        }
        return result;
    }
}
