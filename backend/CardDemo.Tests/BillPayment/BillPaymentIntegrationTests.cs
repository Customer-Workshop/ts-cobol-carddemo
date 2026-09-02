using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using CardDemo.Application.BillPayment;
using CardDemo.Application.Sessions;
using CardDemo.Domain.Accounts;
using CardDemo.Domain.Cards;
using CardDemo.Domain.Transactions;
using CardDemo.Infrastructure.Persistence;
using CardDemo.Infrastructure.Security;
using CardDemo.Tests.Users;
using FluentAssertions;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace CardDemo.Tests.BillPayment;

/// <summary>
/// COBIL00C against a real Postgres (Testcontainers): ACCTDAT read/rewrite, CXACAIX first-card read,
/// TRANSACT last-key + write, atomic unit of work, and the /api/v1/bill-payment contract
/// (FR-S11-04, 07..13, 15, 19).
/// </summary>
public class BillPaymentIntegrationTests(PostgresFixture fixture) : IClassFixture<PostgresFixture>
{
    private const string SigningKey = "carddemo-test-signing-key-not-for-production-0001";
    private static readonly DateTimeOffset Now = new(2026, 9, 2, 10, 15, 20, TimeSpan.Zero);

    private static string NewAccountId() => Random.Shared.NextInt64(10_000_000_000, 99_999_999_999).ToString();

    private static Account NewAccount(string accountId, decimal balance) => new()
    {
        AccountId = accountId,
        ActiveStatus = "Y",
        CurrentBalance = balance,
        CreditLimit = 5000m,
        CashCreditLimit = 1000m,
        AddressZip = "12345",
        GroupId = "DEFAULT"
    };

    private static Transaction NewTransaction(string id, string cardNumber) => new()
    {
        TransactionId = id,
        TypeCode = "01",
        CategoryCode = "0001",
        Source = "POS TERM",
        Description = "Purchase",
        Amount = 10m,
        MerchantId = "000000001",
        MerchantName = "Shop",
        MerchantCity = "Town",
        MerchantZip = "00000",
        CardNumber = cardNumber,
        OriginalTimestamp = new DateTime(2022, 6, 10, 19, 27, 53),
        ProcessedTimestamp = new DateTime(2022, 6, 10, 19, 27, 53)
    };

    private async Task<string> SeedAccountAsync(decimal balance, params string[] cardNumbers)
    {
        var accountId = NewAccountId();
        await using var context = fixture.CreateContext();
        context.Accounts.Add(NewAccount(accountId, balance));
        foreach (var card in cardNumbers)
        {
            context.CardXrefs.Add(new CardXref { CardNumber = card, CustomerId = "000000001", AccountId = accountId });
        }
        await context.SaveChangesAsync();
        return accountId;
    }

    private async Task ResetTransactionsAsync(params Transaction[] transactions)
    {
        await using var context = fixture.CreateContext();
        await context.Transactions.ExecuteDeleteAsync();
        context.Transactions.AddRange(transactions);
        await context.SaveChangesAsync();
    }

    private BillPaymentService BuildService(CardDemoDbContext context) =>
        new(new BillPaymentRepository(context), new CardXrefRepository(context), new FixedTimeProvider(Now));

    private async Task<BillPaymentResult> PayAsync(string accountId, string confirm)
    {
        await using var context = fixture.CreateContext();
        return await BuildService(context).PayAsync(new BillPaymentRequest(accountId, confirm));
    }

    private async Task<decimal> BalanceOfAsync(string accountId)
    {
        await using var context = fixture.CreateContext();
        return await context.Accounts.Where(a => a.AccountId == accountId).Select(a => a.CurrentBalance).SingleAsync();
    }

    private async Task<List<Transaction>> TransactionsOfAsync(string cardNumber)
    {
        await using var context = fixture.CreateContext();
        return await context.Transactions.Where(t => t.CardNumber == cardNumber).OrderBy(t => t.TransactionId).ToListAsync();
    }

    [Fact]
    public async Task UnknownAccount_IsNotFound()
    {
        // FR-S11-04
        var result = await PayAsync("99999999999", "Y");

        result.Outcome.Should().Be(BillPaymentOutcome.AccountNotFound);
        result.Message.Should().Be("Account ID NOT found...");
    }

    [Fact]
    public async Task ZeroBalance_HasNothingToPay()
    {
        // FR-S11-07
        var accountId = await SeedAccountAsync(0m, "4000000000000001");

        var result = await PayAsync(accountId, "Y");

        result.Outcome.Should().Be(BillPaymentOutcome.NothingToPay);
        result.CurrentBalance.Should().Be("+0000000000.00");
        (await TransactionsOfAsync("4000000000000001")).Should().BeEmpty();
    }

    [Fact]
    public async Task BlankConfirm_ShowsBalanceAndPromptsWithoutWriting()
    {
        // FR-S11-06, FR-S11-08
        var accountId = await SeedAccountAsync(250.75m, "4000000000000002");

        var result = await PayAsync(accountId, "");

        result.Outcome.Should().Be(BillPaymentOutcome.ConfirmationRequired);
        result.Message.Should().Be("Confirm to make a bill payment...");
        result.CurrentBalance.Should().Be("+0000000250.75");
        (await BalanceOfAsync(accountId)).Should().Be(250.75m);
        (await TransactionsOfAsync("4000000000000002")).Should().BeEmpty();
    }

    [Fact]
    public async Task AccountWithoutCardXref_ReportsNotFoundAndLeavesBalance()
    {
        // FR-S11-09 (+ deviation D1: no partial write)
        var accountId = await SeedAccountAsync(99.99m);

        var result = await PayAsync(accountId, "Y");

        result.Outcome.Should().Be(BillPaymentOutcome.CardNotFound);
        result.Message.Should().Be("Account ID NOT found...");
        (await BalanceOfAsync(accountId)).Should().Be(99.99m);
    }

    [Fact]
    public async Task ConfirmedPayment_WritesTransactionAndZeroesBalance()
    {
        // FR-S11-10, FR-S11-11, FR-S11-12, FR-S11-15
        await ResetTransactionsAsync(
            NewTransaction("0000000000000005", "4000000000000000"),
            NewTransaction("0000000000000120", "4000000000000000"),
            NewTransaction("0000000000000033", "4000000000000000"));
        var accountId = await SeedAccountAsync(1234.56m, "4000000000000010", "4000000000000009");

        var result = await PayAsync(accountId, "Y");

        result.Outcome.Should().Be(BillPaymentOutcome.PaymentSuccessful);
        result.Message.Should().Be("Payment successful.  Your Transaction ID is 0000000000000121.");
        result.Severity.Should().Be(BillPaymentMessageSeverity.Success);
        result.ClearScreen.Should().BeTrue();

        (await BalanceOfAsync(accountId)).Should().Be(0m);
        var written = (await TransactionsOfAsync("4000000000000009")).Should().ContainSingle().Subject;
        written.TransactionId.Should().Be("0000000000000121");
        written.TypeCode.Should().Be("02");
        written.CategoryCode.Should().Be("0002");
        written.Source.Should().Be("POS TERM");
        written.Description.Should().Be("BILL PAYMENT - ONLINE");
        written.Amount.Should().Be(1234.56m);
        written.MerchantId.Should().Be("999999999");
        written.MerchantName.Should().Be("BILL PAYMENT");
        written.MerchantCity.Should().Be("N/A");
        written.MerchantZip.Should().Be("N/A");
        written.OriginalTimestamp.Should().Be(new DateTime(2026, 9, 2, 10, 15, 20));
        written.ProcessedTimestamp.Should().Be(new DateTime(2026, 9, 2, 10, 15, 20));
    }

    [Fact]
    public async Task EmptyTransactionFile_AllocatesIdOne()
    {
        // FR-S11-10 (ENDFILE → zeros + 1)
        await ResetTransactionsAsync();
        var accountId = await SeedAccountAsync(5m, "4000000000000020");

        var result = await PayAsync(accountId, "Y");

        result.TransactionId.Should().Be("0000000000000001");
    }

    [Fact]
    public async Task SecondPaymentOnSameAccount_HasNothingToPay()
    {
        // FR-S11-15 then FR-S11-07
        await ResetTransactionsAsync();
        var accountId = await SeedAccountAsync(42m, "4000000000000030");

        (await PayAsync(accountId, "Y")).Outcome.Should().Be(BillPaymentOutcome.PaymentSuccessful);
        var second = await PayAsync(accountId, "Y");

        second.Outcome.Should().Be(BillPaymentOutcome.NothingToPay);
        second.CurrentBalance.Should().Be("+0000000000.00");
    }

    [Fact]
    public async Task DuplicateTransactionId_IsReportedAndNothingPersists()
    {
        // FR-S11-13 (+ D1 atomicity)
        await ResetTransactionsAsync(NewTransaction("0000000000000700", "4000000000000000"));
        var accountId = await SeedAccountAsync(77m, "4000000000000040");

        await using var context = fixture.CreateContext();
        var repository = new CollidingBillPaymentRepository(context, "0000000000000699");
        var service = new BillPaymentService(repository, new CardXrefRepository(context), new FixedTimeProvider(Now));

        var result = await service.PayAsync(new BillPaymentRequest(accountId, "Y"));

        result.Outcome.Should().Be(BillPaymentOutcome.DuplicateTransaction);
        result.Message.Should().Be("Tran ID already exist...");
        (await BalanceOfAsync(accountId)).Should().Be(77m);
        (await TransactionsOfAsync("4000000000000040")).Should().BeEmpty();
    }

    [Fact]
    public async Task Api_RequiresAuthentication()
    {
        // FR-S11-19
        using var factory = CreateFactory();
        var client = factory.CreateClient();

        var response = await client.PostAsJsonAsync("/api/v1/bill-payment", new { accountId = "1", confirm = "" });

        response.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
    }

    [Fact]
    public async Task Api_PaysThroughTheControllerForASignedOnUser()
    {
        // FR-S11-12 over HTTP; regular user type is sufficient (no admin restriction in COBIL00C)
        await ResetTransactionsAsync();
        var accountId = await SeedAccountAsync(12.34m, "4000000000000050");
        using var factory = CreateFactory();
        var client = CreateAuthenticatedClient(factory, 'U');

        var response = await client.PostAsJsonAsync("/api/v1/bill-payment", new { accountId, confirm = "y" });

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var payload = await response.Content.ReadFromJsonAsync<BillPaymentPayload>();
        payload!.Outcome.Should().Be("paymentSuccessful");
        payload.Severity.Should().Be("success");
        payload.ClearScreen.Should().BeTrue();
        payload.CursorField.Should().Be("accountId");
        payload.TransactionId.Should().Be("0000000000000001");
        payload.Message.Should().Be("Payment successful.  Your Transaction ID is 0000000000000001.");
        (await BalanceOfAsync(accountId)).Should().Be(0m);
    }

    [Fact]
    public async Task Api_BlankAccountId_ReturnsScreenErrorWith200()
    {
        // FR-S11-01 over HTTP
        using var factory = CreateFactory();
        var client = CreateAuthenticatedClient(factory, 'U');

        var response = await client.PostAsJsonAsync("/api/v1/bill-payment", new { accountId = "", confirm = "" });

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var payload = await response.Content.ReadFromJsonAsync<BillPaymentPayload>();
        payload!.Outcome.Should().Be("accountIdRequired");
        payload.Message.Should().Be("Acct ID can NOT be empty...");
        payload.Severity.Should().Be("error");
        payload.CursorField.Should().Be("accountId");
        payload.CurrentBalance.Should().BeNull();
    }

    private WebApplicationFactory<Program> CreateFactory() =>
        new WebApplicationFactory<Program>().WithWebHostBuilder(builder =>
        {
            builder.UseEnvironment("Testing");
            builder.UseSetting("Jwt:SigningKey", SigningKey);
            builder.UseSetting("ConnectionStrings:CardDemo", fixture.ConnectionString);
        });

    private static HttpClient CreateAuthenticatedClient(WebApplicationFactory<Program> factory, char userType)
    {
        var client = factory.CreateClient();
        var issuer = new JwtTokenIssuer(Options.Create(new JwtOptions { SigningKey = SigningKey }));
        var token = issuer.Issue(new SessionContext("USER0001", userType));
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);
        return client;
    }

    private sealed record BillPaymentPayload(
        string Outcome,
        string Message,
        string? Severity,
        string CursorField,
        string? CurrentBalance,
        string? TransactionId,
        bool ClearScreen);

    private sealed class FixedTimeProvider(DateTimeOffset now) : TimeProvider
    {
        public override DateTimeOffset GetUtcNow() => now;

        public override TimeZoneInfo LocalTimeZone => TimeZoneInfo.Utc;
    }

    /// <summary>Simulates a concurrent allocation: the browse returns a stale last key so WRITE hits DUPREC.</summary>
    private sealed class CollidingBillPaymentRepository(CardDemoDbContext context, string staleLastId) : IBillPaymentRepository
    {
        private readonly BillPaymentRepository _inner = new(context);

        public Task<Account?> GetAccountAsync(string accountId, CancellationToken cancellationToken = default) =>
            _inner.GetAccountAsync(accountId, cancellationToken);

        public Task<Account?> GetAccountForUpdateAsync(string accountId, CancellationToken cancellationToken = default) =>
            _inner.GetAccountForUpdateAsync(accountId, cancellationToken);

        public Task ReleaseAccountAsync(CancellationToken cancellationToken = default) =>
            _inner.ReleaseAccountAsync(cancellationToken);

        public Task<string?> GetLastTransactionIdAsync(CancellationToken cancellationToken = default) =>
            Task.FromResult<string?>(staleLastId);

        public Task<BillPaymentPostOutcome> PostPaymentAsync(Account account, Transaction transaction, CancellationToken cancellationToken = default) =>
            _inner.PostPaymentAsync(account, transaction, cancellationToken);
    }
}
