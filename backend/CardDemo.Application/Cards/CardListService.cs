using CardDemo.Application.Menu;
using CardDemo.Domain.Cards;

namespace CardDemo.Application.Cards;

/// <summary>
/// Port of COCRDLIC (app/cbl/COCRDLIC.cbl, transaction CCLI): 7-row paged browse of CARDDAT with
/// exact account / card filters, PF7/PF8 paging, S/U single-row selection. One call = one AID press;
/// the pseudo-conversational paging COMMAREA travels as <see cref="CardListPageState"/>.
/// </summary>
public class CardListService(ICardRepository cards, MenuRouteRegistryOptions registry)
{
    public const int PageSize = 7;
    public const int AccountFilterLength = 11;
    public const int CardFilterLength = 16;

    public const string CardDetailProgram = "COCRDSLC";
    public const string CardUpdateProgram = "COCRDUPC";
    public const string MenuRoute = "/menu";

    public const string MsgAccountFilterInvalid = "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER";
    public const string MsgCardFilterInvalid = "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER";
    public const string MsgMoreThanOneAction = "PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE";
    public const string MsgInvalidActionCode = "INVALID ACTION CODE";
    public const string MsgNoRecordsFound = "NO RECORDS FOUND FOR THIS SEARCH CONDITION.";
    public const string MsgNoMoreRecords = "NO MORE RECORDS TO SHOW";
    public const string MsgNoMorePages = "NO MORE PAGES TO DISPLAY";
    public const string MsgNoPreviousPages = "NO PREVIOUS PAGES TO DISPLAY";
    public const string MsgInformRecActions = "TYPE S FOR DETAIL, U TO UPDATE ANY RECORD";
    public const string MsgReadPrevExhausted = "File Error: READ     on CARDDAT   returned RESP 000000020 ,RESP2 000000090";

    public const string CursorAccount = "account";
    public const string CursorCard = "card";

    private enum FilterState
    {
        Blank,
        Valid,
        Invalid
    }

    public static CardListAid MapAid(string? aid) => aid?.Trim().ToUpperInvariant() switch
    {
        "PF3" or "F3" => CardListAid.Pf3,
        "PF7" or "F7" => CardListAid.Pf7,
        "PF8" or "F8" => CardListAid.Pf8,
        _ => CardListAid.Enter
    };

    public async Task<CardListResult> ProcessAsync(CardListRequest request, CancellationToken cancellationToken = default)
    {
        var aid = MapAid(request.Aid);
        var fresh = request.State is null;
        var state = request.State ?? CardListPageState.Initial();

        var screen = state.ScreenNumber;
        var first = state.FirstCardNumber;
        var last = state.LastCardNumber;
        var nextPageExists = state.NextPageExists;
        var lastPageShown = state.LastPageShown;
        var rows = NormalizeRows(state.Rows);

        var errorMessage = string.Empty;
        var accountText = string.Empty;
        var cardText = string.Empty;
        var accountState = FilterState.Blank;
        var cardState = FilterState.Blank;
        var selections = new string[PageSize];
        Array.Fill(selections, string.Empty);
        var selectionErrors = new bool[PageSize];
        var inputError = false;
        var selectedIndex = -1;

        if (!fresh)
        {
            // 2000-RECEIVE-MAP / 2200-EDIT-INPUTS (COCRDLIC.cbl:951-1121)
            accountText = (request.AccountFilter ?? string.Empty).TrimEnd();
            cardText = (request.CardFilter ?? string.Empty).TrimEnd();
            for (var i = 0; i < PageSize; i++)
            {
                selections[i] = request.Selections is { } s && i < s.Count ? (s[i] ?? string.Empty).Trim() : string.Empty;
            }

            accountState = EditFilter(accountText, AccountFilterLength);
            if (accountState == FilterState.Invalid)
            {
                inputError = true;
                errorMessage = MsgAccountFilterInvalid;
            }

            cardState = EditFilter(cardText, CardFilterLength);
            if (cardState == FilterState.Invalid)
            {
                inputError = true;
                if (errorMessage.Length == 0)
                {
                    errorMessage = MsgCardFilterInvalid;
                }
            }

            if (!inputError)
            {
                var actionCount = selections.Count(IsSelectOk);
                var moreThanOne = actionCount > 1;
                if (moreThanOne)
                {
                    inputError = true;
                    errorMessage = MsgMoreThanOneAction;
                }

                for (var i = 0; i < PageSize; i++)
                {
                    if (IsSelectOk(selections[i]))
                    {
                        selectedIndex = i;
                        selectionErrors[i] = moreThanOne;
                    }
                    else if (selections[i].Length > 0)
                    {
                        inputError = true;
                        selectionErrors[i] = true;
                        if (errorMessage.Length == 0)
                        {
                            errorMessage = MsgInvalidActionCode;
                        }
                    }
                }
            }
        }

        if (aid == CardListAid.Pf3 && !fresh)
        {
            // COCRDLIC.cbl:384-406 — XCTL COMEN01C
            return new CardListResult(
                CardListOutcome.Exit, screen, string.Empty, string.Empty, false, false, CursorAccount,
                EmptyRowViews(), string.Empty, string.Empty, null, null, state,
                new CardListNavigationTarget("COMEN01C", MenuRoute, string.Empty, string.Empty));
        }

        if (aid != CardListAid.Pf8)
        {
            lastPageShown = false;
        }

        var accountFilter = accountState == FilterState.Valid ? accountText : null;
        var cardFilter = cardState == FilterState.Valid ? cardText : null;
        var filterError = accountState == FilterState.Invalid || cardState == FilterState.Invalid;

        // Dispatch EVALUATE (COCRDLIC.cbl:418-583); first matching WHEN wins.
        if (inputError)
        {
            if (!filterError)
            {
                // Selection errors: RID left uninitialized (spaces) — browse restarts at the beginning of the file (S04-B2).
                (rows, first, last, nextPageExists, screen, errorMessage) =
                    await ReadForwardAsync(string.Empty, accountFilter, cardFilter, screen, first, last, errorMessage, cancellationToken);
            }
        }
        else if (aid == CardListAid.Pf7 && screen == 1)
        {
            (rows, first, last, nextPageExists, screen, errorMessage) =
                await ReadForwardAsync(first, accountFilter, cardFilter, screen, first, last, errorMessage, cancellationToken);
        }
        else if (fresh)
        {
            screen = 1;
            (rows, first, last, nextPageExists, screen, errorMessage) =
                await ReadForwardAsync(string.Empty, accountFilter, cardFilter, screen, first, last, errorMessage, cancellationToken);
        }
        else if (aid == CardListAid.Pf8 && nextPageExists)
        {
            screen = (screen + 1) % 10;
            (rows, first, last, nextPageExists, screen, errorMessage) =
                await ReadForwardAsync(last, accountFilter, cardFilter, screen, first, last, errorMessage, cancellationToken);
        }
        else if (aid == CardListAid.Pf7)
        {
            screen = Math.Abs(screen - 1);
            (rows, first, last, nextPageExists, errorMessage) =
                await ReadBackwardAsync(first, accountFilter, cardFilter, errorMessage, cancellationToken);
        }
        else if (aid == CardListAid.Enter && selectedIndex >= 0 && (selections[selectedIndex] == "S" || selections[selectedIndex] == "U"))
        {
            var programKey = selections[selectedIndex] == "S" ? CardDetailProgram : CardUpdateProgram;
            var selectedRow = rows[selectedIndex];
            var newState = new CardListPageState(screen, first, last, nextPageExists, lastPageShown, rows);
            return ResolveHandOff(programKey, selectedRow, newState, accountText, cardText, selections);
        }
        else
        {
            (rows, first, last, nextPageExists, screen, errorMessage) =
                await ReadForwardAsync(first, accountFilter, cardFilter, screen, first, last, errorMessage, cancellationToken);
        }

        // 1400-SETUP-MESSAGE (COCRDLIC.cbl:895-931)
        var infoMessage = string.Empty;
        if (filterError)
        {
            // keep the filter message, no info
        }
        else if (aid == CardListAid.Pf7 && screen == 1)
        {
            errorMessage = MsgNoPreviousPages;
        }
        else if (aid == CardListAid.Pf8 && !nextPageExists && lastPageShown)
        {
            errorMessage = MsgNoMorePages;
        }
        else if (aid == CardListAid.Pf8 && !nextPageExists)
        {
            infoMessage = MsgInformRecActions;
            lastPageShown = true;
        }
        else
        {
            infoMessage = MsgInformRecActions;
        }

        if (errorMessage == MsgNoRecordsFound)
        {
            infoMessage = string.Empty;
        }

        var resultState = new CardListPageState(screen, first, last, nextPageExists, lastPageShown, rows);
        var rowViews = BuildRowViews(rows, selections, selectionErrors, protectAll: filterError);
        return new CardListResult(
            CardListOutcome.Display,
            screen,
            accountState == FilterState.Blank ? string.Empty : accountText,
            cardState == FilterState.Blank ? string.Empty : cardText,
            accountState == FilterState.Invalid,
            cardState == FilterState.Invalid,
            Cursor(accountState, cardState, selectionErrors),
            rowViews,
            errorMessage,
            infoMessage,
            null,
            null,
            resultState,
            null);
    }

    private CardListResult ResolveHandOff(
        string programKey,
        CardListRow? selectedRow,
        CardListPageState state,
        string accountText,
        string cardText,
        string[] selections)
    {
        var accountId = selectedRow?.AccountId ?? string.Empty;
        var cardNumber = selectedRow?.CardNumber ?? string.Empty;
        var option = registry.Main.Concat(registry.Admin).FirstOrDefault(o => o.ProgramKey == programKey);
        var rowViews = BuildRowViews(state.Rows, selections, new bool[PageSize], protectAll: false);

        if (option is { Enabled: true })
        {
            return new CardListResult(
                CardListOutcome.Navigate, state.ScreenNumber, accountText, cardText, false, false, CursorAccount,
                rowViews, string.Empty, string.Empty, null, null, state,
                new CardListNavigationTarget(programKey, option.Route, accountId, cardNumber));
        }

        var notInstalled = option is null || option.NotInstalledWhenDisabled;
        var name = option?.Name ?? programKey;
        return new CardListResult(
            notInstalled ? CardListOutcome.NotInstalled : CardListOutcome.ComingSoon,
            state.ScreenNumber, accountText, cardText, false, false, CursorAccount,
            rowViews, string.Empty, string.Empty,
            notInstalled ? MenuService.NotInstalledMessage(name) : MenuService.ComingSoonMessage(name),
            notInstalled ? "error" : "info",
            state,
            new CardListNavigationTarget(programKey, option?.Route ?? string.Empty, accountId, cardNumber));
    }

    /// <summary>9000-READ-FORWARD (COCRDLIC.cbl:1123-1263).</summary>
    private async Task<(CardListRow?[] Rows, string First, string Last, bool NextPageExists, int Screen, string ErrorMessage)> ReadForwardAsync(
        string startKey,
        string? accountFilter,
        string? cardFilter,
        int screen,
        string first,
        string last,
        string errorMessage,
        CancellationToken cancellationToken)
    {
        var found = await cards.BrowseForwardAsync(startKey, PageSize, accountFilter, cardFilter, cancellationToken);
        var rows = new CardListRow?[PageSize];
        var nextPageExists = true;

        for (var i = 0; i < found.Count; i++)
        {
            rows[i] = ToRow(found[i]);
        }

        if (found.Count > 0)
        {
            first = found[0].CardNumber;
            if (screen == 0)
            {
                screen = 1;
            }
        }

        if (found.Count == PageSize)
        {
            last = found[^1].CardNumber;
            var lookAhead = await cards.ReadNextAsync(last, cancellationToken);
            if (lookAhead is not null)
            {
                last = lookAhead.CardNumber;
            }
            else
            {
                nextPageExists = false;
                if (errorMessage.Length == 0)
                {
                    errorMessage = MsgNoMoreRecords;
                }
            }
        }
        else
        {
            nextPageExists = false;
            if (found.Count > 0)
            {
                last = found[^1].CardNumber;
            }
            if (errorMessage.Length == 0)
            {
                errorMessage = MsgNoMoreRecords;
            }
            if (screen == 1 && found.Count == 0)
            {
                errorMessage = MsgNoRecordsFound;
            }
        }

        return (rows, first, last, nextPageExists, screen, errorMessage);
    }

    /// <summary>9100-READ-BACKWARDS (COCRDLIC.cbl:1264-1380).</summary>
    private async Task<(CardListRow?[] Rows, string First, string Last, bool NextPageExists, string ErrorMessage)> ReadBackwardAsync(
        string firstKey,
        string? accountFilter,
        string? cardFilter,
        string errorMessage,
        CancellationToken cancellationToken)
    {
        var last = firstKey;
        var found = await cards.BrowseBackwardAsync(firstKey, PageSize, accountFilter, cardFilter, cancellationToken);
        var rows = new CardListRow?[PageSize];
        for (var i = 0; i < found.Count; i++)
        {
            rows[PageSize - 1 - i] = ToRow(found[i]);
        }

        var first = firstKey;
        if (found.Count == PageSize)
        {
            first = found[^1].CardNumber;
        }
        else
        {
            errorMessage = MsgReadPrevExhausted;
        }

        return (rows, first, last, true, errorMessage);
    }

    private static FilterState EditFilter(string text, int length)
    {
        var trimmed = text.Trim();
        if (trimmed.Length == 0 || trimmed.All(c => c == '0'))
        {
            return FilterState.Blank;
        }
        return text.Length == length && text.All(char.IsAsciiDigit) ? FilterState.Valid : FilterState.Invalid;
    }

    private static bool IsSelectOk(string code) => code == "S" || code == "U";

    private static string Cursor(FilterState accountState, FilterState cardState, bool[] selectionErrors)
    {
        if (accountState == FilterState.Invalid)
        {
            return CursorAccount;
        }
        if (cardState == FilterState.Invalid)
        {
            return CursorCard;
        }
        for (var i = 1; i < PageSize; i++)
        {
            if (selectionErrors[i])
            {
                return $"select{i + 1}";
            }
        }
        return CursorAccount;
    }

    private static CardListRow ToRow(Card card) => new(card.AccountId, card.CardNumber, card.ActiveStatus);

    private static CardListRow?[] NormalizeRows(IReadOnlyList<CardListRow?>? rows)
    {
        var result = new CardListRow?[PageSize];
        if (rows is null)
        {
            return result;
        }
        for (var i = 0; i < PageSize && i < rows.Count; i++)
        {
            result[i] = rows[i];
        }
        return result;
    }

    private static IReadOnlyList<CardListRowView> BuildRowViews(
        IReadOnlyList<CardListRow?> rows,
        string[] selections,
        bool[] selectionErrors,
        bool protectAll)
    {
        var views = new CardListRowView[PageSize];
        for (var i = 0; i < PageSize; i++)
        {
            var card = i < rows.Count ? rows[i] : null;
            views[i] = new CardListRowView(card, selections[i], selectionErrors[i], protectAll || card is null);
        }
        return views;
    }

    private static IReadOnlyList<CardListRowView> EmptyRowViews() =>
        Enumerable.Range(0, PageSize).Select(_ => new CardListRowView(null, string.Empty, false, true)).ToList();
}
