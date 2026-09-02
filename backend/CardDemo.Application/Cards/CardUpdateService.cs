using CardDemo.Domain.Cards;

namespace CardDemo.Application.Cards;

/// <summary>
/// Port of COCRDUPC (app/cbl/COCRDUPC.cbl, transaction CCUP): one call per pseudo-conversational
/// round trip. Reproduces the AID remapping (:413-424), the search edits (:721-799), the change
/// edits (:641-943), the decision paragraph (:948-1027), the CARDDAT read (:1343-1412) and the
/// READ UPDATE / compare / REWRITE sequence (:1420-1519) with the exact screen messages (:159-213).
/// </summary>
public class CardUpdateService(ICardRepository cardRepository)
{
    public const string InfoFoundCards = "Details of selected card shown above";
    public const string InfoPromptForSearchKeys = "Please enter Account and Card Number";
    public const string InfoPromptForChanges = "Update card details presented above.";
    public const string InfoPromptForConfirmation = "Changes validated.Press F5 to save";
    public const string InfoUpdateSuccess = "Changes committed to database";
    public const string InfoUpdateFailure = "Changes unsuccessful. Please try again";

    public const string MsgAccountNotProvided = "Account number not provided";
    public const string MsgAccountNotNumeric = "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER";
    public const string MsgCardNotProvided = "Card number not provided";
    public const string MsgCardNotNumeric = "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER";
    public const string MsgNoInputReceived = "No input received";
    public const string MsgNoChangesDetected = "No change detected with respect to values fetched.";
    public const string MsgNameNotProvided = "Card name not provided";
    public const string MsgNameMustBeAlpha = "Card name can only contain alphabets and spaces";
    public const string MsgStatusMustBeYesNo = "Card Active Status must be Y or N";
    public const string MsgInvalidMonth = "Card expiry month must be between 1 and 12";
    public const string MsgInvalidYear = "Invalid card expiry year";
    public const string MsgCardNotFound = "Did not find cards for this search condition";
    public const string MsgCouldNotLock = "Could not lock record for update";
    public const string MsgRecordChanged = "Record changed by some one else. Please review";
    public const string MsgUpdateFailed = "Update of record failed";

    /// <summary>WS-FILE-ERROR-MESSAGE (:146-158) truncated to WS-RETURN-MSG X(75); RESP 17 = DFHRESP(IOERR) analogue.</summary>
    public const string MsgReadFileError = "File Error: READ     on CARDDAT   returned RESP 000000017 ,RESP2 000000000";

    private const int AccountLength = 11;
    private const int CardLength = 16;
    private const int MonthLength = 2;
    private const int YearLength = 4;

    private static readonly CardUpdateField[] NoFields = [];
    private static readonly CardUpdateField[] SearchFields = [CardUpdateField.AccountId, CardUpdateField.CardNumber];

    public async Task<CardUpdateScreen> ProcessAsync(CardUpdateRequest request, CancellationToken cancellationToken = default)
    {
        var aid = NormaliseAid(request.Aid, request.State);

        if (request.State is CardUpdateState.ChangesDone or CardUpdateState.ChangesFailed)
        {
            return FreshScreen();
        }
        if (request.State == CardUpdateState.DetailsNotFetched || request.Original is null)
        {
            return await SearchAsync(request.AccountId, request.CardNumber, cancellationToken);
        }

        var original = request.Original;
        var input = NormaliseInput(request.Input);
        var edit = EditChanges(request.State, original, input);

        if (aid == CardUpdateAid.Pf12)
        {
            return await CancelAsync(original, edit.Message, cancellationToken);
        }

        return edit.State switch
        {
            CardUpdateState.ChangesOkNotConfirmed when aid == CardUpdateAid.Pf5 =>
                await SaveAsync(original, input, cancellationToken),
            CardUpdateState.ChangesOkNotConfirmed => Screen(
                CardUpdateState.ChangesOkNotConfirmed, InfoPromptForConfirmation, edit.Message, original,
                input, NoFields, CardUpdateField.AccountId),
            CardUpdateState.ChangesNotOk => Screen(
                CardUpdateState.ChangesNotOk, InfoPromptForChanges, edit.Message, original,
                input, edit.FieldsInError, edit.FieldsInError.Count > 0 ? edit.FieldsInError[0] : CardUpdateField.EmbossedName),
            _ => Screen(
                CardUpdateState.ShowDetails, InfoFoundCards, edit.Message, original,
                null, NoFields, CardUpdateField.EmbossedName)
        };
    }

    /// <summary>PF5 outside the confirmation state and PF12 before a fetch are processed as ENTER (:413-424).</summary>
    public static CardUpdateAid NormaliseAid(CardUpdateAid aid, CardUpdateState state) => aid switch
    {
        CardUpdateAid.Pf5 when state != CardUpdateState.ChangesOkNotConfirmed => CardUpdateAid.Enter,
        CardUpdateAid.Pf12 when state == CardUpdateState.DetailsNotFetched => CardUpdateAid.Enter,
        _ => aid
    };

    public static CardUpdateScreen FreshScreen() => new(
        CardUpdateState.DetailsNotFetched,
        InfoPromptForSearchKeys,
        null,
        string.Empty,
        string.Empty,
        string.Empty,
        string.Empty,
        string.Empty,
        string.Empty,
        string.Empty,
        null,
        NoFields,
        CardUpdateField.AccountId,
        SearchEditable: true,
        DetailsEditable: false,
        ConfirmKeysVisible: false);

    private async Task<CardUpdateScreen> SearchAsync(string? accountInput, string? cardInput, CancellationToken cancellationToken)
    {
        var account = NormaliseField(accountInput);
        var card = NormaliseField(cardInput);
        string? message = null;
        var fields = new List<CardUpdateField>();

        var accountBlank = IsBlank(account);
        if (accountBlank)
        {
            fields.Add(CardUpdateField.AccountId);
            message ??= MsgAccountNotProvided;
        }
        else if (!IsDigits(account, AccountLength))
        {
            fields.Add(CardUpdateField.AccountId);
            message ??= MsgAccountNotNumeric;
        }

        var cardBlank = IsBlank(card);
        if (cardBlank)
        {
            fields.Add(CardUpdateField.CardNumber);
            message ??= MsgCardNotProvided;
        }
        else if (!IsDigits(card, CardLength))
        {
            fields.Add(CardUpdateField.CardNumber);
            message ??= MsgCardNotNumeric;
        }

        if (accountBlank && cardBlank)
        {
            message = MsgNoInputReceived;
        }

        if (fields.Count > 0)
        {
            return SearchScreen(account, card, message, fields, accountBlank, cardBlank);
        }

        Card? record;
        try
        {
            record = await cardRepository.GetByCardNumberAsync(card, cancellationToken);
        }
        catch (Exception)
        {
            return SearchScreen(account, card, MsgReadFileError, SearchFields, false, false);
        }
        if (record is null)
        {
            return SearchScreen(account, card, MsgCardNotFound, SearchFields, false, false);
        }

        var original = ToDetails(record, account);
        return Screen(CardUpdateState.ShowDetails, InfoFoundCards, null, original, null, NoFields, CardUpdateField.EmbossedName);
    }

    private async Task<CardUpdateScreen> CancelAsync(CardUpdateDetails original, string? message, CancellationToken cancellationToken)
    {
        Card? record;
        try
        {
            record = await cardRepository.GetByCardNumberAsync(original.CardNumber, cancellationToken);
        }
        catch (Exception)
        {
            record = null;
            message ??= MsgReadFileError;
        }
        var refreshed = record is null
            ? new CardUpdateDetails(original.AccountId, original.CardNumber, string.Empty, string.Empty, string.Empty, string.Empty, string.Empty)
            : ToDetails(record, original.AccountId);
        if (record is null)
        {
            message ??= MsgCardNotFound;
        }
        return Screen(CardUpdateState.ShowDetails, InfoFoundCards, message, refreshed, null, NoFields, CardUpdateField.EmbossedName);
    }

    private async Task<CardUpdateScreen> SaveAsync(CardUpdateDetails original, CardUpdateInput input, CancellationToken cancellationToken)
    {
        CardUpdateDetails? current = null;
        CardRewriteOutcome outcome;
        try
        {
            outcome = await cardRepository.RewriteAsync(original.CardNumber, record =>
            {
                current = ToDetails(record, original.AccountId);
                if (!SameImage(current, original))
                {
                    return false;
                }
                record.EmbossedName = input.EmbossedName!;
                record.ActiveStatus = input.ActiveStatus!;
                record.ExpirationDate = new DateOnly(
                    int.Parse(input.ExpiryYear!),
                    int.Parse(input.ExpiryMonth!),
                    int.Parse(original.ExpiryDay));
                return true;
            }, cancellationToken);
        }
        catch (Exception) when (current is null)
        {
            return Screen(CardUpdateState.ChangesFailed, InfoUpdateFailure, MsgCouldNotLock, original, input, NoFields, CardUpdateField.AccountId);
        }
        catch (Exception)
        {
            return Screen(CardUpdateState.ChangesFailed, InfoUpdateFailure, MsgUpdateFailed, original, input, NoFields, CardUpdateField.AccountId);
        }

        return outcome switch
        {
            CardRewriteOutcome.NotFound => Screen(
                CardUpdateState.ChangesFailed, InfoUpdateFailure, MsgCouldNotLock, original, input, NoFields, CardUpdateField.AccountId),
            CardRewriteOutcome.Skipped => Screen(
                CardUpdateState.ShowDetails, InfoFoundCards, MsgRecordChanged, current!, null, NoFields, CardUpdateField.EmbossedName),
            _ => Screen(
                CardUpdateState.ChangesDone, InfoUpdateSuccess, null, original, input, NoFields, CardUpdateField.AccountId)
        };
    }

    private sealed record EditResult(CardUpdateState State, string? Message, IReadOnlyList<CardUpdateField> FieldsInError);

    /// <summary>1200-EDIT-MAP-INPUTS for a fetched record (:665-712) plus 1230..1260.</summary>
    private static EditResult EditChanges(CardUpdateState state, CardUpdateDetails original, CardUpdateInput input)
    {
        var noChanges = string.Equals(
            NewCardData(input, original.ExpiryDay),
            OldCardData(original),
            StringComparison.OrdinalIgnoreCase);
        if (noChanges)
        {
            return new EditResult(state, MsgNoChangesDetected, NoFields);
        }

        string? message = null;
        var fields = new List<CardUpdateField>();

        var name = input.EmbossedName ?? string.Empty;
        if (IsBlank(name))
        {
            fields.Add(CardUpdateField.EmbossedName);
            message ??= MsgNameNotProvided;
        }
        else if (!name.All(c => c == ' ' || char.IsAsciiLetter(c)))
        {
            fields.Add(CardUpdateField.EmbossedName);
            message ??= MsgNameMustBeAlpha;
        }

        var status = input.ActiveStatus ?? string.Empty;
        if (status is not ("Y" or "N"))
        {
            fields.Add(CardUpdateField.ActiveStatus);
            message ??= MsgStatusMustBeYesNo;
        }

        var month = input.ExpiryMonth ?? string.Empty;
        if (IsBlank(month) || !IsDigits(month, MonthLength) || int.Parse(month) is < 1 or > 12)
        {
            fields.Add(CardUpdateField.ExpiryMonth);
            message ??= MsgInvalidMonth;
        }

        var year = input.ExpiryYear ?? string.Empty;
        if (IsBlank(year) || !IsDigits(year, YearLength) || int.Parse(year) is < 1950 or > 2099)
        {
            fields.Add(CardUpdateField.ExpiryYear);
            message ??= MsgInvalidYear;
        }

        return fields.Count > 0
            ? new EditResult(CardUpdateState.ChangesNotOk, message, fields)
            : new EditResult(CardUpdateState.ChangesOkNotConfirmed, null, NoFields);
    }

    private static CardUpdateScreen SearchScreen(
        string account,
        string card,
        string? message,
        IReadOnlyList<CardUpdateField> fields,
        bool accountBlank,
        bool cardBlank) => new(
        CardUpdateState.DetailsNotFetched,
        InfoPromptForSearchKeys,
        message,
        accountBlank ? "*" : account,
        cardBlank ? "*" : card,
        string.Empty,
        string.Empty,
        string.Empty,
        string.Empty,
        string.Empty,
        null,
        fields,
        fields.Count > 0 ? fields[0] : CardUpdateField.AccountId,
        SearchEditable: true,
        DetailsEditable: false,
        ConfirmKeysVisible: false);

    /// <summary>3200-SETUP-SCREEN-VARS + 3300-SETUP-SCREEN-ATTRS: OLD image in S, NEW image once changes were made.</summary>
    private static CardUpdateScreen Screen(
        CardUpdateState state,
        string info,
        string? message,
        CardUpdateDetails original,
        CardUpdateInput? input,
        IReadOnlyList<CardUpdateField> fields,
        CardUpdateField cursor)
    {
        var showNew = input is not null && state != CardUpdateState.ShowDetails;
        var name = showNew ? input!.EmbossedName ?? string.Empty : original.EmbossedName;
        var status = showNew ? input!.ActiveStatus ?? string.Empty : original.ActiveStatus;
        var month = showNew ? input!.ExpiryMonth ?? string.Empty : original.ExpiryMonth;
        var year = showNew ? input!.ExpiryYear ?? string.Empty : original.ExpiryYear;
        var blankStar = state == CardUpdateState.ChangesNotOk;

        return new CardUpdateScreen(
            state,
            info,
            message,
            original.AccountId,
            original.CardNumber,
            blankStar && fields.Contains(CardUpdateField.EmbossedName) && IsBlank(name) ? "*" : name,
            blankStar && fields.Contains(CardUpdateField.ActiveStatus) && IsBlank(status) ? "*" : status,
            blankStar && fields.Contains(CardUpdateField.ExpiryMonth) && IsBlank(month) ? "*" : month,
            blankStar && fields.Contains(CardUpdateField.ExpiryYear) && IsBlank(year) ? "*" : year,
            original.ExpiryDay,
            original,
            fields,
            cursor,
            SearchEditable: state == CardUpdateState.ChangesFailed,
            DetailsEditable: state is CardUpdateState.ShowDetails or CardUpdateState.ChangesNotOk,
            ConfirmKeysVisible: state == CardUpdateState.ChangesOkNotConfirmed);
    }

    /// <summary>9000-READ-DATA: OLD image with the embossed name upper-cased (:1354-1358); account as typed (:1346).</summary>
    private static CardUpdateDetails ToDetails(Card card, string accountId) => new(
        accountId,
        card.CardNumber,
        card.EmbossedName.ToUpperInvariant(),
        card.ExpirationDate?.ToString("yyyy") ?? string.Empty,
        card.ExpirationDate?.ToString("MM") ?? string.Empty,
        card.ExpirationDate?.ToString("dd") ?? string.Empty,
        card.ActiveStatus);

    /// <summary>9300-CHECK-CHANGE-IN-REC over the displayed fields (CVV excluded, deviation D2).</summary>
    private static bool SameImage(CardUpdateDetails current, CardUpdateDetails original) =>
        string.Equals(current.EmbossedName, original.EmbossedName, StringComparison.OrdinalIgnoreCase)
        && current.ExpiryYear == original.ExpiryYear
        && current.ExpiryMonth == original.ExpiryMonth
        && current.ExpiryDay == original.ExpiryDay
        && current.ActiveStatus == original.ActiveStatus;

    private static string OldCardData(CardUpdateDetails d) =>
        Fixed(d.EmbossedName, 50) + Fixed(d.ExpiryYear, 4) + Fixed(d.ExpiryMonth, 2) + Fixed(d.ExpiryDay, 2) + Fixed(d.ActiveStatus, 1);

    private static string NewCardData(CardUpdateInput i, string day) =>
        Fixed(i.EmbossedName, 50) + Fixed(i.ExpiryYear, 4) + Fixed(i.ExpiryMonth, 2) + Fixed(day, 2) + Fixed(i.ActiveStatus, 1);

    private static string Fixed(string? value, int length) => (value ?? string.Empty).PadRight(length);

    /// <summary>1100-RECEIVE-MAP: '*' and blanks are low-values (:589-635); month/year are BMS JUSTIFY=RIGHT zero-filled.</summary>
    private static CardUpdateInput NormaliseInput(CardUpdateInput? input) => new(
        NormaliseField(input?.EmbossedName),
        NormaliseField(input?.ActiveStatus),
        ZeroFill(NormaliseField(input?.ExpiryMonth), MonthLength),
        ZeroFill(NormaliseField(input?.ExpiryYear), YearLength));

    private static string NormaliseField(string? value)
    {
        var trimmed = (value ?? string.Empty).Trim();
        return trimmed == "*" ? string.Empty : trimmed;
    }

    private static string ZeroFill(string value, int length) =>
        value.Length == 0 || value.Length >= length ? value : value.PadLeft(length, '0');

    /// <summary>LOW-VALUES, SPACES or ZEROS (:729-731, :770-772, :811-813).</summary>
    private static bool IsBlank(string value) => value.Length == 0 || value.All(c => c == '0');

    private static bool IsDigits(string value, int length) => value.Length == length && value.All(char.IsAsciiDigit);
}
