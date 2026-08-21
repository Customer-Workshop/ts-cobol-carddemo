namespace CardDemo.Application.Menu;

/// <summary>
/// One entry of the config-driven menu route registry (seam S01-B1/B2):
/// disabled entries surface as navigable-but-unmigrated ("not installed") in Wave 3.
/// </summary>
public sealed record MenuRouteOption
{
    public string Id { get; init; } = string.Empty;
    public string Name { get; init; } = string.Empty;
    public string ProgramKey { get; init; } = string.Empty;
    public bool Enabled { get; init; }
}
