namespace CardDemo.Application.Cards;

/// <summary>
/// Screen input of CCRDSLA: ACCTSIDI X(11), CARDSIDI X(16). <see cref="FromCardList"/> is the
/// COCRDLIC hand-off (COCRDSLC.cbl:339-348): keys come from the COMMAREA and edits are skipped.
/// </summary>
public record CardViewRequest(string? AccountId, string? CardNumber, bool FromCardList = false);

public enum CardViewOutcome
{
    Found,
    InputError,
    NotFound,
    StoreError
}

/// <summary>WS-EDIT-ACCT-FLAG / WS-EDIT-CARD-FLAG 88-levels (COCRDSLC.cbl:55-62).</summary>
public enum CardViewFilterState
{
    Valid,
    Blank,
    NotOk
}

public enum CardViewCursorField
{
    Account,
    Card
}

public record CardViewDetails(string EmbossedName, string ExpiryMonth, string ExpiryYear, string ActiveStatus);

/// <summary>
/// Everything 1200-SETUP-SCREEN-VARS / 1300-SETUP-SCREEN-ATTRS put on the map
/// (COCRDSLC.cbl:457-557): echoed keys, messages, field flags, cursor and card details.
/// </summary>
public record CardViewResult(
    CardViewOutcome Outcome,
    string ErrorMessage,
    string InfoMessage,
    string AccountId,
    string CardNumber,
    CardViewFilterState AccountFilter,
    CardViewFilterState CardFilter,
    CardViewCursorField Cursor,
    CardViewDetails? Card)
{
    public bool IsFound => Outcome == CardViewOutcome.Found;
}
