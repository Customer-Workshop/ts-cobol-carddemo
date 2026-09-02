using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using CardDemo.Application.Cards;
using CardDemo.Application.Sessions;
using CardDemo.Domain.Cards;
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
/// COCRDUPC against a real Postgres `cards` table (FR-S06-10, 11, 22, 23, 24) plus the
/// /api/v1/cards/update contract through the JWT-protected host.
/// </summary>
public class CardUpdateIntegrationTests(PostgresFixture fixture, WebApplicationFactory<Program> factory)
    : IClassFixture<PostgresFixture>, IClassFixture<WebApplicationFactory<Program>>
{
    private const string SigningKey = "carddemo-test-signing-key-not-for-production-0006";
    private const string Account = "00000000027";

    private static int _sequence;

    private static string NextCardNumber() => (4000000000000000L + Interlocked.Increment(ref _sequence)).ToString();

    private async Task<string> SeedCardAsync(string name = "Ward Jones", string status = "Y", DateOnly? expiry = null)
    {
        var cardNumber = NextCardNumber();
        await using var context = fixture.CreateContext();
        context.Cards.Add(new Card
        {
            CardNumber = cardNumber,
            AccountId = Account,
            CvvCode = "567",
            EmbossedName = name,
            ExpirationDate = expiry ?? new DateOnly(2025, 7, 13),
            ActiveStatus = status
        });
        await context.SaveChangesAsync();
        return cardNumber;
    }

    private async Task<Card> LoadAsync(string cardNumber)
    {
        await using var context = fixture.CreateContext();
        return await context.Cards.AsNoTracking().SingleAsync(c => c.CardNumber == cardNumber);
    }

    private CardUpdateService BuildService(CardDemoDbContext context) => new(new CardRepository(context));

    [Fact]
    public async Task Search_ReadsTheCardFromPostgres()
    {
        // FR-S06-10
        var cardNumber = await SeedCardAsync();
        await using var context = fixture.CreateContext();

        var screen = await BuildService(context).ProcessAsync(
            new CardUpdateRequest(CardUpdateAid.Enter, CardUpdateState.DetailsNotFetched, Account, cardNumber, null, null));

        screen.State.Should().Be(CardUpdateState.ShowDetails);
        screen.EmbossedName.Should().Be("WARD JONES");
        screen.ExpiryYear.Should().Be("2025");
        screen.ExpiryMonth.Should().Be("07");
        screen.ExpiryDay.Should().Be("13");
        screen.ActiveStatus.Should().Be("Y");
    }

    [Fact]
    public async Task Search_UnknownCardIsNotFound()
    {
        // FR-S06-11
        await using var context = fixture.CreateContext();

        var screen = await BuildService(context).ProcessAsync(
            new CardUpdateRequest(CardUpdateAid.Enter, CardUpdateState.DetailsNotFetched, Account, "4999999999999999", null, null));

        screen.State.Should().Be(CardUpdateState.DetailsNotFetched);
        screen.ErrorMessage.Should().Be("Did not find cards for this search condition");
    }

    [Fact]
    public async Task Save_RewritesOnlyTheEditedColumns()
    {
        // FR-S06-22 + deviation D1
        var cardNumber = await SeedCardAsync();
        await using var context = fixture.CreateContext();
        var original = new CardUpdateDetails(Account, cardNumber, "WARD JONES", "2025", "07", "13", "Y");

        var screen = await BuildService(context).ProcessAsync(new CardUpdateRequest(
            CardUpdateAid.Pf5,
            CardUpdateState.ChangesOkNotConfirmed,
            Account,
            cardNumber,
            original,
            new CardUpdateInput("Ward Jones Junior", "N", "1", "2031")));

        screen.State.Should().Be(CardUpdateState.ChangesDone);
        screen.InfoMessage.Should().Be("Changes committed to database");

        var stored = await LoadAsync(cardNumber);
        stored.EmbossedName.Should().Be("Ward Jones Junior");
        stored.ActiveStatus.Should().Be("N");
        stored.ExpirationDate.Should().Be(new DateOnly(2031, 1, 13));
        stored.CvvCode.Should().Be("567");
        stored.AccountId.Should().Be(Account);
    }

    [Fact]
    public async Task Save_DetectsAConcurrentChange()
    {
        // FR-S06-23
        var cardNumber = await SeedCardAsync();
        var original = new CardUpdateDetails(Account, cardNumber, "WARD JONES", "2025", "07", "13", "Y");

        await using (var other = fixture.CreateContext())
        {
            var row = await other.Cards.SingleAsync(c => c.CardNumber == cardNumber);
            row.ActiveStatus = "N";
            await other.SaveChangesAsync();
        }

        await using var context = fixture.CreateContext();
        var screen = await BuildService(context).ProcessAsync(new CardUpdateRequest(
            CardUpdateAid.Pf5,
            CardUpdateState.ChangesOkNotConfirmed,
            Account,
            cardNumber,
            original,
            new CardUpdateInput("New Name", "Y", "07", "2025")));

        screen.State.Should().Be(CardUpdateState.ShowDetails);
        screen.ErrorMessage.Should().Be("Record changed by some one else. Please review");
        screen.ActiveStatus.Should().Be("N");
        (await LoadAsync(cardNumber)).EmbossedName.Should().Be("Ward Jones");
    }

    [Fact]
    public async Task Save_WhenRowIsGone_CannotLock()
    {
        // FR-S06-24
        var cardNumber = NextCardNumber();
        var original = new CardUpdateDetails(Account, cardNumber, "WARD JONES", "2025", "07", "13", "Y");
        await using var context = fixture.CreateContext();

        var screen = await BuildService(context).ProcessAsync(new CardUpdateRequest(
            CardUpdateAid.Pf5,
            CardUpdateState.ChangesOkNotConfirmed,
            Account,
            cardNumber,
            original,
            new CardUpdateInput("New Name", "Y", "07", "2025")));

        screen.State.Should().Be(CardUpdateState.ChangesFailed);
        screen.ErrorMessage.Should().Be("Could not lock record for update");
    }

    [Fact]
    public async Task Rewrite_LocksTheRowUntilCommit()
    {
        // FR-S06-22 lock semantics (READ UPDATE): a second writer waits for the first transaction
        var cardNumber = await SeedCardAsync();
        await using var first = fixture.CreateContext();
        await using var second = fixture.CreateContext();
        var firstEntered = new TaskCompletionSource();
        var releaseFirst = new TaskCompletionSource();

        var firstWrite = new CardRepository(first).RewriteAsync(cardNumber, card =>
        {
            card.EmbossedName = "First Writer";
            firstEntered.SetResult();
            releaseFirst.Task.Wait();
            return true;
        });

        await firstEntered.Task;
        var secondWrite = new CardRepository(second).RewriteAsync(cardNumber, card =>
        {
            card.EmbossedName.Should().Be("First Writer");
            card.EmbossedName = "Second Writer";
            return true;
        });

        (await Task.WhenAny(secondWrite, Task.Delay(500))).Should().NotBe(secondWrite, "the second writer must block on the row lock");
        releaseFirst.SetResult();
        (await firstWrite).Should().Be(CardRewriteOutcome.Rewritten);
        (await secondWrite).Should().Be(CardRewriteOutcome.Rewritten);
        (await LoadAsync(cardNumber)).EmbossedName.Should().Be("Second Writer");
    }

    private HttpClient CreateClient(bool authenticated)
    {
        var client = factory.WithWebHostBuilder(builder =>
        {
            builder.UseEnvironment("Testing");
            builder.UseSetting("Jwt:SigningKey", SigningKey);
            builder.UseSetting("ConnectionStrings:CardDemo", fixture.ConnectionString);
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
    public async Task Api_AnonymousCallerIsUnauthorized()
    {
        var response = await CreateClient(authenticated: false).PostAsJsonAsync(
            "/api/v1/cards/update", new { aid = "enter", state = "notFetched", accountId = Account, cardNumber = "1" });

        response.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
    }

    [Fact]
    public async Task Api_RegularUserFetchesValidatesAndSaves()
    {
        // FR-S06-10, 20, 22 over HTTP; any user type is allowed (no admin gate in COCRDUPC)
        var cardNumber = await SeedCardAsync();
        var client = CreateClient(authenticated: true);

        var initial = await client.GetFromJsonAsync<Dictionary<string, object?>>("/api/v1/cards/update");
        initial!["state"]!.ToString().Should().Be("notFetched");
        initial["infoMessage"]!.ToString().Should().Be("Please enter Account and Card Number");

        var fetch = await client.PostAsJsonAsync("/api/v1/cards/update",
            new { aid = "enter", state = "notFetched", accountId = Account, cardNumber });
        fetch.StatusCode.Should().Be(HttpStatusCode.OK);
        var fetched = await fetch.Content.ReadFromJsonAsync<CardUpdateScreenDto>();
        fetched!.State.Should().Be("showDetails");
        fetched.EmbossedName.Should().Be("WARD JONES");
        fetched.CursorField.Should().Be("embossedName");

        var validate = await client.PostAsJsonAsync("/api/v1/cards/update", new
        {
            aid = "enter",
            state = fetched.State,
            accountId = Account,
            cardNumber,
            original = fetched.Original,
            input = new { embossedName = "Ward Jones Junior", activeStatus = "Y", expiryMonth = "07", expiryYear = "2025" }
        });
        var validated = await validate.Content.ReadFromJsonAsync<CardUpdateScreenDto>();
        validated!.State.Should().Be("changesOkNotConfirmed");
        validated.InfoMessage.Should().Be("Changes validated.Press F5 to save");
        validated.ConfirmKeysVisible.Should().BeTrue();

        var save = await client.PostAsJsonAsync("/api/v1/cards/update", new
        {
            aid = "pf5",
            state = validated.State,
            accountId = Account,
            cardNumber,
            original = validated.Original,
            input = new { embossedName = "Ward Jones Junior", activeStatus = "Y", expiryMonth = "07", expiryYear = "2025" }
        });
        var saved = await save.Content.ReadFromJsonAsync<CardUpdateScreenDto>();
        saved!.State.Should().Be("changesDone");
        saved.InfoMessage.Should().Be("Changes committed to database");
        (await LoadAsync(cardNumber)).EmbossedName.Should().Be("Ward Jones Junior");
    }

    [Fact]
    public async Task Api_UnknownAidIsBadRequest()
    {
        var response = await CreateClient(authenticated: true).PostAsJsonAsync(
            "/api/v1/cards/update", new { aid = "pf9", state = "notFetched" });

        response.StatusCode.Should().Be(HttpStatusCode.BadRequest);
    }

    private sealed record CardUpdateScreenDto(
        string State,
        string InfoMessage,
        string? ErrorMessage,
        string EmbossedName,
        Dictionary<string, string>? Original,
        string CursorField,
        bool ConfirmKeysVisible);
}
