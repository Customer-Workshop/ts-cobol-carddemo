using CardDemo.Domain.Accounts;
using CardDemo.Domain.Cards;
using CardDemo.Domain.Customers;
using CardDemo.Domain.Transactions;
using CardDemo.Domain.Users;
using Microsoft.EntityFrameworkCore;

namespace CardDemo.Infrastructure.Persistence;

public class CardDemoDbContext(DbContextOptions<CardDemoDbContext> options) : DbContext(options)
{
    public DbSet<User> Users => Set<User>();
    public DbSet<Account> Accounts => Set<Account>();
    public DbSet<Card> Cards => Set<Card>();
    public DbSet<CardXref> CardXrefs => Set<CardXref>();
    public DbSet<Customer> Customers => Set<Customer>();
    public DbSet<Transaction> Transactions => Set<Transaction>();
    public DbSet<TransactionCategoryBalance> TransactionCategoryBalances => Set<TransactionCategoryBalance>();
    public DbSet<DisclosureGroup> DisclosureGroups => Set<DisclosureGroup>();
    public DbSet<TransactionType> TransactionTypes => Set<TransactionType>();
    public DbSet<TransactionCategory> TransactionCategories => Set<TransactionCategory>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<User>(entity =>
        {
            entity.ToTable("users");
            entity.HasKey(u => u.UserId);
            entity.Property(u => u.UserId).HasColumnName("user_id").HasMaxLength(8);
            entity.Property(u => u.FirstName).HasColumnName("first_name").HasMaxLength(20);
            entity.Property(u => u.LastName).HasColumnName("last_name").HasMaxLength(20);
            entity.Property(u => u.PasswordHash).HasColumnName("password_hash");
            entity.Property(u => u.UserType)
                .HasColumnName("user_type")
                .HasMaxLength(1)
                .HasConversion(
                    t => t.ToCode().ToString(),
                    s => UserTypeCodes.FromCode(s[0]));
        });

        modelBuilder.ApplyConfigurationsFromAssembly(typeof(CardDemoDbContext).Assembly);
    }
}
