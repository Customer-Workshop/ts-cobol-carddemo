using CardDemo.Application.Common;
using CardDemo.Application.Users;
using CardDemo.Domain.Users;

namespace CardDemo.Application.UserAdmin;

/// <summary>
/// Port of COUSR00C / COUSR01C / COUSR02C / COUSR03C (app/cbl): user list with keyed paging,
/// user add, user update and user delete against USRSEC, one method per COBOL paragraph
/// (FR-S12-01..40). Messages are the exact legacy texts; PF-key dispatch and navigation are
/// owned by the Angular screens.
/// </summary>
public class UserAdminService(IUserRepository userRepository, IPasswordHashingService passwordHashingService)
{
    public const int PageSize = 10;

    public const string ProgramList = "COUSR00C";
    public const string ProgramAdd = "COUSR01C";
    public const string ProgramUpdate = "COUSR02C";
    public const string ProgramDelete = "COUSR03C";

    // COUSR00C
    public const string InvalidSelectionMessage = "Invalid selection. Valid values are U and D";
    public const string AlreadyAtTopMessage = "You are already at the top of the page...";
    public const string AlreadyAtBottomMessage = "You are already at the bottom of the page...";
    public const string AtTopMessage = "You are at the top of the page...";
    public const string ReachedBottomMessage = "You have reached the bottom of the page...";
    public const string ReachedTopMessage = "You have reached the top of the page...";
    public const string LookupErrorMessage = "Unable to lookup User...";

    // COUSR01C / COUSR02C / COUSR03C
    public const string FirstNameEmptyMessage = "First Name can NOT be empty...";
    public const string LastNameEmptyMessage = "Last Name can NOT be empty...";
    public const string UserIdEmptyMessage = "User ID can NOT be empty...";
    public const string PasswordEmptyMessage = "Password can NOT be empty...";
    public const string UserTypeEmptyMessage = "User Type can NOT be empty...";
    public const string DuplicateUserMessage = "User ID already exist...";
    public const string AddErrorMessage = "Unable to Add User...";
    public const string UserNotFoundMessage = "User ID NOT found...";
    public const string PressPf5ToSaveMessage = "Press PF5 key to save your updates ...";
    public const string PleaseModifyMessage = "Please modify to update ...";
    public const string UpdateErrorMessage = "Unable to Update User...";
    public const string PressPf5ToDeleteMessage = "Press PF5 key to delete this user ...";

    public static string AddedMessage(string userId) => $"User {FirstWord(userId)} has been added ...";
    public static string UpdatedMessage(string userId) => $"User {FirstWord(userId)} has been updated ...";
    public static string DeletedMessage(string userId) => $"User {FirstWord(userId)} has been deleted ...";

    // ------------------------------------------------------------------ COUSR00C

    /// <summary>PROCESS-ENTER-KEY / PROCESS-PF7-KEY / PROCESS-PF8-KEY (COUSR00C.cbl:149-278).</summary>
    public async Task<UserListResult> ListAsync(UserListRequest request, CancellationToken cancellationToken = default)
    {
        return request.Action switch
        {
            UserListAction.PageForward => await ProcessPf8Async(request, cancellationToken),
            UserListAction.PageBackward => await ProcessPf7Async(request, cancellationToken),
            _ => await ProcessEnterAsync(request, cancellationToken)
        };
    }

    private async Task<UserListResult> ProcessEnterAsync(UserListRequest request, CancellationToken cancellationToken)
    {
        string? message = null;
        var selection = request.Selections?.FirstOrDefault(s => !IsBlank(s.Flag));
        if (selection is not null && !IsBlank(selection.UserId))
        {
            var flag = selection.Flag!.Trim();
            var selectedUserId = Normalize(selection.UserId)!;
            switch (flag)
            {
                case "U":
                case "u":
                    return Unchanged(request, null, null, new UserListNavigation(ProgramUpdate, selectedUserId));
                case "D":
                case "d":
                    return Unchanged(request, null, null, new UserListNavigation(ProgramDelete, selectedUserId));
                default:
                    message = InvalidSelectionMessage;
                    break;
            }
        }

        var startKey = Normalize(request.SearchUserId) ?? string.Empty;
        return await PageForwardAsync(request with { PageNum = 0 }, startKey, inclusive: true, message, cancellationToken);
    }

    private async Task<UserListResult> ProcessPf8Async(UserListRequest request, CancellationToken cancellationToken)
    {
        if (!request.NextPage)
        {
            return Unchanged(request, AlreadyAtBottomMessage, UserAdminSeverity.Error);
        }
        var last = Normalize(request.LastUserId);
        return last is null
            ? await PageForwardAsync(request, HighValues, inclusive: true, null, cancellationToken)
            : await PageForwardAsync(request, last, inclusive: false, null, cancellationToken);
    }

    private async Task<UserListResult> ProcessPf7Async(UserListRequest request, CancellationToken cancellationToken)
    {
        // SET NEXT-PAGE-YES precedes the page-number guard (COUSR00C.cbl:245-255).
        request = request with { NextPage = true };
        if (request.PageNum <= 1)
        {
            return Unchanged(request, AlreadyAtTopMessage, UserAdminSeverity.Error);
        }
        var first = Normalize(request.FirstUserId) ?? string.Empty;
        return await PageBackwardAsync(request, first, cancellationToken);
    }

    /// <summary>PROCESS-PAGE-FORWARD (COUSR00C.cbl:282-331) with STARTBR/READNEXT response mapping.</summary>
    private async Task<UserListResult> PageForwardAsync(UserListRequest state, string startKey, bool inclusive, string? message, CancellationToken cancellationToken)
    {
        KeyedPage<User> page;
        try
        {
            page = await userRepository.BrowseForwardAsync(startKey, inclusive, PageSize, cancellationToken);
        }
        catch (Exception)
        {
            return Unchanged(state, LookupErrorMessage, UserAdminSeverity.Error);
        }

        var rows = page.Items.Select(ToRow).ToList();
        var pageNum = state.PageNum;
        var nextPage = false;

        if (rows.Count == 0)
        {
            // STARTBR NOTFND (key beyond the last record) vs READNEXT ENDFILE right after the skip.
            message = inclusive ? AtTopMessage : ReachedBottomMessage;
        }
        else if (rows.Count < PageSize || !page.HasMore)
        {
            pageNum += 1;
            message = ReachedBottomMessage;
        }
        else
        {
            pageNum += 1;
            nextPage = true;
        }

        var first = rows.Count >= 1 ? rows[0].UserId : state.FirstUserId;
        var last = rows.Count == PageSize ? rows[PageSize - 1].UserId : state.LastUserId;
        return new UserListResult(rows, pageNum, nextPage, first, last, SearchUserId: null,
            message, message is null ? null : UserAdminSeverity.Error);
    }

    /// <summary>PROCESS-PAGE-BACKWARD (COUSR00C.cbl:336-379) with READPREV response mapping.</summary>
    private async Task<UserListResult> PageBackwardAsync(UserListRequest state, string beforeKey, CancellationToken cancellationToken)
    {
        KeyedPage<User> page;
        try
        {
            page = await userRepository.BrowseBackwardAsync(beforeKey, PageSize, cancellationToken);
        }
        catch (Exception)
        {
            return Unchanged(state, LookupErrorMessage, UserAdminSeverity.Error);
        }

        var rows = page.Items.Select(ToRow).ToList();
        var pageNum = state.PageNum;
        string? message = null;

        if (rows.Count < PageSize)
        {
            message = ReachedTopMessage;
        }
        else if (page.HasMore)
        {
            pageNum = pageNum > 1 ? pageNum - 1 : 1;
        }
        else
        {
            message = ReachedTopMessage;
            pageNum = 1;
        }

        var last = rows.Count >= 1 ? rows[^1].UserId : state.LastUserId;
        var first = rows.Count == PageSize ? rows[0].UserId : state.FirstUserId;
        return new UserListResult(rows, pageNum, NextPage: true, first, last, state.SearchUserId,
            message, message is null ? null : UserAdminSeverity.Error);
    }

    private static UserListResult Unchanged(UserListRequest state, string? message, UserAdminSeverity? severity, UserListNavigation? navigate = null) =>
        new([], state.PageNum, state.NextPage, state.FirstUserId, state.LastUserId, state.SearchUserId, message, severity, navigate);

    private static UserListRow ToRow(User user) => new(user.UserId, user.FirstName, user.LastName, user.UserType.ToCode());

    // ------------------------------------------------------------------ COUSR01C

    /// <summary>PROCESS-ENTER-KEY + WRITE-USER-SEC-FILE (COUSR01C.cbl:115-273).</summary>
    public async Task<UserAdminResult> AddAsync(UserAddRequest request, CancellationToken cancellationToken = default)
    {
        if (IsBlank(request.FirstName))
        {
            return Error(UserAdminOutcome.ValidationError, FirstNameEmptyMessage, UserAdminFields.FirstName);
        }
        if (IsBlank(request.LastName))
        {
            return Error(UserAdminOutcome.ValidationError, LastNameEmptyMessage, UserAdminFields.LastName);
        }
        if (IsBlank(request.UserId))
        {
            return Error(UserAdminOutcome.ValidationError, UserIdEmptyMessage, UserAdminFields.UserId);
        }
        if (IsBlank(request.Password))
        {
            return Error(UserAdminOutcome.ValidationError, PasswordEmptyMessage, UserAdminFields.Password);
        }
        if (IsBlank(request.UserType))
        {
            return Error(UserAdminOutcome.ValidationError, UserTypeEmptyMessage, UserAdminFields.UserType);
        }

        var userId = Normalize(request.UserId)!;
        bool added;
        try
        {
            var user = new User
            {
                UserId = userId,
                FirstName = Normalize(request.FirstName)!,
                LastName = Normalize(request.LastName)!,
                PasswordHash = passwordHashingService.Hash(Normalize(request.Password)!),
                UserType = UserTypeCodes.FromCode(request.UserType!.Trim()[0])
            };
            added = await userRepository.AddAsync(user, cancellationToken);
        }
        catch (Exception)
        {
            return Error(UserAdminOutcome.StoreError, AddErrorMessage, UserAdminFields.FirstName);
        }

        return added
            ? new UserAdminResult(UserAdminOutcome.Success, AddedMessage(userId), UserAdminSeverity.Success, UserAdminFields.FirstName)
            : Error(UserAdminOutcome.Duplicate, DuplicateUserMessage, UserAdminFields.UserId);
    }

    // ------------------------------------------------------------------ COUSR02C

    /// <summary>PROCESS-ENTER-KEY + READ-USER-SEC-FILE (COUSR02C.cbl:143-172, 318-352).</summary>
    public Task<UserAdminResult> FetchForUpdateAsync(string? userId, CancellationToken cancellationToken = default) =>
        FetchAsync(userId, PressPf5ToSaveMessage, cancellationToken);

    /// <summary>UPDATE-USER-INFO + UPDATE-USER-SEC-FILE (COUSR02C.cbl:177-245, 356-389).</summary>
    public async Task<UserAdminResult> UpdateAsync(UserUpdateRequest request, CancellationToken cancellationToken = default)
    {
        if (IsBlank(request.UserId))
        {
            return Error(UserAdminOutcome.ValidationError, UserIdEmptyMessage, UserAdminFields.UserId);
        }
        if (IsBlank(request.FirstName))
        {
            return Error(UserAdminOutcome.ValidationError, FirstNameEmptyMessage, UserAdminFields.FirstName);
        }
        if (IsBlank(request.LastName))
        {
            return Error(UserAdminOutcome.ValidationError, LastNameEmptyMessage, UserAdminFields.LastName);
        }
        if (IsBlank(request.Password))
        {
            return Error(UserAdminOutcome.ValidationError, PasswordEmptyMessage, UserAdminFields.Password);
        }
        if (IsBlank(request.UserType))
        {
            return Error(UserAdminOutcome.ValidationError, UserTypeEmptyMessage, UserAdminFields.UserType);
        }

        var userId = Normalize(request.UserId)!;
        User? existing;
        try
        {
            existing = await userRepository.GetByIdAsync(userId, cancellationToken);
        }
        catch (Exception)
        {
            return Error(UserAdminOutcome.StoreError, LookupErrorMessage, UserAdminFields.FirstName);
        }
        if (existing is null)
        {
            return Error(UserAdminOutcome.NotFound, UserNotFoundMessage, UserAdminFields.UserId);
        }

        var firstName = Normalize(request.FirstName)!;
        var lastName = Normalize(request.LastName)!;
        var password = Normalize(request.Password)!;
        var userTypeCode = request.UserType!.Trim()[0];

        var modified = false;
        if (firstName != existing.FirstName)
        {
            existing.FirstName = firstName;
            modified = true;
        }
        if (lastName != existing.LastName)
        {
            existing.LastName = lastName;
            modified = true;
        }
        if (!passwordHashingService.Verify(existing.PasswordHash, password))
        {
            existing.PasswordHash = passwordHashingService.Hash(password);
            modified = true;
        }
        if (userTypeCode != existing.UserType.ToCode())
        {
            modified = true;
        }

        if (!modified)
        {
            return Error(UserAdminOutcome.NoChange, PleaseModifyMessage, UserAdminFields.FirstName);
        }

        bool updated;
        try
        {
            existing.UserType = UserTypeCodes.FromCode(userTypeCode);
            updated = await userRepository.UpdateAsync(existing, cancellationToken);
        }
        catch (Exception)
        {
            return Error(UserAdminOutcome.StoreError, UpdateErrorMessage, UserAdminFields.FirstName);
        }

        return updated
            ? new UserAdminResult(UserAdminOutcome.Success, UpdatedMessage(userId), UserAdminSeverity.Success, UserAdminFields.FirstName)
            : Error(UserAdminOutcome.NotFound, UserNotFoundMessage, UserAdminFields.UserId);
    }

    // ------------------------------------------------------------------ COUSR03C

    /// <summary>PROCESS-ENTER-KEY + READ-USER-SEC-FILE (COUSR03C.cbl:142-169, 265-299).</summary>
    public Task<UserAdminResult> FetchForDeleteAsync(string? userId, CancellationToken cancellationToken = default) =>
        FetchAsync(userId, PressPf5ToDeleteMessage, cancellationToken);

    /// <summary>DELETE-USER-INFO + DELETE-USER-SEC-FILE (COUSR03C.cbl:174-192, 303-335).</summary>
    public async Task<UserAdminResult> DeleteAsync(string? userId, CancellationToken cancellationToken = default)
    {
        if (IsBlank(userId))
        {
            return Error(UserAdminOutcome.ValidationError, UserIdEmptyMessage, UserAdminFields.UserId);
        }
        var key = Normalize(userId)!;

        bool deleted;
        try
        {
            deleted = await userRepository.DeleteAsync(key, cancellationToken);
        }
        catch (Exception)
        {
            return Error(UserAdminOutcome.StoreError, UpdateErrorMessage, UserAdminFields.FirstName);
        }

        return deleted
            ? new UserAdminResult(UserAdminOutcome.Success, DeletedMessage(key), UserAdminSeverity.Success, UserAdminFields.UserId)
            : Error(UserAdminOutcome.NotFound, UserNotFoundMessage, UserAdminFields.UserId);
    }

    // ------------------------------------------------------------------ shared

    private async Task<UserAdminResult> FetchAsync(string? userId, string foundMessage, CancellationToken cancellationToken)
    {
        if (IsBlank(userId))
        {
            return Error(UserAdminOutcome.ValidationError, UserIdEmptyMessage, UserAdminFields.UserId);
        }

        User? user;
        try
        {
            user = await userRepository.GetByIdAsync(Normalize(userId)!, cancellationToken);
        }
        catch (Exception)
        {
            return Error(UserAdminOutcome.StoreError, LookupErrorMessage, UserAdminFields.FirstName);
        }

        if (user is null)
        {
            return Error(UserAdminOutcome.NotFound, UserNotFoundMessage, UserAdminFields.UserId);
        }

        return new UserAdminResult(
            UserAdminOutcome.Success,
            foundMessage,
            UserAdminSeverity.Neutral,
            UserAdminFields.UserId,
            new UserAdminDetails(user.UserId, user.FirstName, user.LastName, user.UserType.ToCode()));
    }

    private static UserAdminResult Error(UserAdminOutcome outcome, string message, string focusField) =>
        new(outcome, message, UserAdminSeverity.Error, focusField);

    /// <summary>`SPACES OR LOW-VALUES` test on a BMS input field.</summary>
    private static bool IsBlank(string? value) => string.IsNullOrEmpty(value) || value.All(c => c == ' ' || c == '\0');

    /// <summary>Strip BMS trailing padding; leading characters are kept as typed.</summary>
    private static string? Normalize(string? value)
    {
        if (value is null)
        {
            return null;
        }
        var trimmed = value.TrimEnd(' ', '\0');
        return trimmed.Length == 0 ? null : trimmed;
    }

    /// <summary>`SEC-USR-ID DELIMITED BY SPACE` in the STRING statements.</summary>
    private static string FirstWord(string value)
    {
        var index = value.IndexOf(' ');
        return index < 0 ? value : value[..index];
    }

    /// <summary>HIGH-VALUES start key when CDEMO-CU00-USRID-LAST is blank (COUSR00C.cbl:262-263).</summary>
    private static readonly string HighValues = new('\uFFFF', 8);
}
