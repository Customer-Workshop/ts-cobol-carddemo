using CardDemo.Application.Accounts;
using CardDemo.Application.Cards;
using CardDemo.Application.Customers;
using CardDemo.Domain.Accounts;
using CardDemo.Domain.Cards;
using CardDemo.Domain.Customers;

namespace CardDemo.Tests.Accounts;

/// <summary>In-memory stand-ins for the three shared repositories read by COACTVWC.</summary>
internal sealed class InMemoryCardXrefRepository : ICardXrefRepository
{
    public List<CardXref> Records { get; } = [];
    public bool ThrowOnRead { get; set; }
    public int Reads { get; private set; }

    public Task<CardXref?> GetByCardNumberAsync(string cardNumber, CancellationToken cancellationToken = default) =>
        Task.FromResult(Records.FirstOrDefault(x => x.CardNumber == cardNumber));

    public Task<CardXref?> GetFirstByAccountIdAsync(string accountId, CancellationToken cancellationToken = default)
    {
        Reads++;
        if (ThrowOnRead)
        {
            throw new InvalidOperationException("store unavailable");
        }
        return Task.FromResult(Records.Where(x => x.AccountId == accountId).OrderBy(x => x.CardNumber).FirstOrDefault());
    }

    public Task<IReadOnlyList<CardXref>> ListByAccountIdAsync(string accountId, CancellationToken cancellationToken = default) =>
        Task.FromResult<IReadOnlyList<CardXref>>(Records.Where(x => x.AccountId == accountId).OrderBy(x => x.CardNumber).ToList());
}

internal sealed class InMemoryAccountRepository : IAccountRepository
{
    public List<Account> Records { get; } = [];
    public bool ThrowOnRead { get; set; }
    public int Reads { get; private set; }

    public Task<Account?> GetByIdAsync(string accountId, CancellationToken cancellationToken = default)
    {
        Reads++;
        if (ThrowOnRead)
        {
            throw new InvalidOperationException("store unavailable");
        }
        return Task.FromResult(Records.SingleOrDefault(a => a.AccountId == accountId));
    }
}

internal sealed class InMemoryCustomerRepository : ICustomerRepository
{
    public List<Customer> Records { get; } = [];
    public bool ThrowOnRead { get; set; }
    public int Reads { get; private set; }

    public Task<Customer?> GetByIdAsync(string customerId, CancellationToken cancellationToken = default)
    {
        Reads++;
        if (ThrowOnRead)
        {
            throw new InvalidOperationException("store unavailable");
        }
        return Task.FromResult(Records.SingleOrDefault(c => c.CustomerId == customerId));
    }
}

internal static class AccountViewTestData
{
    public const string AccountId = "00000000001";
    public const string CustomerId = "000000001";

    /// <summary>First ACCTDAT record of app/data/ASCII/acctdata.txt.</summary>
    public static Account Account() => new()
    {
        AccountId = AccountId,
        ActiveStatus = "Y",
        CurrentBalance = 194.00m,
        CreditLimit = 2020.00m,
        CashCreditLimit = 1020.00m,
        OpenDate = new DateOnly(2014, 11, 20),
        ExpirationDate = new DateOnly(2025, 5, 20),
        ReissueDate = new DateOnly(2025, 5, 20),
        CurrentCycleCredit = 0m,
        CurrentCycleDebit = 0m,
        AddressZip = "A000000000",
        GroupId = ""
    };

    /// <summary>First CUSTDAT record of app/data/ASCII/custdata.txt.</summary>
    public static Customer Customer() => new()
    {
        CustomerId = CustomerId,
        FirstName = "Immanuel",
        MiddleName = "Madeline",
        LastName = "Kessler",
        AddressLine1 = "618 Deshaun Route",
        AddressLine2 = "Apt. 802",
        AddressLine3 = "Altenwerthshire",
        AddressStateCode = "NC",
        AddressCountryCode = "USA",
        AddressZip = "12546",
        PhoneNumber1 = "(908)119-8310",
        PhoneNumber2 = "(373)693-8684",
        Ssn = "020973888",
        GovernmentIssuedId = "00000000000049368437",
        DateOfBirth = new DateOnly(1961, 6, 8),
        EftAccountId = "0053581756",
        PrimaryCardHolderIndicator = "Y",
        FicoCreditScore = 274
    };

    public static CardXref Xref(string cardNumber = "4000000000000001", string customerId = CustomerId, string accountId = AccountId) =>
        new() { CardNumber = cardNumber, CustomerId = customerId, AccountId = accountId };

    public static (AccountViewService Service, InMemoryCardXrefRepository Xrefs, InMemoryAccountRepository Accounts, InMemoryCustomerRepository Customers) BuildService()
    {
        var xrefs = new InMemoryCardXrefRepository();
        var accounts = new InMemoryAccountRepository();
        var customers = new InMemoryCustomerRepository();
        return (new AccountViewService(xrefs, accounts, customers), xrefs, accounts, customers);
    }
}
