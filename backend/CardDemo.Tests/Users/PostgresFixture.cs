using CardDemo.Infrastructure.Persistence;
using Microsoft.EntityFrameworkCore;
using Testcontainers.PostgreSql;

namespace CardDemo.Tests.Users;

public sealed class PostgresFixture : IAsyncLifetime
{
    private readonly PostgreSqlContainer _container = new PostgreSqlBuilder().WithImage("postgres:16").Build();

    public string ConnectionString => _container.GetConnectionString();

    public CardDemoDbContext CreateContext()
    {
        var options = new DbContextOptionsBuilder<CardDemoDbContext>()
            .UseNpgsql(ConnectionString)
            .Options;
        return new CardDemoDbContext(options);
    }

    public async Task InitializeAsync()
    {
        await _container.StartAsync();
        await using var context = CreateContext();
        await context.Database.MigrateAsync();
    }

    public Task DisposeAsync() => _container.DisposeAsync().AsTask();
}
