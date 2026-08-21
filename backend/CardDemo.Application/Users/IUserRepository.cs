using CardDemo.Domain.Users;

namespace CardDemo.Application.Users;

/// <summary>
/// Keyed-read parity with the USRSEC VSAM KSDS (KEYS(8,0)): a read either finds the record or does not.
/// </summary>
public interface IUserRepository
{
    Task<User?> GetByIdAsync(string userId, CancellationToken cancellationToken = default);
    Task UpsertAsync(User user, CancellationToken cancellationToken = default);
    Task<int> CountAsync(CancellationToken cancellationToken = default);
}
