using CardDemo.Application.Accounts;
using CardDemo.Domain.Accounts;
using Microsoft.EntityFrameworkCore;

namespace CardDemo.Infrastructure.Persistence;

public class AccountRepository(CardDemoDbContext dbContext) : IAccountRepository
{
    public Task<Account?> GetByIdAsync(string accountId, CancellationToken cancellationToken = default) =>
        dbContext.Accounts.AsNoTracking().SingleOrDefaultAsync(a => a.AccountId == accountId, cancellationToken);
}
