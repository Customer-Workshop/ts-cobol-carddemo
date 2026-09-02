namespace CardDemo.Application.AccountUpdate;

public sealed record AccountUpdateEditOutcome(
    bool ChangesDetected,
    bool IsValid,
    string? ErrorMessage,
    IReadOnlyList<string> InvalidFields,
    AccountUpdateParsedValues? Parsed);

/// <summary>Typed values that 1100-RECEIVE-MAP derives from the text fields once they pass the edits.</summary>
public sealed record AccountUpdateParsedValues(
    decimal CreditLimit,
    decimal CashCreditLimit,
    decimal CurrentBalance,
    decimal CurrentCycleCredit,
    decimal CurrentCycleDebit,
    DateOnly OpenDate,
    DateOnly ExpiryDate,
    DateOnly ReissueDate,
    DateOnly DateOfBirth,
    int FicoScore);

/// <summary>
/// Port of 1200-EDIT-MAP-INPUTS (app/cbl/COACTUPC.cbl:1429-1679) and 1205-COMPARE-OLD-NEW (:1681-1777)
/// plus the field-edit paragraphs 1210-1280 (:1783-2560). Edits run in source order; the first failure owns
/// the error message while every failing field is still flagged.
/// </summary>
public static class AccountUpdateEditRules
{
    /// <summary>1210-EDIT-ACCOUNT (:1783-1820) as driven by 1200 (:1434-1440). Returns null when the id is usable.</summary>
    public static string? EditSearchAccountId(string accountId)
    {
        if (IsBlank(accountId))
        {
            return AccountUpdateMessages.NoSearchCriteriaReceived;
        }
        if (accountId.Length != 11 || !accountId.All(char.IsAsciiDigit) || accountId.All(c => c == '0'))
        {
            return AccountUpdateMessages.AccountFilterNotValid;
        }
        return null;
    }

    public static AccountUpdateEditOutcome EditChanges(AccountUpdateFields old, AccountUpdateFields updated, DateOnly today)
    {
        if (!HasChanges(old, updated))
        {
            return new AccountUpdateEditOutcome(false, false, AccountUpdateMessages.NoChangesDetected, [], null);
        }

        var ctx = new EditContext();

        EditYesNo(ctx, "Account Status", updated.ActiveStatus, AccountUpdateFieldNames.ActiveStatus);
        var openDate = EditDate(ctx, "Open Date", updated.OpenYear, updated.OpenMonth, updated.OpenDay, AccountUpdateFieldNames.OpenDate);
        var creditLimit = EditSigned9V2(ctx, "Credit Limit", updated.CreditLimit, AccountUpdateFieldNames.CreditLimit);
        var expiryDate = EditDate(ctx, "Expiry Date", updated.ExpiryYear, updated.ExpiryMonth, updated.ExpiryDay, AccountUpdateFieldNames.ExpiryDate);
        var cashLimit = EditSigned9V2(ctx, "Cash Credit Limit", updated.CashCreditLimit, AccountUpdateFieldNames.CashCreditLimit);
        var reissueDate = EditDate(ctx, "Reissue Date", updated.ReissueYear, updated.ReissueMonth, updated.ReissueDay, AccountUpdateFieldNames.ReissueDate);
        var currentBalance = EditSigned9V2(ctx, "Current Balance", updated.CurrentBalance, AccountUpdateFieldNames.CurrentBalance);
        var cycleCredit = EditSigned9V2(ctx, "Current Cycle Credit Limit", updated.CurrentCycleCredit, AccountUpdateFieldNames.CurrentCycleCredit);
        var cycleDebit = EditSigned9V2(ctx, "Current Cycle Debit Limit", updated.CurrentCycleDebit, AccountUpdateFieldNames.CurrentCycleDebit);

        EditSsn(ctx, updated);

        var dob = EditDate(ctx, "Date of Birth", updated.DobYear, updated.DobMonth, updated.DobDay, AccountUpdateFieldNames.DateOfBirth);
        if (dob is not null)
        {
            var dobCheck = LegacyDateEdit.EditDateOfBirth("Date of Birth", dob.Value, today);
            if (!dobCheck.IsValid)
            {
                ctx.Fail(AccountUpdateFieldNames.DateOfBirth, dobCheck.Message!);
                dob = null;
            }
        }

        var fico = EditFico(ctx, updated.FicoScore);

        EditAlphaRequired(ctx, "First Name", updated.FirstName, AccountUpdateFieldNames.FirstName);
        EditAlphaOptional(ctx, "Middle Name", updated.MiddleName, AccountUpdateFieldNames.MiddleName);
        EditAlphaRequired(ctx, "Last Name", updated.LastName, AccountUpdateFieldNames.LastName);
        EditMandatory(ctx, "Address Line 1", updated.AddressLine1, AccountUpdateFieldNames.AddressLine1);

        var stateValid = EditAlphaRequired(ctx, "State", updated.State, AccountUpdateFieldNames.State)
            && EditUsStateCode(ctx, updated.State);
        var zipValid = EditNumericRequired(ctx, "Zip", updated.Zip, AccountUpdateFieldNames.Zip);

        EditAlphaRequired(ctx, "City", updated.City, AccountUpdateFieldNames.City);
        EditAlphaRequired(ctx, "Country", updated.Country, AccountUpdateFieldNames.Country);

        EditUsPhone(ctx, "Phone Number 1", updated.Phone1Area, updated.Phone1Prefix, updated.Phone1Line, AccountUpdateFieldNames.Phone1);
        EditUsPhone(ctx, "Phone Number 2", updated.Phone2Area, updated.Phone2Prefix, updated.Phone2Line, AccountUpdateFieldNames.Phone2);

        EditNumericRequired(ctx, "EFT Account Id", updated.EftAccountId, AccountUpdateFieldNames.EftAccountId);
        EditYesNo(ctx, "Primary Card Holder", updated.PrimaryCardHolder, AccountUpdateFieldNames.PrimaryCardHolder);

        if (stateValid && zipValid)
        {
            EditUsStateZip(ctx, updated.State, updated.Zip);
        }

        if (ctx.HasError)
        {
            return new AccountUpdateEditOutcome(true, false, ctx.FirstMessage, ctx.InvalidFields, null);
        }

        var parsed = new AccountUpdateParsedValues(
            creditLimit!.Value, cashLimit!.Value, currentBalance!.Value, cycleCredit!.Value, cycleDebit!.Value,
            openDate!.Value, expiryDate!.Value, reissueDate!.Value, dob!.Value, fico!.Value);
        return new AccountUpdateEditOutcome(true, true, null, [], parsed);
    }

    /// <summary>1205-COMPARE-OLD-NEW (:1681-1777).</summary>
    public static bool HasChanges(AccountUpdateFields old, AccountUpdateFields updated)
    {
        var accountSame =
            updated.AccountId == old.AccountId
            && SameText(updated.ActiveStatus, old.ActiveStatus)
            && SameMoney(updated.CurrentBalance, old.CurrentBalance)
            && SameMoney(updated.CreditLimit, old.CreditLimit)
            && SameMoney(updated.CashCreditLimit, old.CashCreditLimit)
            && SameDate(updated.OpenYear, updated.OpenMonth, updated.OpenDay, old.OpenYear, old.OpenMonth, old.OpenDay)
            && SameDate(updated.ExpiryYear, updated.ExpiryMonth, updated.ExpiryDay, old.ExpiryYear, old.ExpiryMonth, old.ExpiryDay)
            && SameDate(updated.ReissueYear, updated.ReissueMonth, updated.ReissueDay, old.ReissueYear, old.ReissueMonth, old.ReissueDay)
            && SameMoney(updated.CurrentCycleCredit, old.CurrentCycleCredit)
            && SameMoney(updated.CurrentCycleDebit, old.CurrentCycleDebit)
            && SameText(updated.GroupId, old.GroupId);
        if (!accountSame)
        {
            return true;
        }

        var customerSame =
            SameText(updated.CustomerId, old.CustomerId)
            && SameText(updated.FirstName, old.FirstName)
            && SameText(updated.MiddleName, old.MiddleName)
            && SameText(updated.LastName, old.LastName)
            && SameText(updated.AddressLine1, old.AddressLine1)
            && SameText(updated.AddressLine2, old.AddressLine2)
            && SameText(updated.City, old.City)
            && SameText(updated.State, old.State)
            && SameText(updated.Country, old.Country)
            && SameText(updated.Zip, old.Zip)
            && SameExact(updated.Phone1Area, old.Phone1Area)
            && SameExact(updated.Phone1Prefix, old.Phone1Prefix)
            && SameExact(updated.Phone1Line, old.Phone1Line)
            && SameExact(updated.Phone2Area, old.Phone2Area)
            && SameExact(updated.Phone2Prefix, old.Phone2Prefix)
            && SameExact(updated.Phone2Line, old.Phone2Line)
            && SameExact(updated.Ssn1 + updated.Ssn2 + updated.Ssn3, old.Ssn1 + old.Ssn2 + old.Ssn3)
            && SameText(updated.GovernmentId, old.GovernmentId)
            && SameDate(updated.DobYear, updated.DobMonth, updated.DobDay, old.DobYear, old.DobMonth, old.DobDay)
            && SameExact(updated.EftAccountId, old.EftAccountId)
            && SameText(updated.PrimaryCardHolder, old.PrimaryCardHolder)
            && SameExact(updated.FicoScore, old.FicoScore);
        return !customerSame;
    }

    /// <summary>9700-CHECK-CHANGE-IN-REC (:4109-4200): stored rows (mapped through the same derivations) vs the fetched snapshot.</summary>
    public static bool SnapshotMatches(AccountUpdateFields original, AccountUpdateFields current)
    {
        var accountSame =
            SameExact(current.ActiveStatus, original.ActiveStatus)
            && SameMoney(current.CurrentBalance, original.CurrentBalance)
            && SameMoney(current.CreditLimit, original.CreditLimit)
            && SameMoney(current.CashCreditLimit, original.CashCreditLimit)
            && SameMoney(current.CurrentCycleCredit, original.CurrentCycleCredit)
            && SameMoney(current.CurrentCycleDebit, original.CurrentCycleDebit)
            && SameDate(current.OpenYear, current.OpenMonth, current.OpenDay, original.OpenYear, original.OpenMonth, original.OpenDay)
            && SameDate(current.ExpiryYear, current.ExpiryMonth, current.ExpiryDay, original.ExpiryYear, original.ExpiryMonth, original.ExpiryDay)
            && SameDate(current.ReissueYear, current.ReissueMonth, current.ReissueDay, original.ReissueYear, original.ReissueMonth, original.ReissueDay)
            && SameText(current.GroupId, original.GroupId);
        if (!accountSame)
        {
            return false;
        }

        return SameText(current.FirstName, original.FirstName)
            && SameText(current.MiddleName, original.MiddleName)
            && SameText(current.LastName, original.LastName)
            && SameText(current.AddressLine1, original.AddressLine1)
            && SameText(current.AddressLine2, original.AddressLine2)
            && SameText(current.City, original.City)
            && SameText(current.State, original.State)
            && SameText(current.Country, original.Country)
            && SameExact(current.Zip, original.Zip)
            && SameExact(current.Phone1Area + current.Phone1Prefix + current.Phone1Line, original.Phone1Area + original.Phone1Prefix + original.Phone1Line)
            && SameExact(current.Phone2Area + current.Phone2Prefix + current.Phone2Line, original.Phone2Area + original.Phone2Prefix + original.Phone2Line)
            && SameExact(current.Ssn1 + current.Ssn2 + current.Ssn3, original.Ssn1 + original.Ssn2 + original.Ssn3)
            && SameText(current.GovernmentId, original.GovernmentId)
            && SameDate(current.DobYear, current.DobMonth, current.DobDay, original.DobYear, original.DobMonth, original.DobDay)
            && SameExact(current.EftAccountId, original.EftAccountId)
            && SameExact(current.PrimaryCardHolder, original.PrimaryCardHolder)
            && SameExact(current.FicoScore, original.FicoScore);
    }

    private static bool SameText(string a, string b) =>
        string.Equals(Normalize(a).Trim(), Normalize(b).Trim(), StringComparison.OrdinalIgnoreCase);

    private static bool SameExact(string a, string b) =>
        string.Equals(Normalize(a).TrimEnd(), Normalize(b).TrimEnd(), StringComparison.Ordinal);

    private static bool SameDate(string y1, string m1, string d1, string y2, string m2, string d2) =>
        SameExact(y1 + m1 + d1, y2 + m2 + d2);

    /// <summary>ACUP-NEW-*-N is only populated when TEST-NUMVAL-C passes; otherwise the X(12) stays blank and differs.</summary>
    private static bool SameMoney(string updated, string old)
    {
        if (IsBlank(updated) || !LegacyNumval.TryParseSigned9V2(updated, out var newValue))
        {
            return false;
        }
        return LegacyNumval.TryParseSigned9V2(old, out var oldValue) && newValue == oldValue;
    }

    /// <summary>Screen '*' (CSSETATY blank marker) and spaces both map to LOW-VALUES in 1100-RECEIVE-MAP.</summary>
    private static string Normalize(string value) => value.Trim() == "*" ? string.Empty : value;

    private static bool IsBlank(string value) => Normalize(value).Trim(' ', '\0').Length == 0;

    /// <summary>1220-EDIT-YESNO (:1856-1894).</summary>
    private static void EditYesNo(EditContext ctx, string name, string value, string field)
    {
        var v = Normalize(value).Trim();
        if (v.Length == 0 || v == "0")
        {
            ctx.Fail(field, $"{name} must be supplied.");
            return;
        }
        if (v is not ("Y" or "N"))
        {
            ctx.Fail(field, $"{name} must be Y or N.");
        }
    }

    private static DateOnly? EditDate(EditContext ctx, string name, string year, string month, string day, string field)
    {
        var result = LegacyDateEdit.EditDate(name, Normalize(year), Normalize(month), Normalize(day));
        if (!result.IsValid)
        {
            ctx.Fail(field, result.Message!);
            return null;
        }
        return LegacyDateEdit.ToDate(year, month, day);
    }

    /// <summary>1250-EDIT-SIGNED-9V2 (:2180-2221).</summary>
    private static decimal? EditSigned9V2(EditContext ctx, string name, string value, string field)
    {
        if (IsBlank(value))
        {
            ctx.Fail(field, $"{name} must be supplied.");
            return null;
        }
        if (!LegacyNumval.TryParseSigned9V2(value, out var parsed))
        {
            ctx.Fail(field, $"{name} is not valid");
            return null;
        }
        return parsed;
    }

    /// <summary>1215-EDIT-MANDATORY (:1824-1852).</summary>
    private static void EditMandatory(EditContext ctx, string name, string value, string field)
    {
        if (IsBlank(value))
        {
            ctx.Fail(field, $"{name} must be supplied.");
        }
    }

    /// <summary>1225-EDIT-ALPHA-REQD (:1898-1951).</summary>
    private static bool EditAlphaRequired(EditContext ctx, string name, string value, string field)
    {
        if (IsBlank(value))
        {
            ctx.Fail(field, $"{name} must be supplied.");
            return false;
        }
        if (!IsAlphaOnly(value))
        {
            ctx.Fail(field, $"{name} can have alphabets only.");
            return false;
        }
        return true;
    }

    /// <summary>1235-EDIT-ALPHA-OPT (:2012-2057).</summary>
    private static void EditAlphaOptional(EditContext ctx, string name, string value, string field)
    {
        if (IsBlank(value))
        {
            return;
        }
        if (!IsAlphaOnly(value))
        {
            ctx.Fail(field, $"{name} can have alphabets only.");
        }
    }

    private static bool IsAlphaOnly(string value) => value.All(c => char.IsAsciiLetter(c) || c == ' ');

    /// <summary>1245-EDIT-NUM-REQD (:2109-2176).</summary>
    private static bool EditNumericRequired(EditContext ctx, string name, string value, string field)
    {
        if (IsBlank(value))
        {
            ctx.Fail(field, $"{name} must be supplied.");
            return false;
        }
        if (!value.All(char.IsAsciiDigit))
        {
            ctx.Fail(field, $"{name} must be all numeric.");
            return false;
        }
        if (value.All(c => c == '0'))
        {
            ctx.Fail(field, $"{name} must not be zero.");
            return false;
        }
        return true;
    }

    /// <summary>1265-EDIT-US-SSN (:2431-2489).</summary>
    private static void EditSsn(EditContext ctx, AccountUpdateFields updated)
    {
        if (EditNumericRequired(ctx, "SSN: First 3 chars", updated.Ssn1, AccountUpdateFieldNames.Ssn1))
        {
            var part1 = int.Parse(updated.Ssn1);
            if (part1 == 0 || part1 == 666 || part1 is >= 900 and <= 999)
            {
                ctx.Fail(AccountUpdateFieldNames.Ssn1, "SSN: First 3 chars: should not be 000, 666, or between 900 and 999");
            }
        }
        EditNumericRequired(ctx, "SSN 4th & 5th chars", updated.Ssn2, AccountUpdateFieldNames.Ssn2);
        EditNumericRequired(ctx, "SSN Last 4 chars", updated.Ssn3, AccountUpdateFieldNames.Ssn3);
    }

    /// <summary>1245 then 1275-EDIT-FICO-SCORE (:1545-1558, :2514-2531).</summary>
    private static int? EditFico(EditContext ctx, string value)
    {
        if (!EditNumericRequired(ctx, "FICO Score", value, AccountUpdateFieldNames.FicoScore))
        {
            return null;
        }
        var score = int.Parse(value);
        if (score is < 300 or > 850)
        {
            ctx.Fail(AccountUpdateFieldNames.FicoScore, "FICO Score: should be between 300 and 850");
            return null;
        }
        return score;
    }

    /// <summary>1270-EDIT-US-STATE-CD (:2493-2511).</summary>
    private static bool EditUsStateCode(EditContext ctx, string state)
    {
        if (!LegacyLookupCodes.IsUsStateCode(state))
        {
            ctx.Fail(AccountUpdateFieldNames.State, "State: is not a valid state code");
            return false;
        }
        return true;
    }

    /// <summary>1280-EDIT-US-STATE-ZIP-CD (:2536-2558).</summary>
    private static void EditUsStateZip(EditContext ctx, string state, string zip)
    {
        if (!LegacyLookupCodes.IsUsStateZipCombo(state + zip[..2]))
        {
            ctx.Fail(AccountUpdateFieldNames.State, "Invalid zip code for state");
            ctx.Flag(AccountUpdateFieldNames.Zip);
        }
    }

    /// <summary>1260-EDIT-US-PHONE-NUM (:2225-2427). Area and prefix blank together means "no phone".</summary>
    private static void EditUsPhone(EditContext ctx, string name, string area, string prefix, string line, string field)
    {
        if (IsBlank(area) && IsBlank(prefix))
        {
            return;
        }

        if (IsBlank(area))
        {
            ctx.Fail(field, $"{name}: Area code must be supplied.");
        }
        else if (!IsDigits(area, 3))
        {
            ctx.Fail(field, $"{name}: Area code must be A 3 digit number.");
        }
        else if (area == "000")
        {
            ctx.Fail(field, $"{name}: Area code cannot be zero");
        }
        else if (!LegacyLookupCodes.IsGeneralPurposeAreaCode(area))
        {
            ctx.Fail(field, $"{name}: Not valid North America general purpose area code");
        }

        if (IsBlank(prefix))
        {
            ctx.Fail(field, $"{name}: Prefix code must be supplied.");
        }
        else if (!IsDigits(prefix, 3))
        {
            ctx.Fail(field, $"{name}: Prefix code must be A 3 digit number.");
        }
        else if (prefix == "000")
        {
            ctx.Fail(field, $"{name}: Prefix code cannot be zero");
        }

        if (IsBlank(line))
        {
            ctx.Fail(field, $"{name}: Line number code must be supplied.");
        }
        else if (!IsDigits(line, 4))
        {
            ctx.Fail(field, $"{name}: Line number code must be A 4 digit number.");
        }
        else if (line == "0000")
        {
            ctx.Fail(field, $"{name}: Line number code cannot be zero");
        }
    }

    private static bool IsDigits(string value, int width) => value.Length == width && value.All(char.IsAsciiDigit);

    private sealed class EditContext
    {
        private readonly List<string> _invalidFields = [];

        public string? FirstMessage { get; private set; }
        public bool HasError => FirstMessage is not null;
        public IReadOnlyList<string> InvalidFields => _invalidFields;

        public void Fail(string field, string message)
        {
            FirstMessage ??= message;
            Flag(field);
        }

        public void Flag(string field)
        {
            if (!_invalidFields.Contains(field))
            {
                _invalidFields.Add(field);
            }
        }
    }
}
