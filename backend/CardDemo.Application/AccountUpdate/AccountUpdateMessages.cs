namespace CardDemo.Application.AccountUpdate;

/// <summary>
/// Exact COACTUPC message literals (app/cbl/COACTUPC.cbl:463-530). Every error text passes through
/// WS-RETURN-MSG PIC X(75) (:479), hence <see cref="Truncate"/>.
/// </summary>
public static class AccountUpdateMessages
{
    public const int ReturnMessageWidth = 75;

    public const string PromptForSearchKeys = "Enter or update id of account to update";
    public const string PromptForChanges = "Update account details presented above.";
    public const string PromptForConfirmation = "Changes validated.Press F5 to save";
    public const string ConfirmUpdateSuccess = "Changes committed to database";
    public const string InformFailure = "Changes unsuccessful. Please try again";

    public const string NoSearchCriteriaReceived = "No input received";
    public const string NoChangesDetected = "No change detected with respect to values fetched.";
    public const string AccountFilterNotValid = "Account Number if supplied must be a 11 digit Non-Zero Number";
    public const string CouldNotLockAccount = "Could not lock account record for update";
    public const string CouldNotLockCustomer = "Could not lock customer record for update";
    public const string DataChangedBeforeUpdate = "Record changed by some one else. Please review";
    public const string LockedButUpdateFailed = "Update of record failed";

    /// <summary>DFHRESP(NOTFND)=13 / RESP2 80 as rendered by MOVE PIC S9(9) COMP → PIC X(10) (:40-43, :3673-3674).</summary>
    public const string NotFoundResp = "000000013 ";
    public const string NotFoundResp2 = "000000080 ";

    public static string AccountNotInXref(string accountId) =>
        Truncate($"Account:{accountId} not found in Cross ref file.  Resp:{NotFoundResp} Reas:{NotFoundResp2}");

    public static string AccountNotInMaster(string accountId) =>
        Truncate($"Account:{accountId} not found in Acct Master file.Resp:{NotFoundResp} Reas:{NotFoundResp2}");

    public static string CustomerNotInMaster(string customerId) =>
        Truncate($"CustId:{customerId} not found in customer master.Resp: {NotFoundResp} REAS:{NotFoundResp2}");

    public static string Truncate(string message) =>
        message.Length <= ReturnMessageWidth ? message : message[..ReturnMessageWidth];
}
