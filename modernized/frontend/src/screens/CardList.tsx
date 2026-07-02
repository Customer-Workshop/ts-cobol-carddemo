// Card List screen (legacy COCRDLIC / tran CCLI).
// REQ-F-125..REQ-F-160: paginated list (7 rows per page like legacy),
// account filter, and S/U row selection routing to view/update.

import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import type { Card } from '@carddemo/shared';
import { api, ApiError, type Paged } from '../api/client';
import { Screen, type ScreenMessage } from '../components/Screen';

const PAGE_SIZE = 7;

export function CardList() {
  const navigate = useNavigate();
  const [accountFilter, setAccountFilter] = useState('');
  const [appliedFilter, setAppliedFilter] = useState('');
  const [page, setPage] = useState(1);
  const [result, setResult] = useState<Paged<Card> | null>(null);
  const [message, setMessage] = useState<ScreenMessage | null>(null);

  const load = useCallback(async (p: number, filter: string) => {
    try {
      const res = await api.listCards({
        accountId: filter || undefined,
        page: p,
        pageSize: PAGE_SIZE,
      });
      setResult(res);
      setPage(res.page);
      if (res.items.length === 0) {
        // REQ-F-137-style: no records for the search condition
        setMessage({ text: 'Did not find cards for this search condition', kind: 'error' });
      } else {
        setMessage(null);
      }
    } catch (err) {
      setMessage({
        text: err instanceof ApiError ? err.message : 'Unable to lookup cards...',
        kind: 'error',
      });
    }
  }, []);

  useEffect(() => {
    void load(1, '');
  }, [load]);

  function onFilter(e: FormEvent) {
    e.preventDefault();
    // REQ-F-127: account filter must be 11-digit numeric when provided
    if (accountFilter.trim() !== '' && !/^\d{11}$/.test(accountFilter.trim())) {
      setMessage({ text: 'Account number must be an 11-digit number', kind: 'error' });
      return;
    }
    setAppliedFilter(accountFilter.trim());
    void load(1, accountFilter.trim());
  }

  // REQ-F-149/REQ-F-150: page boundary messaging
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
    <Screen tranId="CCLI" program="COCRDLIC" title="List Credit Cards" message={message} backTo="/menu">
      <form onSubmit={onFilter} className="actions" aria-label="Card filter">
        <div className="field">
          <label htmlFor="accountFilter">Account Number</label>
          <input
            id="accountFilter"
            value={accountFilter}
            maxLength={11}
            onChange={(e) => setAccountFilter(e.target.value)}
          />
        </div>
        <button type="submit" className="btn">
          Filter
        </button>
      </form>
      <table className="data" style={{ marginTop: 16 }}>
        <thead>
          <tr>
            <th>Account</th>
            <th>Card Number</th>
            <th>Embossed Name</th>
            <th>Active</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          {result?.items.map((card) => (
            <tr key={card.cardNumber}>
              <td>{card.accountId}</td>
              <td>{card.cardNumber}</td>
              <td>{card.embossedName}</td>
              <td>{card.activeStatus ? 'Y' : 'N'}</td>
              <td>
                {/* REQ-F-153..REQ-F-156: S=view, U=update row selection */}
                <button
                  type="button"
                  className="link-btn"
                  onClick={() => navigate(`/cards/view?cardNumber=${card.cardNumber}`)}
                >
                  S=View
                </button>{' '}
                <button
                  type="button"
                  className="link-btn"
                  onClick={() => navigate(`/cards/update?cardNumber=${card.cardNumber}`)}
                >
                  U=Update
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
