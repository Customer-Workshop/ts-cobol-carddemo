// Sign-on screen (legacy COSGN00C / tran CC00).
// REQ-F-375..REQ-F-388: credential collection, validation, and routing.

import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { UserRole } from '@carddemo/shared';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Screen, type ScreenMessage } from '../components/Screen';

export function SignOn() {
  const { signIn } = useAuth();
  const navigate = useNavigate();
  const [userId, setUserId] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState<ScreenMessage | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    // REQ-F-379: blank user id
    if (userId.trim() === '') {
      setMessage({ text: 'Please enter User ID ...', kind: 'error' });
      return;
    }
    // REQ-F-380: blank password
    if (password.trim() === '') {
      setMessage({ text: 'Please enter Password ...', kind: 'error' });
      return;
    }
    setBusy(true);
    setMessage(null);
    try {
      // REQ-F-381: credentials are uppercased before authentication
      const user = await signIn(userId.trim().toUpperCase(), password.trim().toUpperCase());
      // REQ-F-386/REQ-F-387: ADMIN -> admin menu, USER -> main menu
      navigate(user.role === UserRole.ADMIN ? '/admin' : '/menu');
    } catch (err) {
      // REQ-F-382/REQ-F-383/REQ-F-384: authentication error messaging
      const text = err instanceof ApiError ? err.message : 'Unable to verify the User ...';
      setMessage({ text, kind: 'error' });
    } finally {
      setBusy(false);
    }
  }

  return (
    <Screen tranId="CC00" program="COSGN00C" title="Sign On" message={message}>
      <form onSubmit={onSubmit} className="form-grid" aria-label="Sign on">
        <div className="field">
          <label htmlFor="userId">User ID</label>
          <input
            id="userId"
            value={userId}
            maxLength={8}
            autoFocus
            onChange={(e) => setUserId(e.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            value={password}
            maxLength={8}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>
        <div className="actions full">
          <button className="btn" type="submit" disabled={busy}>
            Sign On
          </button>
        </div>
      </form>
    </Screen>
  );
}
