using CardDemo.Application.AccountUpdate;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CardDemo.Api.Controllers;

/// <summary>CACTUPA map fields (app/bms/COACTUP.bms); each member is the text of one screen field.</summary>
public record AccountUpdateFieldsDto(
    string? AccountId,
    string? ActiveStatus,
    string? OpenYear,
    string? OpenMonth,
    string? OpenDay,
    string? CreditLimit,
    string? ExpiryYear,
    string? ExpiryMonth,
    string? ExpiryDay,
    string? CashCreditLimit,
    string? ReissueYear,
    string? ReissueMonth,
    string? ReissueDay,
    string? CurrentBalance,
    string? CurrentCycleCredit,
    string? GroupId,
    string? CurrentCycleDebit,
    string? CustomerId,
    string? Ssn1,
    string? Ssn2,
    string? Ssn3,
    string? DobYear,
    string? DobMonth,
    string? DobDay,
    string? FicoScore,
    string? FirstName,
    string? MiddleName,
    string? LastName,
    string? AddressLine1,
    string? AddressLine2,
    string? State,
    string? Zip,
    string? City,
    string? Country,
    string? Phone1Area,
    string? Phone1Prefix,
    string? Phone1Line,
    string? Phone2Area,
    string? Phone2Prefix,
    string? Phone2Line,
    string? GovernmentId,
    string? EftAccountId,
    string? PrimaryCardHolder);

public record AccountUpdateLookupRequestDto(string? AccountId);

public record AccountUpdateLookupResponseDto(string Outcome, string? InfoMessage, string? ErrorMessage, AccountUpdateFieldsDto? Fields);

public record AccountUpdateChangeRequestDto(AccountUpdateFieldsDto? Original, AccountUpdateFieldsDto? Updated);

public record AccountUpdateChangeResponseDto(string Outcome, string? InfoMessage, string? ErrorMessage, IReadOnlyList<string> InvalidFields);

public record AccountUpdateErrorDto(string Message);

/// <summary>Account Update endpoints replacing CICS transaction CAUP (COACTUPC).</summary>
[ApiController]
[Route("api/v1/account-update")]
[Authorize]
public class AccountUpdateController(AccountUpdateService service) : ControllerBase
{
    [HttpPost("lookup")]
    public async Task<IActionResult> Lookup([FromBody] AccountUpdateLookupRequestDto request, CancellationToken cancellationToken)
    {
        var result = await service.LookupAsync(request.AccountId, cancellationToken);
        return Ok(new AccountUpdateLookupResponseDto(
            OutcomeName(result.Outcome),
            result.InfoMessage,
            result.ErrorMessage,
            result.Fields is null ? null : ToDto(result.Fields)));
    }

    [HttpPost("validate")]
    public IActionResult Validate([FromBody] AccountUpdateChangeRequestDto request)
    {
        if (request.Original is null || request.Updated is null)
        {
            return BadRequest(new AccountUpdateErrorDto("Both original and updated field sets are required."));
        }
        var result = service.Validate(FromDto(request.Original), FromDto(request.Updated));
        return Ok(new AccountUpdateChangeResponseDto(OutcomeName(result.Outcome), result.InfoMessage, result.ErrorMessage, result.InvalidFields));
    }

    [HttpPost("save")]
    public async Task<IActionResult> Save([FromBody] AccountUpdateChangeRequestDto request, CancellationToken cancellationToken)
    {
        if (request.Original is null || request.Updated is null)
        {
            return BadRequest(new AccountUpdateErrorDto("Both original and updated field sets are required."));
        }
        var result = await service.SaveAsync(FromDto(request.Original), FromDto(request.Updated), cancellationToken);
        return Ok(new AccountUpdateChangeResponseDto(OutcomeName(result.Outcome), result.InfoMessage, result.ErrorMessage, result.InvalidFields));
    }

    internal static string OutcomeName(AccountUpdateOutcome outcome) => outcome switch
    {
        AccountUpdateOutcome.SearchError => "searchError",
        AccountUpdateOutcome.Details => "details",
        AccountUpdateOutcome.Invalid => "invalid",
        AccountUpdateOutcome.NoChanges => "noChanges",
        AccountUpdateOutcome.Confirm => "confirm",
        AccountUpdateOutcome.Committed => "committed",
        AccountUpdateOutcome.Failed => "failed",
        AccountUpdateOutcome.ChangedByOther => "changedByOther",
        _ => throw new ArgumentOutOfRangeException(nameof(outcome), outcome, null)
    };

    internal static AccountUpdateFieldsDto ToDto(AccountUpdateFields f) => new(
        f.AccountId, f.ActiveStatus, f.OpenYear, f.OpenMonth, f.OpenDay, f.CreditLimit,
        f.ExpiryYear, f.ExpiryMonth, f.ExpiryDay, f.CashCreditLimit, f.ReissueYear, f.ReissueMonth, f.ReissueDay,
        f.CurrentBalance, f.CurrentCycleCredit, f.GroupId, f.CurrentCycleDebit, f.CustomerId,
        f.Ssn1, f.Ssn2, f.Ssn3, f.DobYear, f.DobMonth, f.DobDay, f.FicoScore,
        f.FirstName, f.MiddleName, f.LastName, f.AddressLine1, f.AddressLine2, f.State, f.Zip, f.City, f.Country,
        f.Phone1Area, f.Phone1Prefix, f.Phone1Line, f.Phone2Area, f.Phone2Prefix, f.Phone2Line,
        f.GovernmentId, f.EftAccountId, f.PrimaryCardHolder);

    internal static AccountUpdateFields FromDto(AccountUpdateFieldsDto d) => new()
    {
        AccountId = d.AccountId ?? string.Empty,
        ActiveStatus = d.ActiveStatus ?? string.Empty,
        OpenYear = d.OpenYear ?? string.Empty,
        OpenMonth = d.OpenMonth ?? string.Empty,
        OpenDay = d.OpenDay ?? string.Empty,
        CreditLimit = d.CreditLimit ?? string.Empty,
        ExpiryYear = d.ExpiryYear ?? string.Empty,
        ExpiryMonth = d.ExpiryMonth ?? string.Empty,
        ExpiryDay = d.ExpiryDay ?? string.Empty,
        CashCreditLimit = d.CashCreditLimit ?? string.Empty,
        ReissueYear = d.ReissueYear ?? string.Empty,
        ReissueMonth = d.ReissueMonth ?? string.Empty,
        ReissueDay = d.ReissueDay ?? string.Empty,
        CurrentBalance = d.CurrentBalance ?? string.Empty,
        CurrentCycleCredit = d.CurrentCycleCredit ?? string.Empty,
        GroupId = d.GroupId ?? string.Empty,
        CurrentCycleDebit = d.CurrentCycleDebit ?? string.Empty,
        CustomerId = d.CustomerId ?? string.Empty,
        Ssn1 = d.Ssn1 ?? string.Empty,
        Ssn2 = d.Ssn2 ?? string.Empty,
        Ssn3 = d.Ssn3 ?? string.Empty,
        DobYear = d.DobYear ?? string.Empty,
        DobMonth = d.DobMonth ?? string.Empty,
        DobDay = d.DobDay ?? string.Empty,
        FicoScore = d.FicoScore ?? string.Empty,
        FirstName = d.FirstName ?? string.Empty,
        MiddleName = d.MiddleName ?? string.Empty,
        LastName = d.LastName ?? string.Empty,
        AddressLine1 = d.AddressLine1 ?? string.Empty,
        AddressLine2 = d.AddressLine2 ?? string.Empty,
        State = d.State ?? string.Empty,
        Zip = d.Zip ?? string.Empty,
        City = d.City ?? string.Empty,
        Country = d.Country ?? string.Empty,
        Phone1Area = d.Phone1Area ?? string.Empty,
        Phone1Prefix = d.Phone1Prefix ?? string.Empty,
        Phone1Line = d.Phone1Line ?? string.Empty,
        Phone2Area = d.Phone2Area ?? string.Empty,
        Phone2Prefix = d.Phone2Prefix ?? string.Empty,
        Phone2Line = d.Phone2Line ?? string.Empty,
        GovernmentId = d.GovernmentId ?? string.Empty,
        EftAccountId = d.EftAccountId ?? string.Empty,
        PrimaryCardHolder = d.PrimaryCardHolder ?? string.Empty
    };
}
