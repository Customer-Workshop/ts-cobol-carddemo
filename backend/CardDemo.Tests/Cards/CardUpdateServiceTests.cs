using CardDemo.Application.Cards;
using CardDemo.Domain.Cards;
using FluentAssertions;

namespace CardDemo.Tests.Cards;

/// <summary>
/// Parity tests for COCRDUPC (app/cbl/COCRDUPC.cbl) over a fake CARDDAT: FR-S06-01..27.
/// </summary>
public class CardUpdateServiceTests
{
    private const string Account = "00000000050";
    private const string CardNumber = "0500024453765740";

    private readonly FakeCardUpdateRepository _repository = new();
    private readonly CardUpdateService _service;

    public CardUpdateServiceTests()
    {
        _repository.Cards[CardNumber] = new Card
        {
            CardNumber = CardNumber,
            AccountId = Account,
            CvvCode = "747",
            EmbossedName = "Aniya Von",
            ExpirationDate = new DateOnly(2023, 3, 9),
            ActiveStatus = "Y"
        };
        _service = new CardUpdateService(_repository);
    }

    private static CardUpdateDetails FetchedOriginal() =>
        new(Account, CardNumber, "ANIYA VON", "2023", "03", "09", "Y");

    private static CardUpdateRequest Search(string? account, string? card, CardUpdateAid aid = CardUpdateAid.Enter) =>
        new(aid, CardUpdateState.DetailsNotFetched, account, card, null, null);

    private static CardUpdateRequest Edit(
        string? name = "ANIYA VON",
        string? status = "Y",
        string? month = "03",
        string? year = "2023",
        CardUpdateAid aid = CardUpdateAid.Enter,
        CardUpdateState state = CardUpdateState.ShowDetails,
        CardUpdateDetails? original = null) =>
        new(aid, state, Account, CardNumber, original ?? FetchedOriginal(), new CardUpdateInput(name, status, month, year));

    [Fact]
    public void FreshScreen_PromptsForSearchKeysWithDetailsProtected()
    {
        // FR-S06-01
        var screen = CardUpdateService.FreshScreen();

        screen.State.Should().Be(CardUpdateState.DetailsNotFetched);
        screen.InfoMessage.Should().Be("Please enter Account and Card Number");
        screen.ErrorMessage.Should().BeNull();
        screen.SearchEditable.Should().BeTrue();
        screen.DetailsEditable.Should().BeFalse();
        screen.ConfirmKeysVisible.Should().BeFalse();
        screen.CursorField.Should().Be(CardUpdateField.AccountId);
    }

    [Theory]
    [InlineData(CardUpdateAid.Pf5, CardUpdateState.DetailsNotFetched, CardUpdateAid.Enter)]
    [InlineData(CardUpdateAid.Pf5, CardUpdateState.ShowDetails, CardUpdateAid.Enter)]
    [InlineData(CardUpdateAid.Pf5, CardUpdateState.ChangesNotOk, CardUpdateAid.Enter)]
    [InlineData(CardUpdateAid.Pf5, CardUpdateState.ChangesOkNotConfirmed, CardUpdateAid.Pf5)]
    [InlineData(CardUpdateAid.Pf12, CardUpdateState.DetailsNotFetched, CardUpdateAid.Enter)]
    [InlineData(CardUpdateAid.Pf12, CardUpdateState.ShowDetails, CardUpdateAid.Pf12)]
    [InlineData(CardUpdateAid.Enter, CardUpdateState.ChangesOkNotConfirmed, CardUpdateAid.Enter)]
    public void NormaliseAid_RemapsOutOfWindowKeysToEnter(CardUpdateAid aid, CardUpdateState state, CardUpdateAid expected)
    {
        // FR-S06-02
        CardUpdateService.NormaliseAid(aid, state).Should().Be(expected);
    }

    [Fact]
    public async Task Pf5BeforeFetch_BehavesLikeEnter()
    {
        // FR-S06-02
        var screen = await _service.ProcessAsync(Search(Account, CardNumber, CardUpdateAid.Pf5));

        screen.State.Should().Be(CardUpdateState.ShowDetails);
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData("*")]
    [InlineData("00000000000")]
    public async Task BlankAccount_IsRejected(string? account)
    {
        // FR-S06-04
        var screen = await _service.ProcessAsync(Search(account, CardNumber));

        screen.ErrorMessage.Should().Be("Account number not provided");
        screen.State.Should().Be(CardUpdateState.DetailsNotFetched);
        screen.FieldsInError.Should().Equal(CardUpdateField.AccountId);
        screen.CursorField.Should().Be(CardUpdateField.AccountId);
        screen.AccountId.Should().Be("*");
        screen.CardNumber.Should().Be(CardNumber);
    }

    [Theory]
    [InlineData("123")]
    [InlineData("1234567890A")]
    [InlineData("123456789012")]
    public async Task NonNumericOrShortAccount_IsRejected(string account)
    {
        // FR-S06-05
        var screen = await _service.ProcessAsync(Search(account, CardNumber));

        screen.ErrorMessage.Should().Be("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER");
        screen.FieldsInError.Should().Equal(CardUpdateField.AccountId);
        screen.AccountId.Should().Be(account);
    }

    [Theory]
    [InlineData("")]
    [InlineData("*")]
    [InlineData("0000000000000000")]
    public async Task BlankCard_IsRejected(string card)
    {
        // FR-S06-06
        var screen = await _service.ProcessAsync(Search(Account, card));

        screen.ErrorMessage.Should().Be("Card number not provided");
        screen.FieldsInError.Should().Equal(CardUpdateField.CardNumber);
        screen.CursorField.Should().Be(CardUpdateField.CardNumber);
        screen.CardNumber.Should().Be("*");
    }

    [Theory]
    [InlineData("12345")]
    [InlineData("050002445376574X")]
    public async Task NonNumericCard_IsRejected(string card)
    {
        // FR-S06-07
        var screen = await _service.ProcessAsync(Search(Account, card));

        screen.ErrorMessage.Should().Be("CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER");
        screen.FieldsInError.Should().Equal(CardUpdateField.CardNumber);
    }

    [Fact]
    public async Task BothSearchFieldsBlank_ReportsNoInputReceived()
    {
        // FR-S06-08
        var screen = await _service.ProcessAsync(Search("", ""));

        screen.ErrorMessage.Should().Be("No input received");
        screen.FieldsInError.Should().Equal(CardUpdateField.AccountId, CardUpdateField.CardNumber);
        screen.AccountId.Should().Be("*");
        screen.CardNumber.Should().Be("*");
    }

    [Fact]
    public async Task BothSearchFieldsInvalid_ShowsAccountMessageAndFlagsBoth()
    {
        // FR-S06-09
        var screen = await _service.ProcessAsync(Search("ABC", ""));

        screen.ErrorMessage.Should().Be("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER");
        screen.FieldsInError.Should().Equal(CardUpdateField.AccountId, CardUpdateField.CardNumber);
        screen.CursorField.Should().Be(CardUpdateField.AccountId);
    }

    [Fact]
    public async Task ValidKeys_FetchCardAndShowDetails()
    {
        // FR-S06-10
        var screen = await _service.ProcessAsync(Search(Account, CardNumber));

        screen.State.Should().Be(CardUpdateState.ShowDetails);
        screen.InfoMessage.Should().Be("Details of selected card shown above");
        screen.ErrorMessage.Should().BeNull();
        screen.EmbossedName.Should().Be("ANIYA VON");
        screen.ActiveStatus.Should().Be("Y");
        screen.ExpiryYear.Should().Be("2023");
        screen.ExpiryMonth.Should().Be("03");
        screen.ExpiryDay.Should().Be("09");
        screen.Original.Should().Be(FetchedOriginal());
        screen.SearchEditable.Should().BeFalse();
        screen.DetailsEditable.Should().BeTrue();
        screen.ConfirmKeysVisible.Should().BeFalse();
        screen.CursorField.Should().Be(CardUpdateField.EmbossedName);
    }

    [Fact]
    public async Task AccountIsNotMatchedAgainstTheCard()
    {
        // FR-S06-10 (account only format-checked, COCRDUPC.cbl:1379-1380)
        var screen = await _service.ProcessAsync(Search("99999999999", CardNumber));

        screen.State.Should().Be(CardUpdateState.ShowDetails);
        screen.AccountId.Should().Be("99999999999");
    }

    [Fact]
    public async Task UnknownCard_IsNotFound()
    {
        // FR-S06-11
        var screen = await _service.ProcessAsync(Search(Account, "9999999999999999"));

        screen.State.Should().Be(CardUpdateState.DetailsNotFetched);
        screen.ErrorMessage.Should().Be("Did not find cards for this search condition");
        screen.FieldsInError.Should().Equal(CardUpdateField.AccountId, CardUpdateField.CardNumber);
        screen.CardNumber.Should().Be("9999999999999999");
    }

    [Fact]
    public async Task StoreFailureOnRead_ReportsFileErrorTemplate()
    {
        // FR-S06-12
        _repository.FailReads = true;

        var screen = await _service.ProcessAsync(Search(Account, CardNumber));

        screen.ErrorMessage.Should().Be("File Error: READ     on CARDDAT   returned RESP 000000017 ,RESP2 000000000");
        screen.FieldsInError.Should().Equal(CardUpdateField.AccountId, CardUpdateField.CardNumber);
    }

    [Theory]
    [InlineData("ANIYA VON")]
    [InlineData("aniya von")]
    [InlineData("  Aniya Von  ")]
    public async Task UnchangedValues_ReportNoChangeDetected(string name)
    {
        // FR-S06-13
        var screen = await _service.ProcessAsync(Edit(name: name));

        screen.State.Should().Be(CardUpdateState.ShowDetails);
        screen.ErrorMessage.Should().Be("No change detected with respect to values fetched.");
        screen.InfoMessage.Should().Be("Details of selected card shown above");
        screen.EmbossedName.Should().Be("ANIYA VON");
        screen.FieldsInError.Should().BeEmpty();
        screen.CursorField.Should().Be(CardUpdateField.EmbossedName);
        screen.DetailsEditable.Should().BeTrue();
    }

    [Fact]
    public async Task SingleDigitMonthIsZeroFilledBeforeTheNoChangeCompare()
    {
        // FR-S06-13 + FR-S06-17 (BMS JUSTIFY=RIGHT zero fill)
        var screen = await _service.ProcessAsync(Edit(month: "3"));

        screen.ErrorMessage.Should().Be("No change detected with respect to values fetched.");
    }

    [Theory]
    [InlineData("")]
    [InlineData("*")]
    [InlineData(null)]
    public async Task BlankName_IsRejectedAndShownAsStar(string? name)
    {
        // FR-S06-14
        var screen = await _service.ProcessAsync(Edit(name: name));

        screen.State.Should().Be(CardUpdateState.ChangesNotOk);
        screen.ErrorMessage.Should().Be("Card name not provided");
        screen.InfoMessage.Should().Be("Update card details presented above.");
        screen.FieldsInError.Should().Equal(CardUpdateField.EmbossedName);
        screen.EmbossedName.Should().Be("*");
        screen.CursorField.Should().Be(CardUpdateField.EmbossedName);
    }

    [Theory]
    [InlineData("ANIYA V0N")]
    [InlineData("ANIYA-VON")]
    [InlineData("O'NEIL")]
    public async Task NonAlphabeticName_IsRejected(string name)
    {
        // FR-S06-15
        var screen = await _service.ProcessAsync(Edit(name: name));

        screen.ErrorMessage.Should().Be("Card name can only contain alphabets and spaces");
        screen.FieldsInError.Should().Equal(CardUpdateField.EmbossedName);
        screen.EmbossedName.Should().Be(name);
    }

    [Theory]
    [InlineData("")]
    [InlineData("X")]
    [InlineData("0")]
    public async Task InvalidStatus_IsRejected(string status)
    {
        // FR-S06-16
        var screen = await _service.ProcessAsync(Edit(status: status));

        screen.ErrorMessage.Should().Be("Card Active Status must be Y or N");
        screen.FieldsInError.Should().Equal(CardUpdateField.ActiveStatus);
        screen.CursorField.Should().Be(CardUpdateField.ActiveStatus);
    }

    [Fact]
    public async Task LowercaseStatus_IsNoChangeWhenNothingElseChanged_ButRejectedOtherwise()
    {
        // FR-S06-13 vs FR-S06-16: the change compare is case-insensitive, FLG-YES-NO-VALID is not
        (await _service.ProcessAsync(Edit(status: "y"))).ErrorMessage
            .Should().Be("No change detected with respect to values fetched.");

        var screen = await _service.ProcessAsync(Edit(name: "NEW NAME", status: "y"));
        screen.ErrorMessage.Should().Be("Card Active Status must be Y or N");
        screen.FieldsInError.Should().Equal(CardUpdateField.ActiveStatus);
    }

    [Theory]
    [InlineData("")]
    [InlineData("00")]
    [InlineData("13")]
    [InlineData("AB")]
    [InlineData("0")]
    public async Task InvalidMonth_IsRejected(string month)
    {
        // FR-S06-17
        var screen = await _service.ProcessAsync(Edit(month: month));

        screen.ErrorMessage.Should().Be("Card expiry month must be between 1 and 12");
        screen.FieldsInError.Should().Equal(CardUpdateField.ExpiryMonth);
    }

    [Theory]
    [InlineData("")]
    [InlineData("1949")]
    [InlineData("2100")]
    [InlineData("25")]
    [InlineData("20XX")]
    public async Task InvalidYear_IsRejected(string year)
    {
        // FR-S06-18
        var screen = await _service.ProcessAsync(Edit(year: year));

        screen.ErrorMessage.Should().Be("Invalid card expiry year");
        screen.FieldsInError.Should().Equal(CardUpdateField.ExpiryYear);
    }

    [Fact]
    public async Task MultipleInvalidFields_ShowFirstMessageAndFlagAll()
    {
        // FR-S06-19
        var screen = await _service.ProcessAsync(Edit(name: "", status: "Q", month: "99", year: "1"));

        screen.State.Should().Be(CardUpdateState.ChangesNotOk);
        screen.ErrorMessage.Should().Be("Card name not provided");
        screen.FieldsInError.Should().Equal(
            CardUpdateField.EmbossedName, CardUpdateField.ActiveStatus, CardUpdateField.ExpiryMonth, CardUpdateField.ExpiryYear);
        screen.CursorField.Should().Be(CardUpdateField.EmbossedName);
        screen.ActiveStatus.Should().Be("Q");
        screen.ExpiryMonth.Should().Be("99");
        screen.ExpiryYear.Should().Be("0001");
        screen.ExpiryDay.Should().Be("09");
        screen.DetailsEditable.Should().BeTrue();
        screen.SearchEditable.Should().BeFalse();
    }

    [Fact]
    public async Task StatusThenYearInvalid_ShowsStatusMessageFirst()
    {
        // FR-S06-19 ordering name -> status -> month -> year
        var screen = await _service.ProcessAsync(Edit(status: "", year: "1900"));

        screen.ErrorMessage.Should().Be("Card Active Status must be Y or N");
        screen.FieldsInError.Should().Equal(CardUpdateField.ActiveStatus, CardUpdateField.ExpiryYear);
        screen.ActiveStatus.Should().Be("*");
    }

    [Fact]
    public async Task ValidChanges_AwaitConfirmation()
    {
        // FR-S06-20
        var screen = await _service.ProcessAsync(Edit(name: "Aniya Von Smith", status: "N", month: "12", year: "2030"));

        screen.State.Should().Be(CardUpdateState.ChangesOkNotConfirmed);
        screen.InfoMessage.Should().Be("Changes validated.Press F5 to save");
        screen.ErrorMessage.Should().BeNull();
        screen.EmbossedName.Should().Be("Aniya Von Smith");
        screen.ActiveStatus.Should().Be("N");
        screen.ExpiryMonth.Should().Be("12");
        screen.ExpiryYear.Should().Be("2030");
        screen.ExpiryDay.Should().Be("09");
        screen.SearchEditable.Should().BeFalse();
        screen.DetailsEditable.Should().BeFalse();
        screen.ConfirmKeysVisible.Should().BeTrue();
        _repository.Cards[CardNumber].EmbossedName.Should().Be("Aniya Von");
    }

    [Fact]
    public async Task EnterWhileAwaitingConfirmation_RedisplaysConfirmation()
    {
        // FR-S06-21
        var screen = await _service.ProcessAsync(Edit(name: "NEW NAME", state: CardUpdateState.ChangesOkNotConfirmed));

        screen.State.Should().Be(CardUpdateState.ChangesOkNotConfirmed);
        screen.InfoMessage.Should().Be("Changes validated.Press F5 to save");
        screen.EmbossedName.Should().Be("NEW NAME");
        _repository.Cards[CardNumber].EmbossedName.Should().Be("Aniya Von");
    }

    [Fact]
    public async Task Pf5AfterConfirmation_RewritesTheCard()
    {
        // FR-S06-22 (+ deviation D1: CVV and account preserved)
        var screen = await _service.ProcessAsync(
            Edit(name: "New Name", status: "N", month: "12", year: "2030", aid: CardUpdateAid.Pf5, state: CardUpdateState.ChangesOkNotConfirmed));

        screen.State.Should().Be(CardUpdateState.ChangesDone);
        screen.InfoMessage.Should().Be("Changes committed to database");
        screen.ErrorMessage.Should().BeNull();
        screen.EmbossedName.Should().Be("New Name");
        screen.SearchEditable.Should().BeFalse();
        screen.DetailsEditable.Should().BeFalse();
        screen.ConfirmKeysVisible.Should().BeFalse();

        var stored = _repository.Cards[CardNumber];
        stored.EmbossedName.Should().Be("New Name");
        stored.ActiveStatus.Should().Be("N");
        stored.ExpirationDate.Should().Be(new DateOnly(2030, 12, 9));
        stored.CvvCode.Should().Be("747");
        stored.AccountId.Should().Be(Account);
    }

    [Fact]
    public async Task Pf5WhenRecordChangedByAnotherWriter_ReviewsFreshImage()
    {
        // FR-S06-23
        _repository.BeforeLock = card => card.ActiveStatus = "N";

        var screen = await _service.ProcessAsync(
            Edit(name: "New Name", aid: CardUpdateAid.Pf5, state: CardUpdateState.ChangesOkNotConfirmed));

        screen.State.Should().Be(CardUpdateState.ShowDetails);
        screen.ErrorMessage.Should().Be("Record changed by some one else. Please review");
        screen.InfoMessage.Should().Be("Details of selected card shown above");
        screen.ActiveStatus.Should().Be("N");
        screen.EmbossedName.Should().Be("ANIYA VON");
        screen.Original!.ActiveStatus.Should().Be("N");
        _repository.Cards[CardNumber].EmbossedName.Should().Be("Aniya Von");
    }

    [Fact]
    public async Task Pf5WhenRecordVanished_CannotLock()
    {
        // FR-S06-24
        _repository.Cards.Clear();

        var screen = await _service.ProcessAsync(
            Edit(name: "New Name", aid: CardUpdateAid.Pf5, state: CardUpdateState.ChangesOkNotConfirmed));

        screen.State.Should().Be(CardUpdateState.ChangesFailed);
        screen.ErrorMessage.Should().Be("Could not lock record for update");
        screen.InfoMessage.Should().Be("Changes unsuccessful. Please try again");
        screen.EmbossedName.Should().Be("New Name");
        screen.SearchEditable.Should().BeTrue();
        screen.DetailsEditable.Should().BeFalse();
    }

    [Fact]
    public async Task Pf5WhenLockFails_CannotLock()
    {
        // FR-S06-24
        _repository.FailLock = true;

        var screen = await _service.ProcessAsync(
            Edit(name: "New Name", aid: CardUpdateAid.Pf5, state: CardUpdateState.ChangesOkNotConfirmed));

        screen.State.Should().Be(CardUpdateState.ChangesFailed);
        screen.ErrorMessage.Should().Be("Could not lock record for update");
    }

    [Fact]
    public async Task Pf5WhenRewriteFails_ReportsUpdateFailed()
    {
        // FR-S06-25
        _repository.FailRewrite = true;

        var screen = await _service.ProcessAsync(
            Edit(name: "New Name", aid: CardUpdateAid.Pf5, state: CardUpdateState.ChangesOkNotConfirmed));

        screen.State.Should().Be(CardUpdateState.ChangesFailed);
        screen.ErrorMessage.Should().Be("Update of record failed");
        screen.InfoMessage.Should().Be("Changes unsuccessful. Please try again");
    }

    [Fact]
    public async Task Pf5WithNonCalendarDate_ReportsUpdateFailed()
    {
        // FR-S06-25 / deviation D3: day 31 carried into February
        _repository.Cards[CardNumber].ExpirationDate = new DateOnly(2023, 3, 31);
        var original = FetchedOriginal() with { ExpiryDay = "31" };

        var screen = await _service.ProcessAsync(
            Edit(month: "02", aid: CardUpdateAid.Pf5, state: CardUpdateState.ChangesOkNotConfirmed, original: original));

        screen.State.Should().Be(CardUpdateState.ChangesFailed);
        screen.ErrorMessage.Should().Be("Update of record failed");
        _repository.Cards[CardNumber].ExpirationDate.Should().Be(new DateOnly(2023, 3, 31));
    }

    [Theory]
    [InlineData(CardUpdateState.ChangesDone)]
    [InlineData(CardUpdateState.ChangesFailed)]
    public async Task EnterAfterCompletion_ResetsToFreshScreen(CardUpdateState state)
    {
        // FR-S06-26
        var screen = await _service.ProcessAsync(Edit(name: "New Name", state: state));

        screen.Should().Be(CardUpdateService.FreshScreen());
    }

    [Fact]
    public async Task Pf12_RefetchesAndKeepsTheEditMessage()
    {
        // FR-S06-27
        var screen = await _service.ProcessAsync(Edit(name: "BAD1", aid: CardUpdateAid.Pf12));

        screen.State.Should().Be(CardUpdateState.ShowDetails);
        screen.EmbossedName.Should().Be("ANIYA VON");
        screen.ErrorMessage.Should().Be("Card name can only contain alphabets and spaces");
        screen.InfoMessage.Should().Be("Details of selected card shown above");
        screen.FieldsInError.Should().BeEmpty();
        screen.DetailsEditable.Should().BeTrue();
    }

    [Fact]
    public async Task Pf12FromConfirmation_DiscardsValidatedChanges()
    {
        // FR-S06-27
        _repository.Cards[CardNumber].EmbossedName = "Changed Elsewhere";

        var screen = await _service.ProcessAsync(
            Edit(name: "New Name", aid: CardUpdateAid.Pf12, state: CardUpdateState.ChangesOkNotConfirmed));

        screen.State.Should().Be(CardUpdateState.ShowDetails);
        screen.ErrorMessage.Should().BeNull();
        screen.EmbossedName.Should().Be("CHANGED ELSEWHERE");
        screen.Original!.EmbossedName.Should().Be("CHANGED ELSEWHERE");
    }
}
