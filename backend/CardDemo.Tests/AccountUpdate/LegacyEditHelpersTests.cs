using CardDemo.Application.AccountUpdate;
using FluentAssertions;

namespace CardDemo.Tests.AccountUpdate;

/// <summary>TEST-NUMVAL-C / NUMVAL-C (1250-EDIT-SIGNED-9V2), CSUTLDPY date edits, and the +ZZZ,ZZZ,ZZZ.99 edit picture.</summary>
public class LegacyEditHelpersTests
{
    [Theory]
    [InlineData("1000", 1000.00)]
    [InlineData("1,000.50", 1000.50)]
    [InlineData("+     1,000.00", 1000.00)]
    [InlineData("-         12.34", -12.34)]
    [InlineData("$2,500.00", 2500.00)]
    [InlineData("12.34-", -12.34)]
    [InlineData("12.34CR", -12.34)]
    [InlineData("12.34 DB", -12.34)]
    [InlineData(".50", 0.50)]
    [InlineData("7.", 7.00)]
    [InlineData("1.999", 1.99)]
    [InlineData("+           .00", 0.00)]
    public void Numval_AcceptsNumvalCShapes(string text, decimal expected)
    {
        LegacyNumval.TryParseSigned9V2(text, out var value).Should().BeTrue();
        value.Should().Be(expected);
    }

    [Theory]
    [InlineData("")]
    [InlineData("abc")]
    [InlineData("1.2.3")]
    [InlineData("+12-")]
    [InlineData("1,00")]
    [InlineData(".")]
    [InlineData("12 34")]
    public void Numval_RejectsNonNumvalCShapes(string text)
    {
        LegacyNumval.TryParseSigned9V2(text, out _).Should().BeFalse();
    }

    [Fact]
    public void Numval_TruncatesHighOrderDigitsBeyondS9V10()
    {
        LegacyNumval.TryParseSigned9V2("12345678901234.56", out var value).Should().BeTrue();
        value.Should().Be(5678901234.56m);
    }

    [Fact]
    public void DateEdit_AcceptsLeapDayInLeapYear()
    {
        var result = LegacyDateEdit.EditDate("Open Date", "2024", "02", "29");

        result.IsValid.Should().BeTrue();
        result.Message.Should().BeNull();
        LegacyDateEdit.ToDate("2024", "02", "29").Should().Be(new DateOnly(2024, 2, 29));
    }

    [Fact]
    public void DateEdit_CenturyRuleRejects1900And2100ButAccepts1999And2000()
    {
        LegacyDateEdit.EditDate("Open Date", "2100", "01", "01").Message.Should().Be("Open Date : Century is not valid.");
        LegacyDateEdit.EditDate("Open Date", "1800", "01", "01").Message.Should().Be("Open Date : Century is not valid.");
        LegacyDateEdit.EditDate("Open Date", "1999", "12", "31").IsValid.Should().BeTrue();
        LegacyDateEdit.EditDate("Open Date", "2000", "01", "01").IsValid.Should().BeTrue();
    }

    [Fact]
    public void DateOfBirth_MustBeBeforeToday()
    {
        var today = new DateOnly(2026, 9, 2);

        LegacyDateEdit.EditDateOfBirth("Date of Birth", new DateOnly(2026, 9, 1), today).IsValid.Should().BeTrue();
        LegacyDateEdit.EditDateOfBirth("Date of Birth", today, today).Message.Should().Be("Date of Birth:cannot be in the future ");
        LegacyDateEdit.EditDateOfBirth("Date of Birth", new DateOnly(2027, 1, 1), today).IsValid.Should().BeFalse();
    }

    [Theory]
    [InlineData(1000.50, "+      1,000.50")]
    [InlineData(-12.34, "-         12.34")]
    [InlineData(0, "+           .00")]
    [InlineData(123456789.01, "+123,456,789.01")]
    [InlineData(1234567890.12, "+234,567,890.12")]
    public void FormatCurrency_MatchesTheEditPicture(decimal value, string expected)
    {
        var text = AccountUpdateFieldMapper.FormatCurrency(value);

        text.Should().Be(expected);
        text.Should().HaveLength(15);
    }
}
