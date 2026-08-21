namespace CardDemo.Domain.Users;

/// <summary>
/// User security record, ported from SEC-USER-DATA (app/cpy/CSUSR01Y.cpy).
/// UserId X(8), FirstName X(20), LastName X(20), password (hashed in target), UserType X(1) 'A'/'U'.
/// </summary>
public class User
{
    public required string UserId { get; set; }
    public required string FirstName { get; set; }
    public required string LastName { get; set; }
    public required string PasswordHash { get; set; }
    public required UserType UserType { get; set; }
}
