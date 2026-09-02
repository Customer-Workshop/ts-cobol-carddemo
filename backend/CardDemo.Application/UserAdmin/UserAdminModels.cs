namespace CardDemo.Application.UserAdmin;

/// <summary>ERRMSG attribute colour: DFHRED / DFHGREEN / DFHNEUTR.</summary>
public enum UserAdminSeverity
{
    Error,
    Success,
    Neutral
}

public enum UserAdminOutcome
{
    Success,
    ValidationError,
    NotFound,
    Duplicate,
    NoChange,
    StoreError
}

/// <summary>Screen field that receives the cursor (the COBOL `MOVE -1 TO xxxL`).</summary>
public static class UserAdminFields
{
    public const string UserId = "userId";
    public const string FirstName = "firstName";
    public const string LastName = "lastName";
    public const string Password = "password";
    public const string UserType = "userType";
}

/// <summary>SEC-USER-DATA as shown on COUSR2A/COUSR3A after a READ (password never echoed, S12-B2).</summary>
public sealed record UserAdminDetails(string UserId, string FirstName, string LastName, char UserType);

public sealed record UserAdminResult(
    UserAdminOutcome Outcome,
    string? Message,
    UserAdminSeverity? Severity,
    string? FocusField = null,
    UserAdminDetails? User = null);

// ----- COUSR01C -----
public sealed record UserAddRequest(string? FirstName, string? LastName, string? UserId, string? Password, string? UserType);

// ----- COUSR02C -----
public sealed record UserUpdateRequest(string? UserId, string? FirstName, string? LastName, string? Password, string? UserType);

// ----- COUSR00C -----
public enum UserListAction
{
    /// <summary>ENTER (also the first entry of the program).</summary>
    Enter,
    /// <summary>PF8.</summary>
    PageForward,
    /// <summary>PF7.</summary>
    PageBackward
}

/// <summary>One SELnnnn/USRIDnn pair from the list screen.</summary>
public sealed record UserListSelection(string? UserId, string? Flag);

/// <summary>Screen input plus the CDEMO-CU00-INFO COMMAREA state carried between pseudo-conversational turns.</summary>
public sealed record UserListRequest(
    UserListAction Action,
    string? SearchUserId,
    IReadOnlyList<UserListSelection>? Selections,
    int PageNum,
    bool NextPage,
    string? FirstUserId,
    string? LastUserId);

public sealed record UserListRow(string UserId, string FirstName, string LastName, char UserType);

/// <summary>XCTL target replacing COUSR02C/COUSR03C dispatch (FR-S12-03/04).</summary>
public sealed record UserListNavigation(string ProgramKey, string UserId);

public sealed record UserListResult(
    IReadOnlyList<UserListRow> Rows,
    int PageNum,
    bool NextPage,
    string? FirstUserId,
    string? LastUserId,
    string? SearchUserId,
    string? Message,
    UserAdminSeverity? Severity,
    UserListNavigation? Navigate = null);
