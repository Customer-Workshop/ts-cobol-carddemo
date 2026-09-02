using CardDemo.Application.Accounts;
using CardDemo.Application.Cards;
using CardDemo.Application.Customers;

namespace CardDemo.Application.AccountUpdate;

/// <summary>
/// Port of COACTUPC (transaction CAUP, app/cbl/COACTUPC.cbl): 9000-READ-ACCT lookup chain,
/// 1200-EDIT-MAP-INPUTS edits and 9600-WRITE-PROCESSING, driven by 2000-DECIDE-ACTION (:2560-2640).
/// Screen state itself lives in the caller (COMMAREA parity), so each call receives the fetched snapshot.
/// </summary>
public class AccountUpdateService(
    ICardXrefRepository cardXrefs,
    IAccountRepository accounts,
    ICustomerRepository customers,
    IAccountUpdateWriter writer,
    TimeProvider timeProvider)
{
    /// <summary>ENTER on the search prompt (ACUP-DETAILS-NOT-FETCHED) and F12 re-read (:2568-2580).</summary>
    public async Task<AccountUpdateLookupResult> LookupAsync(string? accountId, CancellationToken cancellationToken = default)
    {
        var id = (accountId ?? string.Empty).Trim();
        var searchError = AccountUpdateEditRules.EditSearchAccountId(id);
        if (searchError is not null)
        {
            return new AccountUpdateLookupResult(AccountUpdateOutcome.SearchError, AccountUpdateMessages.PromptForSearchKeys, searchError, null);
        }

        var xref = await cardXrefs.GetFirstByAccountIdAsync(id, cancellationToken);
        if (xref is null)
        {
            return SearchError(AccountUpdateMessages.AccountNotInXref(id));
        }

        var account = await accounts.GetByIdAsync(id, cancellationToken);
        if (account is null)
        {
            return SearchError(AccountUpdateMessages.AccountNotInMaster(id));
        }

        var customer = await customers.GetByIdAsync(xref.CustomerId, cancellationToken);
        if (customer is null)
        {
            return SearchError(AccountUpdateMessages.CustomerNotInMaster(xref.CustomerId));
        }

        return new AccountUpdateLookupResult(
            AccountUpdateOutcome.Details,
            AccountUpdateMessages.PromptForChanges,
            null,
            AccountUpdateFieldMapper.FromEntities(account, customer));
    }

    /// <summary>ENTER with details on screen (ACUP-SHOW-DETAILS / ACUP-CHANGES-NOT-OK).</summary>
    public AccountUpdateValidateResult Validate(AccountUpdateFields original, AccountUpdateFields updated)
    {
        var outcome = AccountUpdateEditRules.EditChanges(original, updated, Today());
        return ToValidateResult(outcome);
    }

    /// <summary>F5 in ACUP-CHANGES-OK-NOT-CONFIRMED (:2600-2612).</summary>
    public async Task<AccountUpdateSaveResult> SaveAsync(
        AccountUpdateFields original,
        AccountUpdateFields updated,
        CancellationToken cancellationToken = default)
    {
        var edit = AccountUpdateEditRules.EditChanges(original, updated, Today());
        if (!edit.IsValid)
        {
            var validate = ToValidateResult(edit);
            return new AccountUpdateSaveResult(validate.Outcome, validate.InfoMessage, validate.ErrorMessage, validate.InvalidFields);
        }

        var status = await writer.WriteAsync(
            original.AccountId,
            original.CustomerId,
            (account, customer) => AccountUpdateEditRules.SnapshotMatches(original, AccountUpdateFieldMapper.FromEntities(account, customer)),
            (account, customer) =>
            {
                AccountUpdateFieldMapper.ApplyToAccount(account, updated, edit.Parsed!);
                AccountUpdateFieldMapper.ApplyToCustomer(customer, updated, edit.Parsed!);
            },
            cancellationToken);

        return status switch
        {
            AccountUpdateWriteStatus.Updated => new AccountUpdateSaveResult(
                AccountUpdateOutcome.Committed, AccountUpdateMessages.ConfirmUpdateSuccess, null, []),
            AccountUpdateWriteStatus.AccountLockFailed => new AccountUpdateSaveResult(
                AccountUpdateOutcome.Failed, AccountUpdateMessages.InformFailure, AccountUpdateMessages.CouldNotLockAccount, []),
            AccountUpdateWriteStatus.CustomerLockFailed => new AccountUpdateSaveResult(
                AccountUpdateOutcome.Failed, AccountUpdateMessages.InformFailure, AccountUpdateMessages.CouldNotLockCustomer, []),
            AccountUpdateWriteStatus.ChangedBeforeUpdate => new AccountUpdateSaveResult(
                AccountUpdateOutcome.ChangedByOther, AccountUpdateMessages.PromptForChanges, AccountUpdateMessages.DataChangedBeforeUpdate, []),
            _ => new AccountUpdateSaveResult(
                AccountUpdateOutcome.Failed, AccountUpdateMessages.InformFailure, AccountUpdateMessages.LockedButUpdateFailed, [])
        };
    }

    private static AccountUpdateValidateResult ToValidateResult(AccountUpdateEditOutcome outcome)
    {
        if (!outcome.ChangesDetected)
        {
            return new AccountUpdateValidateResult(
                AccountUpdateOutcome.NoChanges, AccountUpdateMessages.PromptForChanges, outcome.ErrorMessage, []);
        }
        if (!outcome.IsValid)
        {
            return new AccountUpdateValidateResult(
                AccountUpdateOutcome.Invalid, AccountUpdateMessages.PromptForChanges, outcome.ErrorMessage, outcome.InvalidFields);
        }
        return new AccountUpdateValidateResult(
            AccountUpdateOutcome.Confirm, AccountUpdateMessages.PromptForConfirmation, null, []);
    }

    private static AccountUpdateLookupResult SearchError(string message) =>
        new(AccountUpdateOutcome.SearchError, AccountUpdateMessages.PromptForSearchKeys, message, null);

    private DateOnly Today() => DateOnly.FromDateTime(timeProvider.GetLocalNow().DateTime);
}
