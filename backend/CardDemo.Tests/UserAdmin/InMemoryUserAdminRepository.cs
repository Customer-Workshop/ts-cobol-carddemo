using CardDemo.Application.Common;
using CardDemo.Application.Users;
using CardDemo.Domain.Users;

namespace CardDemo.Tests.UserAdmin;

/// <summary>Ordinal-keyed USRSEC stand-in with switchable failure modes for the OTHER response paths.</summary>
internal sealed class InMemoryUserAdminRepository : IUserRepository
{
    private readonly SortedDictionary<string, User> _users = new(StringComparer.Ordinal);

    public bool ThrowOnRead { get; set; }
    public bool ThrowOnWrite { get; set; }

    public IReadOnlyCollection<User> Users => _users.Values;

    public Task<User?> GetByIdAsync(string userId, CancellationToken cancellationToken = default)
    {
        FailIf(ThrowOnRead);
        return Task.FromResult(_users.TryGetValue(userId, out var user) ? Clone(user) : null);
    }

    public Task UpsertAsync(User user, CancellationToken cancellationToken = default)
    {
        _users[user.UserId] = Clone(user);
        return Task.CompletedTask;
    }

    public Task<int> CountAsync(CancellationToken cancellationToken = default) => Task.FromResult(_users.Count);

    public Task<bool> AddAsync(User user, CancellationToken cancellationToken = default)
    {
        FailIf(ThrowOnWrite);
        return Task.FromResult(_users.TryAdd(user.UserId, Clone(user)));
    }

    public Task<bool> UpdateAsync(User user, CancellationToken cancellationToken = default)
    {
        FailIf(ThrowOnWrite);
        if (!_users.ContainsKey(user.UserId))
        {
            return Task.FromResult(false);
        }
        _users[user.UserId] = Clone(user);
        return Task.FromResult(true);
    }

    public Task<bool> DeleteAsync(string userId, CancellationToken cancellationToken = default)
    {
        FailIf(ThrowOnWrite);
        return Task.FromResult(_users.Remove(userId));
    }

    public Task<KeyedPage<User>> BrowseForwardAsync(string startUserId, bool inclusive, int pageSize, CancellationToken cancellationToken = default)
    {
        FailIf(ThrowOnRead);
        var rows = _users.Values
            .Where(u => inclusive
                ? string.CompareOrdinal(u.UserId, startUserId) >= 0
                : string.CompareOrdinal(u.UserId, startUserId) > 0)
            .Take(pageSize + 1)
            .ToList();
        return Task.FromResult(new KeyedPage<User>(rows.Take(pageSize).Select(Clone).ToList(), rows.Count > pageSize));
    }

    public Task<KeyedPage<User>> BrowseBackwardAsync(string beforeUserId, int pageSize, CancellationToken cancellationToken = default)
    {
        FailIf(ThrowOnRead);
        var rows = _users.Values
            .Where(u => string.CompareOrdinal(u.UserId, beforeUserId) < 0)
            .Reverse()
            .Take(pageSize + 1)
            .ToList();
        return Task.FromResult(new KeyedPage<User>(rows.Take(pageSize).Reverse().Select(Clone).ToList(), rows.Count > pageSize));
    }

    private static void FailIf(bool condition)
    {
        if (condition)
        {
            throw new InvalidOperationException("store unavailable");
        }
    }

    private static User Clone(User user) => new()
    {
        UserId = user.UserId,
        FirstName = user.FirstName,
        LastName = user.LastName,
        PasswordHash = user.PasswordHash,
        UserType = user.UserType
    };
}
