using System.Globalization;
using CardDemo.Application.Cards;
using CardDemo.Domain.Cards;
using CardDemo.Domain.Dates;
using CardDemo.Domain.Transactions;

namespace CardDemo.Application.Transactions;

/// <summary>
/// Port of COTRN02C (CICS CT02, app/cbl/COTRN02C.cbl): ENTER processing (key resolution, field edits in
/// source order, confirmation, next-id allocation, write) and PF5 copy-last. Every edit runs on the
/// blank-padded BMS field exactly as the COBOL class tests do, and the first failure ends processing.
/// </summary>
public class TransactionAddService(
    ICardXrefRepository cardXrefRepository,
    ITransactionRepository transactionRepository,
    DateValidationService dateValidationService)
{
    public const string ProgramName = "COTRN02C";
    public const string TransactionCode = "CT02";

    public const string KeyRequiredMessage = "Account or Card Number must be entered...";
    public const string AccountNotNumericMessage = "Account ID must be Numeric...";
    public const string AccountNotFoundMessage = "Account ID NOT found...";
    public const string AccountLookupErrorMessage = "Unable to lookup Acct in XREF AIX file...";
    public const string CardNotNumericMessage = "Card Number must be Numeric...";
    public const string CardNotFoundMessage = "Card Number NOT found...";
    public const string CardLookupErrorMessage = "Unable to lookup Card # in XREF file...";
    public const string TypeCodeEmptyMessage = "Type CD can NOT be empty...";
    public const string CategoryCodeEmptyMessage = "Category CD can NOT be empty...";
    public const string SourceEmptyMessage = "Source can NOT be empty...";
    public const string DescriptionEmptyMessage = "Description can NOT be empty...";
    public const string AmountEmptyMessage = "Amount can NOT be empty...";
    public const string OriginalDateEmptyMessage = "Orig Date can NOT be empty...";
    public const string ProcessedDateEmptyMessage = "Proc Date can NOT be empty...";
    public const string MerchantIdEmptyMessage = "Merchant ID can NOT be empty...";
    public const string MerchantNameEmptyMessage = "Merchant Name can NOT be empty...";
    public const string MerchantCityEmptyMessage = "Merchant City can NOT be empty...";
    public const string MerchantZipEmptyMessage = "Merchant Zip can NOT be empty...";
    public const string TypeCodeNotNumericMessage = "Type CD must be Numeric...";
    public const string CategoryCodeNotNumericMessage = "Category CD must be Numeric...";
    public const string AmountFormatMessage = "Amount should be in format -99999999.99";
    public const string OriginalDateFormatMessage = "Orig Date should be in format YYYY-MM-DD";
    public const string ProcessedDateFormatMessage = "Proc Date should be in format YYYY-MM-DD";
    public const string OriginalDateInvalidMessage = "Orig Date - Not a valid date...";
    public const string ProcessedDateInvalidMessage = "Proc Date - Not a valid date...";
    public const string MerchantIdNotNumericMessage = "Merchant ID must be Numeric...";
    public const string ConfirmRequiredMessage = "Confirm to add this transaction...";
    public const string ConfirmInvalidMessage = "Invalid value. Valid values are (Y/N)...";
    public const string TransactionLookupErrorMessage = "Unable to lookup Transaction...";
    public const string DuplicateTransactionIdMessage = "Tran ID already exist...";
    public const string AddErrorMessage = "Unable to Add Transaction...";
    public const string AddedMessagePrefix = "Transaction added successfully.  Your Tran ID is ";

    /// <summary>ENTER key (COTRN02C.cbl:164-188).</summary>
    public async Task<TransactionAddResult> AddAsync(TransactionAddRequest request, CancellationToken cancellationToken = default)
    {
        var screen = Screen.From(request);
        return await ValidateKeyFieldsAsync(screen, cancellationToken)
            ?? await ProcessEnterAfterKeyAsync(screen, cancellationToken);
    }

    /// <summary>PF5 copy-last (COTRN02C.cbl:469-495): key edits, copy the highest-id record, then ENTER processing.</summary>
    public async Task<TransactionAddResult> CopyLastAsync(TransactionAddRequest request, CancellationToken cancellationToken = default)
    {
        var screen = Screen.From(request);
        var keyError = await ValidateKeyFieldsAsync(screen, cancellationToken);
        if (keyError is not null)
        {
            return keyError;
        }

        Transaction? last;
        try
        {
            last = await transactionRepository.GetLastAsync(cancellationToken);
        }
        catch (Exception)
        {
            return screen.Error(TransactionAddOutcome.LookupError, TransactionLookupErrorMessage, TransactionAddField.AccountId);
        }

        screen.Set(TransactionAddField.TypeCode, last?.TypeCode ?? string.Empty);
        screen.Set(TransactionAddField.CategoryCode, last?.CategoryCode ?? string.Empty);
        screen.Set(TransactionAddField.Source, last?.Source ?? string.Empty);
        screen.Set(TransactionAddField.Amount, last is null ? string.Empty : FormatAmount(last.Amount));
        screen.Set(TransactionAddField.Description, last?.Description ?? string.Empty);
        screen.Set(TransactionAddField.OriginalDate, FormatTimestamp(last?.OriginalTimestamp));
        screen.Set(TransactionAddField.ProcessedDate, FormatTimestamp(last?.ProcessedTimestamp));
        screen.Set(TransactionAddField.MerchantId, last?.MerchantId ?? string.Empty);
        screen.Set(TransactionAddField.MerchantName, last?.MerchantName ?? string.Empty);
        screen.Set(TransactionAddField.MerchantCity, last?.MerchantCity ?? string.Empty);
        screen.Set(TransactionAddField.MerchantZip, last?.MerchantZip ?? string.Empty);

        return await ProcessEnterAfterKeyAsync(screen, cancellationToken);
    }

    /// <summary>NUMVAL-C → PIC +99999999.99 (COTRN02C.cbl:59, 383-386).</summary>
    public static string FormatAmount(decimal amount) =>
        (amount < 0 ? "-" : "+") + Math.Abs(amount).ToString("00000000.00", CultureInfo.InvariantCulture);

    private async Task<TransactionAddResult> ProcessEnterAfterKeyAsync(Screen screen, CancellationToken cancellationToken)
    {
        var dataError = ValidateDataFields(screen);
        if (dataError is not null)
        {
            return dataError;
        }

        var confirmation = screen.Get(TransactionAddField.Confirmation);
        switch (confirmation)
        {
            case "Y":
            case "y":
                return await WriteTransactionAsync(screen, cancellationToken);
            case "N":
            case "n":
            case " ":
                return screen.Error(TransactionAddOutcome.ConfirmationRequired, ConfirmRequiredMessage, TransactionAddField.Confirmation);
            default:
                return screen.Error(TransactionAddOutcome.InvalidConfirmation, ConfirmInvalidMessage, TransactionAddField.Confirmation);
        }
    }

    /// <summary>VALIDATE-INPUT-KEY-FIELDS (COTRN02C.cbl:190-230).</summary>
    private async Task<TransactionAddResult?> ValidateKeyFieldsAsync(Screen screen, CancellationToken cancellationToken)
    {
        if (!screen.IsBlank(TransactionAddField.AccountId))
        {
            if (!screen.IsNumeric(TransactionAddField.AccountId))
            {
                return screen.Error(TransactionAddOutcome.ValidationError, AccountNotNumericMessage, TransactionAddField.AccountId);
            }

            CardXref? xref;
            try
            {
                xref = await cardXrefRepository.GetFirstByAccountIdAsync(screen.Get(TransactionAddField.AccountId), cancellationToken);
            }
            catch (Exception)
            {
                return screen.Error(TransactionAddOutcome.LookupError, AccountLookupErrorMessage, TransactionAddField.AccountId);
            }
            if (xref is null)
            {
                return screen.Error(TransactionAddOutcome.KeyNotFound, AccountNotFoundMessage, TransactionAddField.AccountId);
            }
            screen.Set(TransactionAddField.CardNumber, xref.CardNumber);
            return null;
        }

        if (!screen.IsBlank(TransactionAddField.CardNumber))
        {
            if (!screen.IsNumeric(TransactionAddField.CardNumber))
            {
                return screen.Error(TransactionAddOutcome.ValidationError, CardNotNumericMessage, TransactionAddField.CardNumber);
            }

            CardXref? xref;
            try
            {
                xref = await cardXrefRepository.GetByCardNumberAsync(screen.Get(TransactionAddField.CardNumber), cancellationToken);
            }
            catch (Exception)
            {
                return screen.Error(TransactionAddOutcome.LookupError, CardLookupErrorMessage, TransactionAddField.CardNumber);
            }
            if (xref is null)
            {
                return screen.Error(TransactionAddOutcome.KeyNotFound, CardNotFoundMessage, TransactionAddField.CardNumber);
            }
            screen.Set(TransactionAddField.AccountId, xref.AccountId);
            return null;
        }

        return screen.Error(TransactionAddOutcome.ValidationError, KeyRequiredMessage, TransactionAddField.AccountId);
    }

    private static readonly (TransactionAddField Field, string Message)[] MandatoryFields =
    [
        (TransactionAddField.TypeCode, TypeCodeEmptyMessage),
        (TransactionAddField.CategoryCode, CategoryCodeEmptyMessage),
        (TransactionAddField.Source, SourceEmptyMessage),
        (TransactionAddField.Description, DescriptionEmptyMessage),
        (TransactionAddField.Amount, AmountEmptyMessage),
        (TransactionAddField.OriginalDate, OriginalDateEmptyMessage),
        (TransactionAddField.ProcessedDate, ProcessedDateEmptyMessage),
        (TransactionAddField.MerchantId, MerchantIdEmptyMessage),
        (TransactionAddField.MerchantName, MerchantNameEmptyMessage),
        (TransactionAddField.MerchantCity, MerchantCityEmptyMessage),
        (TransactionAddField.MerchantZip, MerchantZipEmptyMessage)
    ];

    /// <summary>VALIDATE-INPUT-DATA-FIELDS (COTRN02C.cbl:232-437).</summary>
    private TransactionAddResult? ValidateDataFields(Screen screen)
    {
        foreach (var (field, message) in MandatoryFields)
        {
            if (screen.IsBlank(field))
            {
                return screen.Error(TransactionAddOutcome.ValidationError, message, field);
            }
        }

        if (!screen.IsNumeric(TransactionAddField.TypeCode))
        {
            return screen.Error(TransactionAddOutcome.ValidationError, TypeCodeNotNumericMessage, TransactionAddField.TypeCode);
        }
        if (!screen.IsNumeric(TransactionAddField.CategoryCode))
        {
            return screen.Error(TransactionAddOutcome.ValidationError, CategoryCodeNotNumericMessage, TransactionAddField.CategoryCode);
        }

        var amount = screen.Get(TransactionAddField.Amount);
        if (!IsAmountLayout(amount))
        {
            return screen.Error(TransactionAddOutcome.ValidationError, AmountFormatMessage, TransactionAddField.Amount);
        }
        if (!IsDateLayout(screen.Get(TransactionAddField.OriginalDate)))
        {
            return screen.Error(TransactionAddOutcome.ValidationError, OriginalDateFormatMessage, TransactionAddField.OriginalDate);
        }
        if (!IsDateLayout(screen.Get(TransactionAddField.ProcessedDate)))
        {
            return screen.Error(TransactionAddOutcome.ValidationError, ProcessedDateFormatMessage, TransactionAddField.ProcessedDate);
        }

        screen.Set(TransactionAddField.Amount, FormatAmount(ParseAmount(amount)));

        if (!IsAcceptedDate(screen.Get(TransactionAddField.OriginalDate)))
        {
            return screen.Error(TransactionAddOutcome.ValidationError, OriginalDateInvalidMessage, TransactionAddField.OriginalDate);
        }
        if (!IsAcceptedDate(screen.Get(TransactionAddField.ProcessedDate)))
        {
            return screen.Error(TransactionAddOutcome.ValidationError, ProcessedDateInvalidMessage, TransactionAddField.ProcessedDate);
        }

        if (!screen.IsNumeric(TransactionAddField.MerchantId))
        {
            return screen.Error(TransactionAddOutcome.ValidationError, MerchantIdNotNumericMessage, TransactionAddField.MerchantId);
        }

        return null;
    }

    /// <summary>ADD-TRANSACTION + WRITE-TRANSACT-FILE (COTRN02C.cbl:444-466, 709-749).</summary>
    private async Task<TransactionAddResult> WriteTransactionAsync(Screen screen, CancellationToken cancellationToken)
    {
        Transaction? last;
        try
        {
            last = await transactionRepository.GetLastAsync(cancellationToken);
        }
        catch (Exception)
        {
            return screen.Error(TransactionAddOutcome.LookupError, TransactionLookupErrorMessage, TransactionAddField.AccountId);
        }

        var transactionId = NextTransactionId(last?.TransactionId);
        var transaction = BuildRecord(screen, transactionId);

        try
        {
            await transactionRepository.AddAsync(transaction, cancellationToken);
        }
        catch (DuplicateTransactionIdException)
        {
            return screen.Error(TransactionAddOutcome.DuplicateTransactionId, DuplicateTransactionIdMessage, TransactionAddField.AccountId);
        }
        catch (Exception)
        {
            return screen.Error(TransactionAddOutcome.WriteError, AddErrorMessage, TransactionAddField.AccountId);
        }

        return new TransactionAddResult(
            TransactionAddOutcome.Added,
            Screen.From(TransactionAddRequest.Empty).ToRequest(),
            AddedMessagePrefix + transactionId + ".",
            TransactionAddMessageSeverity.Success,
            TransactionAddField.AccountId,
            transactionId);
    }

    /// <summary>WS-TRAN-ID-N = highest TRAN-ID + 1, PIC 9(16); an empty file yields 0000000000000001 (COTRN02C.cbl:448-451, 688-689).</summary>
    public static string NextTransactionId(string? highestTransactionId)
    {
        var current = string.IsNullOrWhiteSpace(highestTransactionId) ? 0m : decimal.Parse(highestTransactionId, CultureInfo.InvariantCulture);
        return (current + 1).ToString("0000000000000000", CultureInfo.InvariantCulture);
    }

    /// <summary>Screen → TRAN-RECORD (COTRN02C.cbl:452-465); dates become midnight timestamps (S09-B6).</summary>
    private static Transaction BuildRecord(Screen screen, string transactionId) => new()
    {
        TransactionId = transactionId,
        TypeCode = screen.Get(TransactionAddField.TypeCode),
        CategoryCode = screen.Get(TransactionAddField.CategoryCode),
        Source = screen.Trimmed(TransactionAddField.Source),
        Description = screen.Trimmed(TransactionAddField.Description),
        Amount = ParseAmount(screen.Get(TransactionAddField.Amount)),
        CardNumber = screen.Get(TransactionAddField.CardNumber),
        MerchantId = screen.Get(TransactionAddField.MerchantId),
        MerchantName = screen.Trimmed(TransactionAddField.MerchantName),
        MerchantCity = screen.Trimmed(TransactionAddField.MerchantCity),
        MerchantZip = screen.Trimmed(TransactionAddField.MerchantZip),
        OriginalTimestamp = ToTimestamp(screen.Get(TransactionAddField.OriginalDate)),
        ProcessedTimestamp = ToTimestamp(screen.Get(TransactionAddField.ProcessedDate))
    };

    private static DateTime ToTimestamp(string date) =>
        DateOnly.ParseExact(date, "yyyy-MM-dd", CultureInfo.InvariantCulture).ToDateTime(TimeOnly.MinValue);

    private static string FormatTimestamp(DateTime? timestamp) =>
        timestamp is null ? string.Empty : timestamp.Value.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);

    /// <summary>TRNAMTI(1:1) sign, (2:8) digits, (10:1) '.', (11:2) digits over the whole 12-byte field (COTRN02C.cbl:339-351).</summary>
    private static bool IsAmountLayout(string amount) =>
        amount.Length == 12
        && amount[0] is ('-' or '+')
        && amount.AsSpan(1, 8).ToArray().All(char.IsAsciiDigit)
        && amount[9] == '.'
        && amount.AsSpan(10, 2).ToArray().All(char.IsAsciiDigit);

    /// <summary>(1:4) digits, (5:1) '-', (6:2) digits, (8:1) '-', (9:2) digits (COTRN02C.cbl:353-381).</summary>
    private static bool IsDateLayout(string date) =>
        date.Length == 10
        && date.AsSpan(0, 4).ToArray().All(char.IsAsciiDigit)
        && date[4] == '-'
        && date.AsSpan(5, 2).ToArray().All(char.IsAsciiDigit)
        && date[7] == '-'
        && date.AsSpan(8, 2).ToArray().All(char.IsAsciiDigit);

    /// <summary>CSUTLDTC severity '0000', or message 2513 which the caller lets through (COTRN02C.cbl:397-400, 417-420).</summary>
    private bool IsAcceptedDate(string date)
    {
        var result = dateValidationService.Validate(date, DateValidationService.DefaultMask);
        if (result.IsValid)
        {
            return true;
        }
        return result.MessageNumber == DateValidationResult.UnsupportedRangeMessage && result.Date is not null;
    }

    private static decimal ParseAmount(string amount) =>
        decimal.Parse(amount, NumberStyles.AllowLeadingSign | NumberStyles.AllowDecimalPoint, CultureInfo.InvariantCulture);

    /// <summary>Blank-padded BMS input area (COTRN2AI) — the COBOL class tests see the padding.</summary>
    private sealed class Screen
    {
        private static readonly TransactionAddField[] Fields = Enum.GetValues<TransactionAddField>();

        private readonly string[] _values = new string[Fields.Length];

        public static Screen From(TransactionAddRequest request)
        {
            var screen = new Screen();
            foreach (var field in Fields)
            {
                screen.Set(field, request.ValueOf(field) ?? string.Empty);
            }
            return screen;
        }

        public string Get(TransactionAddField field) => _values[(int)field];

        public string Trimmed(TransactionAddField field) => Get(field).TrimEnd();

        public void Set(TransactionAddField field, string value)
        {
            var width = TransactionAddRequest.WidthOf(field);
            var text = value.Replace('\0', ' ');
            _values[(int)field] = text.Length >= width ? text[..width] : text.PadRight(width);
        }

        public bool IsBlank(TransactionAddField field) => string.IsNullOrWhiteSpace(Get(field));

        public bool IsNumeric(TransactionAddField field) => Get(field).All(char.IsAsciiDigit);

        public TransactionAddResult Error(TransactionAddOutcome outcome, string message, TransactionAddField cursorField) =>
            new(outcome, ToRequest(), message, TransactionAddMessageSeverity.Error, cursorField);

        public TransactionAddRequest ToRequest() => new(
            Trimmed(TransactionAddField.AccountId),
            Trimmed(TransactionAddField.CardNumber),
            Trimmed(TransactionAddField.TypeCode),
            Trimmed(TransactionAddField.CategoryCode),
            Trimmed(TransactionAddField.Source),
            Trimmed(TransactionAddField.Description),
            Trimmed(TransactionAddField.Amount),
            Trimmed(TransactionAddField.OriginalDate),
            Trimmed(TransactionAddField.ProcessedDate),
            Trimmed(TransactionAddField.MerchantId),
            Trimmed(TransactionAddField.MerchantName),
            Trimmed(TransactionAddField.MerchantCity),
            Trimmed(TransactionAddField.MerchantZip),
            Trimmed(TransactionAddField.Confirmation));
    }
}
