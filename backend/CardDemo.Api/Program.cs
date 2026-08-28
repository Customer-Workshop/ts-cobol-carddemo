using System.Text;
using CardDemo.Application.Auth;
using CardDemo.Application.Menu;
using CardDemo.Application.Sessions;
using CardDemo.Application.Users;
using CardDemo.Infrastructure.Persistence;
using CardDemo.Infrastructure.Security;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;

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
builder.Services.AddScoped(sp => sp.GetRequiredService<IOptions<MenuRouteRegistryOptions>>().Value);
builder.Services.AddScoped<MenuService>();

var jwtOptions = builder.Configuration.GetSection(JwtOptions.SectionName).Get<JwtOptions>() ?? new JwtOptions();
if (string.IsNullOrWhiteSpace(jwtOptions.SigningKey) || Encoding.UTF8.GetByteCount(jwtOptions.SigningKey) < 32)
{
    throw new InvalidOperationException(
        "Jwt:SigningKey must be configured with at least 32 bytes (e.g. via the Jwt__SigningKey environment variable).");
}
builder.Services
    .AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.MapInboundClaims = false;
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidIssuer = jwtOptions.Issuer,
            ValidateAudience = true,
            ValidAudience = jwtOptions.Audience,
            ValidateIssuerSigningKey = true,
            IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwtOptions.SigningKey)),
            ValidateLifetime = true
        };
    });
builder.Services.AddAuthorization();

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

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();
app.MapGet("/api/v1/health", () => Results.Ok(new { status = "ok" }));

app.Run();

public partial class Program;
