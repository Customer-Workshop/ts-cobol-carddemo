namespace CardDemo.Application.AccountUpdate;

/// <summary>ACUP-CHANGE-ACTION values that the screen can land in (app/cbl/COACTUPC.cbl:414-434).</summary>
public enum AccountUpdateOutcome
{
    /// <summary>ACUP-DETAILS-NOT-FETCHED with an error: stay on the search prompt.</summary>
    SearchError,
    /// <summary>ACUP-SHOW-DETAILS: fetched originals displayed.</summary>
    Details,
    /// <summary>ACUP-CHANGES-NOT-OK: edits failed, values kept as typed.</summary>
    Invalid,
    /// <summary>ACUP-SHOW-DETAILS after 1205 found nothing changed.</summary>
    NoChanges,
    /// <summary>ACUP-CHANGES-OK-NOT-CONFIRMED: waiting for F5.</summary>
    Confirm,
    /// <summary>ACUP-CHANGES-OKAYED-AND-DONE.</summary>
    Committed,
    /// <summary>ACUP-CHANGES-OKAYED-LOCK-ERROR / ACUP-CHANGES-OKAYED-BUT-FAILED.</summary>
    Failed,
    /// <summary>DATA-WAS-CHANGED-BEFORE-UPDATE: back to ACUP-SHOW-DETAILS with the originals.</summary>
    ChangedByOther
}

public sealed record AccountUpdateLookupResult(
    AccountUpdateOutcome Outcome,
    string? InfoMessage,
    string? ErrorMessage,
    AccountUpdateFields? Fields);

public sealed record AccountUpdateValidateResult(
    AccountUpdateOutcome Outcome,
    string? InfoMessage,
    string? ErrorMessage,
    IReadOnlyList<string> InvalidFields);

public sealed record AccountUpdateSaveResult(
    AccountUpdateOutcome Outcome,
    string? InfoMessage,
    string? ErrorMessage,
    IReadOnlyList<string> InvalidFields);
