// User List screen (legacy COUSR00C / tran CU00) — admin only.
// REQ-F-505..REQ-F-522: paginated user list (10 rows), U/D row selection
// routing to update/delete, boundary messages.

import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { User } from '@carddemo/shared';
import { api, ApiError, type Paged } from '../api/client';
import { Screen, type ScreenMessage } from '../components/Screen';

const PAGE_SIZE = 10;

export function UserList() {
  const navigate = useNavigate();
  const [page, setPage] = useState(1);
  const [result, setResult] = useState<Paged<User> | null>(null);
  const [message, setMessage] = useState<ScreenMessage | null>(null);

  const load = useCallback(async (p: number) => {
    try {
      const res = await api.listUsers({ page: p, pageSize: PAGE_SIZE });
      setResult(res);
      setPage(res.page);
      setMessage(null);
    } catch (err) {
      setMessage({
        text: err instanceof ApiError ? err.message : 'Unable to lookup User...',
        kind: 'error',
      });
    }
  }, []);

  useEffect(() => {
    void load(1);
  }, [load]);

  // REQ-F-513/REQ-F-515: page boundary messages
  function prevPage() {
    if (page <= 1) {
      setMessage({ text: 'You are already at the top of the page...', kind: 'error' });
      return;
    }
    void load(page - 1);
  }

  function nextPage() {
    if (result && page >= result.totalPages) {
      setMessage({ text: 'You are already at the bottom of the page...', kind: 'error' });
      return;
    }
    void load(page + 1);
  }

  return (
    <Screen tranId="CU00" program="COUSR00C" title="List Users" message={message} backTo="/admin">
      <div className="actions">
        <button type="button" className="btn" onClick={() => navigate('/admin/users/add')}>
          Add User
        </button>
      </div>
      <table className="data" style={{ marginTop: 16 }}>
        <thead>
          <tr>
            <th>User ID</th>
            <th>First Name</th>
            <th>Last Name</th>
            <th>Type</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          {result?.items.map((u) => (
            <tr key={u.id}>
              <td>{u.id}</td>
              <td>{u.firstName}</td>
              <td>{u.lastName}</td>
              <td>{u.role}</td>
              <td>
                {/* REQ-F-521/REQ-F-522: U=update, D=delete row selection */}
                <button
                  type="button"
                  className="link-btn"
                  onClick={() => navigate(`/admin/users/update?userId=${u.id}`)}
                >
                  U=Update
                </button>{' '}
                <button
                  type="button"
                  className="link-btn"
                  onClick={() => navigate(`/admin/users/delete?userId=${u.id}`)}
                >
                  D=Delete
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
