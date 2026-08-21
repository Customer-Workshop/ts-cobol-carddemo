namespace CardDemo.Application.Users;

/// <summary>Parsed USRSEC seed record (plaintext password as shipped in the legacy seed).</summary>
public record UsrsecRecord(string UserId, string FirstName, string LastName, string Password, char UserType);
