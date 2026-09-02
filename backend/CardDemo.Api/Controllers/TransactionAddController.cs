using CardDemo.Application.Transactions;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CardDemo.Api.Controllers;

/// <summary>The 14 COTRN2A input fields, as keyed (any field may be omitted / null = blank).</summary>
public record TransactionAddScreenDto(
    string? AccountId,
    string? CardNumber,
    string? TypeCode,
    string? CategoryCode,
    string? Source,
    string? Description,
    string? Amount,
    string? OriginalDate,
    string? ProcessedDate,
    string? MerchantId,
    string? MerchantName,
    string? MerchantCity,
    string? MerchantZip,
    string? Confirmation);

/// <summary>Redisplayed screen: echoed values, ERRMSG line, colour, cursor field, and the new TRAN-ID on success.</summary>
public record TransactionAddResponseDto(
    string Outcome,
    TransactionAddScreenDto Screen,
    string Message,
    string Severity,
    string CursorField,
    string? TransactionId);

public record TransactionAddErrorDto(string Message, IReadOnlyList<string> Fields);

/// <summary>Add-transaction endpoints replacing CICS transaction CT02 (COTRN02C).</summary>
[ApiController]
[Route("api/v1/transactions/add")]
[Authorize]
public class TransactionAddController(TransactionAddService transactionAddService) : ControllerBase
{
    public const string OverLengthMessage = "Field value exceeds the screen field length.";

    /// <summary>ENTER: validate, confirm, and write.</summary>
    [HttpPost]
    public async Task<IActionResult> Add([FromBody] TransactionAddScreenDto request, CancellationToken cancellationToken)
    {
        var screen = ToRequest(request);
        var overLength = screen.OverLengthFields();
        if (overLength.Count > 0)
        {
            return BadRequest(new TransactionAddErrorDto(OverLengthMessage, overLength.Select(FieldName).ToList()));
        }

        var result = await transactionAddService.AddAsync(screen, cancellationToken);
        return Ok(ToDto(result));
    }

    /// <summary>PF5: copy the last transaction's data fields, then ENTER processing.</summary>
    [HttpPost("copy-last")]
    public async Task<IActionResult> CopyLast([FromBody] TransactionAddScreenDto request, CancellationToken cancellationToken)
    {
        var screen = ToRequest(request);
        var overLength = screen.OverLengthFields();
        if (overLength.Count > 0)
        {
            return BadRequest(new TransactionAddErrorDto(OverLengthMessage, overLength.Select(FieldName).ToList()));
        }

        var result = await transactionAddService.CopyLastAsync(screen, cancellationToken);
        return Ok(ToDto(result));
    }

    private static TransactionAddRequest ToRequest(TransactionAddScreenDto dto) => new(
        dto.AccountId,
        dto.CardNumber,
        dto.TypeCode,
        dto.CategoryCode,
        dto.Source,
        dto.Description,
        dto.Amount,
        dto.OriginalDate,
        dto.ProcessedDate,
        dto.MerchantId,
        dto.MerchantName,
        dto.MerchantCity,
        dto.MerchantZip,
        dto.Confirmation);

    private static TransactionAddScreenDto ToScreenDto(TransactionAddRequest screen) => new(
        screen.AccountId,
        screen.CardNumber,
        screen.TypeCode,
        screen.CategoryCode,
        screen.Source,
        screen.Description,
        screen.Amount,
        screen.OriginalDate,
        screen.ProcessedDate,
        screen.MerchantId,
        screen.MerchantName,
        screen.MerchantCity,
        screen.MerchantZip,
        screen.Confirmation);

    private static TransactionAddResponseDto ToDto(TransactionAddResult result) => new(
        result.Outcome switch
        {
            TransactionAddOutcome.Added => "added",
            TransactionAddOutcome.ConfirmationRequired => "confirmationRequired",
            TransactionAddOutcome.InvalidConfirmation => "invalidConfirmation",
            TransactionAddOutcome.ValidationError => "validationError",
            TransactionAddOutcome.KeyNotFound => "keyNotFound",
            TransactionAddOutcome.LookupError => "lookupError",
            TransactionAddOutcome.DuplicateTransactionId => "duplicateTransactionId",
            _ => "writeError"
        },
        ToScreenDto(result.Screen),
        result.Message,
        result.Severity == TransactionAddMessageSeverity.Success ? "success" : "error",
        FieldName(result.CursorField),
        result.TransactionId);

    private static string FieldName(TransactionAddField field) =>
        char.ToLowerInvariant(field.ToString()[0]) + field.ToString()[1..];
}
