using CardDemo.Application.Cards;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CardDemo.Api.Controllers;

public record CardUpdateDetailsDto(
    string AccountId,
    string CardNumber,
    string EmbossedName,
    string ExpiryYear,
    string ExpiryMonth,
    string ExpiryDay,
    string ActiveStatus);

public record CardUpdateInputDto(string? EmbossedName, string? ActiveStatus, string? ExpiryMonth, string? ExpiryYear);

/// <summary>
/// One CCUP round trip. <c>Aid</c>: enter | pf5 | pf12. <c>State</c>: the state string returned by the previous response
/// (notFetched on first entry).
/// </summary>
public record CardUpdateRequestDto(
    string? Aid,
    string? State,
    string? AccountId,
    string? CardNumber,
    CardUpdateDetailsDto? Original,
    CardUpdateInputDto? Input);

public record CardUpdateScreenDto(
    string State,
    string InfoMessage,
    string? ErrorMessage,
    string AccountId,
    string CardNumber,
    string EmbossedName,
    string ActiveStatus,
    string ExpiryMonth,
    string ExpiryYear,
    string ExpiryDay,
    CardUpdateDetailsDto? Original,
    IReadOnlyList<string> FieldsInError,
    string CursorField,
    bool SearchEditable,
    bool DetailsEditable,
    bool ConfirmKeysVisible);

public record CardUpdateErrorDto(string Message);

/// <summary>Card update endpoint replacing CICS transaction CCUP (COCRDUPC).</summary>
[ApiController]
[Route("api/v1/cards/update")]
[Authorize]
public class CardUpdateController(CardUpdateService cardUpdateService) : ControllerBase
{
    [HttpGet]
    public ActionResult<CardUpdateScreenDto> GetInitialScreen() => Ok(ToDto(CardUpdateService.FreshScreen()));

    [HttpPost]
    public async Task<ActionResult<CardUpdateScreenDto>> Process([FromBody] CardUpdateRequestDto request, CancellationToken cancellationToken)
    {
        if (!TryParseAid(request.Aid, out var aid))
        {
            return BadRequest(new CardUpdateErrorDto($"Unknown aid '{request.Aid}'."));
        }
        if (!TryParseState(request.State, out var state))
        {
            return BadRequest(new CardUpdateErrorDto($"Unknown state '{request.State}'."));
        }

        var screen = await cardUpdateService.ProcessAsync(
            new CardUpdateRequest(
                aid,
                state,
                request.AccountId,
                request.CardNumber,
                request.Original is null ? null : FromDto(request.Original),
                request.Input is null
                    ? null
                    : new CardUpdateInput(request.Input.EmbossedName, request.Input.ActiveStatus, request.Input.ExpiryMonth, request.Input.ExpiryYear)),
            cancellationToken);
        return Ok(ToDto(screen));
    }

    private static bool TryParseAid(string? aid, out CardUpdateAid parsed)
    {
        switch ((aid ?? "enter").Trim().ToLowerInvariant())
        {
            case "enter":
                parsed = CardUpdateAid.Enter;
                return true;
            case "pf5":
                parsed = CardUpdateAid.Pf5;
                return true;
            case "pf12":
                parsed = CardUpdateAid.Pf12;
                return true;
            default:
                parsed = CardUpdateAid.Enter;
                return false;
        }
    }

    private static bool TryParseState(string? state, out CardUpdateState parsed)
    {
        switch ((state ?? "notFetched").Trim())
        {
            case "notFetched":
                parsed = CardUpdateState.DetailsNotFetched;
                return true;
            case "showDetails":
                parsed = CardUpdateState.ShowDetails;
                return true;
            case "changesNotOk":
                parsed = CardUpdateState.ChangesNotOk;
                return true;
            case "changesOkNotConfirmed":
                parsed = CardUpdateState.ChangesOkNotConfirmed;
                return true;
            case "changesDone":
                parsed = CardUpdateState.ChangesDone;
                return true;
            case "changesFailed":
                parsed = CardUpdateState.ChangesFailed;
                return true;
            default:
                parsed = CardUpdateState.DetailsNotFetched;
                return false;
        }
    }

    private static string StateName(CardUpdateState state) => state switch
    {
        CardUpdateState.ShowDetails => "showDetails",
        CardUpdateState.ChangesNotOk => "changesNotOk",
        CardUpdateState.ChangesOkNotConfirmed => "changesOkNotConfirmed",
        CardUpdateState.ChangesDone => "changesDone",
        CardUpdateState.ChangesFailed => "changesFailed",
        _ => "notFetched"
    };

    private static string FieldName(CardUpdateField field) => field switch
    {
        CardUpdateField.CardNumber => "cardNumber",
        CardUpdateField.EmbossedName => "embossedName",
        CardUpdateField.ActiveStatus => "activeStatus",
        CardUpdateField.ExpiryMonth => "expiryMonth",
        CardUpdateField.ExpiryYear => "expiryYear",
        _ => "accountId"
    };

    private static CardUpdateDetails FromDto(CardUpdateDetailsDto dto) => new(
        dto.AccountId,
        dto.CardNumber,
        dto.EmbossedName,
        dto.ExpiryYear,
        dto.ExpiryMonth,
        dto.ExpiryDay,
        dto.ActiveStatus);

    private static CardUpdateDetailsDto ToDto(CardUpdateDetails details) => new(
        details.AccountId,
        details.CardNumber,
        details.EmbossedName,
        details.ExpiryYear,
        details.ExpiryMonth,
        details.ExpiryDay,
        details.ActiveStatus);

    private static CardUpdateScreenDto ToDto(CardUpdateScreen screen) => new(
        StateName(screen.State),
        screen.InfoMessage,
        screen.ErrorMessage,
        screen.AccountId,
        screen.CardNumber,
        screen.EmbossedName,
        screen.ActiveStatus,
        screen.ExpiryMonth,
        screen.ExpiryYear,
        screen.ExpiryDay,
        screen.Original is null ? null : ToDto(screen.Original),
        screen.FieldsInError.Select(FieldName).ToList(),
        FieldName(screen.CursorField),
        screen.SearchEditable,
        screen.DetailsEditable,
        screen.ConfirmKeysVisible);
}
