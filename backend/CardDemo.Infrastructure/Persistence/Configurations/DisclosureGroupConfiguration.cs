using CardDemo.Domain.Transactions;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace CardDemo.Infrastructure.Persistence.Configurations;

/// <summary>DISCGRP KSDS (app/cpy/CVTRA02Y.cpy): KEYS(16 0) = acct group id + tran type cd + tran cat cd.</summary>
public class DisclosureGroupConfiguration : IEntityTypeConfiguration<DisclosureGroup>
{
    public void Configure(EntityTypeBuilder<DisclosureGroup> entity)
    {
        entity.ToTable("disclosure_groups");
        entity.HasKey(d => new { d.AccountGroupId, d.TransactionTypeCode, d.TransactionCategoryCode });
        entity.Property(d => d.AccountGroupId).HasColumnName("dis_acct_group_id").LegacyKey(10);
        entity.Property(d => d.TransactionTypeCode).HasColumnName("dis_tran_type_cd").LegacyKey(2);
        entity.Property(d => d.TransactionCategoryCode).HasColumnName("dis_tran_cat_cd").LegacyKey(4);
        entity.Property(d => d.InterestRate).HasColumnName("dis_int_rate").LegacyAmount(4);
    }
}
