namespace CardDemo.Application.Menu;

public enum MenuKind
{
    Main,
    Admin
}

public enum MenuSelectOutcome
{
    InvalidOption,
    AdminOnly,
    ComingSoon,
    NotInstalled,
    Navigate
}

public enum MenuMessageSeverity
{
    Error,
    Info
}

/// <summary>One rendered menu row, "<nn>. <name>" source (BUILD-MENU-OPTIONS).</summary>
public sealed record MenuOptionItem(string Id, string Name, bool Enabled);

/// <summary>Navigation target descriptor replacing XCTL PROGRAM(...) (FR-S01-13/19).</summary>
public sealed record MenuNavigationTarget(string Id, string Name, string ProgramKey, string Route);

public sealed record MenuSelectResult(
    MenuSelectOutcome Outcome,
    string? Message = null,
    MenuMessageSeverity? Severity = null,
    MenuNavigationTarget? Target = null);
