namespace CardDemo.Application.Accounts;

public enum AccountViewOutcome
{
    Initial,
    NoInput,
    InvalidFilter,
    AccountNotInXref,
    AccountNotInMaster,
    CustomerNotFound,
    Found,
    StoreError
}

/// <summary>WS-EDIT-ACCT-FLAG (COACTVWC.cbl:85-89): drives the account field colour on redisplay.</summary>
public enum AccountFilterState
{
    Blank,
    Invalid,
    Valid
}

/// <summary>Account block of map CACTVWA (COACTVWC.cbl:471-491), already edited to the BMS output pictures.</summary>
public record AccountViewAccountDetails(
    string ActiveStatus,
    string OpenDate,
    string CreditLimit,
    string ExpirationDate,
    string CashCreditLimit,
    string ReissueDate,
    string CurrentBalance,
    string CurrentCycleCredit,
    string GroupId,
    string CurrentCycleDebit);

/// <summary>Customer block of map CACTVWA (COACTVWC.cbl:494-523), truncated to the BMS field widths.</summary>
public record AccountViewCustomerDetails(
    string CustomerId,
    string Ssn,
    string DateOfBirth,
    string FicoScore,
    string FirstName,
    string MiddleName,
    string LastName,
    string AddressLine1,
    string AddressLine2,
    string State,
    string City,
    string Zip,
    string Country,
    string Phone1,
    string Phone2,
    string GovernmentIssuedId,
    string EftAccountId,
    string PrimaryCardHolder);

/// <summary>
/// One redisplay of the View Account screen: echoed account field + its state, the info and
/// error lines (WS-INFO-MSG / WS-RETURN-MSG) and the optional account / customer blocks.
/// </summary>
public record AccountViewResult(
    AccountViewOutcome Outcome,
    string AccountId,
    AccountFilterState FilterState,
    string InfoMessage,
    string ErrorMessage,
    AccountViewAccountDetails? Account,
    AccountViewCustomerDetails? Customer);
