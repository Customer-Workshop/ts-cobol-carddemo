using CardDemo.Application.Menu;
using CardDemo.Application.Transactions;
using CardDemo.Domain.Transactions;
using FluentAssertions;
using Microsoft.Extensions.Configuration;

namespace CardDemo.Tests.Transactions;

/// <summary>
/// Parity tests for COTRN00C PROCESS-ENTER-KEY / PF7 / PF8 / PAGE-FORWARD / PAGE-BACKWARD (FR-S07-02..17, 20, 21)
/// over a key-ordered in-memory TRANSACT stand-in.
/// </summary>
public class TransactionListServiceTests
{
    private static MenuRouteRegistryOptions LoadApiRegistry() =>
        new ConfigurationBuilder()
            .AddJsonFile(Path.Combine(TestPaths.RepoRoot, "backend", "CardDemo.Api", "appsettings.json"))
            .Build()
            .GetSection(MenuRouteRegistryOptions.SectionName)
            .Get<MenuRouteRegistryOptions>()!;

    private static TransactionListService Service(IEnumerable<Transaction> records, MenuRouteRegistryOptions? registry = null) =>
        new(new InMemoryTransactionRepository(records), registry ?? LoadApiRegistry());

    private static TransactionListRequest Enter(
        string? search = null,
        string? selectionFlag = null,
        string? selectedTranId = null,
        TransactionListState? state = null) =>
        new(TransactionListAction.Enter, search, selectionFlag, selectedTranId, state ?? TransactionListState.Initial);

    private static TransactionListRequest Pf7(TransactionListState state) =>
        new(TransactionListAction.PageBackward, null, null, null, state);

    private static TransactionListRequest Pf8(TransactionListState state) =>
        new(TransactionListAction.PageForward, null, null, null, state);

    private static string Id(int n) => n.ToString("D16");

    private static IEnumerable<string> Ids(TransactionListResult result) => result.Rows!.Select(r => r.TranId);

    [Fact]
    public async Task FirstEntry_BlankSearch_ShowsFirstTenRecordsAsPage1()
    {
        // FR-S07-02, FR-S07-03, FR-S07-07
        var result = await Service(TransactionFixtures.Sequence(25)).ProcessAsync(Enter());

        result.Outcome.Should().Be(TransactionListOutcome.Redisplay);
        result.Rows.Should().HaveCount(10);
        Ids(result).Should().Equal(Enumerable.Range(1, 10).Select(Id));
        result.State.PageNumber.Should().Be(1);
        result.State.NextPageAvailable.Should().BeTrue();
        result.State.FirstTranId.Should().Be(Id(1));
        result.State.LastTranId.Should().Be(Id(10));
        result.Message.Should().BeEmpty();
        result.ClearSearchInput.Should().BeTrue();
    }

    [Fact]
    public async Task Enter_BlankSearchFromLaterPage_RestartsAtPage1()
    {
        // FR-S07-03: MOVE 0 TO CDEMO-CT00-PAGE-NUM then browse from LOW-VALUES
        var state = new TransactionListState(Id(11), Id(20), 2, true);

        var result = await Service(TransactionFixtures.Sequence(25)).ProcessAsync(Enter("   ", state: state));

        Ids(result).First().Should().Be(Id(1));
        result.State.PageNumber.Should().Be(1);
    }

    [Fact]
    public async Task Enter_NumericKey_StartsAtFirstRecordAtOrAfterKey()
    {
        // FR-S07-04: STARTBR GTEQ
        var records = TransactionFixtures.Sequence(30).Where(t => t.TransactionId != Id(15)).ToList();

        var result = await Service(records).ProcessAsync(Enter(Id(15), state: new TransactionListState(Id(1), Id(10), 3, true)));

        Ids(result).First().Should().Be(Id(16));
        result.State.PageNumber.Should().Be(1);
        result.ClearSearchInput.Should().BeTrue();
    }

    [Theory]
    [InlineData("12A")]
    [InlineData("123")]
    [InlineData("000000000000001 ")]
    [InlineData("ABCDEFGHIJKLMNOP")]
    public async Task Enter_NonNumericSearch_ReportsNumericErrorAndKeepsScreen(string search)
    {
        // FR-S07-05
        var state = new TransactionListState(Id(11), Id(20), 2, true);

        var result = await Service(TransactionFixtures.Sequence(25)).ProcessAsync(Enter(search, state: state));

        result.Message.Should().Be("Tran ID must be Numeric ...");
        result.Severity.Should().Be(MenuMessageSeverity.Error);
        result.Rows.Should().BeNull();
        result.ClearSearchInput.Should().BeFalse();
        result.State.Should().Be(state);
    }

    [Fact]
    public async Task Rows_AreFormattedLikePopulateTranData()
    {
        // FR-S07-06
        var records = new[]
        {
            TransactionFixtures.Make(1, 12.34m, "A description that is definitely longer than 26", new DateTime(2022, 7, 19, 1, 2, 3)),
            TransactionFixtures.Make(2, -250m, "Refund", new DateTime(2023, 12, 1)),
            TransactionFixtures.Make(3, 123456789.99m, "Overflow", new DateTime(2020, 1, 31)),
            TransactionFixtures.Make(4, 0m, "Zero")
        };
        records[3].OriginalTimestamp = null;

        var result = await Service(records).ProcessAsync(Enter());

        result.Rows![0].Should().Be(new TransactionListRow(Id(1), "07/19/22", "A description that is defi", "+00000012.34"));
        result.Rows[1].Should().Be(new TransactionListRow(Id(2), "12/01/23", "Refund", "-00000250.00"));
        result.Rows[2].Amount.Should().Be("+23456789.99");
        result.Rows[3].Should().Be(new TransactionListRow(Id(4), "", "Zero", "+00000000.00"));
        result.Rows.Skip(4).Should().AllSatisfy(r => r.Should().Be(TransactionListRow.Blank));
    }

    [Fact]
    public async Task Enter_ExactlyTenRecordsRemaining_ReportsBottomAndDisablesForward()
    {
        // FR-S07-08: 10 rows filled, peek READNEXT hits ENDFILE
        var result = await Service(TransactionFixtures.Sequence(10)).ProcessAsync(Enter());

        result.Rows.Should().HaveCount(10);
        Ids(result).Last().Should().Be(Id(10));
        result.Message.Should().Be("You have reached the bottom of the page...");
        result.State.PageNumber.Should().Be(1);
        result.State.NextPageAvailable.Should().BeFalse();
    }

    [Fact]
    public async Task Enter_FewerThanTenRecords_FillsPartialPageAndReportsBottom()
    {
        // FR-S07-08: ENDFILE during the fill loop, page incremented because WS-IDX > 1
        var result = await Service(TransactionFixtures.Sequence(23)).ProcessAsync(Enter(Id(21), state: new TransactionListState(Id(1), Id(10), 1, true)));

        Ids(result).Take(3).Should().Equal(Id(21), Id(22), Id(23));
        result.Rows!.Skip(3).Should().AllSatisfy(r => r.IsBlank.Should().BeTrue());
        result.Message.Should().Be("You have reached the bottom of the page...");
        result.State.PageNumber.Should().Be(1);
        result.State.NextPageAvailable.Should().BeFalse();
        result.State.FirstTranId.Should().Be(Id(21));
        result.State.LastTranId.Should().Be(Id(10), "TRNID-LAST is only set when row 10 is populated");
    }

    [Fact]
    public async Task Enter_KeyBeyondLastRecord_ReportsAtTopAndKeepsRows()
    {
        // FR-S07-09: STARTBR NOTFND
        var state = new TransactionListState(Id(1), Id(10), 1, true);

        var result = await Service(TransactionFixtures.Sequence(25)).ProcessAsync(Enter(Id(9999), state: state));

        result.Message.Should().Be("You are at the top of the page...");
        result.Rows.Should().BeNull();
        result.State.PageNumber.Should().Be(0);
        result.State.NextPageAvailable.Should().BeFalse();
        result.ClearSearchInput.Should().BeTrue();
    }

    [Fact]
    public async Task Enter_EmptyFile_ReportsAtTop()
    {
        // FR-S07-09 on an empty TRANSACT
        var result = await Service([]).ProcessAsync(Enter());

        result.Message.Should().Be("You are at the top of the page...");
        result.Rows.Should().BeNull();
        result.State.PageNumber.Should().Be(0);
    }

    [Fact]
    public async Task Pf8_WithNextPage_ShowsRecordsAfterLastDisplayedId()
    {
        // FR-S07-10: STARTBR at TRNID-LAST, first READNEXT skipped
        var state = new TransactionListState(Id(1), Id(10), 1, true);

        var result = await Service(TransactionFixtures.Sequence(25)).ProcessAsync(Pf8(state));

        Ids(result).Should().Equal(Enumerable.Range(11, 10).Select(Id));
        result.State.PageNumber.Should().Be(2);
        result.State.NextPageAvailable.Should().BeTrue();
        result.State.FirstTranId.Should().Be(Id(11));
        result.State.LastTranId.Should().Be(Id(20));
        result.Message.Should().BeEmpty();
        result.ClearSearchInput.Should().BeTrue();
    }

    [Fact]
    public async Task Pf8_IntoLastPartialPage_ReportsBottom()
    {
        // FR-S07-08 via PF8
        var state = new TransactionListState(Id(11), Id(20), 2, true);

        var result = await Service(TransactionFixtures.Sequence(25)).ProcessAsync(Pf8(state));

        Ids(result).Take(5).Should().Equal(Enumerable.Range(21, 5).Select(Id));
        result.Rows!.Skip(5).Should().AllSatisfy(r => r.IsBlank.Should().BeTrue());
        result.Message.Should().Be("You have reached the bottom of the page...");
        result.State.PageNumber.Should().Be(3);
        result.State.NextPageAvailable.Should().BeFalse();
    }

    [Fact]
    public async Task Pf8_WhenNoNextPage_ReportsAlreadyAtBottomWithoutBrowsing()
    {
        // FR-S07-11
        var repository = new InMemoryTransactionRepository(TransactionFixtures.Sequence(25));
        var state = new TransactionListState(Id(21), Id(20), 3, false);

        var result = await new TransactionListService(repository, LoadApiRegistry()).ProcessAsync(Pf8(state));

        result.Message.Should().Be("You are already at the bottom of the page...");
        result.Rows.Should().BeNull();
        result.State.Should().Be(state);
        result.ClearSearchInput.Should().BeFalse();
        repository.BrowseCalls.Should().Be(0);
    }

    [Fact]
    public async Task Pf8_WithBlankLastIdAndForcedFlag_UsesHighValuesAndReportsAtTop()
    {
        // FR-S07-13 side effect followed by PF8: TRNID-LAST blank → HIGH-VALUES → NOTFND
        var state = new TransactionListState(string.Empty, string.Empty, 0, true);

        var result = await Service(TransactionFixtures.Sequence(25)).ProcessAsync(Pf8(state));

        result.Message.Should().Be("You are at the top of the page...");
        result.Rows.Should().BeNull();
        result.State.NextPageAvailable.Should().BeFalse();
    }

    [Fact]
    public async Task Pf7_FromPage3_ShowsPrecedingTenRecordsAsPage2()
    {
        // FR-S07-12
        var state = new TransactionListState(Id(21), Id(25), 3, false);

        var result = await Service(TransactionFixtures.Sequence(25)).ProcessAsync(Pf7(state));

        Ids(result).Should().Equal(Enumerable.Range(11, 10).Select(Id));
        result.State.PageNumber.Should().Be(2);
        result.State.NextPageAvailable.Should().BeTrue("PROCESS-PF7-KEY sets NEXT-PAGE-YES");
        result.State.FirstTranId.Should().Be(Id(11));
        result.State.LastTranId.Should().Be(Id(20));
        result.Message.Should().BeEmpty();
        result.ClearSearchInput.Should().BeFalse();
    }

    [Fact]
    public async Task Pf7_WhenPageIsOne_ReportsAlreadyAtTop()
    {
        // FR-S07-13
        var state = new TransactionListState(Id(1), Id(10), 1, false);

        var result = await Service(TransactionFixtures.Sequence(25)).ProcessAsync(Pf7(state));

        result.Message.Should().Be("You are already at the top of the page...");
        result.Rows.Should().BeNull();
        result.State.PageNumber.Should().Be(1);
        result.State.NextPageAvailable.Should().BeTrue();
    }

    [Fact]
    public async Task Pf7_WhenPageIsZero_ReportsAlreadyAtTop()
    {
        // FR-S07-13 after a NOTFND ENTER left PAGE-NUM = 0
        var result = await Service(TransactionFixtures.Sequence(25)).ProcessAsync(Pf7(new TransactionListState(Id(1), Id(10), 0, false)));

        result.Message.Should().Be("You are already at the top of the page...");
        result.Rows.Should().BeNull();
    }

    [Fact]
    public async Task Pf7_ReachingFirstRecordWithFullPage_ReportsTopAndPage1()
    {
        // FR-S07-14: ten READPREVs succeed, peek READPREV hits ENDFILE
        var state = new TransactionListState(Id(11), Id(20), 2, true);

        var result = await Service(TransactionFixtures.Sequence(25)).ProcessAsync(Pf7(state));

        Ids(result).Should().Equal(Enumerable.Range(1, 10).Select(Id));
        result.Message.Should().Be("You have reached the top of the page...");
        result.State.PageNumber.Should().Be(1);
    }

    [Fact]
    public async Task Pf7_WithFewerThanTenPredecessors_FillsBottomUpAndKeepsPageNumber()
    {
        // FR-S07-14: ENDFILE during the fill loop (page reached via search key)
        var state = new TransactionListState(Id(4), Id(13), 2, true);

        var result = await Service(TransactionFixtures.Sequence(25)).ProcessAsync(Pf7(state));

        result.Rows!.Take(7).Should().AllSatisfy(r => r.IsBlank.Should().BeTrue());
        Ids(result).Skip(7).Should().Equal(Id(1), Id(2), Id(3));
        result.Message.Should().Be("You have reached the top of the page...");
        result.State.PageNumber.Should().Be(2);
        result.State.FirstTranId.Should().Be(Id(4), "TRNID-FIRST is only set when row 1 is populated");
        result.State.LastTranId.Should().Be(Id(3));
    }

    [Fact]
    public async Task Pf7_WhenFirstIdBeyondFile_ReportsAtTop()
    {
        // FR-S07-09 on the backward STARTBR
        var state = new TransactionListState(Id(9999), Id(9999), 5, false);

        var result = await Service(TransactionFixtures.Sequence(25)).ProcessAsync(Pf7(state));

        result.Message.Should().Be("You are at the top of the page...");
        result.Rows.Should().BeNull();
        result.State.PageNumber.Should().Be(5);
    }

    [Fact]
    public async Task Enter_SelectWithS_WhileCotrn01cDisabled_ReturnsComingSoon()
    {
        // FR-S07-15 under seam S07-B1 (registry with COTRN01C disabled)
        var registry = new MenuRouteRegistryOptions
        {
            Main = [new MenuRouteOption { Id = "07", Name = "Transaction View", ProgramKey = "COTRN01C", Enabled = false }]
        };
        var state = new TransactionListState(Id(1), Id(10), 1, true);

        var result = await Service(TransactionFixtures.Sequence(25), registry).ProcessAsync(Enter(selectionFlag: "S", selectedTranId: Id(3), state: state));

        result.Outcome.Should().Be(TransactionListOutcome.ComingSoon);
        result.Message.Should().Be("This option Transaction View is coming soon ...");
        result.Severity.Should().Be(MenuMessageSeverity.Info);
        result.SelectedTranId.Should().Be(Id(3));
        result.Rows.Should().BeNull();
        result.State.Should().Be(state);
    }

    [Theory]
    [InlineData("S")]
    [InlineData("s")]
    public async Task Enter_SelectWithS_WhenCotrn01cEnabled_NavigatesWithSelectedId(string flag)
    {
        // FR-S07-15: XCTL COTRN01C with CDEMO-CT00-TRN-SELECTED
        var registry = new MenuRouteRegistryOptions
        {
            Main = [new MenuRouteOption { Id = "07", Name = "Transaction View", ProgramKey = "COTRN01C", Enabled = true, Route = "/transactions/view" }]
        };
        var repository = new InMemoryTransactionRepository(TransactionFixtures.Sequence(25));

        var result = await new TransactionListService(repository, registry)
            .ProcessAsync(Enter(search: "junk-ignored", selectionFlag: flag, selectedTranId: Id(7)));

        result.Outcome.Should().Be(TransactionListOutcome.Navigate);
        result.Target.Should().Be(new MenuNavigationTarget("07", "Transaction View", "COTRN01C", "/transactions/view"));
        result.SelectedTranId.Should().Be(Id(7));
        result.Message.Should().BeEmpty();
        repository.BrowseCalls.Should().Be(0, "XCTL happens before the search-id edit");
    }

    [Fact]
    public async Task Enter_SelectWithOtherCharacter_ReportsInvalidSelectionAndStillPages()
    {
        // FR-S07-16: message set, then the search/paging path still runs
        var result = await Service(TransactionFixtures.Sequence(25)).ProcessAsync(Enter(selectionFlag: "X", selectedTranId: Id(3)));

        result.Outcome.Should().Be(TransactionListOutcome.Redisplay);
        result.Message.Should().Be("Invalid selection. Valid value is S");
        result.Severity.Should().Be(MenuMessageSeverity.Error);
        result.Rows.Should().HaveCount(10);
        result.State.PageNumber.Should().Be(1);
    }

    [Fact]
    public async Task Enter_InvalidSelectionThenNonNumericSearch_NumericErrorWins()
    {
        // FR-S07-16 + FR-S07-05: validation order — numeric edit overwrites WS-MESSAGE
        var result = await Service(TransactionFixtures.Sequence(25)).ProcessAsync(Enter(search: "ABC", selectionFlag: "X", selectedTranId: Id(3)));

        result.Message.Should().Be("Tran ID must be Numeric ...");
        result.Rows.Should().BeNull();
    }

    [Fact]
    public async Task Enter_InvalidSelectionOnLastPage_BottomMessageOverwrites()
    {
        // FR-S07-16: paging message replaces the selection message
        var result = await Service(TransactionFixtures.Sequence(5)).ProcessAsync(Enter(selectionFlag: "X", selectedTranId: Id(3)));

        result.Message.Should().Be("You have reached the bottom of the page...");
    }

    [Fact]
    public async Task Enter_SelectionOnRowWithoutId_IsIgnored()
    {
        // FR-S07-17
        var result = await Service(TransactionFixtures.Sequence(25)).ProcessAsync(Enter(selectionFlag: "X", selectedTranId: "   "));

        result.Outcome.Should().Be(TransactionListOutcome.Redisplay);
        result.Message.Should().BeEmpty();
        result.Rows.Should().HaveCount(10);
    }

    [Fact]
    public async Task Enter_WhenStoreFails_ReportsLookupError()
    {
        // FR-S07-20
        var state = new TransactionListState(Id(1), Id(10), 1, true);

        var result = await new TransactionListService(new FailingTransactionRepository(), LoadApiRegistry()).ProcessAsync(Enter(state: state));

        result.Outcome.Should().Be(TransactionListOutcome.StoreError);
        result.Message.Should().Be("Unable to lookup transaction...");
        result.Rows.Should().BeNull();
        result.State.Should().Be(state with { PageNumber = 0 }, "MOVE 0 TO CDEMO-CT00-PAGE-NUM precedes the browse");
    }

    [Fact]
    public async Task Pf7_WhenStoreFails_ReportsLookupError()
    {
        // FR-S07-20 on the backward path
        var result = await new TransactionListService(new FailingTransactionRepository(), LoadApiRegistry())
            .ProcessAsync(Pf7(new TransactionListState(Id(11), Id(20), 2, true)));

        result.Outcome.Should().Be(TransactionListOutcome.StoreError);
        result.Message.Should().Be("Unable to lookup transaction...");
    }

    [Fact]
    public async Task Rows_FollowTranIdByteOrderNotNumericOrder()
    {
        // FR-S07-21
        var records = new[]
        {
            TransactionFixtures.Make("0000000000000100"),
            TransactionFixtures.Make("0000000000000009"),
            TransactionFixtures.Make("000000000000001A"),
            TransactionFixtures.Make("0000000000000010")
        };

        var result = await Service(records).ProcessAsync(Enter());

        Ids(result).Take(4).Should().Equal("0000000000000009", "0000000000000010", "000000000000001A", "0000000000000100");
    }

    [Theory]
    [InlineData("0000000000000001", true)]
    [InlineData("1234567890123456", true)]
    [InlineData("123456789012345", false)]
    [InlineData("12345678901234567", false)]
    [InlineData("12345678901234 6", false)]
    [InlineData("１２３４５６７８９０１２３４５６", false)]
    public void IsNumericField_MatchesCobolNumericClassOnX16(string value, bool expected)
    {
        // FR-S07-05
        TransactionListService.IsNumericField(value).Should().Be(expected);
    }
}
