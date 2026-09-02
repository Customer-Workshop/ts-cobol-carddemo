using CardDemo.Domain.Cards;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace CardDemo.Infrastructure.Persistence.Configurations;

/// <summary>CCXREF KSDS (app/cpy/CVACT03Y.cpy): KEYS(16 0); AIX CXACAIX KEYS(11 25) NONUNIQUEKEY.</summary>
public class CardXrefConfiguration : IEntityTypeConfiguration<CardXref>
{
    public void Configure(EntityTypeBuilder<CardXref> entity)
    {
        entity.ToTable("card_xref");
        entity.HasKey(x => x.CardNumber);
        entity.Property(x => x.CardNumber).HasColumnName("xref_card_num").LegacyKey(16);
        entity.Property(x => x.CustomerId).HasColumnName("xref_cust_id").LegacyKey(9);
        entity.Property(x => x.AccountId).HasColumnName("xref_acct_id").LegacyKey(11);
        entity.HasIndex(x => x.AccountId).HasDatabaseName("ix_card_xref_xref_acct_id");
    }
}
