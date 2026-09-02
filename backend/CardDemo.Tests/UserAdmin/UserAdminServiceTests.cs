using CardDemo.Application.UserAdmin;
using CardDemo.Application.Users;
using CardDemo.Domain.Users;
using CardDemo.Infrastructure.Security;
using FluentAssertions;

namespace CardDemo.Tests.UserAdmin;

/// <summary>
/// Paragraph-level parity for COUSR00C/01C/02C/03C against an in-memory USRSEC (FR-S12-01..40).
/// </summary>
public class UserAdminServiceTests
{
    private static readonly IPasswordHashingService Hasher = new IdentityPasswordHashingService();

    private static async Task<(UserAdminService Service, InMemoryUserAdminRepository Repository)> BuildAsync(int userCount = 0)
    {
        var repository = new InMemoryUserAdminRepository();
        for (var i = 1; i <= userCount; i++)
        {
            await repository.UpsertAsync(MakeUser($"USER{i:D4}", i % 2 == 0 ? UserType.Admin : UserType.User));
        }
        return (new UserAdminService(repository, Hasher), repository);
    }

    private static User MakeUser(string id, UserType type = UserType.User, string password = "PASSWORD") => new()
    {
        UserId = id,
        FirstName = $"FIRST{id}",
        LastName = $"LAST{id}",
        PasswordHash = Hasher.Hash(password),
        UserType = type
    };

    private static UserListRequest Enter(string? search = null, IReadOnlyList<UserListSelection>? selections = null) =>
        new(UserListAction.Enter, search, selections, PageNum: 0, NextPage: false, FirstUserId: null, LastUserId: null);

    private static UserListRequest Pf8(UserListResult state) =>
        new(UserListAction.PageForward, null, null, state.PageNum, state.NextPage, state.FirstUserId, state.LastUserId);

    private static UserListRequest Pf7(UserListResult state) =>
        new(UserListAction.PageBackward, null, null, state.PageNum, state.NextPage, state.FirstUserId, state.LastUserId);

    // ------------------------------------------------------------ COUSR00C

    [Fact]
    public async Task List_FirstEntry_ReturnsFirstTenUsersInKeyOrder_FrS1201()
    {
        var (service, _) = await BuildAsync(25);

        var result = await service.ListAsync(Enter());

        result.Rows.Select(r => r.UserId).Should().Equal(Enumerable.Range(1, 10).Select(i => $"USER{i:D4}"));
        result.PageNum.Should().Be(1);
        result.NextPage.Should().BeTrue();
        result.FirstUserId.Should().Be("USER0001");
        result.LastUserId.Should().Be("USER0010");
        result.Message.Should().BeNull();
        result.SearchUserId.Should().BeNull();
        result.Rows[0].FirstName.Should().Be("FIRSTUSER0001");
        result.Rows[1].UserType.Should().Be('A');
    }

    [Fact]
    public async Task List_SearchKey_StartsAtFirstKeyGreaterOrEqual_FrS1202()
    {
        var (service, _) = await BuildAsync(25);

        var result = await service.ListAsync(Enter("USER0015"));

        result.Rows[0].UserId.Should().Be("USER0015");
        result.PageNum.Should().Be(1);
        result.NextPage.Should().BeTrue();
    }

    [Theory]
    [InlineData("U", "COUSR02C")]
    [InlineData("u", "COUSR02C")]
    [InlineData("D", "COUSR03C")]
    [InlineData("d", "COUSR03C")]
    public async Task List_SelectionUOrD_NavigatesToUpdateOrDelete_FrS1203_04(string flag, string program)
    {
        var (service, _) = await BuildAsync(5);
        var selections = new List<UserListSelection>
        {
            new("USER0001", " "),
            new("USER0002", flag),
            new("USER0003", null)
        };

        var result = await service.ListAsync(Enter(null, selections));

        result.Navigate.Should().Be(new UserListNavigation(program, "USER0002"));
        result.Message.Should().BeNull();
    }

    [Fact]
    public async Task List_InvalidSelection_ReturnsMessageAndRefreshesList_FrS1205()
    {
        var (service, _) = await BuildAsync(25);
        var selections = new List<UserListSelection> { new("USER0001", "X") };

        var result = await service.ListAsync(Enter(null, selections));

        result.Navigate.Should().BeNull();
        result.Message.Should().Be("Invalid selection. Valid values are U and D");
        result.Severity.Should().Be(UserAdminSeverity.Error);
        result.Rows.Should().HaveCount(10);
        result.PageNum.Should().Be(1);
    }

    [Fact]
    public async Task List_FirstNonBlankSelectionWins_FrS1206()
    {
        var (service, _) = await BuildAsync(5);
        var selections = new List<UserListSelection>
        {
            new("USER0001", ""),
            new("USER0002", "D"),
            new("USER0003", "U")
        };

        var result = await service.ListAsync(Enter(null, selections));

        result.Navigate!.ProgramKey.Should().Be("COUSR03C");
        result.Navigate.UserId.Should().Be("USER0002");
    }

    [Fact]
    public async Task List_Pf8_ReturnsNextPageAfterLastUserId_FrS1207()
    {
        var (service, _) = await BuildAsync(25);
        var page1 = await service.ListAsync(Enter());

        var page2 = await service.ListAsync(Pf8(page1));

        page2.Rows.Select(r => r.UserId).Should().Equal(Enumerable.Range(11, 10).Select(i => $"USER{i:D4}"));
        page2.PageNum.Should().Be(2);
        page2.NextPage.Should().BeTrue();
        page2.FirstUserId.Should().Be("USER0011");
        page2.LastUserId.Should().Be("USER0020");
        page2.Message.Should().BeNull();
    }

    [Fact]
    public async Task List_Pf8AtLastPage_ReturnsAlreadyAtBottom_FrS1208()
    {
        var (service, _) = await BuildAsync(5);
        var page1 = await service.ListAsync(Enter());
        page1.NextPage.Should().BeFalse();

        var result = await service.ListAsync(Pf8(page1));

        result.Message.Should().Be("You are already at the bottom of the page...");
        result.Severity.Should().Be(UserAdminSeverity.Error);
        result.PageNum.Should().Be(1);
        result.Rows.Should().BeEmpty();
    }

    [Fact]
    public async Task List_ForwardShortPage_ReturnsReachedBottom_FrS1209()
    {
        var (service, _) = await BuildAsync(12);
        var page1 = await service.ListAsync(Enter());

        var page2 = await service.ListAsync(Pf8(page1));

        page2.Rows.Select(r => r.UserId).Should().Equal("USER0011", "USER0012");
        page2.PageNum.Should().Be(2);
        page2.NextPage.Should().BeFalse();
        page2.Message.Should().Be("You have reached the bottom of the page...");
        page2.FirstUserId.Should().Be("USER0011");
        page2.LastUserId.Should().Be("USER0010", "USRID-LAST is only set when row 10 is populated");
    }

    [Fact]
    public async Task List_ExactlyTenUsers_FirstPageReportsBottomWithNoNextPage_FrS1209()
    {
        var (service, _) = await BuildAsync(10);

        var result = await service.ListAsync(Enter());

        result.Rows.Should().HaveCount(10);
        result.PageNum.Should().Be(1);
        result.NextPage.Should().BeFalse();
        result.Message.Should().Be("You have reached the bottom of the page...");
    }

    [Fact]
    public async Task List_Pf7_ReturnsPreviousPage_FrS1210()
    {
        var (service, _) = await BuildAsync(35);
        var page1 = await service.ListAsync(Enter());
        var page2 = await service.ListAsync(Pf8(page1));
        var page3 = await service.ListAsync(Pf8(page2));
        page3.PageNum.Should().Be(3);

        var back = await service.ListAsync(Pf7(page3));

        back.Rows.Select(r => r.UserId).Should().Equal(Enumerable.Range(11, 10).Select(i => $"USER{i:D4}"));
        back.PageNum.Should().Be(2);
        back.NextPage.Should().BeTrue();
        back.FirstUserId.Should().Be("USER0011");
        back.LastUserId.Should().Be("USER0020");
        back.Message.Should().BeNull();
    }

    [Fact]
    public async Task List_Pf7AtFirstPage_ReturnsAlreadyAtTop_FrS1211()
    {
        var (service, _) = await BuildAsync(25);
        var page1 = await service.ListAsync(Enter());

        var result = await service.ListAsync(Pf7(page1));

        result.Message.Should().Be("You are already at the top of the page...");
        result.Severity.Should().Be(UserAdminSeverity.Error);
        result.PageNum.Should().Be(1);
        result.NextPage.Should().BeTrue("SET NEXT-PAGE-YES precedes the guard in PROCESS-PF7-KEY");
    }

    [Fact]
    public async Task List_Pf7BackToFirstPage_ReturnsReachedTopAndPageOne_FrS1212()
    {
        var (service, _) = await BuildAsync(25);
        var page1 = await service.ListAsync(Enter());
        var page2 = await service.ListAsync(Pf8(page1));

        var back = await service.ListAsync(Pf7(page2));

        back.Rows.Select(r => r.UserId).Should().Equal(Enumerable.Range(1, 10).Select(i => $"USER{i:D4}"));
        back.PageNum.Should().Be(1);
        back.NextPage.Should().BeTrue();
        back.Message.Should().Be("You have reached the top of the page...");
    }

    [Fact]
    public async Task List_SearchBeyondLastKey_ReturnsAtTopWithNoRows_FrS1213()
    {
        var (service, _) = await BuildAsync(5);

        var result = await service.ListAsync(Enter("ZZZZZZZZ"));

        result.Rows.Should().BeEmpty();
        result.PageNum.Should().Be(0);
        result.NextPage.Should().BeFalse();
        result.Message.Should().Be("You are at the top of the page...");
        result.Severity.Should().Be(UserAdminSeverity.Error);
    }

    [Fact]
    public async Task List_EmptyFile_ReturnsAtTop_FrS1213()
    {
        var (service, _) = await BuildAsync(0);

        var result = await service.ListAsync(Enter());

        result.Rows.Should().BeEmpty();
        result.Message.Should().Be("You are at the top of the page...");
    }

    [Fact]
    public async Task List_StoreError_ReturnsUnableToLookup_FrS1214()
    {
        var (service, repository) = await BuildAsync(25);
        var page1 = await service.ListAsync(Enter());
        repository.ThrowOnRead = true;

        var forward = await service.ListAsync(Enter());
        var pf8 = await service.ListAsync(Pf8(page1));
        var pf7 = await service.ListAsync(Pf7(page1 with { PageNum = 2 }));

        forward.Message.Should().Be("Unable to lookup User...");
        pf8.Message.Should().Be("Unable to lookup User...");
        pf7.Message.Should().Be("Unable to lookup User...");
        pf8.PageNum.Should().Be(1);
    }

    // ------------------------------------------------------------ COUSR01C

    [Theory]
    [InlineData("", "LAST", "NEWUSER1", "PW", "U", "First Name can NOT be empty...", "firstName")]
    [InlineData("FIRST", "   ", "NEWUSER1", "PW", "U", "Last Name can NOT be empty...", "lastName")]
    [InlineData("FIRST", "LAST", null, "PW", "U", "User ID can NOT be empty...", "userId")]
    [InlineData("FIRST", "LAST", "NEWUSER1", "", "U", "Password can NOT be empty...", "password")]
    [InlineData("FIRST", "LAST", "NEWUSER1", "PW", " ", "User Type can NOT be empty...", "userType")]
    public async Task Add_BlankField_ReturnsFirstFailingMessageInOrder_FrS1217(
        string? first, string? last, string? id, string? pwd, string? type, string message, string focus)
    {
        var (service, repository) = await BuildAsync();

        var result = await service.AddAsync(new UserAddRequest(first, last, id, pwd, type));

        result.Outcome.Should().Be(UserAdminOutcome.ValidationError);
        result.Message.Should().Be(message);
        result.Severity.Should().Be(UserAdminSeverity.Error);
        result.FocusField.Should().Be(focus);
        repository.Users.Should().BeEmpty();
    }

    [Fact]
    public async Task Add_AllBlank_FirstNameMessageWins_FrS1217()
    {
        var (service, _) = await BuildAsync();

        var result = await service.AddAsync(new UserAddRequest("", "", "", "", ""));

        result.Message.Should().Be("First Name can NOT be empty...");
    }

    [Fact]
    public async Task Add_NewUser_WritesRecordAndReturnsAddedMessage_FrS1218()
    {
        var (service, repository) = await BuildAsync();

        var result = await service.AddAsync(new UserAddRequest("Jane", "Doe", "JDOE0001", "secret1", "A"));

        result.Outcome.Should().Be(UserAdminOutcome.Success);
        result.Message.Should().Be("User JDOE0001 has been added ...");
        result.Severity.Should().Be(UserAdminSeverity.Success);
        var stored = repository.Users.Single();
        stored.UserId.Should().Be("JDOE0001");
        stored.FirstName.Should().Be("Jane");
        stored.LastName.Should().Be("Doe");
        stored.UserType.Should().Be(UserType.Admin);
        Hasher.Verify(stored.PasswordHash, "secret1").Should().BeTrue();
        stored.PasswordHash.Should().NotBe("secret1");
    }

    [Fact]
    public async Task Add_TrailingPaddingIsStripped_CaseIsPreserved_FrS1218()
    {
        var (service, repository) = await BuildAsync();

        var result = await service.AddAsync(new UserAddRequest("jane      ", "doe  ", "jdoe    ", "pw      ", "U"));

        result.Message.Should().Be("User jdoe has been added ...");
        repository.Users.Single().UserId.Should().Be("jdoe");
        repository.Users.Single().FirstName.Should().Be("jane");
    }

    [Fact]
    public async Task Add_DuplicateUserId_ReturnsAlreadyExist_FrS1219()
    {
        var (service, repository) = await BuildAsync(1);

        var result = await service.AddAsync(new UserAddRequest("X", "Y", "USER0001", "PW", "U"));

        result.Outcome.Should().Be(UserAdminOutcome.Duplicate);
        result.Message.Should().Be("User ID already exist...");
        result.Severity.Should().Be(UserAdminSeverity.Error);
        result.FocusField.Should().Be("userId");
        repository.Users.Single().FirstName.Should().Be("FIRSTUSER0001");
    }

    [Fact]
    public async Task Add_StoreError_ReturnsUnableToAdd_FrS1220()
    {
        var (service, repository) = await BuildAsync();
        repository.ThrowOnWrite = true;

        var result = await service.AddAsync(new UserAddRequest("X", "Y", "NEWUSER1", "PW", "U"));

        result.Outcome.Should().Be(UserAdminOutcome.StoreError);
        result.Message.Should().Be("Unable to Add User...");
    }

    [Fact]
    public async Task Add_UserTypeOutsideDomain_ReturnsUnableToAdd_S12B1()
    {
        var (service, repository) = await BuildAsync();

        var result = await service.AddAsync(new UserAddRequest("X", "Y", "NEWUSER1", "PW", "X"));

        result.Outcome.Should().Be(UserAdminOutcome.StoreError);
        result.Message.Should().Be("Unable to Add User...");
        repository.Users.Should().BeEmpty();
    }

    // ------------------------------------------------------------ COUSR02C

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("        ")]
    public async Task FetchForUpdate_BlankUserId_ReturnsUserIdEmpty_FrS1225(string? userId)
    {
        var (service, _) = await BuildAsync(1);

        var result = await service.FetchForUpdateAsync(userId);

        result.Outcome.Should().Be(UserAdminOutcome.ValidationError);
        result.Message.Should().Be("User ID can NOT be empty...");
        result.FocusField.Should().Be("userId");
    }

    [Fact]
    public async Task FetchForUpdate_Found_ReturnsDetailsWithoutPasswordAndPf5Prompt_FrS1226()
    {
        var (service, _) = await BuildAsync(2);

        var result = await service.FetchForUpdateAsync("USER0002");

        result.Outcome.Should().Be(UserAdminOutcome.Success);
        result.Message.Should().Be("Press PF5 key to save your updates ...");
        result.Severity.Should().Be(UserAdminSeverity.Neutral);
        result.User.Should().Be(new UserAdminDetails("USER0002", "FIRSTUSER0002", "LASTUSER0002", 'A'));
    }

    [Fact]
    public async Task FetchForUpdate_NotFound_ReturnsUserIdNotFound_FrS1227()
    {
        var (service, _) = await BuildAsync(1);

        var result = await service.FetchForUpdateAsync("NOBODY");

        result.Outcome.Should().Be(UserAdminOutcome.NotFound);
        result.Message.Should().Be("User ID NOT found...");
        result.Severity.Should().Be(UserAdminSeverity.Error);
        result.User.Should().BeNull();
    }

    [Fact]
    public async Task FetchForUpdate_StoreError_ReturnsUnableToLookup_FrS1228()
    {
        var (service, repository) = await BuildAsync(1);
        repository.ThrowOnRead = true;

        var result = await service.FetchForUpdateAsync("USER0001");

        result.Outcome.Should().Be(UserAdminOutcome.StoreError);
        result.Message.Should().Be("Unable to lookup User...");
    }

    [Theory]
    [InlineData("", "F", "L", "PW", "U", "User ID can NOT be empty...", "userId")]
    [InlineData("USER0001", "", "L", "PW", "U", "First Name can NOT be empty...", "firstName")]
    [InlineData("USER0001", "F", "", "PW", "U", "Last Name can NOT be empty...", "lastName")]
    [InlineData("USER0001", "F", "L", "", "U", "Password can NOT be empty...", "password")]
    [InlineData("USER0001", "F", "L", "PW", "", "User Type can NOT be empty...", "userType")]
    public async Task Update_BlankField_ReturnsFirstFailingMessageInOrder_FrS1229(
        string? id, string? first, string? last, string? pwd, string? type, string message, string focus)
    {
        var (service, _) = await BuildAsync(1);

        var result = await service.UpdateAsync(new UserUpdateRequest(id, first, last, pwd, type));

        result.Outcome.Should().Be(UserAdminOutcome.ValidationError);
        result.Message.Should().Be(message);
        result.FocusField.Should().Be(focus);
    }

    [Fact]
    public async Task Update_NothingChanged_ReturnsPleaseModify_FrS1230()
    {
        var (service, repository) = await BuildAsync(1);
        var before = repository.Users.Single().PasswordHash;

        var result = await service.UpdateAsync(new UserUpdateRequest("USER0001", "FIRSTUSER0001", "LASTUSER0001", "PASSWORD", "U"));

        result.Outcome.Should().Be(UserAdminOutcome.NoChange);
        result.Message.Should().Be("Please modify to update ...");
        result.Severity.Should().Be(UserAdminSeverity.Error);
        repository.Users.Single().PasswordHash.Should().Be(before);
    }

    [Theory]
    [InlineData("NEWFIRST", "LASTUSER0001", "PASSWORD", "U")]
    [InlineData("FIRSTUSER0001", "NEWLAST", "PASSWORD", "U")]
    [InlineData("FIRSTUSER0001", "LASTUSER0001", "NEWPASS", "U")]
    [InlineData("FIRSTUSER0001", "LASTUSER0001", "PASSWORD", "A")]
    public async Task Update_AnyFieldChanged_RewritesAndReturnsUpdated_FrS1231(string first, string last, string pwd, string type)
    {
        var (service, repository) = await BuildAsync(1);

        var result = await service.UpdateAsync(new UserUpdateRequest("USER0001", first, last, pwd, type));

        result.Outcome.Should().Be(UserAdminOutcome.Success);
        result.Message.Should().Be("User USER0001 has been updated ...");
        result.Severity.Should().Be(UserAdminSeverity.Success);
        var stored = repository.Users.Single();
        stored.FirstName.Should().Be(first);
        stored.LastName.Should().Be(last);
        stored.UserType.ToCode().Should().Be(type[0]);
        Hasher.Verify(stored.PasswordHash, pwd).Should().BeTrue();
    }

    [Fact]
    public async Task Update_UnknownUser_ReturnsUserIdNotFound_FrS1232()
    {
        var (service, _) = await BuildAsync(1);

        var result = await service.UpdateAsync(new UserUpdateRequest("NOBODY", "F", "L", "PW", "U"));

        result.Outcome.Should().Be(UserAdminOutcome.NotFound);
        result.Message.Should().Be("User ID NOT found...");
    }

    [Fact]
    public async Task Update_StoreErrorOnRewrite_ReturnsUnableToUpdate_FrS1232()
    {
        var (service, repository) = await BuildAsync(1);
        repository.ThrowOnWrite = true;

        var result = await service.UpdateAsync(new UserUpdateRequest("USER0001", "NEW", "L", "PW", "U"));

        result.Outcome.Should().Be(UserAdminOutcome.StoreError);
        result.Message.Should().Be("Unable to Update User...");
    }

    [Fact]
    public async Task Update_StoreErrorOnRead_ReturnsUnableToLookup_FrS1232()
    {
        var (service, repository) = await BuildAsync(1);
        repository.ThrowOnRead = true;

        var result = await service.UpdateAsync(new UserUpdateRequest("USER0001", "NEW", "L", "PW", "U"));

        result.Message.Should().Be("Unable to lookup User...");
    }

    [Fact]
    public async Task Update_UserTypeOutsideDomain_ReturnsUnableToUpdate_S12B1()
    {
        var (service, repository) = await BuildAsync(1);

        var result = await service.UpdateAsync(new UserUpdateRequest("USER0001", "FIRSTUSER0001", "LASTUSER0001", "PASSWORD", "X"));

        result.Outcome.Should().Be(UserAdminOutcome.StoreError);
        result.Message.Should().Be("Unable to Update User...");
        repository.Users.Single().UserType.Should().Be(UserType.User);
    }

    // ------------------------------------------------------------ COUSR03C

    [Fact]
    public async Task FetchForDelete_BlankUserId_ReturnsUserIdEmpty_FrS1238()
    {
        var (service, _) = await BuildAsync(1);

        var result = await service.FetchForDeleteAsync("  ");

        result.Message.Should().Be("User ID can NOT be empty...");
        result.FocusField.Should().Be("userId");
    }

    [Fact]
    public async Task FetchForDelete_Found_ReturnsDetailsAndPf5Prompt_FrS1238()
    {
        var (service, _) = await BuildAsync(1);

        var result = await service.FetchForDeleteAsync("USER0001");

        result.Outcome.Should().Be(UserAdminOutcome.Success);
        result.Message.Should().Be("Press PF5 key to delete this user ...");
        result.Severity.Should().Be(UserAdminSeverity.Neutral);
        result.User.Should().Be(new UserAdminDetails("USER0001", "FIRSTUSER0001", "LASTUSER0001", 'U'));
    }

    [Fact]
    public async Task FetchForDelete_NotFoundAndStoreError_FrS1238()
    {
        var (service, repository) = await BuildAsync(1);

        var notFound = await service.FetchForDeleteAsync("NOBODY");
        repository.ThrowOnRead = true;
        var error = await service.FetchForDeleteAsync("USER0001");

        notFound.Message.Should().Be("User ID NOT found...");
        error.Message.Should().Be("Unable to lookup User...");
    }

    [Fact]
    public async Task Delete_BlankUserId_ReturnsUserIdEmpty_FrS1239()
    {
        var (service, repository) = await BuildAsync(1);

        var result = await service.DeleteAsync("");

        result.Message.Should().Be("User ID can NOT be empty...");
        repository.Users.Should().HaveCount(1);
    }

    [Fact]
    public async Task Delete_ExistingUser_RemovesRecordAndReturnsDeleted_FrS1239()
    {
        var (service, repository) = await BuildAsync(2);

        var result = await service.DeleteAsync("USER0001");

        result.Outcome.Should().Be(UserAdminOutcome.Success);
        result.Message.Should().Be("User USER0001 has been deleted ...");
        result.Severity.Should().Be(UserAdminSeverity.Success);
        repository.Users.Select(u => u.UserId).Should().Equal("USER0002");
    }

    [Fact]
    public async Task Delete_UnknownUser_ReturnsUserIdNotFound_FrS1239()
    {
        var (service, _) = await BuildAsync(1);

        var result = await service.DeleteAsync("NOBODY");

        result.Outcome.Should().Be(UserAdminOutcome.NotFound);
        result.Message.Should().Be("User ID NOT found...");
    }

    [Fact]
    public async Task Delete_StoreError_ReturnsSourceUnableToUpdateText_FrS1239()
    {
        var (service, repository) = await BuildAsync(1);
        repository.ThrowOnWrite = true;

        var result = await service.DeleteAsync("USER0001");

        result.Outcome.Should().Be(UserAdminOutcome.StoreError);
        result.Message.Should().Be("Unable to Update User...");
    }
}
