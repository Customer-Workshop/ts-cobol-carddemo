using CardDemo.Application.Common;
using CardDemo.Application.Transactions;
using CardDemo.Domain.Transactions;
using Microsoft.EntityFrameworkCore;

namespace CardDemo.Infrastructure.Persistence;

public class TransactionRepository(CardDemoDbContext dbContext) : ITransactionRepository
{
    public Task<Transaction?> GetByIdAsync(string transactionId, CancellationToken cancellationToken = default) =>
        dbContext.Transactions.AsNoTracking().SingleOrDefaultAsync(t => t.TransactionId == transactionId, cancellationToken);

    public async Task<KeyedPage<Transaction>> BrowseAsync(
        string startTransactionId,
        int pageSize,
        CancellationToken cancellationToken = default)
    {
        ArgumentOutOfRangeException.ThrowIfNegativeOrZero(pageSize);
        var rows = await dbContext.Transactions.AsNoTracking()
            .Where(t => t.TransactionId.CompareTo(startTransactionId) >= 0)
            .OrderBy(t => t.TransactionId)
            .Take(pageSize + 1)
            .ToListAsync(cancellationToken);
        return new KeyedPage<Transaction>(rows.Take(pageSize).ToList(), rows.Count > pageSize);
    }

    public async Task<IReadOnlyList<Transaction>> BrowseBackwardAsync(
        string beforeTransactionId,
        int pageSize,
        CancellationToken cancellationToken = default)
    {
        ArgumentOutOfRangeException.ThrowIfNegativeOrZero(pageSize);
        var rows = await dbContext.Transactions.AsNoTracking()
            .Where(t => t.TransactionId.CompareTo(beforeTransactionId) < 0)
            .OrderByDescending(t => t.TransactionId)
            .Take(pageSize)
            .ToListAsync(cancellationToken);
        rows.Reverse();
        return rows;
    }

    public async Task<IReadOnlyList<Transaction>> ListByCardNumberAsync(string cardNumber, CancellationToken cancellationToken = default) =>
        await dbContext.Transactions.AsNoTracking()
            .Where(t => t.CardNumber == cardNumber)
            .OrderBy(t => t.TransactionId)
            .ToListAsync(cancellationToken);
}
