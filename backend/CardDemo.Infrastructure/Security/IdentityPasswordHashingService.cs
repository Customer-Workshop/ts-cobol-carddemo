using CardDemo.Application.Users;
using CardDemo.Domain.Users;
using Microsoft.AspNetCore.Identity;

namespace CardDemo.Infrastructure.Security;

public class IdentityPasswordHashingService : IPasswordHashingService
{
    private static readonly User Placeholder = new()
    {
        UserId = string.Empty,
        FirstName = string.Empty,
        LastName = string.Empty,
        PasswordHash = string.Empty,
        UserType = UserType.User
    };

    private readonly PasswordHasher<User> _hasher = new();

    public string Hash(string password) => _hasher.HashPassword(Placeholder, password);

    public bool Verify(string hash, string password) =>
        _hasher.VerifyHashedPassword(Placeholder, hash, password) != PasswordVerificationResult.Failed;
}
