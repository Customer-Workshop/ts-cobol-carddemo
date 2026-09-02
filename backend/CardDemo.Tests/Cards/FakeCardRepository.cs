using CardDemo.Application.Cards;
using CardDemo.Application.Common;
using CardDemo.Domain.Cards;

namespace CardDemo.Tests.Cards;

/// <summary>In-memory CARDDAT KSDS: key-ordered browse verbs with the same contract as CardRepository.</summary>
public sealed class FakeCardRepository(IEnumerable<Card> cards) : ICardRepository
{
    private readonly List<Card> _cards = cards.OrderBy(c => c.CardNumber, StringComparer.Ordinal).ToList();

    public static Card Make(string cardNumber, string accountId, string status = "Y") => new()
    {
        CardNumber = cardNumber,
        AccountId = accountId,
        CvvCode = "123",
        EmbossedName = "TEST",
        ActiveStatus = status
    };

    /// <summary>Cards 0000000000000001..N on accounts 00000000001.. (card i belongs to account (i-1)/cardsPerAccount + 1).</summary>
    public static List<Card> Sequence(int count, int cardsPerAccount = 1) =>
        Enumerable.Range(1, count)
            .Select(i => Make(i.ToString("D16"), (((i - 1) / cardsPerAccount) + 1).ToString("D11"), i % 2 == 0 ? "N" : "Y"))
            .ToList();

    public Task<Card?> GetByCardNumberAsync(string cardNumber, CancellationToken cancellationToken = default) =>
        Task.FromResult(_cards.SingleOrDefault(c => c.CardNumber == cardNumber));

    public Task<IReadOnlyList<Card>> ListByAccountIdAsync(string accountId, CancellationToken cancellationToken = default) =>
        Task.FromResult<IReadOnlyList<Card>>(_cards.Where(c => c.AccountId == accountId).ToList());

    public Task<KeyedPage<Card>> BrowseAsync(string startCardNumber, int pageSize, string? accountIdFilter = null, CancellationToken cancellationToken = default)
    {
        var rows = Filter(_cards.Where(c => string.CompareOrdinal(c.CardNumber, startCardNumber) >= 0), accountIdFilter, null)
            .Take(pageSize + 1).ToList();
        return Task.FromResult(new KeyedPage<Card>(rows.Take(pageSize).ToList(), rows.Count > pageSize));
    }

    public Task<IReadOnlyList<Card>> BrowseForwardAsync(string startCardNumber, int maxRows, string? accountIdFilter, string? cardNumberFilter, CancellationToken cancellationToken = default) =>
        Task.FromResult<IReadOnlyList<Card>>(
            Filter(_cards.Where(c => string.CompareOrdinal(c.CardNumber, startCardNumber) >= 0), accountIdFilter, cardNumberFilter)
                .Take(maxRows).ToList());

    public Task<IReadOnlyList<Card>> BrowseBackwardAsync(string beforeCardNumber, int maxRows, string? accountIdFilter, string? cardNumberFilter, CancellationToken cancellationToken = default) =>
        Task.FromResult<IReadOnlyList<Card>>(
            Filter(_cards.Where(c => string.CompareOrdinal(c.CardNumber, beforeCardNumber) < 0), accountIdFilter, cardNumberFilter)
                .OrderByDescending(c => c.CardNumber, StringComparer.Ordinal)
                .Take(maxRows).ToList());

    public Task<Card?> ReadNextAsync(string afterCardNumber, CancellationToken cancellationToken = default) =>
        Task.FromResult(_cards.FirstOrDefault(c => string.CompareOrdinal(c.CardNumber, afterCardNumber) > 0));

    public Task<CardRewriteOutcome> RewriteAsync(string cardNumber, Func<Card, bool> rewrite, CancellationToken cancellationToken = default) =>
        throw new NotSupportedException();

    private static IEnumerable<Card> Filter(IEnumerable<Card> query, string? accountIdFilter, string? cardNumberFilter)
    {
        if (!string.IsNullOrEmpty(accountIdFilter))
        {
            query = query.Where(c => c.AccountId == accountIdFilter);
        }
        if (!string.IsNullOrEmpty(cardNumberFilter))
        {
            query = query.Where(c => c.CardNumber == cardNumberFilter);
        }
        return query;
    }
}
