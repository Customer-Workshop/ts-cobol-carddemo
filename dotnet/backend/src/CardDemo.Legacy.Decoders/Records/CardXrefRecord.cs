namespace CardDemo.Legacy.Decoders.Records;

/// <summary>Copybook CVACT03Y - CARD-XREF-RECORD, 50 bytes.</summary>
public sealed record CardXrefRecord(
    string CardNumber,
    long CustomerId,
    long AccountId,
    string Filler)
{
    public const int Length = 50;

    public static CardXrefRecord Decode(ReadOnlySpan<byte> record)
    {
        CheckLength(record.Length);
        return new CardXrefRecord(
            CardNumber: FieldCodec.DecodeText(record[..16]),
            CustomerId: ZonedDecimal.DecodeUnsigned(record.Slice(16, 9)),
            AccountId: ZonedDecimal.DecodeUnsigned(record.Slice(25, 11)),
            Filler: FieldCodec.DecodeText(record.Slice(36, 14)));
    }

    public void Encode(Span<byte> destination)
    {
        CheckLength(destination.Length);
        FieldCodec.EncodeText(CardNumber, destination[..16]);
        ZonedDecimal.EncodeUnsigned(CustomerId, destination.Slice(16, 9));
        ZonedDecimal.EncodeUnsigned(AccountId, destination.Slice(25, 11));
        FieldCodec.EncodeText(Filler, destination.Slice(36, 14));
    }

    private static void CheckLength(int length)
    {
        if (length != Length)
        {
            throw new ArgumentException($"CARD-XREF-RECORD must be {Length} bytes, got {length}.");
        }
    }
}
