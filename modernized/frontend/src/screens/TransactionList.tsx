// Transaction List screen (legacy COTRN00C / tran CT00).
// REQ-F-389..REQ-F-409: paginated list (10 rows like legacy), card/account
// filter, S row selection to detail, boundary messages.

import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import type { Transaction } from '@carddemo/shared';
import { api, ApiError, type Paged } from '../api/client';
import { Screen, type ScreenMessage } from '../components/Screen';

const PAGE_SIZE = 10;

function formatDate(ts: string): string {
  const d = ts.slice(0, 10);
  const [y = '', m = '', day = ''] = d.split('-');
  return `${m}/${day}/${y.slice(2)}`;
}

export function TransactionList() {
  const navigate = useNavigate();
  const [cardFilter, setCardFilter] = useState('');
  const [appliedFilter, setAppliedFilter] = useState('');
  const [page, setPage] = useState(1);
  const [result, setResult] = useState<Paged<Transaction> | null>(null);
  const [message, setMessage] = useState<ScreenMessage | null>(null);

  const load = useCallback(async (p: number, filter: string) => {
    try {
      const res = await api.listTransactions({
        cardNumber: filter || undefined,
        page: p,
        pageSize: PAGE_SIZE,
      });
      setResult(res);
      setPage(res.page);
      setMessage(null);
    } catch (err) {
      setMessage({
        text: err instanceof ApiError ? err.message : 'Unable to lookup transaction...',
        kind: 'error',
      });
    }
  }, []);

  useEffect(() => {
    void load(1, '');
  }, [load]);

  function onFilter(e: FormEvent) {
    e.preventDefault();
    // REQ-F-407: numeric-only search field
    if (cardFilter.trim() !== '' && !/^\d+$/.test(cardFilter.trim())) {
      setMessage({ text: 'Card Number must be Numeric ...', kind: 'error' });
      return;
    }
    setAppliedFilter(cardFilter.trim());
    void load(1, cardFilter.trim());
  }

  // REQ-F-402/REQ-F-403: top/bottom boundary messages
  function prevPage() {
    if (page <= 1) {
      setMessage({ text: 'You are already at the top of the page...', kind: 'error' });
      return;
    }
    void load(page - 1, appliedFilter);
  }

  function nextPage() {
    if (result && page >= result.totalPages) {
      setMessage({ text: 'You are already at the bottom of the page...', kind: 'error' });
      return;
    }
    void load(page + 1, appliedFilter);
  }

  return (
    <Screen tranId="CT00" program="COTRN00C" title="List Transactions" message={message} backTo="/menu">
      <form onSubmit={onFilter} className="actions" aria-label="Transaction filter">
        <div className="field">
          <label htmlFor="cardFilter">Card Number</label>
          <input
            id="cardFilter"
            value={cardFilter}
            maxLength={16}
            onChange={(e) => setCardFilter(e.target.value)}
          />
        </div>
        <button type="submit" className="btn">
          Search
        </button>
      </form>
      <table className="data" style={{ marginTop: 16 }}>
        <thead>
          <tr>
            <th>Transaction ID</th>
            <th>Date</th>
            <th>Description</th>
            <th className="num">Amount</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          {result?.items.map((t) => (
            <tr key={t.id}>
              <td>{t.id}</td>
              <td>{formatDate(t.originalTs)}</td>
              <td>{t.description}</td>
              <td className="num">{t.amount}</td>
              <td>
                {/* REQ-F-409: selection routes to the detail screen */}
                <button
                  type="button"
                  className="link-btn"
                  onClick={() => navigate(`/transactions/view?transactionId=${t.id}`)}
                >
                  S=View
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="pager">
        <button type="button" className="btn secondary" onClick={prevPage}>
          F7=Backward
        </button>
        <span>
          Page {page} of {result?.totalPages ?? 1}
        </span>
        <button type="button" className="btn secondary" onClick={nextPage}>
          F8=Forward
        </button>
      </div>
    </Screen>
  );
}
