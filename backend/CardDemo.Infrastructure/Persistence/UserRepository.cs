using CardDemo.Application.Common;
using CardDemo.Application.Users;
using CardDemo.Domain.Users;
using Microsoft.EntityFrameworkCore;

namespace CardDemo.Infrastructure.Persistence;

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

    public async Task<bool> AddAsync(User user, CancellationToken cancellationToken = default)
    {
        if (await dbContext.Users.AnyAsync(u => u.UserId == user.UserId, cancellationToken))
        {
            return false;
        }
        dbContext.Users.Add(user);
        try
        {
            await dbContext.SaveChangesAsync(cancellationToken);
        }
        catch (DbUpdateException)
        {
            dbContext.Entry(user).State = EntityState.Detached;
            if (await dbContext.Users.AsNoTracking().AnyAsync(u => u.UserId == user.UserId, cancellationToken))
            {
                return false;
            }
            throw;
        }
        return true;
    }

    public async Task<bool> UpdateAsync(User user, CancellationToken cancellationToken = default)
    {
        var existing = await dbContext.Users.SingleOrDefaultAsync(u => u.UserId == user.UserId, cancellationToken);
        if (existing is null)
        {
            return false;
        }
        existing.FirstName = user.FirstName;
        existing.LastName = user.LastName;
        existing.PasswordHash = user.PasswordHash;
        existing.UserType = user.UserType;
        await dbContext.SaveChangesAsync(cancellationToken);
        return true;
    }

    public async Task<bool> DeleteAsync(string userId, CancellationToken cancellationToken = default)
    {
        var existing = await dbContext.Users.SingleOrDefaultAsync(u => u.UserId == userId, cancellationToken);
        if (existing is null)
        {
            return false;
        }
        dbContext.Users.Remove(existing);
        await dbContext.SaveChangesAsync(cancellationToken);
        return true;
    }

    public async Task<KeyedPage<User>> BrowseForwardAsync(string startUserId, bool inclusive, int pageSize, CancellationToken cancellationToken = default)
    {
        ArgumentOutOfRangeException.ThrowIfNegativeOrZero(pageSize);
        var query = dbContext.Users.AsNoTracking();
        query = inclusive
            ? query.Where(u => u.UserId.CompareTo(startUserId) >= 0)
            : query.Where(u => u.UserId.CompareTo(startUserId) > 0);
        var rows = await query.OrderBy(u => u.UserId).Take(pageSize + 1).ToListAsync(cancellationToken);
        return new KeyedPage<User>(rows.Take(pageSize).ToList(), rows.Count > pageSize);
    }

    public async Task<KeyedPage<User>> BrowseBackwardAsync(string beforeUserId, int pageSize, CancellationToken cancellationToken = default)
    {
        ArgumentOutOfRangeException.ThrowIfNegativeOrZero(pageSize);
        var rows = await dbContext.Users.AsNoTracking()
            .Where(u => u.UserId.CompareTo(beforeUserId) < 0)
            .OrderByDescending(u => u.UserId)
            .Take(pageSize + 1)
            .ToListAsync(cancellationToken);
        var page = rows.Take(pageSize).Reverse().ToList();
        return new KeyedPage<User>(page, rows.Count > pageSize);
    }
}
