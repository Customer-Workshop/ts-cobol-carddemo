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
}
