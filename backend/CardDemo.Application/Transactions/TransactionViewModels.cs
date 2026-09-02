namespace CardDemo.Application.Transactions;

public enum TransactionViewOutcome
{
    Found,
    MissingTransactionId,
    NotFound,
    StoreError
}

/// <summary>
/// The 13 output fields of BMS map COTRN1A (app/bms/COTRN01.bms), already edited and
/// truncated exactly as COTRN01C's MOVEs do (app/cbl/COTRN01C.cbl:176-190), so the
/// screen renders them verbatim.
/// </summary>
public sealed record TransactionViewDetail(
    string TransactionId,
    string CardNumber,
    string TypeCode,
    string CategoryCode,
    string Source,
    string Description,
    string Amount,
    string OriginalDate,
    string ProcessedDate,
    string MerchantId,
    string MerchantName,
    string MerchantCity,
    string MerchantZip);

/// <summary>
/// Outcome of the COTRN01C PROCESS-ENTER-KEY / READ-TRANSACT-FILE sequence: either the
/// populated screen fields or the exact legacy message (FR-S08-04, 06, 07, 08).
/// </summary>
public sealed record TransactionViewResult(
    TransactionViewOutcome Outcome,
    string? Message = null,
    TransactionViewDetail? Detail = null)
{
    public bool IsFound => Outcome == TransactionViewOutcome.Found;
}
