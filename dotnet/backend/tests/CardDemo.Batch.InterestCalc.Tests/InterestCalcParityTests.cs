using CardDemo.Legacy.Decoders.Records;
using CardDemo.Persistence;

namespace CardDemo.Batch.InterestCalc.Tests;

/// <summary>
/// Byte-for-byte parity of the CBACT04C port against the immutable
/// interest-calc goldens, regenerated from the data-roundtrip seed
/// unloads and the wave-1 input fixtures (all oracle-produced bytes).
/// Frozen clock and PARM date match the golden capture: faketime
/// 2025-08-01 09:00:00, PARM date 2025-07-31.
/// </summary>
public class InterestCalcParityTests
{
    private const string ParmDate = "2025-07-31";
    private static readonly DateTime FrozenClock = new(2025, 8, 1, 9, 0, 0, DateTimeKind.Unspecified);

    private static InterestCalcResult RunFromGoldenInputs()
    {
        var input = new InterestCalcInput(
            CategoryBalances: Decode(ParityPaths.Golden("data-roundtrip", "TCATBAL.seed.unload"), TranCatBalRecord.Length, TranCatBalRecord.Decode),
            Accounts: Decode(ParityPaths.Golden("data-roundtrip", "ACCTFILE.seed.unload"), AccountRecord.Length, AccountRecord.Decode),
            DisclosureGroups: Decode(ParityPaths.Fixture("interest-calc-inputs", "DISCGRP.seed.unload"), DisclosureGroupRecord.Length, DisclosureGroupRecord.Decode),
            CardXrefs: Decode(ParityPaths.Fixture("interest-calc-inputs", "XREFFILE.seed.unload"), CardXrefRecord.Length, CardXrefRecord.Decode),
            ParmDate: ParmDate,
            Clock: () => FrozenClock);
        return InterestCalcJob.Run(input);
    }

    private static List<T> Decode<T>(string path, int length, SpanDecoder<T> decode)
    {
        var records = new List<T>();
        foreach (var record in FixedRecordFile.ReadLineSequential(path, length))
        {
            records.Add(decode(record));
        }

        return records;
    }

    private delegate T SpanDecoder<out T>(ReadOnlySpan<byte> record);

    [Fact]
    public void WaveOneInputFixtures_RoundTripByteExact()
    {
        string discPath = ParityPaths.Fixture("interest-calc-inputs", "DISCGRP.seed.unload");
        var discImage = new MemoryStream();
        foreach (var record in FixedRecordFile.ReadLineSequential(discPath, DisclosureGroupRecord.Length))
        {
            byte[] encoded = new byte[DisclosureGroupRecord.Length];
            DisclosureGroupRecord.Decode(record).Encode(encoded);
            WriteLineSequential(discImage, encoded);
        }

        Assert.Equal(File.ReadAllBytes(discPath), discImage.ToArray());

        string xrefPath = ParityPaths.Fixture("interest-calc-inputs", "XREFFILE.seed.unload");
        var xrefImage = new MemoryStream();
        foreach (var record in FixedRecordFile.ReadLineSequential(xrefPath, CardXrefRecord.Length))
        {
            byte[] encoded = new byte[CardXrefRecord.Length];
            CardXrefRecord.Decode(record).Encode(encoded);
            WriteLineSequential(xrefImage, encoded);
        }

        Assert.Equal(File.ReadAllBytes(xrefPath), xrefImage.ToArray());
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

    [Fact]
    public void TransactFile_MatchesGolden_ByteForByte()
    {
        var result = RunFromGoldenInputs();
        byte[] expected = File.ReadAllBytes(ParityPaths.Golden("interest-calc", "TRANSACT.dat"));
        Assert.Equal(expected, UnloadImage.TransactFile(result.Transactions));
    }

    [Fact]
    public void PostAccountUnload_MatchesGolden_ByteForByte()
    {
        var result = RunFromGoldenInputs();
        byte[] expected = File.ReadAllBytes(ParityPaths.Golden("interest-calc", "ACCTFILE.post.unload"));
        Assert.Equal(expected, UnloadImage.AccountUnload(result.PostAccounts));
    }

    [Fact]
    public void PostTcatbalUnload_MatchesGolden_ByteForByte()
    {
        var result = RunFromGoldenInputs();
        byte[] expected = File.ReadAllBytes(ParityPaths.Golden("interest-calc", "TCATBAL.post.unload"));
        Assert.Equal(expected, UnloadImage.TranCatBalUnload(result.PostCategoryBalances));
    }
}
