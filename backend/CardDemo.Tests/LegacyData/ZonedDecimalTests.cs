using CardDemo.Application.LegacyData;
using FluentAssertions;

namespace CardDemo.Tests.LegacyData;

public class ZonedDecimalTests
{
    [Theory]
    [InlineData("00000001940{", 2, "194.00")]
    [InlineData("00000020200{", 2, "2020.00")]
    [InlineData("0000009190}", 2, "-919.00")]
    [InlineData("0000005047G", 2, "504.77")]
    [InlineData("00150{", 2, "15.00")]
    [InlineData("0000{", 2, "0.00")]
    [InlineData("0000}", 2, "0.00")]
    [InlineData("0000A", 2, "0.01")]
    [InlineData("0000I", 2, "0.09")]
    [InlineData("0000J", 2, "-0.01")]
    [InlineData("0000R", 2, "-0.09")]
    [InlineData("12345", 2, "123.45")]
    [InlineData("123", 0, "123")]
    public void Parse_DecodesEbcdicOverpunchSigns(string field, int scale, string expected)
    {
        ZonedDecimal.Parse(field, scale).Should().Be(decimal.Parse(expected, System.Globalization.CultureInfo.InvariantCulture));
    }

    [Fact]
    public void Parse_NegativeZeroIsZero()
    {
        ZonedDecimal.Parse("0000}", 2).Should().Be(0m);
    }

    [Theory]
    [InlineData("")]
    [InlineData("12A4")]
    [InlineData("1234Z")]
    [InlineData("1234 ")]
    [InlineData("12.4")]
    public void Parse_RejectsMalformedFields(string field)
    {
        var act = () => ZonedDecimal.Parse(field, 2);

        act.Should().Throw<FormatException>();
    }

    [Fact]
    public void ParseUnsignedInt_ReadsLeadingZeroDigits()
    {
        ZonedDecimal.ParseUnsignedInt("274").Should().Be(274);
        ZonedDecimal.ParseUnsignedInt("007").Should().Be(7);
    }

    [Fact]
    public void ParseUnsignedInt_RejectsNonDigits()
    {
        var act = () => ZonedDecimal.ParseUnsignedInt("27{");

        act.Should().Throw<FormatException>();
    }
}
