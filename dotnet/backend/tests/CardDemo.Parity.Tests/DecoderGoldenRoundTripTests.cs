using CardDemo.Legacy.Decoders.Records;
using CardDemo.Persistence;

namespace CardDemo.Parity.Tests;

/// <summary>
/// Encode/decode round-trip tests against the committed golden bytes:
/// every record in the golden unloads must decode and re-encode to the
/// identical padded byte image. This is what proves the copybook->C#
/// mapping is faithful before any business logic is ported.
/// </summary>
public class DecoderGoldenRoundTripTests
{
    [Theory]
    [InlineData("data-roundtrip", "ACCTFILE.seed.unload")]
    [InlineData("interest-calc", "ACCTFILE.post.unload")]
    public void AccountRecords_RoundTripByteExact(string scenario, string file)
    {
        int count = 0;
        foreach (byte[] record in FixedRecordFile.ReadLineSequential(GoldenPaths.Scenario(scenario, file), AccountRecord.Length))
        {
            var decoded = AccountRecord.Decode(record);
            var encoded = new byte[AccountRecord.Length];
            decoded.Encode(encoded);
            Assert.Equal(record, encoded);
            count++;
        }

        Assert.Equal(12, count);
    }

    [Theory]
    [InlineData("data-roundtrip", "TCATBAL.seed.unload")]
    [InlineData("interest-calc", "TCATBAL.post.unload")]
    public void TranCatBalRecords_RoundTripByteExact(string scenario, string file)
    {
        int count = 0;
        foreach (byte[] record in FixedRecordFile.ReadLineSequential(GoldenPaths.Scenario(scenario, file), TranCatBalRecord.Length))
        {
            var decoded = TranCatBalRecord.Decode(record);
            var encoded = new byte[TranCatBalRecord.Length];
            decoded.Encode(encoded);
            Assert.Equal(record, encoded);
            count++;
        }

        Assert.Equal(14, count);
    }

    [Fact]
    public void InterestTransactions_RoundTripByteExact()
    {
        int count = 0;
        foreach (byte[] record in FixedRecordFile.ReadFixed(GoldenPaths.Scenario("interest-calc", "TRANSACT.dat"), TransactionRecord.Length))
        {
            var decoded = TransactionRecord.Decode(record);
            Assert.StartsWith("2025-07-31", decoded.TranId, StringComparison.Ordinal);
            Assert.Equal("01", decoded.TypeCode);
            Assert.Equal(5, decoded.CategoryCode);
            var encoded = new byte[TransactionRecord.Length];
            decoded.Encode(encoded);
            Assert.Equal(record, encoded);
            count++;
        }

        Assert.True(count > 0, "interest-calc TRANSACT.dat golden must not be empty");
    }
}
