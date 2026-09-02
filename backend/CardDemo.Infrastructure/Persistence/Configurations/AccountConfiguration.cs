using CardDemo.Domain.Accounts;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace CardDemo.Infrastructure.Persistence.Configurations;

/// <summary>ACCTDAT KSDS (app/cpy/CVACT01Y.cpy): KEYS(11 0).</summary>
public class AccountConfiguration : IEntityTypeConfiguration<Account>
{
    public void Configure(EntityTypeBuilder<Account> entity)
    {
        entity.ToTable("accounts");
        entity.HasKey(a => a.AccountId);
        entity.Property(a => a.AccountId).HasColumnName("acct_id").LegacyKey(11);
        entity.Property(a => a.ActiveStatus).HasColumnName("acct_active_status").HasMaxLength(1);
        entity.Property(a => a.CurrentBalance).HasColumnName("acct_curr_bal").LegacyAmount(10);
        entity.Property(a => a.CreditLimit).HasColumnName("acct_credit_limit").LegacyAmount(10);
        entity.Property(a => a.CashCreditLimit).HasColumnName("acct_cash_credit_limit").LegacyAmount(10);
        entity.Property(a => a.OpenDate).HasColumnName("acct_open_date");
        entity.Property(a => a.ExpirationDate).HasColumnName("acct_expiration_date");
        entity.Property(a => a.ReissueDate).HasColumnName("acct_reissue_date");
        entity.Property(a => a.CurrentCycleCredit).HasColumnName("acct_curr_cyc_credit").LegacyAmount(10);
        entity.Property(a => a.CurrentCycleDebit).HasColumnName("acct_curr_cyc_debit").LegacyAmount(10);
        entity.Property(a => a.AddressZip).HasColumnName("acct_addr_zip").HasMaxLength(10);
        entity.Property(a => a.GroupId).HasColumnName("acct_group_id").HasMaxLength(10);
    }
}
