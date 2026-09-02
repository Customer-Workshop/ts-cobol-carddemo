using CardDemo.Application.Accounts;
using FluentAssertions;
using static CardDemo.Tests.Accounts.AccountViewTestData;

namespace CardDemo.Tests.Accounts;

/// <summary>
/// Parity tests for COACTVWC 2210-EDIT-ACCOUNT + 9000-READ-ACCT (app/cbl/COACTVWC.cbl:648-870):
/// validation order, exact WS-RETURN-MSG texts, read chain and screen blocks (FR-S02-01..10, 13).
/// </summary>
public class AccountViewServiceTests
{
    [Fact]
    public void InitialScreen_ShowsEmptyFieldAndPromptOnly()
    {
        // FR-S02-01
        var screen = AccountViewService.InitialScreen();

        screen.Outcome.Should().Be(AccountViewOutcome.Initial);
        screen.AccountId.Should().BeEmpty();
        screen.FilterState.Should().Be(AccountFilterState.Blank);
        screen.InfoMessage.Should().Be("Enter or update id of account to display");
        screen.ErrorMessage.Should().BeEmpty();
        screen.Account.Should().BeNull();
        screen.Customer.Should().BeNull();
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData("*")]
    [InlineData("*          ")]
    public async Task BlankOrStarAccount_ReportsNoInputAndEchoesStar(string? input)
    {
        // FR-S02-02: 'Account number not provided' is overwritten by NO-SEARCH-CRITERIA-RECEIVED (:641)
        var (service, xrefs, _, _) = BuildService();

        var screen = await service.ViewAsync(input);

        screen.Outcome.Should().Be(AccountViewOutcome.NoInput);
        screen.ErrorMessage.Should().Be("No input received");
        screen.AccountId.Should().Be("*");
        screen.FilterState.Should().Be(AccountFilterState.Blank);
        screen.InfoMessage.Should().Be("Enter or update id of account to display");
        screen.Account.Should().BeNull();
        xrefs.Reads.Should().Be(0);
    }

    [Theory]
    [InlineData("123")]
    [InlineData("1234567890a")]
    [InlineData("  123456789")]
    [InlineData("00000000000")]
    [InlineData("0000000001 ")]
    [InlineData("000000000012")]
    [InlineData("1234567890.")]
    public async Task NonNumericOrZeroAccount_ReportsFilterMessageWithDoubleSpace(string input)
    {
        // FR-S02-03: literal at COACTVWC.cbl:672 has two blanks after 'must'
        var (service, xrefs, _, _) = BuildService();

        var screen = await service.ViewAsync(input);

        screen.Outcome.Should().Be(AccountViewOutcome.InvalidFilter);
        screen.ErrorMessage.Should().Be("Account Filter must  be a non-zero 11 digit number");
        screen.AccountId.Should().Be(input.TrimEnd());
        screen.FilterState.Should().Be(AccountFilterState.Invalid);
        screen.Account.Should().BeNull();
        xrefs.Reads.Should().Be(0);
    }

    [Fact]
    public async Task AccountWithoutXref_ReportsCrossRefNotFoundAndStopsReading()
    {
        // FR-S02-04
        var (service, _, accounts, customers) = BuildService();
        accounts.Records.Add(Account());

        var screen = await service.ViewAsync("00000000001");

        screen.Outcome.Should().Be(AccountViewOutcome.AccountNotInXref);
        screen.ErrorMessage.Should().Be("Account:00000000001 not found in Cross ref file.  Resp:000000013  Reas:0000");
        screen.ErrorMessage.Should().HaveLength(75);
        screen.AccountId.Should().Be("00000000001");
        screen.FilterState.Should().Be(AccountFilterState.Invalid);
        screen.Account.Should().BeNull();
        screen.Customer.Should().BeNull();
        accounts.Reads.Should().Be(0);
        customers.Reads.Should().Be(0);
    }

    [Fact]
    public async Task XrefWithoutAccountMaster_ReportsAcctMasterNotFound()
    {
        // FR-S02-05
        var (service, xrefs, _, customers) = BuildService();
        xrefs.Records.Add(Xref());
        customers.Records.Add(Customer());

        var screen = await service.ViewAsync("00000000001");

        screen.Outcome.Should().Be(AccountViewOutcome.AccountNotInMaster);
        screen.ErrorMessage.Should().Be("Account:00000000001 not found in Acct Master file.Resp:000000013  Reas:0000");
        screen.ErrorMessage.Should().HaveLength(75);
        screen.FilterState.Should().Be(AccountFilterState.Invalid);
        screen.Account.Should().BeNull();
        screen.Customer.Should().BeNull();
        customers.Reads.Should().Be(0);
    }

    [Fact]
    public async Task AccountWithoutCustomer_ReportsCustomerNotFoundButKeepsAccountBlock()
    {
        // FR-S02-06: FLG-CUSTFILTER-NOT-OK only; FOUND-ACCT-IN-MASTER still fills the account block (:471)
        var (service, xrefs, accounts, _) = BuildService();
        xrefs.Records.Add(Xref(customerId: "000000777"));
        accounts.Records.Add(Account());

        var screen = await service.ViewAsync("00000000001");

        screen.Outcome.Should().Be(AccountViewOutcome.CustomerNotFound);
        screen.ErrorMessage.Should().Be("CustId:000000777 not found in customer master.Resp: 000000013  REAS:0000000");
        screen.ErrorMessage.Should().HaveLength(75);
        screen.FilterState.Should().Be(AccountFilterState.Valid);
        screen.Account.Should().NotBeNull();
        screen.Account!.CurrentBalance.Should().Be("+        194.00");
        screen.Customer.Should().BeNull();
    }

    [Fact]
    public async Task AccountAndCustomerFound_FillsBothBlocksWithEditedFields()
    {
        // FR-S02-07, 08, 09
        var (service, xrefs, accounts, customers) = BuildService();
        xrefs.Records.Add(Xref());
        accounts.Records.Add(Account());
        customers.Records.Add(Customer());

        var screen = await service.ViewAsync("00000000001");

        screen.Outcome.Should().Be(AccountViewOutcome.Found);
        screen.ErrorMessage.Should().BeEmpty();
        screen.InfoMessage.Should().Be("Enter or update id of account to display");
        screen.AccountId.Should().Be("00000000001");
        screen.FilterState.Should().Be(AccountFilterState.Valid);

        screen.Account.Should().BeEquivalentTo(new AccountViewAccountDetails(
            ActiveStatus: "Y",
            OpenDate: "2014-11-20",
            CreditLimit: "+      2,020.00",
            ExpirationDate: "2025-05-20",
            CashCreditLimit: "+      1,020.00",
            ReissueDate: "2025-05-20",
            CurrentBalance: "+        194.00",
            CurrentCycleCredit: "+           .00",
            GroupId: "",
            CurrentCycleDebit: "+           .00"));

        screen.Customer.Should().BeEquivalentTo(new AccountViewCustomerDetails(
            CustomerId: "000000001",
            Ssn: "020-97-3888",
            DateOfBirth: "1961-06-08",
            FicoScore: "274",
            FirstName: "Immanuel",
            MiddleName: "Madeline",
            LastName: "Kessler",
            AddressLine1: "618 Deshaun Route",
            AddressLine2: "Apt. 802",
            State: "NC",
            City: "Altenwerthshire",
            Zip: "12546",
            Country: "USA",
            Phone1: "(908)119-8310",
            Phone2: "(373)693-8684",
            GovernmentIssuedId: "00000000000049368437",
            EftAccountId: "0053581756",
            PrimaryCardHolder: "Y"));
    }

    [Fact]
    public async Task CustomerComesFromTheFirstXrefOfTheAccount()
    {
        // FR-S02-10: CXACAIX keyed READ returns the lowest card number for the account
        var (service, xrefs, accounts, customers) = BuildService();
        xrefs.Records.Add(Xref(cardNumber: "4999999999999999", customerId: "000000002"));
        xrefs.Records.Add(Xref(cardNumber: "4000000000000001", customerId: "000000001"));
        accounts.Records.Add(Account());
        customers.Records.Add(Customer());
        var other = Customer();
        other.CustomerId = "000000002";
        other.FirstName = "Other";
        customers.Records.Add(other);

        var screen = await service.ViewAsync("00000000001");

        screen.Customer!.CustomerId.Should().Be("000000001");
        screen.Customer.FirstName.Should().Be("Immanuel");
    }

    [Fact]
    public async Task XrefStoreFailure_ReportsFileErrorForCxacaix()
    {
        // FR-S02-13
        var (service, xrefs, accounts, _) = BuildService();
        xrefs.ThrowOnRead = true;

        var screen = await service.ViewAsync("00000000001");

        screen.Outcome.Should().Be(AccountViewOutcome.StoreError);
        screen.ErrorMessage.Should().Be("File Error: READ     on CXACAIX   returned RESP 000000017 ,RESP2 000000120 ");
        screen.ErrorMessage.Should().HaveLength(75);
        screen.FilterState.Should().Be(AccountFilterState.Invalid);
        screen.Account.Should().BeNull();
        accounts.Reads.Should().Be(0);
    }

    [Fact]
    public async Task AccountStoreFailure_ReportsFileErrorForAcctdat()
    {
        // FR-S02-13
        var (service, xrefs, accounts, customers) = BuildService();
        xrefs.Records.Add(Xref());
        accounts.ThrowOnRead = true;

        var screen = await service.ViewAsync("00000000001");

        screen.Outcome.Should().Be(AccountViewOutcome.StoreError);
        screen.ErrorMessage.Should().Be("File Error: READ     on ACCTDAT   returned RESP 000000017 ,RESP2 000000120 ");
        screen.Account.Should().BeNull();
        customers.Reads.Should().Be(0);
    }

    [Fact]
    public async Task CustomerStoreFailure_ReportsFileErrorForCustdatAndKeepsAccountBlock()
    {
        // FR-S02-13
        var (service, xrefs, accounts, customers) = BuildService();
        xrefs.Records.Add(Xref());
        accounts.Records.Add(Account());
        customers.ThrowOnRead = true;

        var screen = await service.ViewAsync("00000000001");

        screen.Outcome.Should().Be(AccountViewOutcome.StoreError);
        screen.ErrorMessage.Should().Be("File Error: READ     on CUSTDAT   returned RESP 000000017 ,RESP2 000000120 ");
        screen.FilterState.Should().Be(AccountFilterState.Valid);
        screen.Account.Should().NotBeNull();
        screen.Customer.Should().BeNull();
    }
}
