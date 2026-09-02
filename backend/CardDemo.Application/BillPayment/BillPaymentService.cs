using System.Globalization;
using CardDemo.Application.Cards;
using CardDemo.Domain.Accounts;
using CardDemo.Domain.Cards;
using CardDemo.Domain.Transactions;

namespace CardDemo.Application.BillPayment;

/// <summary>
/// Port of COBIL00C PROCESS-ENTER-KEY (app/cbl/COBIL00C.cbl:154-244): mandatory account id,
/// Y/N edit, account read, balance edit, nothing-to-pay guard, confirmation prompt, and the
/// confirmed path (first card, TRAN-ID = last + 1, WRITE TRANSACT, REWRITE ACCTDAT) with the
/// exact legacy messages (FR-S11-01..15).
/// </summary>
public class BillPaymentService(
    IBillPaymentRepository repository,
    ICardXrefRepository cardXrefRepository,
    TimeProvider timeProvider)
{
    public const string AccountIdRequiredMessage = "Acct ID can NOT be empty...";
    public const string InvalidConfirmationMessage = "Invalid value. Valid values are (Y/N)...";
    public const string NothingToPayMessage = "You have nothing to pay...";
    public const string ConfirmationRequiredMessage = "Confirm to make a bill payment...";
    public const string AccountNotFoundMessage = "Account ID NOT found...";
    public const string AccountLookupErrorMessage = "Unable to lookup Account...";
    public const string AccountUpdateErrorMessage = "Unable to Update Account...";
    public const string CardLookupErrorMessage = "Unable to lookup XREF AIX file...";
    public const string TransactionNotFoundMessage = "Transaction ID NOT found...";
    public const string TransactionLookupErrorMessage = "Unable to lookup Transaction...";
    public const string DuplicateTransactionMessage = "Tran ID already exist...";
    public const string TransactionWriteErrorMessage = "Unable to Add Bill pay Transaction...";

    public const string TransactionTypeCode = "02";
    public const string TransactionCategoryCode = "0002";
    public const string TransactionSource = "POS TERM";
    public const string TransactionDescription = "BILL PAYMENT - ONLINE";
    public const string MerchantId = "999999999";
    public const string MerchantName = "BILL PAYMENT";
    public const string MerchantCity = "N/A";
    public const string MerchantZip = "N/A";

    private const int TransactionIdDigits = 16;
    private const decimal TransactionAmountModulus = 1_000_000_000m;   // TRAN-AMT PIC S9(09)V99
    private const decimal BalanceEditModulus = 10_000_000_000m;        // WS-CURR-BAL PIC +9999999999.99
    private const long TransactionIdModulus = 10_000_000_000_000_000;  // WS-TRAN-ID-NUM PIC 9(16)

    public async Task<BillPaymentResult> PayAsync(BillPaymentRequest request, CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(request.AccountId))
        {
            return Error(BillPaymentOutcome.AccountIdRequired, AccountIdRequiredMessage, BillPaymentCursorField.AccountId);
        }

        var accountId = request.AccountId.Trim();
        var confirm = request.Confirm?.Trim() ?? string.Empty;

        switch (confirm)
        {
            case "Y":
            case "y":
                return await ProcessConfirmedAsync(accountId, cancellationToken);
            case "N":
            case "n":
                return new BillPaymentResult(BillPaymentOutcome.Declined, string.Empty, null, BillPaymentCursorField.AccountId, ClearScreen: true);
            case "":
                return await ProcessUnconfirmedAsync(accountId, cancellationToken);
            default:
                return Error(BillPaymentOutcome.InvalidConfirmation, InvalidConfirmationMessage, BillPaymentCursorField.Confirm);
        }
    }

    private async Task<BillPaymentResult> ProcessUnconfirmedAsync(string accountId, CancellationToken cancellationToken)
    {
        Account? account;
        try
        {
            account = await repository.GetAccountAsync(accountId, cancellationToken);
        }
        catch (Exception)
        {
            return Error(BillPaymentOutcome.AccountLookupError, AccountLookupErrorMessage, BillPaymentCursorField.AccountId);
        }
        if (account is null)
        {
            return Error(BillPaymentOutcome.AccountNotFound, AccountNotFoundMessage, BillPaymentCursorField.AccountId);
        }

        var balance = FormatBalance(account.CurrentBalance);
        if (account.CurrentBalance <= 0)
        {
            return Error(BillPaymentOutcome.NothingToPay, NothingToPayMessage, BillPaymentCursorField.AccountId, balance);
        }
        return Error(BillPaymentOutcome.ConfirmationRequired, ConfirmationRequiredMessage, BillPaymentCursorField.Confirm, balance);
    }

    private async Task<BillPaymentResult> ProcessConfirmedAsync(string accountId, CancellationToken cancellationToken)
    {
        Account? account;
        try
        {
            account = await repository.GetAccountForUpdateAsync(accountId, cancellationToken);
        }
        catch (Exception)
        {
            return Error(BillPaymentOutcome.AccountLookupError, AccountLookupErrorMessage, BillPaymentCursorField.AccountId);
        }

        var posted = false;
        try
        {
            if (account is null)
            {
                return Error(BillPaymentOutcome.AccountNotFound, AccountNotFoundMessage, BillPaymentCursorField.AccountId);
            }

            var balance = FormatBalance(account.CurrentBalance);
            if (account.CurrentBalance <= 0)
            {
                return Error(BillPaymentOutcome.NothingToPay, NothingToPayMessage, BillPaymentCursorField.AccountId, balance);
            }

            CardXref? cardXref;
            try
            {
                cardXref = await cardXrefRepository.GetFirstByAccountIdAsync(accountId, cancellationToken);
            }
            catch (Exception)
            {
                return Error(BillPaymentOutcome.CardLookupError, CardLookupErrorMessage, BillPaymentCursorField.AccountId, balance);
            }
            if (cardXref is null)
            {
                return Error(BillPaymentOutcome.CardNotFound, AccountNotFoundMessage, BillPaymentCursorField.AccountId, balance);
            }

            string transactionId;
            try
            {
                var lastTransactionId = await repository.GetLastTransactionIdAsync(cancellationToken);
                if (!TryAllocateTransactionId(lastTransactionId, out transactionId))
                {
                    return Error(BillPaymentOutcome.TransactionLookupError, TransactionLookupErrorMessage, BillPaymentCursorField.AccountId, balance);
                }
            }
            catch (Exception)
            {
                return Error(BillPaymentOutcome.TransactionLookupError, TransactionLookupErrorMessage, BillPaymentCursorField.AccountId, balance);
            }

            var transaction = BuildTransaction(transactionId, account.CurrentBalance, cardXref.CardNumber);
            account.CurrentBalance -= transaction.Amount;

            var outcome = await repository.PostPaymentAsync(account, transaction, cancellationToken);
            switch (outcome)
            {
                case BillPaymentPostOutcome.Posted:
                    posted = true;
                    return new BillPaymentResult(
                        BillPaymentOutcome.PaymentSuccessful,
                        SuccessMessage(transactionId),
                        BillPaymentMessageSeverity.Success,
                        BillPaymentCursorField.AccountId,
                        TransactionId: transactionId,
                        ClearScreen: true);
                case BillPaymentPostOutcome.DuplicateTransaction:
                    return Error(BillPaymentOutcome.DuplicateTransaction, DuplicateTransactionMessage, BillPaymentCursorField.AccountId, balance);
                case BillPaymentPostOutcome.TransactionWriteError:
                    return Error(BillPaymentOutcome.TransactionWriteError, TransactionWriteErrorMessage, BillPaymentCursorField.AccountId, balance);
                case BillPaymentPostOutcome.AccountNotFound:
                    return Error(BillPaymentOutcome.AccountNotFound, AccountNotFoundMessage, BillPaymentCursorField.AccountId, balance);
                default:
                    return Error(BillPaymentOutcome.AccountUpdateError, AccountUpdateErrorMessage, BillPaymentCursorField.AccountId, balance);
            }
        }
        finally
        {
            if (!posted)
            {
                await repository.ReleaseAccountAsync(cancellationToken);
            }
        }
    }

    private Transaction BuildTransaction(string transactionId, decimal currentBalance, string cardNumber)
    {
        var timestamp = CurrentTimestamp();
        return new Transaction
        {
            TransactionId = transactionId,
            TypeCode = TransactionTypeCode,
            CategoryCode = TransactionCategoryCode,
            Source = TransactionSource,
            Description = TransactionDescription,
            Amount = ToTransactionAmount(currentBalance),
            MerchantId = MerchantId,
            MerchantName = MerchantName,
            MerchantCity = MerchantCity,
            MerchantZip = MerchantZip,
            CardNumber = cardNumber,
            OriginalTimestamp = timestamp,
            ProcessedTimestamp = timestamp
        };
    }

    /// <summary>GET-CURRENT-TIMESTAMP (:249-267): yyyy-MM-dd HH:mm:ss.000000, microseconds never populated.</summary>
    private DateTime CurrentTimestamp()
    {
        var now = timeProvider.GetLocalNow();
        return new DateTime(now.Year, now.Month, now.Day, now.Hour, now.Minute, now.Second, DateTimeKind.Unspecified);
    }

    /// <summary>STRING at :526-530 — note the double space produced by 'Payment successful. ' + ' Your Transaction ID is '.</summary>
    public static string SuccessMessage(string transactionId) =>
        $"Payment successful.  Your Transaction ID is {transactionId}.";

    /// <summary>WS-CURR-BAL PIC +9999999999.99 edit of ACCT-CURR-BAL (:56, :193-194).</summary>
    public static string FormatBalance(decimal balance)
    {
        var magnitude = Math.Truncate(Math.Abs(balance) * 100) / 100 % BalanceEditModulus;
        var sign = balance < 0 ? "-" : "+";
        return sign + magnitude.ToString("0000000000.00", CultureInfo.InvariantCulture);
    }

    /// <summary>MOVE ACCT-CURR-BAL (S9(10)V99) TO TRAN-AMT (S9(09)V99) at :223 — high-order digit truncated.</summary>
    public static decimal ToTransactionAmount(decimal balance)
    {
        var truncated = Math.Truncate(Math.Abs(balance) * 100) / 100 % TransactionAmountModulus;
        return balance < 0 ? -truncated : truncated;
    }

    /// <summary>MOVE TRAN-ID TO WS-TRAN-ID-NUM / ADD 1 (:215-217): last key + 1 in PIC 9(16); ENDFILE → zeros + 1.</summary>
    public static bool TryAllocateTransactionId(string? lastTransactionId, out string transactionId)
    {
        long last = 0;
        if (!string.IsNullOrWhiteSpace(lastTransactionId) &&
            !long.TryParse(lastTransactionId.Trim(), NumberStyles.None, CultureInfo.InvariantCulture, out last))
        {
            transactionId = string.Empty;
            return false;
        }

        transactionId = ((last + 1) % TransactionIdModulus).ToString(CultureInfo.InvariantCulture).PadLeft(TransactionIdDigits, '0');
        return true;
    }

    private static BillPaymentResult Error(BillPaymentOutcome outcome, string message, BillPaymentCursorField cursor, string? balance = null) =>
        new(outcome, message, BillPaymentMessageSeverity.Error, cursor, balance);
}
