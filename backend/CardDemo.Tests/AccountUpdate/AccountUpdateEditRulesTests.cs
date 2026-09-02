using CardDemo.Application.AccountUpdate;
using FluentAssertions;

namespace CardDemo.Tests.AccountUpdate;

/// <summary>1200-EDIT-MAP-INPUTS parity (FR-S03-04..07, 12..22).</summary>
public class AccountUpdateEditRulesTests
{
    private static readonly DateOnly Today = AccountUpdateTestData.Today;
    private static readonly AccountUpdateFields Original = AccountUpdateTestData.Fields();

    private static AccountUpdateEditOutcome Edit(AccountUpdateFields updated) =>
        AccountUpdateEditRules.EditChanges(Original, updated, Today);

    [Theory]
    [InlineData("", "No input received")]
    [InlineData("   ", "No input received")]
    [InlineData("*", "No input received")]
    [InlineData("00000000000", "Account Number if supplied must be a 11 digit Non-Zero Number")]
    [InlineData("1234567890A", "Account Number if supplied must be a 11 digit Non-Zero Number")]
    [InlineData("123", "Account Number if supplied must be a 11 digit Non-Zero Number")]
    public void SearchAccountId_RejectsBlankAndNonElevenDigitValues(string input, string expected)
    {
        AccountUpdateEditRules.EditSearchAccountId(input).Should().Be(expected);
    }

    [Fact]
    public void SearchAccountId_AcceptsElevenDigitNonZero()
    {
        AccountUpdateEditRules.EditSearchAccountId("00000000010").Should().BeNull();
    }

    [Fact]
    public void NoChanges_WhenEveryFieldMatchesTheFetchedValues()
    {
        var outcome = Edit(Original);

        outcome.ChangesDetected.Should().BeFalse();
        outcome.IsValid.Should().BeFalse();
        outcome.ErrorMessage.Should().Be("No change detected with respect to values fetched.");
    }

    [Fact]
    public void NoChanges_ComparesMoneyNumericallyAndTextCaseInsensitively()
    {
        var updated = Original with
        {
            CreditLimit = "10000",
            CurrentBalance = "$1,250.75",
            FirstName = "john",
            GroupId = "zeroapr"
        };

        Edit(updated).ChangesDetected.Should().BeFalse();
    }

    [Fact]
    public void Changes_AreDetectedForAnyDifferingField()
    {
        Edit(Original with { CreditLimit = "10001" }).ChangesDetected.Should().BeTrue();
        Edit(Original with { LastName = "OTHER" }).ChangesDetected.Should().BeTrue();
        Edit(Original with { Zip = "10002" }).ChangesDetected.Should().BeTrue();
        Edit(Original with { PrimaryCardHolder = "N" }).ChangesDetected.Should().BeTrue();
    }

    [Fact]
    public void ValidChanges_ProduceParsedValues()
    {
        var updated = Original with
        {
            CreditLimit = "12,500.99",
            ExpiryYear = "2028",
            ExpiryMonth = "02",
            ExpiryDay = "29",
            FicoScore = "801"
        };

        var outcome = Edit(updated);

        outcome.IsValid.Should().BeTrue();
        outcome.ErrorMessage.Should().BeNull();
        outcome.InvalidFields.Should().BeEmpty();
        outcome.Parsed!.CreditLimit.Should().Be(12500.99m);
        outcome.Parsed.ExpiryDate.Should().Be(new DateOnly(2028, 2, 29));
        outcome.Parsed.FicoScore.Should().Be(801);
        outcome.Parsed.DateOfBirth.Should().Be(new DateOnly(1980, 6, 20));
        outcome.Parsed.CurrentBalance.Should().Be(1250.75m);
    }

    [Fact]
    public void FirstErrorInSourceOrder_OwnsTheMessage_ButEveryBadFieldIsFlagged()
    {
        var updated = Original with { ActiveStatus = "", FicoScore = "200", LastName = "" };

        var outcome = Edit(updated);

        outcome.IsValid.Should().BeFalse();
        outcome.ErrorMessage.Should().Be("Account Status must be supplied.");
        outcome.InvalidFields.Should().Equal(
            AccountUpdateFieldNames.ActiveStatus,
            AccountUpdateFieldNames.FicoScore,
            AccountUpdateFieldNames.LastName);
    }

    [Theory]
    [InlineData("X", "Account Status must be Y or N.")]
    [InlineData("*", "Account Status must be supplied.")]
    [InlineData("0", "Account Status must be supplied.")]
    public void ActiveStatus_MustBeYOrN(string status, string expected)
    {
        Edit(Original with { ActiveStatus = status }).ErrorMessage.Should().Be(expected);
    }

    [Theory]
    [InlineData("", "Credit Limit must be supplied.")]
    [InlineData("abc", "Credit Limit is not valid")]
    [InlineData("1.2.3", "Credit Limit is not valid")]
    public void CreditLimit_MustBeSuppliedAndNumeric(string value, string expected)
    {
        Edit(Original with { CreditLimit = value }).ErrorMessage.Should().Be(expected);
    }

    [Fact]
    public void EachMonetaryField_UsesItsOwnLabel()
    {
        Edit(Original with { CashCreditLimit = "x" }).ErrorMessage.Should().Be("Cash Credit Limit is not valid");
        Edit(Original with { CurrentBalance = "" }).ErrorMessage.Should().Be("Current Balance must be supplied.");
        Edit(Original with { CurrentCycleCredit = "x" }).ErrorMessage.Should().Be("Current Cycle Credit Limit is not valid");
        Edit(Original with { CurrentCycleDebit = "x" }).ErrorMessage.Should().Be("Current Cycle Debit Limit is not valid");
    }

    [Theory]
    [InlineData("", "03", "15", "Open Date : Year must be supplied.")]
    [InlineData("19AB", "03", "15", "Open Date must be 4 digit number.")]
    [InlineData("1850", "03", "15", "Open Date : Century is not valid.")]
    [InlineData("2019", "", "15", "Open Date : Month must be supplied.")]
    [InlineData("2019", "13", "15", "Open Date: Month must be a number between 1 and 12.")]
    [InlineData("2019", "03", "", "Open Date : Day must be supplied.")]
    [InlineData("2019", "03", "32", "Open Date:day must be a number between 1 and 31.")]
    [InlineData("2019", "04", "31", "Open Date:Cannot have 31 days in this month.")]
    [InlineData("2019", "02", "30", "Open Date:Cannot have 30 days in this month.")]
    [InlineData("2019", "02", "29", "Open Date:Not a leap year.Cannot have 29 days in this month.")]
    public void OpenDate_FollowsCsutldpyEdits(string year, string month, string day, string expected)
    {
        var outcome = Edit(Original with { OpenYear = year, OpenMonth = month, OpenDay = day });

        outcome.ErrorMessage.Should().Be(expected);
        outcome.InvalidFields.Should().Contain(AccountUpdateFieldNames.OpenDate);
    }

    [Fact]
    public void ExpiryAndReissueDates_UseTheirOwnLabels()
    {
        Edit(Original with { ExpiryMonth = "00" }).ErrorMessage.Should().Be("Expiry Date: Month must be a number between 1 and 12.");
        Edit(Original with { ReissueDay = "00" }).ErrorMessage.Should().Be("Reissue Date:day must be a number between 1 and 31.");
    }

    [Theory]
    [InlineData("000", "SSN: First 3 chars must not be zero.")]
    [InlineData("666", "SSN: First 3 chars: should not be 000, 666, or between 900 and 999")]
    [InlineData("900", "SSN: First 3 chars: should not be 000, 666, or between 900 and 999")]
    [InlineData("12A", "SSN: First 3 chars must be all numeric.")]
    [InlineData("", "SSN: First 3 chars must be supplied.")]
    public void SsnPart1_Edits(string part1, string expected)
    {
        var outcome = Edit(Original with { Ssn1 = part1 });

        outcome.ErrorMessage.Should().Be(expected);
        outcome.InvalidFields.Should().Contain(AccountUpdateFieldNames.Ssn1);
    }

    [Fact]
    public void SsnParts2And3_MustBeNumericNonZero()
    {
        Edit(Original with { Ssn2 = "AB" }).ErrorMessage.Should().Be("SSN 4th & 5th chars must be all numeric.");
        Edit(Original with { Ssn3 = "0000" }).ErrorMessage.Should().Be("SSN Last 4 chars must not be zero.");
    }

    [Fact]
    public void DateOfBirth_CannotBeInTheFuture()
    {
        var outcome = Edit(Original with { DobYear = "2026", DobMonth = "09", DobDay = "02" });

        outcome.ErrorMessage.Should().Be("Date of Birth:cannot be in the future ");
        outcome.InvalidFields.Should().Contain(AccountUpdateFieldNames.DateOfBirth);
    }

    [Theory]
    [InlineData("299", "FICO Score: should be between 300 and 850")]
    [InlineData("851", "FICO Score: should be between 300 and 850")]
    [InlineData("", "FICO Score must be supplied.")]
    [InlineData("7A0", "FICO Score must be all numeric.")]
    public void FicoScore_Edits(string fico, string expected)
    {
        Edit(Original with { FicoScore = fico }).ErrorMessage.Should().Be(expected);
    }

    [Fact]
    public void Names_AlphabeticRules()
    {
        Edit(Original with { FirstName = "" }).ErrorMessage.Should().Be("First Name must be supplied.");
        Edit(Original with { FirstName = "J0HN" }).ErrorMessage.Should().Be("First Name can have alphabets only.");
        Edit(Original with { MiddleName = "" }).IsValid.Should().BeTrue();
        Edit(Original with { MiddleName = "Q1" }).ErrorMessage.Should().Be("Middle Name can have alphabets only.");
        Edit(Original with { LastName = "PUBLIC-X" }).ErrorMessage.Should().Be("Last Name can have alphabets only.");
        Edit(Original with { FirstName = "MARY ANN" }).IsValid.Should().BeTrue();
    }

    [Fact]
    public void AddressLine1_IsMandatoryOnly()
    {
        Edit(Original with { AddressLine1 = "" }).ErrorMessage.Should().Be("Address Line 1 must be supplied.");
        Edit(Original with { AddressLine1 = "12 #5 ST." }).IsValid.Should().BeTrue();
    }

    [Fact]
    public void AddressLine2_IsNotEdited()
    {
        Edit(Original with { AddressLine2 = "" }).IsValid.Should().BeTrue();
    }

    [Theory]
    [InlineData("", "State must be supplied.")]
    [InlineData("N1", "State can have alphabets only.")]
    [InlineData("ZZ", "State: is not a valid state code")]
    public void State_Edits(string state, string expected)
    {
        var outcome = Edit(Original with { State = state });

        outcome.ErrorMessage.Should().Be(expected);
        outcome.InvalidFields.Should().Contain(AccountUpdateFieldNames.State);
    }

    [Theory]
    [InlineData("", "Zip must be supplied.")]
    [InlineData("1000A", "Zip must be all numeric.")]
    [InlineData("00000", "Zip must not be zero.")]
    public void Zip_Edits(string zip, string expected)
    {
        Edit(Original with { Zip = zip }).ErrorMessage.Should().Be(expected);
    }

    [Fact]
    public void StateZipCombination_IsCheckedLastAndFlagsBothFields()
    {
        var outcome = Edit(Original with { State = "CA", Zip = "10001" });

        outcome.ErrorMessage.Should().Be("Invalid zip code for state");
        outcome.InvalidFields.Should().Equal(AccountUpdateFieldNames.State, AccountUpdateFieldNames.Zip);
    }

    [Fact]
    public void StateZipCombination_AcceptsMatchingPrefix()
    {
        Edit(Original with { State = "CA", Zip = "90210" }).IsValid.Should().BeTrue();
    }

    [Fact]
    public void StateZipCombination_IsSkippedWhenEitherFieldAlreadyFailed()
    {
        var outcome = Edit(Original with { State = "ZZ", Zip = "10001" });

        outcome.ErrorMessage.Should().Be("State: is not a valid state code");
        outcome.InvalidFields.Should().NotContain(AccountUpdateFieldNames.Zip);
    }

    [Fact]
    public void CityAndCountry_AreAlphabeticRequired()
    {
        Edit(Original with { City = "" }).ErrorMessage.Should().Be("City must be supplied.");
        Edit(Original with { Country = "US1" }).ErrorMessage.Should().Be("Country can have alphabets only.");
    }

    [Theory]
    [InlineData("", "555", "1234", "Phone Number 1: Area code must be supplied.")]
    [InlineData("21", "555", "1234", "Phone Number 1: Area code must be A 3 digit number.")]
    [InlineData("000", "555", "1234", "Phone Number 1: Area code cannot be zero")]
    [InlineData("999", "555", "1234", "Phone Number 1: Not valid North America general purpose area code")]
    [InlineData("212", "", "1234", "Phone Number 1: Prefix code must be supplied.")]
    [InlineData("212", "5A5", "1234", "Phone Number 1: Prefix code must be A 3 digit number.")]
    [InlineData("212", "000", "1234", "Phone Number 1: Prefix code cannot be zero")]
    [InlineData("212", "555", "", "Phone Number 1: Line number code must be supplied.")]
    [InlineData("212", "555", "123", "Phone Number 1: Line number code must be A 4 digit number.")]
    [InlineData("212", "555", "0000", "Phone Number 1: Line number code cannot be zero")]
    public void Phone1_Edits(string area, string prefix, string line, string expected)
    {
        var outcome = Edit(Original with { Phone1Area = area, Phone1Prefix = prefix, Phone1Line = line });

        outcome.ErrorMessage.Should().Be(expected);
        outcome.InvalidFields.Should().Contain(AccountUpdateFieldNames.Phone1);
    }

    [Fact]
    public void Phone_BlankAreaAndPrefixTogether_MeansNoPhone()
    {
        Edit(Original with { Phone2Area = "", Phone2Prefix = "", Phone2Line = "" }).IsValid.Should().BeTrue();
        Edit(Original with { Phone2Area = "", Phone2Prefix = "555", Phone2Line = "9876" }).ErrorMessage
            .Should().Be("Phone Number 2: Area code must be supplied.");
    }

    [Fact]
    public void EftAccountId_MustBeNumericNonZero()
    {
        Edit(Original with { EftAccountId = "" }).ErrorMessage.Should().Be("EFT Account Id must be supplied.");
        Edit(Original with { EftAccountId = "12345678A0" }).ErrorMessage.Should().Be("EFT Account Id must be all numeric.");
        Edit(Original with { EftAccountId = "0000000000" }).ErrorMessage.Should().Be("EFT Account Id must not be zero.");
    }

    [Fact]
    public void PrimaryCardHolder_MustBeYOrN()
    {
        Edit(Original with { PrimaryCardHolder = "Q" }).ErrorMessage.Should().Be("Primary Card Holder must be Y or N.");
        Edit(Original with { PrimaryCardHolder = "" }).ErrorMessage.Should().Be("Primary Card Holder must be supplied.");
    }

    [Fact]
    public void ValidationOrder_MatchesTheCobolParagraphSequence()
    {
        var everythingWrong = Original with
        {
            ActiveStatus = "X",
            OpenMonth = "13",
            CreditLimit = "x",
            ExpiryMonth = "13",
            CashCreditLimit = "x",
            ReissueMonth = "13",
            CurrentBalance = "x",
            CurrentCycleCredit = "x",
            CurrentCycleDebit = "x",
            Ssn1 = "666",
            DobMonth = "13",
            FicoScore = "1",
            FirstName = "1",
            MiddleName = "1",
            LastName = "1",
            AddressLine1 = "",
            State = "ZZ",
            Zip = "0",
            City = "1",
            Country = "1",
            Phone1Area = "999",
            Phone2Area = "999",
            EftAccountId = "A",
            PrimaryCardHolder = "X"
        };

        var outcome = Edit(everythingWrong);

        outcome.ErrorMessage.Should().Be("Account Status must be Y or N.");
        outcome.InvalidFields.Should().Equal(
            AccountUpdateFieldNames.ActiveStatus,
            AccountUpdateFieldNames.OpenDate,
            AccountUpdateFieldNames.CreditLimit,
            AccountUpdateFieldNames.ExpiryDate,
            AccountUpdateFieldNames.CashCreditLimit,
            AccountUpdateFieldNames.ReissueDate,
            AccountUpdateFieldNames.CurrentBalance,
            AccountUpdateFieldNames.CurrentCycleCredit,
            AccountUpdateFieldNames.CurrentCycleDebit,
            AccountUpdateFieldNames.Ssn1,
            AccountUpdateFieldNames.DateOfBirth,
            AccountUpdateFieldNames.FicoScore,
            AccountUpdateFieldNames.FirstName,
            AccountUpdateFieldNames.MiddleName,
            AccountUpdateFieldNames.LastName,
            AccountUpdateFieldNames.AddressLine1,
            AccountUpdateFieldNames.State,
            AccountUpdateFieldNames.Zip,
            AccountUpdateFieldNames.City,
            AccountUpdateFieldNames.Country,
            AccountUpdateFieldNames.Phone1,
            AccountUpdateFieldNames.Phone2,
            AccountUpdateFieldNames.EftAccountId,
            AccountUpdateFieldNames.PrimaryCardHolder);
    }

    [Fact]
    public void SnapshotMatches_DetectsStoredRowDrift()
    {
        var current = AccountUpdateTestData.Fields();

        AccountUpdateEditRules.SnapshotMatches(Original, current).Should().BeTrue();
        AccountUpdateEditRules.SnapshotMatches(Original, current with { CreditLimit = "+     9,999.00" }).Should().BeFalse();
        AccountUpdateEditRules.SnapshotMatches(Original, current with { FicoScore = "721" }).Should().BeFalse();
        AccountUpdateEditRules.SnapshotMatches(Original, current with { FirstName = "john" }).Should().BeTrue();
        AccountUpdateEditRules.SnapshotMatches(Original, current with { Phone1Line = "0000" }).Should().BeFalse();
    }
}
