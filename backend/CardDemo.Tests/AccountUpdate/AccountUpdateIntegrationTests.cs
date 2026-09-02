using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using CardDemo.Application.AccountUpdate;
using CardDemo.Application.Sessions;
using CardDemo.Infrastructure.Persistence;
using CardDemo.Infrastructure.Security;
using CardDemo.Tests.Users;
using FluentAssertions;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace CardDemo.Tests.AccountUpdate;

/// <summary>
/// 9600-WRITE-PROCESSING against a real PostgreSQL 16: locks, snapshot check, account-then-customer rewrite,
/// rollback, plus the /api/v1/account-update endpoints end to end (FR-S03-23..29, 31).
/// </summary>
public class AccountUpdateIntegrationTests(PostgresFixture fixture) : IClassFixture<PostgresFixture>
{
    private const string SigningKey = "carddemo-test-signing-key-not-for-production-0001";

    private async Task SeedAsync()
    {
        await using var context = fixture.CreateContext();
        await context.Database.ExecuteSqlRawAsync("DELETE FROM card_xref; DELETE FROM accounts; DELETE FROM customers;");
        context.Accounts.Add(AccountUpdateTestData.Account());
        context.Customers.Add(AccountUpdateTestData.Customer());
        context.CardXrefs.Add(AccountUpdateTestData.Xref());
        await context.SaveChangesAsync();
    }

    private AccountUpdateService BuildService(CardDemoDbContext context) => new(
        new CardXrefRepository(context),
        new AccountRepository(context),
        new CustomerRepository(context),
        new AccountUpdateWriter(context),
        TimeProvider.System);

    [Fact]
    public async Task Lookup_ReadsXrefThenAccountThenCustomer()
    {
        await SeedAsync();
        await using var context = fixture.CreateContext();

        var result = await BuildService(context).LookupAsync(AccountUpdateTestData.AccountId);

        result.Outcome.Should().Be(AccountUpdateOutcome.Details);
        result.Fields!.CustomerId.Should().Be(AccountUpdateTestData.CustomerId);
        result.Fields.LastName.Should().Be("PUBLIC");
        result.Fields.CashCreditLimit.Should().Be("+      2,000.00");
    }

    [Fact]
    public async Task Lookup_AccountWithoutXref_FailsAtTheXrefStep()
    {
        await SeedAsync();
        await using (var setup = fixture.CreateContext())
        {
            await setup.Database.ExecuteSqlRawAsync("DELETE FROM card_xref;");
        }
        await using var context = fixture.CreateContext();

        var result = await BuildService(context).LookupAsync(AccountUpdateTestData.AccountId);

        result.Outcome.Should().Be(AccountUpdateOutcome.SearchError);
        result.ErrorMessage.Should().StartWith("Account:00000000010 not found in Cross ref file.");
    }

    [Fact]
    public async Task Save_RewritesAccountAndCustomerInOneTransaction()
    {
        await SeedAsync();
        var original = AccountUpdateTestData.Fields();
        var updated = original with { ActiveStatus = "N", CreditLimit = "15000", LastName = "CITIZEN", Zip = "10002" };

        AccountUpdateSaveResult result;
        await using (var context = fixture.CreateContext())
        {
            result = await BuildService(context).SaveAsync(original, updated);
        }

        result.Outcome.Should().Be(AccountUpdateOutcome.Committed);
        result.InfoMessage.Should().Be("Changes committed to database");
        await using var verify = fixture.CreateContext();
        var account = await verify.Accounts.SingleAsync(a => a.AccountId == AccountUpdateTestData.AccountId);
        var customer = await verify.Customers.SingleAsync(c => c.CustomerId == AccountUpdateTestData.CustomerId);
        account.ActiveStatus.Should().Be("N");
        account.CreditLimit.Should().Be(15000m);
        account.CurrentBalance.Should().Be(1250.75m);
        customer.LastName.Should().Be("CITIZEN");
        customer.AddressZip.Should().Be("10002");
        customer.FirstName.Should().Be("JOHN");
    }

    [Fact]
    public async Task Save_WhenAnotherUserChangedTheRow_ReportsChangedAndWritesNothing()
    {
        await SeedAsync();
        var original = AccountUpdateTestData.Fields();
        await using (var other = fixture.CreateContext())
        {
            var account = await other.Accounts.SingleAsync(a => a.AccountId == AccountUpdateTestData.AccountId);
            account.CreditLimit = 9999m;
            await other.SaveChangesAsync();
        }

        AccountUpdateSaveResult result;
        await using (var context = fixture.CreateContext())
        {
            result = await BuildService(context).SaveAsync(original, original with { LastName = "CITIZEN" });
        }

        result.Outcome.Should().Be(AccountUpdateOutcome.ChangedByOther);
        result.ErrorMessage.Should().Be("Record changed by some one else. Please review");
        await using var verify = fixture.CreateContext();
        (await verify.Customers.SingleAsync(c => c.CustomerId == AccountUpdateTestData.CustomerId)).LastName.Should().Be("PUBLIC");
        (await verify.Accounts.SingleAsync(a => a.AccountId == AccountUpdateTestData.AccountId)).CreditLimit.Should().Be(9999m);
    }

    [Fact]
    public async Task Save_WhenAccountRowIsLockedElsewhere_ReportsCouldNotLockAccount()
    {
        await SeedAsync();
        var original = AccountUpdateTestData.Fields();

        await using var locker = fixture.CreateContext();
        await using var lockTx = await locker.Database.BeginTransactionAsync();
        await locker.Database.ExecuteSqlInterpolatedAsync(
            $"SELECT * FROM accounts WHERE acct_id = {AccountUpdateTestData.AccountId} FOR UPDATE");

        AccountUpdateSaveResult result;
        await using (var context = fixture.CreateContext())
        {
            result = await BuildService(context).SaveAsync(original, original with { LastName = "CITIZEN" });
        }
        await lockTx.RollbackAsync();

        result.Outcome.Should().Be(AccountUpdateOutcome.Failed);
        result.ErrorMessage.Should().Be("Could not lock account record for update");
        result.InfoMessage.Should().Be("Changes unsuccessful. Please try again");
    }

    [Fact]
    public async Task Save_WhenCustomerRowIsLockedElsewhere_ReportsCouldNotLockCustomer()
    {
        await SeedAsync();
        var original = AccountUpdateTestData.Fields();

        await using var locker = fixture.CreateContext();
        await using var lockTx = await locker.Database.BeginTransactionAsync();
        await locker.Database.ExecuteSqlInterpolatedAsync(
            $"SELECT * FROM customers WHERE cust_id = {AccountUpdateTestData.CustomerId} FOR UPDATE");

        AccountUpdateSaveResult result;
        await using (var context = fixture.CreateContext())
        {
            result = await BuildService(context).SaveAsync(original, original with { LastName = "CITIZEN" });
        }
        await lockTx.RollbackAsync();

        result.Outcome.Should().Be(AccountUpdateOutcome.Failed);
        result.ErrorMessage.Should().Be("Could not lock customer record for update");
        await using var verify = fixture.CreateContext();
        (await verify.Customers.SingleAsync(c => c.CustomerId == AccountUpdateTestData.CustomerId)).LastName.Should().Be("PUBLIC");
    }

    [Fact]
    public async Task Save_WhenCustomerRowIsGone_ReportsCouldNotLockCustomer()
    {
        await SeedAsync();
        var original = AccountUpdateTestData.Fields();
        await using (var setup = fixture.CreateContext())
        {
            await setup.Database.ExecuteSqlRawAsync("DELETE FROM customers;");
        }

        AccountUpdateSaveResult result;
        await using (var context = fixture.CreateContext())
        {
            result = await BuildService(context).SaveAsync(original, original with { LastName = "CITIZEN" });
        }

        result.Outcome.Should().Be(AccountUpdateOutcome.Failed);
        result.ErrorMessage.Should().Be("Could not lock customer record for update");
    }

    [Fact]
    public async Task Api_LookupValidateSave_RoundTripUnderJwt()
    {
        await SeedAsync();
        using var factory = new WebApplicationFactory<Program>().WithWebHostBuilder(builder =>
        {
            builder.UseEnvironment("Testing");
            builder.UseSetting("Jwt:SigningKey", SigningKey);
            builder.UseSetting("ConnectionStrings:CardDemo", fixture.ConnectionString);
        });
        var client = factory.CreateClient();
        var issuer = new JwtTokenIssuer(Options.Create(new JwtOptions { SigningKey = SigningKey }));
        client.DefaultRequestHeaders.Authorization =
            new AuthenticationHeaderValue("Bearer", issuer.Issue(new SessionContext("USER0001", 'U')));

        var lookup = await client.PostAsJsonAsync("/api/v1/account-update/lookup", new { accountId = AccountUpdateTestData.AccountId });
        lookup.StatusCode.Should().Be(HttpStatusCode.OK);
        var lookupBody = await lookup.Content.ReadFromJsonAsync<LookupPayload>();
        lookupBody!.Outcome.Should().Be("details");
        lookupBody.InfoMessage.Should().Be("Update account details presented above.");
        lookupBody.Fields!["accountId"].Should().Be(AccountUpdateTestData.AccountId);
        lookupBody.Fields["creditLimit"].Should().Be("+     10,000.00");

        var updated = new Dictionary<string, string?>(lookupBody.Fields) { ["ficoScore"] = "12" };
        var invalid = await client.PostAsJsonAsync("/api/v1/account-update/validate", new { original = lookupBody.Fields, updated });
        var invalidBody = await invalid.Content.ReadFromJsonAsync<ChangePayload>();
        invalidBody!.Outcome.Should().Be("invalid");
        invalidBody.ErrorMessage.Should().Be("FICO Score: should be between 300 and 850");
        invalidBody.InvalidFields.Should().Equal("ficoScore");

        updated["ficoScore"] = "700";
        var confirm = await client.PostAsJsonAsync("/api/v1/account-update/validate", new { original = lookupBody.Fields, updated });
        var confirmBody = await confirm.Content.ReadFromJsonAsync<ChangePayload>();
        confirmBody!.Outcome.Should().Be("confirm");
        confirmBody.InfoMessage.Should().Be("Changes validated.Press F5 to save");

        var save = await client.PostAsJsonAsync("/api/v1/account-update/save", new { original = lookupBody.Fields, updated });
        var saveBody = await save.Content.ReadFromJsonAsync<ChangePayload>();
        saveBody!.Outcome.Should().Be("committed");
        saveBody.InfoMessage.Should().Be("Changes committed to database");

        var again = await client.PostAsJsonAsync("/api/v1/account-update/lookup", new { accountId = AccountUpdateTestData.AccountId });
        (await again.Content.ReadFromJsonAsync<LookupPayload>())!.Fields!["ficoScore"].Should().Be("700");
    }

    [Fact]
    public async Task Api_RejectsAnonymousCalls()
    {
        using var factory = new WebApplicationFactory<Program>().WithWebHostBuilder(builder =>
        {
            builder.UseEnvironment("Testing");
            builder.UseSetting("Jwt:SigningKey", SigningKey);
            builder.UseSetting("ConnectionStrings:CardDemo", fixture.ConnectionString);
        });
        var client = factory.CreateClient();

        var response = await client.PostAsJsonAsync("/api/v1/account-update/lookup", new { accountId = "1" });

        response.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
    }

    private sealed record LookupPayload(string Outcome, string? InfoMessage, string? ErrorMessage, Dictionary<string, string?>? Fields);

    private sealed record ChangePayload(string Outcome, string? InfoMessage, string? ErrorMessage, List<string> InvalidFields);
}
