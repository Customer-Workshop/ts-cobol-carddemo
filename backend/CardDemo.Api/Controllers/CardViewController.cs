using CardDemo.Application.Cards;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CardDemo.Api.Controllers;

public record CardViewDetailsDto(string EmbossedName, string ExpiryMonth, string ExpiryYear, string ActiveStatus);

public record CardViewResponseDto(
    string Outcome,
    string Message,
    string InfoMessage,
    string AccountId,
    string CardNumber,
    string AccountFilter,
    string CardFilter,
    string Cursor,
    CardViewDetailsDto? Card);

/// <summary>Card detail endpoint replacing CICS transaction CCDL (COCRDSLC).</summary>
[ApiController]
[Route("api/v1/cards/view")]
[Authorize]
public class CardViewController(CardViewService cardViewService) : ControllerBase
{
    [HttpGet]
    public async Task<IActionResult> View(
        [FromQuery] string? accountId,
        [FromQuery] string? cardNumber,
        [FromQuery] bool fromCardList,
        CancellationToken cancellationToken)
    {
        var result = await cardViewService.ViewAsync(new CardViewRequest(accountId, cardNumber, fromCardList), cancellationToken);
        var dto = ToDto(result);
        return result.Outcome == CardViewOutcome.StoreError
            ? StatusCode(StatusCodes.Status500InternalServerError, dto)
            : Ok(dto);
    }

    private static CardViewResponseDto ToDto(CardViewResult result) => new(
        result.Outcome switch
        {
            CardViewOutcome.Found => "found",
            CardViewOutcome.InputError => "inputError",
            CardViewOutcome.NotFound => "notFound",
            _ => "storeError"
        },
        result.ErrorMessage,
        result.InfoMessage,
        result.AccountId,
        result.CardNumber,
        ToDto(result.AccountFilter),
        ToDto(result.CardFilter),
        result.Cursor == CardViewCursorField.Card ? "card" : "account",
        result.Card is null
            ? null
            : new CardViewDetailsDto(result.Card.EmbossedName, result.Card.ExpiryMonth, result.Card.ExpiryYear, result.Card.ActiveStatus));

    private static string ToDto(CardViewFilterState state) => state switch
    {
        CardViewFilterState.Blank => "blank",
        CardViewFilterState.NotOk => "notOk",
        _ => "valid"
    };
}
