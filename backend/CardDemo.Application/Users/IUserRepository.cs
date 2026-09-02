using CardDemo.Application.Common;
using CardDemo.Domain.Users;

namespace CardDemo.Application.Users;

/// <summary>Keyed access parity with USRSEC KSDS (KEYS(8,0) = SEC-USR-ID).</summary>
public interface IUserRepository
{
    Task<User?> GetByIdAsync(string userId, CancellationToken cancellationToken = default);
    Task UpsertAsync(User user, CancellationToken cancellationToken = default);
    Task<int> CountAsync(CancellationToken cancellationToken = default);

    /// <summary>COUSR01C WRITE: false when the key already exists (DUPKEY/DUPREC).</summary>
    Task<bool> AddAsync(User user, CancellationToken cancellationToken = default);

    /// <summary>COUSR02C REWRITE: false when the key is not on file (NOTFND).</summary>
    Task<bool> UpdateAsync(User user, CancellationToken cancellationToken = default);

    /// <summary>COUSR03C DELETE: false when the key is not on file (NOTFND).</summary>
    Task<bool> DeleteAsync(string userId, CancellationToken cancellationToken = default);

    /// <summary>
    /// COUSR00C forward browse: STARTBR at <paramref name="startUserId"/> (GTEQ) then READNEXT × pageSize;
    /// <paramref name="inclusive"/> false skips the record at the start key (PF8 re-positions on USRID-LAST).
    /// <see cref="KeyedPage{T}.HasMore"/> is the look-ahead READNEXT.
    /// </summary>
    Task<KeyedPage<User>> BrowseForwardAsync(string startUserId, bool inclusive, int pageSize, CancellationToken cancellationToken = default);

    /// <summary>
    /// COUSR00C backward browse: READPREV × pageSize strictly before <paramref name="beforeUserId"/>,
    /// returned in ascending key order; <see cref="KeyedPage{T}.HasMore"/> is the look-behind READPREV.
    /// </summary>
    Task<KeyedPage<User>> BrowseBackwardAsync(string beforeUserId, int pageSize, CancellationToken cancellationToken = default);
}
