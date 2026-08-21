using CardDemo.Application.Auth;
using CardDemo.Application.Sessions;
using Microsoft.AspNetCore.Mvc;

namespace CardDemo.Api.Controllers;

public record SignInRequestDto(string? UserId, string? Password);

public record SignInResponseDto(string Token, string UserId, string UserType, string LandingRoute);

public record SignInErrorDto(string Message);

/// <summary>Sign-on endpoint replacing CICS transaction CC00 (COSGN00C).</summary>
[ApiController]
[Route("api/v1/auth")]
public class AuthController(SignInService signInService, IJwtTokenIssuer tokenIssuer) : ControllerBase
{
    [HttpPost("signin")]
    public async Task<IActionResult> SignIn([FromBody] SignInRequestDto request, CancellationToken cancellationToken)
    {
        var result = await signInService.SignInAsync(new SignInRequest(request.UserId, request.Password), cancellationToken);

        return result.Outcome switch
        {
            SignInOutcome.Success => Ok(new SignInResponseDto(
                tokenIssuer.Issue(result.Session!),
                result.Session!.UserId,
                result.Session.UserType.ToString(),
                result.LandingRoute!)),
            SignInOutcome.MissingUserId or SignInOutcome.MissingPassword =>
                BadRequest(new SignInErrorDto(result.Message!)),
            SignInOutcome.UserNotFound or SignInOutcome.WrongPassword =>
                Unauthorized(new SignInErrorDto(result.Message!)),
            _ => StatusCode(StatusCodes.Status500InternalServerError, new SignInErrorDto(result.Message!))
        };
    }
}
