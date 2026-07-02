// Bill Pay screen (legacy COBIL00C / tran CB00).
// REQ-F-096..REQ-F-124: account lookup, current balance display, and
// confirmed full-balance payment.

import { useState, type FormEvent } from 'react';
import { api, ApiError, type AccountDetail } from '../api/client';
import { Screen, type ScreenMessage } from '../components/Screen';
import { validateAccountId } from '../validation';

export function BillPay() {
  const [accountId, setAccountId] = useState('');
  const [detail, setDetail] = useState<AccountDetail | null>(null);
  const [message, setMessage] = useState<ScreenMessage | null>(null);
  const [paid, setPaid] = useState(false);

  async function onLookup(e: FormEvent) {
    e.preventDefault();
    // REQ-F-100: account ID required and numeric
    const err = validateAccountId(accountId);
    if (err) {
      setMessage({ text: 'Acct ID can NOT be empty...', kind: 'error' });
      setDetail(null);
      return;
    }
    try {
      const res = await api.getAccount(accountId.trim());
      setDetail(res);
      setPaid(false);
      if (Number(res.account.currentBalance) <= 0) {
        // REQ-F-108: nothing to pay
        setMessage({ text: 'You have nothing to pay...', kind: 'error' });
      } else {
        // REQ-F-103: confirmation prompt before payment
        setMessage({ text: 'Confirm to make a bill payment of the full balance...', kind: 'info' });
      }
    } catch (apiErr) {
      setDetail(null);
      setMessage({
        text: apiErr instanceof ApiError ? apiErr.message : 'Account ID NOT found...',
        kind: 'error',
      });
    }
  }

  // REQ-F-104/REQ-F-110..REQ-F-117: confirmed payment creates the transaction
  // and zeroes the balance
  async function onConfirm() {
    if (!detail) return;
    try {
      const res = await api.payBill(detail.account.id);
      setDetail({ ...detail, account: res.account });
      setPaid(true);
      setMessage({
        text: `Payment successful. Your Transaction ID is ${res.transaction.id}.`,
        kind: 'success',
      });
    } catch (apiErr) {
      setMessage({
        text: apiErr instanceof ApiError ? apiErr.message : 'Unable to process payment...',
        kind: 'error',
      });
    }
  }

  // REQ-F-105: decline resets the input fields
  function onDecline() {
    setAccountId('');
    setDetail(null);
    setPaid(false);
    setMessage(null);
  }

  const balance = detail ? Number(detail.account.currentBalance) : 0;

  return (
    <Screen tranId="CB00" program="COBIL00C" title="Bill Payment" message={message} backTo="/menu">
      <form onSubmit={onLookup} className="actions" aria-label="Bill pay lookup">
        <div className="field">
          <label htmlFor="accountId">Account ID</label>
          <input
            id="accountId"
            value={accountId}
            maxLength={11}
            onChange={(e) => setAccountId(e.target.value)}
          />
        </div>
        <button type="submit" className="btn">
          Get Balance
        </button>
      </form>
      {detail && (
        <div style={{ marginTop: 16 }}>
          <dl className="detail">
            <dt>Account ID</dt>
            <dd>{detail.account.id}</dd>
            <dt>Current Balance</dt>
            <dd>{detail.account.currentBalance}</dd>
          </dl>
          {!paid && balance > 0 && (
            <div className="actions">
              <button type="button" className="btn" onClick={onConfirm}>
                Confirm Payment (Y)
              </button>
              <button type="button" className="btn secondary" onClick={onDecline}>
                Decline (N)
              </button>
            </div>
          )}
        </div>
      )}
    </Screen>
  );
}
