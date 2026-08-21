using CardDemo.Domain.Users;
using Microsoft.EntityFrameworkCore;

namespace CardDemo.Infrastructure.Persistence;

public class CardDemoDbContext(DbContextOptions<CardDemoDbContext> options) : DbContext(options)
{
    public DbSet<User> Users => Set<User>();

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
    }
}
