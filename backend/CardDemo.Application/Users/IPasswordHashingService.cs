namespace CardDemo.Application.Users;

/// <summary>Passwords are stored hashed in the target (STOP C decision; FR §11 deviation).</summary>
public interface IPasswordHashingService
{
    string Hash(string password);
    bool Verify(string hash, string password);
}
