using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace CardDemo.Infrastructure.Persistence.Configurations;

/// <summary>
/// Column shapes shared by the VSAM-derived tables. Key columns use the "C" collation so that
/// ORDER BY / range scans reproduce VSAM byte-wise key ordering (STARTBR/READNEXT/READPREV parity).
/// </summary>
internal static class LegacyColumnConventions
{
    public const string KeyCollation = "C";

    public static PropertyBuilder<string> LegacyKey(this PropertyBuilder<string> builder, int width) =>
        builder.HasMaxLength(width).UseCollation(KeyCollation);

    /// <summary>PIC S9(n)V99 → numeric(n+2,2).</summary>
    public static PropertyBuilder<decimal> LegacyAmount(this PropertyBuilder<decimal> builder, int integerDigits) =>
        builder.HasPrecision(integerDigits + 2, 2);

    /// <summary>X(26) yyyy-MM-dd HH:mm:ss.ffffff timestamps carry no zone in the legacy record.</summary>
    public static PropertyBuilder<DateTime?> LegacyTimestamp(this PropertyBuilder<DateTime?> builder) =>
        builder.HasColumnType("timestamp without time zone");
}
