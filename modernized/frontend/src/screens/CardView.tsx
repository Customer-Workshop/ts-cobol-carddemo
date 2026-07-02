// Card View screen (legacy COCRDSLC / tran CCDL).
// REQ-F-200..REQ-F-232: card search by card number (with optional account
// context) and detail display.

import { useEffect, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { Card } from '@carddemo/shared';
import { api, ApiError } from '../api/client';
import { Screen, type ScreenMessage } from '../components/Screen';
import { validateCardNumber } from '../validation';

export function CardView() {
  const [searchParams] = useSearchParams();
  const [cardNumber, setCardNumber] = useState(searchParams.get('cardNumber') ?? '');
  const [card, setCard] = useState<Card | null>(null);
  const [message, setMessage] = useState<ScreenMessage | null>(null);

  async function lookup(num: string) {
    const err = validateCardNumber(num);
    if (err) {
      setMessage({ text: err, kind: 'error' });
      setCard(null);
      return;
    }
    try {
      const res = await api.getCard(num.trim());
      setCard(res);
      setMessage(null);
    } catch (apiErr) {
      setCard(null);
      setMessage({
        text: apiErr instanceof ApiError ? apiErr.message : 'Unable to lookup card...',
        kind: 'error',
      });
    }
  }

  useEffect(() => {
    const preset = searchParams.get('cardNumber');
    if (preset) void lookup(preset);
  }, []);

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    void lookup(cardNumber);
  }

  return (
    <Screen tranId="CCDL" program="COCRDSLC" title="View Credit Card" message={message} backTo="/cards">
      <form onSubmit={onSubmit} className="actions" aria-label="Card search">
        <div className="field">
          <label htmlFor="cardNumber">Card Number</label>
          <input
            id="cardNumber"
            value={cardNumber}
            maxLength={16}
            onChange={(e) => setCardNumber(e.target.value)}
          />
        </div>
        <button type="submit" className="btn">
          Search
        </button>
      </form>
      {card && (
        <dl className="detail" style={{ marginTop: 16 }}>
          <dt>Card Number</dt>
          <dd>{card.cardNumber}</dd>
          <dt>Account ID</dt>
          <dd>{card.accountId}</dd>
          <dt>Embossed Name</dt>
          <dd>{card.embossedName}</dd>
          <dt>Expiry Date</dt>
          <dd>{card.expiryDate}</dd>
          <dt>Active</dt>
          <dd>{card.activeStatus ? 'Y' : 'N'}</dd>
        </dl>
      )}
    </Screen>
  );
}
