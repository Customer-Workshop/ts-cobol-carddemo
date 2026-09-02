namespace CardDemo.Application.Transactions;

/// <summary>WRITE TRANSACT returned DFHRESP(DUPKEY) / DFHRESP(DUPREC) (COTRN02C.cbl:735-741).</summary>
public class DuplicateTransactionIdException(string transactionId, Exception? innerException = null)
    : Exception($"Transaction id '{transactionId}' already exists.", innerException)
{
    public string TransactionId { get; } = transactionId;
}
