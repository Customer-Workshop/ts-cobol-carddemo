using CardDemo.Application.AccountUpdate;
using CardDemo.Application.Accounts;
using CardDemo.Application.Cards;
using CardDemo.Application.Customers;
using CardDemo.Domain.Accounts;
using CardDemo.Domain.Cards;
using CardDemo.Domain.Customers;
using FluentAssertions;

namespace CardDemo.Tests.AccountUpdate;

/// <summary>9000-READ-ACCT lookup chain and 2000-DECIDE-ACTION outcomes over in-memory repositories (FR-S03-08..11, 23..29).</summary>
public class AccountUpdateServiceTests
{
    private readonly FakeXrefs _xrefs = new();
    private readonly FakeAccounts _accounts = new();
    private readonly FakeCustomers _customers = new();
    private readonly FakeWriter _writer = new();
    private readonly AccountUpdateService _service;

    public AccountUpdateServiceTests()
    {
        _service = new AccountUpdateService(_xrefs, _accounts, _customers, _writer, new FixedClock());
        _xrefs.Items.Add(AccountUpdateTestData.Xref());
        _accounts.Items.Add(AccountUpdateTestData.Account());
        _customers.Items.Add(AccountUpdateTestData.Customer());
    }

    [Fact]
    public async Task Lookup_BlankId_StaysOnSearchWithNoInputReceived()
    {
        var result = await _service.LookupAsync("   ");

        result.Outcome.Should().Be(AccountUpdateOutcome.SearchError);
        result.ErrorMessage.Should().Be("No input received");
        result.InfoMessage.Should().Be("Enter or update id of account to update");
        result.Fields.Should().BeNull();
    }

    [Fact]
    public async Task Lookup_InvalidId_StaysOnSearchWithFilterMessage()
    {
        var result = await _service.LookupAsync("12AB");

        result.Outcome.Should().Be(AccountUpdateOutcome.SearchError);
        result.ErrorMessage.Should().Be("Account Number if supplied must be a 11 digit Non-Zero Number");
    }

    [Fact]
    public async Task Lookup_AccountMissingFromXref_ReportsXrefNotFound()
    {
        var result = await _service.LookupAsync("00000000099");

        result.Outcome.Should().Be(AccountUpdateOutcome.SearchError);
        result.ErrorMessage.Should().Be("Account:00000000099 not found in Cross ref file.  Resp:000000013  Reas:0000");
        result.ErrorMessage!.Length.Should().Be(75);
    }

    [Fact]
    public async Task Lookup_AccountMissingFromMaster_ReportsMasterNotFound()
    {
        _accounts.Items.Clear();

        var result = await _service.LookupAsync(AccountUpdateTestData.AccountId);

        result.Outcome.Should().Be(AccountUpdateOutcome.SearchError);
        result.ErrorMessage.Should().Be("Account:00000000010 not found in Acct Master file.Resp:000000013  Reas:0000");
        result.ErrorMessage!.Length.Should().Be(75);
    }

    [Fact]
    public async Task Lookup_CustomerMissing_ReportsCustomerNotFound()
    {
        _customers.Items.Clear();

        var result = await _service.LookupAsync(AccountUpdateTestData.AccountId);

        result.Outcome.Should().Be(AccountUpdateOutcome.SearchError);
        result.ErrorMessage.Should().Be("CustId:000000010 not found in customer master.Resp: 000000013  REAS:0000000");
        result.ErrorMessage!.Length.Should().Be(75);
    }

    [Fact]
    public async Task Lookup_Found_ReturnsScreenShapedFieldsAndPromptForChanges()
    {
        var result = await _service.LookupAsync(AccountUpdateTestData.AccountId);

        result.Outcome.Should().Be(AccountUpdateOutcome.Details);
        result.InfoMessage.Should().Be("Update account details presented above.");
        result.ErrorMessage.Should().BeNull();
        var f = result.Fields!;
        f.AccountId.Should().Be("00000000010");
        f.ActiveStatus.Should().Be("Y");
        f.CreditLimit.Should().Be("+     10,000.00");
        f.CurrentBalance.Should().Be("+      1,250.75");
        f.OpenYear.Should().Be("2019");
        f.OpenMonth.Should().Be("03");
        f.OpenDay.Should().Be("15");
        f.CustomerId.Should().Be("000000010");
        f.Ssn1.Should().Be("123");
        f.Ssn2.Should().Be("45");
        f.Ssn3.Should().Be("6789");
        f.FicoScore.Should().Be("720");
        f.Phone1Area.Should().Be("212");
        f.Phone1Prefix.Should().Be("555");
        f.Phone1Line.Should().Be("1234");
        f.DobYear.Should().Be("1980");
        f.City.Should().Be("NEW YORK");
        f.GroupId.Should().Be("ZEROAPR");
    }

    [Fact]
    public async Task Lookup_TrimsAndUsesTheTypedId()
    {
        var result = await _service.LookupAsync(" 00000000010 ");

        result.Outcome.Should().Be(AccountUpdateOutcome.Details);
    }

    [Fact]
    public void Validate_NoChanges_KeepsDetailsWithNoChangeMessage()
    {
        var original = AccountUpdateTestData.Fields();

        var result = _service.Validate(original, original);

        result.Outcome.Should().Be(AccountUpdateOutcome.NoChanges);
        result.ErrorMessage.Should().Be("No change detected with respect to values fetched.");
        result.InfoMessage.Should().Be("Update account details presented above.");
    }

    [Fact]
    public void Validate_InvalidChange_ReturnsFirstErrorAndFlags()
    {
        var original = AccountUpdateTestData.Fields();

        var result = _service.Validate(original, original with { ActiveStatus = "Z" });

        result.Outcome.Should().Be(AccountUpdateOutcome.Invalid);
        result.ErrorMessage.Should().Be("Account Status must be Y or N.");
        result.InfoMessage.Should().Be("Update account details presented above.");
        result.InvalidFields.Should().Equal(AccountUpdateFieldNames.ActiveStatus);
    }

    [Fact]
    public void Validate_ValidChange_PromptsForF5()
    {
        var original = AccountUpdateTestData.Fields();

        var result = _service.Validate(original, original with { CreditLimit = "20000" });

        result.Outcome.Should().Be(AccountUpdateOutcome.Confirm);
        result.InfoMessage.Should().Be("Changes validated.Press F5 to save");
        result.ErrorMessage.Should().BeNull();
    }

    [Fact]
    public async Task Save_ReRunsTheEditsBeforeWriting()
    {
        var original = AccountUpdateTestData.Fields();

        var result = await _service.SaveAsync(original, original with { FicoScore = "100" });

        result.Outcome.Should().Be(AccountUpdateOutcome.Invalid);
        result.ErrorMessage.Should().Be("FICO Score: should be between 300 and 850");
        _writer.Calls.Should().Be(0);
    }

    [Fact]
    public async Task Save_NoChanges_DoesNotWrite()
    {
        var original = AccountUpdateTestData.Fields();

        var result = await _service.SaveAsync(original, original);

        result.Outcome.Should().Be(AccountUpdateOutcome.NoChanges);
        _writer.Calls.Should().Be(0);
    }

    [Fact]
    public async Task Save_Updated_AppliesNewValuesAndConfirms()
    {
        var original = AccountUpdateTestData.Fields();
        var updated = original with
        {
            ActiveStatus = "N",
            CreditLimit = "20,000.00",
            LastName = "CITIZEN",
            Phone2Area = "",
            Phone2Prefix = "",
            Phone2Line = "",
            FicoScore = "650"
        };

        var result = await _service.SaveAsync(original, updated);

        result.Outcome.Should().Be(AccountUpdateOutcome.Committed);
        result.InfoMessage.Should().Be("Changes committed to database");
        result.ErrorMessage.Should().BeNull();
        _writer.AccountId.Should().Be(AccountUpdateTestData.AccountId);
        _writer.CustomerId.Should().Be(AccountUpdateTestData.CustomerId);
        _writer.Account!.ActiveStatus.Should().Be("N");
        _writer.Account.CreditLimit.Should().Be(20000m);
        _writer.Account.CurrentBalance.Should().Be(1250.75m);
        _writer.Customer!.LastName.Should().Be("CITIZEN");
        _writer.Customer.PhoneNumber2.Should().Be("(   )   -");
        _writer.Customer.PhoneNumber1.Should().Be("(212)555-1234");
        _writer.Customer.FicoCreditScore.Should().Be(650);
        _writer.Customer.Ssn.Should().Be("123456789");
    }

    [Fact]
    public async Task Save_SnapshotCheck_UsesTheFetchedValues()
    {
        var original = AccountUpdateTestData.Fields();
        _writer.StoredAccount.CreditLimit = 99m;

        var result = await _service.SaveAsync(original, original with { LastName = "CITIZEN" });

        result.Outcome.Should().Be(AccountUpdateOutcome.ChangedByOther);
        result.ErrorMessage.Should().Be("Record changed by some one else. Please review");
        result.InfoMessage.Should().Be("Update account details presented above.");
    }

    [Theory]
    [InlineData(AccountUpdateWriteStatus.AccountLockFailed, "Could not lock account record for update")]
    [InlineData(AccountUpdateWriteStatus.CustomerLockFailed, "Could not lock customer record for update")]
    [InlineData(AccountUpdateWriteStatus.UpdateFailed, "Update of record failed")]
    public async Task Save_WriteFailures_InformFailure(AccountUpdateWriteStatus status, string expected)
    {
        var original = AccountUpdateTestData.Fields();
        _writer.ForcedStatus = status;

        var result = await _service.SaveAsync(original, original with { LastName = "CITIZEN" });

        result.Outcome.Should().Be(AccountUpdateOutcome.Failed);
        result.ErrorMessage.Should().Be(expected);
        result.InfoMessage.Should().Be("Changes unsuccessful. Please try again");
    }

    private sealed class FixedClock : TimeProvider
    {
        public override DateTimeOffset GetUtcNow() => new(2026, 9, 2, 12, 0, 0, TimeSpan.Zero);

        public override TimeZoneInfo LocalTimeZone => TimeZoneInfo.Utc;
    }

    private sealed class FakeXrefs : ICardXrefRepository
    {
        public List<CardXref> Items { get; } = [];

        public Task<CardXref?> GetByCardNumberAsync(string cardNumber, CancellationToken cancellationToken = default) =>
            Task.FromResult(Items.FirstOrDefault(x => x.CardNumber == cardNumber));

        public Task<CardXref?> GetFirstByAccountIdAsync(string accountId, CancellationToken cancellationToken = default) =>
            Task.FromResult(Items.Where(x => x.AccountId == accountId).OrderBy(x => x.CardNumber).FirstOrDefault());

        public Task<IReadOnlyList<CardXref>> ListByAccountIdAsync(string accountId, CancellationToken cancellationToken = default) =>
            Task.FromResult<IReadOnlyList<CardXref>>(Items.Where(x => x.AccountId == accountId).OrderBy(x => x.CardNumber).ToList());
    }

    private sealed class FakeAccounts : IAccountRepository
    {
        public List<Account> Items { get; } = [];

        public Task<Account?> GetByIdAsync(string accountId, CancellationToken cancellationToken = default) =>
            Task.FromResult(Items.FirstOrDefault(a => a.AccountId == accountId));
    }

    private sealed class FakeCustomers : ICustomerRepository
    {
        public List<Customer> Items { get; } = [];

        public Task<Customer?> GetByIdAsync(string customerId, CancellationToken cancellationToken = default) =>
            Task.FromResult(Items.FirstOrDefault(c => c.CustomerId == customerId));
    }

    private sealed class FakeWriter : IAccountUpdateWriter
    {
        public Account StoredAccount { get; } = AccountUpdateTestData.Account();
        public Customer StoredCustomer { get; } = AccountUpdateTestData.Customer();
        public AccountUpdateWriteStatus? ForcedStatus { get; set; }
        public int Calls { get; private set; }
        public string? AccountId { get; private set; }
        public string? CustomerId { get; private set; }
        public Account? Account { get; private set; }
        public Customer? Customer { get; private set; }

        public Task<AccountUpdateWriteStatus> WriteAsync(
            string accountId,
            string customerId,
            Func<Account, Customer, bool> snapshotUnchanged,
            Action<Account, Customer> applyChanges,
            CancellationToken cancellationToken = default)
        {
            Calls++;
            AccountId = accountId;
            CustomerId = customerId;
            if (ForcedStatus is not null)
            {
                return Task.FromResult(ForcedStatus.Value);
            }
            if (!snapshotUnchanged(StoredAccount, StoredCustomer))
            {
                return Task.FromResult(AccountUpdateWriteStatus.ChangedBeforeUpdate);
            }
            applyChanges(StoredAccount, StoredCustomer);
            Account = StoredAccount;
            Customer = StoredCustomer;
            return Task.FromResult(AccountUpdateWriteStatus.Updated);
        }
    }
}
