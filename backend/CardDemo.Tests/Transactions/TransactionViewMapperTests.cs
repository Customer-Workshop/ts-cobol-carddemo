using CardDemo.Application.Transactions;
using FluentAssertions;

namespace CardDemo.Tests.Transactions;

/// <summary>
/// Screen-field derivations of COTRN01C (app/cbl/COTRN01C.cbl:49, 176-190) against the
/// COTRN1A map lengths (app/bms/COTRN01.bms): FR-S08-09, 10, 11.
/// </summary>
public class TransactionViewMapperTests
{
    [Theory]
    [InlineData("504.77", "+00000504.77")]
    [InlineData("-919.00", "-00000919.00")]
    [InlineData("0", "+00000000.00")]
    [InlineData("0.05", "+00000000.05")]
    [InlineData("99999999.99", "+99999999.99")]
    [InlineData("123456789.12", "+23456789.12")]
    [InlineData("-123456789.12", "-23456789.12")]
    public void Amount_FollowsEditPicturePlus8Digits2Decimals(string amount, string expected)
    {
        // FR-S08-09: WS-TRAN-AMT PIC +99999999.99
        TransactionViewMapper.FormatAmount(decimal.Parse(amount, System.Globalization.CultureInfo.InvariantCulture))
            .Should().Be(expected).And.HaveLength(12);
    }

    [Fact]
    public void Timestamps_KeepOnlyTheTenCharacterDatePart_BlankWhenAbsent()
    {
        // FR-S08-10
        var transaction = InMemoryTransactionRepository.SampleTransaction();
        transaction.OriginalTimestamp = new DateTime(2022, 6, 10, 19, 27, 53, 123);
        transaction.ProcessedTimestamp = null;

        var detail = TransactionViewMapper.ToDetail(transaction);

        detail.OriginalDate.Should().Be("2022-06-10");
        detail.ProcessedDate.Should().Be("");
    }

    [Fact]
    public void LongText_IsTruncatedToMapLengths_ShortFieldsPassThrough()
    {
        // FR-S08-11: TDESC X(60), MNAME X(30), MCITY X(25)
        var transaction = InMemoryTransactionRepository.SampleTransaction();
        transaction.Description = new string('D', 100);
        transaction.MerchantName = new string('N', 50);
        transaction.MerchantCity = new string('C', 50);

        var detail = TransactionViewMapper.ToDetail(transaction);

        detail.Description.Should().Be(new string('D', 60));
        detail.MerchantName.Should().Be(new string('N', 30));
        detail.MerchantCity.Should().Be(new string('C', 25));
        detail.TransactionId.Should().Be("0000000000683580");
        detail.CardNumber.Should().Be("4859452612877065");
        detail.TypeCode.Should().Be("01");
        detail.CategoryCode.Should().Be("0001");
        detail.Source.Should().Be("POS TERM");
        detail.MerchantId.Should().Be("800000000");
        detail.MerchantZip.Should().Be("72112");
    }

    [Fact]
    public void ShortText_IsNotPadded()
    {
        // FR-S08-11: values shorter than the map field are shown as-is
        var detail = TransactionViewMapper.ToDetail(InMemoryTransactionRepository.SampleTransaction());

        detail.Description.Should().Be("Purchase at Abshire-Lowe");
        detail.MerchantName.Should().Be("Abshire-Lowe");
        detail.MerchantCity.Should().Be("North Enoshaven");
    }
}
