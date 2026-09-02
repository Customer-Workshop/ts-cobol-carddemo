using CardDemo.Application.Menu;
using FluentAssertions;
using Microsoft.Extensions.Configuration;

namespace CardDemo.Tests.Menu;

/// <summary>
/// Parity tests for COMEN01C/COADM01C PROCESS-ENTER-KEY + BUILD-MENU-OPTIONS
/// over the config-driven registry (FR-S01-10..15, 17..19).
/// </summary>
public class MenuServiceTests
{
    private static MenuRouteRegistryOptions LoadApiRegistry() =>
        new ConfigurationBuilder()
            .AddJsonFile(Path.Combine(TestPaths.RepoRoot, "backend", "CardDemo.Api", "appsettings.json"))
            .Build()
            .GetSection(MenuRouteRegistryOptions.SectionName)
            .Get<MenuRouteRegistryOptions>()!;

    private readonly MenuService _service = new(LoadApiRegistry());

    [Fact]
    public void MainMenu_Lists11CatalogueOptionsInComen02yOrder()
    {
        // FR-S01-10
        var options = _service.GetMenu(MenuKind.Main);

        options.Should().HaveCount(11);
        options.Select(o => o.Name).Should().ContainInOrder(
            "Account View", "Account Update", "Credit Card List", "Credit Card View",
            "Credit Card Update", "Transaction List", "Transaction View", "Transaction Add",
            "Transaction Reports", "Bill Payment", "Pending Authorization View");
        options.Select(o => o.Id).Should().BeInAscendingOrder();
    }

    [Fact]
    public void AdminMenu_Lists6CatalogueOptionsInCoadm02yOrder()
    {
        // FR-S01-17
        var options = _service.GetMenu(MenuKind.Admin);

        options.Should().HaveCount(6);
        options.Select(o => o.Name).Should().ContainInOrder(
            "User List (Security)", "User Add (Security)", "User Update (Security)",
            "User Delete (Security)", "Transaction Type List/Update (Db2)",
            "Transaction Type Maintenance (Db2)");
    }

    [Theory]
    [InlineData("AB")]
    [InlineData("0")]
    [InlineData("99")]
    [InlineData("")]
    [InlineData(null)]
    public void MainMenu_InvalidOption_YieldsValidOptionError(string? option)
    {
        // FR-S01-11
        var result = _service.Select(MenuKind.Main, 'U', option);

        result.Outcome.Should().Be(MenuSelectOutcome.InvalidOption);
        result.Message.Should().Be(MenuService.InvalidOptionMessage);
        result.Severity.Should().Be(MenuMessageSeverity.Error);
    }

    [Theory]
    [InlineData("AB")]
    [InlineData("0")]
    [InlineData("7")]
    public void AdminMenu_InvalidOption_YieldsValidOptionError(string option)
    {
        // FR-S01-18
        var result = _service.Select(MenuKind.Admin, 'A', option);

        result.Outcome.Should().Be(MenuSelectOutcome.InvalidOption);
        result.Message.Should().Be(MenuService.InvalidOptionMessage);
    }

    [Fact]
    public void RegularUser_SelectingAdminFlaggedOption_IsDenied()
    {
        // FR-S01-12 — the shipped catalogue has no 'A' rows, so use a fixture (plan risk #5).
        var registry = new MenuRouteRegistryOptions
        {
            Main =
            [
                new MenuRouteOption { Id = "01", Name = "Transaction Add", ProgramKey = "COTRN02C", AdminOnly = true }
            ]
        };
        var service = new MenuService(registry);

        var result = service.Select(MenuKind.Main, 'U', "1");

        result.Outcome.Should().Be(MenuSelectOutcome.AdminOnly);
        result.Message.Should().Be(MenuService.AdminOnlyMessage);
        result.Target.Should().BeNull();
    }

    [Fact]
    public void AdminUser_SelectingAdminFlaggedOption_PassesTheGate()
    {
        var registry = new MenuRouteRegistryOptions
        {
            Main =
            [
                new MenuRouteOption { Id = "01", Name = "Transaction Add", ProgramKey = "COTRN02C", AdminOnly = true, Enabled = true, Route = "/transactions/add" }
            ]
        };
        var service = new MenuService(registry);

        var result = service.Select(MenuKind.Main, 'A', "1");

        result.Outcome.Should().Be(MenuSelectOutcome.Navigate);
    }

    [Fact]
    public void Copaus0cOption11_NotInstalled_YieldsNotInstalledMessage()
    {
        // FR-S01-14 (seam S01-B2, flag default off)
        var result = _service.Select(MenuKind.Main, 'U', "11");

        result.Outcome.Should().Be(MenuSelectOutcome.NotInstalled);
        result.Message.Should().Be("This option Pending Authorization View is not installed...");
        result.Severity.Should().Be(MenuMessageSeverity.Error);
    }

    [Fact]
    public void DisabledUnmigratedOption_YieldsComingSoonMessage()
    {
        // FR-S01-15 (DUMMY idiom generalized to unmigrated targets, seam S01-B1); CORPT00C is still off-stream
        var result = _service.Select(MenuKind.Main, 'U', "9");

        result.Outcome.Should().Be(MenuSelectOutcome.ComingSoon);
        result.Message.Should().Be("This option Transaction Reports is coming soon ...");
        result.Severity.Should().Be(MenuMessageSeverity.Info);
    }

    [Fact]
    public void DisabledAdminOption_YieldsComingSoonMessage()
    {
        var result = _service.Select(MenuKind.Admin, 'A', "5");

        result.Outcome.Should().Be(MenuSelectOutcome.ComingSoon);
        result.Message.Should().Be("This option Transaction Type List/Update (Db2) is coming soon ...");
    }

    [Theory]
    [InlineData(MenuKind.Main, 'U', "1", "01", "Account View", "COACTVWC", "/accounts/view")]
    [InlineData(MenuKind.Main, 'U', "3", "03", "Credit Card List", "COCRDLIC", "/cards/list")]
    [InlineData(MenuKind.Main, 'U', "10", "10", "Bill Payment", "COBIL00C", "/bill-payment")]
    [InlineData(MenuKind.Admin, 'A', "1", "01", "User List (Security)", "COUSR00C", "/admin/users")]
    public void ShippedRegistry_MigratedOption_NavigatesToItsRoute(MenuKind kind, char userType, string option, string id, string name, string programKey, string route)
    {
        // FR-S01-13/19 over the shipped registry after Batch A
        var result = _service.Select(kind, userType, option);

        result.Outcome.Should().Be(MenuSelectOutcome.Navigate);
        result.Target.Should().Be(new MenuNavigationTarget(id, name, programKey, route));
    }

    [Fact]
    public void EnabledOption_YieldsNavigationTargetDescriptor()
    {
        // FR-S01-13/19
        var registry = new MenuRouteRegistryOptions
        {
            Main =
            [
                new MenuRouteOption { Id = "01", Name = "Account View", ProgramKey = "COACTVWC", Enabled = true, Route = "/accounts/view" }
            ]
        };
        var service = new MenuService(registry);

        var result = service.Select(MenuKind.Main, 'U', "1");

        result.Outcome.Should().Be(MenuSelectOutcome.Navigate);
        result.Message.Should().BeNull();
        result.Target.Should().Be(new MenuNavigationTarget("01", "Account View", "COACTVWC", "/accounts/view"));
    }

    [Fact]
    public void OptionInput_IsTrimmedLikeTheBmsRightJustifiedField()
    {
        var result = _service.Select(MenuKind.Main, 'U', " 9 ");

        result.Outcome.Should().Be(MenuSelectOutcome.ComingSoon);
    }
}
