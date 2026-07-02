// Statement formatting — modernizes CBSTM03A/CBSTM03B statement layout.
// StatementandReportGeneration REQ-F-068..REQ-F-074: header, customer name and
// address, account details (id, balance, FICO), transaction summary with
// detail lines, running total, and matching HTML document structure.
import { Prisma } from '@prisma/client';

export interface StatementCustomer {
  firstName: string;
  middleName: string | null;
  lastName: string;
  addressLine1: string;
  addressLine2: string | null;
  addressLine3: string | null;
  stateCode: string;
  countryCode: string;
  zipCode: string;
  ficoCreditScore: number;
}

export interface StatementAccount {
  id: string;
  currentBalance: Prisma.Decimal;
}

export interface StatementTransaction {
  id: string;
  description: string;
  amount: Prisma.Decimal;
}

export interface StatementData {
  customer: StatementCustomer;
  account: StatementAccount;
  transactions: StatementTransaction[];
  periodStart: Date;
  periodEnd: Date;
}

const SEP = '*'.repeat(80);
const iso = (d: Date): string => d.toISOString().slice(0, 10);

export function customerName(c: StatementCustomer): string {
  return [c.firstName, c.middleName, c.lastName].filter(Boolean).join(' ');
}

export function statementTotal(transactions: StatementTransaction[]): Prisma.Decimal {
  return transactions.reduce((sum, t) => sum.plus(t.amount), new Prisma.Decimal(0));
}

/** Plain-text statement (legacy STATEMNT.PS). REQ-F-068, REQ-F-070..REQ-F-073. */
export function renderTextStatement(data: StatementData): string {
  const { customer, account, transactions } = data;
  const lines: string[] = [];
  lines.push(SEP);
  lines.push(`${' '.repeat(25)}STATEMENT OF ACCOUNT`);
  lines.push(`Period: ${iso(data.periodStart)} to ${iso(data.periodEnd)}`);
  lines.push(SEP);
  lines.push(customerName(customer));
  lines.push(customer.addressLine1);
  if (customer.addressLine2) lines.push(customer.addressLine2);
  lines.push(
    `${customer.addressLine3 ?? ''} ${customer.stateCode} ${customer.countryCode} ${customer.zipCode}`.trim(),
  );
  lines.push('');
  lines.push(`Account ID         : ${account.id}`);
  lines.push(`Current Balance    : ${account.currentBalance.toFixed(2)}`);
  lines.push(`FICO Score         : ${customer.ficoCreditScore}`);
  lines.push('');
  lines.push('TRANSACTION SUMMARY');
  lines.push('-'.repeat(80));
  lines.push(`${'Tran ID'.padEnd(18)}${'Description'.padEnd(52)}${'Amount'.padStart(10)}`);
  lines.push('-'.repeat(80));
  for (const t of transactions) {
    lines.push(`${t.id.padEnd(18)}${t.description.slice(0, 50).padEnd(52)}${t.amount.toFixed(2).padStart(10)}`);
  }
  lines.push('-'.repeat(80));
  lines.push(`${'Total Expenses'.padEnd(70)}${statementTotal(transactions).toFixed(2).padStart(10)}`);
  lines.push(SEP);
  return lines.join('\n') + '\n';
}

const esc = (s: string): string =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

/** HTML statement (legacy STATEMNT.HTML). REQ-F-069, REQ-F-074. */
export function renderHtmlStatement(data: StatementData): string {
  const { customer, account, transactions } = data;
  const rows = transactions
    .map(
      (t) =>
        `      <tr><td><p>${esc(t.id)}</p></td><td><p>${esc(t.description)}</p></td>` +
        `<td><p>${t.amount.toFixed(2)}</p></td></tr>`,
    )
    .join('\n');
  return `<!DOCTYPE html>
<html>
<head><title>Statement of Account ${esc(account.id)}</title></head>
<body>
  <table border="1">
    <tr><th colspan="3">Account ID: ${esc(account.id)}</th></tr>
    <tr><td colspan="3">${esc(customerName(customer))}<br/>${esc(customer.addressLine1)}<br/>${esc(
      [customer.addressLine3 ?? '', customer.stateCode, customer.countryCode, customer.zipCode]
        .filter(Boolean)
        .join(' '),
    )}</td></tr>
    <tr><td>Current Balance</td><td colspan="2">${account.currentBalance.toFixed(2)}</td></tr>
    <tr><td>FICO Score</td><td colspan="2">${customer.ficoCreditScore}</td></tr>
    <tr><th>Tran ID</th><th>Description</th><th>Amount</th></tr>
${rows}
    <tr><td colspan="2">Total Expenses</td><td>${statementTotal(transactions).toFixed(2)}</td></tr>
  </table>
</body>
</html>
`;
}
