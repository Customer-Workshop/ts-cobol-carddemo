// Account View screen (legacy COACTVWC / tran CAVW).
// REQ-F-010..REQ-F-015: account search validation and combined
// account + customer display.

import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api, ApiError, type AccountDetail } from '../api/client';
import { Screen, type ScreenMessage } from '../components/Screen';
import { validateAccountId } from '../validation';

export function AccountView() {
  const [searchParams] = useSearchParams();
  const [accountId, setAccountId] = useState(searchParams.get('accountId') ?? '');
  const [detail, setDetail] = useState<AccountDetail | null>(null);
  const [message, setMessage] = useState<ScreenMessage | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const err = validateAccountId(accountId);
    if (err) {
      setMessage({ text: err, kind: 'error' });
      setDetail(null);
      return;
    }
    try {
      const res = await api.getAccount(accountId.trim());
      setDetail(res);
      setMessage(null);
    } catch (apiErr) {
      setDetail(null);
      setMessage({
        text: apiErr instanceof ApiError ? apiErr.message : 'Unable to lookup Account...',
        kind: 'error',
      });
    }
  }

  return (
    <Screen tranId="CAVW" program="COACTVWC" title="View Account" message={message} backTo="/menu">
      <form onSubmit={onSubmit} className="actions" aria-label="Account search">
        <div className="field">
          <label htmlFor="accountId">Account Number</label>
          <input
            id="accountId"
            value={accountId}
            maxLength={11}
            onChange={(e) => setAccountId(e.target.value)}
          />
        </div>
        <button type="submit" className="btn">
          Search
        </button>
      </form>
      {detail && (
        <div className="form-grid" style={{ marginTop: 16 }}>
          <fieldset>
            <legend>Account</legend>
            <dl className="detail">
              <dt>Account ID</dt>
              <dd>{detail.account.id}</dd>
              <dt>Active</dt>
              <dd>{detail.account.activeStatus ? 'Y' : 'N'}</dd>
              <dt>Current Balance</dt>
              <dd>{detail.account.currentBalance}</dd>
              <dt>Credit Limit</dt>
              <dd>{detail.account.creditLimit}</dd>
              <dt>Cash Credit Limit</dt>
              <dd>{detail.account.cashCreditLimit}</dd>
              <dt>Open Date</dt>
              <dd>{detail.account.openDate}</dd>
              <dt>Expiry Date</dt>
              <dd>{detail.account.expirationDate}</dd>
              <dt>Reissue Date</dt>
              <dd>{detail.account.reissueDate ?? '-'}</dd>
              <dt>Cycle Credit</dt>
              <dd>{detail.account.currCycleCredit}</dd>
              <dt>Cycle Debit</dt>
              <dd>{detail.account.currCycleDebit}</dd>
              <dt>Group ID</dt>
              <dd>{detail.account.groupId ?? '-'}</dd>
            </dl>
          </fieldset>
          <fieldset>
            <legend>Customer</legend>
            <dl className="detail">
              <dt>Customer ID</dt>
              <dd>{detail.customer.id}</dd>
              <dt>Name</dt>
              <dd>
                {detail.customer.firstName} {detail.customer.middleName ?? ''} {detail.customer.lastName}
              </dd>
              <dt>Address</dt>
              <dd>
                {detail.customer.addressLine1} {detail.customer.addressLine2 ?? ''}
              </dd>
              <dt>State / Zip</dt>
              <dd>
                {detail.customer.stateCode} {detail.customer.zipCode}
              </dd>
              <dt>Country</dt>
              <dd>{detail.customer.countryCode}</dd>
              <dt>Phone 1</dt>
              <dd>{detail.customer.phoneNumber1 ?? '-'}</dd>
              <dt>SSN</dt>
              <dd>***-**-{detail.customer.ssn.slice(-4)}</dd>
              <dt>Date of Birth</dt>
              <dd>{detail.customer.dateOfBirth}</dd>
              <dt>FICO Score</dt>
              <dd>{detail.customer.ficoCreditScore}</dd>
              <dt>Primary Holder</dt>
              <dd>{detail.customer.primaryCardHolder ? 'Y' : 'N'}</dd>
            </dl>
          </fieldset>
        </div>
      )}
    </Screen>
  );
}
