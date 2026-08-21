using CardDemo.Application.Users;
using CardDemo.Domain.Users;
using CardDemo.Infrastructure.Persistence;
using CardDemo.Infrastructure.Security;
using FluentAssertions;

namespace CardDemo.Tests.Users;

public class UsrsecImportIntegrationTests(PostgresFixture fixture) : IClassFixture<PostgresFixture>
{
    private readonly IPasswordHashingService _hasher = new IdentityPasswordHashingService();

    private async Task<int> RunImportAsync()
    {
        await using var context = fixture.CreateContext();
        var importer = new UsrsecImportService(new UserRepository(context), _hasher);
        return await importer.ImportAsync(UsrsecSeedSource.ReadRecords(TestPaths.UsrsecSeedJcl));
    }

    [Fact]
    public async Task Import_ParsesRealSeedRecordsAndIsIdempotent()
    {
        var firstRun = await RunImportAsync();
        var secondRun = await RunImportAsync();

        firstRun.Should().Be(10);
        secondRun.Should().Be(10);

        await using var context = fixture.CreateContext();
        var repository = new UserRepository(context);
        (await repository.CountAsync()).Should().Be(10, "re-running the import must upsert, not duplicate");

        var admin = await repository.GetByIdAsync("ADMIN001");
        admin.Should().NotBeNull();
        admin!.FirstName.Should().Be("MARGARET");
        admin.LastName.Should().Be("GOLD");
        admin.UserType.Should().Be(UserType.Admin);

        var user = await repository.GetByIdAsync("USER0001");
        user.Should().NotBeNull();
        user!.FirstName.Should().Be("LAWRENCE");
        user.LastName.Should().Be("THOMAS");
        user.UserType.Should().Be(UserType.User);
    }

    [Fact]
    public async Task GetById_NotFoundParityWithVsamResp13()
    {
        await RunImportAsync();

        await using var context = fixture.CreateContext();
        var repository = new UserRepository(context);

        (await repository.GetByIdAsync("NOSUCHID")).Should().BeNull();
    }

    [Fact]
    public async Task PasswordVerification_SucceedsAgainstHashedStorageForKnownSeedCredential()
    {
        await RunImportAsync();

        await using var context = fixture.CreateContext();
        var admin = await new UserRepository(context).GetByIdAsync("ADMIN001");

        admin.Should().NotBeNull();
        admin!.PasswordHash.Should().NotBe("PASSWORD", "passwords must be stored hashed in the target");
        _hasher.Verify(admin.PasswordHash, "PASSWORD").Should().BeTrue();
        _hasher.Verify(admin.PasswordHash, "WRONGPWD").Should().BeFalse();
    }
}
