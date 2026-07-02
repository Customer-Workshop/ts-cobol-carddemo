// Add Transaction screen (legacy COTRN02C / tran CT02).
// REQ-F-474..REQ-F-497: field validation, Y/N confirmation before add,
// success message with new transaction ID, PF4-style clear.

import { useState, type FormEvent } from 'react';
import { api, ApiError } from '../api/client';
import { Screen, type ScreenMessage } from '../components/Screen';
import { isMoney, validateIsoDate } from '../validation';

interface TxnForm {
  accountId: string;
  cardNumber: string;
  typeCode: string;
  categoryCode: string;
  source: string;
  description: string;
  amount: string;
  originalDate: string;
  merchantId: string;
  merchantName: string;
  merchantCity: string;
  merchantZip: string;
}

const EMPTY: TxnForm = {
  accountId: '',
  cardNumber: '',
  typeCode: '',
  categoryCode: '',
  source: '',
  description: '',
  amount: '',
  originalDate: '',
  merchantId: '',
  merchantName: '',
  merchantCity: '',
  merchantZip: '',
};

// REQ-F-487/REQ-F-488/REQ-F-489: field-level edits
function validate(form: TxnForm): Record<string, string> {
  const errors: Record<string, string> = {};
  if (form.accountId.trim() === '' && form.cardNumber.trim() === '') {
    errors.accountId = 'Account or Card Number must be entered...';
  }
  if (form.accountId.trim() !== '' && !/^\d{11}$/.test(form.accountId.trim())) {
    errors.accountId = 'Account ID must be an 11-digit number';
  }
  if (form.cardNumber.trim() !== '' && !/^\d{16}$/.test(form.cardNumber.trim())) {
    errors.cardNumber = 'Card Number must be a 16-digit number';
  }
  if (!/^\d{2}$/.test(form.typeCode.trim())) errors.typeCode = 'Type CD must be 2-digit numeric...';
  if (!/^\d{1,4}$/.test(form.categoryCode.trim())) errors.categoryCode = 'Category CD must be numeric...';
  if (form.source.trim() === '') errors.source = 'Source can NOT be empty...';
  if (form.description.trim() === '') errors.description = 'Description can NOT be empty...';
  if (!isMoney(form.amount)) errors.amount = 'Amount must be in format -99999999.99';
  const dateErr =
    form.originalDate.trim() === '' ? 'Orig Date can NOT be empty...' : validateIsoDate(form.originalDate);
  if (dateErr) errors.originalDate = dateErr;
  if (!/^\d{1,9}$/.test(form.merchantId.trim())) errors.merchantId = 'Merchant ID must be numeric...';
  if (form.merchantName.trim() === '') errors.merchantName = 'Merchant Name can NOT be empty...';
  if (form.merchantCity.trim() === '') errors.merchantCity = 'Merchant City can NOT be empty...';
  if (form.merchantZip.trim() === '') errors.merchantZip = 'Merchant Zip can NOT be empty...';
  return errors;
}

export function TransactionAdd() {
  const [form, setForm] = useState<TxnForm>(EMPTY);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [confirming, setConfirming] = useState(false);
  const [message, setMessage] = useState<ScreenMessage | null>(null);

  function set(field: keyof TxnForm, value: string) {
    setForm((f) => ({ ...f, [field]: value }));
    setConfirming(false);
  }

  function onValidate(e: FormEvent) {
    e.preventDefault();
    const errors = validate(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      setMessage({ text: 'Correct the highlighted fields and retry', kind: 'error' });
      setConfirming(false);
      return;
    }
    // REQ-F-490: explicit Y confirmation is required before adding
    setConfirming(true);
    setMessage({ text: 'Press Confirm (Y) to add this transaction...', kind: 'info' });
  }

  async function onConfirm() {
    let cardNumber = form.cardNumber.trim();
    try {
      // REQ-F-477: resolve card from account when only account provided
      if (cardNumber === '') {
        const cards = await api.listCards({ accountId: form.accountId.trim(), pageSize: 1 });
        const first = cards.items[0];
        if (!first) {
          setFieldErrors({ accountId: 'Account ID NOT found...' });
          setMessage({ text: 'Account ID NOT found...', kind: 'error' });
          setConfirming(false);
          return;
        }
        cardNumber = first.cardNumber;
      }
      const created = await api.createTransaction({
        typeCode: form.typeCode.trim(),
        categoryCode: Number(form.categoryCode),
        source: form.source.trim(),
        description: form.description.trim(),
        amount: form.amount.trim(),
        merchantId: form.merchantId.trim(),
        merchantName: form.merchantName.trim(),
        merchantCity: form.merchantCity.trim(),
        merchantZip: form.merchantZip.trim(),
        cardNumber,
        originalTs: `${form.originalDate.trim()}T00:00:00.000Z`,
      });
      // REQ-F-492: success message with the new transaction id, fields cleared
      setForm(EMPTY);
      setFieldErrors({});
      setConfirming(false);
      setMessage({ text: `Transaction added successfully. Your Tran ID is ${created.id}.`, kind: 'success' });
    } catch (err) {
      setConfirming(false);
      setMessage({
        text: err instanceof ApiError ? err.message : 'Unable to Add Transaction...',
        kind: 'error',
      });
    }
  }

  // REQ-F-497: PF4 clears the screen
  function onClear() {
    setForm(EMPTY);
    setFieldErrors({});
    setConfirming(false);
    setMessage(null);
  }

  function field(name: keyof TxnForm, label: string, maxLength?: number) {
    const error = fieldErrors[name];
    return (
      <div className={`field ${error ? 'invalid' : ''}`}>
        <label htmlFor={name}>{label}</label>
        <input id={name} value={form[name]} maxLength={maxLength} onChange={(e) => set(name, e.target.value)} />
        {error && <span className="field-error">{error}</span>}
      </div>
    );
  }

  return (
    <Screen tranId="CT02" program="COTRN02C" title="Add Transaction" message={message} backTo="/transactions">
      <form onSubmit={onValidate} aria-label="Add transaction">
        <div className="form-grid">
          {field('accountId', 'Account ID', 11)}
          {field('cardNumber', 'Card Number', 16)}
          {field('typeCode', 'Type Code', 2)}
          {field('categoryCode', 'Category Code', 4)}
          {field('source', 'Source', 10)}
          {field('amount', 'Amount (-99999999.99)', 12)}
          {field('originalDate', 'Orig Date (YYYY-MM-DD)', 10)}
          {field('merchantId', 'Merchant ID', 9)}
          {field('merchantName', 'Merchant Name', 50)}
          {field('merchantCity', 'Merchant City', 50)}
          {field('merchantZip', 'Merchant Zip', 10)}
          <div className={`field full ${fieldErrors.description ? 'invalid' : ''}`}>
            <label htmlFor="description">Description</label>
            <input
              id="description"
              value={form.description}
              maxLength={100}
              onChange={(e) => set('description', e.target.value)}
            />
            {fieldErrors.description && <span className="field-error">{fieldErrors.description}</span>}
          </div>
        </div>
        <div className="actions">
          <button type="submit" className="btn secondary">
            Validate
          </button>
          {confirming && (
            <button type="button" className="btn" onClick={onConfirm}>
              Confirm Add (Y)
            </button>
          )}
          <button type="button" className="btn secondary" onClick={onClear}>
            F4=Clear
          </button>
        </div>
      </form>
    </Screen>
  );
}
