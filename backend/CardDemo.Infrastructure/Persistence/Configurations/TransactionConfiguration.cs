using CardDemo.Domain.Transactions;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace CardDemo.Infrastructure.Persistence.Configurations;

/// <summary>TRANSACT KSDS (app/cpy/CVTRA05Y.cpy): KEYS(16 0); AIX KEYS(26 304) = TRAN-PROC-TS NONUNIQUEKEY.</summary>
public class TransactionConfiguration : IEntityTypeConfiguration<Transaction>
{
    public void Configure(EntityTypeBuilder<Transaction> entity)
    {
        entity.ToTable("transactions");
        entity.HasKey(t => t.TransactionId);
        entity.Property(t => t.TransactionId).HasColumnName("tran_id").LegacyKey(16);
        entity.Property(t => t.TypeCode).HasColumnName("tran_type_cd").LegacyKey(2);
        entity.Property(t => t.CategoryCode).HasColumnName("tran_cat_cd").LegacyKey(4);
        entity.Property(t => t.Source).HasColumnName("tran_source").HasMaxLength(10);
        entity.Property(t => t.Description).HasColumnName("tran_desc").HasMaxLength(100);
        entity.Property(t => t.Amount).HasColumnName("tran_amt").LegacyAmount(9);
        entity.Property(t => t.MerchantId).HasColumnName("tran_merchant_id").HasMaxLength(9);
        entity.Property(t => t.MerchantName).HasColumnName("tran_merchant_name").HasMaxLength(50);
        entity.Property(t => t.MerchantCity).HasColumnName("tran_merchant_city").HasMaxLength(50);
        entity.Property(t => t.MerchantZip).HasColumnName("tran_merchant_zip").HasMaxLength(10);
        entity.Property(t => t.CardNumber).HasColumnName("tran_card_num").LegacyKey(16);
        entity.Property(t => t.OriginalTimestamp).HasColumnName("tran_orig_ts").LegacyTimestamp();
        entity.Property(t => t.ProcessedTimestamp).HasColumnName("tran_proc_ts").LegacyTimestamp();
        entity.HasIndex(t => t.ProcessedTimestamp).HasDatabaseName("ix_transactions_tran_proc_ts");
    }
}
