using CardDemo.Application.Cards;
using CardDemo.Application.LegacyData;
using CardDemo.Application.Menu;
using CardDemo.Domain.Cards;
using CardDemo.Infrastructure.Persistence;
using CardDemo.Tests.Users;
using FluentAssertions;
using Microsoft.EntityFrameworkCore;

namespace CardDemo.Tests.Cards;

/// <summary>
/// COCRDLIC over the shared PostgreSQL cards table seeded from app/data/ASCII/carddata.txt
/// (50 cards, one per account) plus a three-card account added to exercise the account filter.
/// </summary>
public class CardListIntegrationTests(PostgresFixture fixture) : IClassFixture<PostgresFixture>, IAsyncLifetime
{
    private const string MultiCardAccount = "00000000777";
    private static readonly string[] MultiCardNumbers = ["7770000000000001", "7770000000000002", "7770000000000003"];

    private static readonly List<string> SeedCardNumbers = File.ReadLines(TestPaths.AsciiData("carddata.txt"))
        .Where(l => l.Length >= 16)
        .Select(l => l[..16])
        .Concat(MultiCardNumbers)
        .OrderBy(c => c, StringComparer.Ordinal)
        .ToList();

    public async Task InitializeAsync()
    {
        await using var context = fixture.CreateContext();
        if (await context.Cards.AnyAsync())
        {
            return;
        }
        var importer = new LegacyDataImportService(new LegacyDataWriter(context));
        await importer.ImportCardsAsync(File.ReadLines(TestPaths.AsciiData("carddata.txt")));
        context.Cards.AddRange(MultiCardNumbers.Select((n, i) => new Card
        {
            CardNumber = n,
            AccountId = MultiCardAccount,
            CvvCode = "999",
            EmbossedName = "MULTI CARD HOLDER",
            ExpirationDate = new DateOnly(2030, 12, 31),
            ActiveStatus = i == 1 ? "N" : "Y"
        }));
        await context.SaveChangesAsync();
    }

    public Task DisposeAsync() => Task.CompletedTask;

    private static MenuRouteRegistryOptions Registry(bool detailEnabled = false) => new()
    {
        Main =
        [
            new MenuRouteOption { Id = "03", Name = "Credit Card List", ProgramKey = "COCRDLIC", Enabled = false },
            new MenuRouteOption { Id = "04", Name = "Credit Card View", ProgramKey = "COCRDSLC", Enabled = detailEnabled, Route = "/cards/view" },
            new MenuRouteOption { Id = "05", Name = "Credit Card Update", ProgramKey = "COCRDUPC", Enabled = false, Route = "/cards/update" }
        ]
    };

    private async Task<CardListResult> RunAsync(CardListRequest request, MenuRouteRegistryOptions? registry = null)
    {
        await using var context = fixture.CreateContext();
        var service = new CardListService(new CardRepository(context), registry ?? Registry());
        return await service.ProcessAsync(request);
    }

    private static CardListRequest Reentry(CardListPageState state, string aid = "ENTER", string? account = null, string? card = null, params (int Row, string Code)[] selections)
    {
        var codes = new string?[CardListService.PageSize];
        foreach (var (row, code) in selections)
        {
            codes[row] = code;
        }
        return new CardListRequest(aid, state, account, card, codes);
    }

    private static IEnumerable<string?> CardNumbers(CardListResult result) => result.Rows.Select(r => r.Card?.CardNumber);

    [Fact]
    public async Task Repository_BrowseForward_IsKeyOrderedGteqAndFiltered()
    {
        await using var context = fixture.CreateContext();
        var repository = new CardRepository(context);

        var fromStart = await repository.BrowseForwardAsync(string.Empty, 7, null, null);
        fromStart.Select(c => c.CardNumber).Should().Equal(SeedCardNumbers.Take(7));

        var fromKey = await repository.BrowseForwardAsync(SeedCardNumbers[10], 3, null, null);
        fromKey.Select(c => c.CardNumber).Should().Equal(SeedCardNumbers.Skip(10).Take(3), "STARTBR GTEQ includes the start key");

        var byAccount = await repository.BrowseForwardAsync(string.Empty, 7, MultiCardAccount, null);
        byAccount.Select(c => c.CardNumber).Should().Equal(MultiCardNumbers);

        var byCard = await repository.BrowseForwardAsync(string.Empty, 7, null, MultiCardNumbers[1]);
        byCard.Should().ContainSingle().Which.CardNumber.Should().Be(MultiCardNumbers[1]);

        var disjoint = await repository.BrowseForwardAsync(string.Empty, 7, "00000000001", MultiCardNumbers[1]);
        disjoint.Should().BeEmpty();
    }

    [Fact]
    public async Task Repository_BrowseBackwardAndReadNext_MatchReadPrevReadNextSemantics()
    {
        await using var context = fixture.CreateContext();
        var repository = new CardRepository(context);

        var backward = await repository.BrowseBackwardAsync(SeedCardNumbers[10], 3, null, null);
        backward.Select(c => c.CardNumber).Should().Equal(SeedCardNumbers[9], SeedCardNumbers[8], SeedCardNumbers[7]);

        var beforeFirst = await repository.BrowseBackwardAsync(SeedCardNumbers[0], 7, null, null);
        beforeFirst.Should().BeEmpty();

        var next = await repository.ReadNextAsync(SeedCardNumbers[10]);
        next!.CardNumber.Should().Be(SeedCardNumbers[11]);

        var afterLast = await repository.ReadNextAsync(SeedCardNumbers[^1]);
        afterLast.Should().BeNull();
    }

    [Fact]
    public async Task FreshEntry_ListsFirstSevenSeedCards_FR01_FR02()
    {
        var result = await RunAsync(new CardListRequest("ENTER", null, null, null, null));

        result.Outcome.Should().Be(CardListOutcome.Display);
        result.ScreenNumber.Should().Be(1);
        CardNumbers(result).Should().Equal(SeedCardNumbers.Take(7));
        result.Rows.Should().OnlyContain(r => r.Card!.AccountId.Length == 11 && r.Card.CardNumber.Length == 16 && r.Card.ActiveStatus.Length == 1);
        result.State.LastCardNumber.Should().Be(SeedCardNumbers[7]);
        result.State.NextPageExists.Should().BeTrue();
        result.InfoMessage.Should().Be(CardListService.MsgInformRecActions);
    }

    [Fact]
    public async Task PagingForwardThenBackward_WalksTheWholeFile_FR07_FR08_FR09_FR10_FR11()
    {
        var pages = new List<CardListResult> { await RunAsync(new CardListRequest("ENTER", null, null, null, null)) };
        while (pages[^1].State.NextPageExists)
        {
            pages.Add(await RunAsync(Reentry(pages[^1].State, "PF8")));
        }

        pages.Should().HaveCount(8, "53 cards / 7 per page");
        pages.Select(p => p.ScreenNumber).Should().Equal(1, 2, 3, 4, 5, 6, 7, 8);
        pages.SelectMany(CardNumbers).Where(c => c is not null).Should().Equal(SeedCardNumbers);
        pages[^1].ErrorMessage.Should().Be(CardListService.MsgNoMoreRecords);
        pages[^1].State.LastPageShown.Should().BeTrue();
        CardNumbers(pages[^1]).Should().Equal(SeedCardNumbers[49], SeedCardNumbers[50], SeedCardNumbers[51], SeedCardNumbers[52], null, null, null);

        var beyond = await RunAsync(Reentry(pages[^1].State, "PF8"));
        beyond.ErrorMessage.Should().Be(CardListService.MsgNoMorePages);
        beyond.InfoMessage.Should().BeEmpty();
        CardNumbers(beyond).Should().Equal(CardNumbers(pages[^1]));

        var back = await RunAsync(Reentry(beyond.State, "PF7"));
        back.ScreenNumber.Should().Be(7);
        CardNumbers(back).Should().Equal(CardNumbers(pages[6]));
        back.ErrorMessage.Should().BeEmpty();

        var state = back.State;
        for (var screen = 6; screen >= 2; screen--)
        {
            var page = await RunAsync(Reentry(state, "PF7"));
            page.ScreenNumber.Should().Be(screen);
            CardNumbers(page).Should().Equal(CardNumbers(pages[screen - 1]));
            state = page.State;
        }

        var first = await RunAsync(Reentry(state, "PF7"));
        first.ScreenNumber.Should().Be(1);
        CardNumbers(first).Should().Equal(SeedCardNumbers.Take(7));
        first.ErrorMessage.Should().Be(CardListService.MsgNoPreviousPages);

        var again = await RunAsync(Reentry(first.State, "PF7"));
        again.ErrorMessage.Should().Be(CardListService.MsgNoPreviousPages);
        CardNumbers(again).Should().Equal(SeedCardNumbers.Take(7));
    }

    [Fact]
    public async Task AccountFilter_ListsOnlyThatAccount_FR05_FR08()
    {
        var pageOne = await RunAsync(new CardListRequest("ENTER", null, null, null, null));

        var result = await RunAsync(Reentry(pageOne.State, account: MultiCardAccount));

        CardNumbers(result).Should().Equal(MultiCardNumbers[0], MultiCardNumbers[1], MultiCardNumbers[2], null, null, null, null);
        result.Rows[1].Card!.ActiveStatus.Should().Be("N");
        result.ErrorMessage.Should().Be(CardListService.MsgNoMoreRecords);
        result.AccountFilter.Should().Be(MultiCardAccount);
        result.State.NextPageExists.Should().BeFalse();
    }

    [Fact]
    public async Task CardFilter_ListsThatCard_AndUnknownCardIsNoRecords_FR05_FR06()
    {
        var pageOne = await RunAsync(new CardListRequest("ENTER", null, null, null, null));

        var found = await RunAsync(Reentry(pageOne.State, card: SeedCardNumbers[20]));
        CardNumbers(found).Should().Equal(SeedCardNumbers[20], null, null, null, null, null, null);
        found.CardFilter.Should().Be(SeedCardNumbers[20]);

        var missing = await RunAsync(Reentry(pageOne.State, card: "9999999999999999"));
        missing.ErrorMessage.Should().Be(CardListService.MsgNoRecordsFound);
        missing.InfoMessage.Should().BeEmpty();
        missing.Rows.Should().OnlyContain(r => r.Card == null);
    }

    [Fact]
    public async Task InvalidFilters_ReportInOrderWithoutTouchingTheDatabaseRows_FR03_FR04()
    {
        var pageOne = await RunAsync(new CardListRequest("ENTER", null, null, null, null));

        var result = await RunAsync(Reentry(pageOne.State, account: "ABC", card: "12"));

        result.ErrorMessage.Should().Be(CardListService.MsgAccountFilterInvalid);
        result.AccountFilterError.Should().BeTrue();
        result.CardFilterError.Should().BeTrue();
        CardNumbers(result).Should().Equal(SeedCardNumbers.Take(7));
        result.Rows.Should().OnlyContain(r => r.SelectionProtected);
    }

    [Fact]
    public async Task Pf7_WithFilterExhaustingBackwardBrowse_BottomAlignsAndReportsFileError_FR22()
    {
        var pageOne = await RunAsync(new CardListRequest("ENTER", null, null, null, null));
        var pageTwo = await RunAsync(Reentry(pageOne.State, "PF8"));
        var pageThree = await RunAsync(Reentry(pageTwo.State, "PF8"));

        var result = await RunAsync(Reentry(pageThree.State, "PF7", account: pageOne.Rows[2].Card!.AccountId));

        result.ScreenNumber.Should().Be(2);
        CardNumbers(result).Should().Equal(null, null, null, null, null, null, SeedCardNumbers[2]);
        result.ErrorMessage.Should().Be(CardListService.MsgReadPrevExhausted);
        result.State.FirstCardNumber.Should().Be(pageThree.State.FirstCardNumber);
    }

    [Fact]
    public async Task SelectionErrors_AndHandOffsAgainstRealRows_FR12_FR13_FR15_FR16()
    {
        var pageOne = await RunAsync(new CardListRequest("ENTER", null, null, null, null));

        var invalid = await RunAsync(Reentry(pageOne.State, selections: [(2, "Q")]));
        invalid.ErrorMessage.Should().Be(CardListService.MsgInvalidActionCode);
        invalid.Rows[2].SelectionError.Should().BeTrue();

        var many = await RunAsync(Reentry(pageOne.State, selections: [(0, "S"), (6, "U")]));
        many.ErrorMessage.Should().Be(CardListService.MsgMoreThanOneAction);

        var comingSoon = await RunAsync(Reentry(pageOne.State, selections: [(4, "S")]));
        comingSoon.Outcome.Should().Be(CardListOutcome.ComingSoon);
        comingSoon.Target.Should().BeEquivalentTo(
            new CardListNavigationTarget("COCRDSLC", "/cards/view", pageOne.Rows[4].Card!.AccountId, SeedCardNumbers[4]));

        var navigate = await RunAsync(Reentry(pageOne.State, selections: [(4, "S")]), Registry(detailEnabled: true));
        navigate.Outcome.Should().Be(CardListOutcome.Navigate);
        navigate.Target!.Route.Should().Be("/cards/view");

        var update = await RunAsync(Reentry(pageOne.State, selections: [(4, "U")]));
        update.Outcome.Should().Be(CardListOutcome.ComingSoon);
        update.Target!.ProgramKey.Should().Be("COCRDUPC");
    }

    [Fact]
    public async Task Pf3_ExitsToMenu_FR17()
    {
        var pageOne = await RunAsync(new CardListRequest("ENTER", null, null, null, null));

        var result = await RunAsync(Reentry(pageOne.State, "PF3"));

        result.Outcome.Should().Be(CardListOutcome.Exit);
        result.Target!.Route.Should().Be("/menu");
    }
}
