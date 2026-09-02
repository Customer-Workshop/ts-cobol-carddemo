using CardDemo.Domain.Transactions;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace CardDemo.Infrastructure.Persistence.Configurations;

/// <summary>TRANCATG KSDS (app/cpy/CVTRA04Y.cpy): KEYS(6 0) = tran type cd + tran cat cd.</summary>
public class TransactionCategoryConfiguration : IEntityTypeConfiguration<TransactionCategory>
{
    public void Configure(EntityTypeBuilder<TransactionCategory> entity)
    {
        entity.ToTable("transaction_categories");
        entity.HasKey(c => new { c.TypeCode, c.CategoryCode });
        entity.Property(c => c.TypeCode).HasColumnName("tran_type_cd").LegacyKey(2);
        entity.Property(c => c.CategoryCode).HasColumnName("tran_cat_cd").LegacyKey(4);
        entity.Property(c => c.Description).HasColumnName("tran_cat_type_desc").HasMaxLength(50);
    }
}
