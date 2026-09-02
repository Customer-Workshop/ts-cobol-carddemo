namespace CardDemo.Application.AccountUpdate;

/// <summary>
/// Screen-shaped snapshot of one account + customer, the web equivalent of
/// ACUP-OLD-DETAILS / ACUP-NEW-DETAILS (app/cbl/COACTUPC.cbl:656-849). Every member is the text
/// of one CACTUPA map field (app/bms/COACTUP.bms); widths are the BMS LENGTHs.
/// </summary>
public sealed record AccountUpdateFields
{
    public string AccountId { get; init; } = string.Empty;
    public string ActiveStatus { get; init; } = string.Empty;
    public string OpenYear { get; init; } = string.Empty;
    public string OpenMonth { get; init; } = string.Empty;
    public string OpenDay { get; init; } = string.Empty;
    public string CreditLimit { get; init; } = string.Empty;
    public string ExpiryYear { get; init; } = string.Empty;
    public string ExpiryMonth { get; init; } = string.Empty;
    public string ExpiryDay { get; init; } = string.Empty;
    public string CashCreditLimit { get; init; } = string.Empty;
    public string ReissueYear { get; init; } = string.Empty;
    public string ReissueMonth { get; init; } = string.Empty;
    public string ReissueDay { get; init; } = string.Empty;
    public string CurrentBalance { get; init; } = string.Empty;
    public string CurrentCycleCredit { get; init; } = string.Empty;
    public string GroupId { get; init; } = string.Empty;
    public string CurrentCycleDebit { get; init; } = string.Empty;
    public string CustomerId { get; init; } = string.Empty;
    public string Ssn1 { get; init; } = string.Empty;
    public string Ssn2 { get; init; } = string.Empty;
    public string Ssn3 { get; init; } = string.Empty;
    public string DobYear { get; init; } = string.Empty;
    public string DobMonth { get; init; } = string.Empty;
    public string DobDay { get; init; } = string.Empty;
    public string FicoScore { get; init; } = string.Empty;
    public string FirstName { get; init; } = string.Empty;
    public string MiddleName { get; init; } = string.Empty;
    public string LastName { get; init; } = string.Empty;
    public string AddressLine1 { get; init; } = string.Empty;
    public string AddressLine2 { get; init; } = string.Empty;
    public string State { get; init; } = string.Empty;
    public string Zip { get; init; } = string.Empty;
    public string City { get; init; } = string.Empty;
    public string Country { get; init; } = string.Empty;
    public string Phone1Area { get; init; } = string.Empty;
    public string Phone1Prefix { get; init; } = string.Empty;
    public string Phone1Line { get; init; } = string.Empty;
    public string Phone2Area { get; init; } = string.Empty;
    public string Phone2Prefix { get; init; } = string.Empty;
    public string Phone2Line { get; init; } = string.Empty;
    public string GovernmentId { get; init; } = string.Empty;
    public string EftAccountId { get; init; } = string.Empty;
    public string PrimaryCardHolder { get; init; } = string.Empty;
}

/// <summary>Map field identifiers used to flag invalid fields (CSSETATY red / '*' marking).</summary>
public static class AccountUpdateFieldNames
{
    public const string AccountId = "accountId";
    public const string ActiveStatus = "activeStatus";
    public const string OpenDate = "openDate";
    public const string CreditLimit = "creditLimit";
    public const string ExpiryDate = "expiryDate";
    public const string CashCreditLimit = "cashCreditLimit";
    public const string ReissueDate = "reissueDate";
    public const string CurrentBalance = "currentBalance";
    public const string CurrentCycleCredit = "currentCycleCredit";
    public const string CurrentCycleDebit = "currentCycleDebit";
    public const string Ssn1 = "ssn1";
    public const string Ssn2 = "ssn2";
    public const string Ssn3 = "ssn3";
    public const string DateOfBirth = "dateOfBirth";
    public const string FicoScore = "ficoScore";
    public const string FirstName = "firstName";
    public const string MiddleName = "middleName";
    public const string LastName = "lastName";
    public const string AddressLine1 = "addressLine1";
    public const string State = "state";
    public const string Zip = "zip";
    public const string City = "city";
    public const string Country = "country";
    public const string Phone1 = "phone1";
    public const string Phone2 = "phone2";
    public const string EftAccountId = "eftAccountId";
    public const string PrimaryCardHolder = "primaryCardHolder";
}
