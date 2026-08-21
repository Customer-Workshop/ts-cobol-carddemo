using CardDemo.Application.Menu;
using CardDemo.Infrastructure.Security;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CardDemo.Api.Controllers;

public record MenuOptionDto(string Id, string Name, bool Enabled);

public record MenuResponseDto(string Menu, IReadOnlyList<MenuOptionDto> Options);

public record MenuSelectRequestDto(string? Menu, string? Option);

public record MenuNavigationTargetDto(string Id, string Name, string ProgramKey, string Route);

public record MenuSelectResponseDto(string Outcome, string? Message, string? Severity, MenuNavigationTargetDto? Target);

public record MenuErrorDto(string Message);

/// <summary>Menu endpoints replacing CICS transactions CM00 (COMEN01C) and CA00 (COADM01C).</summary>
[ApiController]
[Route("api/v1/menu")]
[Authorize]
public class MenuController(MenuService menuService) : ControllerBase
{
    [HttpGet]
    public IActionResult GetMenu([FromQuery] string menu = "main")
    {
        if (!TryParseMenuKind(menu, out var kind))
        {
            return BadRequest(new MenuErrorDto($"Unknown menu '{menu}'."));
        }
        if (kind == MenuKind.Admin && UserType() != 'A')
        {
            return StatusCode(StatusCodes.Status403Forbidden, new MenuErrorDto(MenuService.AdminOnlyMessage));
        }

        var options = menuService.GetMenu(kind);
        return Ok(new MenuResponseDto(
            kind == MenuKind.Admin ? "admin" : "main",
            options.Select(o => new MenuOptionDto(o.Id, o.Name, o.Enabled)).ToList()));
    }

    [HttpPost("select")]
    public IActionResult Select([FromBody] MenuSelectRequestDto request)
    {
        if (!TryParseMenuKind(request.Menu ?? "main", out var kind))
        {
            return BadRequest(new MenuErrorDto($"Unknown menu '{request.Menu}'."));
        }
        if (kind == MenuKind.Admin && UserType() != 'A')
        {
            return StatusCode(StatusCodes.Status403Forbidden, new MenuErrorDto(MenuService.AdminOnlyMessage));
        }

        var result = menuService.Select(kind, UserType(), request.Option);
        return Ok(ToDto(result));
    }

    private char UserType()
    {
        var claim = User.FindFirst(JwtTokenIssuer.UserTypeClaim)?.Value;
        return string.IsNullOrEmpty(claim) ? 'U' : claim[0];
    }

    private static bool TryParseMenuKind(string menu, out MenuKind kind)
    {
        switch (menu.Trim().ToLowerInvariant())
        {
            case "main":
                kind = MenuKind.Main;
                return true;
            case "admin":
                kind = MenuKind.Admin;
                return true;
            default:
                kind = MenuKind.Main;
                return false;
        }
    }

    private static MenuSelectResponseDto ToDto(MenuSelectResult result) => new(
        result.Outcome switch
        {
            MenuSelectOutcome.InvalidOption => "invalidOption",
            MenuSelectOutcome.AdminOnly => "adminOnly",
            MenuSelectOutcome.ComingSoon => "comingSoon",
            MenuSelectOutcome.NotInstalled => "notInstalled",
            _ => "navigate"
        },
        result.Message,
        result.Severity switch
        {
            MenuMessageSeverity.Error => "error",
            MenuMessageSeverity.Info => "info",
            _ => null
        },
        result.Target is null
            ? null
            : new MenuNavigationTargetDto(result.Target.Id, result.Target.Name, result.Target.ProgramKey, result.Target.Route));
}
