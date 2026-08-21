using CardDemo.Application.Sessions;

namespace CardDemo.Application.Auth;

public record SignInRequest(string? UserId, string? Password);

public enum SignInOutcome
{
    Success,
    MissingUserId,
    MissingPassword,
    UserNotFound,
    WrongPassword,
    StoreError
}

/// <summary>
/// Outcome of the COSGN00C authentication sequence: either a populated session +
/// role-based landing route, or the exact legacy screen message.
/// </summary>
public record SignInResult(
    SignInOutcome Outcome,
    string? Message = null,
    SessionContext? Session = null,
    string? LandingRoute = null)
{
    public bool IsSuccess => Outcome == SignInOutcome.Success;
}
