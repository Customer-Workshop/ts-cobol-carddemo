namespace CardDemo.Domain.Transactions;

/// <summary>
/// Transaction category balance, ported from TRAN-CAT-BAL-RECORD (app/cpy/CVTRA01Y.cpy, RECLN 50).
/// VSAM TCATBALF KSDS KEYS(17 0) = TRANCAT-ACCT-ID + TRANCAT-TYPE-CD + TRANCAT-CD. Balance is PIC S9(09)V99.
/// </summary>
public class TransactionCategoryBalance
{
    public required string AccountId { get; set; }
    public required string TypeCode { get; set; }
    public required string CategoryCode { get; set; }
    public decimal Balance { get; set; }
}
