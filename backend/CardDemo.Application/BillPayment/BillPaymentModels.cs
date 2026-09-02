namespace CardDemo.Application.BillPayment;

/// <summary>Screen input of COBIL0A (ACTIDINI X(11), CONFIRMI X(1)) as typed.</summary>
public sealed record BillPaymentRequest(string? AccountId, string? Confirm);

public enum BillPaymentOutcome
{
    AccountIdRequired,
    InvalidConfirmation,
    Declined,
    AccountNotFound,
    AccountLookupError,
    NothingToPay,
    ConfirmationRequired,
    CardNotFound,
    CardLookupError,
    TransactionLookupError,
    DuplicateTransaction,
    TransactionWriteError,
    AccountUpdateError,
    PaymentSuccessful
}

/// <summary>Field receiving the cursor (MOVE -1 TO ...L OF COBIL0AI).</summary>
public enum BillPaymentCursorField
{
    AccountId,
    Confirm
}

public enum BillPaymentMessageSeverity
{
    Error,
    Success
}

/// <summary>
/// One ENTER round trip of COBIL00C PROCESS-ENTER-KEY.
/// <paramref name="CurrentBalance"/> is the CURBAL edit (+9999999999.99) when the screen shows it,
/// null when the previously displayed value is left untouched; <paramref name="ClearScreen"/> mirrors
/// INITIALIZE-ALL-FIELDS.
/// </summary>
public sealed record BillPaymentResult(
    BillPaymentOutcome Outcome,
    string Message,
    BillPaymentMessageSeverity? Severity,
    BillPaymentCursorField CursorField,
    string? CurrentBalance = null,
    string? TransactionId = null,
    bool ClearScreen = false);

/// <summary>RESP protocol of WRITE TRANSACT + REWRITE ACCTDAT (COBIL00C.cbl:510-547, :375-403).</summary>
public enum BillPaymentPostOutcome
{
    Posted,
    DuplicateTransaction,
    TransactionWriteError,
    AccountNotFound,
    AccountUpdateError
}
