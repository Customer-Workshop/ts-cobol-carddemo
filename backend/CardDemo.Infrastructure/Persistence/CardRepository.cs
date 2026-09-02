using CardDemo.Application.Cards;
using CardDemo.Application.Common;
using CardDemo.Domain.Cards;
using Microsoft.EntityFrameworkCore;

namespace CardDemo.Infrastructure.Persistence;

public class CardRepository(CardDemoDbContext dbContext) : ICardRepository
{
    public Task<Card?> GetByCardNumberAsync(string cardNumber, CancellationToken cancellationToken = default) =>
        dbContext.Cards.AsNoTracking().SingleOrDefaultAsync(c => c.CardNumber == cardNumber, cancellationToken);

    public async Task<IReadOnlyList<Card>> ListByAccountIdAsync(string accountId, CancellationToken cancellationToken = default) =>
        await dbContext.Cards.AsNoTracking()
            .Where(c => c.AccountId == accountId)
            .OrderBy(c => c.CardNumber)
            .ToListAsync(cancellationToken);

    public async Task<KeyedPage<Card>> BrowseAsync(
        string startCardNumber,
        int pageSize,
        string? accountIdFilter = null,
        CancellationToken cancellationToken = default)
    {
        ArgumentOutOfRangeException.ThrowIfNegativeOrZero(pageSize);
        var query = dbContext.Cards.AsNoTracking()
            .Where(c => c.CardNumber.CompareTo(startCardNumber) >= 0);
        if (!string.IsNullOrEmpty(accountIdFilter))
        {
            query = query.Where(c => c.AccountId == accountIdFilter);
        }
        var rows = await query.OrderBy(c => c.CardNumber).Take(pageSize + 1).ToListAsync(cancellationToken);
        return new KeyedPage<Card>(rows.Take(pageSize).ToList(), rows.Count > pageSize);
    }
}
