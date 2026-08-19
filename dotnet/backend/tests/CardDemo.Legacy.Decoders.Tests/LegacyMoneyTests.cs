using CardDemo.Domain;

namespace CardDemo.Legacy.Decoders.Tests;

public class LegacyMoneyTests
{
    [Theory]
    [InlineData(1.999, 2, 1.99)]
    [InlineData(-1.999, 2, -1.99)]
    [InlineData(1.995, 2, 1.99)]
    public void Truncate_TowardZero(decimal value, int scale, decimal expected)
    {
        Assert.Equal(expected, LegacyMoney.Truncate(value, scale));
    }

    [Theory]
    [InlineData(1.995, 2, 2.00)]
    [InlineData(-1.995, 2, -2.00)]
    [InlineData(2.005, 2, 2.01)]
    public void Round_HalfAwayFromZero(decimal value, int scale, decimal expected)
    {
        Assert.Equal(expected, LegacyMoney.Round(value, scale));
    }

    [Theory]
    // CBACT04C: COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200
    [InlineData(388.45, 12.50, 4.04)]      // 4855.625/1200 = 4.046354 -> 4.04
    [InlineData(-250.00, 12.50, -2.60)]    // -3125/1200 = -2.60416 -> -2.60
    [InlineData(1000.00, 0.00, 0.00)]
    public void MonthlyInterest_MatchesCbact04c(decimal balance, decimal rate, decimal expected)
    {
        Assert.Equal(expected, LegacyMoney.MonthlyInterest(balance, rate));
    }
}
