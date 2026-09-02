using CardDemo.Application.LegacyData;
using CardDemo.Infrastructure.Persistence;
using CardDemo.Tests.Users;
using FluentAssertions;
using Microsoft.EntityFrameworkCore;
using Npgsql;

namespace CardDemo.Tests.LegacyData;

public class LegacyDataImportIntegrationTests(PostgresFixture fixture) : IClassFixture<PostgresFixture>
{
    private static readonly string[] ExpectedTables =
    [
        "accounts",
        "cards",
        "card_xref",
        "customers",
        "transactions",
        "transaction_category_balances",
        "disclosure_groups",
        "transaction_types",
        "transaction_categories"
    ];

    private static LegacyDataSeedPaths SeedPaths => new()
    {
        AccountPath = TestPaths.AsciiData("acctdata.txt"),
        CardPath = TestPaths.AsciiData("carddata.txt"),
        CardXrefPath = TestPaths.AsciiData("cardxref.txt"),
        CustomerPath = TestPaths.AsciiData("custdata.txt"),
        TransactionPath = TestPaths.AsciiData("dailytran.txt"),
        TransactionCategoryBalancePath = TestPaths.AsciiData("tcatbal.txt"),
        DisclosureGroupPath = TestPaths.AsciiData("discgrp.txt"),
        TransactionTypePath = TestPaths.AsciiData("trantype.txt"),
        TransactionCategoryPath = TestPaths.AsciiData("trancatg.txt")
    };

    private async Task<LegacyDataImportResult> RunImportAsync()
    {
        await using var context = fixture.CreateContext();
        var importer = new LegacyDataImportService(new LegacyDataWriter(context));
        return await importer.ImportAsync(SeedPaths);
    }

    [Fact]
    public async Task Migration_CreatesAllSharedTablesWithVsamKeysAndAlternateIndexes()
    {
        await using var connection = new NpgsqlConnection(fixture.ConnectionString);
        await connection.OpenAsync();

        var tables = await QueryStringsAsync(connection,
            "select table_name from information_schema.tables where table_schema = 'public'");
        tables.Should().Contain(ExpectedTables).And.Contain("users");

        var primaryKeys = await QueryStringsAsync(connection,
            """
            select tc.table_name || ':' || string_agg(kcu.column_name, ',' order by kcu.ordinal_position)
            from information_schema.table_constraints tc
            join information_schema.key_column_usage kcu on kcu.constraint_name = tc.constraint_name
            where tc.constraint_type = 'PRIMARY KEY' and tc.table_schema = 'public'
            group by tc.table_name
            """);
        primaryKeys.Should().Contain(
        [
            "accounts:acct_id",
            "cards:card_num",
            "card_xref:xref_card_num",
            "customers:cust_id",
            "transactions:tran_id",
            "transaction_category_balances:trancat_acct_id,trancat_type_cd,trancat_cd",
            "disclosure_groups:dis_acct_group_id,dis_tran_type_cd,dis_tran_cat_cd",
            "transaction_types:tran_type",
            "transaction_categories:tran_type_cd,tran_cat_cd"
        ]);

        var indexes = await QueryStringsAsync(connection,
            "select indexname || ':' || indexdef from pg_indexes where schemaname = 'public'");
        indexes.Should().Contain(i => i.StartsWith("ix_cards_card_acct_id:") && !i.Contains("UNIQUE"), "CARDAIX is NONUNIQUEKEY");
        indexes.Should().Contain(i => i.StartsWith("ix_card_xref_xref_acct_id:") && !i.Contains("UNIQUE"), "CXACAIX is NONUNIQUEKEY");
        indexes.Should().Contain(i => i.StartsWith("ix_transactions_tran_proc_ts:") && !i.Contains("UNIQUE"), "TRANSACT AIX on TRAN-PROC-TS is NONUNIQUEKEY");

        var columns = await QueryStringsAsync(connection,
            """
            select table_name || '.' || column_name || ':' || data_type
                || coalesce('(' || character_maximum_length || ')', '')
                || coalesce('(' || numeric_precision || ',' || numeric_scale || ')', '')
            from information_schema.columns where table_schema = 'public'
            """);
        columns.Should().Contain(
        [
            "accounts.acct_id:character varying(11)",
            "accounts.acct_curr_bal:numeric(12,2)",
            "accounts.acct_open_date:date",
            "cards.card_num:character varying(16)",
            "cards.card_embossed_name:character varying(50)",
            "customers.cust_id:character varying(9)",
            "customers.cust_fico_credit_score:integer(32,0)",
            "transactions.tran_amt:numeric(11,2)",
            "transactions.tran_desc:character varying(100)",
            "transactions.tran_proc_ts:timestamp without time zone",
            "transaction_category_balances.tran_cat_bal:numeric(11,2)",
            "disclosure_groups.dis_int_rate:numeric(6,2)"
        ]);
    }

    [Fact]
    public async Task Import_LoadsAllAsciiSeedFilesAndIsIdempotent()
    {
        var firstRun = await RunImportAsync();
        var secondRun = await RunImportAsync();

        var expected = new LegacyDataImportResult(50, 50, 50, 50, 300, 50, 51, 7, 18);
        firstRun.Should().Be(expected);
        secondRun.Should().Be(expected);

        await using var context = fixture.CreateContext();
        (await context.Accounts.CountAsync()).Should().Be(50, "re-running the import must upsert, not duplicate");
        (await context.Cards.CountAsync()).Should().Be(50);
        (await context.CardXrefs.CountAsync()).Should().Be(50);
        (await context.Customers.CountAsync()).Should().Be(50);
        (await context.Transactions.CountAsync()).Should().Be(300);
        (await context.TransactionCategoryBalances.CountAsync()).Should().Be(50);
        (await context.DisclosureGroups.CountAsync()).Should().Be(51);
        (await context.TransactionTypes.CountAsync()).Should().Be(7);
        (await context.TransactionCategories.CountAsync()).Should().Be(18);
    }

    [Fact]
    public async Task Import_ReimportOverwritesNonKeyColumnsInsteadOfDuplicating()
    {
        await RunImportAsync();

        var original = LegacyDataSeedSource.ReadRecords(TestPaths.AsciiData("acctdata.txt"))[0];
        var modified = original[..11] + "N" + original[12..];

        await using (var context = fixture.CreateContext())
        {
            var importer = new LegacyDataImportService(new LegacyDataWriter(context));
            (await importer.ImportAccountsAsync([modified])).Should().Be(1);
        }

        await using var verify = fixture.CreateContext();
        (await verify.Accounts.CountAsync()).Should().Be(50);
        (await new AccountRepository(verify).GetByIdAsync("00000000001"))!.ActiveStatus.Should().Be("N");

        await RunImportAsync();
        await using var restored = fixture.CreateContext();
        (await new AccountRepository(restored).GetByIdAsync("00000000001"))!.ActiveStatus.Should().Be("Y");
    }

    [Fact]
    public async Task Account_RoundTripsDecimalsDatesAndWidths()
    {
        await RunImportAsync();

        await using var context = fixture.CreateContext();
        var account = await new AccountRepository(context).GetByIdAsync("00000000001");

        account.Should().NotBeNull();
        account!.ActiveStatus.Should().Be("Y");
        account.CurrentBalance.Should().Be(194.00m);
        account.CreditLimit.Should().Be(2020.00m);
        account.CashCreditLimit.Should().Be(1020.00m);
        account.OpenDate.Should().Be(new DateOnly(2014, 11, 20));
        account.ExpirationDate.Should().Be(new DateOnly(2025, 5, 20));
        account.ReissueDate.Should().Be(new DateOnly(2025, 5, 20));
        account.AddressZip.Should().Be("A000000000");

        (await new AccountRepository(context).GetByIdAsync("99999999999")).Should().BeNull();
    }

    [Fact]
    public async Task Transaction_RoundTripsNegativeOverpunchAmountAndTimestamps()
    {
        await RunImportAsync();

        await using var context = fixture.CreateContext();
        var repository = new TransactionRepository(context);

        var credit = await repository.GetByIdAsync("0000000001774260");
        credit.Should().NotBeNull();
        credit!.Amount.Should().Be(-919.00m);
        credit.TypeCode.Should().Be("03");
        credit.CategoryCode.Should().Be("0001");
        credit.CardNumber.Should().Be("0927987108636232");
        credit.OriginalTimestamp.Should().Be(new DateTime(2022, 6, 10, 19, 27, 53));
        credit.ProcessedTimestamp.Should().BeNull();

        var debit = await repository.GetByIdAsync("0000000000683580");
        debit!.Amount.Should().Be(504.77m);
    }

    [Fact]
    public async Task Customer_RoundTripsThroughCardXrefFromAccount()
    {
        await RunImportAsync();

        await using var context = fixture.CreateContext();
        var xref = await new CardXrefRepository(context).GetFirstByAccountIdAsync("00000000050");
        xref.Should().NotBeNull();
        xref!.CardNumber.Should().Be("0500024453765740");
        xref.CustomerId.Should().Be("000000050");

        var customer = await new CustomerRepository(context).GetByIdAsync(xref.CustomerId);
        customer.Should().NotBeNull();
        customer!.CustomerId.Should().Be("000000050");
        customer.DateOfBirth.Should().NotBeNull();
        customer.FicoCreditScore.Should().BeInRange(0, 999);

        var byCard = await new CardXrefRepository(context).GetByCardNumberAsync("0500024453765740");
        byCard!.AccountId.Should().Be("00000000050");

        (await new CardXrefRepository(context).GetFirstByAccountIdAsync("99999999999")).Should().BeNull();
    }

    [Fact]
    public async Task Cards_AreReadableByCardNumberAndByAccountAlternateIndex()
    {
        await RunImportAsync();

        await using var context = fixture.CreateContext();
        var repository = new CardRepository(context);

        var card = await repository.GetByCardNumberAsync("0500024453765740");
        card.Should().NotBeNull();
        card!.AccountId.Should().Be("00000000050");
        card.CvvCode.Should().Be("747");
        card.EmbossedName.Should().Be("Aniya Von");
        card.ExpirationDate.Should().Be(new DateOnly(2023, 3, 9));

        var byAccount = await repository.ListByAccountIdAsync("00000000050");
        byAccount.Should().NotBeEmpty().And.OnlyContain(c => c.AccountId == "00000000050");
        byAccount.Select(c => c.CardNumber).Should().BeInAscendingOrder(StringComparer.Ordinal);

        var page = await repository.BrowseAsync("0000000000000000", 7);
        page.Items.Should().HaveCount(7);
        page.HasMore.Should().BeTrue();
        page.Items.Select(c => c.CardNumber).Should().BeInAscendingOrder(StringComparer.Ordinal);

        var filtered = await repository.BrowseAsync("0000000000000000", 7, "00000000050");
        filtered.Items.Should().OnlyContain(c => c.AccountId == "00000000050");
    }

    [Fact]
    public async Task Transactions_PageInTranIdOrderLikeStartbrReadnext()
    {
        await RunImportAsync();

        await using var context = fixture.CreateContext();
        var repository = new TransactionRepository(context);
        var allIds = await context.Transactions.AsNoTracking()
            .OrderBy(t => t.TransactionId)
            .Select(t => t.TransactionId)
            .ToListAsync();
        allIds.Should().HaveCount(300).And.BeInAscendingOrder(StringComparer.Ordinal);

        var first = await repository.BrowseAsync("0000000000000000", 10);
        first.Items.Select(t => t.TransactionId).Should().Equal(allIds.Take(10));
        first.HasMore.Should().BeTrue();

        var second = await repository.BrowseAsync(allIds[10], 10);
        second.Items.Select(t => t.TransactionId).Should().Equal(allIds.Skip(10).Take(10));

        var last = await repository.BrowseAsync(allIds[295], 10);
        last.Items.Should().HaveCount(5);
        last.HasMore.Should().BeFalse();

        var previous = await repository.BrowseBackwardAsync(allIds[10], 10);
        previous.Select(t => t.TransactionId).Should().Equal(allIds.Take(10));

        var byCard = await repository.ListByCardNumberAsync("0927987108636232");
        byCard.Should().Contain(t => t.TransactionId == "0000000001774260");
        byCard.Select(t => t.TransactionId).Should().BeInAscendingOrder(StringComparer.Ordinal);
    }

    [Fact]
    public async Task ReferenceTables_RoundTripCompositeKeys()
    {
        await RunImportAsync();

        await using var context = fixture.CreateContext();

        var disclosure = await context.DisclosureGroups.AsNoTracking()
            .SingleAsync(d => d.AccountGroupId == "A000000000" && d.TransactionTypeCode == "01" && d.TransactionCategoryCode == "0001");
        disclosure.InterestRate.Should().Be(15.00m);

        var zeroApr = await context.DisclosureGroups.AsNoTracking()
            .SingleAsync(d => d.AccountGroupId == "ZEROAPR" && d.TransactionTypeCode == "07" && d.TransactionCategoryCode == "0001");
        zeroApr.InterestRate.Should().Be(0m);

        var balance = await context.TransactionCategoryBalances.AsNoTracking()
            .SingleAsync(b => b.AccountId == "00000000001" && b.TypeCode == "01" && b.CategoryCode == "0001");
        balance.Balance.Should().Be(0m);

        (await context.TransactionTypes.AsNoTracking().SingleAsync(t => t.TypeCode == "01")).Description.Should().Be("Purchase");
        (await context.TransactionCategories.AsNoTracking().SingleAsync(c => c.TypeCode == "01" && c.CategoryCode == "0001"))
            .Description.Should().Be("Regular Sales Draft");
    }

    private static async Task<List<string>> QueryStringsAsync(NpgsqlConnection connection, string sql)
    {
        await using var command = new NpgsqlCommand(sql, connection);
        await using var reader = await command.ExecuteReaderAsync();
        var values = new List<string>();
        while (await reader.ReadAsync())
        {
            values.Add(reader.GetString(0));
        }
        return values;
    }
}
