using CardDemo.Application.Transactions;
using FluentAssertions;

namespace CardDemo.Tests.Transactions;

/// <summary>
/// Parity tests for COTRN01C PROCESS-ENTER-KEY / READ-TRANSACT-FILE
/// (app/cbl/COTRN01C.cbl:144-192, 267-296): FR-S08-04, 06, 07, 08, 12.
/// </summary>
public class TransactionViewServiceTests
{
    private static (TransactionViewService Service, TransactionViewTestRepository Repository) Build()
    {
        var repository = new TransactionViewTestRepository();
        repository.Add(TransactionViewTestRepository.SampleTransaction());
        return (new TransactionViewService(repository), repository);
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("                ")]
    public async Task BlankId_ReturnsLegacyEmptyMessageWithoutReading(string? tranId)
    {
        // FR-S08-04
        var (service, repository) = Build();

        var result = await service.ViewAsync(tranId);

        result.Outcome.Should().Be(TransactionViewOutcome.MissingTransactionId);
        result.Message.Should().Be("Tran ID can NOT be empty...");
        result.Detail.Should().BeNull();
        repository.RequestedKeys.Should().BeEmpty();
    }

    [Fact]
    public async Task UnknownId_ReturnsLegacyNotFoundMessage()
    {
        // FR-S08-06
        var (service, _) = Build();

        var result = await service.ViewAsync("NOPE000000000001");

        result.Outcome.Should().Be(TransactionViewOutcome.NotFound);
        result.Message.Should().Be("Transaction ID NOT found...");
        result.Detail.Should().BeNull();
    }

    [Fact]
    public async Task StoreFailure_ReturnsLegacyLookupErrorMessage()
    {
        // FR-S08-07
        var (service, repository) = Build();
        repository.ThrowOnRead = true;

        var result = await service.ViewAsync("0000000000683580");

        result.Outcome.Should().Be(TransactionViewOutcome.StoreError);
        result.Message.Should().Be("Unable to lookup Transaction...");
        result.Detail.Should().BeNull();
    }

    [Fact]
    public async Task KnownId_ReturnsAllThirteenScreenFieldsAndNoMessage()
    {
        // FR-S08-08
        var (service, _) = Build();

        var result = await service.ViewAsync("0000000000683580");

        result.IsFound.Should().BeTrue();
        result.Message.Should().BeNull();
        result.Detail.Should().Be(new TransactionViewDetail(
            "0000000000683580",
            "4859452612877065",
            "01",
            "0001",
            "POS TERM",
            "Purchase at Abshire-Lowe",
            "+00000504.77",
            "2022-06-10",
            "",
            "800000000",
            "Abshire-Lowe",
            "North Enoshaven",
            "72112"));
    }

    [Fact]
    public async Task Id_TrailingBlanksAreKeyPadding()
    {
        // FR-S08-12: TRNIDINI is a space-padded X(16) field
        var (service, repository) = Build();

        var result = await service.ViewAsync("0000000000683580".PadRight(16));

        result.IsFound.Should().BeTrue();
        repository.RequestedKeys.Should().Equal("0000000000683580");
    }

    [Theory]
    [InlineData("abcdefghijklmnop", "ABCDEFGHIJKLMNOP")]
    [InlineData(" 000000000683580", "0000000000683580")]
    public async Task Id_IsUsedVerbatimWithoutCaseFoldingOrLeadingBlankRemoval(string typed, string stored)
    {
        // FR-S08-12
        var repository = new TransactionViewTestRepository();
        repository.Add(TransactionViewTestRepository.SampleTransaction(stored));
        var service = new TransactionViewService(repository);

        var result = await service.ViewAsync(typed);

        result.Outcome.Should().Be(TransactionViewOutcome.NotFound);
        repository.RequestedKeys.Should().Equal(typed);
    }
}
