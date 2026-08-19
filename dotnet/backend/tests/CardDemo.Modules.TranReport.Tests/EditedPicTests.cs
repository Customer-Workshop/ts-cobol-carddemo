namespace CardDemo.Modules.TranReport.Tests;

/// <summary>
/// Micro-parity for the CVTRA07Y edited pictures. Expected strings are
/// taken verbatim from oracle-produced golden report bytes
/// (tran-report / tran-report-eof), not re-derived.
/// </summary>
public class EditedPicTests
{
    [Theory]
    [InlineData("113.37", "         113.37")]
    [InlineData("-1130.25", "-      1,130.25")]
    [InlineData("0", "               ")]
    public void MinusEdited_MatchesGoldenDetailAmounts(string value, string expected) =>
        Assert.Equal(expected, EditedPic.MinusEdited(decimal.Parse(value, System.Globalization.CultureInfo.InvariantCulture)));

    [Theory]
    [InlineData("4847.57", "+      4,847.57")]
    [InlineData("33247.09", "+     33,247.09")]
    [InlineData("0", "               ")]
    public void PlusEdited_MatchesGoldenTotalAmounts(string value, string expected) =>
        Assert.Equal(expected, EditedPic.PlusEdited(decimal.Parse(value, System.Globalization.CultureInfo.InvariantCulture)));
}
