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

    /// <summary>CDEMO-MENU-OPT-USRTYPE = 'A' gate (FR-S01-12); all shipped catalogue rows are 'U'.</summary>
    public bool AdminOnly { get; init; }

    /// <summary>COPAUS0C availability probe (seam S01-B2): disabled entry surfaces as "not installed" instead of "coming soon".</summary>
    public bool NotInstalledWhenDisabled { get; init; }

    /// <summary>Angular route the option navigates to once its owning stream migrates (seam S01-B1); empty while unmigrated.</summary>
    public string Route { get; init; } = string.Empty;
}
