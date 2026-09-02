using CardDemo.Application.AccountUpdate;
using CardDemo.Domain.Accounts;
using CardDemo.Domain.Cards;
using CardDemo.Domain.Customers;

namespace CardDemo.Tests.AccountUpdate;

/// <summary>A consistent account/customer pair shaped like the app/data/ASCII seed rows.</summary>
public static class AccountUpdateTestData
{
    public const string AccountId = "00000000010";
    public const string CustomerId = "000000010";
    public const string CardNumber = "4000000000000010";

    public static readonly DateOnly Today = new(2026, 9, 2);

    public static Account Account() => new()
    {
        AccountId = AccountId,
        ActiveStatus = "Y",
        CurrentBalance = 1250.75m,
        CreditLimit = 10000.00m,
        CashCreditLimit = 2000.00m,
        OpenDate = new DateOnly(2019, 3, 15),
        ExpirationDate = new DateOnly(2027, 3, 31),
        ReissueDate = new DateOnly(2024, 3, 31),
        CurrentCycleCredit = 300.00m,
        CurrentCycleDebit = 125.50m,
        AddressZip = "10001",
        GroupId = "ZEROAPR"
    };

    public static Customer Customer() => new()
    {
        CustomerId = CustomerId,
        FirstName = "JOHN",
        MiddleName = "Q",
        LastName = "PUBLIC",
        AddressLine1 = "1 MAIN ST",
        AddressLine2 = "APT 2",
        AddressLine3 = "NEW YORK",
        AddressStateCode = "NY",
        AddressCountryCode = "USA",
        AddressZip = "10001",
        PhoneNumber1 = "(212)555-1234",
        PhoneNumber2 = "(718)555-9876",
        Ssn = "123456789",
        GovernmentIssuedId = "DL12345",
        DateOfBirth = new DateOnly(1980, 6, 20),
        EftAccountId = "0000123456",
        PrimaryCardHolderIndicator = "Y",
        FicoCreditScore = 720
    };

    public static CardXref Xref() => new()
    {
        CardNumber = CardNumber,
        CustomerId = CustomerId,
        AccountId = AccountId
    };

    /// <summary>The fields exactly as 3202-SHOW-ORIGINAL-VALUES would paint them.</summary>
    public static AccountUpdateFields Fields() => AccountUpdateFieldMapper.FromEntities(Account(), Customer());
}
