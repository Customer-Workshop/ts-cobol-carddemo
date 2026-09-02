namespace CardDemo.Domain.Transactions;

/// <summary>
/// Disclosure group, ported from DIS-GROUP-RECORD (app/cpy/CVTRA02Y.cpy, RECLN 50).
/// VSAM DISCGRP KSDS KEYS(16 0) = DIS-ACCT-GROUP-ID + DIS-TRAN-TYPE-CD + DIS-TRAN-CAT-CD. Rate is PIC S9(04)V99.
/// </summary>
public class DisclosureGroup
{
    public required string AccountGroupId { get; set; }
    public required string TransactionTypeCode { get; set; }
    public required string TransactionCategoryCode { get; set; }
    public decimal InterestRate { get; set; }
}
