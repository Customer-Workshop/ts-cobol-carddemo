using CardDemo.Application.Menu;

namespace CardDemo.Application.Transactions;

/// <summary>AID keys handled by COTRN00C MAIN-PARA (app/cbl/COTRN00C.cbl:119-134): ENTER, PF7, PF8.</summary>
public enum TransactionListAction
{
    Enter,
    PageBackward,
    PageForward
}

public enum TransactionListOutcome
{
    /// <summary>Screen redisplayed (rows replaced when <see cref="TransactionListResult.Rows"/> is non-null).</summary>
    Redisplay,
    /// <summary>Row selected but the COTRN01C route is still disabled in the registry (S07-B1).</summary>
    ComingSoon,
    NotInstalled,
    /// <summary>Row selected and the COTRN01C route is enabled: XCTL replacement (FR-S07-15).</summary>
    Navigate,
    /// <summary>File access failure — `Unable to lookup transaction...` (FR-S07-20).</summary>
    StoreError
}

/// <summary>Paging state CDEMO-CT00-INFO (COTRN00C.cbl:62-70), round-tripped by the client (S07-B5).</summary>
public sealed record TransactionListState(
    string FirstTranId,
    string LastTranId,
    int PageNumber,
    bool NextPageAvailable)
{
    public static TransactionListState Initial { get; } = new(string.Empty, string.Empty, 0, false);
}

public sealed record TransactionListRequest(
    TransactionListAction Action,
    string? SearchTranId,
    string? SelectionFlag,
    string? SelectedTranId,
    TransactionListState State);

/// <summary>One screen row TRNIDnn / TDATEnn / TDESCnn / TAMTnnn (COTRN00C.cbl:383-445); blank when unfilled.</summary>
public sealed record TransactionListRow(string TranId, string Date, string Description, string Amount)
{
    public static TransactionListRow Blank { get; } = new(string.Empty, string.Empty, string.Empty, string.Empty);

    public bool IsBlank => TranId.Length == 0;
}

public sealed record TransactionListResult(
    TransactionListOutcome Outcome,
    TransactionListState State,
    string Message = "",
    MenuMessageSeverity? Severity = null,
    IReadOnlyList<TransactionListRow>? Rows = null,
    bool ClearSearchInput = false,
    string? SelectedTranId = null,
    MenuNavigationTarget? Target = null);
