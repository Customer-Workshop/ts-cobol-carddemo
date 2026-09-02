using CardDemo.Application.Menu;
using CardDemo.Application.Transactions;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CardDemo.Api.Controllers;

public record TransactionListStateDto(string? FirstTranId, string? LastTranId, int PageNumber, bool NextPageAvailable);

public record TransactionListRequestDto(
    string? Action,
    string? SearchTranId,
    string? SelectionFlag,
    string? SelectedTranId,
    TransactionListStateDto? State);

public record TransactionListRowDto(string TranId, string Date, string Description, string Amount);

public record TransactionListResponseDto(
    string Outcome,
    string? Message,
    string? Severity,
    IReadOnlyList<TransactionListRowDto>? Rows,
    bool ClearSearchInput,
    TransactionListStateDto State,
    string? SelectedTranId,
    MenuNavigationTargetDto? Target);

public record TransactionListErrorDto(string Message);

/// <summary>Transaction list endpoint replacing CICS transaction CT00 (COTRN00C).</summary>
[ApiController]
[Route("api/v1/transactions")]
[Authorize]
public class TransactionsController(TransactionListService transactionListService) : ControllerBase
{
    [HttpPost("list")]
    public async Task<IActionResult> List([FromBody] TransactionListRequestDto request, CancellationToken cancellationToken)
    {
        if (!TryParseAction(request.Action, out var action))
        {
            return BadRequest(new TransactionListErrorDto($"Unknown action '{request.Action}'."));
        }

        var state = request.State is null
            ? TransactionListState.Initial
            : new TransactionListState(
                request.State.FirstTranId ?? string.Empty,
                request.State.LastTranId ?? string.Empty,
                request.State.PageNumber,
                request.State.NextPageAvailable);

        var result = await transactionListService.ProcessAsync(
            new TransactionListRequest(action, request.SearchTranId, request.SelectionFlag, request.SelectedTranId, state),
            cancellationToken);

        if (result.Outcome == TransactionListOutcome.StoreError)
        {
            return StatusCode(StatusCodes.Status500InternalServerError, new TransactionListErrorDto(result.Message));
        }
        return Ok(ToDto(result));
    }

    private static bool TryParseAction(string? action, out TransactionListAction parsed)
    {
        switch ((action ?? "enter").Trim().ToLowerInvariant())
        {
            case "enter":
                parsed = TransactionListAction.Enter;
                return true;
            case "pagebackward":
                parsed = TransactionListAction.PageBackward;
                return true;
            case "pageforward":
                parsed = TransactionListAction.PageForward;
                return true;
            default:
                parsed = TransactionListAction.Enter;
                return false;
        }
    }

    private static TransactionListResponseDto ToDto(TransactionListResult result) => new(
        result.Outcome switch
        {
            TransactionListOutcome.ComingSoon => "comingSoon",
            TransactionListOutcome.NotInstalled => "notInstalled",
            TransactionListOutcome.Navigate => "navigate",
            _ => "redisplay"
        },
        result.Message.Length > 0 ? result.Message : null,
        result.Severity switch
        {
            MenuMessageSeverity.Error => "error",
            MenuMessageSeverity.Info => "info",
            _ => null
        },
        result.Rows?.Select(r => new TransactionListRowDto(r.TranId, r.Date, r.Description, r.Amount)).ToList(),
        result.ClearSearchInput,
        new TransactionListStateDto(result.State.FirstTranId, result.State.LastTranId, result.State.PageNumber, result.State.NextPageAvailable),
        result.SelectedTranId,
        result.Target is null
            ? null
            : new MenuNavigationTargetDto(result.Target.Id, result.Target.Name, result.Target.ProgramKey, result.Target.Route));
}
