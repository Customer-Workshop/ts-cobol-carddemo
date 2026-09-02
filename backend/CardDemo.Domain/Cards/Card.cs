namespace CardDemo.Domain.Cards;

/// <summary>
/// Card record, ported from CARD-RECORD (app/cpy/CVACT02Y.cpy, RECLN 150).
/// VSAM CARDDAT KSDS KEYS(16 0) = CARD-NUM; AIX CARDAIX KEYS(11 16) = CARD-ACCT-ID (non-unique).
/// </summary>
public class Card
{
    public required string CardNumber { get; set; }
    public required string AccountId { get; set; }
    public required string CvvCode { get; set; }
    public required string EmbossedName { get; set; }
    public DateOnly? ExpirationDate { get; set; }
    public required string ActiveStatus { get; set; }
}
