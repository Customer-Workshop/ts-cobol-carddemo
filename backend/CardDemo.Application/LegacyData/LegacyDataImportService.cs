namespace CardDemo.Application.LegacyData;

/// <summary>Per-dataset upsert counts from one <see cref="LegacyDataImportService.ImportAsync"/> run.</summary>
public record LegacyDataImportResult(
    int Accounts,
    int Cards,
    int CardXrefs,
    int Customers,
    int Transactions,
    int TransactionCategoryBalances,
    int DisclosureGroups,
    int TransactionTypes,
    int TransactionCategories)
{
    public int Total => Accounts + Cards + CardXrefs + Customers + Transactions
        + TransactionCategoryBalances + DisclosureGroups + TransactionTypes + TransactionCategories;
}

/// <summary>
/// Idempotent importer for the app/data/ASCII fixed-width exports, following the UsrsecImportService
/// pattern: parse each record per its copybook, then upsert by KSDS primary key.
/// </summary>
public class LegacyDataImportService(ILegacyDataWriter writer)
{
    public async Task<LegacyDataImportResult> ImportAsync(LegacyDataSeedPaths paths, CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(paths);
        return new LegacyDataImportResult(
            Accounts: await ImportFileAsync(paths.AccountPath, AccountRecordParser.Parse, writer.UpsertAccountsAsync, cancellationToken),
            Cards: await ImportFileAsync(paths.CardPath, CardRecordParser.Parse, writer.UpsertCardsAsync, cancellationToken),
            CardXrefs: await ImportFileAsync(paths.CardXrefPath, CardXrefRecordParser.Parse, writer.UpsertCardXrefsAsync, cancellationToken),
            Customers: await ImportFileAsync(paths.CustomerPath, CustomerRecordParser.Parse, writer.UpsertCustomersAsync, cancellationToken),
            Transactions: await ImportFileAsync(paths.TransactionPath, TransactionRecordParser.Parse, writer.UpsertTransactionsAsync, cancellationToken),
            TransactionCategoryBalances: await ImportFileAsync(paths.TransactionCategoryBalancePath, TransactionCategoryBalanceRecordParser.Parse, writer.UpsertTransactionCategoryBalancesAsync, cancellationToken),
            DisclosureGroups: await ImportFileAsync(paths.DisclosureGroupPath, DisclosureGroupRecordParser.Parse, writer.UpsertDisclosureGroupsAsync, cancellationToken),
            TransactionTypes: await ImportFileAsync(paths.TransactionTypePath, TransactionTypeRecordParser.Parse, writer.UpsertTransactionTypesAsync, cancellationToken),
            TransactionCategories: await ImportFileAsync(paths.TransactionCategoryPath, TransactionCategoryRecordParser.Parse, writer.UpsertTransactionCategoriesAsync, cancellationToken));
    }

    public Task<int> ImportAccountsAsync(IEnumerable<string> records, CancellationToken cancellationToken = default) =>
        ImportRecordsAsync(records, AccountRecordParser.Parse, writer.UpsertAccountsAsync, cancellationToken);

    public Task<int> ImportCardsAsync(IEnumerable<string> records, CancellationToken cancellationToken = default) =>
        ImportRecordsAsync(records, CardRecordParser.Parse, writer.UpsertCardsAsync, cancellationToken);

    public Task<int> ImportCardXrefsAsync(IEnumerable<string> records, CancellationToken cancellationToken = default) =>
        ImportRecordsAsync(records, CardXrefRecordParser.Parse, writer.UpsertCardXrefsAsync, cancellationToken);

    public Task<int> ImportCustomersAsync(IEnumerable<string> records, CancellationToken cancellationToken = default) =>
        ImportRecordsAsync(records, CustomerRecordParser.Parse, writer.UpsertCustomersAsync, cancellationToken);

    public Task<int> ImportTransactionsAsync(IEnumerable<string> records, CancellationToken cancellationToken = default) =>
        ImportRecordsAsync(records, TransactionRecordParser.Parse, writer.UpsertTransactionsAsync, cancellationToken);

    public Task<int> ImportTransactionCategoryBalancesAsync(IEnumerable<string> records, CancellationToken cancellationToken = default) =>
        ImportRecordsAsync(records, TransactionCategoryBalanceRecordParser.Parse, writer.UpsertTransactionCategoryBalancesAsync, cancellationToken);

    public Task<int> ImportDisclosureGroupsAsync(IEnumerable<string> records, CancellationToken cancellationToken = default) =>
        ImportRecordsAsync(records, DisclosureGroupRecordParser.Parse, writer.UpsertDisclosureGroupsAsync, cancellationToken);

    public Task<int> ImportTransactionTypesAsync(IEnumerable<string> records, CancellationToken cancellationToken = default) =>
        ImportRecordsAsync(records, TransactionTypeRecordParser.Parse, writer.UpsertTransactionTypesAsync, cancellationToken);

    public Task<int> ImportTransactionCategoriesAsync(IEnumerable<string> records, CancellationToken cancellationToken = default) =>
        ImportRecordsAsync(records, TransactionCategoryRecordParser.Parse, writer.UpsertTransactionCategoriesAsync, cancellationToken);

    private static Task<int> ImportFileAsync<T>(
        string? path,
        Func<string, T> parse,
        Func<IReadOnlyCollection<T>, CancellationToken, Task> upsert,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(path) || !File.Exists(path))
        {
            return Task.FromResult(0);
        }
        return ImportRecordsAsync(LegacyDataSeedSource.ReadRecords(path), parse, upsert, cancellationToken);
    }

    private static async Task<int> ImportRecordsAsync<T>(
        IEnumerable<string> records,
        Func<string, T> parse,
        Func<IReadOnlyCollection<T>, CancellationToken, Task> upsert,
        CancellationToken cancellationToken)
    {
        var parsed = records.Select(parse).ToList();
        if (parsed.Count > 0)
        {
            await upsert(parsed, cancellationToken);
        }
        return parsed.Count;
    }
}
