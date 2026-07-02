// Reports screen (legacy CORPT00C / tran CR00).
// REQ-F-356..REQ-F-370: report type selection (monthly/yearly/custom),
// date-range validation, Y/N confirmation, and submission.

import { useCallback, useEffect, useState, type FormEvent } from 'react';
import type { Report } from '@carddemo/shared';
import { api, ApiError, type Paged } from '../api/client';
import { Screen, type ScreenMessage } from '../components/Screen';
import { validateIsoDate } from '../validation';

type ReportType = 'monthly' | 'yearly' | 'custom';

function monthRange(now: Date): [string, string] {
  const y = now.getFullYear();
  const m = now.getMonth();
  const last = new Date(y, m + 1, 0).getDate();
  const mm = String(m + 1).padStart(2, '0');
  return [`${y}-${mm}-01`, `${y}-${mm}-${String(last).padStart(2, '0')}`];
}

export function Reports() {
  const [reports, setReports] = useState<Paged<Report> | null>(null);
  const [type, setType] = useState<ReportType | ''>('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [pending, setPending] = useState<{ name: string; startDate: string; endDate: string } | null>(null);
  const [message, setMessage] = useState<ScreenMessage | null>(null);

  const load = useCallback(async () => {
    try {
      const res = await api.listReports({ pageSize: 20 });
      setReports(res);
    } catch (err) {
      setMessage({
        text: err instanceof ApiError ? err.message : 'Unable to list reports...',
        kind: 'error',
      });
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const now = new Date();
    // REQ-F-364: a report type must be selected
    if (type === '') {
      setMessage({ text: 'Select a report type to print report...', kind: 'error' });
      return;
    }
    let name: string;
    let start: string;
    let end: string;
    if (type === 'monthly') {
      // REQ-F-358
      name = 'Monthly';
      [start, end] = monthRange(now);
    } else if (type === 'yearly') {
      // REQ-F-359
      name = 'Yearly';
      start = `${now.getFullYear()}-01-01`;
      end = `${now.getFullYear()}-12-31`;
    } else {
      // REQ-F-360..REQ-F-363: custom dates must be present and valid
      name = 'Custom';
      if (startDate.trim() === '' || endDate.trim() === '') {
        setMessage({ text: 'Start and End dates are required for a custom report...', kind: 'error' });
        return;
      }
      const startErr = validateIsoDate(startDate);
      if (startErr) {
        setMessage({ text: `Start Date - ${startErr}`, kind: 'error' });
        return;
      }
      const endErr = validateIsoDate(endDate);
      if (endErr) {
        setMessage({ text: `End Date - ${endErr}`, kind: 'error' });
        return;
      }
      if (startDate > endDate) {
        setMessage({ text: 'Start Date must not be after End Date...', kind: 'error' });
        return;
      }
      start = startDate.trim();
      end = endDate.trim();
    }
    // REQ-F-365: confirmation required before submitting the job
    setPending({ name, startDate: start, endDate: end });
    setConfirming(true);
    setMessage({
      text: `Print the ${name} report from ${start} to ${end}? Confirm Y/N`,
      kind: 'info',
    });
  }

  async function onConfirm() {
    if (!pending) return;
    try {
      await api.createReport(pending);
      // REQ-F-370: success message, fields reset
      setType('');
      setStartDate('');
      setEndDate('');
      setConfirming(false);
      setPending(null);
      setMessage({ text: `${pending.name} report submitted for printing ...`, kind: 'success' });
      void load();
    } catch (err) {
      setConfirming(false);
      setMessage({
        text: err instanceof ApiError ? err.message : 'Unable to submit report...',
        kind: 'error',
      });
    }
  }

  // REQ-F-366: N resets the input fields
  function onDecline() {
    setType('');
    setStartDate('');
    setEndDate('');
    setConfirming(false);
    setPending(null);
    setMessage(null);
  }

  return (
    <Screen tranId="CR00" program="CORPT00C" title="Transaction Reports" message={message} backTo="/menu">
      <form onSubmit={onSubmit} aria-label="Report request">
        <div className="form-grid">
          <div className="field">
            <label htmlFor="reportType">Report Type</label>
            <select id="reportType" value={type} onChange={(e) => setType(e.target.value as ReportType | '')}>
              <option value="">-- select --</option>
              <option value="monthly">Monthly (Current Month)</option>
              <option value="yearly">Yearly (Current Year)</option>
              <option value="custom">Custom (Date Range)</option>
            </select>
          </div>
          {type === 'custom' && (
            <>
              <div className="field">
                <label htmlFor="startDate">Start Date (YYYY-MM-DD)</label>
                <input
                  id="startDate"
                  value={startDate}
                  maxLength={10}
                  onChange={(e) => setStartDate(e.target.value)}
                />
              </div>
              <div className="field">
                <label htmlFor="endDate">End Date (YYYY-MM-DD)</label>
                <input id="endDate" value={endDate} maxLength={10} onChange={(e) => setEndDate(e.target.value)} />
              </div>
            </>
          )}
        </div>
        <div className="actions">
          <button type="submit" className="btn secondary">
            Submit
          </button>
          {confirming && (
            <>
              <button type="button" className="btn" onClick={onConfirm}>
                Confirm (Y)
              </button>
              <button type="button" className="btn secondary" onClick={onDecline}>
                Decline (N)
              </button>
            </>
          )}
        </div>
      </form>
      <h2 style={{ fontSize: '1rem', marginTop: 24 }}>Generated Reports</h2>
      <table className="data">
        <thead>
          <tr>
            <th>ID</th>
            <th>Name</th>
            <th>Start</th>
            <th>End</th>
            <th>Created</th>
          </tr>
        </thead>
        <tbody>
          {reports?.items.map((r) => (
            <tr key={r.id}>
              <td>{r.id}</td>
              <td>{r.name}</td>
              <td>{r.startDate}</td>
              <td>{r.endDate}</td>
              <td>{r.createdAt.slice(0, 10)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Screen>
  );
}
