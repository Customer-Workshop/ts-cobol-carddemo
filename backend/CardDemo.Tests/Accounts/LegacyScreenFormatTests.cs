using CardDemo.Application.Accounts;
using FluentAssertions;

namespace CardDemo.Tests.Accounts;

/// <summary>BMS PICOUT '+ZZZ,ZZZ,ZZZ.99' and COBOL MOVE/STRING editing used by map CACTVWA (FR-S02-08, 09).</summary>
public class LegacyScreenFormatTests
{
    [Theory]
    [InlineData("0", "+           .00")]
    [InlineData("1940.00", "+      1,940.00")]
    [InlineData("20200.00", "+     20,200.00")]
    [InlineData("-12.5", "-         12.50")]
    [InlineData("0.07", "+           .07")]
    [InlineData("123456789.99", "+123,456,789.99")]
    [InlineData("1000.00", "+      1,000.00")]
    public void EditedAmount_MatchesPlusZzzCommaPicture(string value, string expected)
    {
        // FR-S02-08
        var edited = LegacyScreenFormat.EditedAmount(decimal.Parse(value, System.Globalization.CultureInfo.InvariantCulture));

        edited.Should().Be(expected);
        edited.Should().HaveLength(15);
    }

    [Fact]
    public void EditedAmount_DropsTheTenthIntegerDigitAndExtraDecimals_LikeACobolMove()
    {
        // FR-S02-08: S9(10)V99 → 9 integer positions in the picture; MOVE truncates, never rounds
        LegacyScreenFormat.EditedAmount(9_999_999_999.99m).Should().Be("+999,999,999.99");
        LegacyScreenFormat.EditedAmount(1_234_567_890.12m).Should().Be("+234,567,890.12");
        LegacyScreenFormat.EditedAmount(1.999m).Should().Be("+          1.99");
    }

    [Fact]
    public void Fit_TruncatesToTheMapWidthAndDropsTrailingBlanks()
    {
        // FR-S02-09: zip X(10) → ACSZIPC 5, phone X(15) → ACSPHN1 13
        LegacyScreenFormat.Fit("12546", 5).Should().Be("12546");
        LegacyScreenFormat.Fit("12546-1234", 5).Should().Be("12546");
        LegacyScreenFormat.Fit("(908)119-8310", 13).Should().Be("(908)119-8310");
        LegacyScreenFormat.Fit("(908)119-8310xx", 13).Should().Be("(908)119-8310");
        LegacyScreenFormat.Fit("NC ", 2).Should().Be("NC");
    }

    [Fact]
    public void RespCode_IsNineUnsignedDigitsLeftJustifiedInTenPositions()
    {
        LegacyScreenFormat.RespCode(13).Should().Be("000000013 ");
        LegacyScreenFormat.RespCode(80).Should().Be("000000080 ");
    }

    [Fact]
    public void StringInto_DropsOverflowBeyondTheReceiverWidth()
    {
        LegacyScreenFormat.StringInto(5, "abc", "defg").Should().Be("abcde");
        LegacyScreenFormat.StringInto(10, "abc").Should().Be("abc");
    }

    [Fact]
    public void IsoDate_RendersYyyyMmDdOrBlank()
    {
        LegacyScreenFormat.IsoDate(new DateOnly(2014, 11, 20)).Should().Be("2014-11-20");
        LegacyScreenFormat.IsoDate(null).Should().BeEmpty();
    }
}
