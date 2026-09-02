namespace CardDemo.Domain.Transactions;

/// <summary>
/// Transaction category, ported from TRAN-CAT-RECORD (app/cpy/CVTRA04Y.cpy, RECLN 60).
/// VSAM TRANCATG KSDS KEYS(6 0) = TRAN-TYPE-CD + TRAN-CAT-CD.
/// </summary>
public class TransactionCategory
{
    public required string TypeCode { get; set; }
    public required string CategoryCode { get; set; }
    public required string Description { get; set; }
}
