using CardDemo.Application.BillPayment;
using CardDemo.Application.Cards;
using CardDemo.Domain.Accounts;
using CardDemo.Domain.Cards;
using CardDemo.Domain.Transactions;
using FluentAssertions;

namespace CardDemo.Tests.BillPayment;

/// <summary>
/// Parity tests for COBIL00C PROCESS-ENTER-KEY (app/cbl/COBIL00C.cbl:154-244) over in-memory fakes:
/// validation order, exact messages, cursor placement, balance edit, TRAN-ID allocation,
/// transaction content and RESP mapping (FR-S11-01..15).
/// </summary>
public class BillPaymentServiceTests
{
    private const string AccountId = "00000000010";
    private static readonly DateTimeOffset Now = new(2026, 9, 2, 14, 30, 45, 123, TimeSpan.Zero);

    private readonly FakeBillPaymentRepository _repository = new();
    private readonly FakeCardXrefRepository _xrefs = new();
    private readonly BillPaymentService _service;

    public BillPaymentServiceTests()
    {
        _service = new BillPaymentService(_repository, _xrefs, new FixedTimeProvider(Now));
    }

    private static Account NewAccount(decimal balance) => new()
    {
        AccountId = AccountId,
        ActiveStatus = "Y",
        CurrentBalance = balance,
        AddressZip = "12345",
        GroupId = "DEFAULT"
    };

    private void SeedAccount(decimal balance, string? cardNumber = "4111111111111111")
    {
        _repository.Accounts[AccountId] = NewAccount(balance);
        if (cardNumber is not null)
        {
            _xrefs.Xrefs.Add(new CardXref { CardNumber = cardNumber, CustomerId = "000000001", AccountId = AccountId });
        }
    }

    private Task<BillPaymentResult> PayAsync(string? accountId, string? confirm) =>
        _service.PayAsync(new BillPaymentRequest(accountId, confirm));

    // FR-S11-01
    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    public async Task BlankAccountId_IsRejectedBeforeAnythingElse(string? accountId)
    {
        var result = await PayAsync(accountId, "Q");

        result.Outcome.Should().Be(BillPaymentOutcome.AccountIdRequired);
        result.Message.Should().Be("Acct ID can NOT be empty...");
        result.Severity.Should().Be(BillPaymentMessageSeverity.Error);
        result.CursorField.Should().Be(BillPaymentCursorField.AccountId);
        result.CurrentBalance.Should().BeNull();
        _repository.ReadCount.Should().Be(0);
    }

    // FR-S11-02
    [Theory]
    [InlineData("Q")]
    [InlineData("1")]
    [InlineData("YES")]
    public async Task InvalidConfirm_IsRejectedWithoutAccountLookup(string confirm)
    {
        var result = await PayAsync("99999999999", confirm);

        result.Outcome.Should().Be(BillPaymentOutcome.InvalidConfirmation);
        result.Message.Should().Be("Invalid value. Valid values are (Y/N)...");
        result.CursorField.Should().Be(BillPaymentCursorField.Confirm);
        result.CurrentBalance.Should().BeNull("the previously displayed balance stays on screen");
        result.ClearScreen.Should().BeFalse();
        _repository.ReadCount.Should().Be(0);
    }

    // FR-S11-03
    [Theory]
    [InlineData("N")]
    [InlineData("n")]
    public async Task DeclineN_ClearsTheScreenWithoutReadingOrWriting(string confirm)
    {
        SeedAccount(100m);

        var result = await PayAsync(AccountId, confirm);

        result.Outcome.Should().Be(BillPaymentOutcome.Declined);
        result.Message.Should().BeEmpty();
        result.Severity.Should().BeNull();
        result.ClearScreen.Should().BeTrue();
        result.CursorField.Should().Be(BillPaymentCursorField.AccountId);
        _repository.ReadCount.Should().Be(0);
        _repository.Posted.Should().BeEmpty();
    }

    // FR-S11-04
    [Theory]
    [InlineData("")]
    [InlineData("Y")]
    public async Task AccountNotFound_ReportsLegacyMessage(string confirm)
    {
        var result = await PayAsync("99999999999", confirm);

        result.Outcome.Should().Be(BillPaymentOutcome.AccountNotFound);
        result.Message.Should().Be("Account ID NOT found...");
        result.CursorField.Should().Be(BillPaymentCursorField.AccountId);
        result.CurrentBalance.Should().BeNull();
        _repository.Posted.Should().BeEmpty();
    }

    // FR-S11-04 / §6: key is the typed value, no zero padding
    [Fact]
    public async Task AccountId_IsNotNumericallyNormalised()
    {
        SeedAccount(100m);

        var result = await PayAsync("10", "");

        result.Outcome.Should().Be(BillPaymentOutcome.AccountNotFound);
    }

    [Fact]
    public async Task AccountId_IsTrimmedBeforeLookup()
    {
        SeedAccount(100m);

        var result = await PayAsync($" {AccountId} ", "");

        result.Outcome.Should().Be(BillPaymentOutcome.ConfirmationRequired);
    }

    // FR-S11-05
    [Theory]
    [InlineData("")]
    [InlineData("Y")]
    public async Task AccountStoreError_ReportsLegacyMessage(string confirm)
    {
        _repository.FailReads = true;

        var result = await PayAsync(AccountId, confirm);

        result.Outcome.Should().Be(BillPaymentOutcome.AccountLookupError);
        result.Message.Should().Be("Unable to lookup Account...");
        result.CursorField.Should().Be(BillPaymentCursorField.AccountId);
    }

    // FR-S11-06
    [Theory]
    [InlineData("1234.56", "+0000001234.56")]
    [InlineData("0", "+0000000000.00")]
    [InlineData("-50", "-0000000050.00")]
    [InlineData("9999999999.99", "+9999999999.99")]
    [InlineData("0.129", "+0000000000.12")]
    public void BalanceFormat_MatchesPicPlus10Dot2Edit(string balance, string expected)
    {
        BillPaymentService.FormatBalance(decimal.Parse(balance, System.Globalization.CultureInfo.InvariantCulture))
            .Should().Be(expected).And.HaveLength(14);
    }

    // FR-S11-07
    [Theory]
    [InlineData("0", "", "+0000000000.00")]
    [InlineData("-25.10", "", "-0000000025.10")]
    [InlineData("0", "Y", "+0000000000.00")]
    [InlineData("-25.10", "y", "-0000000025.10")]
    public async Task NothingToPay_WhenBalanceNotPositive(string balance, string confirm, string expectedBalance)
    {
        SeedAccount(decimal.Parse(balance, System.Globalization.CultureInfo.InvariantCulture));

        var result = await PayAsync(AccountId, confirm);

        result.Outcome.Should().Be(BillPaymentOutcome.NothingToPay);
        result.Message.Should().Be("You have nothing to pay...");
        result.CursorField.Should().Be(BillPaymentCursorField.AccountId);
        result.CurrentBalance.Should().Be(expectedBalance);
        _repository.Posted.Should().BeEmpty();
        _xrefs.ReadCount.Should().Be(0, "balance guard precedes the xref read");
    }

    // FR-S11-08
    [Theory]
    [InlineData("")]
    [InlineData(null)]
    [InlineData("  ")]
    public async Task BlankConfirm_PromptsForConfirmationWithBalance(string? confirm)
    {
        SeedAccount(1234.56m);

        var result = await PayAsync(AccountId, confirm);

        result.Outcome.Should().Be(BillPaymentOutcome.ConfirmationRequired);
        result.Message.Should().Be("Confirm to make a bill payment...");
        result.CursorField.Should().Be(BillPaymentCursorField.Confirm);
        result.CurrentBalance.Should().Be("+0000001234.56");
        result.ClearScreen.Should().BeFalse();
        _repository.Posted.Should().BeEmpty();
        _repository.LockCount.Should().Be(0);
    }

    // FR-S11-09
    [Fact]
    public async Task XrefNotFound_ReportsAccountNotFoundAndWritesNothing()
    {
        SeedAccount(100m, cardNumber: null);

        var result = await PayAsync(AccountId, "Y");

        result.Outcome.Should().Be(BillPaymentOutcome.CardNotFound);
        result.Message.Should().Be("Account ID NOT found...");
        result.CursorField.Should().Be(BillPaymentCursorField.AccountId);
        result.CurrentBalance.Should().Be("+0000000100.00");
        _repository.Posted.Should().BeEmpty();
        _repository.Released.Should().BeTrue();
    }

    [Fact]
    public async Task XrefStoreError_ReportsLegacyMessage()
    {
        SeedAccount(100m);
        _xrefs.Fail = true;

        var result = await PayAsync(AccountId, "Y");

        result.Outcome.Should().Be(BillPaymentOutcome.CardLookupError);
        result.Message.Should().Be("Unable to lookup XREF AIX file...");
        _repository.Posted.Should().BeEmpty();
    }

    // FR-S11-10
    [Theory]
    [InlineData(null, "0000000000000001")]
    [InlineData("0000000000000123", "0000000000000124")]
    [InlineData("0000000000683580", "0000000000683581")]
    [InlineData("9999999999999999", "0000000000000000")]
    public void TransactionId_IsLastPlusOneIn16Digits(string? last, string expected)
    {
        BillPaymentService.TryAllocateTransactionId(last, out var id).Should().BeTrue();
        id.Should().Be(expected);
    }

    [Fact]
    public void TransactionId_NonNumericLastKeyCannotBeAllocated()
    {
        BillPaymentService.TryAllocateTransactionId("ABC", out _).Should().BeFalse();
    }

    [Fact]
    public async Task TransactionId_UsesHighestExistingKey()
    {
        SeedAccount(100m);
        _repository.LastTransactionId = "0000000000000123";

        var result = await PayAsync(AccountId, "Y");

        result.TransactionId.Should().Be("0000000000000124");
        _repository.Posted.Single().Transaction.TransactionId.Should().Be("0000000000000124");
    }

    [Fact]
    public async Task TransactionBrowseError_ReportsLegacyMessage()
    {
        SeedAccount(100m);
        _repository.FailLastTransactionId = true;

        var result = await PayAsync(AccountId, "Y");

        result.Outcome.Should().Be(BillPaymentOutcome.TransactionLookupError);
        result.Message.Should().Be("Unable to lookup Transaction...");
        _repository.Posted.Should().BeEmpty();
    }

    // FR-S11-11
    [Fact]
    public async Task TransactionContent_MatchesCobil00cConstants()
    {
        SeedAccount(1234.56m, cardNumber: "4111111111111111");

        await PayAsync(AccountId, "y");

        var tran = _repository.Posted.Single().Transaction;
        tran.TransactionId.Should().Be("0000000000000001");
        tran.TypeCode.Should().Be("02");
        tran.CategoryCode.Should().Be("0002");
        tran.Source.Should().Be("POS TERM");
        tran.Description.Should().Be("BILL PAYMENT - ONLINE");
        tran.Amount.Should().Be(1234.56m);
        tran.CardNumber.Should().Be("4111111111111111");
        tran.MerchantId.Should().Be("999999999");
        tran.MerchantName.Should().Be("BILL PAYMENT");
        tran.MerchantCity.Should().Be("N/A");
        tran.MerchantZip.Should().Be("N/A");
        tran.OriginalTimestamp.Should().Be(new DateTime(2026, 9, 2, 14, 30, 45));
        tran.ProcessedTimestamp.Should().Be(tran.OriginalTimestamp);
    }

    [Fact]
    public async Task TransactionContent_UsesFirstCardOfTheAccount()
    {
        SeedAccount(10m, cardNumber: "4999999999999999");
        _xrefs.Xrefs.Add(new CardXref { CardNumber = "4000000000000000", CustomerId = "000000001", AccountId = AccountId });

        await PayAsync(AccountId, "Y");

        _repository.Posted.Single().Transaction.CardNumber.Should().Be("4000000000000000");
    }

    // FR-S11-11 / §6: MOVE S9(10)V99 TO S9(09)V99 truncates the high-order digit
    [Theory]
    [InlineData("1234.56", "1234.56")]
    [InlineData("999999999.99", "999999999.99")]
    [InlineData("1234567890.12", "234567890.12")]
    public void TransactionAmount_TruncatesToNineIntegerDigits(string balance, string expected)
    {
        BillPaymentService.ToTransactionAmount(decimal.Parse(balance, System.Globalization.CultureInfo.InvariantCulture))
            .Should().Be(decimal.Parse(expected, System.Globalization.CultureInfo.InvariantCulture));
    }

    [Fact]
    public async Task Payment_OnHugeBalance_LeavesTruncatedResidualOnAccount()
    {
        SeedAccount(1234567890.12m);

        await PayAsync(AccountId, "Y");

        var posted = _repository.Posted.Single();
        posted.Transaction.Amount.Should().Be(234567890.12m);
        posted.Account.CurrentBalance.Should().Be(1000000000.00m);
    }

    // FR-S11-12 + FR-S11-15
    [Fact]
    public async Task ConfirmedPayment_ZeroesBalanceAndReportsGreenSuccess()
    {
        SeedAccount(1234.56m);

        var result = await PayAsync(AccountId, "Y");

        result.Outcome.Should().Be(BillPaymentOutcome.PaymentSuccessful);
        result.Message.Should().Be("Payment successful.  Your Transaction ID is 0000000000000001.");
        result.Severity.Should().Be(BillPaymentMessageSeverity.Success);
        result.ClearScreen.Should().BeTrue();
        result.CursorField.Should().Be(BillPaymentCursorField.AccountId);
        result.CurrentBalance.Should().BeNull();
        result.TransactionId.Should().Be("0000000000000001");
        var posted = _repository.Posted.Single();
        posted.Account.AccountId.Should().Be(AccountId);
        posted.Account.CurrentBalance.Should().Be(0m);
        _repository.LockCount.Should().Be(1, "confirmed path reads the account for update");
        _repository.Released.Should().BeFalse("the unit of work was committed, not abandoned");
    }

    // FR-S11-13
    [Fact]
    public async Task DuplicateTransactionId_ReportsLegacyMessage()
    {
        SeedAccount(100m);
        _repository.PostOutcome = BillPaymentPostOutcome.DuplicateTransaction;

        var result = await PayAsync(AccountId, "Y");

        result.Outcome.Should().Be(BillPaymentOutcome.DuplicateTransaction);
        result.Message.Should().Be("Tran ID already exist...");
        result.CursorField.Should().Be(BillPaymentCursorField.AccountId);
        result.ClearScreen.Should().BeFalse();
        _repository.Released.Should().BeTrue();
    }

    // FR-S11-14
    [Fact]
    public async Task TransactionWriteError_ReportsLegacyMessage()
    {
        SeedAccount(100m);
        _repository.PostOutcome = BillPaymentPostOutcome.TransactionWriteError;

        var result = await PayAsync(AccountId, "Y");

        result.Outcome.Should().Be(BillPaymentOutcome.TransactionWriteError);
        result.Message.Should().Be("Unable to Add Bill pay Transaction...");
        _repository.Released.Should().BeTrue();
    }

    // FR-S11-15
    [Fact]
    public async Task AccountRewriteNotFound_ReportsAccountNotFound()
    {
        SeedAccount(100m);
        _repository.PostOutcome = BillPaymentPostOutcome.AccountNotFound;

        var result = await PayAsync(AccountId, "Y");

        result.Outcome.Should().Be(BillPaymentOutcome.AccountNotFound);
        result.Message.Should().Be("Account ID NOT found...");
    }

    [Fact]
    public async Task AccountRewriteError_ReportsLegacyMessage()
    {
        SeedAccount(100m);
        _repository.PostOutcome = BillPaymentPostOutcome.AccountUpdateError;

        var result = await PayAsync(AccountId, "Y");

        result.Outcome.Should().Be(BillPaymentOutcome.AccountUpdateError);
        result.Message.Should().Be("Unable to Update Account...");
    }

    private sealed class FixedTimeProvider(DateTimeOffset now) : TimeProvider
    {
        public override DateTimeOffset GetUtcNow() => now;

        public override TimeZoneInfo LocalTimeZone => TimeZoneInfo.Utc;
    }

    private sealed class FakeCardXrefRepository : ICardXrefRepository
    {
        public List<CardXref> Xrefs { get; } = [];
        public bool Fail { get; set; }
        public int ReadCount { get; private set; }

        public Task<CardXref?> GetByCardNumberAsync(string cardNumber, CancellationToken cancellationToken = default) =>
            Task.FromResult(Xrefs.FirstOrDefault(x => x.CardNumber == cardNumber));

        public Task<CardXref?> GetFirstByAccountIdAsync(string accountId, CancellationToken cancellationToken = default)
        {
            ReadCount++;
            if (Fail)
            {
                throw new InvalidOperationException("CXACAIX unavailable");
            }
            return Task.FromResult(Xrefs.Where(x => x.AccountId == accountId).OrderBy(x => x.CardNumber, StringComparer.Ordinal).FirstOrDefault());
        }

        public Task<IReadOnlyList<CardXref>> ListByAccountIdAsync(string accountId, CancellationToken cancellationToken = default) =>
            Task.FromResult<IReadOnlyList<CardXref>>(Xrefs.Where(x => x.AccountId == accountId).ToList());
    }

    private sealed class FakeBillPaymentRepository : IBillPaymentRepository
    {
        public Dictionary<string, Account> Accounts { get; } = new(StringComparer.Ordinal);
        public List<(Account Account, Transaction Transaction)> Posted { get; } = [];
        public string? LastTransactionId { get; set; }
        public bool FailReads { get; set; }
        public bool FailLastTransactionId { get; set; }
        public BillPaymentPostOutcome PostOutcome { get; set; } = BillPaymentPostOutcome.Posted;
        public int ReadCount { get; private set; }
        public int LockCount { get; private set; }
        public bool Released { get; private set; }

        public Task<Account?> GetAccountAsync(string accountId, CancellationToken cancellationToken = default)
        {
            ReadCount++;
            if (FailReads)
            {
                throw new InvalidOperationException("ACCTDAT unavailable");
            }
            return Task.FromResult(Accounts.GetValueOrDefault(accountId));
        }

        public Task<Account?> GetAccountForUpdateAsync(string accountId, CancellationToken cancellationToken = default)
        {
            LockCount++;
            return GetAccountAsync(accountId, cancellationToken);
        }

        public Task ReleaseAccountAsync(CancellationToken cancellationToken = default)
        {
            Released = true;
            return Task.CompletedTask;
        }

        public Task<string?> GetLastTransactionIdAsync(CancellationToken cancellationToken = default)
        {
            if (FailLastTransactionId)
            {
                throw new InvalidOperationException("TRANSACT unavailable");
            }
            return Task.FromResult(LastTransactionId);
        }

        public Task<BillPaymentPostOutcome> PostPaymentAsync(Account account, Transaction transaction, CancellationToken cancellationToken = default)
        {
            if (PostOutcome == BillPaymentPostOutcome.Posted)
            {
                Posted.Add((account, transaction));
            }
            return Task.FromResult(PostOutcome);
        }
    }
}
