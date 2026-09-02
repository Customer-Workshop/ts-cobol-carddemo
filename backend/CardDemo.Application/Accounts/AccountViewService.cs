using CardDemo.Application.Cards;
using CardDemo.Application.Customers;
using CardDemo.Domain.Accounts;
using CardDemo.Domain.Cards;
using CardDemo.Domain.Customers;
using static CardDemo.Application.Accounts.LegacyScreenFormat;

namespace CardDemo.Application.Accounts;

/// <summary>
/// Port of COACTVWC (app/cbl/COACTVWC.cbl): 2210-EDIT-ACCOUNT input edit, then the
/// 9000-READ-ACCT chain CXACAIX → ACCTDAT → CUSTDAT with the exact WS-RETURN-MSG texts
/// (FR-S02-01..10, 13). Every outcome is a redisplay of the same screen.
/// </summary>
public class AccountViewService(
    ICardXrefRepository cardXrefRepository,
    IAccountRepository accountRepository,
    ICustomerRepository customerRepository)
{
    public const string PromptMessage = "Enter or update id of account to display";
    public const string NoInputMessage = "No input received";
    public const string AccountFilterMessage = "Account Filter must  be a non-zero 11 digit number";
    public const string BlankAccountEcho = "*";

    public const int AccountIdLength = 11;
    private const int ReturnMessageLength = 75;

    private const int NotFoundResp = 13;
    private const int NotFoundResp2 = 80;
    private const int StoreErrorResp = 17;
    private const int StoreErrorResp2 = 120;

    private const string XrefFile = "CXACAIX ";
    private const string AccountFile = "ACCTDAT ";
    private const string CustomerFile = "CUSTDAT ";

    public static string AccountNotInXrefMessage(string accountId) => StringInto(ReturnMessageLength,
        "Account:", Pad(accountId, 11), " not found in", " Cross ref file.  Resp:", RespCode(NotFoundResp), " Reas:", RespCode(NotFoundResp2));

    public static string AccountNotInMasterMessage(string accountId) => StringInto(ReturnMessageLength,
        "Account:", Pad(accountId, 11), " not found in", " Acct Master file.Resp:", RespCode(NotFoundResp), " Reas:", RespCode(NotFoundResp2));

    public static string CustomerNotFoundMessage(string customerId) => StringInto(ReturnMessageLength,
        "CustId:", Pad(customerId, 9), " not found", " in customer master.Resp: ", RespCode(NotFoundResp), " REAS:", RespCode(NotFoundResp2));

    public static string FileErrorMessage(string file) => StringInto(ReturnMessageLength,
        "File Error: ", "READ    ", " on ", Pad(file, 9), " returned RESP ", RespCode(StoreErrorResp), ",RESP2 ", RespCode(StoreErrorResp2), "     ");

    public static string XrefFileErrorMessage => FileErrorMessage(XrefFile);
    public static string AccountFileErrorMessage => FileErrorMessage(AccountFile);
    public static string CustomerFileErrorMessage => FileErrorMessage(CustomerFile);

    /// <summary>First send of the map (COACTVWC.cbl:353-360): empty field, prompt, no data.</summary>
    public static AccountViewResult InitialScreen() =>
        new(AccountViewOutcome.Initial, string.Empty, AccountFilterState.Blank, PromptMessage, string.Empty, null, null);

    public async Task<AccountViewResult> ViewAsync(string? accountId, CancellationToken cancellationToken = default)
    {
        var typed = (accountId ?? string.Empty).TrimEnd();

        if (typed.Length == 0 || typed == BlankAccountEcho)
        {
            return Redisplay(AccountViewOutcome.NoInput, BlankAccountEcho, AccountFilterState.Blank, NoInputMessage);
        }

        if (typed.Length != AccountIdLength || !typed.All(char.IsAsciiDigit) || typed.All(c => c == '0'))
        {
            return Redisplay(AccountViewOutcome.InvalidFilter, typed, AccountFilterState.Invalid, AccountFilterMessage);
        }

        CardXref? xref;
        try
        {
            xref = await cardXrefRepository.GetFirstByAccountIdAsync(typed, cancellationToken);
        }
        catch (Exception)
        {
            return Redisplay(AccountViewOutcome.StoreError, typed, AccountFilterState.Invalid, XrefFileErrorMessage);
        }
        if (xref is null)
        {
            return Redisplay(AccountViewOutcome.AccountNotInXref, typed, AccountFilterState.Invalid, AccountNotInXrefMessage(typed));
        }

        Account? account;
        try
        {
            account = await accountRepository.GetByIdAsync(typed, cancellationToken);
        }
        catch (Exception)
        {
            return Redisplay(AccountViewOutcome.StoreError, typed, AccountFilterState.Invalid, AccountFileErrorMessage);
        }
        if (account is null)
        {
            return Redisplay(AccountViewOutcome.AccountNotInMaster, typed, AccountFilterState.Invalid, AccountNotInMasterMessage(typed));
        }

        var accountDetails = AccountViewScreenMapper.ToAccountDetails(account);

        Customer? customer;
        try
        {
            customer = await customerRepository.GetByIdAsync(xref.CustomerId, cancellationToken);
        }
        catch (Exception)
        {
            return Redisplay(AccountViewOutcome.StoreError, typed, AccountFilterState.Valid, CustomerFileErrorMessage, accountDetails);
        }
        if (customer is null)
        {
            return Redisplay(AccountViewOutcome.CustomerNotFound, typed, AccountFilterState.Valid, CustomerNotFoundMessage(xref.CustomerId), accountDetails);
        }

        return Redisplay(AccountViewOutcome.Found, typed, AccountFilterState.Valid, string.Empty, accountDetails,
            AccountViewScreenMapper.ToCustomerDetails(customer));
    }

    private static AccountViewResult Redisplay(
        AccountViewOutcome outcome,
        string accountEcho,
        AccountFilterState filterState,
        string errorMessage,
        AccountViewAccountDetails? account = null,
        AccountViewCustomerDetails? customer = null) =>
        new(outcome, accountEcho, filterState, PromptMessage, errorMessage, account, customer);
}
