using CardDemo.Application.Menu;
using CardDemo.Application.UserAdmin;
using CardDemo.Infrastructure.Security;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CardDemo.Api.Controllers;

public record UserListSelectionDto(string? UserId, string? Flag);

public record UserListRequestDto(
    string? Action,
    string? SearchUserId,
    List<UserListSelectionDto>? Selections,
    int PageNum,
    bool NextPage,
    string? FirstUserId,
    string? LastUserId);

public record UserListRowDto(string UserId, string FirstName, string LastName, string UserType);

public record UserListNavigationDto(string ProgramKey, string UserId);

public record UserListResponseDto(
    IReadOnlyList<UserListRowDto> Rows,
    int PageNum,
    bool NextPage,
    string? FirstUserId,
    string? LastUserId,
    string? SearchUserId,
    string? Message,
    string? Severity,
    UserListNavigationDto? Navigate);

public record UserAddRequestDto(string? FirstName, string? LastName, string? UserId, string? Password, string? UserType);

public record UserUpdateRequestDto(string? UserId, string? FirstName, string? LastName, string? Password, string? UserType);

public record UserIdRequestDto(string? UserId);

public record UserAdminDetailsDto(string UserId, string FirstName, string LastName, string UserType);

public record UserAdminResponseDto(string Outcome, string? Message, string? Severity, string? FocusField, UserAdminDetailsDto? User);

public record UserAdminErrorDto(string Message);

/// <summary>
/// User security maintenance endpoints replacing CICS transactions CU00 (COUSR00C), CU01 (COUSR01C),
/// CU02 (COUSR02C) and CU03 (COUSR03C). Reachable only from the admin menu in the source, so every
/// action requires the JWT userType 'A' claim (S12-B5).
/// </summary>
[ApiController]
[Route("api/v1/admin/users")]
[Authorize]
public class UserAdminController(UserAdminService userAdminService) : ControllerBase
{
    [HttpPost("list")]
    public async Task<IActionResult> List([FromBody] UserListRequestDto request, CancellationToken cancellationToken)
    {
        if (Forbidden() is { } forbidden)
        {
            return forbidden;
        }

        var action = (request.Action ?? string.Empty).Trim().ToLowerInvariant() switch
        {
            "pageforward" or "pf8" => UserListAction.PageForward,
            "pagebackward" or "pf7" => UserListAction.PageBackward,
            _ => UserListAction.Enter
        };
        var result = await userAdminService.ListAsync(
            new UserListRequest(
                action,
                request.SearchUserId,
                request.Selections?.Select(s => new UserListSelection(s.UserId, s.Flag)).ToList(),
                request.PageNum,
                request.NextPage,
                request.FirstUserId,
                request.LastUserId),
            cancellationToken);

        return Ok(new UserListResponseDto(
            result.Rows.Select(r => new UserListRowDto(r.UserId, r.FirstName, r.LastName, r.UserType.ToString())).ToList(),
            result.PageNum,
            result.NextPage,
            result.FirstUserId,
            result.LastUserId,
            result.SearchUserId,
            result.Message,
            ToDto(result.Severity),
            result.Navigate is null ? null : new UserListNavigationDto(result.Navigate.ProgramKey, result.Navigate.UserId)));
    }

    [HttpPost("add")]
    public async Task<IActionResult> Add([FromBody] UserAddRequestDto request, CancellationToken cancellationToken)
    {
        if (Forbidden() is { } forbidden)
        {
            return forbidden;
        }
        var result = await userAdminService.AddAsync(
            new UserAddRequest(request.FirstName, request.LastName, request.UserId, request.Password, request.UserType),
            cancellationToken);
        return Ok(ToDto(result));
    }

    [HttpPost("update/fetch")]
    public async Task<IActionResult> FetchForUpdate([FromBody] UserIdRequestDto request, CancellationToken cancellationToken)
    {
        if (Forbidden() is { } forbidden)
        {
            return forbidden;
        }
        return Ok(ToDto(await userAdminService.FetchForUpdateAsync(request.UserId, cancellationToken)));
    }

    [HttpPost("update")]
    public async Task<IActionResult> Update([FromBody] UserUpdateRequestDto request, CancellationToken cancellationToken)
    {
        if (Forbidden() is { } forbidden)
        {
            return forbidden;
        }
        var result = await userAdminService.UpdateAsync(
            new UserUpdateRequest(request.UserId, request.FirstName, request.LastName, request.Password, request.UserType),
            cancellationToken);
        return Ok(ToDto(result));
    }

    [HttpPost("delete/fetch")]
    public async Task<IActionResult> FetchForDelete([FromBody] UserIdRequestDto request, CancellationToken cancellationToken)
    {
        if (Forbidden() is { } forbidden)
        {
            return forbidden;
        }
        return Ok(ToDto(await userAdminService.FetchForDeleteAsync(request.UserId, cancellationToken)));
    }

    [HttpPost("delete")]
    public async Task<IActionResult> Delete([FromBody] UserIdRequestDto request, CancellationToken cancellationToken)
    {
        if (Forbidden() is { } forbidden)
        {
            return forbidden;
        }
        return Ok(ToDto(await userAdminService.DeleteAsync(request.UserId, cancellationToken)));
    }

    private IActionResult? Forbidden()
    {
        var claim = User.FindFirst(JwtTokenIssuer.UserTypeClaim)?.Value;
        var userType = string.IsNullOrEmpty(claim) ? 'U' : claim[0];
        return userType == 'A'
            ? null
            : StatusCode(StatusCodes.Status403Forbidden, new UserAdminErrorDto(MenuService.AdminOnlyMessage));
    }

    private static UserAdminResponseDto ToDto(UserAdminResult result) => new(
        result.Outcome switch
        {
            UserAdminOutcome.Success => "success",
            UserAdminOutcome.ValidationError => "validationError",
            UserAdminOutcome.NotFound => "notFound",
            UserAdminOutcome.Duplicate => "duplicate",
            UserAdminOutcome.NoChange => "noChange",
            _ => "storeError"
        },
        result.Message,
        ToDto(result.Severity),
        result.FocusField,
        result.User is null
            ? null
            : new UserAdminDetailsDto(result.User.UserId, result.User.FirstName, result.User.LastName, result.User.UserType.ToString()));

    private static string? ToDto(UserAdminSeverity? severity) => severity switch
    {
        UserAdminSeverity.Error => "error",
        UserAdminSeverity.Success => "success",
        UserAdminSeverity.Neutral => "neutral",
        _ => null
    };
}
