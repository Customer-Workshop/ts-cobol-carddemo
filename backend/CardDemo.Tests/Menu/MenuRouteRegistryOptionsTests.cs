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
        options.Main.Concat(options.Admin).Should().OnlyContain(o => !o.Enabled, "no stream has migrated yet");
    }
}
