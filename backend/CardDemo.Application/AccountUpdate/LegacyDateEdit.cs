namespace CardDemo.Application.AccountUpdate;

/// <summary>
/// Port of EDIT-DATE-CCYYMMDD / EDIT-DATE-OF-BIRTH (app/cpy/CSUTLDPY.cpy) with the working storage of
/// app/cpy/CSUTLDWY.cpy. Year, month and day are edited in sequence (each failure only records a message
/// when none is recorded yet), then the day/month/year combination checks run.
/// </summary>
public static class LegacyDateEdit
{
    public sealed record DateEditResult(bool IsValid, string? Message);

    public static DateEditResult EditDate(string name, string year, string month, string day)
    {
        string? message = null;
        var valid = true;

        var yearOk = EditYear(name, year, ref message);
        var monthOk = EditMonth(name, month, ref message);
        var dayOk = EditDay(name, day, ref message);
        valid = yearOk && monthOk && dayOk;

        if (monthOk && dayOk)
        {
            var mm = int.Parse(month);
            var dd = int.Parse(day);
            var is31DayMonth = mm is 1 or 3 or 5 or 7 or 8 or 10 or 12;
            if (!is31DayMonth && dd == 31)
            {
                Record(ref message, $"{name}:Cannot have 31 days in this month.");
                return new DateEditResult(false, message);
            }
            if (mm == 2 && dd == 30)
            {
                Record(ref message, $"{name}:Cannot have 30 days in this month.");
                return new DateEditResult(false, message);
            }
            if (mm == 2 && dd == 29 && yearOk)
            {
                var ccyy = int.Parse(year);
                var divisor = ccyy % 100 == 0 ? 400 : 4;
                if (ccyy % divisor != 0)
                {
                    Record(ref message, $"{name}:Not a leap year.Cannot have 29 days in this month.");
                    return new DateEditResult(false, message);
                }
            }
        }

        return new DateEditResult(valid, message);
    }

    /// <summary>EDIT-DATE-OF-BIRTH (CSUTLDPY.cpy:333-368): the date must be strictly before today.</summary>
    public static DateEditResult EditDateOfBirth(string name, DateOnly dateOfBirth, DateOnly today)
    {
        if (today > dateOfBirth)
        {
            return new DateEditResult(true, null);
        }
        return new DateEditResult(false, $"{name}:cannot be in the future ");
    }

    public static DateOnly ToDate(string year, string month, string day) =>
        new(int.Parse(year), int.Parse(month), int.Parse(day));

    private static bool EditYear(string name, string year, ref string? message)
    {
        if (IsBlank(year))
        {
            Record(ref message, $"{name} : Year must be supplied.");
            return false;
        }
        if (!IsDigits(year, 4))
        {
            Record(ref message, $"{name} must be 4 digit number.");
            return false;
        }
        var century = year[..2];
        if (century != "19" && century != "20")
        {
            Record(ref message, $"{name} : Century is not valid.");
            return false;
        }
        return true;
    }

    private static bool EditMonth(string name, string month, ref string? message)
    {
        if (IsBlank(month))
        {
            Record(ref message, $"{name} : Month must be supplied.");
            return false;
        }
        if (!IsDigits(month, 2) || int.Parse(month) is < 1 or > 12)
        {
            Record(ref message, $"{name}: Month must be a number between 1 and 12.");
            return false;
        }
        return true;
    }

    private static bool EditDay(string name, string day, ref string? message)
    {
        if (IsBlank(day))
        {
            Record(ref message, $"{name} : Day must be supplied.");
            return false;
        }
        if (!IsDigits(day, 2) || int.Parse(day) is < 1 or > 31)
        {
            Record(ref message, $"{name}:day must be a number between 1 and 31.");
            return false;
        }
        return true;
    }

    private static bool IsBlank(string value) => value.Trim(' ', '\0').Length == 0;

    private static bool IsDigits(string value, int width) => value.Length == width && value.All(char.IsAsciiDigit);

    private static void Record(ref string? message, string text) => message ??= text;
}
