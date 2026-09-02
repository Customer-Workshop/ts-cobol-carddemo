namespace CardDemo.Domain.Cards;

/// <summary>
/// Card cross-reference, ported from CARD-XREF-RECORD (app/cpy/CVACT03Y.cpy, RECLN 50).
/// VSAM CCXREF KSDS KEYS(16 0) = XREF-CARD-NUM; AIX CXACAIX KEYS(11 25) = XREF-ACCT-ID (non-unique).
/// </summary>
public class CardXref
{
    public required string CardNumber { get; set; }
    public required string CustomerId { get; set; }
    public required string AccountId { get; set; }
}
