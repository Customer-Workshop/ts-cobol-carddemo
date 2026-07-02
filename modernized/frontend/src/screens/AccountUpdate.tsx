// Account Update screen (legacy COACTUPC / tran CAUP).
// REQ-F-016..REQ-F-065: fetch account+customer, field-level edits with
// validation, change detection (REQ-F-019/REQ-F-020: warn when no changes),
// and confirm-before-save (REQ-F-057..REQ-F-060).

import { useState, type FormEvent } from 'react';
import { api, ApiError, type AccountDetail, type AccountUpdateRequest } from '../api/client';
import { Screen, type ScreenMessage } from '../components/Screen';
import {
  isAlpha,
  isMoney,
  validateAccountId,
  validateDateOfBirth,
  validateFico,
  validateIsoDate,
  validatePhone,
  validateSsn,
  validateStateZip,
} from '../validation';

interface FormState {
  activeStatus: string;
  creditLimit: string;
  cashCreditLimit: string;
  expirationDate: string;
  reissueDate: string;
  groupId: string;
  firstName: string;
  middleName: string;
  lastName: string;
  addressLine1: string;
  addressLine2: string;
  stateCode: string;
  countryCode: string;
  zipCode: string;
  phoneNumber1: string;
  phoneNumber2: string;
  ssn: string;
  dateOfBirth: string;
  ficoCreditScore: string;
}

function toForm(detail: AccountDetail): FormState {
  const { account, customer } = detail;
  return {
    activeStatus: account.activeStatus ? 'Y' : 'N',
    creditLimit: account.creditLimit,
    cashCreditLimit: account.cashCreditLimit,
    expirationDate: account.expirationDate,
    reissueDate: account.reissueDate ?? '',
    groupId: account.groupId ?? '',
    firstName: customer.firstName,
    middleName: customer.middleName ?? '',
    lastName: customer.lastName,
    addressLine1: customer.addressLine1,
    addressLine2: customer.addressLine2 ?? '',
    stateCode: customer.stateCode,
    countryCode: customer.countryCode,
    zipCode: customer.zipCode,
    phoneNumber1: customer.phoneNumber1 ?? '',
    phoneNumber2: customer.phoneNumber2 ?? '',
    ssn: customer.ssn,
    dateOfBirth: customer.dateOfBirth,
    ficoCreditScore: String(customer.ficoCreditScore),
  };
}

// REQ-F-031..REQ-F-056: per-field edit rules
function validate(form: FormState): Record<string, string> {
  const errors: Record<string, string> = {};
  if (!['Y', 'N'].includes(form.activeStatus.toUpperCase())) {
    errors.activeStatus = 'Active status must be Y or N';
  }
  if (!isMoney(form.creditLimit)) errors.creditLimit = 'Credit limit must be a valid amount';
  if (!isMoney(form.cashCreditLimit)) errors.cashCreditLimit = 'Cash credit limit must be a valid amount';
  const expErr = validateIsoDate(form.expirationDate);
  if (expErr) errors.expirationDate = expErr;
  if (form.reissueDate.trim() !== '') {
    const reissueErr = validateIsoDate(form.reissueDate);
    if (reissueErr) errors.reissueDate = reissueErr;
  }
  // REQ-F-031/REQ-F-034: names required and alphabetic
  if (form.firstName.trim() === '' || !isAlpha(form.firstName)) {
    errors.firstName = 'First name must be provided and alphabetic';
  }
  if (form.middleName.trim() !== '' && !isAlpha(form.middleName)) {
    errors.middleName = 'Middle name must be alphabetic';
  }
  if (form.lastName.trim() === '' || !isAlpha(form.lastName)) {
    errors.lastName = 'Last name must be provided and alphabetic';
  }
  if (form.addressLine1.trim() === '') errors.addressLine1 = 'Address line 1 must be provided';
  const stateZipErr = validateStateZip(form.stateCode, form.zipCode);
  if (stateZipErr) errors.stateCode = stateZipErr;
  if (form.countryCode.trim() === '') errors.countryCode = 'Country code must be provided';
  const phone1Err = validatePhone(form.phoneNumber1);
  if (phone1Err) errors.phoneNumber1 = phone1Err;
  const phone2Err = validatePhone(form.phoneNumber2);
  if (phone2Err) errors.phoneNumber2 = phone2Err;
  const ssnErr = validateSsn(form.ssn);
  if (ssnErr) errors.ssn = ssnErr;
  const dobErr = validateDateOfBirth(form.dateOfBirth);
  if (dobErr) errors.dateOfBirth = dobErr;
  const ficoErr = validateFico(form.ficoCreditScore);
  if (ficoErr) errors.ficoCreditScore = ficoErr;
  return errors;
}

export function AccountUpdate() {
  const [accountId, setAccountId] = useState('');
  const [detail, setDetail] = useState<AccountDetail | null>(null);
  const [form, setForm] = useState<FormState | null>(null);
  const [original, setOriginal] = useState<FormState | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [confirming, setConfirming] = useState(false);
  const [message, setMessage] = useState<ScreenMessage | null>(null);

  async function onSearch(e: FormEvent) {
    e.preventDefault();
    const err = validateAccountId(accountId);
    if (err) {
      setMessage({ text: err, kind: 'error' });
      return;
    }
    try {
      const res = await api.getAccount(accountId.trim());
      setDetail(res);
      const f = toForm(res);
      setForm(f);
      setOriginal(f);
      setFieldErrors({});
      setConfirming(false);
      setMessage({ text: 'Update the account details and press Save', kind: 'info' });
    } catch (apiErr) {
      setDetail(null);
      setForm(null);
      setMessage({
        text: apiErr instanceof ApiError ? apiErr.message : 'Unable to lookup Account...',
        kind: 'error',
      });
    }
  }

  function set(field: keyof FormState, value: string) {
    if (!form) return;
    setForm({ ...form, [field]: value });
    setConfirming(false);
  }

  function onValidate(e: FormEvent) {
    e.preventDefault();
    if (!form || !original) return;
    // REQ-F-019/REQ-F-020: change detection — warn when nothing changed
    if (JSON.stringify(form) === JSON.stringify(original)) {
      setMessage({ text: 'Please modify to update ...', kind: 'error' });
      return;
    }
    const errors = validate(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      setMessage({ text: 'Correct the highlighted fields and retry', kind: 'error' });
      setConfirming(false);
      return;
    }
    // REQ-F-057: valid changes await explicit confirmation before saving
    setConfirming(true);
    setMessage({ text: 'Changes validated. Press Confirm Save (F5) to save updates', kind: 'info' });
  }

  async function onConfirmSave() {
    if (!form || !detail) return;
    const body: AccountUpdateRequest = {
      activeStatus: form.activeStatus.toUpperCase() === 'Y',
      creditLimit: form.creditLimit,
      cashCreditLimit: form.cashCreditLimit,
      expirationDate: form.expirationDate,
      reissueDate: form.reissueDate.trim() === '' ? undefined : form.reissueDate,
      groupId: form.groupId.trim() === '' ? undefined : form.groupId,
      customer: {
        firstName: form.firstName.trim(),
        middleName: form.middleName.trim() === '' ? null : form.middleName.trim(),
        lastName: form.lastName.trim(),
        addressLine1: form.addressLine1,
        addressLine2: form.addressLine2.trim() === '' ? null : form.addressLine2,
        stateCode: form.stateCode.toUpperCase(),
        countryCode: form.countryCode.toUpperCase(),
        zipCode: form.zipCode,
        phoneNumber1: form.phoneNumber1.trim() === '' ? null : form.phoneNumber1,
        phoneNumber2: form.phoneNumber2.trim() === '' ? null : form.phoneNumber2,
        ssn: form.ssn.replace(/-/g, ''),
        dateOfBirth: form.dateOfBirth,
        ficoCreditScore: Number(form.ficoCreditScore),
      },
    };
    try {
      const res = await api.updateAccount(detail.account.id, body);
      const f = toForm(res);
      setDetail(res);
      setForm(f);
      setOriginal(f);
      setConfirming(false);
      // REQ-F-059-style success confirmation
      setMessage({ text: 'Account has been updated ...', kind: 'success' });
    } catch (apiErr) {
      setConfirming(false);
      setMessage({
        text: apiErr instanceof ApiError ? apiErr.message : 'Unable to update Account...',
        kind: 'error',
      });
    }
  }

  function field(name: keyof FormState, label: string, maxLength?: number) {
    if (!form) return null;
    const error = fieldErrors[name];
    return (
      <div className={`field ${error ? 'invalid' : ''}`}>
        <label htmlFor={name}>{label}</label>
        <input
          id={name}
          value={form[name]}
          maxLength={maxLength}
          onChange={(e) => set(name, e.target.value)}
        />
        {error && <span className="field-error">{error}</span>}
      </div>
    );
  }

  return (
    <Screen tranId="CAUP" program="COACTUPC" title="Update Account" message={message} backTo="/menu">
      <form onSubmit={onSearch} className="actions" aria-label="Account search">
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
      {form && detail && (
        <form onSubmit={onValidate} style={{ marginTop: 16 }} aria-label="Account update">
          <fieldset>
            <legend>Account {detail.account.id}</legend>
            <div className="form-grid">
              {field('activeStatus', 'Active (Y/N)', 1)}
              {field('creditLimit', 'Credit Limit')}
              {field('cashCreditLimit', 'Cash Credit Limit')}
              {field('expirationDate', 'Expiry Date (YYYY-MM-DD)', 10)}
              {field('reissueDate', 'Reissue Date (YYYY-MM-DD)', 10)}
              {field('groupId', 'Group ID', 10)}
            </div>
          </fieldset>
          <fieldset>
            <legend>Customer {detail.customer.id}</legend>
            <div className="form-grid">
              {field('firstName', 'First Name', 25)}
              {field('middleName', 'Middle Name', 25)}
              {field('lastName', 'Last Name', 25)}
              {field('addressLine1', 'Address Line 1', 50)}
              {field('addressLine2', 'Address Line 2', 50)}
              {field('stateCode', 'State', 2)}
              {field('zipCode', 'Zip Code', 10)}
              {field('countryCode', 'Country', 3)}
              {field('phoneNumber1', 'Phone 1', 15)}
              {field('phoneNumber2', 'Phone 2', 15)}
              {field('ssn', 'SSN', 11)}
              {field('dateOfBirth', 'Date of Birth (YYYY-MM-DD)', 10)}
              {field('ficoCreditScore', 'FICO Score', 3)}
            </div>
          </fieldset>
          <div className="actions">
            <button type="submit" className="btn secondary">
              Validate Changes
            </button>
            {confirming && (
              <button type="button" className="btn" onClick={onConfirmSave}>
                F5=Confirm Save
              </button>
            )}
          </div>
        </form>
      )}
    </Screen>
  );
}
