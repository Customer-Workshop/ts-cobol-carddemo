namespace CardDemo.Domain.Users;

public enum UserType
{
    Admin = 'A',
    User = 'U'
}

public static class UserTypeCodes
{
    public static UserType FromCode(char code) => code switch
    {
        'A' => UserType.Admin,
        'U' => UserType.User,
        _ => throw new ArgumentOutOfRangeException(nameof(code), code, "SEC-USR-TYPE must be 'A' or 'U'.")
    };

    public static char ToCode(this UserType type) => (char)type;
}
