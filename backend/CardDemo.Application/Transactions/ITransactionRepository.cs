using CardDemo.Application.Common;
using CardDemo.Domain.Transactions;

namespace CardDemo.Application.Transactions;

/// <summary>
/// Read parity with TRANSACT KSDS (KEYS(16 0) = TRAN-ID). Browses run in TRAN-ID byte order,
/// which is what COTRN00C's STARTBR/READNEXT/READPREV paging presents.
/// </summary>
public interface ITransactionRepository
{
    Task<Transaction?> GetByIdAsync(string transactionId, CancellationToken cancellationToken = default);

    /// <summary>STARTBR TRANSACT at <paramref name="startTransactionId"/> (GTEQ) then READNEXT × <paramref name="pageSize"/>.</summary>
    Task<KeyedPage<Transaction>> BrowseAsync(
        string startTransactionId,
        int pageSize,
        CancellationToken cancellationToken = default);

    /// <summary>READPREV-style page: the <paramref name="pageSize"/> records strictly before <paramref name="beforeTransactionId"/>, returned in ascending TRAN-ID order.</summary>
    Task<IReadOnlyList<Transaction>> BrowseBackwardAsync(
        string beforeTransactionId,
        int pageSize,
        CancellationToken cancellationToken = default);

    /// <summary>All transactions on a card in TRAN-ID order (account-level views join through card_xref).</summary>
    Task<IReadOnlyList<Transaction>> ListByCardNumberAsync(string cardNumber, CancellationToken cancellationToken = default);

    /// <summary>STARTBR at HIGH-VALUES then READPREV: the record with the highest TRAN-ID, or null when the file is empty (COTRN02C.cbl:644-697).</summary>
    Task<Transaction?> GetLastAsync(CancellationToken cancellationToken = default);

    /// <summary>WRITE TRANSACT (COTRN02C.cbl:713-721). Throws <see cref="DuplicateTransactionIdException"/> on DUPKEY/DUPREC.</summary>
    Task AddAsync(Transaction transaction, CancellationToken cancellationToken = default);
}
