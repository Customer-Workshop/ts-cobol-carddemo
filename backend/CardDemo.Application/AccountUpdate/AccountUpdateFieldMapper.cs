using System.Globalization;
using CardDemo.Domain.Accounts;
using CardDemo.Domain.Customers;

namespace CardDemo.Application.AccountUpdate;

/// <summary>
/// 9500-STORE-FETCHED-DATA + 3202-SHOW-ORIGINAL-VALUES (app/cbl/COACTUPC.cbl:3801-3885, :2787-2867) going out,
/// 9600-WRITE-PROCESSING record building (:3945-4050) going in.
/// </summary>
public static class AccountUpdateFieldMapper
{
    public static AccountUpdateFields FromEntities(Account account, Customer customer)
    {
        var (openY, openM, openD) = SplitDate(account.OpenDate);
        var (expY, expM, expD) = SplitDate(account.ExpirationDate);
        var (risY, risM, risD) = SplitDate(account.ReissueDate);
        var (dobY, dobM, dobD) = SplitDate(customer.DateOfBirth);
        var (p1a, p1b, p1c) = SplitPhone(customer.PhoneNumber1);
        var (p2a, p2b, p2c) = SplitPhone(customer.PhoneNumber2);
        var ssn = customer.Ssn.PadRight(9);

        return new AccountUpdateFields
        {
            AccountId = account.AccountId,
            ActiveStatus = account.ActiveStatus,
            OpenYear = openY,
            OpenMonth = openM,
            OpenDay = openD,
            CreditLimit = FormatCurrency(account.CreditLimit),
            ExpiryYear = expY,
            ExpiryMonth = expM,
            ExpiryDay = expD,
            CashCreditLimit = FormatCurrency(account.CashCreditLimit),
            ReissueYear = risY,
            ReissueMonth = risM,
            ReissueDay = risD,
            CurrentBalance = FormatCurrency(account.CurrentBalance),
            CurrentCycleCredit = FormatCurrency(account.CurrentCycleCredit),
            GroupId = account.GroupId,
            CurrentCycleDebit = FormatCurrency(account.CurrentCycleDebit),
            CustomerId = customer.CustomerId,
            Ssn1 = ssn[..3],
            Ssn2 = ssn[3..5],
            Ssn3 = ssn[5..9],
            DobYear = dobY,
            DobMonth = dobM,
            DobDay = dobD,
            FicoScore = customer.FicoCreditScore.ToString("000", CultureInfo.InvariantCulture),
            FirstName = customer.FirstName,
            MiddleName = customer.MiddleName,
            LastName = customer.LastName,
            AddressLine1 = customer.AddressLine1,
            AddressLine2 = customer.AddressLine2,
            State = customer.AddressStateCode,
            Zip = Clip(customer.AddressZip, 5),
            City = customer.AddressLine3,
            Country = customer.AddressCountryCode,
            Phone1Area = p1a,
            Phone1Prefix = p1b,
            Phone1Line = p1c,
            Phone2Area = p2a,
            Phone2Prefix = p2b,
            Phone2Line = p2c,
            GovernmentId = customer.GovernmentIssuedId,
            EftAccountId = customer.EftAccountId,
            PrimaryCardHolder = customer.PrimaryCardHolderIndicator
        };
    }

    public static void ApplyToAccount(Account account, AccountUpdateFields updated, AccountUpdateParsedValues parsed)
    {
        account.ActiveStatus = updated.ActiveStatus;
        account.CurrentBalance = parsed.CurrentBalance;
        account.CreditLimit = parsed.CreditLimit;
        account.CashCreditLimit = parsed.CashCreditLimit;
        account.CurrentCycleCredit = parsed.CurrentCycleCredit;
        account.CurrentCycleDebit = parsed.CurrentCycleDebit;
        account.OpenDate = parsed.OpenDate;
        account.ExpirationDate = parsed.ExpiryDate;
        account.ReissueDate = parsed.ReissueDate;
        account.GroupId = updated.GroupId.TrimEnd();
    }

    public static void ApplyToCustomer(Customer customer, AccountUpdateFields updated, AccountUpdateParsedValues parsed)
    {
        customer.FirstName = updated.FirstName.TrimEnd();
        customer.MiddleName = updated.MiddleName.TrimEnd();
        customer.LastName = updated.LastName.TrimEnd();
        customer.AddressLine1 = updated.AddressLine1.TrimEnd();
        customer.AddressLine2 = updated.AddressLine2.TrimEnd();
        customer.AddressLine3 = updated.City.TrimEnd();
        customer.AddressStateCode = updated.State;
        customer.AddressZip = updated.Zip.TrimEnd();
        customer.PhoneNumber1 = JoinPhone(updated.Phone1Area, updated.Phone1Prefix, updated.Phone1Line);
        customer.PhoneNumber2 = JoinPhone(updated.Phone2Area, updated.Phone2Prefix, updated.Phone2Line);
        customer.Ssn = updated.Ssn1 + updated.Ssn2 + updated.Ssn3;
        customer.GovernmentIssuedId = updated.GovernmentId.TrimEnd();
        customer.DateOfBirth = parsed.DateOfBirth;
        customer.EftAccountId = updated.EftAccountId;
        customer.PrimaryCardHolderIndicator = updated.PrimaryCardHolder;
        customer.FicoCreditScore = parsed.FicoScore;
    }

    /// <summary>WS-EDIT-CURRENCY-9-2-F PIC +ZZZ,ZZZ,ZZZ.99 (:371).</summary>
    public static string FormatCurrency(decimal value)
    {
        var sign = value < 0 ? '-' : '+';
        var magnitude = Math.Abs(value);
        var integerPart = decimal.Truncate(magnitude);
        var cents = (int)((magnitude - integerPart) * 100m);
        var integerText = integerPart == 0
            ? string.Empty
            : (integerPart % 1_000_000_000m).ToString("#,##0", CultureInfo.InvariantCulture);
        return $"{sign}{integerText,11}.{cents:00}";
    }

    private static (string Year, string Month, string Day) SplitDate(DateOnly? date) =>
        date is null
            ? (string.Empty, string.Empty, string.Empty)
            : (date.Value.Year.ToString("0000", CultureInfo.InvariantCulture),
               date.Value.Month.ToString("00", CultureInfo.InvariantCulture),
               date.Value.Day.ToString("00", CultureInfo.InvariantCulture));

    /// <summary>CUST-PHONE-NUM-n(2:3), (6:3), (10:4) of the X(15) "(aaa)bbb-cccc" layout.</summary>
    private static (string Area, string Prefix, string Line) SplitPhone(string phone)
    {
        var padded = phone.PadRight(15);
        return (padded.Substring(1, 3).TrimEnd(), padded.Substring(5, 3).TrimEnd(), padded.Substring(9, 4).TrimEnd());
    }

    private static string JoinPhone(string area, string prefix, string line) =>
        $"({Pad(area, 3)}){Pad(prefix, 3)}-{Pad(line, 4)}".TrimEnd();

    private static string Pad(string value, int width) => value.PadRight(width)[..width];

    private static string Clip(string value, int width) => value.Length <= width ? value : value[..width];
}
