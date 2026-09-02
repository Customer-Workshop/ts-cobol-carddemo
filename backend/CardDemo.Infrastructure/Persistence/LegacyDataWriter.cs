using CardDemo.Application.LegacyData;
using CardDemo.Domain.Accounts;
using CardDemo.Domain.Cards;
using CardDemo.Domain.Customers;
using CardDemo.Domain.Transactions;
using Microsoft.EntityFrameworkCore;

namespace CardDemo.Infrastructure.Persistence;

/// <summary>
/// EF Core keyed upserts for the VSAM-derived tables. Each batch is loaded by primary key,
/// existing rows have their non-key columns overwritten, new rows are added, one SaveChanges per dataset.
/// </summary>
public class LegacyDataWriter(CardDemoDbContext dbContext) : ILegacyDataWriter
{
    public Task UpsertAccountsAsync(IReadOnlyCollection<Account> records, CancellationToken cancellationToken = default) =>
        UpsertAsync(dbContext.Accounts, records, a => a.AccountId, (e, s) =>
        {
            e.ActiveStatus = s.ActiveStatus;
            e.CurrentBalance = s.CurrentBalance;
            e.CreditLimit = s.CreditLimit;
            e.CashCreditLimit = s.CashCreditLimit;
            e.OpenDate = s.OpenDate;
            e.ExpirationDate = s.ExpirationDate;
            e.ReissueDate = s.ReissueDate;
            e.CurrentCycleCredit = s.CurrentCycleCredit;
            e.CurrentCycleDebit = s.CurrentCycleDebit;
            e.AddressZip = s.AddressZip;
            e.GroupId = s.GroupId;
        }, cancellationToken);

    public Task UpsertCardsAsync(IReadOnlyCollection<Card> records, CancellationToken cancellationToken = default) =>
        UpsertAsync(dbContext.Cards, records, c => c.CardNumber, (e, s) =>
        {
            e.AccountId = s.AccountId;
            e.CvvCode = s.CvvCode;
            e.EmbossedName = s.EmbossedName;
            e.ExpirationDate = s.ExpirationDate;
            e.ActiveStatus = s.ActiveStatus;
        }, cancellationToken);

    public Task UpsertCardXrefsAsync(IReadOnlyCollection<CardXref> records, CancellationToken cancellationToken = default) =>
        UpsertAsync(dbContext.CardXrefs, records, x => x.CardNumber, (e, s) =>
        {
            e.CustomerId = s.CustomerId;
            e.AccountId = s.AccountId;
        }, cancellationToken);

    public Task UpsertCustomersAsync(IReadOnlyCollection<Customer> records, CancellationToken cancellationToken = default) =>
        UpsertAsync(dbContext.Customers, records, c => c.CustomerId, (e, s) =>
        {
            e.FirstName = s.FirstName;
            e.MiddleName = s.MiddleName;
            e.LastName = s.LastName;
            e.AddressLine1 = s.AddressLine1;
            e.AddressLine2 = s.AddressLine2;
            e.AddressLine3 = s.AddressLine3;
            e.AddressStateCode = s.AddressStateCode;
            e.AddressCountryCode = s.AddressCountryCode;
            e.AddressZip = s.AddressZip;
            e.PhoneNumber1 = s.PhoneNumber1;
            e.PhoneNumber2 = s.PhoneNumber2;
            e.Ssn = s.Ssn;
            e.GovernmentIssuedId = s.GovernmentIssuedId;
            e.DateOfBirth = s.DateOfBirth;
            e.EftAccountId = s.EftAccountId;
            e.PrimaryCardHolderIndicator = s.PrimaryCardHolderIndicator;
            e.FicoCreditScore = s.FicoCreditScore;
        }, cancellationToken);

    public Task UpsertTransactionsAsync(IReadOnlyCollection<Transaction> records, CancellationToken cancellationToken = default) =>
        UpsertAsync(dbContext.Transactions, records, t => t.TransactionId, (e, s) =>
        {
            e.TypeCode = s.TypeCode;
            e.CategoryCode = s.CategoryCode;
            e.Source = s.Source;
            e.Description = s.Description;
            e.Amount = s.Amount;
            e.MerchantId = s.MerchantId;
            e.MerchantName = s.MerchantName;
            e.MerchantCity = s.MerchantCity;
            e.MerchantZip = s.MerchantZip;
            e.CardNumber = s.CardNumber;
            e.OriginalTimestamp = s.OriginalTimestamp;
            e.ProcessedTimestamp = s.ProcessedTimestamp;
        }, cancellationToken);

    public Task UpsertTransactionCategoryBalancesAsync(IReadOnlyCollection<TransactionCategoryBalance> records, CancellationToken cancellationToken = default) =>
        UpsertAsync(dbContext.TransactionCategoryBalances, records,
            b => (b.AccountId, b.TypeCode, b.CategoryCode),
            (e, s) => e.Balance = s.Balance,
            cancellationToken);

    public Task UpsertDisclosureGroupsAsync(IReadOnlyCollection<DisclosureGroup> records, CancellationToken cancellationToken = default) =>
        UpsertAsync(dbContext.DisclosureGroups, records,
            d => (d.AccountGroupId, d.TransactionTypeCode, d.TransactionCategoryCode),
            (e, s) => e.InterestRate = s.InterestRate,
            cancellationToken);

    public Task UpsertTransactionTypesAsync(IReadOnlyCollection<TransactionType> records, CancellationToken cancellationToken = default) =>
        UpsertAsync(dbContext.TransactionTypes, records, t => t.TypeCode, (e, s) => e.Description = s.Description, cancellationToken);

    public Task UpsertTransactionCategoriesAsync(IReadOnlyCollection<TransactionCategory> records, CancellationToken cancellationToken = default) =>
        UpsertAsync(dbContext.TransactionCategories, records,
            c => (c.TypeCode, c.CategoryCode),
            (e, s) => e.Description = s.Description,
            cancellationToken);

    private async Task UpsertAsync<TEntity, TKey>(
        DbSet<TEntity> set,
        IReadOnlyCollection<TEntity> records,
        Func<TEntity, TKey> key,
        Action<TEntity, TEntity> copyNonKeyColumns,
        CancellationToken cancellationToken)
        where TEntity : class
        where TKey : notnull
    {
        if (records.Count == 0)
        {
            return;
        }

        var existing = (await set.ToListAsync(cancellationToken)).ToDictionary(key);
        foreach (var record in records)
        {
            if (existing.TryGetValue(key(record), out var tracked))
            {
                copyNonKeyColumns(tracked, record);
            }
            else
            {
                set.Add(record);
                existing[key(record)] = record;
            }
        }
        await dbContext.SaveChangesAsync(cancellationToken);
    }
}
