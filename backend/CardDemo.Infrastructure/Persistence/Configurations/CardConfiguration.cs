using CardDemo.Domain.Cards;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace CardDemo.Infrastructure.Persistence.Configurations;

/// <summary>CARDDAT KSDS (app/cpy/CVACT02Y.cpy): KEYS(16 0); AIX CARDAIX KEYS(11 16) NONUNIQUEKEY.</summary>
public class CardConfiguration : IEntityTypeConfiguration<Card>
{
    public void Configure(EntityTypeBuilder<Card> entity)
    {
        entity.ToTable("cards");
        entity.HasKey(c => c.CardNumber);
        entity.Property(c => c.CardNumber).HasColumnName("card_num").LegacyKey(16);
        entity.Property(c => c.AccountId).HasColumnName("card_acct_id").LegacyKey(11);
        entity.Property(c => c.CvvCode).HasColumnName("card_cvv_cd").HasMaxLength(3);
        entity.Property(c => c.EmbossedName).HasColumnName("card_embossed_name").HasMaxLength(50);
        entity.Property(c => c.ExpirationDate).HasColumnName("card_expiration_date");
        entity.Property(c => c.ActiveStatus).HasColumnName("card_active_status").HasMaxLength(1);
        entity.HasIndex(c => c.AccountId).HasDatabaseName("ix_cards_card_acct_id");
    }
}
