namespace CardDemo.Domain.Transactions;

/// <summary>
/// Transaction record, ported from TRAN-RECORD (app/cpy/CVTRA05Y.cpy, RECLN 350).
/// VSAM TRANSACT KSDS KEYS(16 0) = TRAN-ID; AIX KEYS(26 304) = TRAN-PROC-TS (non-unique).
/// TRAN-AMT is PIC S9(09)V99; timestamps are X(26) yyyy-MM-dd HH:mm:ss.ffffff.
/// </summary>
public class Transaction
{
    public required string TransactionId { get; set; }
    public required string TypeCode { get; set; }
    public required string CategoryCode { get; set; }
    public required string Source { get; set; }
    public required string Description { get; set; }
    public decimal Amount { get; set; }
    public required string MerchantId { get; set; }
    public required string MerchantName { get; set; }
    public required string MerchantCity { get; set; }
    public required string MerchantZip { get; set; }
    public required string CardNumber { get; set; }
    public DateTime? OriginalTimestamp { get; set; }
    public DateTime? ProcessedTimestamp { get; set; }
}
