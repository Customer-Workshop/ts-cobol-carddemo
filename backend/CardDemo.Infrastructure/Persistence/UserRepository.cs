using CardDemo.Application.Users;
using CardDemo.Domain.Users;
using Microsoft.EntityFrameworkCore;

namespace CardDemo.Infrastructure.Persistence;

/// <summary>
/// EF Core implementation of the USRSEC keyed read (seam S01-B4):
/// GetById mirrors CICS READ RESP 0 (found) / 13 (not found).
/// </summary>
public class UserRepository(CardDemoDbContext dbContext) : IUserRepository
{
    public Task<User?> GetByIdAsync(string userId, CancellationToken cancellationToken = default) =>
        dbContext.Users.AsNoTracking().SingleOrDefaultAsync(u => u.UserId == userId, cancellationToken);

    public async Task UpsertAsync(User user, CancellationToken cancellationToken = default)
    {
        var existing = await dbContext.Users.SingleOrDefaultAsync(u => u.UserId == user.UserId, cancellationToken);
        if (existing is null)
        {
            dbContext.Users.Add(user);
        }
        else
        {
            existing.FirstName = user.FirstName;
            existing.LastName = user.LastName;
            existing.PasswordHash = user.PasswordHash;
            existing.UserType = user.UserType;
        }
        await dbContext.SaveChangesAsync(cancellationToken);
    }

    public Task<int> CountAsync(CancellationToken cancellationToken = default) =>
        dbContext.Users.CountAsync(cancellationToken);
}
