// User Delete screen (legacy COUSR03C / tran CU03) — admin only.
// REQ-F-535..REQ-F-547, REQ-F-598..REQ-F-611: lookup, review, PF5-style
// confirmed deletion, PF4-style clear.

import { useEffect, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { User } from '@carddemo/shared';
import { api, ApiError } from '../api/client';
import { Screen, type ScreenMessage } from '../components/Screen';

export function UserDelete() {
  const [searchParams] = useSearchParams();
  const [userId, setUserId] = useState(searchParams.get('userId') ?? '');
  const [user, setUser] = useState<User | null>(null);
  const [message, setMessage] = useState<ScreenMessage | null>(null);

  async function lookup(id: string) {
    // REQ-F-537/REQ-F-601: user ID required
    if (id.trim() === '') {
      setMessage({ text: 'User ID can NOT be empty...', kind: 'error' });
      return;
    }
    try {
      const res = await api.listUsers({ pageSize: 100 });
      const found = res.items.find((u) => u.id === id.trim().toUpperCase());
      if (!found) {
        // REQ-F-539/REQ-F-606
        setMessage({ text: 'User ID NOT found...', kind: 'error' });
        setUser(null);
        return;
      }
      setUser(found);
      // REQ-F-538/REQ-F-602: review then confirm with PF5
      setMessage({ text: 'Press Delete (F5) key to delete this user ...', kind: 'info' });
    } catch (err) {
      setUser(null);
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

  // REQ-F-541/REQ-F-604/REQ-F-605: confirmed deletion
  async function onDelete() {
    if (!user) return;
    try {
      await api.deleteUser(user.id);
      const deletedId = user.id;
      setUser(null);
      setUserId('');
      setMessage({ text: `User ${deletedId} has been deleted ...`, kind: 'success' });
    } catch (err) {
      setMessage({
        text: err instanceof ApiError ? err.message : 'Unable to Update User...',
        kind: 'error',
      });
    }
  }

  // REQ-F-543/REQ-F-608: PF4 clears the screen
  function onClear() {
    setUserId('');
    setUser(null);
    setMessage(null);
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    void lookup(userId);
  }

  return (
    <Screen tranId="CU03" program="COUSR03C" title="Delete User" message={message} backTo="/admin/users">
      <form onSubmit={onSubmit} className="actions" aria-label="User search">
        <div className="field">
          <label htmlFor="userId">User ID</label>
          <input id="userId" value={userId} maxLength={8} onChange={(e) => setUserId(e.target.value)} />
        </div>
        <button type="submit" className="btn">
          Search
        </button>
        <button type="button" className="btn secondary" onClick={onClear}>
          F4=Clear
        </button>
      </form>
      {user && (
        <div style={{ marginTop: 16 }}>
          <dl className="detail">
            <dt>User ID</dt>
            <dd>{user.id}</dd>
            <dt>First Name</dt>
            <dd>{user.firstName}</dd>
            <dt>Last Name</dt>
            <dd>{user.lastName}</dd>
            <dt>User Type</dt>
            <dd>{user.role}</dd>
          </dl>
          <div className="actions">
            <button type="button" className="btn" onClick={onDelete}>
              F5=Delete
            </button>
          </div>
        </div>
      )}
    </Screen>
  );
}
