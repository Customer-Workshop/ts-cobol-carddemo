// Shared screen chrome replicating the legacy BMS screen layout:
// title lines, transaction id + program name, current date/time (REQ-F-342,
// REQ-F-356, REQ-F-393), signed-on user, message line, and a PF3-equivalent
// Back control (REQ-F-410).

import { useEffect, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export interface ScreenMessage {
  text: string;
  kind: 'error' | 'success' | 'info';
}

function formatDate(d: Date): string {
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  const yy = String(d.getFullYear() % 100).padStart(2, '0');
  return `${mm}/${dd}/${yy}`;
}

function formatTime(d: Date): string {
  return [d.getHours(), d.getMinutes(), d.getSeconds()].map((n) => String(n).padStart(2, '0')).join(':');
}

export function Screen({
  tranId,
  program,
  title,
  message,
  backTo,
  children,
}: {
  tranId: string;
  program: string;
  title: string;
  message?: ScreenMessage | null;
  backTo?: string;
  children: ReactNode;
}) {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(timer);
  }, []);

  return (
    <div className="screen">
      <header className="screen-header">
        <div className="header-row">
          <span className="tran-id" title={`Tran: ${tranId} Prog: ${program}`}>
            {tranId} / {program}
          </span>
          <span className="app-title">AWS Mainframe Modernization &mdash; CardDemo</span>
          <span className="date-time">
            {formatDate(now)} {formatTime(now)}
          </span>
        </div>
        <div className="header-row">
          <h1 className="screen-title">{title}</h1>
          {user && (
            <span className="user-info">
              {user.id} ({user.role})
              <button type="button" className="link-btn" onClick={() => { signOut(); navigate('/signon'); }}>
                Sign Out
              </button>
            </span>
          )}
        </div>
      </header>
      {/* Legacy message line semantics: one message area per screen */}
      <div role="status" aria-live="polite" className={`message-line ${message ? message.kind : 'empty'}`}>
        {message?.text ?? '\u00a0'}
      </div>
      <main className="screen-body">{children}</main>
      {backTo && (
        <footer className="screen-footer">
          <button type="button" className="btn" onClick={() => navigate(backTo)}>
            F3=Back
          </button>
        </footer>
      )}
    </div>
  );
}
