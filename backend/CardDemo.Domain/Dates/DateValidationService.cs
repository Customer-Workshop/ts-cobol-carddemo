namespace CardDemo.Domain.Dates;

/// <summary>
/// Port of CSUTLDTC (app/cbl/CSUTLDTC.cbl): validates a date string against a CEEDAYS picture mask
/// and classifies the outcome with the LE feedback codes the COBOL decodes (CSUTLDTC.cbl:62-70).
/// Supported mask tokens: YYYY, MM, DD and literal separators. Dates before the Lillian epoch
/// (1582-10-15) are reported as unsupported range (2513), which COTRN02C accepts.
/// </summary>
public class DateValidationService
{
    public const string DefaultMask = "YYYY-MM-DD";

    private static readonly DateOnly LillianEpoch = new(1582, 10, 15);

    public DateValidationResult Validate(string? date, string? mask = DefaultMask)
    {
        var dateText = (date ?? string.Empty).TrimEnd();
        var maskText = (mask ?? string.Empty).TrimEnd();

        if (!TryParseMask(maskText, out var yearAt, out var monthAt, out var dayAt))
        {
            return Fail(DateValidationResult.BadPictureStringMessage, "Bad Pic String", dateText, maskText);
        }
        if (dateText.Length < maskText.Length)
        {
            return Fail(DateValidationResult.InsufficientDataMessage, "Insufficient", dateText, maskText);
        }
        for (var i = 0; i < maskText.Length; i++)
        {
            var expected = maskText[i];
            var actual = dateText[i];
            var matches = expected is 'Y' or 'M' or 'D' ? char.IsAsciiDigit(actual) : actual == expected;
            if (!matches)
            {
                return Fail(DateValidationResult.NonNumericDataMessage, "Nonnumeric data", dateText, maskText);
            }
        }

        var year = int.Parse(dateText.AsSpan(yearAt, 4));
        var month = int.Parse(dateText.AsSpan(monthAt, 2));
        var day = int.Parse(dateText.AsSpan(dayAt, 2));

        if (month is < 1 or > 12)
        {
            return Fail(DateValidationResult.InvalidMonthMessage, "Invalid month", dateText, maskText);
        }
        if (day < 1 || day > DaysInMonth(year, month))
        {
            return Fail(DateValidationResult.BadDateValueMessage, "Datevalue error", dateText, maskText);
        }

        var parsed = year >= 1 ? new DateOnly(year, month, day) : (DateOnly?)null;
        if (parsed is null || parsed < LillianEpoch)
        {
            return new DateValidationResult(3, DateValidationResult.UnsupportedRangeMessage, "Unsupp. Range", dateText, maskText, parsed);
        }

        return new DateValidationResult(0, DateValidationResult.ValidMessage, "Date is valid", dateText, maskText, parsed);
    }

    private static DateValidationResult Fail(int messageNumber, string verdict, string dateText, string maskText) =>
        new(3, messageNumber, verdict, dateText, maskText, null);

    private static bool TryParseMask(string mask, out int yearAt, out int monthAt, out int dayAt)
    {
        yearAt = mask.IndexOf("YYYY", StringComparison.Ordinal);
        monthAt = mask.IndexOf("MM", StringComparison.Ordinal);
        dayAt = mask.IndexOf("DD", StringComparison.Ordinal);
        if (yearAt < 0 || monthAt < 0 || dayAt < 0)
        {
            return false;
        }

        var covered = new bool[mask.Length];
        foreach (var (start, length) in new[] { (yearAt, 4), (monthAt, 2), (dayAt, 2) })
        {
            for (var i = start; i < start + length; i++)
            {
                if (covered[i])
                {
                    return false;
                }
                covered[i] = true;
            }
        }
        for (var i = 0; i < mask.Length; i++)
        {
            if (!covered[i] && char.IsAsciiLetter(mask[i]))
            {
                return false;
            }
        }
        return true;
    }

    private static int DaysInMonth(int year, int month)
    {
        if (month == 2)
        {
            var leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
            return leap ? 29 : 28;
        }
        return month is 4 or 6 or 9 or 11 ? 30 : 31;
    }
}
