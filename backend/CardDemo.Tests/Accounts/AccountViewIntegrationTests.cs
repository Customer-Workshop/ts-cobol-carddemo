using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using CardDemo.Application.Accounts;
using CardDemo.Application.LegacyData;
using CardDemo.Application.Sessions;
using CardDemo.Domain.Accounts;
using CardDemo.Domain.Cards;
using CardDemo.Infrastructure.Persistence;
using CardDemo.Infrastructure.Security;
using CardDemo.Tests.Users;
using FluentAssertions;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace CardDemo.Tests.Accounts;

/// <summary>
/// COACTVWC over a real Postgres 16 seeded from app/data/ASCII (accounts, customers, card xref):
/// the service with the shared repositories, and the /api/v1/accounts/view endpoint end to end
/// (FR-S02-02..10, 13, 15).
/// </summary>
public class AccountViewIntegrationTests(PostgresFixture fixture) : IClassFixture<PostgresFixture>, IDisposable
{
    private const string SigningKey = "carddemo-test-signing-key-not-for-production-0002";

    private const string OrphanXrefAccount = "00000000901";
    private const string OrphanCustomerAccount = "00000000902";
    private const string TwoCardAccount = "00000000903";

    private WebApplicationFactory<Program>? _factory;

    private async Task SeedAsync()
    {
        await using var context = fixture.CreateContext();
        var importer = new LegacyDataImportService(new LegacyDataWriter(context));
        await importer.ImportAsync(new LegacyDataSeedPaths
        {
            AccountPath = TestPaths.AsciiData("acctdata.txt"),
            CardPath = TestPaths.AsciiData("carddata.txt"),
            CardXrefPath = TestPaths.AsciiData("cardxref.txt"),
            CustomerPath = TestPaths.AsciiData("custdata.txt"),
            TransactionPath = TestPaths.AsciiData("dailytran.txt"),
            TransactionCategoryBalancePath = TestPaths.AsciiData("tcatbal.txt"),
            DisclosureGroupPath = TestPaths.AsciiData("discgrp.txt"),
            TransactionTypePath = TestPaths.AsciiData("trantype.txt"),
            TransactionCategoryPath = TestPaths.AsciiData("trancatg.txt")
        });

        var writer = new LegacyDataWriter(context);
        await writer.UpsertCardXrefsAsync(
        [
            new CardXref { CardNumber = "9900000000000901", CustomerId = "000000001", AccountId = OrphanXrefAccount },
            new CardXref { CardNumber = "9900000000000902", CustomerId = "000000999", AccountId = OrphanCustomerAccount },
            new CardXref { CardNumber = "9900000000000932", CustomerId = "000000002", AccountId = TwoCardAccount },
            new CardXref { CardNumber = "9900000000000931", CustomerId = "000000001", AccountId = TwoCardAccount }
        ]);
        await writer.UpsertAccountsAsync(
        [
            SyntheticAccount(OrphanCustomerAccount),
            SyntheticAccount(TwoCardAccount)
        ]);
    }

    private static Account SyntheticAccount(string accountId) => new()
    {
        AccountId = accountId,
        ActiveStatus = "N",
        CurrentBalance = -1234567.89m,
        CreditLimit = 0m,
        CashCreditLimit = 5m,
        OpenDate = new DateOnly(2020, 1, 2),
        ExpirationDate = null,
        ReissueDate = null,
        CurrentCycleCredit = 0.5m,
        CurrentCycleDebit = 999999999.99m,
        AddressZip = "00000",
        GroupId = "ZEROAPR"
    };

    private async Task<AccountViewService> BuildServiceAsync()
    {
        await SeedAsync();
        var context = fixture.CreateContext();
        return new AccountViewService(new CardXrefRepository(context), new AccountRepository(context), new CustomerRepository(context));
    }

    private async Task<HttpClient> CreateClientAsync(char? userType)
    {
        await SeedAsync();
        _factory = new WebApplicationFactory<Program>().WithWebHostBuilder(builder =>
        {
            builder.UseEnvironment("Testing");
            builder.UseSetting("Jwt:SigningKey", SigningKey);
            builder.UseSetting("ConnectionStrings:CardDemo", fixture.ConnectionString);
        });
        var client = _factory.CreateClient();
        if (userType is not null)
        {
            var issuer = new JwtTokenIssuer(Options.Create(new JwtOptions { SigningKey = SigningKey }));
            var token = issuer.Issue(new SessionContext(userType == 'A' ? "ADMIN001" : "USER0001", userType.Value));
            client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);
        }
        return client;
    }

    public void Dispose() => _factory?.Dispose();

    [Fact]
    public async Task SeededAccount_ReturnsAccountAndCustomerBlocksFromTheAsciiData()
    {
        // FR-S02-07, 08, 09 against acctdata.txt / custdata.txt / cardxref.txt record 00000000001
        var service = await BuildServiceAsync();

        var screen = await service.ViewAsync("00000000001");

        screen.Outcome.Should().Be(AccountViewOutcome.Found);
        screen.ErrorMessage.Should().BeEmpty();
        screen.Account!.ActiveStatus.Should().Be("Y");
        screen.Account.CurrentBalance.Should().Be("+        194.00");
        screen.Account.CreditLimit.Should().Be("+      2,020.00");
        screen.Account.CashCreditLimit.Should().Be("+      1,020.00");
        screen.Account.OpenDate.Should().Be("2014-11-20");
        screen.Account.ExpirationDate.Should().Be("2025-05-20");
        screen.Account.ReissueDate.Should().Be("2025-05-20");
        screen.Account.CurrentCycleCredit.Should().Be("+           .00");
        screen.Account.CurrentCycleDebit.Should().Be("+           .00");
        screen.Customer!.CustomerId.Should().Be("000000001");
        screen.Customer.FirstName.Should().Be("Immanuel");
        screen.Customer.LastName.Should().Be("Kessler");
        screen.Customer.Ssn.Should().Be("020-97-3888");
        screen.Customer.DateOfBirth.Should().Be("1961-06-08");
        screen.Customer.FicoScore.Should().Be("274");
        screen.Customer.State.Should().Be("NC");
        screen.Customer.Zip.Should().Be("12546");
        screen.Customer.Country.Should().Be("USA");
        screen.Customer.Phone1.Should().Be("(908)119-8310");
        screen.Customer.Phone2.Should().Be("(373)693-8684");
        screen.Customer.EftAccountId.Should().Be("0053581756");
        screen.Customer.PrimaryCardHolder.Should().Be("Y");
    }

    [Fact]
    public async Task EverySeededAccount_ResolvesToItsCustomerThroughTheXref()
    {
        // FR-S02-07/10: all 50 ACCTDAT records have a CXACAIX entry and a CUSTDAT record
        var service = await BuildServiceAsync();
        await using var context = fixture.CreateContext();
        var seededIds = await context.Accounts.Where(a => string.Compare(a.AccountId, "00000000900") < 0)
            .Select(a => a.AccountId).OrderBy(id => id).ToListAsync();
        seededIds.Should().HaveCount(50);

        foreach (var accountId in seededIds)
        {
            var screen = await service.ViewAsync(accountId);
            screen.Outcome.Should().Be(AccountViewOutcome.Found, $"account {accountId}");
            screen.Customer!.CustomerId.Should().Be(accountId[2..], "seed customer ids mirror the account ids");
        }
    }

    [Fact]
    public async Task UnknownAccount_IsNotFoundInCrossRef()
    {
        // FR-S02-04
        var service = await BuildServiceAsync();

        var screen = await service.ViewAsync("00000000099");

        screen.Outcome.Should().Be(AccountViewOutcome.AccountNotInXref);
        screen.ErrorMessage.Should().Be("Account:00000000099 not found in Cross ref file.  Resp:000000013  Reas:0000");
    }

    [Fact]
    public async Task XrefWithoutAccountRow_IsNotFoundInAcctMaster()
    {
        // FR-S02-05
        var service = await BuildServiceAsync();

        var screen = await service.ViewAsync(OrphanXrefAccount);

        screen.Outcome.Should().Be(AccountViewOutcome.AccountNotInMaster);
        screen.ErrorMessage.Should().Be("Account:00000000901 not found in Acct Master file.Resp:000000013  Reas:0000");
        screen.Account.Should().BeNull();
    }

    [Fact]
    public async Task AccountWhoseXrefCustomerIsMissing_KeepsAccountBlockAndReportsCustomer()
    {
        // FR-S02-06, 08 (negative amount, zero, nine-digit maximum)
        var service = await BuildServiceAsync();

        var screen = await service.ViewAsync(OrphanCustomerAccount);

        screen.Outcome.Should().Be(AccountViewOutcome.CustomerNotFound);
        screen.ErrorMessage.Should().Be("CustId:000000999 not found in customer master.Resp: 000000013  REAS:0000000");
        screen.FilterState.Should().Be(AccountFilterState.Valid);
        screen.Customer.Should().BeNull();
        screen.Account!.ActiveStatus.Should().Be("N");
        screen.Account.CurrentBalance.Should().Be("-  1,234,567.89");
        screen.Account.CreditLimit.Should().Be("+           .00");
        screen.Account.CashCreditLimit.Should().Be("+          5.00");
        screen.Account.CurrentCycleCredit.Should().Be("+           .50");
        screen.Account.CurrentCycleDebit.Should().Be("+999,999,999.99");
        screen.Account.ExpirationDate.Should().BeEmpty();
        screen.Account.GroupId.Should().Be("ZEROAPR");
    }

    [Fact]
    public async Task AccountWithTwoCards_UsesTheLowestCardNumberXref()
    {
        // FR-S02-10: CXACAIX non-unique AIX, READ returns the first base-key record
        var service = await BuildServiceAsync();

        var screen = await service.ViewAsync(TwoCardAccount);

        screen.Outcome.Should().Be(AccountViewOutcome.Found);
        screen.Customer!.CustomerId.Should().Be("000000001");
    }

    [Fact]
    public async Task UnavailableStore_ReportsTheFileErrorMessage()
    {
        // FR-S02-13: the repositories fail against a dead connection
        var options = new DbContextOptionsBuilder<CardDemoDbContext>()
            .UseNpgsql("Host=127.0.0.1;Port=1;Username=x;Password=x;Database=x;Timeout=1")
            .Options;
        await using var context = new CardDemoDbContext(options);
        var service = new AccountViewService(new CardXrefRepository(context), new AccountRepository(context), new CustomerRepository(context));

        var screen = await service.ViewAsync("00000000001");

        screen.Outcome.Should().Be(AccountViewOutcome.StoreError);
        screen.ErrorMessage.Should().Be("File Error: READ     on CXACAIX   returned RESP 000000017 ,RESP2 000000120 ");
    }

    private sealed record AccountPayload(string ActiveStatus, string CurrentBalance, string GroupId);

    private sealed record CustomerPayload(string CustomerId, string Ssn, string FicoScore, string Zip);

    private sealed record ScreenPayload(
        string Outcome,
        string AccountId,
        string AccountFieldState,
        string InfoMessage,
        string ErrorMessage,
        AccountPayload? Account,
        CustomerPayload? Customer);

    [Fact]
    public async Task Api_AnonymousCaller_IsUnauthorized()
    {
        // FR-S02-15
        var client = await CreateClientAsync(null);

        var response = await client.GetAsync("/api/v1/accounts/view?accountId=00000000001");

        response.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
    }

    [Fact]
    public async Task Api_RegularUser_GetsTheFullScreenForASeededAccount()
    {
        // FR-S02-07, 15 (any user type)
        var client = await CreateClientAsync('U');

        var response = await client.GetAsync("/api/v1/accounts/view?accountId=00000000001");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var payload = await response.Content.ReadFromJsonAsync<ScreenPayload>();
        payload!.Outcome.Should().Be("found");
        payload.AccountId.Should().Be("00000000001");
        payload.AccountFieldState.Should().Be("valid");
        payload.InfoMessage.Should().Be("Enter or update id of account to display");
        payload.ErrorMessage.Should().BeEmpty();
        payload.Account!.CurrentBalance.Should().Be("+        194.00");
        payload.Customer!.Ssn.Should().Be("020-97-3888");
        payload.Customer.FicoScore.Should().Be("274");
        payload.Customer.Zip.Should().Be("12546");
    }

    [Fact]
    public async Task Api_BlankAccount_ReturnsNoInputWithStarEcho()
    {
        // FR-S02-02
        var client = await CreateClientAsync('A');

        var response = await client.GetAsync("/api/v1/accounts/view?accountId=");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var payload = await response.Content.ReadFromJsonAsync<ScreenPayload>();
        payload!.Outcome.Should().Be("noInput");
        payload.AccountId.Should().Be("*");
        payload.AccountFieldState.Should().Be("blank");
        payload.ErrorMessage.Should().Be("No input received");
        payload.Account.Should().BeNull();
    }

    [Fact]
    public async Task Api_InvalidAndMissingAccounts_ReturnTheExactMessages()
    {
        // FR-S02-03, 04
        var client = await CreateClientAsync('U');

        var invalid = await (await client.GetAsync("/api/v1/accounts/view?accountId=12ab")).Content.ReadFromJsonAsync<ScreenPayload>();
        invalid!.Outcome.Should().Be("invalidFilter");
        invalid.AccountFieldState.Should().Be("invalid");
        invalid.AccountId.Should().Be("12ab");
        invalid.ErrorMessage.Should().Be("Account Filter must  be a non-zero 11 digit number");

        var missing = await (await client.GetAsync("/api/v1/accounts/view?accountId=00000000099")).Content.ReadFromJsonAsync<ScreenPayload>();
        missing!.Outcome.Should().Be("accountNotInXref");
        missing.ErrorMessage.Should().Be("Account:00000000099 not found in Cross ref file.  Resp:000000013  Reas:0000");
    }
}
