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
    /// STARTBR GTEQ at <paramref name="startCardNumber"/> + READNEXT loop with 9500-FILTER-RECORDS
    /// (COCRDLIC.cbl:1129-1171, 1382-1411): up to <paramref name="maxRows"/> cards with CARD-NUM &gt;= start,
    /// in key order, keeping only rows equal to the supplied account / card filters.
    /// </summary>
    Task<IReadOnlyList<Card>> BrowseForwardAsync(
        string startCardNumber,
        int maxRows,
        string? accountIdFilter,
        string? cardNumberFilter,
        CancellationToken cancellationToken = default);

    /// <summary>
    /// READPREV loop below <paramref name="beforeCardNumber"/> (COCRDLIC.cbl:1322-1346): up to
    /// <paramref name="maxRows"/> filtered cards with CARD-NUM &lt; before, nearest key first.
    /// </summary>
    Task<IReadOnlyList<Card>> BrowseBackwardAsync(
        string beforeCardNumber,
        int maxRows,
        string? accountIdFilter,
        string? cardNumberFilter,
        CancellationToken cancellationToken = default);

    /// <summary>Unfiltered look-ahead READNEXT (COCRDLIC.cbl:1197-1214): the card with the smallest CARD-NUM &gt; the given key.</summary>
    Task<Card?> ReadNextAsync(string afterCardNumber, CancellationToken cancellationToken = default);
}
