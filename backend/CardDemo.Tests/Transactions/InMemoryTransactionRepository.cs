using CardDemo.Application.Common;
using CardDemo.Application.Transactions;
using CardDemo.Domain.Transactions;

namespace CardDemo.Tests.Transactions;

/// <summary>
/// Key-ordered stand-in for the TRANSACT KSDS with the same GTEQ/peek contract as TransactionRepository.
/// </summary>
public sealed class InMemoryTransactionRepository(IEnumerable<Transaction> transactions) : ITransactionRepository
{
    private readonly List<Transaction> _records = transactions
        .OrderBy(t => t.TransactionId, StringComparer.Ordinal)
        .ToList();

    public int BrowseCalls { get; private set; }

    public Task<Transaction?> GetByIdAsync(string transactionId, CancellationToken cancellationToken = default) =>
        Task.FromResult(_records.FirstOrDefault(t => t.TransactionId == transactionId));

    public Task<KeyedPage<Transaction>> BrowseAsync(string startTransactionId, int pageSize, CancellationToken cancellationToken = default)
    {
        BrowseCalls++;
        var rows = _records
            .Where(t => string.CompareOrdinal(t.TransactionId, startTransactionId) >= 0)
            .Take(pageSize + 1)
            .ToList();
        return Task.FromResult(new KeyedPage<Transaction>(rows.Take(pageSize).ToList(), rows.Count > pageSize));
    }

    public Task<IReadOnlyList<Transaction>> BrowseBackwardAsync(string beforeTransactionId, int pageSize, CancellationToken cancellationToken = default)
    {
        var rows = _records
            .Where(t => string.CompareOrdinal(t.TransactionId, beforeTransactionId) < 0)
            .OrderByDescending(t => t.TransactionId, StringComparer.Ordinal)
            .Take(pageSize)
            .ToList();
        rows.Reverse();
        return Task.FromResult<IReadOnlyList<Transaction>>(rows);
    }

    public Task<IReadOnlyList<Transaction>> ListByCardNumberAsync(string cardNumber, CancellationToken cancellationToken = default) =>
        Task.FromResult<IReadOnlyList<Transaction>>(_records.Where(t => t.CardNumber == cardNumber).ToList());
}

/// <summary>RESP other than NORMAL/NOTFND/ENDFILE on every browse call (FR-S07-20).</summary>
public sealed class FailingTransactionRepository : ITransactionRepository
{
    public Task<Transaction?> GetByIdAsync(string transactionId, CancellationToken cancellationToken = default) => throw new InvalidOperationException("TRANSACT unavailable");

    public Task<KeyedPage<Transaction>> BrowseAsync(string startTransactionId, int pageSize, CancellationToken cancellationToken = default) => throw new InvalidOperationException("TRANSACT unavailable");

    public Task<IReadOnlyList<Transaction>> BrowseBackwardAsync(string beforeTransactionId, int pageSize, CancellationToken cancellationToken = default) => throw new InvalidOperationException("TRANSACT unavailable");

    public Task<IReadOnlyList<Transaction>> ListByCardNumberAsync(string cardNumber, CancellationToken cancellationToken = default) => throw new InvalidOperationException("TRANSACT unavailable");
}

public static class TransactionFixtures
{
    public static Transaction Make(long sequence, decimal amount = 12.34m, string description = "Purchase at Abshire-Lowe", DateTime? originalTimestamp = null) =>
        Make(sequence.ToString("D16"), amount, description, originalTimestamp);

    public static Transaction Make(string transactionId, decimal amount = 12.34m, string description = "Purchase at Abshire-Lowe", DateTime? originalTimestamp = null) => new()
    {
        TransactionId = transactionId,
        TypeCode = "01",
        CategoryCode = "0001",
        Source = "POS TERM",
        Description = description,
        Amount = amount,
        MerchantId = "000000001",
        MerchantName = "Merchant",
        MerchantCity = "City",
        MerchantZip = "00000",
        CardNumber = "4000000000000001",
        OriginalTimestamp = originalTimestamp ?? new DateTime(2022, 7, 19, 23, 12, 34),
        ProcessedTimestamp = originalTimestamp ?? new DateTime(2022, 7, 19, 23, 12, 34)
    };

    /// <summary>Ids 0000000000000001 .. count, zero-filled to 16 (VSAM key order == numeric order).</summary>
    public static List<Transaction> Sequence(int count) =>
        Enumerable.Range(1, count).Select(i => Make(i)).ToList();
}
