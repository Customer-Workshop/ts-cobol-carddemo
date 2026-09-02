using CardDemo.Application.LegacyData;
using CardDemo.Application.Transactions;
using CardDemo.Infrastructure.Persistence;
using CardDemo.Tests.Users;
using FluentAssertions;

namespace CardDemo.Tests.Transactions;

/// <summary>
/// COTRN01C lookups against a real Postgres seeded from app/data/ASCII/dailytran.txt
/// through the shared importer (FR-S08-06, 08, 09, 10, 12; boundary S08-B1).
/// </summary>
public class TransactionViewIntegrationTests(PostgresFixture fixture) : IClassFixture<PostgresFixture>
{
    private async Task<TransactionViewService> BuildServiceAsync()
    {
        await using (var seedContext = fixture.CreateContext())
        {
            var importer = new LegacyDataImportService(new LegacyDataWriter(seedContext));
            await importer.ImportAsync(LegacySeed.Paths);
        }
        var context = fixture.CreateContext();
        return new TransactionViewService(new TransactionRepository(context));
    }

    [Fact]
    public async Task SeededDebit_IsDisplayedWithAllScreenFields()
    {
        // FR-S08-08
        var service = await BuildServiceAsync();

        var result = await service.ViewAsync("0000000000683580");

        result.IsFound.Should().BeTrue();
        result.Detail.Should().Be(new TransactionViewDetail(
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

    [Fact]
    public async Task SeededCredit_ShowsNegativeEditedAmountAndDateOnly()
    {
        // FR-S08-09, FR-S08-10
        var service = await BuildServiceAsync();

        var result = await service.ViewAsync("0000000001774260");

        result.IsFound.Should().BeTrue();
        result.Detail!.Amount.Should().Be("-00000919.00");
        result.Detail.TypeCode.Should().Be("03");
        result.Detail.OriginalDate.Should().Be("2022-06-10");
        result.Detail.ProcessedDate.Should().Be("");
    }

    [Fact]
    public async Task UnknownId_IsNotFoundAgainstSeededStore()
    {
        // FR-S08-06
        var service = await BuildServiceAsync();

        var result = await service.ViewAsync("NOPE000000000001");

        result.Outcome.Should().Be(TransactionViewOutcome.NotFound);
        result.Message.Should().Be("Transaction ID NOT found...");
    }

    [Fact]
    public async Task CaseAndLeadingSpace_AreNotNormalized_TrailingSpacesAreKeyPadding()
    {
        // FR-S08-12: the C-collated key is compared verbatim, only the X(16) padding is ignored
        var service = await BuildServiceAsync();
        await using (var context = fixture.CreateContext())
        {
            if (await context.Transactions.FindAsync("abcdefghijklmnop") is null)
            {
                context.Transactions.Add(InMemoryTransactionRepository.SampleTransaction("abcdefghijklmnop"));
                await context.SaveChangesAsync();
            }
        }

        (await service.ViewAsync("0000000000683580   ")).IsFound.Should().BeTrue();
        (await service.ViewAsync(" 000000000683580")).Outcome.Should().Be(TransactionViewOutcome.NotFound);
        (await service.ViewAsync("abcdefghijklmnop")).IsFound.Should().BeTrue();
        (await service.ViewAsync("ABCDEFGHIJKLMNOP")).Outcome.Should().Be(TransactionViewOutcome.NotFound);
    }
}
