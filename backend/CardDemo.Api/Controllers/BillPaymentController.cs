using CardDemo.Application.BillPayment;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CardDemo.Api.Controllers;

public record BillPaymentRequestDto(string? AccountId, string? Confirm);

public record BillPaymentResponseDto(
    string Outcome,
    string Message,
    string? Severity,
    string CursorField,
    string? CurrentBalance,
    string? TransactionId,
    bool ClearScreen);

/// <summary>Bill payment endpoint replacing CICS transaction CB00 (COBIL00C).</summary>
[ApiController]
[Route("api/v1/bill-payment")]
[Authorize]
public class BillPaymentController(BillPaymentService billPaymentService) : ControllerBase
{
    [HttpPost]
    public async Task<IActionResult> Pay([FromBody] BillPaymentRequestDto request, CancellationToken cancellationToken)
    {
        var result = await billPaymentService.PayAsync(
            new BillPaymentRequest(request.AccountId, request.Confirm),
            cancellationToken);
        return Ok(ToDto(result));
    }

    private static BillPaymentResponseDto ToDto(BillPaymentResult result) => new(
        result.Outcome switch
        {
            BillPaymentOutcome.AccountIdRequired => "accountIdRequired",
            BillPaymentOutcome.InvalidConfirmation => "invalidConfirmation",
            BillPaymentOutcome.Declined => "declined",
            BillPaymentOutcome.AccountNotFound => "accountNotFound",
            BillPaymentOutcome.AccountLookupError => "accountLookupError",
            BillPaymentOutcome.NothingToPay => "nothingToPay",
            BillPaymentOutcome.ConfirmationRequired => "confirmationRequired",
            BillPaymentOutcome.CardNotFound => "cardNotFound",
            BillPaymentOutcome.CardLookupError => "cardLookupError",
            BillPaymentOutcome.TransactionLookupError => "transactionLookupError",
            BillPaymentOutcome.DuplicateTransaction => "duplicateTransaction",
            BillPaymentOutcome.TransactionWriteError => "transactionWriteError",
            BillPaymentOutcome.AccountUpdateError => "accountUpdateError",
            _ => "paymentSuccessful"
        },
        result.Message,
        result.Severity switch
        {
            BillPaymentMessageSeverity.Error => "error",
            BillPaymentMessageSeverity.Success => "success",
            _ => null
        },
        result.CursorField == BillPaymentCursorField.Confirm ? "confirm" : "accountId",
        result.CurrentBalance,
        result.TransactionId,
        result.ClearScreen);
}
