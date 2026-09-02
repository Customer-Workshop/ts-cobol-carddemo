using CardDemo.Application.LegacyData;
using FluentAssertions;

namespace CardDemo.Tests.LegacyData;

public class LegacyRecordParserTests
{
    private const string AccountLine =
        "00000000001Y00000001940{00000020200{00000010200{2014-11-202025-05-202025-05-2000000000000{00000000000{A000000000";

    private const string CardLine =
        "050002445376574000000000050747Aniya Von                                         2023-03-09Y";

    private const string XrefLine = "050002445376574000000005000000000050";

    private const string DiscGroupLine = "A00000000001000100150{0000000000000000000000000000";

    private const string TcatBalLine = "000000000010100010000000000{0000000000000000000000";

    private const string TranTypeLine = "01Purchase                                          00000000";

    private const string TranCatLine = "010001Regular Sales Draft                               0000";

    [Fact]
    public void AccountRecordParser_MapsCvact01yFields()
    {
        var account = AccountRecordParser.Parse(AccountLine);

        account.AccountId.Should().Be("00000000001");
        account.ActiveStatus.Should().Be("Y");
        account.CurrentBalance.Should().Be(194.00m);
        account.CreditLimit.Should().Be(2020.00m);
        account.CashCreditLimit.Should().Be(1020.00m);
        account.OpenDate.Should().Be(new DateOnly(2014, 11, 20));
        account.ExpirationDate.Should().Be(new DateOnly(2025, 5, 20));
        account.ReissueDate.Should().Be(new DateOnly(2025, 5, 20));
        account.CurrentCycleCredit.Should().Be(0m);
        account.CurrentCycleDebit.Should().Be(0m);
        account.AddressZip.Should().Be("A000000000");
        account.GroupId.Should().Be("");
    }

    [Fact]
    public void AccountRecordParser_RejectsRecordsLongerThan300Bytes()
    {
        var act = () => AccountRecordParser.Parse(new string('0', 301));

        act.Should().Throw<FormatException>();
    }

    [Fact]
    public void AccountRecordParser_RejectsNonNumericKey()
    {
        var act = () => AccountRecordParser.Parse("ABCDEFGHIJK" + AccountLine[11..]);

        act.Should().Throw<FormatException>();
    }

    [Fact]
    public void AccountRecordParser_RejectsInvalidDate()
    {
        var act = () => AccountRecordParser.Parse(AccountLine[..48] + "2014-13-40" + AccountLine[58..]);

        act.Should().Throw<FormatException>();
    }

    [Fact]
    public void AccountRecordParser_BlankDateIsNull()
    {
        var account = AccountRecordParser.Parse(AccountLine[..48] + "          " + AccountLine[58..]);

        account.OpenDate.Should().BeNull();
    }

    [Fact]
    public void CardRecordParser_MapsCvact02yFields()
    {
        var card = CardRecordParser.Parse(CardLine);

        card.CardNumber.Should().Be("0500024453765740");
        card.AccountId.Should().Be("00000000050");
        card.CvvCode.Should().Be("747");
        card.EmbossedName.Should().Be("Aniya Von");
        card.ExpirationDate.Should().Be(new DateOnly(2023, 3, 9));
        card.ActiveStatus.Should().Be("Y");
    }

    [Fact]
    public void CardXrefRecordParser_MapsCvact03yFields()
    {
        var xref = CardXrefRecordParser.Parse(XrefLine);

        xref.CardNumber.Should().Be("0500024453765740");
        xref.CustomerId.Should().Be("000000050");
        xref.AccountId.Should().Be("00000000050");
    }

    [Fact]
    public void CustomerRecordParser_MapsCvcus01yFields()
    {
        var line = LegacyDataSeedSource.ReadRecords(TestPaths.AsciiData("custdata.txt"))[0];

        var customer = CustomerRecordParser.Parse(line);

        customer.CustomerId.Should().Be("000000001");
        customer.FirstName.Should().Be("Immanuel");
        customer.MiddleName.Should().Be("Madeline");
        customer.LastName.Should().Be("Kessler");
        customer.AddressStateCode.Should().Be("NC");
        customer.AddressCountryCode.Should().Be("USA");
        customer.AddressZip.Should().Be("12546");
        customer.PhoneNumber1.Should().Be("(908)119-8310");
        customer.PhoneNumber2.Should().Be("(373)693-8684");
        customer.Ssn.Should().Be("020973888");
        customer.GovernmentIssuedId.Should().Be("00000000000049368437");
        customer.DateOfBirth.Should().Be(new DateOnly(1961, 6, 8));
        customer.EftAccountId.Should().Be("0053581756");
        customer.PrimaryCardHolderIndicator.Should().Be("Y");
        customer.FicoCreditScore.Should().Be(274);
    }

    [Fact]
    public void TransactionRecordParser_MapsCvtra05yFieldsWithNegativeAmount()
    {
        var line = LegacyDataSeedSource.ReadRecords(TestPaths.AsciiData("dailytran.txt"))[1];

        var transaction = TransactionRecordParser.Parse(line);

        transaction.TransactionId.Should().Be("0000000001774260");
        transaction.TypeCode.Should().Be("03");
        transaction.CategoryCode.Should().Be("0001");
        transaction.Source.Should().Be("OPERATOR");
        transaction.Description.Should().Be("Return item at Nitzsche, Nicolas and Lowe");
        transaction.Amount.Should().Be(-919.00m);
        transaction.MerchantId.Should().Be("800000000");
        transaction.MerchantName.Should().Be("Nitzsche, Nicolas and Lowe");
        transaction.MerchantCity.Should().Be("Fidelshire");
        transaction.MerchantZip.Should().Be("53378");
        transaction.CardNumber.Should().Be("0927987108636232");
        transaction.OriginalTimestamp.Should().Be(new DateTime(2022, 6, 10, 19, 27, 53));
        transaction.ProcessedTimestamp.Should().BeNull();
    }

    [Fact]
    public void TransactionRecordParser_PositiveOverpunchAmount()
    {
        var line = LegacyDataSeedSource.ReadRecords(TestPaths.AsciiData("dailytran.txt"))[0];

        var transaction = TransactionRecordParser.Parse(line);

        transaction.TransactionId.Should().Be("0000000000683580");
        transaction.Amount.Should().Be(504.77m);
    }

    [Fact]
    public void TransactionRecordParser_ParsesMicrosecondTimestamps()
    {
        var line = LegacyDataSeedSource.ReadRecords(TestPaths.AsciiData("dailytran.txt"))[0];
        var withProcTs = line[..304] + "2022-06-11 01:02:03.123456" + line[330..];

        var transaction = TransactionRecordParser.Parse(withProcTs);

        transaction.ProcessedTimestamp.Should().Be(new DateTime(2022, 6, 11, 1, 2, 3).AddTicks(1234560));
        transaction.ProcessedTimestamp!.Value.Kind.Should().Be(DateTimeKind.Unspecified);
    }

    [Fact]
    public void DisclosureGroupRecordParser_MapsCvtra02yFields()
    {
        var group = DisclosureGroupRecordParser.Parse(DiscGroupLine);

        group.AccountGroupId.Should().Be("A000000000");
        group.TransactionTypeCode.Should().Be("01");
        group.TransactionCategoryCode.Should().Be("0001");
        group.InterestRate.Should().Be(15.00m);
    }

    [Fact]
    public void TransactionCategoryBalanceRecordParser_MapsCvtra01yFields()
    {
        var balance = TransactionCategoryBalanceRecordParser.Parse(TcatBalLine);

        balance.AccountId.Should().Be("00000000001");
        balance.TypeCode.Should().Be("01");
        balance.CategoryCode.Should().Be("0001");
        balance.Balance.Should().Be(0m);
    }

    [Fact]
    public void TransactionTypeRecordParser_MapsCvtra03yFields()
    {
        var type = TransactionTypeRecordParser.Parse(TranTypeLine);

        type.TypeCode.Should().Be("01");
        type.Description.Should().Be("Purchase");
    }

    [Fact]
    public void TransactionCategoryRecordParser_MapsCvtra04yFields()
    {
        var category = TransactionCategoryRecordParser.Parse(TranCatLine);

        category.TypeCode.Should().Be("01");
        category.CategoryCode.Should().Be("0001");
        category.Description.Should().Be("Regular Sales Draft");
    }

    [Fact]
    public void LegacyDataSeedSource_StripsCrlfAndSkipsBlankLines()
    {
        var records = LegacyDataSeedSource.ReadRecords(TestPaths.AsciiData("tcatbal.txt"));

        records.Should().HaveCount(50);
        records.Should().OnlyContain(r => r.Length == TransactionCategoryBalanceRecordParser.RecordLength && !r.Contains('\r'));
    }

    [Theory]
    [InlineData("acctdata.txt", 50)]
    [InlineData("carddata.txt", 50)]
    [InlineData("cardxref.txt", 50)]
    [InlineData("custdata.txt", 50)]
    [InlineData("dailytran.txt", 300)]
    [InlineData("discgrp.txt", 51)]
    [InlineData("tcatbal.txt", 50)]
    [InlineData("trancatg.txt", 18)]
    [InlineData("trantype.txt", 7)]
    public void AllAsciiSeedFilesParseWithoutError(string fileName, int expectedRecords)
    {
        var records = LegacyDataSeedSource.ReadRecords(TestPaths.AsciiData(fileName));

        records.Should().HaveCount(expectedRecords);
        var act = () =>
        {
            foreach (var record in records)
            {
                ParseAny(fileName, record);
            }
        };
        act.Should().NotThrow();
    }

    private static object ParseAny(string fileName, string record) => fileName switch
    {
        "acctdata.txt" => AccountRecordParser.Parse(record),
        "carddata.txt" => CardRecordParser.Parse(record),
        "cardxref.txt" => CardXrefRecordParser.Parse(record),
        "custdata.txt" => CustomerRecordParser.Parse(record),
        "dailytran.txt" => TransactionRecordParser.Parse(record),
        "discgrp.txt" => DisclosureGroupRecordParser.Parse(record),
        "tcatbal.txt" => TransactionCategoryBalanceRecordParser.Parse(record),
        "trancatg.txt" => TransactionCategoryRecordParser.Parse(record),
        "trantype.txt" => TransactionTypeRecordParser.Parse(record),
        _ => throw new ArgumentOutOfRangeException(nameof(fileName), fileName, null)
    };
}
