using CardDemo.Application.Common;
using CardDemo.Application.Transactions;
using CardDemo.Domain.Transactions;

namespace CardDemo.Tests.Transactions;

internal sealed class TransactionViewTestRepository : ITransactionRepository
{
    private readonly Dictionary<string, Transaction> _rows = new(StringComparer.Ordinal);

    public bool ThrowOnRead { get; set; }

    public List<string> RequestedKeys { get; } = [];

    public void Add(Transaction transaction) => _rows[transaction.TransactionId] = transaction;

    public Task<Transaction?> GetByIdAsync(string transactionId, CancellationToken cancellationToken = default)
    {
        RequestedKeys.Add(transactionId);
        if (ThrowOnRead)
        {
            throw new InvalidOperationException("store unavailable");
        }
        return Task.FromResult(_rows.TryGetValue(transactionId, out var row) ? row : null);
    }

    public Task<KeyedPage<Transaction>> BrowseAsync(string startTransactionId, int pageSize, CancellationToken cancellationToken = default) =>
        throw new NotSupportedException();

    public Task<IReadOnlyList<Transaction>> BrowseBackwardAsync(string beforeTransactionId, int pageSize, CancellationToken cancellationToken = default) =>
        throw new NotSupportedException();

    public Task<IReadOnlyList<Transaction>> ListByCardNumberAsync(string cardNumber, CancellationToken cancellationToken = default) =>
        throw new NotSupportedException();

    public Task<Transaction?> GetLastAsync(CancellationToken cancellationToken = default) =>
        throw new NotSupportedException();

    public Task AddAsync(Transaction transaction, CancellationToken cancellationToken = default) =>
        throw new NotSupportedException();

    public static Transaction SampleTransaction(string transactionId = "0000000000683580") =>
        new()
        {
            TransactionId = transactionId,
            TypeCode = "01",
            CategoryCode = "0001",
            Source = "POS TERM",
            Description = "Purchase at Abshire-Lowe",
            Amount = 504.77m,
            MerchantId = "800000000",
            MerchantName = "Abshire-Lowe",
            MerchantCity = "North Enoshaven",
            MerchantZip = "72112",
            CardNumber = "4859452612877065",
            OriginalTimestamp = new DateTime(2022, 6, 10, 19, 27, 53),
            ProcessedTimestamp = null
        };
}
