namespace CardDemo.Legacy.Decoders.Records;

/// <summary>Copybook CVTRA01Y - TRAN-CAT-BAL-RECORD, 50 bytes.</summary>
public sealed record TranCatBalRecord(
    long AccountId,
    string TypeCode,
    int CategoryCode,
    decimal Balance,
    string Filler)
{
    public const int Length = 50;

    public static TranCatBalRecord Decode(ReadOnlySpan<byte> record)
    {
        CheckLength(record.Length);
        return new TranCatBalRecord(
            AccountId: ZonedDecimal.DecodeUnsigned(record[..11]),
            TypeCode: FieldCodec.DecodeText(record.Slice(11, 2)),
            CategoryCode: (int)ZonedDecimal.DecodeUnsigned(record.Slice(13, 4)),
            Balance: ZonedDecimal.DecodeSigned(record.Slice(17, 11), 2),
            Filler: FieldCodec.DecodeText(record.Slice(28, 22)));
    }

    public void Encode(Span<byte> destination)
    {
        CheckLength(destination.Length);
        ZonedDecimal.EncodeUnsigned(AccountId, destination[..11]);
        FieldCodec.EncodeText(TypeCode, destination.Slice(11, 2));
        ZonedDecimal.EncodeUnsigned(CategoryCode, destination.Slice(13, 4));
        ZonedDecimal.EncodeSigned(Balance, destination.Slice(17, 11), 2);
        FieldCodec.EncodeText(Filler, destination.Slice(28, 22));
    }

    private static void CheckLength(int length)
    {
        if (length != Length)
        {
            throw new ArgumentException($"TRAN-CAT-BAL-RECORD must be {Length} bytes, got {length}.");
        }
    }
}
