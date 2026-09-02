using CardDemo.Application.AccountUpdate;
using CardDemo.Domain.Accounts;
using CardDemo.Domain.Customers;
using Microsoft.EntityFrameworkCore;
using Npgsql;

namespace CardDemo.Infrastructure.Persistence;

/// <summary>
/// 9600-WRITE-PROCESSING (app/cbl/COACTUPC.cbl:3888-4105) over PostgreSQL: the two READ UPDATE calls become
/// SELECT ... FOR UPDATE NOWAIT inside one transaction, REWRITE failures roll the whole unit back.
/// </summary>
public class AccountUpdateWriter(CardDemoDbContext dbContext) : IAccountUpdateWriter
{
    private const string LockNotAvailable = "55P03";

    public async Task<AccountUpdateWriteStatus> WriteAsync(
        string accountId,
        string customerId,
        Func<Account, Customer, bool> snapshotUnchanged,
        Action<Account, Customer> applyChanges,
        CancellationToken cancellationToken = default)
    {
        await using var transaction = await dbContext.Database.BeginTransactionAsync(cancellationToken);

        Account? account;
        try
        {
            account = await dbContext.Accounts
                .FromSqlInterpolated($"SELECT * FROM accounts WHERE acct_id = {accountId} FOR UPDATE NOWAIT")
                .SingleOrDefaultAsync(cancellationToken);
        }
        catch (Exception ex) when (IsLockNotAvailable(ex))
        {
            await transaction.RollbackAsync(cancellationToken);
            return AccountUpdateWriteStatus.AccountLockFailed;
        }
        if (account is null)
        {
            await transaction.RollbackAsync(cancellationToken);
            return AccountUpdateWriteStatus.AccountLockFailed;
        }

        Customer? customer;
        try
        {
            customer = await dbContext.Customers
                .FromSqlInterpolated($"SELECT * FROM customers WHERE cust_id = {customerId} FOR UPDATE NOWAIT")
                .SingleOrDefaultAsync(cancellationToken);
        }
        catch (Exception ex) when (IsLockNotAvailable(ex))
        {
            await transaction.RollbackAsync(cancellationToken);
            return AccountUpdateWriteStatus.CustomerLockFailed;
        }
        if (customer is null)
        {
            await transaction.RollbackAsync(cancellationToken);
            return AccountUpdateWriteStatus.CustomerLockFailed;
        }

        if (!snapshotUnchanged(account, customer))
        {
            await transaction.RollbackAsync(cancellationToken);
            return AccountUpdateWriteStatus.ChangedBeforeUpdate;
        }

        applyChanges(account, customer);
        try
        {
            await dbContext.SaveChangesAsync(cancellationToken);
            await transaction.CommitAsync(cancellationToken);
            return AccountUpdateWriteStatus.Updated;
        }
        catch (DbUpdateException)
        {
            await transaction.RollbackAsync(cancellationToken);
            return AccountUpdateWriteStatus.UpdateFailed;
        }
    }

    private static bool IsLockNotAvailable(Exception ex) =>
        ex is PostgresException { SqlState: LockNotAvailable }
        || (ex.InnerException is not null && IsLockNotAvailable(ex.InnerException));
}
