using CardDemo.Application.Cards;
using CardDemo.Application.Common;
using CardDemo.Domain.Cards;
using FluentAssertions;

namespace CardDemo.Tests.Cards;

/// <summary>
/// Parity tests for COCRDSLC 2200-EDIT-MAP-INPUTS + 9100-GETCARD-BYACCTCARD + screen setup
/// over an in-memory card store (FR-S05-02..13).
/// </summary>
public class CardViewServiceTests
{
    private const string SeedAccount = "00000000050";
    private const string SeedCard = "0500024453765740";

    private sealed class InMemoryCardRepository : ICardRepository
    {
        private readonly Dictionary<string, Card> _cards = new();
        public bool ThrowOnRead { get; set; }
        public int Reads { get; private set; }

        public void Add(Card card) => _cards[card.CardNumber] = card;

        public Task<Card?> GetByCardNumberAsync(string cardNumber, CancellationToken cancellationToken = default)
        {
            Reads++;
            if (ThrowOnRead)
            {
                throw new InvalidOperationException("store unavailable");
            }
            return Task.FromResult(_cards.TryGetValue(cardNumber, out var card) ? card : null);
        }

        public Task<IReadOnlyList<Card>> ListByAccountIdAsync(string accountId, CancellationToken cancellationToken = default) =>
            throw new NotSupportedException();

        public Task<KeyedPage<Card>> BrowseAsync(string startCardNumber, int pageSize, string? accountIdFilter = null, CancellationToken cancellationToken = default) =>
            throw new NotSupportedException();

        public Task<IReadOnlyList<Card>> BrowseForwardAsync(string startCardNumber, int maxRows, string? accountIdFilter, string? cardNumberFilter, CancellationToken cancellationToken = default) =>
            throw new NotSupportedException();

        public Task<IReadOnlyList<Card>> BrowseBackwardAsync(string beforeCardNumber, int maxRows, string? accountIdFilter, string? cardNumberFilter, CancellationToken cancellationToken = default) =>
            throw new NotSupportedException();

        public Task<Card?> ReadNextAsync(string afterCardNumber, CancellationToken cancellationToken = default) =>
            throw new NotSupportedException();

        public Task<CardRewriteOutcome> RewriteAsync(string cardNumber, Func<Card, bool> rewrite, CancellationToken cancellationToken = default) =>
            throw new NotSupportedException();
    }

    private readonly InMemoryCardRepository _repository = new();
    private readonly CardViewService _service;

    public CardViewServiceTests()
    {
        _repository.Add(new Card
        {
            CardNumber = SeedCard,
            AccountId = SeedAccount,
            CvvCode = "747",
            EmbossedName = "Aniya Von",
            ExpirationDate = new DateOnly(2023, 3, 9),
            ActiveStatus = "Y"
        });
        _service = new CardViewService(_repository);
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData("*")]
    public async Task AccountBlank_PromptsForAccount_AndSkipsRead(string? accountId)
    {
        // FR-S05-02
        var result = await _service.ViewAsync(new CardViewRequest(accountId, SeedCard));

        result.Outcome.Should().Be(CardViewOutcome.InputError);
        result.ErrorMessage.Should().Be("Account number not provided");
        result.InfoMessage.Should().Be("Please enter Account and Card Number");
        result.AccountFilter.Should().Be(CardViewFilterState.Blank);
        result.AccountId.Should().Be("*");
        result.CardFilter.Should().Be(CardViewFilterState.Valid);
        result.CardNumber.Should().Be(SeedCard);
        result.Cursor.Should().Be(CardViewCursorField.Account);
        result.Card.Should().BeNull();
        _repository.Reads.Should().Be(0);
    }

    [Theory]
    [InlineData("12345")]
    [InlineData("ABCDEFGHIJK")]
    [InlineData("0000000005A")]
    [InlineData("123456789012")]
    public async Task AccountNotNumeric_ShowsFilterMessage_AndClearsAccount(string accountId)
    {
        // FR-S05-03
        var result = await _service.ViewAsync(new CardViewRequest(accountId, SeedCard));

        result.Outcome.Should().Be(CardViewOutcome.InputError);
        result.ErrorMessage.Should().Be("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER");
        result.AccountFilter.Should().Be(CardViewFilterState.NotOk);
        result.AccountId.Should().BeEmpty();
        result.CardFilter.Should().Be(CardViewFilterState.Valid);
        result.Cursor.Should().Be(CardViewCursorField.Account);
        _repository.Reads.Should().Be(0);
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("*")]
    public async Task CardBlank_PromptsForCard_WithCursorOnCard(string? cardNumber)
    {
        // FR-S05-04
        var result = await _service.ViewAsync(new CardViewRequest(SeedAccount, cardNumber));

        result.Outcome.Should().Be(CardViewOutcome.InputError);
        result.ErrorMessage.Should().Be("Card number not provided");
        result.AccountFilter.Should().Be(CardViewFilterState.Valid);
        result.AccountId.Should().Be(SeedAccount);
        result.CardFilter.Should().Be(CardViewFilterState.Blank);
        result.CardNumber.Should().Be("*");
        result.Cursor.Should().Be(CardViewCursorField.Card);
        _repository.Reads.Should().Be(0);
    }

    [Theory]
    [InlineData("1234")]
    [InlineData("05000244537657AB")]
    [InlineData("05000244537657401")]
    public async Task CardNotNumeric_ShowsFilterMessage_AndClearsCard(string cardNumber)
    {
        // FR-S05-05
        var result = await _service.ViewAsync(new CardViewRequest(SeedAccount, cardNumber));

        result.Outcome.Should().Be(CardViewOutcome.InputError);
        result.ErrorMessage.Should().Be("CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER");
        result.CardFilter.Should().Be(CardViewFilterState.NotOk);
        result.CardNumber.Should().BeEmpty();
        result.AccountFilter.Should().Be(CardViewFilterState.Valid);
        result.Cursor.Should().Be(CardViewCursorField.Card);
        _repository.Reads.Should().Be(0);
    }

    [Theory]
    [InlineData(null, null)]
    [InlineData("", "")]
    [InlineData("*", "*")]
    [InlineData("00000000000", "0000000000000000")]
    public async Task BothBlank_ReportsNoInputReceived(string? accountId, string? cardNumber)
    {
        // FR-S05-06
        var result = await _service.ViewAsync(new CardViewRequest(accountId, cardNumber));

        result.Outcome.Should().Be(CardViewOutcome.InputError);
        result.ErrorMessage.Should().Be("No input received");
        result.AccountFilter.Should().Be(CardViewFilterState.Blank);
        result.CardFilter.Should().Be(CardViewFilterState.Blank);
        result.AccountId.Should().Be("*");
        result.CardNumber.Should().Be("*");
        result.Cursor.Should().Be(CardViewCursorField.Account);
        _repository.Reads.Should().Be(0);
    }

    [Fact]
    public async Task AccountMessageWins_WhenAccountInvalidAndCardBlank()
    {
        // FR-S05-07
        var result = await _service.ViewAsync(new CardViewRequest("ABC", ""));

        result.ErrorMessage.Should().Be("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER");
        result.AccountFilter.Should().Be(CardViewFilterState.NotOk);
        result.CardFilter.Should().Be(CardViewFilterState.Blank);
        result.CardNumber.Should().Be("*");
        result.Cursor.Should().Be(CardViewCursorField.Account);
    }

    [Fact]
    public async Task AccountMessageWins_WhenAccountBlankAndCardInvalid()
    {
        // FR-S05-07
        var result = await _service.ViewAsync(new CardViewRequest("", "12"));

        result.ErrorMessage.Should().Be("Account number not provided");
        result.AccountFilter.Should().Be(CardViewFilterState.Blank);
        result.CardFilter.Should().Be(CardViewFilterState.NotOk);
        result.CardNumber.Should().BeEmpty();
        result.Cursor.Should().Be(CardViewCursorField.Account);
    }

    [Fact]
    public async Task AccountMessageWins_WhenBothInvalid()
    {
        // FR-S05-07
        var result = await _service.ViewAsync(new CardViewRequest("ABC", "12"));

        result.ErrorMessage.Should().Be("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER");
        result.AccountFilter.Should().Be(CardViewFilterState.NotOk);
        result.CardFilter.Should().Be(CardViewFilterState.NotOk);
    }

    [Theory]
    [InlineData("00000000000")]
    [InlineData("0")]
    public async Task ZeroValue_Account_IsTreatedAsNotProvided(string accountId)
    {
        // FR-S05-08
        var result = await _service.ViewAsync(new CardViewRequest(accountId, SeedCard));

        result.ErrorMessage.Should().Be("Account number not provided");
        result.AccountFilter.Should().Be(CardViewFilterState.Blank);
    }

    [Theory]
    [InlineData("0000000000000000")]
    [InlineData("00")]
    public async Task ZeroValue_Card_IsTreatedAsNotProvided(string cardNumber)
    {
        // FR-S05-08
        var result = await _service.ViewAsync(new CardViewRequest(SeedAccount, cardNumber));

        result.ErrorMessage.Should().Be("Card number not provided");
        result.CardFilter.Should().Be(CardViewFilterState.Blank);
    }

    [Fact]
    public async Task CardFound_DisplaysDetails_AndInfoMessage()
    {
        // FR-S05-09
        var result = await _service.ViewAsync(new CardViewRequest(SeedAccount, SeedCard));

        result.Outcome.Should().Be(CardViewOutcome.Found);
        result.IsFound.Should().BeTrue();
        result.ErrorMessage.Should().BeEmpty();
        result.InfoMessage.Should().Be("   Displaying requested details");
        result.AccountId.Should().Be(SeedAccount);
        result.CardNumber.Should().Be(SeedCard);
        result.AccountFilter.Should().Be(CardViewFilterState.Valid);
        result.CardFilter.Should().Be(CardViewFilterState.Valid);
        result.Cursor.Should().Be(CardViewCursorField.Account);
        result.Card.Should().Be(new CardViewDetails("Aniya Von", "03", "2023", "Y"));
    }

    [Fact]
    public async Task CardFound_WithoutExpirationDate_LeavesExpiryBlank()
    {
        // FR-S05-09 (legacy CARD-EXPIRAION-DATE unparseable)
        _repository.Add(new Card
        {
            CardNumber = "1111111111111111",
            AccountId = "00000000001",
            CvvCode = "000",
            EmbossedName = "No Date",
            ExpirationDate = null,
            ActiveStatus = "N"
        });

        var result = await _service.ViewAsync(new CardViewRequest("00000000001", "1111111111111111"));

        result.Card.Should().Be(new CardViewDetails("No Date", "", "", "N"));
    }

    [Fact]
    public async Task CardNotFound_FlagsBothFields_AndKeepsValues()
    {
        // FR-S05-10
        var result = await _service.ViewAsync(new CardViewRequest(SeedAccount, "9999999999999999"));

        result.Outcome.Should().Be(CardViewOutcome.NotFound);
        result.ErrorMessage.Should().Be("Did not find cards for this search condition");
        result.InfoMessage.Should().Be("Please enter Account and Card Number");
        result.AccountFilter.Should().Be(CardViewFilterState.NotOk);
        result.CardFilter.Should().Be(CardViewFilterState.NotOk);
        result.AccountId.Should().Be(SeedAccount);
        result.CardNumber.Should().Be("9999999999999999");
        result.Cursor.Should().Be(CardViewCursorField.Account);
        result.Card.Should().BeNull();
    }

    [Fact]
    public async Task StoreError_ReportsFileErrorFrame_AndFlagsAccount()
    {
        // FR-S05-11
        _repository.ThrowOnRead = true;

        var result = await _service.ViewAsync(new CardViewRequest(SeedAccount, SeedCard));

        result.Outcome.Should().Be(CardViewOutcome.StoreError);
        result.ErrorMessage.Should().StartWith("File Error: READ     on CARDDAT   returned RESP ");
        result.ErrorMessage.Should().MatchRegex(@"^File Error: READ {5}on CARDDAT {3}returned RESP \d{9,10} *,RESP2 000000000$");
        result.ErrorMessage.Length.Should().BeLessThanOrEqualTo(75);
        result.AccountFilter.Should().Be(CardViewFilterState.NotOk);
        result.CardFilter.Should().Be(CardViewFilterState.Valid);
        result.Cursor.Should().Be(CardViewCursorField.Account);
        result.Card.Should().BeNull();
    }

    [Fact]
    public void FileErrorMessage_FollowsTheCobolFrame()
    {
        // FR-S05-11 (WS-FILE-ERROR-MESSAGE, COCRDSLC.cbl:102-121)
        CardViewService.FileErrorMessage(13, 80).Should().Be(
            "File Error: READ     on CARDDAT   returned RESP 000000013 ,RESP2 000000080");
    }

    [Fact]
    public async Task AccountNotCrossChecked_CardOfAnotherAccountIsDisplayed()
    {
        // FR-S05-12
        var result = await _service.ViewAsync(new CardViewRequest("00000000001", SeedCard));

        result.Outcome.Should().Be(CardViewOutcome.Found);
        result.AccountId.Should().Be("00000000001");
        result.Card!.EmbossedName.Should().Be("Aniya Von");
    }

    [Fact]
    public async Task FromCardList_SkipsEdits_AndReadsImmediately()
    {
        // FR-S05-13
        var result = await _service.ViewAsync(new CardViewRequest("50", "500024453765740", FromCardList: true));

        result.Outcome.Should().Be(CardViewOutcome.Found);
        result.AccountId.Should().Be(SeedAccount);
        result.CardNumber.Should().Be(SeedCard);
        result.Card!.EmbossedName.Should().Be("Aniya Von");
        _repository.Reads.Should().Be(1);
    }

    [Fact]
    public async Task FromCardList_UnknownCard_ReportsNotFound()
    {
        // FR-S05-13 + FR-S05-10
        var result = await _service.ViewAsync(new CardViewRequest(SeedAccount, "9999999999999999", FromCardList: true));

        result.Outcome.Should().Be(CardViewOutcome.NotFound);
        result.ErrorMessage.Should().Be("Did not find cards for this search condition");
        result.AccountFilter.Should().Be(CardViewFilterState.NotOk);
        result.CardFilter.Should().Be(CardViewFilterState.NotOk);
    }
}
