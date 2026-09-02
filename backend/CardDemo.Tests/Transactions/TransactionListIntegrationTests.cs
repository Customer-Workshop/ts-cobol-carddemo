using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using CardDemo.Application.Menu;
using CardDemo.Application.Sessions;
using CardDemo.Application.Transactions;
using CardDemo.Domain.Transactions;
using CardDemo.Infrastructure.Persistence;
using CardDemo.Infrastructure.Security;
using CardDemo.Tests.Users;
using FluentAssertions;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Options;

namespace CardDemo.Tests.Transactions;

/// <summary>
/// COTRN00C paging over a real Postgres `transactions` table (shared repository) and through
/// POST /api/v1/transactions/list with JWT auth (FR-S07-02..04, 07..14, 21).
/// </summary>
public class TransactionListIntegrationTests(PostgresFixture fixture) : IClassFixture<PostgresFixture>
{
    private const string SigningKey = "carddemo-test-signing-key-not-for-production-0001";

    private static readonly SemaphoreSlim SeedGate = new(1, 1);
    private static bool _seeded;

    private static string Id(int n) => n.ToString("D16");

    private static MenuRouteRegistryOptions LoadApiRegistry() =>
        new ConfigurationBuilder()
            .AddJsonFile(Path.Combine(TestPaths.RepoRoot, "backend", "CardDemo.Api", "appsettings.json"))
            .Build()
            .GetSection(MenuRouteRegistryOptions.SectionName)
            .Get<MenuRouteRegistryOptions>()!;

    /// <summary>25 sequential ids plus byte-order probes (0000000000000009 &lt; 0000000000000010 &lt; 000000000000001A &lt; 0000000000000100).</summary>
    private static IEnumerable<Transaction> SeedRecords()
    {
        foreach (var t in TransactionFixtures.Sequence(25))
        {
            yield return t;
        }
        yield return TransactionFixtures.Make("000000000000001A", 99.99m, "Byte-order probe");
        yield return TransactionFixtures.Make("0000000000000100", 1000m, "Last record");
    }

    private async Task EnsureSeededAsync()
    {
        await SeedGate.WaitAsync();
        try
        {
            if (_seeded)
            {
                return;
            }
            await using var context = fixture.CreateContext();
            await context.Database.ExecuteSqlRawAsync("DELETE FROM transactions");
            context.Transactions.AddRange(SeedRecords());
            await context.SaveChangesAsync();
            _seeded = true;
        }
        finally
        {
            SeedGate.Release();
        }
    }

    private async Task<TransactionListService> BuildServiceAsync()
    {
        await EnsureSeededAsync();
        return new TransactionListService(new TransactionRepository(fixture.CreateContext()), LoadApiRegistry());
    }

    private static TransactionListRequest Request(TransactionListAction action, TransactionListState? state = null, string? search = null) =>
        new(action, search, null, null, state ?? TransactionListState.Initial);

    [Fact]
    public async Task FirstPage_ReturnsLowestTenIdsInKeyOrder()
    {
        // FR-S07-02, FR-S07-07, FR-S07-21
        var service = await BuildServiceAsync();

        var result = await service.ProcessAsync(Request(TransactionListAction.Enter));

        result.Rows!.Select(r => r.TranId).Should().Equal(Enumerable.Range(1, 10).Select(Id));
        result.State.Should().Be(new TransactionListState(Id(1), Id(10), 1, true));
        result.Message.Should().BeEmpty();
    }

    [Fact]
    public async Task Search_PositionsAtFirstIdAtOrAfterKey_InByteOrder()
    {
        // FR-S07-04, FR-S07-21: key 0000000000000019 → 19, 1A, 20..25, 100 (byte order, not numeric)
        var service = await BuildServiceAsync();

        var result = await service.ProcessAsync(Request(TransactionListAction.Enter, search: Id(19)));

        result.Rows!.Select(r => r.TranId).Should().Equal(
            Id(19), "000000000000001A", Id(20), Id(21), Id(22), Id(23), Id(24), Id(25), "0000000000000100", "");
        result.Message.Should().Be("You have reached the bottom of the page...");
        result.State.PageNumber.Should().Be(1);
        result.State.NextPageAvailable.Should().BeFalse();
    }

    [Fact]
    public async Task Search_BeyondLastKey_ReportsAtTop()
    {
        // FR-S07-09
        var service = await BuildServiceAsync();

        var result = await service.ProcessAsync(Request(TransactionListAction.Enter, search: "9999999999999999"));

        result.Message.Should().Be("You are at the top of the page...");
        result.Rows.Should().BeNull();
    }

    [Fact]
    public async Task ForwardThenBackward_RoundTripsPagesWithLegacyMessages()
    {
        // FR-S07-10, FR-S07-12, FR-S07-14, FR-S07-08, FR-S07-11, FR-S07-13
        var service = await BuildServiceAsync();

        var page1 = await service.ProcessAsync(Request(TransactionListAction.Enter));
        var page2 = await service.ProcessAsync(Request(TransactionListAction.PageForward, page1.State));
        page2.Rows!.Select(r => r.TranId).Should().Equal(Enumerable.Range(11, 9).Select(Id).Append("000000000000001A"));
        page2.State.PageNumber.Should().Be(2);
        page2.State.NextPageAvailable.Should().BeTrue();

        var page3 = await service.ProcessAsync(Request(TransactionListAction.PageForward, page2.State));
        page3.Rows!.Select(r => r.TranId).Take(7).Should().Equal(Enumerable.Range(20, 6).Select(Id).Append("0000000000000100"));
        page3.Rows.Skip(7).Should().AllSatisfy(r => r.IsBlank.Should().BeTrue());
        page3.Message.Should().Be("You have reached the bottom of the page...");
        page3.State.PageNumber.Should().Be(3);
        page3.State.NextPageAvailable.Should().BeFalse();

        var stuck = await service.ProcessAsync(Request(TransactionListAction.PageForward, page3.State));
        stuck.Message.Should().Be("You are already at the bottom of the page...");
        stuck.Rows.Should().BeNull();

        var back2 = await service.ProcessAsync(Request(TransactionListAction.PageBackward, page3.State));
        back2.Rows!.Select(r => r.TranId).Should().Equal(page2.Rows.Select(r => r.TranId));
        back2.State.PageNumber.Should().Be(2);
        back2.Message.Should().BeEmpty();

        var back1 = await service.ProcessAsync(Request(TransactionListAction.PageBackward, back2.State));
        back1.Rows!.Select(r => r.TranId).Should().Equal(page1.Rows!.Select(r => r.TranId));
        back1.State.PageNumber.Should().Be(1);
        back1.Message.Should().Be("You have reached the top of the page...");

        var top = await service.ProcessAsync(Request(TransactionListAction.PageBackward, back1.State));
        top.Message.Should().Be("You are already at the top of the page...");
        top.Rows.Should().BeNull();
    }

    [Fact]
    public async Task Rows_UseLegacyEditPictures()
    {
        // FR-S07-06 through the real repository
        var service = await BuildServiceAsync();

        var result = await service.ProcessAsync(Request(TransactionListAction.Enter, search: "0000000000000100"));

        result.Rows![0].Should().Be(new TransactionListRow("0000000000000100", "07/19/22", "Last record", "+00001000.00"));
    }

    private WebApplicationFactory<Program> CreateFactory() =>
        new WebApplicationFactory<Program>().WithWebHostBuilder(builder =>
        {
            builder.UseEnvironment("Testing");
            builder.UseSetting("Jwt:SigningKey", SigningKey);
            builder.UseSetting("ConnectionStrings:CardDemo", fixture.ConnectionString);
        });

    private static HttpClient CreateClient(WebApplicationFactory<Program> factory, bool authenticated)
    {
        var client = factory.CreateClient();
        if (authenticated)
        {
            var issuer = new JwtTokenIssuer(Options.Create(new JwtOptions { SigningKey = SigningKey }));
            client.DefaultRequestHeaders.Authorization =
                new AuthenticationHeaderValue("Bearer", issuer.Issue(new SessionContext("USER0001", 'U')));
        }
        return client;
    }

    [Fact]
    public async Task Api_AnonymousCaller_IsUnauthorized()
    {
        // FR-S07-01 (server side of the entry guard)
        await EnsureSeededAsync();
        using var factory = CreateFactory();
        var client = CreateClient(factory, authenticated: false);

        var response = await client.PostAsJsonAsync("/api/v1/transactions/list", new { action = "enter" });

        response.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
    }

    [Fact]
    public async Task Api_Enter_ReturnsFirstPageAndState()
    {
        // FR-S07-02 over HTTP
        await EnsureSeededAsync();
        using var factory = CreateFactory();
        var client = CreateClient(factory, authenticated: true);

        var response = await client.PostAsJsonAsync("/api/v1/transactions/list", new { action = "enter", searchTranId = "" });

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        var root = body.RootElement;
        root.GetProperty("outcome").GetString().Should().Be("redisplay");
        root.GetProperty("rows").GetArrayLength().Should().Be(10);
        root.GetProperty("rows")[0].GetProperty("tranId").GetString().Should().Be(Id(1));
        root.GetProperty("rows")[0].GetProperty("amount").GetString().Should().Be("+00000012.34");
        root.GetProperty("state").GetProperty("pageNumber").GetInt32().Should().Be(1);
        root.GetProperty("state").GetProperty("nextPageAvailable").GetBoolean().Should().BeTrue();
        root.GetProperty("clearSearchInput").GetBoolean().Should().BeTrue();
        root.GetProperty("message").ValueKind.Should().Be(JsonValueKind.Null);
    }

    [Fact]
    public async Task Api_Enter_NonNumericSearch_ReturnsLegacyMessage()
    {
        // FR-S07-05 over HTTP
        await EnsureSeededAsync();
        using var factory = CreateFactory();
        var client = CreateClient(factory, authenticated: true);

        var response = await client.PostAsJsonAsync("/api/v1/transactions/list", new
        {
            action = "enter",
            searchTranId = "12A",
            state = new { firstTranId = Id(1), lastTranId = Id(10), pageNumber = 1, nextPageAvailable = true }
        });

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        body.RootElement.GetProperty("message").GetString().Should().Be("Tran ID must be Numeric ...");
        body.RootElement.GetProperty("severity").GetString().Should().Be("error");
        body.RootElement.GetProperty("rows").ValueKind.Should().Be(JsonValueKind.Null);
        body.RootElement.GetProperty("state").GetProperty("pageNumber").GetInt32().Should().Be(1);
    }

    [Fact]
    public async Task Api_SelectRow_NavigatesToTransactionView()
    {
        // FR-S07-15 / S07-B1 over HTTP (COTRN01C enabled by Batch A)
        await EnsureSeededAsync();
        using var factory = CreateFactory();
        var client = CreateClient(factory, authenticated: true);

        var response = await client.PostAsJsonAsync("/api/v1/transactions/list", new
        {
            action = "enter",
            selectionFlag = "S",
            selectedTranId = Id(3)
        });

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        body.RootElement.GetProperty("outcome").GetString().Should().Be("navigate");
        body.RootElement.GetProperty("target").GetProperty("programKey").GetString().Should().Be("COTRN01C");
        body.RootElement.GetProperty("target").GetProperty("route").GetString().Should().Be("/transactions/view");
        body.RootElement.GetProperty("selectedTranId").GetString().Should().Be(Id(3));
    }

    [Fact]
    public async Task Api_UnknownAction_IsBadRequest()
    {
        await EnsureSeededAsync();
        using var factory = CreateFactory();
        var client = CreateClient(factory, authenticated: true);

        var response = await client.PostAsJsonAsync("/api/v1/transactions/list", new { action = "pf12" });

        response.StatusCode.Should().Be(HttpStatusCode.BadRequest);
    }
}
