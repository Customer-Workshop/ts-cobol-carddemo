namespace CardDemo.Application.Menu;

/// <summary>
/// Port of COMEN01C/COADM01C PROCESS-ENTER-KEY + BUILD-MENU-OPTIONS
/// (app/cbl/COMEN01C.cbl, app/cbl/COADM01C.cbl) over the config-driven
/// route registry (seams S01-B1/B2). Validation order matches the COBOL:
/// numeric/range, admin-only gate (main menu), availability, dispatch.
/// </summary>
public class MenuService(MenuRouteRegistryOptions registry)
{
    public const string InvalidOptionMessage = "Please enter a valid option number...";
    public const string AdminOnlyMessage = "No access - Admin Only option... ";

    public static string ComingSoonMessage(string name) => $"This option {name} is coming soon ...";
    public static string NotInstalledMessage(string name) => $"This option {name} is not installed...";

    public IReadOnlyList<MenuOptionItem> GetMenu(MenuKind menu) =>
        CatalogueFor(menu).Select(o => new MenuOptionItem(o.Id, o.Name, o.Enabled)).ToList();

    public MenuSelectResult Select(MenuKind menu, char userType, string? option)
    {
        var catalogue = CatalogueFor(menu);

        if (!int.TryParse(option?.Trim(), out var number) || number <= 0 || number > catalogue.Count)
        {
            return new MenuSelectResult(MenuSelectOutcome.InvalidOption, InvalidOptionMessage, MenuMessageSeverity.Error);
        }

        var selected = catalogue[number - 1];

        if (menu == MenuKind.Main && userType != 'A' && selected.AdminOnly)
        {
            return new MenuSelectResult(MenuSelectOutcome.AdminOnly, AdminOnlyMessage, MenuMessageSeverity.Error);
        }

        if (!selected.Enabled)
        {
            return selected.NotInstalledWhenDisabled
                ? new MenuSelectResult(MenuSelectOutcome.NotInstalled, NotInstalledMessage(selected.Name), MenuMessageSeverity.Error)
                : new MenuSelectResult(MenuSelectOutcome.ComingSoon, ComingSoonMessage(selected.Name), MenuMessageSeverity.Info);
        }

        return new MenuSelectResult(
            MenuSelectOutcome.Navigate,
            Target: new MenuNavigationTarget(selected.Id, selected.Name, selected.ProgramKey, selected.Route));
    }

    private List<MenuRouteOption> CatalogueFor(MenuKind menu) =>
        menu == MenuKind.Admin ? registry.Admin : registry.Main;
}
