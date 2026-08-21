using CardDemo.Application.Auth;
using CardDemo.Application.Users;
using CardDemo.Domain.Users;
using CardDemo.Infrastructure.Security;
using FluentAssertions;

namespace CardDemo.Tests.Auth;

public class SignInServiceTests
{
    private static readonly IPasswordHashingService Hasher = new IdentityPasswordHashingService();

    private sealed class InMemoryUserRepository : IUserRepository
    {
        private readonly Dictionary<string, User> _users = new();
        public bool ThrowOnRead { get; set; }

        public Task<User?> GetByIdAsync(string userId, CancellationToken cancellationToken = default)
        {
            if (ThrowOnRead)
            {
                throw new InvalidOperationException("store unavailable");
            }
            return Task.FromResult(_users.TryGetValue(userId, out var user) ? user : null);
        }

        public Task UpsertAsync(User user, CancellationToken cancellationToken = default)
        {
            _users[user.UserId] = user;
            return Task.CompletedTask;
        }

        public Task<int> CountAsync(CancellationToken cancellationToken = default) =>
            Task.FromResult(_users.Count);
    }

    private static async Task<(SignInService Service, InMemoryUserRepository Repository)> BuildAsync()
    {
        var repository = new InMemoryUserRepository();
        await repository.UpsertAsync(new User
        {
            UserId = "ADMIN001",
            FirstName = "MARGARET",
            LastName = "GOLD",
            PasswordHash = Hasher.Hash("PASSWORD"),
            UserType = UserType.Admin
        });
        await repository.UpsertAsync(new User
        {
            UserId = "USER0001",
            FirstName = "LAWRENCE",
            LastName = "THOMAS",
            PasswordHash = Hasher.Hash("PASSWORD"),
            UserType = UserType.User
        });
        return (new SignInService(repository, Hasher), repository);
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("        ")]
    public async Task BlankUserId_ReturnsMissingUserIdMessage_FrS0101(string? userId)
    {
        var (service, _) = await BuildAsync();

        var result = await service.SignInAsync(new SignInRequest(userId, "PASSWORD"));

        result.Outcome.Should().Be(SignInOutcome.MissingUserId);
        result.Message.Should().Be("Please enter User ID ...");
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("        ")]
    public async Task BlankPassword_ReturnsMissingPasswordMessage_FrS0102(string? password)
    {
        var (service, _) = await BuildAsync();

        var result = await service.SignInAsync(new SignInRequest("ADMIN001", password));

        result.Outcome.Should().Be(SignInOutcome.MissingPassword);
        result.Message.Should().Be("Please enter Password ...");
    }

    [Fact]
    public async Task UnknownUser_ReturnsUserNotFoundMessage_FrS0103()
    {
        var (service, _) = await BuildAsync();

        var result = await service.SignInAsync(new SignInRequest("NOSUCHID", "PASSWORD"));

        result.Outcome.Should().Be(SignInOutcome.UserNotFound);
        result.Message.Should().Be("User not found. Try again ...");
    }

    [Fact]
    public async Task WrongPassword_ReturnsWrongPasswordMessage_FrS0104()
    {
        var (service, _) = await BuildAsync();

        var result = await service.SignInAsync(new SignInRequest("ADMIN001", "WRONGPWD"));

        result.Outcome.Should().Be(SignInOutcome.WrongPassword);
        result.Message.Should().Be("Wrong Password. Try again ...");
    }

    [Fact]
    public async Task AdminCredentials_RouteToAdminMenuWithSession_FrS0105()
    {
        var (service, _) = await BuildAsync();

        var result = await service.SignInAsync(new SignInRequest("ADMIN001", "PASSWORD"));

        result.IsSuccess.Should().BeTrue();
        result.LandingRoute.Should().Be("/admin");
        result.Session.Should().NotBeNull();
        result.Session!.UserId.Should().Be("ADMIN001");
        result.Session.UserType.Should().Be('A');
        result.Session.IsAdmin.Should().BeTrue();
        result.Session.FromProgram.Should().Be("COSGN00C");
    }

    [Fact]
    public async Task RegularCredentials_RouteToMainMenuWithSession_FrS0106()
    {
        var (service, _) = await BuildAsync();

        var result = await service.SignInAsync(new SignInRequest("USER0001", "PASSWORD"));

        result.IsSuccess.Should().BeTrue();
        result.LandingRoute.Should().Be("/menu");
        result.Session!.UserId.Should().Be("USER0001");
        result.Session.UserType.Should().Be('U');
        result.Session.IsAdmin.Should().BeFalse();
    }

    [Fact]
    public async Task StoreError_ReturnsUnableToVerifyMessage_FrS0107()
    {
        var (service, repository) = await BuildAsync();
        repository.ThrowOnRead = true;

        var result = await service.SignInAsync(new SignInRequest("ADMIN001", "PASSWORD"));

        result.Outcome.Should().Be(SignInOutcome.StoreError);
        result.Message.Should().Be("Unable to verify the User ...");
    }

    [Fact]
    public async Task LowerCaseCredentials_AreUpperCasedBeforeAuth_FrS0109()
    {
        var (service, _) = await BuildAsync();

        var result = await service.SignInAsync(new SignInRequest("admin001", "password"));

        result.IsSuccess.Should().BeTrue();
        result.Session!.UserId.Should().Be("ADMIN001");
        result.LandingRoute.Should().Be("/admin");
    }
}
