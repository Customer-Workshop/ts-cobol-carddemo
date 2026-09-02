using CardDemo.Application.LegacyData;
using CardDemo.Application.Transactions;
using CardDemo.Domain.Dates;
using CardDemo.Domain.Transactions;
using CardDemo.Infrastructure.Persistence;
using CardDemo.Tests.Users;
using FluentAssertions;
using Microsoft.EntityFrameworkCore;

namespace CardDemo.Tests.Transactions;

/// <summary>
/// COTRN02C against a real Postgres seeded from app/data/ASCII (cardxref.txt, dailytran.txt):
/// xref key resolution, highest-id allocation, the written row, duplicate-key mapping and copy-last
/// (FR-S09-05, 08, 20..23, 27).
/// </summary>
public class TransactionAddIntegrationTests(PostgresFixture fixture) : IClassFixture<PostgresFixture>
{
    /// <summary>Last account in cardxref.txt and its card; highest TRAN-ID in dailytran.txt.</summary>
    private const string SeedAccountId = "00000000050";
    private const string SeedCardNumber = "0500024453765740";
    private const string SeedHighestTransactionId = "0000000996722787";

    private static readonly TransactionAddRequest ValidRequest = new(
        AccountId: SeedAccountId,
        CardNumber: null,
        TypeCode: "01",
        CategoryCode: "0001",
        Source: "POS TERM",
        Description: "Integration test purchase",
        Amount: "-00000123.45",
        OriginalDate: "2024-03-01",
        ProcessedDate: "2024-03-02",
        MerchantId: "123456789",
        MerchantName: "Test Merchant",
        MerchantCity: "Testville",
        MerchantZip: "12345",
        Confirmation: "Y");

    private async Task SeedAsync()
    {
        await using var context = fixture.CreateContext();
        var importer = new LegacyDataImportService(new LegacyDataWriter(context));
        await importer.ImportCardXrefsAsync(File.ReadLines(TestPaths.AsciiData("cardxref.txt")));
        await importer.ImportTransactionsAsync(File.ReadLines(TestPaths.AsciiData("dailytran.txt")));
    }

    private TransactionAddService BuildService(CardDemoDbContext context) =>
        new(new CardXrefRepository(context), new TransactionRepository(context), new DateValidationService());

    [Fact]
    public async Task Add_ResolvesCardFromXref_AllocatesHighestIdPlusOne_AndWritesTheRow()
    {
        await SeedAsync();
        await using var context = fixture.CreateContext();
        var service = BuildService(context);
        var highestBefore = await context.Transactions.MaxAsync(t => t.TransactionId);

        var result = await service.AddAsync(ValidRequest);

        result.Outcome.Should().Be(TransactionAddOutcome.Added);
        result.TransactionId.Should().Be(TransactionAddService.NextTransactionId(highestBefore));
        result.Message.Should().Be($"Transaction added successfully.  Your Tran ID is {result.TransactionId}.");

        await using var verify = fixture.CreateContext();
        var row = await verify.Transactions.SingleAsync(t => t.TransactionId == result.TransactionId);
        row.CardNumber.Should().Be(SeedCardNumber);
        row.TypeCode.Should().Be("01");
        row.CategoryCode.Should().Be("0001");
        row.Source.Should().Be("POS TERM");
        row.Description.Should().Be("Integration test purchase");
        row.Amount.Should().Be(-123.45m);
        row.MerchantId.Should().Be("123456789");
        row.MerchantName.Should().Be("Test Merchant");
        row.MerchantCity.Should().Be("Testville");
        row.MerchantZip.Should().Be("12345");
        row.OriginalTimestamp.Should().Be(new DateTime(2024, 3, 1));
        row.ProcessedTimestamp.Should().Be(new DateTime(2024, 3, 2));
    }

    [Fact]
    public async Task Add_BySeedCard_ResolvesAccountFromXref()
    {
        await SeedAsync();
        await using var context = fixture.CreateContext();
        var service = BuildService(context);

        var result = await service.AddAsync(ValidRequest with { AccountId = null, CardNumber = SeedCardNumber, Confirmation = "N" });

        result.Outcome.Should().Be(TransactionAddOutcome.ConfirmationRequired);
        result.Screen.AccountId.Should().Be(SeedAccountId);
        result.Screen.CardNumber.Should().Be(SeedCardNumber);
    }

    [Fact]
    public async Task SeedHighestId_IsTheOneTheCobolReadprevWouldReturn()
    {
        await SeedAsync();
        await using var context = fixture.CreateContext();

        var last = await new TransactionRepository(context).GetLastAsync();

        last!.TransactionId.Should().HaveLength(16);
        string.CompareOrdinal(last.TransactionId, SeedHighestTransactionId).Should().BeGreaterThanOrEqualTo(0,
            "the seed's highest id is 0000000996722787; earlier tests in this class may have added higher ones");
    }

    [Fact]
    public async Task Add_UnknownAccountAndCard_AreNotFoundAgainstTheSeed()
    {
        await SeedAsync();
        await using var context = fixture.CreateContext();
        var service = BuildService(context);

        (await service.AddAsync(ValidRequest with { AccountId = "99999999999" })).Message.Should().Be("Account ID NOT found...");
        (await service.AddAsync(ValidRequest with { AccountId = null, CardNumber = "9999999999999999" })).Message.Should().Be("Card Number NOT found...");
    }

    [Fact]
    public async Task Add_ConsecutiveAdds_AllocateConsecutiveIds()
    {
        await SeedAsync();
        await using var context = fixture.CreateContext();
        var service = BuildService(context);

        var first = await service.AddAsync(ValidRequest);
        var second = await service.AddAsync(ValidRequest);

        first.Outcome.Should().Be(TransactionAddOutcome.Added);
        second.Outcome.Should().Be(TransactionAddOutcome.Added);
        second.TransactionId.Should().Be(TransactionAddService.NextTransactionId(first.TransactionId));
    }

    [Fact]
    public async Task Repository_AddDuplicateKey_ThrowsDuplicateTransactionId_AndLeavesTheContextUsable()
    {
        await SeedAsync();
        await using var context = fixture.CreateContext();
        var repository = new TransactionRepository(context);
        var last = await repository.GetLastAsync();

        var duplicate = new Transaction
        {
            TransactionId = last!.TransactionId,
            TypeCode = "01",
            CategoryCode = "0001",
            Source = "DUP",
            Description = "duplicate",
            Amount = 1m,
            MerchantId = "1",
            MerchantName = "m",
            MerchantCity = "c",
            MerchantZip = "z",
            CardNumber = SeedCardNumber,
            OriginalTimestamp = new DateTime(2024, 1, 1),
            ProcessedTimestamp = new DateTime(2024, 1, 1)
        };

        var act = () => repository.AddAsync(duplicate);

        (await act.Should().ThrowAsync<DuplicateTransactionIdException>()).Which.TransactionId.Should().Be(last.TransactionId);
        (await repository.GetLastAsync())!.TransactionId.Should().Be(last.TransactionId);
    }

    [Fact]
    public async Task CopyLast_CopiesTheHighestTransactionOntoTheScreen_ThenAsksForConfirmation()
    {
        await SeedAsync();
        await using var context = fixture.CreateContext();
        var service = BuildService(context);
        var added = await service.AddAsync(ValidRequest with { Description = "Copy-last source row", Amount = "-00000987.65" });
        added.Outcome.Should().Be(TransactionAddOutcome.Added);

        var result = await service.CopyLastAsync(TransactionAddRequest.Empty with { AccountId = SeedAccountId });

        result.Outcome.Should().Be(TransactionAddOutcome.ConfirmationRequired);
        result.Message.Should().Be("Confirm to add this transaction...");
        result.Screen.CardNumber.Should().Be(SeedCardNumber);
        result.Screen.TypeCode.Should().Be("01");
        result.Screen.CategoryCode.Should().Be("0001");
        result.Screen.Source.Should().Be("POS TERM");
        result.Screen.Description.Should().Be("Copy-last source row");
        result.Screen.Amount.Should().Be("-00000987.65");
        result.Screen.OriginalDate.Should().Be("2024-03-01");
        result.Screen.ProcessedDate.Should().Be("2024-03-02");
        result.Screen.MerchantId.Should().Be("123456789");
        result.Screen.MerchantName.Should().Be("Test Merchant");
        result.Screen.MerchantCity.Should().Be("Testville");
        result.Screen.MerchantZip.Should().Be("12345");
    }

    [Fact]
    public async Task CopyLast_FromASeedRowWithoutProcTs_StopsAtProcDateEmpty()
    {
        await SeedAsync();
        await using var context = fixture.CreateContext();
        var service = BuildService(context);
        var seedRow = await context.Transactions.AsNoTracking().SingleAsync(t => t.TransactionId == SeedHighestTransactionId);
        seedRow.ProcessedTimestamp.Should().BeNull("dailytran.txt carries no TRAN-PROC-TS");

        // Make the seed row the highest id again by removing anything other tests added above it.
        await context.Transactions
            .Where(t => string.Compare(t.TransactionId, SeedHighestTransactionId) > 0)
            .ExecuteDeleteAsync();

        var result = await service.CopyLastAsync(TransactionAddRequest.Empty with { AccountId = SeedAccountId });

        result.Outcome.Should().Be(TransactionAddOutcome.ValidationError);
        result.Message.Should().Be("Proc Date can NOT be empty...");
        result.CursorField.Should().Be(TransactionAddField.ProcessedDate);
        result.Screen.TypeCode.Should().Be(seedRow.TypeCode);
        result.Screen.Source.Should().Be(seedRow.Source);
        result.Screen.Amount.Should().Be(TransactionAddService.FormatAmount(seedRow.Amount));
        result.Screen.Description.Should().Be(seedRow.Description[..Math.Min(60, seedRow.Description.Length)].TrimEnd());
        result.Screen.OriginalDate.Should().Be("2022-06-10");
        result.Screen.ProcessedDate.Should().BeEmpty();
        result.Screen.MerchantName.Should().Be(seedRow.MerchantName[..Math.Min(30, seedRow.MerchantName.Length)].TrimEnd());
        result.Screen.MerchantCity.Should().Be(seedRow.MerchantCity[..Math.Min(25, seedRow.MerchantCity.Length)].TrimEnd());
        result.Screen.MerchantZip.Should().Be(seedRow.MerchantZip);
    }
}
