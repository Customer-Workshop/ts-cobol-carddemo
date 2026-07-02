import { describe, expect, it } from 'vitest';
import { Prisma } from '@prisma/client';
import { renderTransactionReport, ReportRow } from '../src/lib/reportFormat';
import {
  renderTextStatement,
  renderHtmlStatement,
  statementTotal,
  StatementData,
} from '../src/lib/statementFormat';

const D = (v: string | number) => new Prisma.Decimal(v);

function row(overrides: Partial<ReportRow>): ReportRow {
  return {
    transactionId: '0000000000000001',
    accountId: '00000000001',
    cardNumber: '4000000000000001',
    typeCode: '01',
    typeDescription: 'Purchase',
    categoryCode: 1,
    categoryDescription: 'Regular Sales Draft',
    source: 'POS TERM',
    amount: D('10.00'),
    ...overrides,
  };
}

describe('renderTransactionReport (CBTRN03C)', () => {
  it('writes headers, detail lines, and a grand total', () => {
    const report = renderTransactionReport(
      [row({}), row({ transactionId: '0000000000000002', amount: D('5.50') })],
      new Date(Date.UTC(2026, 6, 1)),
      new Date(Date.UTC(2026, 6, 31)),
    );
    expect(report).toContain('DAILY TRANSACTION REPORT FROM 2026-07-01 TO 2026-07-31');
    expect(report).toContain('0000000000000001');
    expect(report).toContain('Regular Sales Draft');
    expect(report).toContain('GRAND TOTAL:');
    expect(report).toContain('15.50');
  });

  it('writes an account total on card-number control breaks (REQ-F-097/REQ-F-108)', () => {
    const report = renderTransactionReport(
      [
        row({ amount: D('10.00') }),
        row({ transactionId: '0000000000000002', amount: D('20.00') }),
        row({
          transactionId: '0000000000000003',
          cardNumber: '4000000000000099',
          accountId: '00000000002',
          amount: D('7.00'),
        }),
      ],
      new Date(Date.UTC(2026, 6, 1)),
      new Date(Date.UTC(2026, 6, 31)),
    );
    const accountTotals = report.match(/ACCOUNT TOTAL:/g) ?? [];
    expect(accountTotals.length).toBe(2);
    expect(report).toContain('30.00'); // first card's account total
    expect(report).toContain('37.00'); // grand total
  });

  it('emits page totals and repeated headers every 20 lines (REQ-F-106)', () => {
    const rows = Array.from({ length: 40 }, (_, i) =>
      row({ transactionId: String(i + 1).padStart(16, '0'), amount: D('1.00') }),
    );
    const report = renderTransactionReport(
      rows,
      new Date(Date.UTC(2026, 6, 1)),
      new Date(Date.UTC(2026, 6, 31)),
    );
    expect((report.match(/PAGE TOTAL:/g) ?? []).length).toBeGreaterThanOrEqual(2);
    expect((report.match(/DAILY TRANSACTION REPORT/g) ?? []).length).toBeGreaterThanOrEqual(2);
  });
});

describe('statement rendering (CBSTM03A/CBSTM03B)', () => {
  const data: StatementData = {
    customer: {
      firstName: 'Ada',
      middleName: null,
      lastName: 'Lovelace',
      addressLine1: '1 Analytical Way',
      addressLine2: null,
      addressLine3: 'Mathtown',
      stateCode: 'NY',
      countryCode: 'USA',
      zipCode: '10001',
      ficoCreditScore: 780,
    },
    account: { id: '00000000001', currentBalance: D('123.45') },
    transactions: [
      { id: '0000000000000001', description: 'Purchase at Babbage & Co <tags>', amount: D('100.00') },
      { id: '0000000000000002', description: 'Coffee', amount: D('23.45') },
    ],
    periodStart: new Date(Date.UTC(2026, 6, 1)),
    periodEnd: new Date(Date.UTC(2026, 6, 31)),
  };

  it('totals transaction amounts', () => {
    expect(statementTotal(data.transactions).toFixed(2)).toBe('123.45');
  });

  it('renders the text statement with customer, account, and totals (REQ-F-068..073)', () => {
    const text = renderTextStatement(data);
    expect(text).toContain('STATEMENT OF ACCOUNT');
    expect(text).toContain('Ada Lovelace');
    expect(text).toContain('Account ID         : 00000000001');
    expect(text).toContain('FICO Score         : 780');
    expect(text).toContain('0000000000000001');
    expect(text).toContain('Total Expenses');
    expect(text).toContain('123.45');
  });

  it('renders the HTML statement with escaped content (REQ-F-069/REQ-F-074)', () => {
    const html = renderHtmlStatement(data);
    expect(html).toContain('<html>');
    expect(html).toContain('Account ID: 00000000001');
    expect(html).toContain('&lt;tags&gt;');
    expect(html).not.toContain('<tags>');
    expect(html).toContain('Total Expenses');
  });
});
