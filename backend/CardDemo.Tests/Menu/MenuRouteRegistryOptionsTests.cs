using CardDemo.Application.Menu;
using FluentAssertions;
using Microsoft.Extensions.Configuration;

namespace CardDemo.Tests.Menu;

public class MenuRouteRegistryOptionsTests
{
    [Fact]
    public void Binds_MenuRoutesSection_FromApiConfiguration()
    {
        var configuration = new ConfigurationBuilder()
            .AddJsonFile(Path.Combine(TestPaths.RepoRoot, "backend", "CardDemo.Api", "appsettings.json"))
            .Build();

        var options = configuration.GetSection(MenuRouteRegistryOptions.SectionName).Get<MenuRouteRegistryOptions>();

        options.Should().NotBeNull();
        options!.Main.Should().HaveCount(11, "COMEN02Y ships 11 main-menu options");
        options.Admin.Should().HaveCount(6, "COADM02Y ships 6 admin-menu options");
        options.Main.Select(o => o.ProgramKey).Should().Contain(["COACTVWC", "COPAUS0C"]);
        options.Admin.Select(o => o.ProgramKey).Should().Contain(["COUSR00C", "COTRTUPC"]);
        options.Main.Where(o => o.Enabled).Select(o => o.ProgramKey).Should().Equal(
            ["COACTVWC", "COACTUPC", "COCRDLIC", "COCRDSLC", "COCRDUPC", "COTRN00C", "COTRN01C", "COTRN02C", "COBIL00C"],
            "Batch A migrated S-02..S-09 and S-11");
        options.Main.Where(o => !o.Enabled).Select(o => o.ProgramKey).Should().Equal(
            ["CORPT00C", "COPAUS0C"],
            "CORPT00C is off-stream and COPAUS0C stays not-installed (S01-B2)");
        options.Admin.Where(o => o.Enabled).Select(o => o.ProgramKey).Should().Equal(
            ["COUSR00C", "COUSR01C", "COUSR02C", "COUSR03C"],
            "Batch A migrated S-12");
        options.Admin.Where(o => !o.Enabled).Select(o => o.ProgramKey).Should().Equal(
            ["COTRTLIC", "COTRTUPC"],
            "Db2 transaction-type programs are off-stream");
        options.Main.Concat(options.Admin).Where(o => o.Enabled).Should().OnlyContain(
            o => !string.IsNullOrWhiteSpace(o.Route) && o.Route.StartsWith('/'),
            "every enabled option must carry its Angular route");
    }
}
