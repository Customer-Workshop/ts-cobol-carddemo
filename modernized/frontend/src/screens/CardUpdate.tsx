// Card Update screen (legacy COCRDUPC / tran CCUP).
// REQ-F-300..REQ-F-334: fetch card, edit embossed name / status / expiry with
// validation (REQ-F-312..REQ-F-323), change detection (REQ-F-311), and
// PF5-style confirm-before-save (REQ-F-327/REQ-F-328).

import { useEffect, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { Card } from '@carddemo/shared';
import { api, ApiError } from '../api/client';
import { Screen, type ScreenMessage } from '../components/Screen';
import { isAlpha, validateCardNumber } from '../validation';

interface CardForm {
  embossedName: string;
  activeStatus: string;
  expiryMonth: string;
  expiryYear: string;
}

function toForm(card: Card): CardForm {
  const [year = '', month = ''] = card.expiryDate.split('-');
  return {
    embossedName: card.embossedName,
    activeStatus: card.activeStatus ? 'Y' : 'N',
    expiryMonth: month,
    expiryYear: year,
  };
}

function validate(form: CardForm): Record<string, string> {
  const errors: Record<string, string> = {};
  // REQ-F-312/REQ-F-313: name required, alphabets and spaces only
  if (form.embossedName.trim() === '') {
    errors.embossedName = 'Card name not provided';
  } else if (!isAlpha(form.embossedName)) {
    errors.embossedName = 'Card name can only contain alphabets and spaces';
  }
  // REQ-F-315/REQ-F-316: status Y or N
  if (!['Y', 'N'].includes(form.activeStatus.toUpperCase())) {
    errors.activeStatus = 'Card Active Status must be Y or N';
  }
  // REQ-F-318/REQ-F-319: month 1-12
  const month = Number(form.expiryMonth);
  if (!/^\d{1,2}$/.test(form.expiryMonth.trim()) || month < 1 || month > 12) {
    errors.expiryMonth = 'Card expiry month must be between 1 and 12';
  }
  // REQ-F-321/REQ-F-322: year 1950-2099
  const year = Number(form.expiryYear);
  if (!/^\d{4}$/.test(form.expiryYear.trim()) || year < 1950 || year > 2099) {
    errors.expiryYear = 'Card expiry year is invalid';
  }
  return errors;
}

export function CardUpdate() {
  const [searchParams] = useSearchParams();
  const [cardNumber, setCardNumber] = useState(searchParams.get('cardNumber') ?? '');
  const [card, setCard] = useState<Card | null>(null);
  const [form, setForm] = useState<CardForm | null>(null);
  const [original, setOriginal] = useState<CardForm | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [confirming, setConfirming] = useState(false);
  const [message, setMessage] = useState<ScreenMessage | null>(null);

  async function lookup(num: string) {
    const err = validateCardNumber(num);
    if (err) {
      setMessage({ text: err, kind: 'error' });
      return;
    }
    try {
      const res = await api.getCard(num.trim());
      setCard(res);
      const f = toForm(res);
      setForm(f);
      setOriginal(f);
      setConfirming(false);
      setFieldErrors({});
      // REQ-F-333: details shown, prompt for updates
      setMessage({ text: 'Update the card details and validate changes', kind: 'info' });
    } catch (apiErr) {
      setCard(null);
      setForm(null);
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

  function set(field: keyof CardForm, value: string) {
    if (!form) return;
    setForm({ ...form, [field]: value });
    setConfirming(false);
  }

  function onValidate(e: FormEvent) {
    e.preventDefault();
    if (!form || !original) return;
    // REQ-F-311: no-change detection (case-insensitive)
    if (JSON.stringify(form).toUpperCase() === JSON.stringify(original).toUpperCase()) {
      setMessage({ text: 'No change detected with respect to values fetched...', kind: 'error' });
      return;
    }
    const errors = validate(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      setMessage({ text: 'Correct the highlighted fields and retry', kind: 'error' });
      return;
    }
    setConfirming(true);
    // REQ-F-333: prompt to press PF5 to save
    setMessage({ text: 'Changes validated. Press F5=Save to commit the update', kind: 'info' });
  }

  async function onSave() {
    if (!form || !card) return;
    const lastDay = new Date(Number(form.expiryYear), Number(form.expiryMonth), 0).getDate();
    try {
      const res = await api.updateCard(card.cardNumber, {
        embossedName: form.embossedName.trim().toUpperCase(),
        activeStatus: form.activeStatus.toUpperCase() === 'Y',
        expiryDate: `${form.expiryYear}-${form.expiryMonth.padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`,
      });
      setCard(res);
      const f = toForm(res);
      setForm(f);
      setOriginal(f);
      setConfirming(false);
      setMessage({ text: 'Changes committed to database', kind: 'success' });
    } catch (apiErr) {
      setConfirming(false);
      setMessage({
        text: apiErr instanceof ApiError ? apiErr.message : 'Unable to update card...',
        kind: 'error',
      });
    }
  }

  return (
    <Screen tranId="CCUP" program="COCRDUPC" title="Update Credit Card" message={message} backTo="/cards">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void lookup(cardNumber);
        }}
        className="actions"
        aria-label="Card search"
      >
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
      {form && card && (
        <form onSubmit={onValidate} style={{ marginTop: 16 }} aria-label="Card update">
          <fieldset>
            <legend>
              Card {card.cardNumber} (Account {card.accountId})
            </legend>
            <div className="form-grid">
              <div className={`field ${fieldErrors.embossedName ? 'invalid' : ''}`}>
                <label htmlFor="embossedName">Name on Card</label>
                <input
                  id="embossedName"
                  value={form.embossedName}
                  maxLength={50}
                  onChange={(e) => set('embossedName', e.target.value)}
                />
                {fieldErrors.embossedName && <span className="field-error">{fieldErrors.embossedName}</span>}
              </div>
              <div className={`field ${fieldErrors.activeStatus ? 'invalid' : ''}`}>
                <label htmlFor="activeStatus">Active (Y/N)</label>
                <input
                  id="activeStatus"
                  value={form.activeStatus}
                  maxLength={1}
                  onChange={(e) => set('activeStatus', e.target.value)}
                />
                {fieldErrors.activeStatus && <span className="field-error">{fieldErrors.activeStatus}</span>}
              </div>
              <div className={`field ${fieldErrors.expiryMonth ? 'invalid' : ''}`}>
                <label htmlFor="expiryMonth">Expiry Month (1-12)</label>
                <input
                  id="expiryMonth"
                  value={form.expiryMonth}
                  maxLength={2}
                  onChange={(e) => set('expiryMonth', e.target.value)}
                />
                {fieldErrors.expiryMonth && <span className="field-error">{fieldErrors.expiryMonth}</span>}
              </div>
              <div className={`field ${fieldErrors.expiryYear ? 'invalid' : ''}`}>
                <label htmlFor="expiryYear">Expiry Year</label>
                <input
                  id="expiryYear"
                  value={form.expiryYear}
                  maxLength={4}
                  onChange={(e) => set('expiryYear', e.target.value)}
                />
                {fieldErrors.expiryYear && <span className="field-error">{fieldErrors.expiryYear}</span>}
              </div>
            </div>
          </fieldset>
          <div className="actions">
            <button type="submit" className="btn secondary">
              Validate Changes
            </button>
            {confirming && (
              <button type="button" className="btn" onClick={onSave}>
                F5=Save
              </button>
            )}
          </div>
        </form>
      )}
    </Screen>
  );
}
