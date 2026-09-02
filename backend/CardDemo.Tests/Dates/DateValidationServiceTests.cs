using CardDemo.Domain.Dates;
using FluentAssertions;

namespace CardDemo.Tests.Dates;

/// <summary>CSUTLDTC port: feedback classification and the 80-byte result layout (FR-S09-29..32, CSUTLDTC-01..10).</summary>
public class DateValidationServiceTests
{
    private readonly DateValidationService _service = new();

    [Theory]
    [InlineData("2024-01-15", 2024, 1, 15)]
    [InlineData("2024-02-29", 2024, 2, 29)]
    [InlineData("2000-02-29", 2000, 2, 29)]
    [InlineData("1582-10-15", 1582, 10, 15)]
    [InlineData("9999-12-31", 9999, 12, 31)]
    public void ValidDate_SeverityZero_MessageZero_DateIsValid(string text, int y, int m, int d)
    {
        var result = _service.Validate(text, "YYYY-MM-DD");

        result.IsValid.Should().BeTrue();
        result.ReturnCode.Should().Be(0);
        result.Severity.Should().Be(0);
        result.MessageNumber.Should().Be(0);
        result.Verdict.Should().Be("Date is valid");
        result.Date.Should().Be(new DateOnly(y, m, d));
    }

    [Theory]
    [InlineData("2023-02-29")]
    [InlineData("1900-02-29")]
    [InlineData("2023-04-31")]
    [InlineData("2023-06-00")]
    [InlineData("2023-01-32")]
    public void DayOutsideMonth_Is2508_DatevalueError(string text)
    {
        var result = _service.Validate(text, "YYYY-MM-DD");

        result.IsValid.Should().BeFalse();
        result.Severity.Should().Be(3);
        result.ReturnCode.Should().Be(3);
        result.MessageNumber.Should().Be(2508);
        result.Verdict.Should().Be("Datevalue error");
        result.Date.Should().BeNull();
    }

    [Theory]
    [InlineData("2023-00-10")]
    [InlineData("2023-13-01")]
    [InlineData("2023-99-01")]
    public void MonthOutsideOneToTwelve_Is2517_InvalidMonth(string text)
    {
        var result = _service.Validate(text, "YYYY-MM-DD");

        result.MessageNumber.Should().Be(2517);
        result.Verdict.Should().Be("Invalid month");
        result.Severity.Should().Be(3);
    }

    [Fact]
    public void MonthIsCheckedBeforeDay()
    {
        _service.Validate("2023-13-45", "YYYY-MM-DD").MessageNumber.Should().Be(2517);
    }

    [Theory]
    [InlineData("1582-10-14", 1582, 10, 14)]
    [InlineData("1500-01-01", 1500, 1, 1)]
    [InlineData("0001-01-01", 1, 1, 1)]
    public void BeforeLillianEpoch_Is2513_UnsuppRange_WithTheDate(string text, int y, int m, int d)
    {
        var result = _service.Validate(text, "YYYY-MM-DD");

        result.IsValid.Should().BeFalse();
        result.Severity.Should().Be(3);
        result.MessageNumber.Should().Be(2513);
        result.Verdict.Should().Be("Unsupp. Range");
        result.Date.Should().Be(new DateOnly(y, m, d));
    }

    [Fact]
    public void YearZero_Is2513_WithoutARepresentableDate()
    {
        var result = _service.Validate("0000-01-01", "YYYY-MM-DD");

        result.MessageNumber.Should().Be(2513);
        result.Date.Should().BeNull();
    }

    [Theory]
    [InlineData("2023-1-150")]
    [InlineData("2023/01/15")]
    [InlineData("2023-01-1A")]
    [InlineData("ABCD-01-15")]
    public void NonDigitWhereMaskExpectsDigit_OrWrongSeparator_Is2520_NonnumericData(string text)
    {
        var result = _service.Validate(text, "YYYY-MM-DD");

        result.MessageNumber.Should().Be(2520);
        result.Verdict.Should().Be("Nonnumeric data");
    }

    [Theory]
    [InlineData("")]
    [InlineData("2023-01")]
    [InlineData("2023-01-1")]
    [InlineData("2023-1-15")]
    [InlineData(null)]
    public void ShorterThanMask_Is2507_Insufficient(string? text)
    {
        var result = _service.Validate(text, "YYYY-MM-DD");

        result.MessageNumber.Should().Be(2507);
        result.Verdict.Should().Be("Insufficient");
    }

    [Theory]
    [InlineData("YYYY-MM")]
    [InlineData("YYYY-MM-DD-HH")]
    [InlineData("YYYYMMDDYYYY")]
    [InlineData("")]
    public void UnsupportedMask_Is2518_BadPicString(string mask)
    {
        var result = _service.Validate("2023-01-15", mask);

        result.MessageNumber.Should().Be(2518);
        result.Verdict.Should().Be("Bad Pic String");
    }

    [Theory]
    [InlineData("MM/DD/YYYY", "01/15/2023")]
    [InlineData("YYYYMMDD", "20230115")]
    [InlineData("DD.MM.YYYY", "15.01.2023")]
    public void OtherMasksWithYyyyMmDdTokens_AreSupported(string mask, string text)
    {
        var result = _service.Validate(text, mask);

        result.IsValid.Should().BeTrue();
        result.Date.Should().Be(new DateOnly(2023, 1, 15));
    }

    [Fact]
    public void TrailingBlanksBeyondTheMask_AreIgnored_LikeTheCobolVstring()
    {
        _service.Validate("2023-01-15  ", "YYYY-MM-DD  ").IsValid.Should().BeTrue();
    }

    [Fact]
    public void ResultText_IsTheEightyByteWsMessageLayout()
    {
        var valid = _service.Validate("2024-01-15", "YYYY-MM-DD").ResultText;

        valid.Should().HaveLength(80);
        valid.Should().Be(
            "0000" + "Mesg Code: " + "0000" + " " + "Date is valid  " + " " + "TstDate: " + "2024-01-15" + " " + "Mask used:" + "YYYY-MM-DD" + "    ");
    }

    [Fact]
    public void ResultText_ForAnInvalidDate_CarriesSeverityAndMessageNumber()
    {
        var invalid = _service.Validate("2023-02-30", "YYYY-MM-DD").ResultText;

        invalid.Should().HaveLength(80);
        invalid[..4].Should().Be("0003");
        invalid.Substring(15, 4).Should().Be("2508");
        invalid.Substring(20, 15).Should().Be("Datevalue error");
        invalid.Substring(45, 10).Should().Be("2023-02-30");
        invalid.Substring(66, 10).Should().Be("YYYY-MM-DD");
    }
}
