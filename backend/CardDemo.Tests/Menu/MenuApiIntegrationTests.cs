using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using CardDemo.Application.Sessions;
using CardDemo.Infrastructure.Security;
using FluentAssertions;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.Options;

namespace CardDemo.Tests.Menu;

/// <summary>
/// End-to-end menu authorization over the JWT userType claim:
/// an admin sees the admin menu, a regular user is denied (FR-S01-12/17).
/// </summary>
public class MenuApiIntegrationTests : IClassFixture<WebApplicationFactory<Program>>
{
    private const string SigningKey = "carddemo-test-signing-key-not-for-production-0001";

    private readonly WebApplicationFactory<Program> _factory;

    public MenuApiIntegrationTests(WebApplicationFactory<Program> factory)
    {
        _factory = factory.WithWebHostBuilder(builder =>
        {
            builder.UseEnvironment("Testing");
            builder.UseSetting("Jwt:SigningKey", SigningKey);
        });
    }

    private HttpClient CreateClient(char? userType)
    {
        var client = _factory.CreateClient();
        if (userType is not null)
        {
            var issuer = new JwtTokenIssuer(Options.Create(new JwtOptions { SigningKey = SigningKey }));
            var token = issuer.Issue(new SessionContext(userType == 'A' ? "ADMIN001" : "USER0001", userType.Value));
            client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);
        }
        return client;
    }

    private sealed record MenuOptionPayload(string Id, string Name, bool Enabled);

    private sealed record MenuPayload(string Menu, List<MenuOptionPayload> Options);

    [Fact]
    public async Task Admin_SeesTheAdminMenu()
    {
        var client = CreateClient('A');

        var response = await client.GetAsync("/api/v1/menu?menu=admin");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var payload = await response.Content.ReadFromJsonAsync<MenuPayload>();
        payload!.Menu.Should().Be("admin");
        payload.Options.Should().HaveCount(6);
        payload.Options[0].Name.Should().Be("User List (Security)");
    }

    [Fact]
    public async Task RegularUser_IsDeniedTheAdminMenu()
    {
        var client = CreateClient('U');

        var response = await client.GetAsync("/api/v1/menu?menu=admin");

        response.StatusCode.Should().Be(HttpStatusCode.Forbidden);
    }

    [Fact]
    public async Task RegularUser_SeesTheMainMenu()
    {
        var client = CreateClient('U');

        var response = await client.GetAsync("/api/v1/menu");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var payload = await response.Content.ReadFromJsonAsync<MenuPayload>();
        payload!.Menu.Should().Be("main");
        payload.Options.Should().HaveCount(11);
    }

    [Fact]
    public async Task AnonymousCaller_IsUnauthorized()
    {
        var client = CreateClient(null);

        var response = await client.GetAsync("/api/v1/menu");

        response.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
    }

    [Fact]
    public async Task Select_NotInstalledOption11_ReturnsParityMessage()
    {
        var client = CreateClient('U');

        var response = await client.PostAsJsonAsync("/api/v1/menu/select", new { menu = "main", option = "11" });

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var body = await response.Content.ReadFromJsonAsync<Dictionary<string, object?>>();
        body!["outcome"]!.ToString().Should().Be("notInstalled");
        body["message"]!.ToString().Should().Be("This option Pending Authorization View is not installed...");
    }
}
