using CardDemo.Application.Common;
using CardDemo.Domain.Cards;

namespace CardDemo.Application.Cards;

/// <summary>
/// Read parity with CARDDAT KSDS (KEYS(16 0) = CARD-NUM) and its AIX CARDAIX (CARD-ACCT-ID, non-unique).
/// </summary>
public interface ICardRepository
{
    Task<Card?> GetByCardNumberAsync(string cardNumber, CancellationToken cancellationToken = default);

    /// <summary>CARDAIX path: all cards on an account, in CARD-NUM order.</summary>
    Task<IReadOnlyList<Card>> ListByAccountIdAsync(string accountId, CancellationToken cancellationToken = default);

    /// <summary>
    /// COCRDLIC-style browse: STARTBR CARDDAT at <paramref name="startCardNumber"/> (GTEQ) then READNEXT,
    /// optionally keeping only rows whose CARD-ACCT-ID matches <paramref name="accountIdFilter"/>.
    /// </summary>
    Task<KeyedPage<Card>> BrowseAsync(
        string startCardNumber,
        int pageSize,
        string? accountIdFilter = null,
        CancellationToken cancellationToken = default);

    /// <summary>
    /// COCRDUPC-style READ UPDATE + REWRITE: locks the CARDDAT row, hands the current image to
    /// <paramref name="rewrite"/>, and persists the mutated entity only when it returns true.
    /// </summary>
    Task<CardRewriteOutcome> RewriteAsync(
        string cardNumber,
        Func<Card, bool> rewrite,
        CancellationToken cancellationToken = default);
}
