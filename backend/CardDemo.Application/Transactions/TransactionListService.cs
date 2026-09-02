using System.Globalization;
using CardDemo.Application.Common;
using CardDemo.Application.Menu;
using CardDemo.Domain.Transactions;

namespace CardDemo.Application.Transactions;

/// <summary>
/// Port of COTRN00C (app/cbl/COTRN00C.cbl): PROCESS-ENTER-KEY (:146-229), PROCESS-PF7-KEY (:234-252),
/// PROCESS-PF8-KEY (:257-274), PROCESS-PAGE-FORWARD (:279-328), PROCESS-PAGE-BACKWARD (:333-376) over the
/// shared TRANSACT browse repository. Ten rows per page, TRAN-ID key order, exact legacy messages (FR-S07-02..17, 20, 21).
/// </summary>
public class TransactionListService(ITransactionRepository transactionRepository, MenuRouteRegistryOptions registry)
{
    public const int PageSize = 10;
    public const int TranIdLength = 16;
    public const int DescriptionLength = 26;

    public const string SelectionTargetProgram = "COTRN01C";
    public const string SelectionTargetName = "Transaction View";

    public const string InvalidSelectionMessage = "Invalid selection. Valid value is S";
    public const string TranIdNotNumericMessage = "Tran ID must be Numeric ...";
    public const string AlreadyAtTopMessage = "You are already at the top of the page...";
    public const string AlreadyAtBottomMessage = "You are already at the bottom of the page...";
    public const string AtTopMessage = "You are at the top of the page...";
    public const string ReachedBottomMessage = "You have reached the bottom of the page...";
    public const string ReachedTopMessage = "You have reached the top of the page...";
    public const string LookupErrorMessage = "Unable to lookup transaction...";

    /// <summary>MOVE HIGH-VALUES TO TRAN-ID (:260): no key can sort at or after it, so STARTBR yields NOTFND.</summary>
    private const string HighValues = "\uFFFF";

    public Task<TransactionListResult> ProcessAsync(TransactionListRequest request, CancellationToken cancellationToken = default) =>
        request.Action switch
        {
            TransactionListAction.PageBackward => ProcessPf7Async(request.State, cancellationToken),
            TransactionListAction.PageForward => ProcessPf8Async(request.State, cancellationToken),
            _ => ProcessEnterAsync(request, cancellationToken)
        };

    private async Task<TransactionListResult> ProcessEnterAsync(TransactionListRequest request, CancellationToken cancellationToken)
    {
        var message = string.Empty;

        var selectionFlag = (request.SelectionFlag ?? string.Empty).Trim();
        var selectedTranId = (request.SelectedTranId ?? string.Empty).Trim();
        if (selectionFlag.Length > 0 && selectedTranId.Length > 0)
        {
            if (selectionFlag is "S" or "s")
            {
                return ResolveSelection(selectedTranId, request.State);
            }
            message = InvalidSelectionMessage;
        }

        string startKey;
        if (string.IsNullOrWhiteSpace(request.SearchTranId))
        {
            startKey = string.Empty;
        }
        else
        {
            var search = request.SearchTranId.Length > TranIdLength ? request.SearchTranId[..TranIdLength] : request.SearchTranId;
            if (!IsNumericField(search))
            {
                return new TransactionListResult(TransactionListOutcome.Redisplay, request.State, TranIdNotNumericMessage, MenuMessageSeverity.Error);
            }
            startKey = search;
        }

        var state = request.State with { PageNumber = 0 };
        return await PageForwardAsync(startKey, state, skipFirstRecord: false, message, cancellationToken);
    }

    private async Task<TransactionListResult> ProcessPf7Async(TransactionListState state, CancellationToken cancellationToken)
    {
        state = state with { NextPageAvailable = true };
        if (state.PageNumber > 1)
        {
            return await PageBackwardAsync(state.FirstTranId, state, cancellationToken);
        }
        return new TransactionListResult(TransactionListOutcome.Redisplay, state, AlreadyAtTopMessage, MenuMessageSeverity.Error);
    }

    private async Task<TransactionListResult> ProcessPf8Async(TransactionListState state, CancellationToken cancellationToken)
    {
        if (!state.NextPageAvailable)
        {
            return new TransactionListResult(TransactionListOutcome.Redisplay, state, AlreadyAtBottomMessage, MenuMessageSeverity.Error);
        }
        var startKey = string.IsNullOrWhiteSpace(state.LastTranId) ? HighValues : state.LastTranId;
        return await PageForwardAsync(startKey, state, skipFirstRecord: true, string.Empty, cancellationToken);
    }

    private async Task<TransactionListResult> PageForwardAsync(
        string startKey,
        TransactionListState state,
        bool skipFirstRecord,
        string message,
        CancellationToken cancellationToken)
    {
        KeyedPage<Transaction> page;
        try
        {
            page = startKey == HighValues
                ? new KeyedPage<Transaction>([], false)
                : await transactionRepository.BrowseAsync(startKey, skipFirstRecord ? PageSize + 1 : PageSize, cancellationToken);
        }
        catch (Exception)
        {
            return LookupError(state);
        }

        if (page.Items.Count == 0)
        {
            return new TransactionListResult(
                TransactionListOutcome.Redisplay,
                state with { NextPageAvailable = false },
                AtTopMessage,
                MenuMessageSeverity.Error,
                ClearSearchInput: true);
        }

        var records = skipFirstRecord ? page.Items.Skip(1).ToList() : page.Items.ToList();
        var rows = new TransactionListRow[PageSize];
        for (var i = 0; i < PageSize; i++)
        {
            rows[i] = i < records.Count ? ToRow(records[i]) : TransactionListRow.Blank;
        }

        var firstTranId = records.Count > 0 ? records[0].TransactionId : state.FirstTranId;
        var lastTranId = records.Count == PageSize ? records[PageSize - 1].TransactionId : state.LastTranId;
        var pageNumber = state.PageNumber;
        bool nextPageAvailable;

        if (records.Count == PageSize)
        {
            pageNumber++;
            nextPageAvailable = page.HasMore;
            if (!nextPageAvailable)
            {
                message = ReachedBottomMessage;
            }
        }
        else
        {
            nextPageAvailable = false;
            if (records.Count > 0)
            {
                pageNumber++;
            }
            message = ReachedBottomMessage;
        }

        return new TransactionListResult(
            TransactionListOutcome.Redisplay,
            new TransactionListState(firstTranId, lastTranId, pageNumber, nextPageAvailable),
            message,
            message.Length > 0 ? MenuMessageSeverity.Error : null,
            rows,
            ClearSearchInput: true);
    }

    private async Task<TransactionListResult> PageBackwardAsync(string firstTranId, TransactionListState state, CancellationToken cancellationToken)
    {
        IReadOnlyList<Transaction> previous;
        try
        {
            var positioned = await transactionRepository.BrowseAsync(firstTranId ?? string.Empty, 1, cancellationToken);
            if (positioned.Items.Count == 0)
            {
                return new TransactionListResult(TransactionListOutcome.Redisplay, state, AtTopMessage, MenuMessageSeverity.Error);
            }
            previous = await transactionRepository.BrowseBackwardAsync(positioned.Items[0].TransactionId, PageSize + 1, cancellationToken);
        }
        catch (Exception)
        {
            return LookupError(state);
        }

        var morePrecede = previous.Count > PageSize;
        var records = morePrecede ? previous.Skip(previous.Count - PageSize).ToList() : previous.ToList();

        var rows = new TransactionListRow[PageSize];
        var offset = PageSize - records.Count;
        for (var i = 0; i < PageSize; i++)
        {
            rows[i] = i >= offset ? ToRow(records[i - offset]) : TransactionListRow.Blank;
        }

        var newFirst = records.Count == PageSize ? records[0].TransactionId : state.FirstTranId;
        var newLast = records.Count > 0 ? records[^1].TransactionId : state.LastTranId;
        var pageNumber = state.PageNumber;
        var message = string.Empty;

        if (records.Count == PageSize)
        {
            if (morePrecede)
            {
                pageNumber = pageNumber > 1 ? pageNumber - 1 : 1;
            }
            else
            {
                message = ReachedTopMessage;
                pageNumber = 1;
            }
        }
        else
        {
            message = ReachedTopMessage;
        }

        return new TransactionListResult(
            TransactionListOutcome.Redisplay,
            new TransactionListState(newFirst, newLast, pageNumber, state.NextPageAvailable),
            message,
            message.Length > 0 ? MenuMessageSeverity.Error : null,
            rows);
    }

    private TransactionListResult ResolveSelection(string selectedTranId, TransactionListState state)
    {
        var option = registry.Main.Concat(registry.Admin)
            .FirstOrDefault(o => string.Equals(o.ProgramKey, SelectionTargetProgram, StringComparison.OrdinalIgnoreCase));
        var name = string.IsNullOrWhiteSpace(option?.Name) ? SelectionTargetName : option!.Name;

        if (option is null || !option.Enabled || string.IsNullOrWhiteSpace(option.Route))
        {
            return option?.NotInstalledWhenDisabled == true
                ? new TransactionListResult(TransactionListOutcome.NotInstalled, state, MenuService.NotInstalledMessage(name), MenuMessageSeverity.Error, SelectedTranId: selectedTranId)
                : new TransactionListResult(TransactionListOutcome.ComingSoon, state, MenuService.ComingSoonMessage(name), MenuMessageSeverity.Info, SelectedTranId: selectedTranId);
        }

        return new TransactionListResult(
            TransactionListOutcome.Navigate,
            state,
            SelectedTranId: selectedTranId,
            Target: new MenuNavigationTarget(option.Id, option.Name, option.ProgramKey, option.Route));
    }

    private static TransactionListResult LookupError(TransactionListState state) =>
        new(TransactionListOutcome.StoreError, state, LookupErrorMessage, MenuMessageSeverity.Error);

    /// <summary>COBOL `IS NUMERIC` over the X(16) map field: every position a digit (BMS space-pads shorter entries).</summary>
    public static bool IsNumericField(string value) =>
        value.Length == TranIdLength && value.All(char.IsAsciiDigit);

    /// <summary>POPULATE-TRAN-DATA (:383-388, :392-396): mm/dd/yy from TRAN-ORIG-TS, TRAN-DESC(1:26), TRAN-AMT as +99999999.99.</summary>
    public static TransactionListRow ToRow(Transaction transaction) => new(
        transaction.TransactionId,
        FormatDate(transaction.OriginalTimestamp),
        TruncateDescription(transaction.Description),
        FormatAmount(transaction.Amount));

    public static string FormatDate(DateTime? timestamp) =>
        timestamp?.ToString("MM/dd/yy", CultureInfo.InvariantCulture) ?? string.Empty;

    public static string TruncateDescription(string description) =>
        (description.Length > DescriptionLength ? description[..DescriptionLength] : description).TrimEnd();

    /// <summary>PIC +99999999.99 (:56): explicit sign, 8 integer digits (high-order truncation), 2 decimals.</summary>
    public static string FormatAmount(decimal amount)
    {
        var sign = amount < 0 ? '-' : '+';
        var cents = (long)decimal.Truncate(Math.Abs(amount) * 100m) % 10_000_000_000L;
        return string.Create(CultureInfo.InvariantCulture, $"{sign}{cents / 100:D8}.{cents % 100:D2}");
    }
}
