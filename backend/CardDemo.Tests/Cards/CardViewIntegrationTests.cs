using CardDemo.Application.Cards;
using CardDemo.Application.LegacyData;
using CardDemo.Infrastructure.Persistence;
using CardDemo.Tests.Users;
using FluentAssertions;
using Microsoft.EntityFrameworkCore;

namespace CardDemo.Tests.Cards;

/// <summary>
/// COCRDSLC keyed CARDDAT read against PostgreSQL seeded from app/data/ASCII/carddata.txt
/// (S05-B1; FR-S05-09, 10, 12, 13).
/// </summary>
public class CardViewIntegrationTests(PostgresFixture fixture) : IClassFixture<PostgresFixture>
{
    private const string SeedAccount = "00000000050";
    private const string SeedCard = "0500024453765740";

    private async Task<CardViewService> BuildAsync()
    {
        await using var seedContext = fixture.CreateContext();
        var importer = new LegacyDataImportService(new LegacyDataWriter(seedContext));
        var imported = await importer.ImportCardsAsync(File.ReadLines(TestPaths.AsciiData("carddata.txt")));
        imported.Should().Be(50);

        return new CardViewService(new CardRepository(fixture.CreateContext()));
    }

    [Fact]
    public async Task SeedCard_IsDisplayedWithLegacyValues()
    {
        // FR-S05-09
        var service = await BuildAsync();

        var result = await service.ViewAsync(new CardViewRequest(SeedAccount, SeedCard));

        result.Outcome.Should().Be(CardViewOutcome.Found);
        result.InfoMessage.Should().Be("   Displaying requested details");
        result.ErrorMessage.Should().BeEmpty();
        result.Card.Should().Be(new CardViewDetails("Aniya Von", "03", "2023", "Y"));
        result.AccountId.Should().Be(SeedAccount);
        result.CardNumber.Should().Be(SeedCard);
    }

    [Fact]
    public async Task UnknownCard_ReportsNotFound()
    {
        // FR-S05-10
        var service = await BuildAsync();

        var result = await service.ViewAsync(new CardViewRequest(SeedAccount, "9999999999999999"));

        result.Outcome.Should().Be(CardViewOutcome.NotFound);
        result.ErrorMessage.Should().Be("Did not find cards for this search condition");
        result.AccountFilter.Should().Be(CardViewFilterState.NotOk);
        result.CardFilter.Should().Be(CardViewFilterState.NotOk);
        result.Card.Should().BeNull();
    }

    [Fact]
    public async Task ForeignAccount_StillReadsTheCardByNumber()
    {
        // FR-S05-12
        var service = await BuildAsync();

        var result = await service.ViewAsync(new CardViewRequest("00000000001", SeedCard));

        result.Outcome.Should().Be(CardViewOutcome.Found);
        result.AccountId.Should().Be("00000000001");
        result.Card!.EmbossedName.Should().Be("Aniya Von");
    }

    [Fact]
    public async Task FromCardList_ReadsTheSelectedCard()
    {
        // FR-S05-13
        var service = await BuildAsync();

        var result = await service.ViewAsync(new CardViewRequest("1", "9680294154603697", FromCardList: true));

        result.Outcome.Should().Be(CardViewOutcome.Found);
        result.AccountId.Should().Be("00000000001");
        result.Card.Should().Be(new CardViewDetails("Immanuel Kessler", "05", "2025", "Y"));
    }

    [Fact]
    public async Task UnreachableStore_ReportsFileError()
    {
        // FR-S05-11 (READ RESP other than NORMAL/NOTFND)
        var options = new DbContextOptionsBuilder<CardDemoDbContext>()
            .UseNpgsql("Host=127.0.0.1;Port=1;Database=carddemo;Username=nobody;Password=nobody;Timeout=1")
            .Options;
        await using var context = new CardDemoDbContext(options);
        var service = new CardViewService(new CardRepository(context));

        var result = await service.ViewAsync(new CardViewRequest(SeedAccount, SeedCard));

        result.Outcome.Should().Be(CardViewOutcome.StoreError);
        result.ErrorMessage.Should().StartWith("File Error: READ     on CARDDAT   returned RESP ");
        result.AccountFilter.Should().Be(CardViewFilterState.NotOk);
        result.Card.Should().BeNull();
    }
}
