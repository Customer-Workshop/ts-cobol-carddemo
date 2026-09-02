using CardDemo.Application.Cards;
using CardDemo.Application.Menu;
using CardDemo.Domain.Cards;
using FluentAssertions;

namespace CardDemo.Tests.Cards;

/// <summary>COCRDLIC parity (FR-S04-01..22) over an in-memory CARDDAT.</summary>
public class CardListServiceTests
{
    private static string Key(int i) => i.ToString("D16");

    private static string Acct(int i) => i.ToString("D11");

    private static MenuRouteRegistryOptions Registry(bool detailEnabled = false, bool updateEnabled = false) => new()
    {
        Main =
        [
            new MenuRouteOption { Id = "03", Name = "Credit Card List", ProgramKey = "COCRDLIC", Enabled = false },
            new MenuRouteOption { Id = "04", Name = "Credit Card View", ProgramKey = "COCRDSLC", Enabled = detailEnabled, Route = "/cards/view" },
            new MenuRouteOption { Id = "05", Name = "Credit Card Update", ProgramKey = "COCRDUPC", Enabled = updateEnabled, Route = "/cards/update" }
        ]
    };

    /// <summary>20 cards, 3 per account: account 1 → cards 1-3, account 2 → 4-6, ... account 7 → 19-20.</summary>
    private static CardListService Service(int cardCount = 20, MenuRouteRegistryOptions? registry = null) =>
        new(new FakeCardRepository(FakeCardRepository.Sequence(cardCount, cardsPerAccount: 3)), registry ?? Registry());

    private static CardListRequest Fresh(string aid = "ENTER") => new(aid, null, null, null, null);

    private static CardListRequest Reentry(
        CardListPageState state,
        string aid = "ENTER",
        string? account = null,
        string? card = null,
        params (int Row, string Code)[] selections)
    {
        var codes = new string?[CardListService.PageSize];
        foreach (var (row, code) in selections)
        {
            codes[row] = code;
        }
        return new CardListRequest(aid, state, account, card, codes);
    }

    private static async Task<CardListPageState> PageOneAsync(CardListService service)
    {
        var result = await service.ProcessAsync(Fresh());
        return result.State;
    }

    private static IEnumerable<string?> CardNumbers(CardListResult result) => result.Rows.Select(r => r.Card?.CardNumber);

    [Fact]
    public async Task FreshEntry_ListsFirstSevenCardsInKeyOrder_FR01_FR19()
    {
        var result = await Service().ProcessAsync(Fresh());

        result.Outcome.Should().Be(CardListOutcome.Display);
        result.ScreenNumber.Should().Be(1);
        result.Rows.Should().HaveCount(7);
        CardNumbers(result).Should().Equal(Enumerable.Range(1, 7).Select(Key));
        result.Rows[0].Card!.AccountId.Should().Be(Acct(1));
        result.Rows[0].Card!.ActiveStatus.Should().Be("Y");
        result.Rows[1].Card!.ActiveStatus.Should().Be("N");
        result.AccountFilter.Should().BeEmpty();
        result.CardFilter.Should().BeEmpty();
        result.ErrorMessage.Should().BeEmpty();
        result.InfoMessage.Should().Be(CardListService.MsgInformRecActions);
        result.State.FirstCardNumber.Should().Be(Key(1));
        result.State.LastCardNumber.Should().Be(Key(8), "the raw look-ahead record is the next-page anchor");
        result.State.NextPageExists.Should().BeTrue();
        result.State.LastPageShown.Should().BeFalse();
        result.Rows.Should().OnlyContain(r => !r.SelectionProtected && !r.SelectionError && r.Selection == string.Empty);
    }

    [Theory]
    [InlineData("PF1")]
    [InlineData("PF12")]
    [InlineData("CLEAR")]
    [InlineData(null)]
    public async Task UnmappedAid_IsTreatedAsEnter_FR18(string? aid)
    {
        var service = Service();
        var state = await PageOneAsync(service);

        var result = await service.ProcessAsync(Reentry(state, aid!, account: "123"));

        result.Outcome.Should().Be(CardListOutcome.Display);
        result.ErrorMessage.Should().Be(CardListService.MsgAccountFilterInvalid, "the edits ran exactly as for ENTER");
        CardListService.MapAid(aid).Should().Be(CardListAid.Enter);
    }

    [Theory]
    [InlineData("PF3", CardListAid.Pf3)]
    [InlineData("f7", CardListAid.Pf7)]
    [InlineData("PF8", CardListAid.Pf8)]
    [InlineData("ENTER", CardListAid.Enter)]
    public void MapAid_RecognisesTheFourValidKeys(string aid, CardListAid expected) =>
        CardListService.MapAid(aid).Should().Be(expected);

    [Theory]
    [InlineData("123")]
    [InlineData("1234567890A")]
    [InlineData("123456789012")]
    [InlineData(" 1234567890")]
    public async Task InvalidAccountFilter_ShowsMessageKeepsRowsAndProtectsSelect_FR03_FR20_FR21(string filter)
    {
        var service = Service();
        var state = await PageOneAsync(service);

        var result = await service.ProcessAsync(Reentry(state, account: filter));

        result.ErrorMessage.Should().Be(CardListService.MsgAccountFilterInvalid);
        result.InfoMessage.Should().BeEmpty();
        result.AccountFilterError.Should().BeTrue();
        result.CardFilterError.Should().BeFalse();
        result.AccountFilter.Should().Be(filter, "the typed value is echoed");
        result.CursorField.Should().Be(CardListService.CursorAccount);
        CardNumbers(result).Should().Equal(Enumerable.Range(1, 7).Select(Key), "no re-read on a filter error");
        result.Rows.Should().OnlyContain(r => r.SelectionProtected);
        result.State.Should().BeEquivalentTo(state with { LastPageShown = false });
    }

    [Fact]
    public async Task InvalidCardFilter_ShowsMessage_AccountMessageWins_FR04()
    {
        var service = Service();
        var state = await PageOneAsync(service);

        var cardOnly = await service.ProcessAsync(Reentry(state, card: "ABC"));
        cardOnly.ErrorMessage.Should().Be(CardListService.MsgCardFilterInvalid);
        cardOnly.CardFilterError.Should().BeTrue();
        cardOnly.CursorField.Should().Be(CardListService.CursorCard);
        cardOnly.CardFilter.Should().Be("ABC");
        cardOnly.Rows.Should().OnlyContain(r => r.SelectionProtected);

        var both = await service.ProcessAsync(Reentry(state, account: "12", card: "ABC"));
        both.ErrorMessage.Should().Be(CardListService.MsgAccountFilterInvalid);
        both.AccountFilterError.Should().BeTrue();
        both.CardFilterError.Should().BeTrue();
        both.CursorField.Should().Be(CardListService.CursorAccount);
    }

    [Fact]
    public async Task ValidAccountFilter_ListsOnlyThatAccountsCards_FR05_FR08()
    {
        var service = Service();
        var state = await PageOneAsync(service);

        var result = await service.ProcessAsync(Reentry(state, account: Acct(2)));

        CardNumbers(result).Should().Equal(Key(4), Key(5), Key(6), null, null, null, null);
        result.ErrorMessage.Should().Be(CardListService.MsgNoMoreRecords);
        result.InfoMessage.Should().Be(CardListService.MsgInformRecActions);
        result.AccountFilter.Should().Be(Acct(2));
        result.State.NextPageExists.Should().BeFalse();
        result.State.FirstCardNumber.Should().Be(Key(4));
        result.Rows.Take(3).Should().OnlyContain(r => !r.SelectionProtected);
        result.Rows.Skip(3).Should().OnlyContain(r => r.SelectionProtected, "empty rows are protected (FR-S04-21)");
    }

    [Fact]
    public async Task ValidCardFilter_ListsThatCard_AndFiltersCombineWithAnd_FR05()
    {
        var service = Service();
        var state = await PageOneAsync(service);

        var cardOnly = await service.ProcessAsync(Reentry(state, card: Key(5)));
        CardNumbers(cardOnly).Should().Equal(Key(5), null, null, null, null, null, null);
        cardOnly.CardFilter.Should().Be(Key(5));

        var matching = await service.ProcessAsync(Reentry(state, account: Acct(2), card: Key(5)));
        CardNumbers(matching).Should().Equal(Key(5), null, null, null, null, null, null);

        var disjoint = await service.ProcessAsync(Reentry(state, account: Acct(1), card: Key(5)));
        disjoint.ErrorMessage.Should().Be(CardListService.MsgNoRecordsFound);
    }

    [Fact]
    public async Task FilterAppliesFromTheCurrentFirstAnchor_NotFileStart_FR05()
    {
        var service = Service();
        var pageOne = await PageOneAsync(service);
        var pageTwo = (await service.ProcessAsync(Reentry(pageOne, "PF8"))).State;

        var result = await service.ProcessAsync(Reentry(pageTwo, account: Acct(1)));

        CardNumbers(result).Should().OnlyContain(c => c == null, "account 1's cards precede page 2's first anchor");
        result.ErrorMessage.Should().Be(CardListService.MsgNoMoreRecords, "page number is 2 so it is not the no-records case");
    }

    [Theory]
    [InlineData("", "")]
    [InlineData("00000000000", "0000000000000000")]
    [InlineData("000", "0")]
    [InlineData("   ", "  ")]
    public async Task BlankOrZeroFilters_MeanNoFilter_AndEchoBlank_FR03_FR04_FR20(string account, string card)
    {
        var service = Service();
        var state = await PageOneAsync(service);

        var result = await service.ProcessAsync(Reentry(state, account: account, card: card));

        result.ErrorMessage.Should().BeEmpty();
        CardNumbers(result).Should().Equal(Enumerable.Range(1, 7).Select(Key));
        result.AccountFilter.Should().BeEmpty();
        result.CardFilter.Should().BeEmpty();
    }

    [Fact]
    public async Task NoMatchOnPageOne_ShowsNoRecordsFound_WithoutInfoMessage_FR06()
    {
        var service = Service();
        var state = await PageOneAsync(service);

        var result = await service.ProcessAsync(Reentry(state, account: Acct(99)));

        result.ErrorMessage.Should().Be(CardListService.MsgNoRecordsFound);
        result.InfoMessage.Should().BeEmpty();
        result.Rows.Should().OnlyContain(r => r.Card == null && r.SelectionProtected);
        result.ScreenNumber.Should().Be(1);
    }

    [Fact]
    public async Task Pf8_WithNextPage_ShowsNextSevenFromAnchor_FR07()
    {
        var service = Service();
        var state = await PageOneAsync(service);

        var result = await service.ProcessAsync(Reentry(state, "PF8"));

        result.ScreenNumber.Should().Be(2);
        CardNumbers(result).Should().Equal(Enumerable.Range(8, 7).Select(Key));
        result.State.FirstCardNumber.Should().Be(Key(8));
        result.State.LastCardNumber.Should().Be(Key(15));
        result.State.NextPageExists.Should().BeTrue();
        result.ErrorMessage.Should().BeEmpty();
        result.InfoMessage.Should().Be(CardListService.MsgInformRecActions);
    }

    [Fact]
    public async Task Pf8_ReachingEndOfFile_ShowsNoMoreRecords_FR08()
    {
        var service = Service(cardCount: 10);
        var state = await PageOneAsync(service);

        var result = await service.ProcessAsync(Reentry(state, "PF8"));

        result.ScreenNumber.Should().Be(2);
        CardNumbers(result).Should().Equal(Key(8), Key(9), Key(10), null, null, null, null);
        result.ErrorMessage.Should().Be(CardListService.MsgNoMoreRecords);
        result.InfoMessage.Should().Be(CardListService.MsgInformRecActions);
        result.State.NextPageExists.Should().BeFalse();
        result.State.LastPageShown.Should().BeTrue();
    }

    [Fact]
    public async Task ExactlySevenRemaining_LookAheadEndsFile_ShowsNoMoreRecords_FR08()
    {
        var service = Service(cardCount: 14);
        var state = await PageOneAsync(service);

        var result = await service.ProcessAsync(Reentry(state, "PF8"));

        CardNumbers(result).Should().Equal(Enumerable.Range(8, 7).Select(Key));
        result.ErrorMessage.Should().Be(CardListService.MsgNoMoreRecords);
        result.State.NextPageExists.Should().BeFalse();
        result.State.LastCardNumber.Should().Be(Key(14));
    }

    [Fact]
    public async Task Pf8_AgainOnLastPage_ShowsNoMorePages_ThenAnyOtherKeyResets_FR09()
    {
        var service = Service(cardCount: 10);
        var pageOne = await PageOneAsync(service);
        var lastPage = (await service.ProcessAsync(Reentry(pageOne, "PF8"))).State;

        var again = await service.ProcessAsync(Reentry(lastPage, "PF8"));

        again.ScreenNumber.Should().Be(2);
        CardNumbers(again).Should().Equal(Key(8), Key(9), Key(10), null, null, null, null);
        again.ErrorMessage.Should().Be(CardListService.MsgNoMorePages);
        again.InfoMessage.Should().BeEmpty();
        again.State.LastPageShown.Should().BeTrue();

        var enter = await service.ProcessAsync(Reentry(again.State));
        enter.ErrorMessage.Should().Be(CardListService.MsgNoMoreRecords);
        enter.InfoMessage.Should().Be(CardListService.MsgInformRecActions);
        enter.State.LastPageShown.Should().BeFalse();
    }

    [Fact]
    public async Task Pf7_FromPageThree_ListsPrecedingSeven_FR10()
    {
        var service = Service();
        var pageOne = await PageOneAsync(service);
        var pageTwo = (await service.ProcessAsync(Reentry(pageOne, "PF8"))).State;
        var pageThree = (await service.ProcessAsync(Reentry(pageTwo, "PF8"))).State;
        pageThree.ScreenNumber.Should().Be(3);
        pageThree.FirstCardNumber.Should().Be(Key(15));

        var result = await service.ProcessAsync(Reentry(pageThree, "PF7"));

        result.ScreenNumber.Should().Be(2);
        CardNumbers(result).Should().Equal(Enumerable.Range(8, 7).Select(Key));
        result.State.FirstCardNumber.Should().Be(Key(8));
        result.State.LastCardNumber.Should().Be(Key(15), "the old first anchor becomes the next-page anchor");
        result.State.NextPageExists.Should().BeTrue();
        result.ErrorMessage.Should().BeEmpty();
        result.InfoMessage.Should().Be(CardListService.MsgInformRecActions);
    }

    [Fact]
    public async Task Pf7_LandingOnPageOne_ShowsNoPreviousPages_FR10_FR11()
    {
        var service = Service();
        var pageOne = await PageOneAsync(service);
        var pageTwo = (await service.ProcessAsync(Reentry(pageOne, "PF8"))).State;

        var result = await service.ProcessAsync(Reentry(pageTwo, "PF7"));

        result.ScreenNumber.Should().Be(1);
        CardNumbers(result).Should().Equal(Enumerable.Range(1, 7).Select(Key));
        result.ErrorMessage.Should().Be(CardListService.MsgNoPreviousPages, "COCRDLIC.cbl:901-904 tests CA-FIRST-PAGE after the decrement");
        result.InfoMessage.Should().BeEmpty();
    }

    [Fact]
    public async Task Pf7_OnPageOne_RelistsWithNoPreviousPages_FR11()
    {
        var service = Service();
        var state = await PageOneAsync(service);

        var result = await service.ProcessAsync(Reentry(state, "PF7"));

        result.ScreenNumber.Should().Be(1);
        CardNumbers(result).Should().Equal(Enumerable.Range(1, 7).Select(Key));
        result.ErrorMessage.Should().Be(CardListService.MsgNoPreviousPages);
        result.InfoMessage.Should().BeEmpty();
        result.State.NextPageExists.Should().BeTrue();
    }

    [Fact]
    public async Task InvalidSelectionCode_ShowsInvalidActionCode_FlagsRow_FR12()
    {
        var service = Service();
        var state = await PageOneAsync(service);

        var result = await service.ProcessAsync(Reentry(state, selections: [(1, "X")]));

        result.Outcome.Should().Be(CardListOutcome.Display);
        result.ErrorMessage.Should().Be(CardListService.MsgInvalidActionCode);
        result.InfoMessage.Should().Be(CardListService.MsgInformRecActions);
        result.Rows[1].SelectionError.Should().BeTrue();
        result.Rows[1].Selection.Should().Be("X");
        result.Rows.Where((_, i) => i != 1).Should().OnlyContain(r => !r.SelectionError);
        result.CursorField.Should().Be("select2");
    }

    [Theory]
    [InlineData("s")]
    [InlineData("u")]
    [InlineData("1")]
    public async Task LowerCaseOrOtherCodes_AreInvalidActionCodes_FR12(string code)
    {
        var service = Service();
        var state = await PageOneAsync(service);

        var result = await service.ProcessAsync(Reentry(state, selections: [(0, code)]));

        result.ErrorMessage.Should().Be(CardListService.MsgInvalidActionCode);
        result.Rows[0].SelectionError.Should().BeTrue();
    }

    [Fact]
    public async Task MultipleSelections_ShowSelectOnlyOne_FlagsEverySelectedRow_FR13()
    {
        var service = Service();
        var state = await PageOneAsync(service);

        var result = await service.ProcessAsync(Reentry(state, selections: [(0, "S"), (2, "U"), (4, "X")]));

        result.Outcome.Should().Be(CardListOutcome.Display);
        result.ErrorMessage.Should().Be(CardListService.MsgMoreThanOneAction, "counted before the per-row code check");
        result.Rows.Select(r => r.SelectionError).Should().Equal(true, false, true, false, true, false, false);
        result.Rows.Select(r => r.Selection).Should().Equal("S", "", "U", "", "X", "", "");
    }

    [Fact]
    public async Task FilterErrorSkipsSelectionEdits_FR03_FR13()
    {
        var service = Service();
        var state = await PageOneAsync(service);

        var result = await service.ProcessAsync(Reentry(state, account: "12", selections: [(0, "S"), (1, "S")]));

        result.ErrorMessage.Should().Be(CardListService.MsgAccountFilterInvalid);
        result.Rows.Should().OnlyContain(r => !r.SelectionError);
    }

    [Fact]
    public async Task SelectionError_RelistsFromFileStart_KeepingPageNumber_FR14()
    {
        var service = Service();
        var pageOne = await PageOneAsync(service);
        var pageTwo = (await service.ProcessAsync(Reentry(pageOne, "PF8"))).State;
        var pageThree = (await service.ProcessAsync(Reentry(pageTwo, "PF8"))).State;

        var result = await service.ProcessAsync(Reentry(pageThree, selections: [(3, "Z")]));

        result.ScreenNumber.Should().Be(3);
        CardNumbers(result).Should().Equal(Enumerable.Range(1, 7).Select(Key));
        result.State.FirstCardNumber.Should().Be(Key(1));
        result.Rows[3].Selection.Should().Be("Z");
        result.Rows[3].SelectionError.Should().BeTrue();
    }

    [Fact]
    public async Task SelectS_WithDetailDisabled_YieldsComingSoonWithSelectedCardContext_FR15()
    {
        var service = Service();
        var state = await PageOneAsync(service);

        var result = await service.ProcessAsync(Reentry(state, selections: [(1, "S")]));

        result.Outcome.Should().Be(CardListOutcome.ComingSoon);
        result.Message.Should().Be("This option Credit Card View is coming soon ...");
        result.Severity.Should().Be("info");
        result.Target.Should().BeEquivalentTo(new CardListNavigationTarget("COCRDSLC", "/cards/view", Acct(1), Key(2)));
        CardNumbers(result).Should().Equal(Enumerable.Range(1, 7).Select(Key), "the page is redisplayed");
        result.Rows[1].Selection.Should().Be("S");
    }

    [Fact]
    public async Task SelectS_WithDetailEnabled_NavigatesWithAccountAndCard_FR15()
    {
        var service = Service(registry: Registry(detailEnabled: true));
        var state = await PageOneAsync(service);

        var result = await service.ProcessAsync(Reentry(state, selections: [(6, "S")]));

        result.Outcome.Should().Be(CardListOutcome.Navigate);
        result.Target.Should().BeEquivalentTo(new CardListNavigationTarget("COCRDSLC", "/cards/view", Acct(3), Key(7)));
        result.Message.Should().BeNull();
    }

    [Fact]
    public async Task SelectU_RoutesToCardUpdate_FR16()
    {
        var disabled = Service();
        var state = await PageOneAsync(disabled);
        var comingSoon = await disabled.ProcessAsync(Reentry(state, selections: [(0, "U")]));
        comingSoon.Outcome.Should().Be(CardListOutcome.ComingSoon);
        comingSoon.Message.Should().Be("This option Credit Card Update is coming soon ...");
        comingSoon.Target!.ProgramKey.Should().Be("COCRDUPC");

        var enabled = Service(registry: Registry(updateEnabled: true));
        var navigate = await enabled.ProcessAsync(Reentry(state, selections: [(0, "U")]));
        navigate.Outcome.Should().Be(CardListOutcome.Navigate);
        navigate.Target.Should().BeEquivalentTo(new CardListNavigationTarget("COCRDUPC", "/cards/update", Acct(1), Key(1)));
    }

    [Fact]
    public async Task SelectionTargetMissingFromRegistry_IsNotInstalled_FR15()
    {
        var service = Service(registry: new MenuRouteRegistryOptions());
        var state = await PageOneAsync(service);

        var result = await service.ProcessAsync(Reentry(state, selections: [(0, "S")]));

        result.Outcome.Should().Be(CardListOutcome.NotInstalled);
        result.Message.Should().Be("This option COCRDSLC is not installed...");
        result.Severity.Should().Be("error");
    }

    [Fact]
    public async Task Pf3_OnReentry_ExitsToMainMenu_FR17()
    {
        var service = Service();
        var state = await PageOneAsync(service);

        var result = await service.ProcessAsync(Reentry(state, "PF3", account: "bad", selections: [(0, "X")]));

        result.Outcome.Should().Be(CardListOutcome.Exit);
        result.Target!.Route.Should().Be(CardListService.MenuRoute);
        result.ErrorMessage.Should().BeEmpty();
    }

    [Fact]
    public async Task Pf3_OnFreshEntry_ListsPageOne_FR17()
    {
        var result = await Service().ProcessAsync(Fresh("PF3"));

        result.Outcome.Should().Be(CardListOutcome.Display);
        CardNumbers(result).Should().Equal(Enumerable.Range(1, 7).Select(Key));
    }

    [Fact]
    public async Task Pf7_BackwardExhaustedByFilter_BottomAlignsRowsAndShowsFileError_FR22()
    {
        var service = Service();
        var pageOne = await PageOneAsync(service);
        var pageTwo = (await service.ProcessAsync(Reentry(pageOne, "PF8"))).State;
        var pageThree = (await service.ProcessAsync(Reentry(pageTwo, "PF8"))).State;

        var result = await service.ProcessAsync(Reentry(pageThree, "PF7", account: Acct(4)));

        result.ScreenNumber.Should().Be(2);
        CardNumbers(result).Should().Equal(null, null, null, null, Key(10), Key(11), Key(12));
        result.ErrorMessage.Should().Be(CardListService.MsgReadPrevExhausted);
        result.InfoMessage.Should().Be(CardListService.MsgInformRecActions);
        result.State.FirstCardNumber.Should().Be(Key(15), "the first anchor only moves when 7 rows were found");
        result.State.LastCardNumber.Should().Be(Key(15));
        result.Rows.Take(4).Should().OnlyContain(r => r.SelectionProtected);
        result.Rows.Skip(4).Should().OnlyContain(r => !r.SelectionProtected);
    }

    [Fact]
    public async Task Pf8_FromPageNine_WrapsSingleDigitPageNumber()
    {
        var service = Service(cardCount: 80);
        var state = new CardListPageState(9, Key(57), Key(64), true, false, new CardListRow?[7]);

        var result = await service.ProcessAsync(Reentry(state, "PF8"));

        result.ScreenNumber.Should().Be(1, "PIC 9(1): 9 + 1 truncates to 0, which the first row bumps to 1");
        CardNumbers(result).Should().Equal(Enumerable.Range(64, 7).Select(Key));
    }

    [Fact]
    public async Task InfoMessage_SuppressedOnlyForTheDocumentedCases_FR19()
    {
        var service = Service(cardCount: 10);
        var pageOne = await PageOneAsync(service);

        (await service.ProcessAsync(Reentry(pageOne))).InfoMessage.Should().Be(CardListService.MsgInformRecActions);
        (await service.ProcessAsync(Reentry(pageOne, account: "x"))).InfoMessage.Should().BeEmpty();
        (await service.ProcessAsync(Reentry(pageOne, "PF7"))).InfoMessage.Should().BeEmpty();
        (await service.ProcessAsync(Reentry(pageOne, account: Acct(50)))).InfoMessage.Should().BeEmpty();
        var last = await service.ProcessAsync(Reentry(pageOne, "PF8"));
        last.InfoMessage.Should().Be(CardListService.MsgInformRecActions);
        (await service.ProcessAsync(Reentry(last.State, "PF8"))).InfoMessage.Should().BeEmpty();
    }

    [Fact]
    public async Task SelectOnBlankRow_HandsOffEmptyContext()
    {
        var service = Service(cardCount: 3, registry: Registry(detailEnabled: true));
        var state = await PageOneAsync(service);

        var result = await service.ProcessAsync(Reentry(state, selections: [(5, "S")]));

        result.Outcome.Should().Be(CardListOutcome.Navigate);
        result.Target!.AccountId.Should().BeEmpty();
        result.Target.CardNumber.Should().BeEmpty();
    }

    [Fact]
    public void InitialState_HasSevenBlankRowsOnPageOne()
    {
        var state = CardListPageState.Initial();

        state.ScreenNumber.Should().Be(1);
        state.Rows.Should().HaveCount(7).And.OnlyContain(r => r == null);
        state.NextPageExists.Should().BeFalse();
        state.LastPageShown.Should().BeFalse();
    }
}
