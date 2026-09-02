using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using CardDemo.Application.Cards;
using CardDemo.Application.LegacyData;
using CardDemo.Application.Sessions;
using CardDemo.Infrastructure.Persistence;
using CardDemo.Infrastructure.Security;
using CardDemo.Tests.Users;
using FluentAssertions;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace CardDemo.Tests.Cards;

/// <summary>
/// CCLI over HTTP: POST /api/v1/cards/list requires a JWT session (FR-S04-02) and round-trips the
/// paging COMMAREA through the DTO layer against the shared PostgreSQL cards table.
/// </summary>
public class CardsApiIntegrationTests(PostgresFixture fixture, WebApplicationFactory<Program> factory)
    : IClassFixture<PostgresFixture>, IClassFixture<WebApplicationFactory<Program>>, IAsyncLifetime
{
    private const string SigningKey = "carddemo-test-signing-key-not-for-production-0001";

    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);

    private WebApplicationFactory<Program> _factory = factory;

    public async Task InitializeAsync()
    {
        _factory = factory.WithWebHostBuilder(builder =>
        {
            builder.UseEnvironment("Testing");
            builder.UseSetting("Jwt:SigningKey", SigningKey);
            builder.UseSetting("ConnectionStrings:CardDemo", fixture.ConnectionString);
        });

        await using var context = fixture.CreateContext();
        if (!await context.Cards.AnyAsync())
        {
            var importer = new LegacyDataImportService(new LegacyDataWriter(context));
            await importer.ImportCardsAsync(File.ReadLines(TestPaths.AsciiData("carddata.txt")));
        }
    }

    public Task DisposeAsync() => Task.CompletedTask;

    private HttpClient CreateClient(bool authenticated)
    {
        var client = _factory.CreateClient();
        if (authenticated)
        {
            var issuer = new JwtTokenIssuer(Options.Create(new JwtOptions { SigningKey = SigningKey }));
            client.DefaultRequestHeaders.Authorization =
                new AuthenticationHeaderValue("Bearer", issuer.Issue(new SessionContext("USER0001", 'U')));
        }
        return client;
    }

    private sealed record RowPayload(bool HasCard, string AccountId, string CardNumber, string ActiveStatus, string Selection, bool SelectionError, bool SelectionProtected);

    private sealed record TargetPayload(string ProgramKey, string Route, string AccountId, string CardNumber);

    private sealed record ListPayload(
        string Outcome,
        int ScreenNumber,
        string AccountFilter,
        string CardFilter,
        bool AccountFilterError,
        bool CardFilterError,
        string CursorField,
        List<RowPayload> Rows,
        string ErrorMessage,
        string InfoMessage,
        string? Message,
        string? Severity,
        JsonElement State,
        TargetPayload? Target);

    private static async Task<ListPayload> PostAsync(HttpClient client, object body)
    {
        var response = await client.PostAsJsonAsync("/api/v1/cards/list", body);
        response.StatusCode.Should().Be(HttpStatusCode.OK);
        return (await response.Content.ReadFromJsonAsync<ListPayload>(Json))!;
    }

    [Fact]
    public async Task WithoutSession_IsUnauthorized_FR02()
    {
        var response = await CreateClient(authenticated: false)
            .PostAsJsonAsync("/api/v1/cards/list", new { aid = "ENTER" });

        response.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
    }

    [Fact]
    public async Task FreshEntry_ThenPf8_ThenPf3_RoundTripsStateThroughTheApi_FR01_FR07_FR17()
    {
        var client = CreateClient(authenticated: true);

        var first = await PostAsync(client, new { aid = "ENTER" });
        first.Outcome.Should().Be("display");
        first.ScreenNumber.Should().Be(1);
        first.Rows.Should().HaveCount(7).And.OnlyContain(r => r.HasCard && r.CardNumber.Length == 16 && r.AccountId.Length == 11);
        first.InfoMessage.Should().Be(CardListService.MsgInformRecActions);
        first.ErrorMessage.Should().BeEmpty();

        var second = await PostAsync(client, new { aid = "PF8", state = first.State, selections = new string?[7] });
        second.ScreenNumber.Should().Be(2);
        second.Rows[0].CardNumber.Should().Be(first.State.GetProperty("lastCardNumber").GetString());
        second.Rows.Select(r => r.CardNumber).Should().NotIntersectWith(first.Rows.Select(r => r.CardNumber));

        var back = await PostAsync(client, new { aid = "PF7", state = second.State });
        back.ScreenNumber.Should().Be(1);
        back.Rows.Select(r => r.CardNumber).Should().Equal(first.Rows.Select(r => r.CardNumber));
        back.ErrorMessage.Should().Be(CardListService.MsgNoPreviousPages);

        var exit = await PostAsync(client, new { aid = "PF3", state = back.State });
        exit.Outcome.Should().Be("exit");
        exit.Target!.Route.Should().Be("/menu");
    }

    [Fact]
    public async Task FilterAndSelectionErrors_SurfaceLegacyMessages_FR03_FR12_FR15()
    {
        var client = CreateClient(authenticated: true);
        var first = await PostAsync(client, new { aid = "ENTER" });

        var badFilter = await PostAsync(client, new { aid = "ENTER", state = first.State, accountFilter = "12AB" });
        badFilter.ErrorMessage.Should().Be(CardListService.MsgAccountFilterInvalid);
        badFilter.AccountFilterError.Should().BeTrue();
        badFilter.AccountFilter.Should().Be("12AB");
        badFilter.CursorField.Should().Be("account");
        badFilter.Rows.Should().OnlyContain(r => r.SelectionProtected);

        var badCode = await PostAsync(client, new
        {
            aid = "ENTER",
            state = first.State,
            selections = new[] { null, null, "X", null, null, null, null }
        });
        badCode.ErrorMessage.Should().Be(CardListService.MsgInvalidActionCode);
        badCode.Rows[2].SelectionError.Should().BeTrue();
        badCode.Rows[2].Selection.Should().Be("X");
        badCode.CursorField.Should().Be("select3");

        var select = await PostAsync(client, new
        {
            aid = "ENTER",
            state = first.State,
            selections = new[] { "S", null, null, null, null, null, null }
        });
        select.Outcome.Should().Be("comingSoon", "COCRDSLC stays disabled in the shipped route registry");
        select.Message.Should().Be("This option Credit Card View is coming soon ...");
        select.Severity.Should().Be("info");
        select.Target.Should().BeEquivalentTo(new TargetPayload("COCRDSLC", string.Empty, first.Rows[0].AccountId, first.Rows[0].CardNumber));
    }
}
