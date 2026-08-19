using CardDemo.Legacy.Decoders;
using System.Text;

namespace CardDemo.Legacy.Decoders.Tests;

public class ZonedDecimalTests
{
    [Theory]
    [InlineData("000000010{", 2, 1.00)]
    [InlineData("000003884E", 2, 388.45)]
    [InlineData("0000038845", 2, 388.45)]
    [InlineData("000003884N", 2, -388.45)]
    [InlineData("000012345}", 2, -1234.50)]
    [InlineData("0000{", 0, 0)]
    public void DecodeSigned_HandlesEbcdicOverpunch(string field, int scale, decimal expected)
    {
        Assert.Equal(expected, ZonedDecimal.DecodeSigned(Encoding.ASCII.GetBytes(field), scale));
    }

    [Theory]
    [InlineData(388.45, 10, 2, "000003884E")]
    [InlineData(-388.45, 10, 2, "000003884N")]
    [InlineData(0, 5, 0, "0000{")]
    [InlineData(-0.5, 3, 1, "00N")]
    public void EncodeSigned_WritesPreferredOverpunch(decimal value, int width, int scale, string expected)
    {
        var buffer = new byte[width];
        ZonedDecimal.EncodeSigned(value, buffer, scale);
        Assert.Equal(expected, Encoding.ASCII.GetString(buffer));
    }

    [Fact]
    public void RoundTrip_SignedValues()
    {
        foreach (decimal v in new[] { 0m, 0.01m, -0.01m, 99999.99m, -99999.99m, 1234567890.12m })
        {
            var buffer = new byte[12];
            ZonedDecimal.EncodeSigned(v, buffer, 2);
            Assert.Equal(v, ZonedDecimal.DecodeSigned(buffer, 2));
        }
    }

    [Fact]
    public void DecodeUnsigned_PlainDigits()
    {
        Assert.Equal(12345678901L, ZonedDecimal.DecodeUnsigned(Encoding.ASCII.GetBytes("12345678901")));
    }

    [Fact]
    public void DecodeSigned_RejectsGarbage()
    {
        Assert.Throws<FormatException>(() => ZonedDecimal.DecodeSigned(Encoding.ASCII.GetBytes("00_0{"), 2));
        Assert.Throws<FormatException>(() => ZonedDecimal.DecodeSigned(Encoding.ASCII.GetBytes("0000*"), 2));
    }
}
