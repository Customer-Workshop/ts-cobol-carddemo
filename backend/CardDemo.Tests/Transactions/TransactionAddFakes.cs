using CardDemo.Application.Cards;
using CardDemo.Application.Common;
using CardDemo.Application.Transactions;
using CardDemo.Domain.Cards;
using CardDemo.Domain.Transactions;

namespace CardDemo.Tests.Transactions;

internal sealed class FakeCardXrefRepository : ICardXrefRepository
{
    public List<CardXref> Rows { get; } = [];

    public bool Fail { get; set; }

    public Task<CardXref?> GetByCardNumberAsync(string cardNumber, CancellationToken cancellationToken = default)
    {
        ThrowIfFailing();
        return Task.FromResult(Rows.FirstOrDefault(x => x.CardNumber == cardNumber));
    }

    public Task<CardXref?> GetFirstByAccountIdAsync(string accountId, CancellationToken cancellationToken = default)
    {
        ThrowIfFailing();
        return Task.FromResult(Rows.Where(x => x.AccountId == accountId).OrderBy(x => x.CardNumber).FirstOrDefault());
    }

    public Task<IReadOnlyList<CardXref>> ListByAccountIdAsync(string accountId, CancellationToken cancellationToken = default)
    {
        ThrowIfFailing();
        return Task.FromResult<IReadOnlyList<CardXref>>(Rows.Where(x => x.AccountId == accountId).ToList());
    }

    private void ThrowIfFailing()
    {
        if (Fail)
        {
            throw new InvalidOperationException("xref store unavailable");
        }
    }
}

internal sealed class FakeTransactionRepository : ITransactionRepository
{
    public List<Transaction> Rows { get; } = [];

    public bool FailRead { get; set; }

    public bool FailWrite { get; set; }

    /// <summary>Simulates another writer taking the id between READPREV and WRITE.</summary>
    public bool DuplicateOnWrite { get; set; }

    public Task<Transaction?> GetByIdAsync(string transactionId, CancellationToken cancellationToken = default) =>
        Task.FromResult(Rows.FirstOrDefault(t => t.TransactionId == transactionId));

    public Task<KeyedPage<Transaction>> BrowseAsync(string startTransactionId, int pageSize, CancellationToken cancellationToken = default) =>
        throw new NotSupportedException();

    public Task<IReadOnlyList<Transaction>> BrowseBackwardAsync(string beforeTransactionId, int pageSize, CancellationToken cancellationToken = default) =>
        throw new NotSupportedException();

    public Task<IReadOnlyList<Transaction>> ListByCardNumberAsync(string cardNumber, CancellationToken cancellationToken = default) =>
        Task.FromResult<IReadOnlyList<Transaction>>(Rows.Where(t => t.CardNumber == cardNumber).ToList());

    public Task<Transaction?> GetLastAsync(CancellationToken cancellationToken = default)
    {
        if (FailRead)
        {
            throw new InvalidOperationException("transaction store unavailable");
        }
        return Task.FromResult(Rows.OrderByDescending(t => t.TransactionId, StringComparer.Ordinal).FirstOrDefault());
    }

    public Task AddAsync(Transaction transaction, CancellationToken cancellationToken = default)
    {
        if (FailWrite)
        {
            throw new InvalidOperationException("transaction store unavailable");
        }
        if (DuplicateOnWrite || Rows.Any(t => t.TransactionId == transaction.TransactionId))
        {
            throw new DuplicateTransactionIdException(transaction.TransactionId);
        }
        Rows.Add(transaction);
        return Task.CompletedTask;
    }
}
