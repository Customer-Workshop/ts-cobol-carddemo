using CardDemo.Legacy.Decoders;

namespace CardDemo.Modules.TranReport.Records;

/// <summary>Copybook CVTRA03Y - TRAN-TYPE-RECORD, 60 bytes.</summary>
public sealed record TranTypeRecord(
    string TypeCode,
    string Description,
    string Filler)
{
    public const int Length = 60;

    public static TranTypeRecord Decode(ReadOnlySpan<byte> record)
    {
        if (record.Length != Length)
        {
            throw new ArgumentException($"TRAN-TYPE-RECORD must be {Length} bytes, got {record.Length}.");
        }

        return new TranTypeRecord(
            TypeCode: FieldCodec.DecodeText(record[..2]),
            Description: FieldCodec.DecodeText(record.Slice(2, 50)),
            Filler: FieldCodec.DecodeText(record.Slice(52, 8)));
    }
}
