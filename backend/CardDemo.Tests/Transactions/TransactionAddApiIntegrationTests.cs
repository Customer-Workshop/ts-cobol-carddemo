using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using CardDemo.Application.LegacyData;
using CardDemo.Application.Sessions;
using CardDemo.Infrastructure.Persistence;
using CardDemo.Infrastructure.Security;
using CardDemo.Tests.Users;
using FluentAssertions;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.Options;

namespace CardDemo.Tests.Transactions;

/// <summary>
/// /api/v1/transactions/add over HTTP against a real Postgres: authentication, the JSON screen
/// contract, the API-edge width guard, and menu option 08 staying disabled (FR-S09-01, 25, 26; S09-B1, S09-B2).
/// </summary>
public class TransactionAddApiIntegrationTests : IClassFixture<PostgresFixture>, IClassFixture<WebApplicationFactory<Program>>
{
    private const string SigningKey = "carddemo-test-signing-key-not-for-production-0001";

    private readonly PostgresFixture _fixture;
    private readonly WebApplicationFactory<Program> _factory;

    public TransactionAddApiIntegrationTests(PostgresFixture fixture, WebApplicationFactory<Program> factory)
    {
        _fixture = fixture;
        _factory = factory.WithWebHostBuilder(builder =>
        {
            builder.UseEnvironment("Testing");
            builder.UseSetting("Jwt:SigningKey", SigningKey);
            builder.UseSetting("ConnectionStrings:CardDemo", fixture.ConnectionString);
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

    private async Task SeedAsync()
    {
        await using var context = _fixture.CreateContext();
        var importer = new LegacyDataImportService(new LegacyDataWriter(context));
        await importer.ImportCardXrefsAsync(File.ReadLines(TestPaths.AsciiData("cardxref.txt")));
        await importer.ImportTransactionsAsync(File.ReadLines(TestPaths.AsciiData("dailytran.txt")));
    }

    private static object ValidBody(string confirmation = "Y") => new
    {
        accountId = "00000000050",
        typeCode = "01",
        categoryCode = "0001",
        source = "POS TERM",
        description = "API purchase",
        amount = "+00000010.00",
        originalDate = "2024-05-01",
        processedDate = "2024-05-01",
        merchantId = "123456789",
        merchantName = "API Merchant",
        merchantCity = "Apiville",
        merchantZip = "54321",
        confirmation
    };

    [Fact]
    public async Task AnonymousCaller_IsUnauthorized()
    {
        var client = CreateClient(null);

        var response = await client.PostAsJsonAsync("/api/v1/transactions/add", ValidBody());

        response.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
    }

    [Fact]
    public async Task RegularUser_CanAdd_AndGetsTheSuccessScreen()
    {
        await SeedAsync();
        var client = CreateClient('U');

        var response = await client.PostAsJsonAsync("/api/v1/transactions/add", ValidBody());

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();
        body.GetProperty("outcome").GetString().Should().Be("added");
        body.GetProperty("severity").GetString().Should().Be("success");
        body.GetProperty("cursorField").GetString().Should().Be("accountId");
        var id = body.GetProperty("transactionId").GetString();
        id.Should().MatchRegex("^[0-9]{16}$");
        body.GetProperty("message").GetString().Should().Be($"Transaction added successfully.  Your Tran ID is {id}.");
        body.GetProperty("screen").GetProperty("accountId").GetString().Should().BeEmpty();
        body.GetProperty("screen").GetProperty("typeCode").GetString().Should().BeEmpty();
    }

    [Fact]
    public async Task ValidationError_IsAnOkScreenRedisplay_WithTheLegacyMessageAndCursor()
    {
        await SeedAsync();
        var client = CreateClient('U');

        var response = await client.PostAsJsonAsync("/api/v1/transactions/add", new { accountId = "00000000050", typeCode = "01" });

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();
        body.GetProperty("outcome").GetString().Should().Be("validationError");
        body.GetProperty("severity").GetString().Should().Be("error");
        body.GetProperty("message").GetString().Should().Be("Category CD can NOT be empty...");
        body.GetProperty("cursorField").GetString().Should().Be("categoryCode");
        body.GetProperty("screen").GetProperty("cardNumber").GetString().Should().Be("0500024453765740", "the xref card is echoed back");
        body.GetProperty("transactionId").ValueKind.Should().Be(JsonValueKind.Null);
    }

    [Fact]
    public async Task ConfirmationPrompt_EchoesTheNormalisedAmount()
    {
        await SeedAsync();
        var client = CreateClient('U');

        var response = await client.PostAsJsonAsync("/api/v1/transactions/add", ValidBody("N"));

        var body = await response.Content.ReadFromJsonAsync<JsonElement>();
        body.GetProperty("outcome").GetString().Should().Be("confirmationRequired");
        body.GetProperty("message").GetString().Should().Be("Confirm to add this transaction...");
        body.GetProperty("cursorField").GetString().Should().Be("confirmation");
        body.GetProperty("screen").GetProperty("amount").GetString().Should().Be("+00000010.00");
    }

    [Fact]
    public async Task OverLengthField_IsRejectedAtTheApiEdge()
    {
        var client = CreateClient('U');

        var response = await client.PostAsJsonAsync("/api/v1/transactions/add", new { accountId = "000000000501", merchantZip = "12345-678901" });

        response.StatusCode.Should().Be(HttpStatusCode.BadRequest);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();
        body.GetProperty("fields").EnumerateArray().Select(f => f.GetString()).Should().Equal("accountId", "merchantZip");
    }

    [Fact]
    public async Task CopyLast_ReturnsTheConfirmationScreenFilledFromTheLastTransaction()
    {
        await SeedAsync();
        var client = CreateClient('U');
        (await client.PostAsJsonAsync("/api/v1/transactions/add", ValidBody())).EnsureSuccessStatusCode();

        var response = await client.PostAsJsonAsync("/api/v1/transactions/add/copy-last", new { accountId = "00000000050" });

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();
        body.GetProperty("outcome").GetString().Should().Be("confirmationRequired");
        body.GetProperty("cursorField").GetString().Should().Be("confirmation");
        var screen = body.GetProperty("screen");
        screen.GetProperty("cardNumber").GetString().Should().Be("0500024453765740");
        screen.GetProperty("typeCode").GetString().Should().Be("01");
        screen.GetProperty("description").GetString().Should().Be("API purchase");
        screen.GetProperty("amount").GetString().Should().Be("+00000010.00");
        screen.GetProperty("originalDate").GetString().Should().Be("2024-05-01");
        screen.GetProperty("processedDate").GetString().Should().Be("2024-05-01");
        screen.GetProperty("merchantZip").GetString().Should().Be("54321");
    }

    [Fact]
    public async Task MainMenuOption08_StaysDisabledUntilIntegration()
    {
        var client = CreateClient('U');

        var response = await client.GetAsync("/api/v1/menu");

        var body = await response.Content.ReadFromJsonAsync<JsonElement>();
        var option08 = body.GetProperty("options").EnumerateArray().Single(o => o.GetProperty("id").GetString() == "08");
        option08.GetProperty("name").GetString().Should().Be("Transaction Add");
        option08.GetProperty("enabled").GetBoolean().Should().BeFalse();
    }
}
