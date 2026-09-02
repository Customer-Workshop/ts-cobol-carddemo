using CardDemo.Application.Transactions;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CardDemo.Api.Controllers;

public record TransactionViewDto(
    string TransactionId,
    string CardNumber,
    string TypeCode,
    string CategoryCode,
    string Source,
    string Description,
    string Amount,
    string OriginalDate,
    string ProcessedDate,
    string MerchantId,
    string MerchantName,
    string MerchantCity,
    string MerchantZip);

public record TransactionViewErrorDto(string Message);

/// <summary>Transaction view endpoint replacing CICS transaction CT01 (COTRN01C).</summary>
[ApiController]
[Route("api/v1/transactions")]
[Authorize]
public class TransactionViewController(TransactionViewService transactionViewService) : ControllerBase
{
    [HttpGet("view")]
    public async Task<IActionResult> View([FromQuery] string? tranId, CancellationToken cancellationToken)
    {
        var result = await transactionViewService.ViewAsync(tranId, cancellationToken);

        return result.Outcome switch
        {
            TransactionViewOutcome.Found => Ok(ToDto(result.Detail!)),
            TransactionViewOutcome.MissingTransactionId => BadRequest(new TransactionViewErrorDto(result.Message!)),
            TransactionViewOutcome.NotFound => NotFound(new TransactionViewErrorDto(result.Message!)),
            _ => StatusCode(StatusCodes.Status500InternalServerError, new TransactionViewErrorDto(result.Message!))
        };
    }

    private static TransactionViewDto ToDto(TransactionViewDetail detail) =>
        new(
            detail.TransactionId,
            detail.CardNumber,
            detail.TypeCode,
            detail.CategoryCode,
            detail.Source,
            detail.Description,
            detail.Amount,
            detail.OriginalDate,
            detail.ProcessedDate,
            detail.MerchantId,
            detail.MerchantName,
            detail.MerchantCity,
            detail.MerchantZip);
}
