using CardDemo.Domain.Transactions;

namespace CardDemo.Application.Transactions;

/// <summary>
/// Port of COTRN01C PROCESS-ENTER-KEY / READ-TRANSACT-FILE (app/cbl/COTRN01C.cbl:144-192, 267-296):
/// mandatory-id check, keyed read protocol (found / not-found / store error), screen-field
/// population (FR-S08-04, 06, 07, 08, 12).
/// </summary>
public class TransactionViewService(ITransactionRepository transactionRepository)
{
    public const string MissingTransactionIdMessage = "Tran ID can NOT be empty...";
    public const string NotFoundMessage = "Transaction ID NOT found...";
    public const string StoreErrorMessage = "Unable to lookup Transaction...";

    public async Task<TransactionViewResult> ViewAsync(string? transactionId, CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(transactionId))
        {
            return new TransactionViewResult(TransactionViewOutcome.MissingTransactionId, MissingTransactionIdMessage);
        }

        var key = transactionId.TrimEnd();

        Transaction? transaction;
        try
        {
            transaction = await transactionRepository.GetByIdAsync(key, cancellationToken);
        }
        catch (Exception)
        {
            return new TransactionViewResult(TransactionViewOutcome.StoreError, StoreErrorMessage);
        }

        if (transaction is null)
        {
            return new TransactionViewResult(TransactionViewOutcome.NotFound, NotFoundMessage);
        }

        return new TransactionViewResult(TransactionViewOutcome.Found, Detail: TransactionViewMapper.ToDetail(transaction));
    }
}
