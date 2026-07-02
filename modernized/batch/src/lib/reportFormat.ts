// Transaction report formatting — modernizes CBTRN03C / TRANREPT.
// StatementandReportGeneration REQ-F-093..REQ-F-111: headers, detail lines
// enriched with type/category lookups, page totals every PAGE_SIZE lines,
// account totals on card-number control breaks, and a grand total.
import { Prisma } from '@prisma/client';

export const PAGE_SIZE = 20; // REQ-F-106 (OQ-008: fixed business rule)
const WIDTH = 133;
const DASHES = '-'.repeat(WIDTH);

export interface ReportRow {
  transactionId: string;
  accountId: string;
  cardNumber: string;
  typeCode: string;
  typeDescription: string;
  categoryCode: number;
  categoryDescription: string;
  source: string;
  amount: Prisma.Decimal;
}

const iso = (d: Date): string => d.toISOString().slice(0, 10);
const D0 = new Prisma.Decimal(0);

function headerLines(startDate: Date, endDate: Date): string[] {
  // REQ-F-093/REQ-F-094: report-name header with date range, blank line,
  // column headers, separator line.
  const title = `DAILY TRANSACTION REPORT FROM ${iso(startDate)} TO ${iso(endDate)}`;
  const pad = Math.max(0, Math.floor((WIDTH - title.length) / 2));
  return [
    `${' '.repeat(pad)}${title}`,
    '',
    `${'Tran ID'.padEnd(17)}${'Account ID'.padEnd(12)}${'Tran Type'.padEnd(23)}${'Tran Category'.padEnd(38)}${'Tran Source'.padEnd(12)}${'Amount'.padStart(15)}`,
    DASHES,
  ];
}

export function renderTransactionReport(
  rows: ReportRow[],
  startDate: Date,
  endDate: Date,
): string {
  const lines: string[] = [...headerLines(startDate, endDate)];
  let lineCount = lines.length;
  let pageTotal = D0;
  let accountTotal = D0;
  let grandTotal = D0;
  let prevCard: string | null = null;

  const writePageTotal = (): void => {
    // REQ-F-107: page total, add to grand total, reset, separator.
    lines.push(`${'PAGE TOTAL:'.padEnd(102)}${pageTotal.toFixed(2).padStart(15)}`);
    grandTotal = grandTotal.plus(pageTotal);
    pageTotal = D0;
    lineCount += 1;
    lines.push(DASHES);
    lineCount += 1;
  };
  const writeAccountTotal = (): void => {
    // REQ-F-108: account total on control break, reset, separator.
    lines.push(`${'ACCOUNT TOTAL:'.padEnd(102)}${accountTotal.toFixed(2).padStart(15)}`);
    accountTotal = D0;
    lineCount += 1;
    lines.push(DASHES);
    lineCount += 1;
  };

  for (const row of rows) {
    // REQ-F-097: control break when the card number changes.
    if (prevCard !== null && row.cardNumber !== prevCard) {
      writeAccountTotal();
    }
    prevCard = row.cardNumber;
    // REQ-F-104: detail line with lookups and formatted amount.
    lines.push(
      `${row.transactionId.padEnd(17)}${row.accountId.padEnd(12)}` +
        `${`${row.typeCode} ${row.typeDescription.slice(0, 20)}`.padEnd(23)}` +
        `${`${String(row.categoryCode).padStart(4, '0')} ${row.categoryDescription.slice(0, 32)}`.padEnd(38)}` +
        `${row.source.padEnd(12)}${row.amount.toFixed(2).padStart(15)}`,
    );
    lineCount += 1;
    // REQ-F-105: accumulate page and account totals.
    pageTotal = pageTotal.plus(row.amount);
    accountTotal = accountTotal.plus(row.amount);
    // REQ-F-106: page break every PAGE_SIZE lines.
    if (lineCount % PAGE_SIZE === 0) {
      writePageTotal();
      lines.push(...headerLines(startDate, endDate));
      lineCount += 4;
    }
  }

  // REQ-F-109/REQ-F-110: final account, page, and grand totals at EOF.
  if (prevCard !== null) writeAccountTotal();
  writePageTotal();
  lines.push(`${'GRAND TOTAL:'.padEnd(102)}${grandTotal.toFixed(2).padStart(15)}`);
  return lines.join('\n') + '\n';
}
