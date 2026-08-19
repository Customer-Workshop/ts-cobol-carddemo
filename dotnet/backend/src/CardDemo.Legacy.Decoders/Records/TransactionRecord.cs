namespace CardDemo.Legacy.Decoders.Records;

/// <summary>Copybook CVTRA05Y - TRAN-RECORD, 350 bytes.</summary>
public sealed record TransactionRecord(
    string TranId,
    string TypeCode,
    int CategoryCode,
    string Source,
    string Description,
    decimal Amount,
    long MerchantId,
    string MerchantName,
    string MerchantCity,
    string MerchantZip,
    string CardNumber,
    string OriginTimestamp,
    string ProcessTimestamp,
    string Filler)
{
    public const int Length = 350;

    public static TransactionRecord Decode(ReadOnlySpan<byte> record)
    {
        CheckLength(record.Length);
        return new TransactionRecord(
            TranId: FieldCodec.DecodeText(record[..16]),
            TypeCode: FieldCodec.DecodeText(record.Slice(16, 2)),
            CategoryCode: (int)ZonedDecimal.DecodeUnsigned(record.Slice(18, 4)),
            Source: FieldCodec.DecodeText(record.Slice(22, 10)),
            Description: FieldCodec.DecodeText(record.Slice(32, 100)),
            Amount: ZonedDecimal.DecodeSigned(record.Slice(132, 11), 2),
            MerchantId: ZonedDecimal.DecodeUnsigned(record.Slice(143, 9)),
            MerchantName: FieldCodec.DecodeText(record.Slice(152, 50)),
            MerchantCity: FieldCodec.DecodeText(record.Slice(202, 50)),
            MerchantZip: FieldCodec.DecodeText(record.Slice(252, 10)),
            CardNumber: FieldCodec.DecodeText(record.Slice(262, 16)),
            OriginTimestamp: FieldCodec.DecodeText(record.Slice(278, 26)),
            ProcessTimestamp: FieldCodec.DecodeText(record.Slice(304, 26)),
            Filler: FieldCodec.DecodeText(record.Slice(330, 20)));
    }

    public void Encode(Span<byte> destination)
    {
        CheckLength(destination.Length);
        FieldCodec.EncodeText(TranId, destination[..16]);
        FieldCodec.EncodeText(TypeCode, destination.Slice(16, 2));
        ZonedDecimal.EncodeUnsigned(CategoryCode, destination.Slice(18, 4));
        FieldCodec.EncodeText(Source, destination.Slice(22, 10));
        FieldCodec.EncodeText(Description, destination.Slice(32, 100));
        ZonedDecimal.EncodeSigned(Amount, destination.Slice(132, 11), 2);
        ZonedDecimal.EncodeUnsigned(MerchantId, destination.Slice(143, 9));
        FieldCodec.EncodeText(MerchantName, destination.Slice(152, 50));
        FieldCodec.EncodeText(MerchantCity, destination.Slice(202, 50));
        FieldCodec.EncodeText(MerchantZip, destination.Slice(252, 10));
        FieldCodec.EncodeText(CardNumber, destination.Slice(262, 16));
        FieldCodec.EncodeText(OriginTimestamp, destination.Slice(278, 26));
        FieldCodec.EncodeText(ProcessTimestamp, destination.Slice(304, 26));
        FieldCodec.EncodeText(Filler, destination.Slice(330, 20));
    }

    private static void CheckLength(int length)
    {
        if (length != Length)
        {
            throw new ArgumentException($"TRAN-RECORD must be {Length} bytes, got {length}.");
        }
    }
}
