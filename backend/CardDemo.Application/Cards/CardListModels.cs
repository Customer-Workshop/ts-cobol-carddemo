namespace CardDemo.Application.Cards;

/// <summary>AID keys COCRDLIC distinguishes (app/cbl/COCRDLIC.cbl:370-380); every other key is remapped to ENTER.</summary>
public enum CardListAid
{
    Enter,
    Pf3,
    Pf7,
    Pf8
}

public enum CardListOutcome
{
    Display,
    Exit,
    Navigate,
    ComingSoon,
    NotInstalled
}

/// <summary>One of the 7 WS-SCREEN-ROWS carried in the paging COMMAREA (COCRDLIC.cbl:252-260).</summary>
public sealed record CardListRow(string AccountId, string CardNumber, string ActiveStatus);

/// <summary>
/// Port of WS-THIS-PROGCOMMAREA (COCRDLIC.cbl:229-260): the pseudo-conversational paging state
/// the client echoes back on every AID press. A null state means a fresh entry from the menu.
/// </summary>
public sealed record CardListPageState(
    int ScreenNumber,
    string FirstCardNumber,
    string LastCardNumber,
    bool NextPageExists,
    bool LastPageShown,
    IReadOnlyList<CardListRow?> Rows)
{
    public static CardListPageState Initial() => new(
        ScreenNumber: 1,
        FirstCardNumber: string.Empty,
        LastCardNumber: string.Empty,
        NextPageExists: false,
        LastPageShown: false,
        Rows: new CardListRow?[CardListService.PageSize]);
}

public sealed record CardListRequest(
    string? Aid,
    CardListPageState? State,
    string? AccountFilter,
    string? CardFilter,
    IReadOnlyList<string?>? Selections);

public sealed record CardListRowView(
    CardListRow? Card,
    string Selection,
    bool SelectionError,
    bool SelectionProtected);

/// <summary>Replacement for XCTL PROGRAM(CCARD-NEXT-PROG) with CDEMO-ACCT-ID / CDEMO-CARD-NUM (COCRDLIC.cbl:526-541).</summary>
public sealed record CardListNavigationTarget(string ProgramKey, string Route, string AccountId, string CardNumber);

public sealed record CardListResult(
    CardListOutcome Outcome,
    int ScreenNumber,
    string AccountFilter,
    string CardFilter,
    bool AccountFilterError,
    bool CardFilterError,
    string CursorField,
    IReadOnlyList<CardListRowView> Rows,
    string ErrorMessage,
    string InfoMessage,
    string? Message,
    string? Severity,
    CardListPageState State,
    CardListNavigationTarget? Target);
