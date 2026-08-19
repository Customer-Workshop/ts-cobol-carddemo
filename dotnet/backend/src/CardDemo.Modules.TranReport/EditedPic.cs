using System.Globalization;

namespace CardDemo.Modules.TranReport;

/// <summary>
/// COBOL numeric-edited MOVE semantics for the two 15-character report
/// pictures in CVTRA07Y: a fixed leading sign position followed by
/// 'ZZZ,ZZZ,ZZZ.ZZ' with full zero suppression (an all-zero value edits
/// to all spaces; suppression replaces leading zeros and their commas
/// with spaces and stops at the first significant digit or the point).
/// </summary>
public static class EditedPic
{
    public const int Width = 15;

    /// <summary>PIC -ZZZ,ZZZ,ZZZ.ZZ: '-' when negative, space otherwise.</summary>
    public static string MinusEdited(decimal value) =>
        Format(value, value < 0 ? '-' : ' ');

    /// <summary>PIC +ZZZ,ZZZ,ZZZ.ZZ: '-' when negative, '+' otherwise.</summary>
    public static string PlusEdited(decimal value) =>
        Format(value, value < 0 ? '-' : '+');

    private static string Format(decimal value, char sign)
    {
        // MOVE to S9(9)V99-shaped edited field: truncate beyond two
        // decimals, keep the low-order nine integer digits.
        decimal hundredths = decimal.Truncate(Math.Abs(value) * 100m) % 100_000_000_000m;
        if (hundredths == 0m)
        {
            return new string(' ', Width);
        }

        string d = ((long)hundredths).ToString("D11", CultureInfo.InvariantCulture);
        char[] field = $"{sign}{d[..3]},{d[3..6]},{d[6..9]}.{d[9..]}".ToCharArray();
        for (int i = 1; i < field.Length && field[i] != '.'; i++)
        {
            if (field[i] is not ('0' or ','))
            {
                break;
            }

            field[i] = ' ';
        }

        return new string(field);
    }
}
