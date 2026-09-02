using CardDemo.Application.Cards;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CardDemo.Api.Controllers;

public record CardListRowStateDto(string AccountId, string CardNumber, string ActiveStatus);

/// <summary>Paging COMMAREA of COCRDLIC (WS-THIS-PROGCOMMAREA); echoed back verbatim by the client on each AID press.</summary>
public record CardListPageStateDto(
    int ScreenNumber,
    string FirstCardNumber,
    string LastCardNumber,
    bool NextPageExists,
    bool LastPageShown,
    IReadOnlyList<CardListRowStateDto?> Rows);

public record CardListRequestDto(
    string? Aid,
    CardListPageStateDto? State,
    string? AccountFilter,
    string? CardFilter,
    IReadOnlyList<string?>? Selections);

public record CardListRowDto(
    bool HasCard,
    string AccountId,
    string CardNumber,
    string ActiveStatus,
    string Selection,
    bool SelectionError,
    bool SelectionProtected);

public record CardListTargetDto(string ProgramKey, string Route, string AccountId, string CardNumber);

public record CardListResponseDto(
    string Outcome,
    int ScreenNumber,
    string AccountFilter,
    string CardFilter,
    bool AccountFilterError,
    bool CardFilterError,
    string CursorField,
    IReadOnlyList<CardListRowDto> Rows,
    string ErrorMessage,
    string InfoMessage,
    string? Message,
    string? Severity,
    CardListPageStateDto State,
    CardListTargetDto? Target);

/// <summary>Card endpoints; <c>POST list</c> replaces CICS transaction CCLI (COCRDLIC, map CCRDLIA).</summary>
[ApiController]
[Route("api/v1/cards")]
[Authorize]
public class CardsController(CardListService cardListService) : ControllerBase
{
    [HttpPost("list")]
    public async Task<ActionResult<CardListResponseDto>> List([FromBody] CardListRequestDto request, CancellationToken cancellationToken)
    {
        var result = await cardListService.ProcessAsync(ToRequest(request), cancellationToken);
        return Ok(ToDto(result));
    }

    private static CardListRequest ToRequest(CardListRequestDto dto) => new(
        dto.Aid,
        dto.State is null ? null : ToState(dto.State),
        dto.AccountFilter,
        dto.CardFilter,
        dto.Selections);

    private static CardListPageState ToState(CardListPageStateDto dto) => new(
        dto.ScreenNumber,
        dto.FirstCardNumber ?? string.Empty,
        dto.LastCardNumber ?? string.Empty,
        dto.NextPageExists,
        dto.LastPageShown,
        (dto.Rows ?? []).Select(r => r is null ? null : new CardListRow(r.AccountId, r.CardNumber, r.ActiveStatus)).ToList());

    private static CardListPageStateDto ToDto(CardListPageState state) => new(
        state.ScreenNumber,
        state.FirstCardNumber,
        state.LastCardNumber,
        state.NextPageExists,
        state.LastPageShown,
        state.Rows.Select(r => r is null ? null : new CardListRowStateDto(r.AccountId, r.CardNumber, r.ActiveStatus)).ToList());

    private static CardListResponseDto ToDto(CardListResult result) => new(
        result.Outcome switch
        {
            CardListOutcome.Exit => "exit",
            CardListOutcome.Navigate => "navigate",
            CardListOutcome.ComingSoon => "comingSoon",
            CardListOutcome.NotInstalled => "notInstalled",
            _ => "display"
        },
        result.ScreenNumber,
        result.AccountFilter,
        result.CardFilter,
        result.AccountFilterError,
        result.CardFilterError,
        result.CursorField,
        result.Rows.Select(r => new CardListRowDto(
            r.Card is not null,
            r.Card?.AccountId ?? string.Empty,
            r.Card?.CardNumber ?? string.Empty,
            r.Card?.ActiveStatus ?? string.Empty,
            r.Selection,
            r.SelectionError,
            r.SelectionProtected)).ToList(),
        result.ErrorMessage,
        result.InfoMessage,
        result.Message,
        result.Severity,
        ToDto(result.State),
        result.Target is null
            ? null
            : new CardListTargetDto(result.Target.ProgramKey, result.Target.Route, result.Target.AccountId, result.Target.CardNumber));
}
