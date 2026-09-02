using CardDemo.Domain.Accounts;
using CardDemo.Domain.Customers;

namespace CardDemo.Application.AccountUpdate;

/// <summary>Outcome classes of 9600-WRITE-PROCESSING (app/cbl/COACTUPC.cbl:3888-4105).</summary>
public enum AccountUpdateWriteStatus
{
    Updated,
    AccountLockFailed,
    CustomerLockFailed,
    ChangedBeforeUpdate,
    UpdateFailed
}

/// <summary>
/// One unit of work mirroring READ UPDATE account → READ UPDATE customer → compare → REWRITE account →
/// REWRITE customer → commit, with SYNCPOINT ROLLBACK on any failure after the locks.
/// </summary>
public interface IAccountUpdateWriter
{
    /// <param name="snapshotUnchanged">9700-CHECK-CHANGE-IN-REC: true when the locked rows still match the fetched snapshot.</param>
    /// <param name="applyChanges">Mutates the locked rows with the validated new values.</param>
    Task<AccountUpdateWriteStatus> WriteAsync(
        string accountId,
        string customerId,
        Func<Account, Customer, bool> snapshotUnchanged,
        Action<Account, Customer> applyChanges,
        CancellationToken cancellationToken = default);
}
