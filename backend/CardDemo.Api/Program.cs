using CardDemo.Application.Auth;
using CardDemo.Application.Menu;
using CardDemo.Application.Sessions;
using CardDemo.Application.Users;
using CardDemo.Infrastructure.Persistence;
using CardDemo.Infrastructure.Security;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

builder.Services.AddDbContext<CardDemoDbContext>(options =>
    options.UseNpgsql(builder.Configuration.GetConnectionString("CardDemo")));
builder.Services.AddScoped<IUserRepository, UserRepository>();
builder.Services.AddSingleton<IPasswordHashingService, IdentityPasswordHashingService>();
builder.Services.AddScoped<UsrsecImportService>();
builder.Services.AddScoped<SignInService>();
builder.Services.AddSingleton<IJwtTokenIssuer, JwtTokenIssuer>();
builder.Services.Configure<JwtOptions>(builder.Configuration.GetSection(JwtOptions.SectionName));
builder.Services.Configure<MenuRouteRegistryOptions>(builder.Configuration.GetSection(MenuRouteRegistryOptions.SectionName));

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();

    var usrsecSeedPath = app.Configuration["Seed:UsrsecPath"];
    if (!string.IsNullOrWhiteSpace(usrsecSeedPath) && File.Exists(usrsecSeedPath))
    {
        using var scope = app.Services.CreateScope();
        var dbContext = scope.ServiceProvider.GetRequiredService<CardDemoDbContext>();
        await dbContext.Database.MigrateAsync();
        var importer = scope.ServiceProvider.GetRequiredService<UsrsecImportService>();
        var count = await importer.ImportAsync(UsrsecSeedSource.ReadRecords(usrsecSeedPath));
        app.Logger.LogInformation("USRSEC seed import: {Count} users upserted from {Path}", count, usrsecSeedPath);
    }
}

app.MapControllers();
app.MapGet("/api/v1/health", () => Results.Ok(new { status = "ok" }));

app.Run();

public partial class Program;
