namespace CardDemo.Domain.Customers;

/// <summary>
/// Customer record, ported from CUSTOMER-RECORD (app/cpy/CVCUS01Y.cpy, RECLN 500).
/// VSAM CUSTDAT KSDS KEYS(9 0) = CUST-ID.
/// </summary>
public class Customer
{
    public required string CustomerId { get; set; }
    public required string FirstName { get; set; }
    public required string MiddleName { get; set; }
    public required string LastName { get; set; }
    public required string AddressLine1 { get; set; }
    public required string AddressLine2 { get; set; }
    public required string AddressLine3 { get; set; }
    public required string AddressStateCode { get; set; }
    public required string AddressCountryCode { get; set; }
    public required string AddressZip { get; set; }
    public required string PhoneNumber1 { get; set; }
    public required string PhoneNumber2 { get; set; }
    public required string Ssn { get; set; }
    public required string GovernmentIssuedId { get; set; }
    public DateOnly? DateOfBirth { get; set; }
    public required string EftAccountId { get; set; }
    public required string PrimaryCardHolderIndicator { get; set; }
    public int FicoCreditScore { get; set; }
}
