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

namespace CardDemo.Tests.Transactions;

/// <summary>
/// End-to-end CT01 over HTTP: JWT gate, legacy messages per HTTP outcome and the
/// screen-shaped payload against the seeded Postgres (FR-S08-01, 04, 06, 07, 08).
/// </summary>
public class TransactionViewApiIntegrationTests(PostgresFixture fixture, WebApplicationFactory<Program> factory)
    : IClassFixture<PostgresFixture>, IClassFixture<WebApplicationFactory<Program>>
{
    private const string SigningKey = "carddemo-test-signing-key-not-for-production-0001";
    private const string UnreachableStore = "Host=127.0.0.1;Port=1;Database=carddemo;Username=x;Password=x;Timeout=1";

    private sealed record ErrorPayload(string Message);

    private sealed record ViewPayload(
        string TransactionId,
        string CardNumber,
        string TypeCode,
        string CategoryCode,
        string Source,
        string Description,
        string Amount,
        string OriginalDate,
        string ProcessedDate,
        string MerchantId,
        string MerchantName,
        string MerchantCity,
        string MerchantZip);

    private async Task<HttpClient> CreateClientAsync(bool authenticated = true, string? connectionString = null)
    {
        await using (var seedContext = fixture.CreateContext())
        {
            var importer = new LegacyDataImportService(new LegacyDataWriter(seedContext));
            await importer.ImportAsync(LegacySeed.Paths);
        }

        var client = factory.WithWebHostBuilder(builder =>
        {
            builder.UseEnvironment("Testing");
            builder.UseSetting("Jwt:SigningKey", SigningKey);
            builder.UseSetting("ConnectionStrings:CardDemo", connectionString ?? fixture.ConnectionString);
        }).CreateClient();

        if (authenticated)
        {
            var issuer = new JwtTokenIssuer(Options.Create(new JwtOptions { SigningKey = SigningKey }));
            var token = issuer.Issue(new SessionContext("USER0001", 'U'));
            client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);
        }
        return client;
    }

    [Fact]
    public async Task WithoutToken_IsUnauthorized()
    {
        // FR-S08-01
        var client = await CreateClientAsync(authenticated: false);

        var response = await client.GetAsync("/api/v1/transactions/view?tranId=0000000000683580");

        response.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
    }

    [Theory]
    [InlineData("")]
    [InlineData("?tranId=")]
    [InlineData("?tranId=%20%20%20")]
    public async Task BlankTranId_IsBadRequestWithLegacyMessage(string query)
    {
        // FR-S08-04
        var client = await CreateClientAsync();

        var response = await client.GetAsync("/api/v1/transactions/view" + query);

        response.StatusCode.Should().Be(HttpStatusCode.BadRequest);
        (await response.Content.ReadFromJsonAsync<ErrorPayload>())!.Message.Should().Be("Tran ID can NOT be empty...");
    }

    [Fact]
    public async Task UnknownTranId_IsNotFoundWithLegacyMessage()
    {
        // FR-S08-06
        var client = await CreateClientAsync();

        var response = await client.GetAsync("/api/v1/transactions/view?tranId=NOPE000000000001");

        response.StatusCode.Should().Be(HttpStatusCode.NotFound);
        (await response.Content.ReadFromJsonAsync<ErrorPayload>())!.Message.Should().Be("Transaction ID NOT found...");
    }

    [Fact]
    public async Task UnreachableStore_IsServerErrorWithLegacyMessage()
    {
        // FR-S08-07
        var client = await CreateClientAsync(connectionString: UnreachableStore);

        var response = await client.GetAsync("/api/v1/transactions/view?tranId=0000000000683580");

        response.StatusCode.Should().Be(HttpStatusCode.InternalServerError);
        (await response.Content.ReadFromJsonAsync<ErrorPayload>())!.Message.Should().Be("Unable to lookup Transaction...");
    }

    [Fact]
    public async Task SeededTranId_ReturnsScreenFields()
    {
        // FR-S08-08
        var client = await CreateClientAsync();

        var response = await client.GetAsync("/api/v1/transactions/view?tranId=0000000000683580");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var payload = await response.Content.ReadFromJsonAsync<ViewPayload>();
        payload.Should().Be(new ViewPayload(
            "0000000000683580",
            "4859452612877065",
            "01",
            "0001",
            "POS TERM",
            "Purchase at Abshire-Lowe",
            "+00000504.77",
            "2022-06-10",
            "",
            "800000000",
            "Abshire-Lowe",
            "North Enoshaven",
            "72112"));
    }
}
