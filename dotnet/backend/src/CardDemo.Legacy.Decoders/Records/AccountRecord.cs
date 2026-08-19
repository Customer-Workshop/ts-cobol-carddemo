namespace CardDemo.Legacy.Decoders.Records;

/// <summary>
/// Copybook CVACT01Y - ACCOUNT-RECORD, 300 bytes.
/// Offsets: see docs/migration/01-inventory.md copybook mapping table.
/// </summary>
public sealed record AccountRecord(
    long AccountId,
    string ActiveStatus,
    decimal CurrentBalance,
    decimal CreditLimit,
    decimal CashCreditLimit,
    string OpenDate,
    string ExpirationDate,
    string ReissueDate,
    decimal CurrentCycleCredit,
    decimal CurrentCycleDebit,
    string AddressZip,
    string GroupId,
    string Filler)
{
    public const int Length = 300;

    public static AccountRecord Decode(ReadOnlySpan<byte> record)
    {
        CheckLength(record.Length);
        return new AccountRecord(
            AccountId: ZonedDecimal.DecodeUnsigned(record[..11]),
            ActiveStatus: FieldCodec.DecodeText(record.Slice(11, 1)),
            CurrentBalance: ZonedDecimal.DecodeSigned(record.Slice(12, 12), 2),
            CreditLimit: ZonedDecimal.DecodeSigned(record.Slice(24, 12), 2),
            CashCreditLimit: ZonedDecimal.DecodeSigned(record.Slice(36, 12), 2),
            OpenDate: FieldCodec.DecodeText(record.Slice(48, 10)),
            ExpirationDate: FieldCodec.DecodeText(record.Slice(58, 10)),
            ReissueDate: FieldCodec.DecodeText(record.Slice(68, 10)),
            CurrentCycleCredit: ZonedDecimal.DecodeSigned(record.Slice(78, 12), 2),
            CurrentCycleDebit: ZonedDecimal.DecodeSigned(record.Slice(90, 12), 2),
            AddressZip: FieldCodec.DecodeText(record.Slice(102, 10)),
            GroupId: FieldCodec.DecodeText(record.Slice(112, 10)),
            Filler: FieldCodec.DecodeText(record.Slice(122, 178)));
    }

    public void Encode(Span<byte> destination)
    {
        CheckLength(destination.Length);
        ZonedDecimal.EncodeUnsigned(AccountId, destination[..11]);
        FieldCodec.EncodeText(ActiveStatus, destination.Slice(11, 1));
        ZonedDecimal.EncodeSigned(CurrentBalance, destination.Slice(12, 12), 2);
        ZonedDecimal.EncodeSigned(CreditLimit, destination.Slice(24, 12), 2);
        ZonedDecimal.EncodeSigned(CashCreditLimit, destination.Slice(36, 12), 2);
        FieldCodec.EncodeText(OpenDate, destination.Slice(48, 10));
        FieldCodec.EncodeText(ExpirationDate, destination.Slice(58, 10));
        FieldCodec.EncodeText(ReissueDate, destination.Slice(68, 10));
        ZonedDecimal.EncodeSigned(CurrentCycleCredit, destination.Slice(78, 12), 2);
        ZonedDecimal.EncodeSigned(CurrentCycleDebit, destination.Slice(90, 12), 2);
        FieldCodec.EncodeText(AddressZip, destination.Slice(102, 10));
        FieldCodec.EncodeText(GroupId, destination.Slice(112, 10));
        FieldCodec.EncodeText(Filler, destination.Slice(122, 178));
    }

    private static void CheckLength(int length)
    {
        if (length != Length)
        {
            throw new ArgumentException($"ACCOUNT-RECORD must be {Length} bytes, got {length}.");
        }
    }
}
