namespace CardDemo.Legacy.Decoders.Records;

/// <summary>Copybook CVTRA02Y - DIS-GROUP-RECORD, 50 bytes.</summary>
public sealed record DisclosureGroupRecord(
    string GroupId,
    string TypeCode,
    int CategoryCode,
    decimal InterestRate,
    string Filler)
{
    public const int Length = 50;

    public static DisclosureGroupRecord Decode(ReadOnlySpan<byte> record)
    {
        CheckLength(record.Length);
        return new DisclosureGroupRecord(
            GroupId: FieldCodec.DecodeText(record[..10]),
            TypeCode: FieldCodec.DecodeText(record.Slice(10, 2)),
            CategoryCode: (int)ZonedDecimal.DecodeUnsigned(record.Slice(12, 4)),
            InterestRate: ZonedDecimal.DecodeSigned(record.Slice(16, 6), 2),
            Filler: FieldCodec.DecodeText(record.Slice(22, 28)));
    }

    public void Encode(Span<byte> destination)
    {
        CheckLength(destination.Length);
        FieldCodec.EncodeText(GroupId, destination[..10]);
        FieldCodec.EncodeText(TypeCode, destination.Slice(10, 2));
        ZonedDecimal.EncodeUnsigned(CategoryCode, destination.Slice(12, 4));
        ZonedDecimal.EncodeSigned(InterestRate, destination.Slice(16, 6), 2);
        FieldCodec.EncodeText(Filler, destination.Slice(22, 28));
    }

    private static void CheckLength(int length)
    {
        if (length != Length)
        {
            throw new ArgumentException($"DIS-GROUP-RECORD must be {Length} bytes, got {length}.");
        }
    }
}
