// User Add screen (legacy COUSR01C / tran CU01) — admin only.
// REQ-F-553..REQ-F-562: required-field validation, duplicate handling,
// success message, PF4-style clear.

import { useState, type FormEvent } from 'react';
import { UserRole } from '@carddemo/shared';
import { api, ApiError } from '../api/client';
import { Screen, type ScreenMessage } from '../components/Screen';

interface UserForm {
  id: string;
  firstName: string;
  lastName: string;
  password: string;
  role: string;
}

const EMPTY: UserForm = { id: '', firstName: '', lastName: '', password: '', role: '' };

// REQ-F-557: field-specific empty-field messages
function validate(form: UserForm): Record<string, string> {
  const errors: Record<string, string> = {};
  if (form.firstName.trim() === '') errors.firstName = 'First Name can NOT be empty...';
  if (form.lastName.trim() === '') errors.lastName = 'Last Name can NOT be empty...';
  if (form.id.trim() === '') errors.id = 'User ID can NOT be empty...';
  if (form.password.trim() === '') errors.password = 'Password can NOT be empty...';
  if (form.role.trim() === '') errors.role = 'User Type can NOT be empty...';
  return errors;
}

export function UserAdd() {
  const [form, setForm] = useState<UserForm>(EMPTY);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<ScreenMessage | null>(null);

  function set(field: keyof UserForm, value: string) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const errors = validate(form);
    setFieldErrors(errors);
    const firstError = Object.values(errors)[0];
    if (firstError) {
      setMessage({ text: firstError, kind: 'error' });
      return;
    }
    try {
      const created = await api.createUser({
        id: form.id.trim().toUpperCase(),
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        password: form.password.trim(),
        role: form.role as UserRole,
      });
      // REQ-F-559: success clears the fields
      setForm(EMPTY);
      setMessage({ text: `User ${created.id} has been added ...`, kind: 'success' });
    } catch (err) {
      // REQ-F-560/REQ-F-561: duplicate vs generic error
      setMessage({
        text: err instanceof ApiError ? err.message : 'Unable to Add User...',
        kind: 'error',
      });
    }
  }

  // REQ-F-558: PF4 clears the fields and message
  function onClear() {
    setForm(EMPTY);
    setFieldErrors({});
    setMessage(null);
  }

  function field(name: keyof UserForm, label: string, maxLength: number, type = 'text') {
    const error = fieldErrors[name];
    return (
      <div className={`field ${error ? 'invalid' : ''}`}>
        <label htmlFor={name}>{label}</label>
        <input
          id={name}
          type={type}
          value={form[name]}
          maxLength={maxLength}
          onChange={(e) => set(name, e.target.value)}
        />
        {error && <span className="field-error">{error}</span>}
      </div>
    );
  }

  return (
    <Screen tranId="CU01" program="COUSR01C" title="Add User" message={message} backTo="/admin/users">
      <form onSubmit={onSubmit} aria-label="Add user">
        <div className="form-grid">
          {field('firstName', 'First Name', 20)}
          {field('lastName', 'Last Name', 20)}
          {field('id', 'User ID', 9)}
          {field('password', 'Password', 8, 'password')}
          <div className={`field ${fieldErrors.role ? 'invalid' : ''}`}>
            <label htmlFor="role">User Type</label>
            <select id="role" value={form.role} onChange={(e) => set('role', e.target.value)}>
              <option value="">-- select --</option>
              <option value={UserRole.USER}>USER (Regular)</option>
              <option value={UserRole.ADMIN}>ADMIN</option>
            </select>
            {fieldErrors.role && <span className="field-error">{fieldErrors.role}</span>}
          </div>
        </div>
        <div className="actions">
          <button type="submit" className="btn">
            Add User
          </button>
          <button type="button" className="btn secondary" onClick={onClear}>
            F4=Clear
          </button>
        </div>
      </form>
    </Screen>
  );
}
