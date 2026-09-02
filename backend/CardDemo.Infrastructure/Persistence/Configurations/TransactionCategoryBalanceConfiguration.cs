using CardDemo.Domain.Transactions;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace CardDemo.Infrastructure.Persistence.Configurations;

/// <summary>TCATBALF KSDS (app/cpy/CVTRA01Y.cpy): KEYS(17 0) = acct id + type cd + cat cd.</summary>
public class TransactionCategoryBalanceConfiguration : IEntityTypeConfiguration<TransactionCategoryBalance>
{
    public void Configure(EntityTypeBuilder<TransactionCategoryBalance> entity)
    {
        entity.ToTable("transaction_category_balances");
        entity.HasKey(b => new { b.AccountId, b.TypeCode, b.CategoryCode });
        entity.Property(b => b.AccountId).HasColumnName("trancat_acct_id").LegacyKey(11);
        entity.Property(b => b.TypeCode).HasColumnName("trancat_type_cd").LegacyKey(2);
        entity.Property(b => b.CategoryCode).HasColumnName("trancat_cd").LegacyKey(4);
        entity.Property(b => b.Balance).HasColumnName("tran_cat_bal").LegacyAmount(9);
    }
}
