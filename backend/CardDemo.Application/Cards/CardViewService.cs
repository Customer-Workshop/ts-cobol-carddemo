using CardDemo.Domain.Cards;

namespace CardDemo.Application.Cards;

/// <summary>
/// Port of COCRDSLC 2200-EDIT-MAP-INPUTS / 9100-GETCARD-BYACCTCARD / 1200-1300 screen setup
/// (app/cbl/COCRDSLC.cbl): account then card edits with first-message-wins, the both-blank
/// override, the keyed CARDDAT read protocol (found / not-found / store error) and the
/// resulting field flags and cursor (FR-S05-02..13).
/// </summary>
public class CardViewService(ICardRepository cardRepository)
{
    public const string PromptForInputMessage = "Please enter Account and Card Number";
    public const string DisplayingDetailsMessage = "   Displaying requested details";
    public const string AccountNotProvidedMessage = "Account number not provided";
    public const string CardNotProvidedMessage = "Card number not provided";
    public const string NoInputMessage = "No input received";
    public const string AccountNotNumericMessage = "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER";
    public const string CardNotNumericMessage = "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER";
    public const string CardNotFoundMessage = "Did not find cards for this search condition";

    public const int AccountIdLength = 11;
    public const int CardNumberLength = 16;

    private const string BlankMarker = "*";
    private const int ReturnMessageLength = 75;

    public async Task<CardViewResult> ViewAsync(CardViewRequest request, CancellationToken cancellationToken = default)
    {
        if (request.FromCardList)
        {
            var accountKey = NumericMove(request.AccountId, AccountIdLength);
            var cardKey = NumericMove(request.CardNumber, CardNumberLength);
            return await ReadAsync(
                IsAllZeros(accountKey) ? string.Empty : accountKey,
                IsAllZeros(cardKey) ? string.Empty : cardKey,
                cardKey,
                cancellationToken);
        }

        string? message = null;
        var (accountFilter, accountId) = Edit(request.AccountId, AccountIdLength, AccountNotProvidedMessage, AccountNotNumericMessage, ref message);
        var (cardFilter, cardNumber) = Edit(request.CardNumber, CardNumberLength, CardNotProvidedMessage, CardNotNumericMessage, ref message);
        if (accountFilter == CardViewFilterState.Blank && cardFilter == CardViewFilterState.Blank)
        {
            message = NoInputMessage;
        }

        if (message is not null)
        {
            return new CardViewResult(
                CardViewOutcome.InputError,
                message,
                PromptForInputMessage,
                accountId,
                cardNumber,
                accountFilter,
                cardFilter,
                CursorFor(accountFilter, cardFilter),
                Card: null);
        }

        return await ReadAsync(accountId, cardNumber, cardNumber, cancellationToken);
    }

    /// <summary>WS-FILE-ERROR-MESSAGE frame (COCRDSLC.cbl:102-121) as moved into WS-RETURN-MSG X(75).</summary>
    public static string FileErrorMessage(long resp, long resp2)
    {
        var frame = "File Error: "
            + "READ".PadRight(8)
            + " on "
            + "CARDDAT".PadRight(9)
            + " returned RESP "
            + FormatCode(resp).PadRight(10)
            + ",RESP2 "
            + FormatCode(resp2).PadRight(10)
            + new string(' ', 5);
        return frame[..ReturnMessageLength].TrimEnd();
    }

    public static string FileErrorMessage(Exception exception) => FileErrorMessage((uint)exception.HResult, 0);

    private async Task<CardViewResult> ReadAsync(string accountId, string cardNumber, string cardKey, CancellationToken cancellationToken)
    {
        Card? card;
        try
        {
            card = await cardRepository.GetByCardNumberAsync(cardKey, cancellationToken);
        }
        catch (Exception exception)
        {
            return new CardViewResult(
                CardViewOutcome.StoreError,
                FileErrorMessage(exception),
                PromptForInputMessage,
                accountId,
                cardNumber,
                CardViewFilterState.NotOk,
                CardViewFilterState.Valid,
                CardViewCursorField.Account,
                Card: null);
        }

        if (card is null)
        {
            return new CardViewResult(
                CardViewOutcome.NotFound,
                CardNotFoundMessage,
                PromptForInputMessage,
                accountId,
                cardNumber,
                CardViewFilterState.NotOk,
                CardViewFilterState.NotOk,
                CardViewCursorField.Account,
                Card: null);
        }

        return new CardViewResult(
            CardViewOutcome.Found,
            string.Empty,
            DisplayingDetailsMessage,
            accountId,
            cardNumber,
            CardViewFilterState.Valid,
            CardViewFilterState.Valid,
            CardViewCursorField.Account,
            new CardViewDetails(
                card.EmbossedName,
                card.ExpirationDate?.ToString("MM") ?? string.Empty,
                card.ExpirationDate?.ToString("yyyy") ?? string.Empty,
                card.ActiveStatus));
    }

    private static (CardViewFilterState Filter, string Echo) Edit(
        string? raw,
        int length,
        string notProvidedMessage,
        string notNumericMessage,
        ref string? message)
    {
        var value = (raw ?? string.Empty).TrimEnd();
        if (value.Length == 0 || value == BlankMarker || (value.Length <= length && IsAllZeros(value)))
        {
            message ??= notProvidedMessage;
            return (CardViewFilterState.Blank, BlankMarker);
        }
        if (value.Length != length || !value.All(char.IsAsciiDigit))
        {
            message ??= notNumericMessage;
            return (CardViewFilterState.NotOk, string.Empty);
        }
        return (CardViewFilterState.Valid, value);
    }

    private static CardViewCursorField CursorFor(CardViewFilterState accountFilter, CardViewFilterState cardFilter)
    {
        if (accountFilter != CardViewFilterState.Valid)
        {
            return CardViewCursorField.Account;
        }
        return cardFilter != CardViewFilterState.Valid ? CardViewCursorField.Card : CardViewCursorField.Account;
    }

    private static string NumericMove(string? raw, int length)
    {
        var value = (raw ?? string.Empty).Trim();
        if (value.Length > length)
        {
            value = value[^length..];
        }
        return value.PadLeft(length, '0');
    }

    private static bool IsAllZeros(string value) => value.Length > 0 && value.All(c => c == '0');

    private static string FormatCode(long code) => code.ToString().PadLeft(9, '0');
}
