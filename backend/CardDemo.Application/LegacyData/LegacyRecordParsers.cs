using CardDemo.Domain.Accounts;
using CardDemo.Domain.Cards;
using CardDemo.Domain.Customers;
using CardDemo.Domain.Transactions;

namespace CardDemo.Application.LegacyData;

/// <summary>ACCOUNT-RECORD, app/cpy/CVACT01Y.cpy (RECLN 300).</summary>
public static class AccountRecordParser
{
    public const int RecordLength = 300;

    public static Account Parse(string line)
    {
        var r = new FixedWidthRecord(line, RecordLength, "ACCTDAT");
        return new Account
        {
            AccountId = r.Digits(11),
            ActiveStatus = r.Text(1),
            CurrentBalance = r.Amount(10, 2),
            CreditLimit = r.Amount(10, 2),
            CashCreditLimit = r.Amount(10, 2),
            OpenDate = r.Date(),
            ExpirationDate = r.Date(),
            ReissueDate = r.Date(),
            CurrentCycleCredit = r.Amount(10, 2),
            CurrentCycleDebit = r.Amount(10, 2),
            AddressZip = r.Text(10),
            GroupId = r.Text(10)
        };
    }
}

/// <summary>CARD-RECORD, app/cpy/CVACT02Y.cpy (RECLN 150).</summary>
public static class CardRecordParser
{
    public const int RecordLength = 150;

    public static Card Parse(string line)
    {
        var r = new FixedWidthRecord(line, RecordLength, "CARDDAT");
        return new Card
        {
            CardNumber = r.Text(16),
            AccountId = r.Digits(11),
            CvvCode = r.Digits(3),
            EmbossedName = r.Text(50),
            ExpirationDate = r.Date(),
            ActiveStatus = r.Text(1)
        };
    }
}

/// <summary>CARD-XREF-RECORD, app/cpy/CVACT03Y.cpy (RECLN 50).</summary>
public static class CardXrefRecordParser
{
    public const int RecordLength = 50;

    public static CardXref Parse(string line)
    {
        var r = new FixedWidthRecord(line, RecordLength, "CCXREF");
        return new CardXref
        {
            CardNumber = r.Text(16),
            CustomerId = r.Digits(9),
            AccountId = r.Digits(11)
        };
    }
}

/// <summary>CUSTOMER-RECORD, app/cpy/CVCUS01Y.cpy (RECLN 500).</summary>
public static class CustomerRecordParser
{
    public const int RecordLength = 500;

    public static Customer Parse(string line)
    {
        var r = new FixedWidthRecord(line, RecordLength, "CUSTDAT");
        return new Customer
        {
            CustomerId = r.Digits(9),
            FirstName = r.Text(25),
            MiddleName = r.Text(25),
            LastName = r.Text(25),
            AddressLine1 = r.Text(50),
            AddressLine2 = r.Text(50),
            AddressLine3 = r.Text(50),
            AddressStateCode = r.Text(2),
            AddressCountryCode = r.Text(3),
            AddressZip = r.Text(10),
            PhoneNumber1 = r.Text(15),
            PhoneNumber2 = r.Text(15),
            Ssn = r.Digits(9),
            GovernmentIssuedId = r.Text(20),
            DateOfBirth = r.Date(),
            EftAccountId = r.Text(10),
            PrimaryCardHolderIndicator = r.Text(1),
            FicoCreditScore = r.UnsignedInt(3)
        };
    }
}

/// <summary>TRAN-RECORD, app/cpy/CVTRA05Y.cpy (RECLN 350).</summary>
public static class TransactionRecordParser
{
    public const int RecordLength = 350;

    public static Transaction Parse(string line)
    {
        var r = new FixedWidthRecord(line, RecordLength, "TRANSACT");
        return new Transaction
        {
            TransactionId = r.Text(16),
            TypeCode = r.Text(2),
            CategoryCode = r.Digits(4),
            Source = r.Text(10),
            Description = r.Text(100),
            Amount = r.Amount(9, 2),
            MerchantId = r.Digits(9),
            MerchantName = r.Text(50),
            MerchantCity = r.Text(50),
            MerchantZip = r.Text(10),
            CardNumber = r.Text(16),
            OriginalTimestamp = r.Timestamp(),
            ProcessedTimestamp = r.Timestamp()
        };
    }
}

/// <summary>TRAN-CAT-BAL-RECORD, app/cpy/CVTRA01Y.cpy (RECLN 50).</summary>
public static class TransactionCategoryBalanceRecordParser
{
    public const int RecordLength = 50;

    public static TransactionCategoryBalance Parse(string line)
    {
        var r = new FixedWidthRecord(line, RecordLength, "TCATBALF");
        return new TransactionCategoryBalance
        {
            AccountId = r.Digits(11),
            TypeCode = r.Text(2),
            CategoryCode = r.Digits(4),
            Balance = r.Amount(9, 2)
        };
    }
}

/// <summary>DIS-GROUP-RECORD, app/cpy/CVTRA02Y.cpy (RECLN 50).</summary>
public static class DisclosureGroupRecordParser
{
    public const int RecordLength = 50;

    public static DisclosureGroup Parse(string line)
    {
        var r = new FixedWidthRecord(line, RecordLength, "DISCGRP");
        return new DisclosureGroup
        {
            AccountGroupId = r.Text(10),
            TransactionTypeCode = r.Text(2),
            TransactionCategoryCode = r.Digits(4),
            InterestRate = r.Amount(4, 2)
        };
    }
}

/// <summary>TRAN-TYPE-RECORD, app/cpy/CVTRA03Y.cpy (RECLN 60).</summary>
public static class TransactionTypeRecordParser
{
    public const int RecordLength = 60;

    public static TransactionType Parse(string line)
    {
        var r = new FixedWidthRecord(line, RecordLength, "TRANTYPE");
        return new TransactionType
        {
            TypeCode = r.Text(2),
            Description = r.Text(50)
        };
    }
}

/// <summary>TRAN-CAT-RECORD, app/cpy/CVTRA04Y.cpy (RECLN 60).</summary>
public static class TransactionCategoryRecordParser
{
    public const int RecordLength = 60;

    public static TransactionCategory Parse(string line)
    {
        var r = new FixedWidthRecord(line, RecordLength, "TRANCATG");
        return new TransactionCategory
        {
            TypeCode = r.Text(2),
            CategoryCode = r.Digits(4),
            Description = r.Text(50)
        };
    }
}
