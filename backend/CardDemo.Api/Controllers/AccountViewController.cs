using CardDemo.Application.Accounts;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CardDemo.Api.Controllers;

public record AccountViewAccountDto(
    string ActiveStatus,
    string OpenDate,
    string CreditLimit,
    string ExpirationDate,
    string CashCreditLimit,
    string ReissueDate,
    string CurrentBalance,
    string CurrentCycleCredit,
    string GroupId,
    string CurrentCycleDebit);

public record AccountViewCustomerDto(
    string CustomerId,
    string Ssn,
    string DateOfBirth,
    string FicoScore,
    string FirstName,
    string MiddleName,
    string LastName,
    string AddressLine1,
    string AddressLine2,
    string State,
    string City,
    string Zip,
    string Country,
    string Phone1,
    string Phone2,
    string GovernmentIssuedId,
    string EftAccountId,
    string PrimaryCardHolder);

public record AccountViewResponseDto(
    string Outcome,
    string AccountId,
    string AccountFieldState,
    string InfoMessage,
    string ErrorMessage,
    AccountViewAccountDto? Account,
    AccountViewCustomerDto? Customer);

/// <summary>Account view endpoint replacing CICS transaction CAVW (COACTVWC, map CACTVWA).</summary>
[ApiController]
[Route("api/v1/accounts")]
[Authorize]
public class AccountViewController(AccountViewService accountViewService) : ControllerBase
{
    /// <summary>ENTER on the View Account screen: edit the account id, then read xref → account → customer.</summary>
    [HttpGet("view")]
    public async Task<IActionResult> View([FromQuery] string? accountId, CancellationToken cancellationToken)
    {
        var result = await accountViewService.ViewAsync(accountId, cancellationToken);
        var dto = ToDto(result);
        return result.Outcome == AccountViewOutcome.StoreError
            ? StatusCode(StatusCodes.Status500InternalServerError, dto)
            : Ok(dto);
    }

    private static AccountViewResponseDto ToDto(AccountViewResult result) => new(
        result.Outcome switch
        {
            AccountViewOutcome.Initial => "initial",
            AccountViewOutcome.NoInput => "noInput",
            AccountViewOutcome.InvalidFilter => "invalidFilter",
            AccountViewOutcome.AccountNotInXref => "accountNotInXref",
            AccountViewOutcome.AccountNotInMaster => "accountNotInMaster",
            AccountViewOutcome.CustomerNotFound => "customerNotFound",
            AccountViewOutcome.StoreError => "storeError",
            _ => "found"
        },
        result.AccountId,
        result.FilterState switch
        {
            AccountFilterState.Blank => "blank",
            AccountFilterState.Invalid => "invalid",
            _ => "valid"
        },
        result.InfoMessage,
        result.ErrorMessage,
        result.Account is null ? null : ToDto(result.Account),
        result.Customer is null ? null : ToDto(result.Customer));

    private static AccountViewAccountDto ToDto(AccountViewAccountDetails a) => new(
        a.ActiveStatus, a.OpenDate, a.CreditLimit, a.ExpirationDate, a.CashCreditLimit, a.ReissueDate,
        a.CurrentBalance, a.CurrentCycleCredit, a.GroupId, a.CurrentCycleDebit);

    private static AccountViewCustomerDto ToDto(AccountViewCustomerDetails c) => new(
        c.CustomerId, c.Ssn, c.DateOfBirth, c.FicoScore, c.FirstName, c.MiddleName, c.LastName,
        c.AddressLine1, c.AddressLine2, c.State, c.City, c.Zip, c.Country, c.Phone1, c.Phone2,
        c.GovernmentIssuedId, c.EftAccountId, c.PrimaryCardHolder);
}
