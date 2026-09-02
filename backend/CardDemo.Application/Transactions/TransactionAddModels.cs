namespace CardDemo.Application.Transactions;

/// <summary>The 14 input fields of map COTRN2A (app/cpy-bms/COTRN02.CPY:60-138), in screen order.</summary>
public enum TransactionAddField
{
    AccountId,
    CardNumber,
    TypeCode,
    CategoryCode,
    Source,
    Description,
    Amount,
    OriginalDate,
    ProcessedDate,
    MerchantId,
    MerchantName,
    MerchantCity,
    MerchantZip,
    Confirmation
}

/// <summary>Raw screen contents as keyed; null and shorter values are blank-padded to the BMS width by the service.</summary>
public record TransactionAddRequest(
    string? AccountId,
    string? CardNumber,
    string? TypeCode,
    string? CategoryCode,
    string? Source,
    string? Description,
    string? Amount,
    string? OriginalDate,
    string? ProcessedDate,
    string? MerchantId,
    string? MerchantName,
    string? MerchantCity,
    string? MerchantZip,
    string? Confirmation)
{
    public static readonly TransactionAddRequest Empty = new(null, null, null, null, null, null, null, null, null, null, null, null, null, null);

    /// <summary>BMS field lengths (app/cpy-bms/COTRN02.CPY).</summary>
    public static int WidthOf(TransactionAddField field) => field switch
    {
        TransactionAddField.AccountId => 11,
        TransactionAddField.CardNumber => 16,
        TransactionAddField.TypeCode => 2,
        TransactionAddField.CategoryCode => 4,
        TransactionAddField.Source => 10,
        TransactionAddField.Description => 60,
        TransactionAddField.Amount => 12,
        TransactionAddField.OriginalDate => 10,
        TransactionAddField.ProcessedDate => 10,
        TransactionAddField.MerchantId => 9,
        TransactionAddField.MerchantName => 30,
        TransactionAddField.MerchantCity => 25,
        TransactionAddField.MerchantZip => 10,
        TransactionAddField.Confirmation => 1,
        _ => throw new ArgumentOutOfRangeException(nameof(field))
    };

    public string? ValueOf(TransactionAddField field) => field switch
    {
        TransactionAddField.AccountId => AccountId,
        TransactionAddField.CardNumber => CardNumber,
        TransactionAddField.TypeCode => TypeCode,
        TransactionAddField.CategoryCode => CategoryCode,
        TransactionAddField.Source => Source,
        TransactionAddField.Description => Description,
        TransactionAddField.Amount => Amount,
        TransactionAddField.OriginalDate => OriginalDate,
        TransactionAddField.ProcessedDate => ProcessedDate,
        TransactionAddField.MerchantId => MerchantId,
        TransactionAddField.MerchantName => MerchantName,
        TransactionAddField.MerchantCity => MerchantCity,
        TransactionAddField.MerchantZip => MerchantZip,
        TransactionAddField.Confirmation => Confirmation,
        _ => throw new ArgumentOutOfRangeException(nameof(field))
    };

    /// <summary>Fields whose value exceeds the BMS width — impossible from a 3270, rejected at the API edge.</summary>
    public IReadOnlyList<TransactionAddField> OverLengthFields() =>
        Enum.GetValues<TransactionAddField>().Where(f => (ValueOf(f)?.Length ?? 0) > WidthOf(f)).ToList();
}

public enum TransactionAddOutcome
{
    Added,
    ConfirmationRequired,
    InvalidConfirmation,
    ValidationError,
    KeyNotFound,
    LookupError,
    DuplicateTransactionId,
    WriteError
}

public enum TransactionAddMessageSeverity
{
    Error,
    Success
}

/// <summary>
/// The redisplayed COTRN2A screen: echoed (normalised) field values, the ERRMSG line, its colour,
/// and the field the COBOL positions the cursor on (MOVE -1 TO ...L).
/// </summary>
public record TransactionAddResult(
    TransactionAddOutcome Outcome,
    TransactionAddRequest Screen,
    string Message,
    TransactionAddMessageSeverity Severity,
    TransactionAddField CursorField,
    string? TransactionId = null)
{
    public bool IsAdded => Outcome == TransactionAddOutcome.Added;
}
