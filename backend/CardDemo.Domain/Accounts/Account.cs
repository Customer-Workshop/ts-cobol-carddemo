namespace CardDemo.Domain.Accounts;

/// <summary>
/// Account master record, ported from ACCOUNT-RECORD (app/cpy/CVACT01Y.cpy, RECLN 300).
/// VSAM ACCTDAT KSDS KEYS(11 0) = ACCT-ID. Amounts are PIC S9(10)V99; dates are X(10) yyyy-MM-dd.
/// </summary>
public class Account
{
    public required string AccountId { get; set; }
    public required string ActiveStatus { get; set; }
    public decimal CurrentBalance { get; set; }
    public decimal CreditLimit { get; set; }
    public decimal CashCreditLimit { get; set; }
    public DateOnly? OpenDate { get; set; }
    public DateOnly? ExpirationDate { get; set; }
    public DateOnly? ReissueDate { get; set; }
    public decimal CurrentCycleCredit { get; set; }
    public decimal CurrentCycleDebit { get; set; }
    public required string AddressZip { get; set; }
    public required string GroupId { get; set; }
}
