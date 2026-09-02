using CardDemo.Domain.Accounts;
using CardDemo.Domain.Transactions;

namespace CardDemo.Application.BillPayment;

/// <summary>
/// Data seam of COBIL00C: ACCTDAT READ / READ UPDATE + REWRITE, TRANSACT last-key browse and WRITE
/// (boundaries S11-B1..B3). The confirmed path is one unit of work: <see cref="GetAccountForUpdateAsync"/>
/// opens it (record lock), <see cref="PostPaymentAsync"/> commits it, <see cref="ReleaseAccountAsync"/>
/// abandons it when the service stops before writing.
/// </summary>
public interface IBillPaymentRepository
{
    Task<Account?> GetAccountAsync(string accountId, CancellationToken cancellationToken = default);

    Task<Account?> GetAccountForUpdateAsync(string accountId, CancellationToken cancellationToken = default);

    Task ReleaseAccountAsync(CancellationToken cancellationToken = default);

    /// <summary>Highest TRAN-ID (READPREV from HIGH-VALUES); null when the file is empty (ENDFILE).</summary>
    Task<string?> GetLastTransactionIdAsync(CancellationToken cancellationToken = default);

    Task<BillPaymentPostOutcome> PostPaymentAsync(Account account, Transaction transaction, CancellationToken cancellationToken = default);
}
