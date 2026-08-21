namespace CardDemo.Infrastructure.Security;

public sealed class JwtOptions
{
    public const string SectionName = "Jwt";

    public string SigningKey { get; init; } = string.Empty;
    public string Issuer { get; init; } = "carddemo";
    public string Audience { get; init; } = "carddemo";
    public int ExpiryMinutes { get; init; } = 30;
}
