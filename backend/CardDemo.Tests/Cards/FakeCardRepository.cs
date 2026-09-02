using CardDemo.Application.Cards;
using CardDemo.Application.Common;
using CardDemo.Domain.Cards;

namespace CardDemo.Tests.Cards;

/// <summary>In-memory CARDDAT stand-in with switchable failure modes for the RESP paths.</summary>
public sealed class FakeCardRepository : ICardRepository
{
    public Dictionary<string, Card> Cards { get; } = new();

    public bool FailReads { get; set; }

    public bool FailLock { get; set; }

    public bool FailRewrite { get; set; }

    /// <summary>Simulates another writer touching the row between the fetch and the READ UPDATE.</summary>
    public Action<Card>? BeforeLock { get; set; }

    public Task<Card?> GetByCardNumberAsync(string cardNumber, CancellationToken cancellationToken = default)
    {
        if (FailReads)
        {
            throw new InvalidOperationException("store unavailable");
        }
        return Task.FromResult(Cards.TryGetValue(cardNumber, out var card) ? Clone(card) : null);
    }

    public Task<IReadOnlyList<Card>> ListByAccountIdAsync(string accountId, CancellationToken cancellationToken = default) =>
        Task.FromResult<IReadOnlyList<Card>>(Cards.Values.Where(c => c.AccountId == accountId).OrderBy(c => c.CardNumber).Select(Clone).ToList());

    public Task<KeyedPage<Card>> BrowseAsync(string startCardNumber, int pageSize, string? accountIdFilter = null, CancellationToken cancellationToken = default) =>
        throw new NotSupportedException();

    public Task<CardRewriteOutcome> RewriteAsync(string cardNumber, Func<Card, bool> rewrite, CancellationToken cancellationToken = default)
    {
        if (FailLock)
        {
            throw new InvalidOperationException("lock failed");
        }
        if (!Cards.TryGetValue(cardNumber, out var stored))
        {
            return Task.FromResult(CardRewriteOutcome.NotFound);
        }
        BeforeLock?.Invoke(stored);
        var working = Clone(stored);
        if (!rewrite(working))
        {
            return Task.FromResult(CardRewriteOutcome.Skipped);
        }
        if (FailRewrite)
        {
            throw new InvalidOperationException("rewrite failed");
        }
        Cards[cardNumber] = working;
        return Task.FromResult(CardRewriteOutcome.Rewritten);
    }

    private static Card Clone(Card card) => new()
    {
        CardNumber = card.CardNumber,
        AccountId = card.AccountId,
        CvvCode = card.CvvCode,
        EmbossedName = card.EmbossedName,
        ExpirationDate = card.ExpirationDate,
        ActiveStatus = card.ActiveStatus
    };
}
