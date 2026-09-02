using CardDemo.Application.BillPayment;
using CardDemo.Domain.Accounts;
using CardDemo.Domain.Transactions;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Storage;
using Npgsql;

namespace CardDemo.Infrastructure.Persistence;

/// <summary>
/// COBIL00C file access over the shared schema: ACCTDAT READ UPDATE becomes SELECT ... FOR UPDATE inside an
/// explicit transaction that the payment commits (S11-B2); TRANSACT READPREV from HIGH-VALUES becomes MAX(tran_id)
/// under the C-collated key (S11-B3); WRITE / REWRITE RESP codes map to <see cref="BillPaymentPostOutcome"/> (S11-B1).
/// </summary>
public class BillPaymentRepository(CardDemoDbContext dbContext) : IBillPaymentRepository
{
    private const string UniqueViolation = "23505";

    private IDbContextTransaction? _transaction;

    public Task<Account?> GetAccountAsync(string accountId, CancellationToken cancellationToken = default) =>
        dbContext.Accounts.AsNoTracking().SingleOrDefaultAsync(a => a.AccountId == accountId, cancellationToken);

    public async Task<Account?> GetAccountForUpdateAsync(string accountId, CancellationToken cancellationToken = default)
    {
        _transaction ??= await dbContext.Database.BeginTransactionAsync(cancellationToken);
        return await dbContext.Accounts
            .FromSql($"SELECT * FROM accounts WHERE acct_id = {accountId} FOR UPDATE")
            .SingleOrDefaultAsync(cancellationToken);
    }

    public async Task ReleaseAccountAsync(CancellationToken cancellationToken = default)
    {
        if (_transaction is null)
        {
            return;
        }
        await _transaction.RollbackAsync(cancellationToken);
        await _transaction.DisposeAsync();
        _transaction = null;
        dbContext.ChangeTracker.Clear();
    }

    public Task<string?> GetLastTransactionIdAsync(CancellationToken cancellationToken = default) =>
        dbContext.Transactions
            .OrderByDescending(t => t.TransactionId)
            .Select(t => t.TransactionId)
            .FirstOrDefaultAsync(cancellationToken);

    public async Task<BillPaymentPostOutcome> PostPaymentAsync(Account account, Transaction transaction, CancellationToken cancellationToken = default)
    {
        _transaction ??= await dbContext.Database.BeginTransactionAsync(cancellationToken);

        dbContext.Transactions.Add(transaction);
        try
        {
            await dbContext.SaveChangesAsync(cancellationToken);
        }
        catch (DbUpdateException ex) when (ex.InnerException is PostgresException { SqlState: UniqueViolation })
        {
            return BillPaymentPostOutcome.DuplicateTransaction;
        }
        catch (Exception)
        {
            return BillPaymentPostOutcome.TransactionWriteError;
        }

        if (dbContext.Entry(account).State == EntityState.Detached)
        {
            dbContext.Accounts.Attach(account);
        }
        dbContext.Entry(account).Property(a => a.CurrentBalance).IsModified = true;
        try
        {
            await dbContext.SaveChangesAsync(cancellationToken);
        }
        catch (DbUpdateConcurrencyException)
        {
            return BillPaymentPostOutcome.AccountNotFound;
        }
        catch (Exception)
        {
            return BillPaymentPostOutcome.AccountUpdateError;
        }

        await _transaction.CommitAsync(cancellationToken);
        await _transaction.DisposeAsync();
        _transaction = null;
        return BillPaymentPostOutcome.Posted;
    }
}
