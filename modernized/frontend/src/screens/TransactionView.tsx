// Transaction View screen (legacy COTRN01C / tran CT01).
// REQ-F-414..REQ-F-426, REQ-F-433..REQ-F-443: lookup by transaction ID and
// full detail display with clear (PF4-equivalent).

import { useEffect, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { Transaction } from '@carddemo/shared';
import { api, ApiError } from '../api/client';
import { Screen, type ScreenMessage } from '../components/Screen';

export function TransactionView() {
  const [searchParams] = useSearchParams();
  const [transactionId, setTransactionId] = useState(searchParams.get('transactionId') ?? '');
  const [transaction, setTransaction] = useState<Transaction | null>(null);
  const [message, setMessage] = useState<ScreenMessage | null>(null);

  async function lookup(id: string) {
    // REQ-F-435: transaction ID must not be empty
    if (id.trim() === '') {
      setMessage({ text: 'Tran ID can NOT be empty...', kind: 'error' });
      return;
    }
    try {
      const res = await api.getTransaction(id.trim());
      setTransaction(res);
      setMessage(null);
    } catch (err) {
      setTransaction(null);
      // REQ-F-439/REQ-F-440
      setMessage({
        text: err instanceof ApiError ? err.message : 'Unable to lookup Transaction...',
        kind: 'error',
      });
    }
  }

  useEffect(() => {
    // REQ-F-443: process a pre-selected transaction ID immediately
    const preset = searchParams.get('transactionId');
    if (preset) void lookup(preset);
  }, []);

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    void lookup(transactionId);
  }

  // REQ-F-422: PF4 clears all fields
  function onClear() {
    setTransactionId('');
    setTransaction(null);
    setMessage(null);
  }

  return (
    <Screen tranId="CT01" program="COTRN01C" title="View Transaction" message={message} backTo="/transactions">
      <form onSubmit={onSubmit} className="actions" aria-label="Transaction search">
        <div className="field">
          <label htmlFor="transactionId">Transaction ID</label>
          <input
            id="transactionId"
            value={transactionId}
            maxLength={16}
            onChange={(e) => setTransactionId(e.target.value)}
          />
        </div>
        <button type="submit" className="btn">
          Search
        </button>
        <button type="button" className="btn secondary" onClick={onClear}>
          F4=Clear
        </button>
      </form>
      {transaction && (
        <dl className="detail" style={{ marginTop: 16 }}>
          <dt>Transaction ID</dt>
          <dd>{transaction.id}</dd>
          <dt>Card Number</dt>
          <dd>{transaction.cardNumber}</dd>
          <dt>Type Code</dt>
          <dd>{transaction.typeCode}</dd>
          <dt>Category Code</dt>
          <dd>{transaction.categoryCode}</dd>
          <dt>Source</dt>
          <dd>{transaction.source}</dd>
          <dt>Amount</dt>
          <dd>{transaction.amount}</dd>
          <dt>Description</dt>
          <dd>{transaction.description}</dd>
          <dt>Original Date</dt>
          <dd>{transaction.originalTs}</dd>
          <dt>Processed Date</dt>
          <dd>{transaction.processedTs}</dd>
          <dt>Merchant ID</dt>
          <dd>{transaction.merchantId}</dd>
          <dt>Merchant Name</dt>
          <dd>{transaction.merchantName}</dd>
          <dt>Merchant City</dt>
          <dd>{transaction.merchantCity}</dd>
          <dt>Merchant Zip</dt>
          <dd>{transaction.merchantZip}</dd>
        </dl>
      )}
    </Screen>
  );
}
