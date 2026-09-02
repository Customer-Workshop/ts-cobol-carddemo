using CardDemo.Application.Transactions;
using CardDemo.Domain.Cards;
using CardDemo.Domain.Dates;
using CardDemo.Domain.Transactions;
using FluentAssertions;

namespace CardDemo.Tests.Transactions;

/// <summary>
/// COTRN02C ENTER / PF5 processing against in-memory stores: message text, validation order,
/// cursor field, normalisation, id allocation and write outcome parity (FR-S09-02..24, 27, 28).
/// </summary>
public class TransactionAddServiceTests
{
    private const string AccountId = "00000000050";
    private const string CardNumber = "0500024453765740";

    private readonly FakeCardXrefRepository _xrefs = new();
    private readonly FakeTransactionRepository _transactions = new();
    private readonly TransactionAddService _service;

    public TransactionAddServiceTests()
    {
        _xrefs.Rows.Add(new CardXref { CardNumber = CardNumber, CustomerId = "000000050", AccountId = AccountId });
        _service = new TransactionAddService(_xrefs, _transactions, new DateValidationService());
    }

    private static TransactionAddRequest Valid(string confirmation = "Y") => new(
        AccountId: AccountId,
        CardNumber: null,
        TypeCode: "01",
        CategoryCode: "0001",
        Source: "POS TERM",
        Description: "Purchase at Abshire-Lowe",
        Amount: "+00000050.47",
        OriginalDate: "2022-06-10",
        ProcessedDate: "2022-06-10",
        MerchantId: "485945261",
        MerchantName: "Abshire-Lowe",
        MerchantCity: "North Enoshaven",
        MerchantZip: "72112",
        Confirmation: confirmation);

    private static Transaction Existing(string id = "0000000996722787") => new()
    {
        TransactionId = id,
        TypeCode = "02",
        CategoryCode = "0002",
        Source = "LAST SRC",
        Description = new string('D', 100),
        Amount = -1234.56m,
        MerchantId = "123456789",
        MerchantName = new string('N', 50),
        MerchantCity = new string('C', 50),
        MerchantZip = "99999-0000",
        CardNumber = "1111222233334444",
        OriginalTimestamp = new DateTime(2023, 1, 2, 3, 4, 5),
        ProcessedTimestamp = new DateTime(2023, 1, 3, 0, 0, 0)
    };

    // ---- key fields (FR-S09-02..10) ----

    [Fact]
    public async Task KeyFields_BothBlank_AsksForAccountOrCard()
    {
        var result = await _service.AddAsync(Valid() with { AccountId = "   ", CardNumber = null });

        result.Outcome.Should().Be(TransactionAddOutcome.ValidationError);
        result.Message.Should().Be("Account or Card Number must be entered...");
        result.CursorField.Should().Be(TransactionAddField.AccountId);
        result.Severity.Should().Be(TransactionAddMessageSeverity.Error);
    }

    [Theory]
    [InlineData("123")]
    [InlineData("0000000005A")]
    [InlineData("50")]
    public async Task AccountNotNumeric_OverTheFullWidth_IsRejected(string accountId)
    {
        var result = await _service.AddAsync(Valid() with { AccountId = accountId });

        result.Message.Should().Be("Account ID must be Numeric...");
        result.CursorField.Should().Be(TransactionAddField.AccountId);
    }

    [Fact]
    public async Task AccountFound_FillsCardNumber_AndOverridesTypedCard()
    {
        var result = await _service.AddAsync(Valid("N") with { CardNumber = "9999999999999999" });

        result.Outcome.Should().Be(TransactionAddOutcome.ConfirmationRequired);
        result.Screen.CardNumber.Should().Be(CardNumber);
        result.Screen.AccountId.Should().Be(AccountId);
    }

    [Fact]
    public async Task AccountNotFound_IsReported()
    {
        var result = await _service.AddAsync(Valid() with { AccountId = "99999999999" });

        result.Outcome.Should().Be(TransactionAddOutcome.KeyNotFound);
        result.Message.Should().Be("Account ID NOT found...");
        result.CursorField.Should().Be(TransactionAddField.AccountId);
    }

    [Fact]
    public async Task AccountStoreError_IsReported()
    {
        _xrefs.Fail = true;

        var result = await _service.AddAsync(Valid());

        result.Outcome.Should().Be(TransactionAddOutcome.LookupError);
        result.Message.Should().Be("Unable to lookup Acct in XREF AIX file...");
        result.CursorField.Should().Be(TransactionAddField.AccountId);
    }

    [Theory]
    [InlineData("12AB")]
    [InlineData("050002445376574")]
    public async Task CardNotNumeric_OverTheFullWidth_IsRejected(string cardNumber)
    {
        var result = await _service.AddAsync(Valid() with { AccountId = null, CardNumber = cardNumber });

        result.Message.Should().Be("Card Number must be Numeric...");
        result.CursorField.Should().Be(TransactionAddField.CardNumber);
    }

    [Fact]
    public async Task CardFound_FillsAccountId()
    {
        var result = await _service.AddAsync(Valid("N") with { AccountId = "", CardNumber = CardNumber });

        result.Outcome.Should().Be(TransactionAddOutcome.ConfirmationRequired);
        result.Screen.AccountId.Should().Be(AccountId);
    }

    [Fact]
    public async Task CardNotFound_IsReported()
    {
        var result = await _service.AddAsync(Valid() with { AccountId = null, CardNumber = "9999999999999999" });

        result.Outcome.Should().Be(TransactionAddOutcome.KeyNotFound);
        result.Message.Should().Be("Card Number NOT found...");
        result.CursorField.Should().Be(TransactionAddField.CardNumber);
    }

    [Fact]
    public async Task CardStoreError_IsReported()
    {
        _xrefs.Fail = true;

        var result = await _service.AddAsync(Valid() with { AccountId = null, CardNumber = CardNumber });

        result.Outcome.Should().Be(TransactionAddOutcome.LookupError);
        result.Message.Should().Be("Unable to lookup Card # in XREF file...");
        result.CursorField.Should().Be(TransactionAddField.CardNumber);
    }

    // ---- mandatory fields in source order (FR-S09-11) ----

    public static TheoryData<TransactionAddField, string> MandatoryFieldMessages => new()
    {
        { TransactionAddField.TypeCode, "Type CD can NOT be empty..." },
        { TransactionAddField.CategoryCode, "Category CD can NOT be empty..." },
        { TransactionAddField.Source, "Source can NOT be empty..." },
        { TransactionAddField.Description, "Description can NOT be empty..." },
        { TransactionAddField.Amount, "Amount can NOT be empty..." },
        { TransactionAddField.OriginalDate, "Orig Date can NOT be empty..." },
        { TransactionAddField.ProcessedDate, "Proc Date can NOT be empty..." },
        { TransactionAddField.MerchantId, "Merchant ID can NOT be empty..." },
        { TransactionAddField.MerchantName, "Merchant Name can NOT be empty..." },
        { TransactionAddField.MerchantCity, "Merchant City can NOT be empty..." },
        { TransactionAddField.MerchantZip, "Merchant Zip can NOT be empty..." }
    };

    [Theory]
    [MemberData(nameof(MandatoryFieldMessages))]
    public async Task MandatoryFields_FirstBlankFieldInSourceOrderWins(TransactionAddField blankField, string expectedMessage)
    {
        // Blank the field under test and every field after it: the message must be the one for the earliest blank.
        var request = Valid();
        var fields = Enum.GetValues<TransactionAddField>()
            .Where(f => f >= blankField && f != TransactionAddField.Confirmation);
        foreach (var field in fields)
        {
            request = Blank(request, field);
        }

        var result = await _service.AddAsync(request);

        result.Outcome.Should().Be(TransactionAddOutcome.ValidationError);
        result.Message.Should().Be(expectedMessage);
        result.CursorField.Should().Be(blankField);
    }

    [Fact]
    public async Task MandatoryFields_AreCheckedBeforeAnyFormatEdit()
    {
        var result = await _service.AddAsync(Valid() with { TypeCode = "AB", MerchantZip = "" });

        result.Message.Should().Be("Merchant Zip can NOT be empty...");
    }

    // ---- format edits (FR-S09-12..17) ----

    [Theory]
    [InlineData("1")]
    [InlineData("AB")]
    [InlineData("1 ")]
    public async Task TypeCode_MustBeNumericOverTheFullWidth(string typeCode)
    {
        var result = await _service.AddAsync(Valid() with { TypeCode = typeCode });

        result.Message.Should().Be("Type CD must be Numeric...");
        result.CursorField.Should().Be(TransactionAddField.TypeCode);
    }

    [Fact]
    public async Task CategoryCode_MustBeNumeric_AfterTypeCode()
    {
        var result = await _service.AddAsync(Valid() with { CategoryCode = "1" });

        result.Message.Should().Be("Category CD must be Numeric...");
        result.CursorField.Should().Be(TransactionAddField.CategoryCode);

        var both = await _service.AddAsync(Valid() with { TypeCode = "X1", CategoryCode = "1" });
        both.Message.Should().Be("Type CD must be Numeric...");
    }

    [Theory]
    [InlineData("100.00")]
    [InlineData("-100.00")]
    [InlineData("+1234567890.00")]
    [InlineData("+00000100,00")]
    [InlineData("00000100.001")]
    [InlineData("+0000010.500")]
    [InlineData("+00000100.5x")]
    public async Task Amount_MustMatchSignEightDigitsPointTwoDigits(string amount)
    {
        var result = await _service.AddAsync(Valid() with { Amount = amount });

        result.Message.Should().Be("Amount should be in format -99999999.99");
        result.CursorField.Should().Be(TransactionAddField.Amount);
        result.Screen.Amount.Should().Be(amount.Length > 12 ? amount[..12] : amount, "the amount is not normalised until the layout edit passes");
    }

    [Theory]
    [InlineData("2024/01/15", "Orig Date should be in format YYYY-MM-DD", TransactionAddField.OriginalDate)]
    [InlineData("20240115", "Orig Date should be in format YYYY-MM-DD", TransactionAddField.OriginalDate)]
    [InlineData("2024-1-15", "Orig Date should be in format YYYY-MM-DD", TransactionAddField.OriginalDate)]
    public async Task OriginalDate_MustBeFourDashTwoDashTwo(string date, string message, TransactionAddField cursor)
    {
        var result = await _service.AddAsync(Valid() with { OriginalDate = date });

        result.Message.Should().Be(message);
        result.CursorField.Should().Be(cursor);
    }

    [Fact]
    public async Task ProcessedDate_LayoutIsCheckedAfterOriginalDate()
    {
        var result = await _service.AddAsync(Valid() with { ProcessedDate = "2024.01.15" });
        result.Message.Should().Be("Proc Date should be in format YYYY-MM-DD");
        result.CursorField.Should().Be(TransactionAddField.ProcessedDate);

        var both = await _service.AddAsync(Valid() with { OriginalDate = "20240115", ProcessedDate = "2024.01.15" });
        both.Message.Should().Be("Orig Date should be in format YYYY-MM-DD");
    }

    [Fact]
    public async Task Amount_IsNormalisedToPlusEightDigitsBeforeLaterEdits()
    {
        var result = await _service.AddAsync(Valid() with { Amount = "-00000100.50", OriginalDate = "2024-02-30" });

        result.Message.Should().Be("Orig Date - Not a valid date...");
        result.Screen.Amount.Should().Be("-00000100.50");

        var positive = await _service.AddAsync(Valid("N") with { Amount = "+00000000.05" });
        positive.Screen.Amount.Should().Be("+00000000.05");
    }

    [Theory]
    [InlineData("2024-02-30")]
    [InlineData("2024-13-01")]
    [InlineData("2023-00-10")]
    [InlineData("2023-04-31")]
    [InlineData("0000-01-01")]
    public async Task OriginalDate_NotValid_IsRejected(string date)
    {
        var result = await _service.AddAsync(Valid() with { OriginalDate = date });

        result.Message.Should().Be("Orig Date - Not a valid date...");
        result.CursorField.Should().Be(TransactionAddField.OriginalDate);
    }

    [Fact]
    public async Task ProcessedDate_NotValid_IsRejected_AfterOriginalDate()
    {
        var result = await _service.AddAsync(Valid() with { ProcessedDate = "2024-00-10" });
        result.Message.Should().Be("Proc Date - Not a valid date...");
        result.CursorField.Should().Be(TransactionAddField.ProcessedDate);

        var both = await _service.AddAsync(Valid() with { OriginalDate = "2024-02-30", ProcessedDate = "2024-00-10" });
        both.Message.Should().Be("Orig Date - Not a valid date...");
    }

    [Fact]
    public async Task LeapDay_IsAccepted()
    {
        var result = await _service.AddAsync(Valid("N") with { OriginalDate = "2024-02-29", ProcessedDate = "2000-02-29" });

        result.Outcome.Should().Be(TransactionAddOutcome.ConfirmationRequired);
    }

    [Fact]
    public async Task DateBefore1582_UnsupportedRange2513_IsAcceptedLikeTheSource()
    {
        var result = await _service.AddAsync(Valid() with { OriginalDate = "1500-01-01", ProcessedDate = "0001-01-01" });

        result.Outcome.Should().Be(TransactionAddOutcome.Added);
        _transactions.Rows.Single().OriginalTimestamp.Should().Be(new DateTime(1500, 1, 1));
        _transactions.Rows.Single().ProcessedTimestamp.Should().Be(new DateTime(1, 1, 1));
    }

    [Theory]
    [InlineData("12345678A")]
    [InlineData("12345678")]
    public async Task MerchantId_MustBeNumeric_AndIsCheckedAfterTheDates(string merchantId)
    {
        var result = await _service.AddAsync(Valid() with { MerchantId = merchantId });
        result.Message.Should().Be("Merchant ID must be Numeric...");
        result.CursorField.Should().Be(TransactionAddField.MerchantId);

        var dateFirst = await _service.AddAsync(Valid() with { MerchantId = merchantId, ProcessedDate = "2024-02-30" });
        dateFirst.Message.Should().Be("Proc Date - Not a valid date...");
    }

    // ---- confirmation (FR-S09-18, 19) ----

    [Theory]
    [InlineData("")]
    [InlineData(" ")]
    [InlineData(null)]
    [InlineData("N")]
    [InlineData("n")]
    public async Task ConfirmBlankOrN_PromptsAndWritesNothing(string? confirmation)
    {
        var result = await _service.AddAsync(Valid(confirmation!) with { Confirmation = confirmation });

        result.Outcome.Should().Be(TransactionAddOutcome.ConfirmationRequired);
        result.Message.Should().Be("Confirm to add this transaction...");
        result.CursorField.Should().Be(TransactionAddField.Confirmation);
        result.Severity.Should().Be(TransactionAddMessageSeverity.Error);
        result.Screen.TypeCode.Should().Be("01", "typed values are kept on the screen");
        _transactions.Rows.Should().BeEmpty();
    }

    [Theory]
    [InlineData("X")]
    [InlineData("1")]
    public async Task ConfirmInvalid_IsRejected(string confirmation)
    {
        var result = await _service.AddAsync(Valid(confirmation));

        result.Outcome.Should().Be(TransactionAddOutcome.InvalidConfirmation);
        result.Message.Should().Be("Invalid value. Valid values are (Y/N)...");
        result.CursorField.Should().Be(TransactionAddField.Confirmation);
        _transactions.Rows.Should().BeEmpty();
    }

    [Fact]
    public async Task Confirmation_IsOnlyEvaluatedAfterAllEdits()
    {
        var result = await _service.AddAsync(Valid("X") with { MerchantZip = "" });

        result.Message.Should().Be("Merchant Zip can NOT be empty...");
    }

    // ---- add (FR-S09-20..24) ----

    [Theory]
    [InlineData("Y")]
    [InlineData("y")]
    public async Task Add_UsesHighestIdPlusOne_AndMapsTheRecord(string confirmation)
    {
        _transactions.Rows.Add(Existing("0000000996722787"));
        _transactions.Rows.Add(Existing("0000000000000001"));

        var result = await _service.AddAsync(Valid(confirmation) with { Amount = "-00000050.47", Description = "Purchase at Abshire-Lowe" });

        result.Outcome.Should().Be(TransactionAddOutcome.Added);
        result.TransactionId.Should().Be("0000000996722788");
        var written = _transactions.Rows.Single(t => t.TransactionId == "0000000996722788");
        written.TypeCode.Should().Be("01");
        written.CategoryCode.Should().Be("0001");
        written.Source.Should().Be("POS TERM");
        written.Description.Should().Be("Purchase at Abshire-Lowe");
        written.Amount.Should().Be(-50.47m);
        written.CardNumber.Should().Be(CardNumber, "the card comes from the xref, not the screen");
        written.MerchantId.Should().Be("485945261");
        written.MerchantName.Should().Be("Abshire-Lowe");
        written.MerchantCity.Should().Be("North Enoshaven");
        written.MerchantZip.Should().Be("72112");
        written.OriginalTimestamp.Should().Be(new DateTime(2022, 6, 10));
        written.ProcessedTimestamp.Should().Be(new DateTime(2022, 6, 10));
    }

    [Fact]
    public async Task Add_OnEmptyFile_UsesIdOne()
    {
        var result = await _service.AddAsync(Valid());

        result.TransactionId.Should().Be("0000000000000001");
    }

    [Fact]
    public async Task Add_Success_ClearsTheScreenAndShowsGreenMessage()
    {
        _transactions.Rows.Add(Existing("0000000001774260"));

        var result = await _service.AddAsync(Valid());

        result.Message.Should().Be("Transaction added successfully.  Your Tran ID is 0000000001774261.");
        result.Severity.Should().Be(TransactionAddMessageSeverity.Success);
        result.CursorField.Should().Be(TransactionAddField.AccountId);
        result.Screen.Should().BeEquivalentTo(new TransactionAddRequest("", "", "", "", "", "", "", "", "", "", "", "", "", ""));
    }

    [Fact]
    public async Task Add_DuplicateId_IsReportedAndScreenRetained()
    {
        _transactions.DuplicateOnWrite = true;

        var result = await _service.AddAsync(Valid());

        result.Outcome.Should().Be(TransactionAddOutcome.DuplicateTransactionId);
        result.Message.Should().Be("Tran ID already exist...");
        result.CursorField.Should().Be(TransactionAddField.AccountId);
        result.Screen.TypeCode.Should().Be("01");
        result.Screen.Confirmation.Should().Be("Y");
    }

    [Fact]
    public async Task Add_StoreError_IsReported()
    {
        _transactions.FailWrite = true;

        var result = await _service.AddAsync(Valid());

        result.Outcome.Should().Be(TransactionAddOutcome.WriteError);
        result.Message.Should().Be("Unable to Add Transaction...");
        result.CursorField.Should().Be(TransactionAddField.AccountId);
    }

    [Fact]
    public async Task Add_BrowseLastFails_IsReported()
    {
        _transactions.FailRead = true;

        var result = await _service.AddAsync(Valid());

        result.Outcome.Should().Be(TransactionAddOutcome.LookupError);
        result.Message.Should().Be("Unable to lookup Transaction...");
        result.CursorField.Should().Be(TransactionAddField.AccountId);
    }

    [Theory]
    [InlineData(null, "0000000000000001")]
    [InlineData("0000000000000009", "0000000000000010")]
    [InlineData("9999999999999998", "9999999999999999")]
    public void NextTransactionId_IsSixteenDigitIncrement(string? highest, string expected) =>
        TransactionAddService.NextTransactionId(highest).Should().Be(expected);

    [Theory]
    [InlineData(0, "+00000000.00")]
    [InlineData(50.47, "+00000050.47")]
    [InlineData(-1234.5, "-00001234.50")]
    [InlineData(99999999.99, "+99999999.99")]
    public void FormatAmount_IsPicPlusEightDigitsPointTwo(decimal amount, string expected) =>
        TransactionAddService.FormatAmount(amount).Should().Be(expected);

    // ---- copy last (FR-S09-27, 28) ----

    [Fact]
    public async Task CopyLast_FillsDataFieldsFromHighestTransaction_TruncatedToScreenWidths()
    {
        _transactions.Rows.Add(Existing("0000000000000005"));
        _transactions.Rows.Add(Existing("0000000000000009"));

        var result = await _service.CopyLastAsync(new TransactionAddRequest(AccountId, null, null, null, null, null, null, null, null, null, null, null, null, null));

        result.Outcome.Should().Be(TransactionAddOutcome.ConfirmationRequired);
        result.Message.Should().Be("Confirm to add this transaction...");
        result.Screen.AccountId.Should().Be(AccountId);
        result.Screen.CardNumber.Should().Be(CardNumber);
        result.Screen.TypeCode.Should().Be("02");
        result.Screen.CategoryCode.Should().Be("0002");
        result.Screen.Source.Should().Be("LAST SRC");
        result.Screen.Amount.Should().Be("-00001234.56");
        result.Screen.Description.Should().Be(new string('D', 60));
        result.Screen.OriginalDate.Should().Be("2023-01-02");
        result.Screen.ProcessedDate.Should().Be("2023-01-03");
        result.Screen.MerchantId.Should().Be("123456789");
        result.Screen.MerchantName.Should().Be(new string('N', 30));
        result.Screen.MerchantCity.Should().Be(new string('C', 25));
        result.Screen.MerchantZip.Should().Be("99999-0000");
        _transactions.Rows.Should().HaveCount(2);
    }

    [Fact]
    public async Task CopyLast_WithConfirmY_AddsImmediately()
    {
        _transactions.Rows.Add(Existing("0000000000000009"));

        var result = await _service.CopyLastAsync(Valid("Y"));

        result.Outcome.Should().Be(TransactionAddOutcome.Added);
        result.TransactionId.Should().Be("0000000000000010");
        _transactions.Rows.Single(t => t.TransactionId == "0000000000000010").Amount.Should().Be(-1234.56m);
    }

    [Fact]
    public async Task CopyLast_RunsKeyEditsFirst()
    {
        _transactions.Rows.Add(Existing());

        var result = await _service.CopyLastAsync(TransactionAddRequest.Empty);

        result.Message.Should().Be("Account or Card Number must be entered...");
        var notFound = await _service.CopyLastAsync(TransactionAddRequest.Empty with { CardNumber = "9999999999999999" });
        notFound.Message.Should().Be("Card Number NOT found...");
    }

    [Fact]
    public async Task CopyLast_OnEmptyFile_CopiesBlanksSoTypeCodeIsReportedEmpty()
    {
        var result = await _service.CopyLastAsync(Valid());

        result.Message.Should().Be("Type CD can NOT be empty...");
        result.Screen.Amount.Should().BeEmpty();
    }

    [Fact]
    public async Task CopyLast_BrowseFails_IsReported()
    {
        _transactions.FailRead = true;

        var result = await _service.CopyLastAsync(Valid());

        result.Message.Should().Be("Unable to lookup Transaction...");
        result.CursorField.Should().Be(TransactionAddField.AccountId);
    }

    // ---- request helpers ----

    [Fact]
    public void OverLengthFields_ReportsValuesWiderThanTheBmsField()
    {
        var request = Valid() with { TypeCode = "011", MerchantZip = "12345-678901" };

        request.OverLengthFields().Should().Equal(TransactionAddField.TypeCode, TransactionAddField.MerchantZip);
        Valid().OverLengthFields().Should().BeEmpty();
    }

    private static TransactionAddRequest Blank(TransactionAddRequest request, TransactionAddField field) => field switch
    {
        TransactionAddField.TypeCode => request with { TypeCode = "" },
        TransactionAddField.CategoryCode => request with { CategoryCode = " " },
        TransactionAddField.Source => request with { Source = null },
        TransactionAddField.Description => request with { Description = "" },
        TransactionAddField.Amount => request with { Amount = "" },
        TransactionAddField.OriginalDate => request with { OriginalDate = "" },
        TransactionAddField.ProcessedDate => request with { ProcessedDate = "" },
        TransactionAddField.MerchantId => request with { MerchantId = "" },
        TransactionAddField.MerchantName => request with { MerchantName = "" },
        TransactionAddField.MerchantCity => request with { MerchantCity = "" },
        TransactionAddField.MerchantZip => request with { MerchantZip = "" },
        _ => request
    };
}
