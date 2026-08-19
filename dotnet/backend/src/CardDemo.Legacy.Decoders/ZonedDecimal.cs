namespace CardDemo.Legacy.Decoders;

/// <summary>
/// Codec for COBOL zoned-decimal (USAGE DISPLAY) numeric fields as they
/// appear in the CardDemo estate: ASCII digits with an EBCDIC sign
/// overpunch on the trailing byte ('{' = +0, 'A'-'I' = +1..+9,
/// '}' = -0, 'J'-'R' = -1..-9). This matches GnuCOBOL's
/// -fsign=ebcdic representation and IBM zoned data exported to ASCII.
/// Unsigned PIC 9(n) fields are plain ASCII digits.
/// </summary>
public static class ZonedDecimal
{
    private const string PositiveOverpunch = "{ABCDEFGHI";
    private const string NegativeOverpunch = "}JKLMNOPQR";

    /// <summary>Decodes a signed zoned field with <paramref name="scale"/> implied decimal digits.</summary>
    public static decimal DecodeSigned(ReadOnlySpan<byte> field, int scale)
    {
        if (field.IsEmpty)
        {
            throw new ArgumentException("Zoned field must not be empty.", nameof(field));
        }

        long magnitude = 0;
        for (int i = 0; i < field.Length - 1; i++)
        {
            magnitude = checked(magnitude * 10 + Digit(field[i], i));
        }

        char last = (char)field[^1];
        bool negative;
        int lastDigit;
        int pos = PositiveOverpunch.IndexOf(last, StringComparison.Ordinal);
        int neg = NegativeOverpunch.IndexOf(last, StringComparison.Ordinal);
        if (pos >= 0)
        {
            negative = false;
            lastDigit = pos;
        }
        else if (neg >= 0)
        {
            negative = true;
            lastDigit = neg;
        }
        else if (last is >= '0' and <= '9')
        {
            // Unsigned zone on a signed field: tolerated on read,
            // matching COBOL's acceptance of F-zone data.
            negative = false;
            lastDigit = last - '0';
        }
        else
        {
            throw new FormatException($"Invalid zoned sign byte 0x{field[^1]:X2}.");
        }

        magnitude = checked(magnitude * 10 + lastDigit);
        decimal value = magnitude / Pow10(scale);
        return negative ? -value : value;
    }

    /// <summary>Decodes an unsigned PIC 9(n) field.</summary>
    public static long DecodeUnsigned(ReadOnlySpan<byte> field)
    {
        long value = 0;
        for (int i = 0; i < field.Length; i++)
        {
            value = checked(value * 10 + Digit(field[i], i));
        }

        return value;
    }

    /// <summary>
    /// Encodes a signed value into zoned bytes with EBCDIC overpunch on
    /// the trailing byte (preferred sign: C-zone positive, D-zone
    /// negative; -0 encodes as '}').
    /// </summary>
    public static void EncodeSigned(decimal value, Span<byte> destination, int scale)
    {
        bool negative = value < 0 || (value == 0 && decimal.IsNegative(value));
        decimal scaled = Math.Abs(value) * Pow10(scale);
        long magnitude = (long)scaled;
        if (magnitude != scaled)
        {
            throw new ArgumentException($"Value {value} does not fit scale {scale}.", nameof(value));
        }

        for (int i = destination.Length - 1; i >= 0; i--)
        {
            int digit = (int)(magnitude % 10);
            magnitude /= 10;
            destination[i] = i == destination.Length - 1
                ? (byte)(negative ? NegativeOverpunch[digit] : PositiveOverpunch[digit])
                : (byte)('0' + digit);
        }

        if (magnitude != 0)
        {
            throw new ArgumentException($"Value {value} overflows PIC 9({destination.Length}).", nameof(value));
        }
    }

    /// <summary>Encodes an unsigned value into ASCII digit bytes, zero padded.</summary>
    public static void EncodeUnsigned(long value, Span<byte> destination)
    {
        ArgumentOutOfRangeException.ThrowIfNegative(value);
        for (int i = destination.Length - 1; i >= 0; i--)
        {
            destination[i] = (byte)('0' + (int)(value % 10));
            value /= 10;
        }

        if (value != 0)
        {
            throw new ArgumentException($"Value overflows PIC 9({destination.Length}).", nameof(value));
        }
    }

    private static int Digit(byte b, int index) =>
        b is >= (byte)'0' and <= (byte)'9'
            ? b - '0'
            : throw new FormatException($"Invalid zoned digit byte 0x{b:X2} at offset {index}.");

    private static decimal Pow10(int scale) => scale switch
    {
        0 => 1m,
        1 => 10m,
        2 => 100m,
        3 => 1_000m,
        4 => 10_000m,
        _ => throw new ArgumentOutOfRangeException(nameof(scale)),
    };
}
