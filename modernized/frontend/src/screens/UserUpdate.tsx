// User Update screen (legacy COUSR02C / tran CU02) — admin only.
// REQ-F-523..REQ-F-534, REQ-F-571..REQ-F-590: lookup by user ID, required
// fields, change detection ("Please modify to update ..."), PF5-style save.

import { useEffect, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { UserRole } from '@carddemo/shared';
import { api, ApiError } from '../api/client';
import { Screen, type ScreenMessage } from '../components/Screen';

interface UserForm {
  firstName: string;
  lastName: string;
  password: string;
  role: string;
}

export function UserUpdate() {
  const [searchParams] = useSearchParams();
  const [userId, setUserId] = useState(searchParams.get('userId') ?? '');
  const [loadedId, setLoadedId] = useState<string | null>(null);
  const [form, setForm] = useState<UserForm | null>(null);
  const [original, setOriginal] = useState<UserForm | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<ScreenMessage | null>(null);

  async function lookup(id: string) {
    // REQ-F-525/REQ-F-574: user ID required
    if (id.trim() === '') {
      setMessage({ text: 'User ID can NOT be empty...', kind: 'error' });
      return;
    }
    try {
      const res = await api.listUsers({ pageSize: 100 });
      const user = res.items.find((u) => u.id === id.trim().toUpperCase());
      if (!user) {
        // REQ-F-528/REQ-F-577
        setMessage({ text: 'User ID NOT found...', kind: 'error' });
        setForm(null);
        setLoadedId(null);
        return;
      }
      const f: UserForm = { firstName: user.firstName, lastName: user.lastName, password: '', role: user.role };
      setLoadedId(user.id);
      setForm(f);
      setOriginal(f);
      setFieldErrors({});
      // REQ-F-527/REQ-F-576
      setMessage({ text: 'Press Save (F5) key to save your updates ...', kind: 'info' });
    } catch (err) {
      setForm(null);
      setLoadedId(null);
      setMessage({
        text: err instanceof ApiError ? err.message : 'Unable to lookup User...',
        kind: 'error',
      });
    }
  }

  useEffect(() => {
    const preset = searchParams.get('userId');
    if (preset) void lookup(preset);
  }, []);

  function set(field: keyof UserForm, value: string) {
    if (!form) return;
    setForm({ ...form, [field]: value });
  }

  async function onSave(e: FormEvent) {
    e.preventDefault();
    if (!form || !original || !loadedId) return;
    // REQ-F-530/REQ-F-579..REQ-F-583: required fields (password may stay blank
    // in the modernized flow to keep the existing bcrypt hash)
    const errors: Record<string, string> = {};
    if (form.firstName.trim() === '') errors.firstName = 'First Name can NOT be empty...';
    if (form.lastName.trim() === '') errors.lastName = 'Last Name can NOT be empty...';
    if (form.role.trim() === '') errors.role = 'User Type can NOT be empty...';
    setFieldErrors(errors);
    const firstError = Object.values(errors)[0];
    if (firstError) {
      setMessage({ text: firstError, kind: 'error' });
      return;
    }
    // REQ-F-532/REQ-F-589: change detection
    if (JSON.stringify(form) === JSON.stringify(original)) {
      setMessage({ text: 'Please modify to update ...', kind: 'error' });
      return;
    }
    try {
      await api.updateUser(loadedId, {
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        role: form.role as UserRole,
        ...(form.password.trim() !== '' ? { password: form.password.trim() } : {}),
      });
      setOriginal(form);
      // REQ-F-586: success message includes the user ID
      setMessage({ text: `User ${loadedId} has been updated ...`, kind: 'success' });
    } catch (err) {
      setMessage({
        text: err instanceof ApiError ? err.message : 'Unable to Update User...',
        kind: 'error',
      });
    }
  }

  return (
    <Screen tranId="CU02" program="COUSR02C" title="Update User" message={message} backTo="/admin/users">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void lookup(userId);
        }}
        className="actions"
        aria-label="User search"
      >
        <div className="field">
          <label htmlFor="userId">User ID</label>
          <input id="userId" value={userId} maxLength={9} onChange={(e) => setUserId(e.target.value)} />
        </div>
        <button type="submit" className="btn">
          Search
        </button>
      </form>
      {form && loadedId && (
        <form onSubmit={onSave} style={{ marginTop: 16 }} aria-label="User update">
          <fieldset>
            <legend>User {loadedId}</legend>
            <div className="form-grid">
              <div className={`field ${fieldErrors.firstName ? 'invalid' : ''}`}>
                <label htmlFor="firstName">First Name</label>
                <input
                  id="firstName"
                  value={form.firstName}
                  maxLength={20}
                  onChange={(e) => set('firstName', e.target.value)}
                />
                {fieldErrors.firstName && <span className="field-error">{fieldErrors.firstName}</span>}
              </div>
              <div className={`field ${fieldErrors.lastName ? 'invalid' : ''}`}>
                <label htmlFor="lastName">Last Name</label>
                <input
                  id="lastName"
                  value={form.lastName}
                  maxLength={20}
                  onChange={(e) => set('lastName', e.target.value)}
                />
                {fieldErrors.lastName && <span className="field-error">{fieldErrors.lastName}</span>}
              </div>
              <div className="field">
                <label htmlFor="password">New Password (blank keeps current)</label>
                <input
                  id="password"
                  type="password"
                  value={form.password}
                  maxLength={8}
                  onChange={(e) => set('password', e.target.value)}
                />
              </div>
              <div className={`field ${fieldErrors.role ? 'invalid' : ''}`}>
                <label htmlFor="role">User Type</label>
                <select id="role" value={form.role} onChange={(e) => set('role', e.target.value)}>
                  <option value={UserRole.USER}>USER (Regular)</option>
                  <option value={UserRole.ADMIN}>ADMIN</option>
                </select>
                {fieldErrors.role && <span className="field-error">{fieldErrors.role}</span>}
              </div>
            </div>
          </fieldset>
          <div className="actions">
            <button type="submit" className="btn">
              F5=Save
            </button>
          </div>
        </form>
      )}
    </Screen>
  );
}
