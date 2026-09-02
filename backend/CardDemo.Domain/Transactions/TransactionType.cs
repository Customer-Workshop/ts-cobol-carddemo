namespace CardDemo.Domain.Transactions;

/// <summary>
/// Transaction type, ported from TRAN-TYPE-RECORD (app/cpy/CVTRA03Y.cpy, RECLN 60).
/// VSAM TRANTYPE KSDS KEYS(2 0) = TRAN-TYPE.
/// </summary>
public class TransactionType
{
    public required string TypeCode { get; set; }
    public required string Description { get; set; }
}
