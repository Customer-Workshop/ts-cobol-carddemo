using CardDemo.Domain.Cards;

namespace CardDemo.Application.Cards;

/// <summary>
/// Read parity with CCXREF KSDS (KEYS(16 0) = XREF-CARD-NUM) and its AIX CXACAIX (XREF-ACCT-ID, non-unique).
/// </summary>
public interface ICardXrefRepository
{
    Task<CardXref?> GetByCardNumberAsync(string cardNumber, CancellationToken cancellationToken = default);

    /// <summary>CXACAIX keyed READ as in COACTVWC: the first xref (lowest CARD-NUM) for the account, or null.</summary>
    Task<CardXref?> GetFirstByAccountIdAsync(string accountId, CancellationToken cancellationToken = default);

    /// <summary>CXACAIX path: all xrefs for an account, in XREF-CARD-NUM order.</summary>
    Task<IReadOnlyList<CardXref>> ListByAccountIdAsync(string accountId, CancellationToken cancellationToken = default);
}
