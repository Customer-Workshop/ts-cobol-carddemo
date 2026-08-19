namespace CardDemo.Domain;

/// <summary>
/// COBOL-faithful money arithmetic shared by all migration waves.
/// Default COBOL COMPUTE truncates toward zero at the target's implied
/// scale; ROUNDED is half-away-from-zero. Never use banker's rounding
/// (decimal.Round default) for ported estate arithmetic.
/// </summary>
public static class LegacyMoney
{
    /// <summary>COBOL COMPUTE without ROUNDED: truncate toward zero to <paramref name="scale"/> decimals.</summary>
    public static decimal Truncate(decimal value, int scale)
    {
        decimal factor = Pow10(scale);
        return Math.Truncate(value * factor) / factor;
    }

    /// <summary>COBOL ROUNDED: half-away-from-zero to <paramref name="scale"/> decimals.</summary>
    public static decimal Round(decimal value, int scale) =>
        Math.Round(value, scale, MidpointRounding.AwayFromZero);

    /// <summary>
    /// CBACT04C monthly interest: COMPUTE WS-MONTHLY-INT = (bal * rate) / 1200
    /// into PIC S9(09)V99 (truncated toward zero, no ROUNDED).
    /// </summary>
    public static decimal MonthlyInterest(decimal categoryBalance, decimal annualRate) =>
        Truncate(categoryBalance * annualRate / 1200m, 2);

    private static decimal Pow10(int scale) => scale switch
    {
        0 => 1m,
        1 => 10m,
        2 => 100m,
        _ => throw new ArgumentOutOfRangeException(nameof(scale)),
    };
}
