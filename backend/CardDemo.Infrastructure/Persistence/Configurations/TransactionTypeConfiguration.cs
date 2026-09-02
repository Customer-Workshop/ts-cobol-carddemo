using CardDemo.Domain.Transactions;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace CardDemo.Infrastructure.Persistence.Configurations;

/// <summary>TRANTYPE KSDS (app/cpy/CVTRA03Y.cpy): KEYS(2 0).</summary>
public class TransactionTypeConfiguration : IEntityTypeConfiguration<TransactionType>
{
    public void Configure(EntityTypeBuilder<TransactionType> entity)
    {
        entity.ToTable("transaction_types");
        entity.HasKey(t => t.TypeCode);
        entity.Property(t => t.TypeCode).HasColumnName("tran_type").LegacyKey(2);
        entity.Property(t => t.Description).HasColumnName("tran_type_desc").HasMaxLength(50);
    }
}
