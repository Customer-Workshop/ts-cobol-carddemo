using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using CardDemo.Application.Menu;
using CardDemo.Application.Sessions;
using CardDemo.Application.Users;
using CardDemo.Infrastructure.Security;
using FluentAssertions;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Microsoft.Extensions.Options;

namespace CardDemo.Tests.UserAdmin;

/// <summary>
/// /api/v1/admin/users authorization over the JWT userType claim (S12-B5): admins reach the
/// COUSR screens' endpoints, regular users get 403 with the admin-only message, anonymous callers 401.
/// The repository is swapped for the in-memory double so the API surface is exercised without Postgres.
/// </summary>
public class UserAdminApiIntegrationTests : IClassFixture<WebApplicationFactory<Program>>
{
    private const string SigningKey = "carddemo-test-signing-key-not-for-production-0012";

    private readonly WebApplicationFactory<Program> _factory;
    private readonly InMemoryUserAdminRepository _repository = new();

    public UserAdminApiIntegrationTests(WebApplicationFactory<Program> factory)
    {
        _factory = factory.WithWebHostBuilder(builder =>
        {
            builder.UseEnvironment("Testing");
            builder.UseSetting("Jwt:SigningKey", SigningKey);
            builder.ConfigureServices(services =>
            {
                services.RemoveAll<IUserRepository>();
                services.AddSingleton<IUserRepository>(_repository);
            });
        });
    }

    private HttpClient CreateClient(char? userType)
    {
        var client = _factory.CreateClient();
        if (userType is not null)
        {
            var issuer = new JwtTokenIssuer(Options.Create(new JwtOptions { SigningKey = SigningKey }));
            var token = issuer.Issue(new SessionContext(userType == 'A' ? "ADMIN001" : "USER0001", userType.Value));
            client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);
        }
        return client;
    }

    private sealed record ErrorPayload(string Message);

    private sealed record DetailsPayload(string UserId, string FirstName, string LastName, string UserType);

    private sealed record AdminPayload(string Outcome, string? Message, string? Severity, string? FocusField, DetailsPayload? User);

    private sealed record RowPayload(string UserId, string FirstName, string LastName, string UserType);

    private sealed record NavigatePayload(string ProgramKey, string UserId);

    private sealed record ListPayload(
        List<RowPayload> Rows, int PageNum, bool NextPage, string? FirstUserId, string? LastUserId,
        string? SearchUserId, string? Message, string? Severity, NavigatePayload? Navigate);

    public static TheoryData<string, object> Endpoints => new()
    {
        { "/api/v1/admin/users/list", new { action = "enter", pageNum = 0, nextPage = false } },
        { "/api/v1/admin/users/add", new { firstName = "F", lastName = "L", userId = "NEWUSER1", password = "pw", userType = "U" } },
        { "/api/v1/admin/users/update/fetch", new { userId = "NEWUSER1" } },
        { "/api/v1/admin/users/update", new { userId = "NEWUSER1", firstName = "F", lastName = "L", password = "pw", userType = "U" } },
        { "/api/v1/admin/users/delete/fetch", new { userId = "NEWUSER1" } },
        { "/api/v1/admin/users/delete", new { userId = "NEWUSER1" } }
    };

    [Theory]
    [MemberData(nameof(Endpoints))]
    public async Task Anonymous_IsUnauthorized(string url, object body)
    {
        var response = await CreateClient(null).PostAsJsonAsync(url, body);

        response.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
    }

    [Theory]
    [MemberData(nameof(Endpoints))]
    public async Task RegularUser_IsForbiddenWithAdminOnlyMessage(string url, object body)
    {
        var response = await CreateClient('U').PostAsJsonAsync(url, body);

        response.StatusCode.Should().Be(HttpStatusCode.Forbidden);
        var payload = await response.Content.ReadFromJsonAsync<ErrorPayload>();
        payload!.Message.Should().Be(MenuService.AdminOnlyMessage);
        _repository.Users.Should().BeEmpty();
    }

    [Fact]
    public async Task Admin_AddFetchUpdateListDelete_RoundTrip()
    {
        var client = CreateClient('A');

        var add = await client.PostAsJsonAsync("/api/v1/admin/users/add",
            new { firstName = "Jane", lastName = "Doe", userId = "JDOE0001", password = "pw", userType = "A" });
        var duplicate = await client.PostAsJsonAsync("/api/v1/admin/users/add",
            new { firstName = "Jane", lastName = "Doe", userId = "JDOE0001", password = "pw", userType = "A" });
        var fetch = await client.PostAsJsonAsync("/api/v1/admin/users/update/fetch", new { userId = "JDOE0001" });
        var update = await client.PostAsJsonAsync("/api/v1/admin/users/update",
            new { userId = "JDOE0001", firstName = "Janet", lastName = "Doe", password = "pw", userType = "A" });
        var list = await client.PostAsJsonAsync("/api/v1/admin/users/list",
            new { action = "enter", searchUserId = "", pageNum = 0, nextPage = false });
        var select = await client.PostAsJsonAsync("/api/v1/admin/users/list",
            new { action = "enter", selections = new[] { new { userId = "JDOE0001", flag = "d" } }, pageNum = 1, nextPage = false });
        var deleteFetch = await client.PostAsJsonAsync("/api/v1/admin/users/delete/fetch", new { userId = "JDOE0001" });
        var delete = await client.PostAsJsonAsync("/api/v1/admin/users/delete", new { userId = "JDOE0001" });

        add.StatusCode.Should().Be(HttpStatusCode.OK);
        var addPayload = await add.Content.ReadFromJsonAsync<AdminPayload>();
        addPayload.Should().Be(new AdminPayload("success", "User JDOE0001 has been added ...", "success", "firstName", null));

        var duplicatePayload = await duplicate.Content.ReadFromJsonAsync<AdminPayload>();
        duplicatePayload!.Outcome.Should().Be("duplicate");
        duplicatePayload.Message.Should().Be("User ID already exist...");
        duplicatePayload.Severity.Should().Be("error");

        var fetchPayload = await fetch.Content.ReadFromJsonAsync<AdminPayload>();
        fetchPayload!.Message.Should().Be("Press PF5 key to save your updates ...");
        fetchPayload.Severity.Should().Be("neutral");
        fetchPayload.User.Should().Be(new DetailsPayload("JDOE0001", "Jane", "Doe", "A"));

        (await update.Content.ReadFromJsonAsync<AdminPayload>())!.Message.Should().Be("User JDOE0001 has been updated ...");

        var listPayload = await list.Content.ReadFromJsonAsync<ListPayload>();
        listPayload!.Rows.Should().Equal(new RowPayload("JDOE0001", "Janet", "Doe", "A"));
        listPayload.PageNum.Should().Be(1);
        listPayload.NextPage.Should().BeFalse();
        listPayload.Message.Should().Be("You have reached the bottom of the page...");
        listPayload.Severity.Should().Be("error");

        var selectPayload = await select.Content.ReadFromJsonAsync<ListPayload>();
        selectPayload!.Navigate.Should().Be(new NavigatePayload("COUSR03C", "JDOE0001"));

        (await deleteFetch.Content.ReadFromJsonAsync<AdminPayload>())!.Message.Should().Be("Press PF5 key to delete this user ...");
        (await delete.Content.ReadFromJsonAsync<AdminPayload>())!.Message.Should().Be("User JDOE0001 has been deleted ...");
        _repository.Users.Should().BeEmpty();
    }

    [Fact]
    public async Task Admin_ValidationErrorIsReturnedAsScreenStateNot400()
    {
        var client = CreateClient('A');

        var response = await client.PostAsJsonAsync("/api/v1/admin/users/add",
            new { firstName = "", lastName = "", userId = "", password = "", userType = "" });

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var payload = await response.Content.ReadFromJsonAsync<AdminPayload>();
        payload!.Outcome.Should().Be("validationError");
        payload.Message.Should().Be("First Name can NOT be empty...");
        payload.FocusField.Should().Be("firstName");
    }

    [Theory]
    [InlineData("pf7", "You are already at the top of the page...")]
    [InlineData("pageBackward", "You are already at the top of the page...")]
    [InlineData("pf8", "You are already at the bottom of the page...")]
    [InlineData("pageForward", "You are already at the bottom of the page...")]
    public async Task Admin_ListActionAliasesMapToPfKeys(string action, string message)
    {
        var client = CreateClient('A');

        var response = await client.PostAsJsonAsync("/api/v1/admin/users/list",
            new { action, pageNum = 1, nextPage = false });

        var payload = await response.Content.ReadFromJsonAsync<ListPayload>();
        payload!.Message.Should().Be(message);
    }
}
