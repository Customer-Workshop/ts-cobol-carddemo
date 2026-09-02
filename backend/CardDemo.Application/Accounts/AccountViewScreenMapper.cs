using CardDemo.Domain.Accounts;
using CardDemo.Domain.Customers;
using static CardDemo.Application.Accounts.LegacyScreenFormat;

namespace CardDemo.Application.Accounts;

/// <summary>
/// Port of 1200-SETUP-SCREEN-VARS (app/cbl/COACTVWC.cbl:471-523): record fields → map CACTVWA
/// output fields, applying the BMS PICOUT edits and the field widths of app/bms/COACTVW.bms.
/// </summary>
public static class AccountViewScreenMapper
{
    public static AccountViewAccountDetails ToAccountDetails(Account account) => new(
        ActiveStatus: Fit(account.ActiveStatus, 1),
        OpenDate: IsoDate(account.OpenDate),
        CreditLimit: EditedAmount(account.CreditLimit),
        ExpirationDate: IsoDate(account.ExpirationDate),
        CashCreditLimit: EditedAmount(account.CashCreditLimit),
        ReissueDate: IsoDate(account.ReissueDate),
        CurrentBalance: EditedAmount(account.CurrentBalance),
        CurrentCycleCredit: EditedAmount(account.CurrentCycleCredit),
        GroupId: Fit(account.GroupId, 10),
        CurrentCycleDebit: EditedAmount(account.CurrentCycleDebit));

    public static AccountViewCustomerDetails ToCustomerDetails(Customer customer)
    {
        var ssn = customer.Ssn.PadLeft(9, '0');
        return new AccountViewCustomerDetails(
            CustomerId: customer.CustomerId.PadLeft(9, '0'),
            Ssn: $"{ssn[..3]}-{ssn[3..5]}-{ssn[5..9]}",
            DateOfBirth: IsoDate(customer.DateOfBirth),
            FicoScore: customer.FicoCreditScore.ToString("D3"),
            FirstName: Fit(customer.FirstName, 25),
            MiddleName: Fit(customer.MiddleName, 25),
            LastName: Fit(customer.LastName, 25),
            AddressLine1: Fit(customer.AddressLine1, 50),
            AddressLine2: Fit(customer.AddressLine2, 50),
            State: Fit(customer.AddressStateCode, 2),
            City: Fit(customer.AddressLine3, 50),
            Zip: Fit(customer.AddressZip, 5),
            Country: Fit(customer.AddressCountryCode, 3),
            Phone1: Fit(customer.PhoneNumber1, 13),
            Phone2: Fit(customer.PhoneNumber2, 13),
            GovernmentIssuedId: Fit(customer.GovernmentIssuedId, 20),
            EftAccountId: Fit(customer.EftAccountId, 10),
            PrimaryCardHolder: Fit(customer.PrimaryCardHolderIndicator, 1));
    }
}
