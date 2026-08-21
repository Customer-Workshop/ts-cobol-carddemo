namespace CardDemo.Application.Menu;

public sealed class MenuRouteRegistryOptions
{
    public const string SectionName = "MenuRoutes";

    public List<MenuRouteOption> Main { get; init; } = [];
    public List<MenuRouteOption> Admin { get; init; } = [];
}
