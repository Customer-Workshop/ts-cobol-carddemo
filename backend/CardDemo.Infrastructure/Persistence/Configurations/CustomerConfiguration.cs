using CardDemo.Domain.Customers;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace CardDemo.Infrastructure.Persistence.Configurations;

/// <summary>CUSTDAT KSDS (app/cpy/CVCUS01Y.cpy): KEYS(9 0).</summary>
public class CustomerConfiguration : IEntityTypeConfiguration<Customer>
{
    public void Configure(EntityTypeBuilder<Customer> entity)
    {
        entity.ToTable("customers");
        entity.HasKey(c => c.CustomerId);
        entity.Property(c => c.CustomerId).HasColumnName("cust_id").LegacyKey(9);
        entity.Property(c => c.FirstName).HasColumnName("cust_first_name").HasMaxLength(25);
        entity.Property(c => c.MiddleName).HasColumnName("cust_middle_name").HasMaxLength(25);
        entity.Property(c => c.LastName).HasColumnName("cust_last_name").HasMaxLength(25);
        entity.Property(c => c.AddressLine1).HasColumnName("cust_addr_line_1").HasMaxLength(50);
        entity.Property(c => c.AddressLine2).HasColumnName("cust_addr_line_2").HasMaxLength(50);
        entity.Property(c => c.AddressLine3).HasColumnName("cust_addr_line_3").HasMaxLength(50);
        entity.Property(c => c.AddressStateCode).HasColumnName("cust_addr_state_cd").HasMaxLength(2);
        entity.Property(c => c.AddressCountryCode).HasColumnName("cust_addr_country_cd").HasMaxLength(3);
        entity.Property(c => c.AddressZip).HasColumnName("cust_addr_zip").HasMaxLength(10);
        entity.Property(c => c.PhoneNumber1).HasColumnName("cust_phone_num_1").HasMaxLength(15);
        entity.Property(c => c.PhoneNumber2).HasColumnName("cust_phone_num_2").HasMaxLength(15);
        entity.Property(c => c.Ssn).HasColumnName("cust_ssn").HasMaxLength(9);
        entity.Property(c => c.GovernmentIssuedId).HasColumnName("cust_govt_issued_id").HasMaxLength(20);
        entity.Property(c => c.DateOfBirth).HasColumnName("cust_dob");
        entity.Property(c => c.EftAccountId).HasColumnName("cust_eft_account_id").HasMaxLength(10);
        entity.Property(c => c.PrimaryCardHolderIndicator).HasColumnName("cust_pri_card_holder_ind").HasMaxLength(1);
        entity.Property(c => c.FicoCreditScore).HasColumnName("cust_fico_credit_score");
    }
}
