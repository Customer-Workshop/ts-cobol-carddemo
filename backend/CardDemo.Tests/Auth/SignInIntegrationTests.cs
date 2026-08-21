using CardDemo.Application.Auth;
using CardDemo.Application.Users;
using CardDemo.Infrastructure.Persistence;
using CardDemo.Infrastructure.Security;
using CardDemo.Tests.Users;
using FluentAssertions;

namespace CardDemo.Tests.Auth;

/// <summary>
/// Signs in against a real Postgres seeded by the USRSEC importer (FR-S01-03..06, 09).
/// </summary>
public class SignInIntegrationTests(PostgresFixture fixture) : IClassFixture<PostgresFixture>
{
    private readonly IPasswordHashingService _hasher = new IdentityPasswordHashingService();

    private async Task<SignInService> BuildServiceAsync()
    {
        await using (var seedContext = fixture.CreateContext())
        {
            var importer = new UsrsecImportService(new UserRepository(seedContext), _hasher);
            await importer.ImportAsync(UsrsecSeedSource.ReadRecords(TestPaths.UsrsecSeedJcl));
        }
        var context = fixture.CreateContext();
        return new SignInService(new UserRepository(context), _hasher);
    }

    [Fact]
    public async Task AdminSeedCredential_SignsInAndRoutesToAdminMenu()
    {
        var service = await BuildServiceAsync();

        var result = await service.SignInAsync(new SignInRequest("admin001", "password"));

        result.IsSuccess.Should().BeTrue();
        result.LandingRoute.Should().Be("/admin");
        result.Session!.UserId.Should().Be("ADMIN001");
        result.Session.UserType.Should().Be('A');
    }

    [Fact]
    public async Task RegularSeedCredential_SignsInAndRoutesToMainMenu()
    {
        var service = await BuildServiceAsync();

        var result = await service.SignInAsync(new SignInRequest("USER0001", "PASSWORD"));

        result.IsSuccess.Should().BeTrue();
        result.LandingRoute.Should().Be("/menu");
        result.Session!.UserType.Should().Be('U');
    }

    [Fact]
    public async Task WrongPasswordAgainstSeedCredential_IsRejected()
    {
        var service = await BuildServiceAsync();

        var result = await service.SignInAsync(new SignInRequest("USER0001", "WRONGPWD"));

        result.Outcome.Should().Be(SignInOutcome.WrongPassword);
        result.Message.Should().Be("Wrong Password. Try again ...");
    }

    [Fact]
    public async Task UnknownUserAgainstSeededStore_IsNotFound()
    {
        var service = await BuildServiceAsync();

        var result = await service.SignInAsync(new SignInRequest("NOSUCHID", "PASSWORD"));

        result.Outcome.Should().Be(SignInOutcome.UserNotFound);
        result.Message.Should().Be("User not found. Try again ...");
    }
}
