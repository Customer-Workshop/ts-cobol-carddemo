using CardDemo.Legacy.Decoders;

namespace CardDemo.Modules.TranReport.Records;

/// <summary>Copybook CVTRA04Y - TRAN-CAT-RECORD, 60 bytes.</summary>
public sealed record TranCategoryRecord(
    string TypeCode,
    int CategoryCode,
    string Description,
    string Filler)
{
    public const int Length = 60;

    public static TranCategoryRecord Decode(ReadOnlySpan<byte> record)
    {
        if (record.Length != Length)
        {
            throw new ArgumentException($"TRAN-CAT-RECORD must be {Length} bytes, got {record.Length}.");
        }

        return new TranCategoryRecord(
            TypeCode: FieldCodec.DecodeText(record[..2]),
            CategoryCode: (int)ZonedDecimal.DecodeUnsigned(record.Slice(2, 4)),
            Description: FieldCodec.DecodeText(record.Slice(6, 50)),
            Filler: FieldCodec.DecodeText(record.Slice(56, 4)));
    }
}
