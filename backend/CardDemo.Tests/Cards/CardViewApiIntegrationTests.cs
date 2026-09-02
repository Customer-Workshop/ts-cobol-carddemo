using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using CardDemo.Application.LegacyData;
using CardDemo.Application.Sessions;
using CardDemo.Infrastructure.Persistence;
using CardDemo.Infrastructure.Security;
using CardDemo.Tests.Users;
using FluentAssertions;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.Options;

namespace CardDemo.Tests.Cards;

/// <summary>
/// HTTP contract of GET /api/v1/cards/view over the JWT session and the seeded Postgres card store
/// (FR-S05-02, 09, 10, 13; any signed-on user type may view cards).
/// </summary>
public class CardViewApiIntegrationTests : IClassFixture<PostgresFixture>, IClassFixture<WebApplicationFactory<Program>>
{
    private const string SigningKey = "carddemo-test-signing-key-not-for-production-0001";
    private const string SeedAccount = "00000000050";
    private const string SeedCard = "0500024453765740";

    private readonly PostgresFixture _fixture;
    private readonly WebApplicationFactory<Program> _factory;

    public CardViewApiIntegrationTests(PostgresFixture fixture, WebApplicationFactory<Program> factory)
    {
        _fixture = fixture;
        _factory = factory.WithWebHostBuilder(builder =>
        {
            builder.UseEnvironment("Testing");
            builder.UseSetting("Jwt:SigningKey", SigningKey);
            builder.UseSetting("ConnectionStrings:CardDemo", fixture.ConnectionString);
        });
    }

    private sealed record CardPayload(string EmbossedName, string ExpiryMonth, string ExpiryYear, string ActiveStatus);

    private sealed record ViewPayload(
        string Outcome,
        string Message,
        string InfoMessage,
        string AccountId,
        string CardNumber,
        string AccountFilter,
        string CardFilter,
        string Cursor,
        CardPayload? Card);

    private async Task SeedCardsAsync()
    {
        await using var context = _fixture.CreateContext();
        var importer = new LegacyDataImportService(new LegacyDataWriter(context));
        await importer.ImportCardsAsync(File.ReadLines(TestPaths.AsciiData("carddata.txt")));
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

    [Fact]
    public async Task AnonymousCaller_IsUnauthorized()
    {
        var client = CreateClient(null);

        var response = await client.GetAsync($"/api/v1/cards/view?accountId={SeedAccount}&cardNumber={SeedCard}");

        response.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
    }

    [Theory]
    [InlineData('U')]
    [InlineData('A')]
    public async Task SignedOnUser_SeesTheSeedCard(char userType)
    {
        // FR-S05-09
        await SeedCardsAsync();
        var client = CreateClient(userType);

        var response = await client.GetAsync($"/api/v1/cards/view?accountId={SeedAccount}&cardNumber={SeedCard}");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var payload = await response.Content.ReadFromJsonAsync<ViewPayload>();
        payload.Should().Be(new ViewPayload(
            "found", "", "   Displaying requested details", SeedAccount, SeedCard, "valid", "valid", "account",
            new CardPayload("Aniya Von", "03", "2023", "Y")));
    }

    [Fact]
    public async Task BlankAccount_ReturnsInputErrorScreenState()
    {
        // FR-S05-02
        var client = CreateClient('U');

        var response = await client.GetAsync($"/api/v1/cards/view?accountId=&cardNumber={SeedCard}");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var payload = await response.Content.ReadFromJsonAsync<ViewPayload>();
        payload.Should().Be(new ViewPayload(
            "inputError", "Account number not provided", "Please enter Account and Card Number",
            "*", SeedCard, "blank", "valid", "account", null));
    }

    [Fact]
    public async Task UnknownCard_ReturnsNotFoundScreenState()
    {
        // FR-S05-10
        await SeedCardsAsync();
        var client = CreateClient('U');

        var response = await client.GetAsync($"/api/v1/cards/view?accountId={SeedAccount}&cardNumber=9999999999999999");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var payload = await response.Content.ReadFromJsonAsync<ViewPayload>();
        payload!.Outcome.Should().Be("notFound");
        payload.Message.Should().Be("Did not find cards for this search condition");
        payload.AccountFilter.Should().Be("notOk");
        payload.CardFilter.Should().Be("notOk");
    }

    [Fact]
    public async Task FromCardList_ReadsWithoutEdits()
    {
        // FR-S05-13
        await SeedCardsAsync();
        var client = CreateClient('U');

        var response = await client.GetAsync("/api/v1/cards/view?accountId=1&cardNumber=9680294154603697&fromCardList=true");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var payload = await response.Content.ReadFromJsonAsync<ViewPayload>();
        payload!.Outcome.Should().Be("found");
        payload.AccountId.Should().Be("00000000001");
        payload.Card!.EmbossedName.Should().Be("Immanuel Kessler");
    }
}
