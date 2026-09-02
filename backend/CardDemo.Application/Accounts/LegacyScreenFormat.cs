using System.Text;

namespace CardDemo.Application.Accounts;

/// <summary>
/// COBOL/BMS output editing used by the account view map (app/bms/COACTVW.bms):
/// the <c>+ZZZ,ZZZ,ZZZ.99</c> amount picture, alphanumeric MOVE truncation and the
/// STRING ... DELIMITED BY SIZE behaviour into a fixed-width receiver.
/// </summary>
public static class LegacyScreenFormat
{
    private const string AmountPicture = "ZZZ,ZZZ,ZZZ";
    private const decimal IntegerModulus = 1_000_000_000m;

    /// <summary>
    /// PIC +ZZZ,ZZZ,ZZZ.99 (15 chars) from a S9(10)V99 value: fixed sign, zero suppression that
    /// also blanks the commas inside the suppressed region, truncation (not rounding) of extra
    /// decimals and of the tenth integer digit. Zero renders as <c>+           .00</c>.
    /// </summary>
    public static string EditedAmount(decimal value)
    {
        var sign = value < 0 ? '-' : '+';
        var cents = decimal.Truncate(Math.Abs(value) * 100m);
        var integerPart = decimal.Truncate(cents / 100m) % IntegerModulus;
        var fraction = (int)(cents % 100m);
        var digits = ((long)integerPart).ToString("D9");

        var edited = new StringBuilder(15).Append(sign);
        var suppressing = true;
        var digitIndex = 0;
        foreach (var symbol in AmountPicture)
        {
            if (symbol == ',')
            {
                edited.Append(suppressing ? ' ' : ',');
                continue;
            }
            var digit = digits[digitIndex++];
            if (suppressing && digit == '0')
            {
                edited.Append(' ');
            }
            else
            {
                suppressing = false;
                edited.Append(digit);
            }
        }
        return edited.Append('.').Append(fraction.ToString("D2")).ToString();
    }

    /// <summary>MOVE X(n) TO X(width): left-justified, truncated on the right; trailing blanks dropped for the web.</summary>
    public static string Fit(string value, int width) =>
        (value.Length > width ? value[..width] : value).TrimEnd();

    /// <summary>MOVE X(n) TO X(width) keeping the blank padding (used inside STRING receivers).</summary>
    public static string Pad(string value, int width) =>
        value.Length > width ? value[..width] : value.PadRight(width);

    /// <summary>X(10) date field: yyyy-MM-dd text, blank when the record holds no valid date.</summary>
    public static string IsoDate(DateOnly? date) => date?.ToString("yyyy-MM-dd") ?? string.Empty;

    /// <summary>MOVE S9(09) COMP TO PIC X(10): nine unsigned digits left-justified, one trailing blank.</summary>
    public static string RespCode(int code) => Math.Abs(code).ToString("D9").PadRight(10);

    /// <summary>STRING parts DELIMITED BY SIZE INTO a PIC X(width) receiver: overflow past the width is dropped.</summary>
    public static string StringInto(int width, params string[] parts)
    {
        var joined = string.Concat(parts);
        return joined.Length > width ? joined[..width] : joined;
    }
}
