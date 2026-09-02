using System.Text;
using CardDemo.Application.AccountUpdate;
using CardDemo.Application.Accounts;
using CardDemo.Application.Auth;
using CardDemo.Application.Cards;
using CardDemo.Application.Customers;
using CardDemo.Application.LegacyData;
using CardDemo.Application.Menu;
using CardDemo.Application.Sessions;
using CardDemo.Application.Transactions;
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
builder.Services.AddScoped<IAccountRepository, AccountRepository>();
builder.Services.AddScoped<ICustomerRepository, CustomerRepository>();
builder.Services.AddScoped<ICardRepository, CardRepository>();
builder.Services.AddScoped<ICardXrefRepository, CardXrefRepository>();
builder.Services.AddScoped<ITransactionRepository, TransactionRepository>();
builder.Services.AddScoped<ILegacyDataWriter, LegacyDataWriter>();
builder.Services.AddScoped<LegacyDataImportService>();
builder.Services.AddScoped<SignInService>();
builder.Services.AddScoped<TransactionViewService>();
builder.Services.AddSingleton<IJwtTokenIssuer, JwtTokenIssuer>();
builder.Services.Configure<JwtOptions>(builder.Configuration.GetSection(JwtOptions.SectionName));
builder.Services.Configure<MenuRouteRegistryOptions>(builder.Configuration.GetSection(MenuRouteRegistryOptions.SectionName));
builder.Services.AddScoped(sp => sp.GetRequiredService<IOptions<MenuRouteRegistryOptions>>().Value);
builder.Services.AddScoped<MenuService>();
builder.Services.AddScoped<AccountViewService>();
builder.Services.AddSingleton(TimeProvider.System);
builder.Services.AddScoped<IAccountUpdateWriter, AccountUpdateWriter>();
builder.Services.AddScoped<AccountUpdateService>();
builder.Services.AddScoped<CardListService>();
builder.Services.AddScoped<CardViewService>();
builder.Services.AddScoped<CardUpdateService>();
builder.Services.AddScoped<TransactionListService>();

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

    var legacySeedPaths = app.Configuration.GetSection(LegacyDataSeedPaths.SectionName).Get<LegacyDataSeedPaths>();
    if (legacySeedPaths is not null)
    {
        using var scope = app.Services.CreateScope();
        var dbContext = scope.ServiceProvider.GetRequiredService<CardDemoDbContext>();
        await dbContext.Database.MigrateAsync();
        var importer = scope.ServiceProvider.GetRequiredService<LegacyDataImportService>();
        var result = await importer.ImportAsync(legacySeedPaths);
        app.Logger.LogInformation(
            "Legacy data seed import: {Total} records upserted ({Accounts} accounts, {Cards} cards, {CardXrefs} card xrefs, {Customers} customers, {Transactions} transactions, {Balances} category balances, {DisclosureGroups} disclosure groups, {TransactionTypes} transaction types, {TransactionCategories} transaction categories)",
            result.Total,
            result.Accounts,
            result.Cards,
            result.CardXrefs,
            result.Customers,
            result.Transactions,
            result.TransactionCategoryBalances,
            result.DisclosureGroups,
            result.TransactionTypes,
            result.TransactionCategories);
    }
}

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();
app.MapGet("/api/v1/health", () => Results.Ok(new { status = "ok" }));

app.Run();

public partial class Program;
