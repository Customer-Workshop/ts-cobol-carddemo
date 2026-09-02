using CardDemo.Domain.Accounts;

namespace CardDemo.Application.Accounts;

/// <summary>Keyed read parity with ACCTDAT KSDS (KEYS(11 0) = ACCT-ID).</summary>
public interface IAccountRepository
{
    Task<Account?> GetByIdAsync(string accountId, CancellationToken cancellationToken = default);
}
