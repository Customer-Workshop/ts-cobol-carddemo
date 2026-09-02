namespace CardDemo.Application.Cards;

/// <summary>AID keys COCRDUPC reacts to (app/cbl/COCRDUPC.cbl:413-424); PF3 is handled by the shell.</summary>
public enum CardUpdateAid
{
    Enter,
    Pf5,
    Pf12
}

/// <summary>CCUP-CHANGE-ACTION (COCRDUPC.cbl:288-296); L and F share the CCUP-CHANGES-FAILED behaviour.</summary>
public enum CardUpdateState
{
    DetailsNotFetched,
    ShowDetails,
    ChangesNotOk,
    ChangesOkNotConfirmed,
    ChangesDone,
    ChangesFailed
}

public enum CardUpdateField
{
    AccountId,
    CardNumber,
    EmbossedName,
    ActiveStatus,
    ExpiryMonth,
    ExpiryYear
}

/// <summary>CCUP-OLD-DETAILS minus CVV: the image fetched from CARDDAT, round-tripped by the client like the COMMAREA.</summary>
public record CardUpdateDetails(
    string AccountId,
    string CardNumber,
    string EmbossedName,
    string ExpiryYear,
    string ExpiryMonth,
    string ExpiryDay,
    string ActiveStatus);

/// <summary>The editable CCRDUPA fields as typed (CCUP-NEW-* without the carried day).</summary>
public record CardUpdateInput(
    string? EmbossedName,
    string? ActiveStatus,
    string? ExpiryMonth,
    string? ExpiryYear);

/// <summary>One pseudo-conversational round trip: AID + the state the screen was sent with.</summary>
public record CardUpdateRequest(
    CardUpdateAid Aid,
    CardUpdateState State,
    string? AccountId,
    string? CardNumber,
    CardUpdateDetails? Original,
    CardUpdateInput? Input);

/// <summary>The next CCRDUPA screen: values, messages, attributes and the state to send back.</summary>
public record CardUpdateScreen(
    CardUpdateState State,
    string InfoMessage,
    string? ErrorMessage,
    string AccountId,
    string CardNumber,
    string EmbossedName,
    string ActiveStatus,
    string ExpiryMonth,
    string ExpiryYear,
    string ExpiryDay,
    CardUpdateDetails? Original,
    IReadOnlyList<CardUpdateField> FieldsInError,
    CardUpdateField CursorField,
    bool SearchEditable,
    bool DetailsEditable,
    bool ConfirmKeysVisible);
