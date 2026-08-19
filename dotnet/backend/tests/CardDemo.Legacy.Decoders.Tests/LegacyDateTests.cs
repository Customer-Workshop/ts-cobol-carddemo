using CardDemo.Domain;

namespace CardDemo.Legacy.Decoders.Tests;

public class LegacyDateTests
{
    [Fact]
    public void Compare_IsLexicographic()
    {
        Assert.True(LegacyDate.Compare("2025-07-31", "2025-08-01") < 0);
        Assert.True(LegacyDate.Compare("2025-08-01", "2025-08-01") == 0);
        Assert.True(LegacyDate.Compare("2026-01-01", "2025-12-31") > 0);
    }

    [Fact]
    public void ToTimestamp_UsesDb2DotSeparators()
    {
        var ts = LegacyDate.ToTimestamp(new DateTime(2025, 8, 1, 9, 0, 0, DateTimeKind.Unspecified));
        Assert.Equal("2025-08-01 09.00.00.000000", ts);
    }
}
