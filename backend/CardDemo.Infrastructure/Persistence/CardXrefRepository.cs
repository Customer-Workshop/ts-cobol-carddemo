using CardDemo.Application.Cards;
using CardDemo.Domain.Cards;
using Microsoft.EntityFrameworkCore;

namespace CardDemo.Infrastructure.Persistence;

public class CardXrefRepository(CardDemoDbContext dbContext) : ICardXrefRepository
{
    public Task<CardXref?> GetByCardNumberAsync(string cardNumber, CancellationToken cancellationToken = default) =>
        dbContext.CardXrefs.AsNoTracking().SingleOrDefaultAsync(x => x.CardNumber == cardNumber, cancellationToken);

    public Task<CardXref?> GetFirstByAccountIdAsync(string accountId, CancellationToken cancellationToken = default) =>
        dbContext.CardXrefs.AsNoTracking()
            .Where(x => x.AccountId == accountId)
            .OrderBy(x => x.CardNumber)
            .FirstOrDefaultAsync(cancellationToken);

    public async Task<IReadOnlyList<CardXref>> ListByAccountIdAsync(string accountId, CancellationToken cancellationToken = default) =>
        await dbContext.CardXrefs.AsNoTracking()
            .Where(x => x.AccountId == accountId)
            .OrderBy(x => x.CardNumber)
            .ToListAsync(cancellationToken);
}
