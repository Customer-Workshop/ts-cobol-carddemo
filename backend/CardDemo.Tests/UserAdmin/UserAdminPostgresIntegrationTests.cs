using CardDemo.Application.UserAdmin;
using CardDemo.Application.Users;
using CardDemo.Domain.Users;
using CardDemo.Infrastructure.Persistence;
using CardDemo.Infrastructure.Security;
using CardDemo.Tests.Users;
using FluentAssertions;
using Microsoft.EntityFrameworkCore;

namespace CardDemo.Tests.UserAdmin;

/// <summary>
/// COUSR00C-03C against a real PostgreSQL 16 `users` table through <see cref="UserRepository"/>:
/// keyed WRITE/REWRITE/DELETE responses and STARTBR/READNEXT/READPREV paging (FR-S12-01..40).
/// </summary>
[Collection(nameof(UserAdminPostgresCollection))]
public class UserAdminPostgresIntegrationTests(PostgresFixture fixture) : IAsyncLifetime
{
    private static readonly IPasswordHashingService Hasher = new IdentityPasswordHashingService();

    public async Task InitializeAsync()
    {
        await using var context = fixture.CreateContext();
        await context.Database.ExecuteSqlRawAsync("DELETE FROM users");
    }

    public Task DisposeAsync() => Task.CompletedTask;

    private async Task SeedAsync(int count)
    {
        await using var context = fixture.CreateContext();
        for (var i = 1; i <= count; i++)
        {
            context.Users.Add(new User
            {
                UserId = $"USER{i:D4}",
                FirstName = $"FIRST{i:D4}",
                LastName = $"LAST{i:D4}",
                PasswordHash = Hasher.Hash("PASSWORD"),
                UserType = i % 2 == 0 ? UserType.Admin : UserType.User
            });
        }
        await context.SaveChangesAsync();
    }

    private async Task<T> WithServiceAsync<T>(Func<UserAdminService, Task<T>> action)
    {
        await using var context = fixture.CreateContext();
        return await action(new UserAdminService(new UserRepository(context), Hasher));
    }

    private async Task<User?> LoadAsync(string userId)
    {
        await using var context = fixture.CreateContext();
        return await context.Users.AsNoTracking().SingleOrDefaultAsync(u => u.UserId == userId);
    }

    private static UserListRequest Enter(string? search = null) =>
        new(UserListAction.Enter, search, null, 0, false, null, null);

    private static UserListRequest Pf8(UserListResult s) => new(UserListAction.PageForward, null, null, s.PageNum, s.NextPage, s.FirstUserId, s.LastUserId);

    private static UserListRequest Pf7(UserListResult s) => new(UserListAction.PageBackward, null, null, s.PageNum, s.NextPage, s.FirstUserId, s.LastUserId);

    // ------------------------------------------------------------ COUSR00C

    [Fact]
    public async Task List_PagesForwardAndBackwardInKeyOrder_FrS1201_07_09_10_12()
    {
        await SeedAsync(23);

        var page1 = await WithServiceAsync(s => s.ListAsync(Enter()));
        var page2 = await WithServiceAsync(s => s.ListAsync(Pf8(page1)));
        var page3 = await WithServiceAsync(s => s.ListAsync(Pf8(page2)));
        var pf8AtEnd = await WithServiceAsync(s => s.ListAsync(Pf8(page3)));
        var backTo2 = await WithServiceAsync(s => s.ListAsync(Pf7(page3)));
        var backTo1 = await WithServiceAsync(s => s.ListAsync(Pf7(backTo2)));
        var pf7AtTop = await WithServiceAsync(s => s.ListAsync(Pf7(backTo1)));

        page1.Rows.Select(r => r.UserId).Should().Equal(Enumerable.Range(1, 10).Select(i => $"USER{i:D4}"));
        page1.PageNum.Should().Be(1);
        page1.NextPage.Should().BeTrue();

        page2.Rows.Select(r => r.UserId).Should().Equal(Enumerable.Range(11, 10).Select(i => $"USER{i:D4}"));
        page2.PageNum.Should().Be(2);

        page3.Rows.Select(r => r.UserId).Should().Equal("USER0021", "USER0022", "USER0023");
        page3.PageNum.Should().Be(3);
        page3.NextPage.Should().BeFalse();
        page3.Message.Should().Be("You have reached the bottom of the page...");

        pf8AtEnd.Message.Should().Be("You are already at the bottom of the page...");

        backTo2.Rows.Select(r => r.UserId).Should().Equal(Enumerable.Range(11, 10).Select(i => $"USER{i:D4}"));
        backTo2.PageNum.Should().Be(2);
        backTo2.Message.Should().BeNull();

        backTo1.Rows.Select(r => r.UserId).Should().Equal(Enumerable.Range(1, 10).Select(i => $"USER{i:D4}"));
        backTo1.PageNum.Should().Be(1);
        backTo1.Message.Should().Be("You have reached the top of the page...");

        pf7AtTop.Message.Should().Be("You are already at the top of the page...");
    }

    [Fact]
    public async Task List_SearchKeyPositionsGteqAndBeyondEndReportsTop_FrS1202_13()
    {
        await SeedAsync(15);

        var fromKey = await WithServiceAsync(s => s.ListAsync(Enter("USER0008")));
        var beyond = await WithServiceAsync(s => s.ListAsync(Enter("ZZZZ")));
        var between = await WithServiceAsync(s => s.ListAsync(Enter("USER00085")));

        fromKey.Rows[0].UserId.Should().Be("USER0008");
        fromKey.Rows.Should().HaveCount(8);
        fromKey.Message.Should().Be("You have reached the bottom of the page...");

        beyond.Rows.Should().BeEmpty();
        beyond.Message.Should().Be("You are at the top of the page...");

        between.Rows[0].UserId.Should().Be("USER0009", "GTEQ positions at the next higher key");
    }

    [Fact]
    public async Task List_RowsCarryNameAndTypeFromTheRecord_FrS1201()
    {
        await SeedAsync(2);

        var page = await WithServiceAsync(s => s.ListAsync(Enter()));

        page.Rows.Should().Equal(
            new UserListRow("USER0001", "FIRST0001", "LAST0001", 'U'),
            new UserListRow("USER0002", "FIRST0002", "LAST0002", 'A'));
    }

    // ------------------------------------------------------------ COUSR01C

    [Fact]
    public async Task Add_WritesHashedRecordThenRejectsDuplicateKey_FrS1218_19()
    {
        var added = await WithServiceAsync(s => s.AddAsync(new UserAddRequest("Ada", "Lovelace", "ADA00001", "engine", "A")));
        var duplicate = await WithServiceAsync(s => s.AddAsync(new UserAddRequest("Other", "Person", "ADA00001", "x", "U")));

        added.Outcome.Should().Be(UserAdminOutcome.Success);
        added.Message.Should().Be("User ADA00001 has been added ...");
        var stored = await LoadAsync("ADA00001");
        stored!.FirstName.Should().Be("Ada");
        stored.UserType.Should().Be(UserType.Admin);
        Hasher.Verify(stored.PasswordHash, "engine").Should().BeTrue();

        duplicate.Outcome.Should().Be(UserAdminOutcome.Duplicate);
        duplicate.Message.Should().Be("User ID already exist...");
        (await LoadAsync("ADA00001"))!.FirstName.Should().Be("Ada");
    }

    [Fact]
    public async Task Add_ValueTooLongForColumn_ReturnsUnableToAdd_FrS1220()
    {
        var result = await WithServiceAsync(s => s.AddAsync(new UserAddRequest("F", "L", "TOO-LONG-USER-ID", "pw", "U")));

        result.Outcome.Should().Be(UserAdminOutcome.StoreError);
        result.Message.Should().Be("Unable to Add User...");
    }

    // ------------------------------------------------------------ COUSR02C

    [Fact]
    public async Task Update_FetchThenRewrite_FrS1226_27_30_31_32()
    {
        await SeedAsync(1);

        var fetched = await WithServiceAsync(s => s.FetchForUpdateAsync("USER0001"));
        var missing = await WithServiceAsync(s => s.FetchForUpdateAsync("USER9999"));
        var noChange = await WithServiceAsync(s => s.UpdateAsync(new UserUpdateRequest("USER0001", "FIRST0001", "LAST0001", "PASSWORD", "U")));
        var updated = await WithServiceAsync(s => s.UpdateAsync(new UserUpdateRequest("USER0001", "Grace", "LAST0001", "NEWPASS", "A")));
        var notFound = await WithServiceAsync(s => s.UpdateAsync(new UserUpdateRequest("USER9999", "F", "L", "pw", "U")));

        fetched.Message.Should().Be("Press PF5 key to save your updates ...");
        fetched.User.Should().Be(new UserAdminDetails("USER0001", "FIRST0001", "LAST0001", 'U'));
        missing.Message.Should().Be("User ID NOT found...");
        noChange.Message.Should().Be("Please modify to update ...");

        updated.Outcome.Should().Be(UserAdminOutcome.Success);
        updated.Message.Should().Be("User USER0001 has been updated ...");
        var stored = await LoadAsync("USER0001");
        stored!.FirstName.Should().Be("Grace");
        stored.UserType.Should().Be(UserType.Admin);
        Hasher.Verify(stored.PasswordHash, "NEWPASS").Should().BeTrue();

        notFound.Message.Should().Be("User ID NOT found...");
    }

    // ------------------------------------------------------------ COUSR03C

    [Fact]
    public async Task Delete_FetchThenDelete_FrS1238_39()
    {
        await SeedAsync(2);

        var fetched = await WithServiceAsync(s => s.FetchForDeleteAsync("USER0002"));
        var deleted = await WithServiceAsync(s => s.DeleteAsync("USER0002"));
        var again = await WithServiceAsync(s => s.DeleteAsync("USER0002"));

        fetched.Message.Should().Be("Press PF5 key to delete this user ...");
        fetched.User.Should().Be(new UserAdminDetails("USER0002", "FIRST0002", "LAST0002", 'A'));

        deleted.Outcome.Should().Be(UserAdminOutcome.Success);
        deleted.Message.Should().Be("User USER0002 has been deleted ...");
        (await LoadAsync("USER0002")).Should().BeNull();
        (await LoadAsync("USER0001")).Should().NotBeNull();

        again.Outcome.Should().Be(UserAdminOutcome.NotFound);
        again.Message.Should().Be("User ID NOT found...");
    }
}

[CollectionDefinition(nameof(UserAdminPostgresCollection))]
public class UserAdminPostgresCollection : ICollectionFixture<PostgresFixture>;
