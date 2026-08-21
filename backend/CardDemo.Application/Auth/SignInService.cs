using CardDemo.Application.Sessions;
using CardDemo.Application.Users;
using CardDemo.Domain.Users;

namespace CardDemo.Application.Auth;

/// <summary>
/// Port of COSGN00C PROCESS-ENTER-KEY / READ-USER-SEC-FILE (app/cbl/COSGN00C.cbl):
/// mandatory-field checks, uppercasing, keyed read protocol (found / not-found / store error),
/// hashed-password comparison, role-based routing (FR-S01-01..07, 09).
/// </summary>
public class SignInService(IUserRepository userRepository, IPasswordHashingService passwordHashingService)
{
    public const string MissingUserIdMessage = "Please enter User ID ...";
    public const string MissingPasswordMessage = "Please enter Password ...";
    public const string UserNotFoundMessage = "User not found. Try again ...";
    public const string WrongPasswordMessage = "Wrong Password. Try again ...";
    public const string StoreErrorMessage = "Unable to verify the User ...";

    public const string AdminLandingRoute = "/admin";
    public const string MainMenuLandingRoute = "/menu";

    private const string ProgramName = "COSGN00C";

    public async Task<SignInResult> SignInAsync(SignInRequest request, CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(request.UserId))
        {
            return new SignInResult(SignInOutcome.MissingUserId, MissingUserIdMessage);
        }
        if (string.IsNullOrWhiteSpace(request.Password))
        {
            return new SignInResult(SignInOutcome.MissingPassword, MissingPasswordMessage);
        }

        var userId = request.UserId.Trim().ToUpperInvariant();
        var password = request.Password.Trim().ToUpperInvariant();

        User? user;
        try
        {
            user = await userRepository.GetByIdAsync(userId, cancellationToken);
        }
        catch (Exception)
        {
            return new SignInResult(SignInOutcome.StoreError, StoreErrorMessage);
        }

        if (user is null)
        {
            return new SignInResult(SignInOutcome.UserNotFound, UserNotFoundMessage);
        }

        if (!passwordHashingService.Verify(user.PasswordHash, password))
        {
            return new SignInResult(SignInOutcome.WrongPassword, WrongPasswordMessage);
        }

        var session = new SessionContext(userId, user.UserType.ToCode(), FromProgram: ProgramName);
        var landingRoute = session.IsAdmin ? AdminLandingRoute : MainMenuLandingRoute;
        return new SignInResult(SignInOutcome.Success, Session: session, LandingRoute: landingRoute);
    }
}
