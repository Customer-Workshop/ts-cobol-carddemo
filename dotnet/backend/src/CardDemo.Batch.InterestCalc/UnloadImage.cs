using CardDemo.Legacy.Decoders.Records;

namespace CardDemo.Batch.InterestCalc;

/// <summary>
/// Builders for the parity surfaces CBACT04C is gated on: the raw
/// TRANSACT record-sequential bytes and the keyed-order LINE SEQUENTIAL
/// unload images (GnuCOBOL strips trailing spaces on LINE SEQUENTIAL
/// WRITE and terminates each record with LF, so the unload image does
/// too).
/// </summary>
public static class UnloadImage
{
    public static byte[] TransactFile(IEnumerable<TransactionRecord> transactions)
    {
        ArgumentNullException.ThrowIfNull(transactions);
        using var stream = new MemoryStream();
        Span<byte> record = stackalloc byte[TransactionRecord.Length];
        foreach (var transaction in transactions)
        {
            transaction.Encode(record);
            stream.Write(record);
        }

        return stream.ToArray();
    }

    public static byte[] AccountUnload(IEnumerable<AccountRecord> accounts)
    {
        ArgumentNullException.ThrowIfNull(accounts);
        using var stream = new MemoryStream();
        Span<byte> record = stackalloc byte[AccountRecord.Length];
        foreach (var account in accounts)
        {
            account.Encode(record);
            WriteLineSequential(stream, record);
        }

        return stream.ToArray();
    }

    public static byte[] TranCatBalUnload(IEnumerable<TranCatBalRecord> categoryBalances)
    {
        ArgumentNullException.ThrowIfNull(categoryBalances);
        using var stream = new MemoryStream();
        Span<byte> record = stackalloc byte[TranCatBalRecord.Length];
        foreach (var categoryBalance in categoryBalances)
        {
            categoryBalance.Encode(record);
            WriteLineSequential(stream, record);
        }

        return stream.ToArray();
    }

    private static void WriteLineSequential(MemoryStream stream, ReadOnlySpan<byte> record)
    {
        int end = record.Length;
        while (end > 0 && record[end - 1] == (byte)' ')
        {
            end--;
        }

        stream.Write(record[..end]);
        stream.WriteByte((byte)'\n');
    }
}
