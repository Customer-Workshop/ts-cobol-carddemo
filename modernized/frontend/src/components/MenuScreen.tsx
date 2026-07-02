// Shared menu widget: numbered options selectable by click or by typing the
// option number (REQ-F-343, REQ-F-345, REQ-F-346).

import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { ScreenMessage } from './Screen';

export interface MenuOption {
  label: string;
  to: string;
}

export function MenuList({
  options,
  onError,
}: {
  options: MenuOption[];
  onError: (message: ScreenMessage) => void;
}) {
  const navigate = useNavigate();
  const [selection, setSelection] = useState('');

  useEffect(() => {
    // Keyboard-number selection like the legacy OPTION field
    function onKey(e: KeyboardEvent) {
      if (e.target instanceof HTMLInputElement || e.target instanceof HTMLSelectElement) return;
      if (!/^\d$/.test(e.key)) return;
      const n = Number(e.key);
      const option = options[n - 1];
      if (option) {
        navigate(option.to);
      } else {
        // REQ-F-346: out-of-range option
        onError({ text: 'Please enter a valid option number...', kind: 'error' });
      }
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [options, navigate, onError]);

  function submitSelection() {
    const trimmed = selection.trim();
    const n = Number(trimmed);
    // REQ-F-346: option must be numeric, in range, and non-zero
    if (!/^\d+$/.test(trimmed) || n === 0 || n > options.length) {
      onError({ text: 'Please enter a valid option number...', kind: 'error' });
      return;
    }
    navigate(options[n - 1]!.to);
  }

  return (
    <div>
      <ol className="menu-list">
        {options.map((opt, i) => (
          <li key={opt.to}>
            <button type="button" onClick={() => navigate(opt.to)}>
              {String(i + 1).padStart(2, '0')}. {opt.label}
            </button>
          </li>
        ))}
      </ol>
      <div className="actions">
        <div className="field">
          <label htmlFor="option">Option</label>
          <input
            id="option"
            value={selection}
            maxLength={2}
            size={4}
            onChange={(e) => setSelection(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') submitSelection();
            }}
          />
        </div>
        <button type="button" className="btn" onClick={submitSelection}>
          Enter
        </button>
      </div>
      <p className="menu-hint">Type an option number or click an entry to navigate.</p>
    </div>
  );
}
