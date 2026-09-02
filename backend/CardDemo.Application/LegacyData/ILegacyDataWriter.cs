using CardDemo.Domain.Accounts;
using CardDemo.Domain.Cards;
using CardDemo.Domain.Customers;
using CardDemo.Domain.Transactions;

namespace CardDemo.Application.LegacyData;

/// <summary>
/// Keyed upsert port for the VSAM-derived tables (seed/import only). Each upsert is keyed on the
/// dataset's KSDS primary key, so re-importing the same export is a no-op rather than a duplicate.
/// </summary>
public interface ILegacyDataWriter
{
    Task UpsertAccountsAsync(IReadOnlyCollection<Account> records, CancellationToken cancellationToken = default);
    Task UpsertCardsAsync(IReadOnlyCollection<Card> records, CancellationToken cancellationToken = default);
    Task UpsertCardXrefsAsync(IReadOnlyCollection<CardXref> records, CancellationToken cancellationToken = default);
    Task UpsertCustomersAsync(IReadOnlyCollection<Customer> records, CancellationToken cancellationToken = default);
    Task UpsertTransactionsAsync(IReadOnlyCollection<Transaction> records, CancellationToken cancellationToken = default);
    Task UpsertTransactionCategoryBalancesAsync(IReadOnlyCollection<TransactionCategoryBalance> records, CancellationToken cancellationToken = default);
    Task UpsertDisclosureGroupsAsync(IReadOnlyCollection<DisclosureGroup> records, CancellationToken cancellationToken = default);
    Task UpsertTransactionTypesAsync(IReadOnlyCollection<TransactionType> records, CancellationToken cancellationToken = default);
    Task UpsertTransactionCategoriesAsync(IReadOnlyCollection<TransactionCategory> records, CancellationToken cancellationToken = default);
}
