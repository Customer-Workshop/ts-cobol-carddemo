// DB2 timestamp formatting shared by posting and interest jobs.
// REQ-F-043 (TransactionProcessingandValidation), REQ-F-086
// (StatementandReportGeneration): YYYY-MM-DD-HH.MM.SS.MIL0000.
const p = (n: number, w: number): string => String(n).padStart(w, '0');

export function toDb2Timestamp(d: Date): string {
  return (
    `${p(d.getUTCFullYear(), 4)}-${p(d.getUTCMonth() + 1, 2)}-${p(d.getUTCDate(), 2)}` +
    `-${p(d.getUTCHours(), 2)}.${p(d.getUTCMinutes(), 2)}.${p(d.getUTCSeconds(), 2)}` +
    `.${p(d.getUTCMilliseconds(), 3)}0000`
  );
}

/** YYYY-MM-DD (UTC) — the "first 10 characters" of a legacy timestamp. */
export function toIsoDate(d: Date): string {
  return `${p(d.getUTCFullYear(), 4)}-${p(d.getUTCMonth() + 1, 2)}-${p(d.getUTCDate(), 2)}`;
}

/** First day of the month containing `d` (UTC) — default cycle start. */
export function cycleStart(d: Date): Date {
  return new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), 1));
}

/** Last day of the month containing `d` (UTC) — default cycle end. */
export function cycleEnd(d: Date): Date {
  return new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 0));
}
