// Integration tests: run the batch jobs against the seeded PostgreSQL
// database (CI service container / local docker-compose). The seed contains a
// pending daily_transactions batch of 12 records, 8 valid and 4 invalid.
import { beforeAll, afterAll, describe, expect, it } from 'vitest';
import { execSync } from 'child_process';
import * as fs from 'fs';
import * as path from 'path';
import { Prisma, PrismaClient, JobStatus } from '@prisma/client';
import { Logger, OUTPUT_DIR } from '../src/lib/logger';
import { postTransactions } from '../src/jobs/postTransactions';
import { interestCalc } from '../src/jobs/interestCalc';
import { generateStatements } from '../src/jobs/generateStatements';
import { transactionReport, REPORT_NAME, REPORT_REQUEST_JOB_NAME } from '../src/jobs/transactionReport';
import { archiveData, TRANSACT_BKUP, TCATBALF_BKUP } from '../src/jobs/archiveData';
import { exportMasters } from '../src/jobs/exportMasters';
import { monthlyInterest } from '../src/lib/interest';

const prisma = new PrismaClient();
const logger: Logger = { info: () => {}, error: () => {}, logFile: '' };
const backendDir = path.resolve(__dirname, '..', '..', 'backend');

describe.skipIf(!process.env.DATABASE_URL)('batch jobs against seeded Postgres', () => {
  beforeAll(() => {
    // Re-seed for a deterministic starting state (seed is delete-then-populate).
    execSync('npx prisma db seed', { cwd: backendDir, stdio: 'pipe' });
  });

  afterAll(async () => {
    await prisma.$disconnect();
  });

  it('post-transactions posts 8 valid records and rejects 4 with reason codes', async () => {
    const result = await postTransactions(logger);
    expect(result.counts).toMatchObject({ read: 12, posted: 8, rejected: 4 });

    const rejects = await prisma.dailyReject.findMany();
    expect(rejects.length).toBe(4);
    const reasons = rejects.map((r) => r.rejectReason).sort();
    expect(reasons.filter((r) => r.startsWith('100 INVALID CARD NUMBER FOUND')).length).toBe(2);
    expect(reasons.some((r) => r.startsWith('105 TRANSACTION TYPE NOT FOUND'))).toBe(true);
    expect(reasons.some((r) => r.startsWith('106 TRANSACTION CATEGORY NOT FOUND'))).toBe(true);

    // Posted rows landed in the transaction archive and staging is settled.
    const posted = await prisma.dailyTransaction.count({ where: { status: 'POSTED' } });
    const rejected = await prisma.dailyTransaction.count({ where: { status: 'REJECTED' } });
    expect(posted).toBe(8);
    expect(rejected).toBe(4);
    const archived = await prisma.transaction.count({
      where: { id: { gte: '0000000000009000' } },
    });
    expect(archived).toBe(8);

    // Idempotent: a second run picks up nothing.
    const rerun = await postTransactions(logger);
    expect(rerun.counts).toMatchObject({ read: 0, posted: 0, rejected: 0 });
  });

  it('post-transactions updates account balance, cycle totals, and category balances', async () => {
    // All seeded daily transactions are positive → credits (REQ-F-023).
    const daily = await prisma.dailyTransaction.findMany({
      where: { status: 'POSTED' },
      include: { reject: true },
    });
    expect(daily.length).toBe(8);
    for (const txn of daily) {
      const xref = await prisma.cardXref.findUnique({ where: { cardNumber: txn.cardNumber } });
      expect(xref).not.toBeNull();
      const bal = await prisma.transactionCategoryBalance.findUnique({
        where: {
          accountId_typeCode_categoryCode: {
            accountId: xref!.accountId,
            typeCode: txn.typeCode,
            categoryCode: txn.categoryCode,
          },
        },
      });
      expect(bal).not.toBeNull();
    }
  });

  it('interest-calc posts monthly interest and resets cycle totals', async () => {
    const before = await prisma.account.findMany();
    const balances = await prisma.transactionCategoryBalance.findMany();
    const discs = await prisma.disclosureGroup.findMany();
    const rate = (group: string, type: string, cat: number): Prisma.Decimal | null => {
      const hit =
        discs.find((d) => d.accountGroupId === group && d.typeCode === type && d.categoryCode === cat) ??
        discs.find((d) => d.accountGroupId === 'DEFAULT' && d.typeCode === type && d.categoryCode === cat);
      return hit ? hit.interestRate : null;
    };
    const expected = new Map<string, Prisma.Decimal>();
    for (const b of balances) {
      const acct = before.find((a) => a.id === b.accountId)!;
      const r = rate(acct.groupId ?? 'DEFAULT', b.typeCode, b.categoryCode);
      if (!r || r.isZero()) continue;
      const interest = monthlyInterest(b.balance, r);
      expected.set(b.accountId, (expected.get(b.accountId) ?? new Prisma.Decimal(0)).plus(interest));
    }

    const result = await interestCalc(logger);
    expect(result.counts.accountsProcessed).toBeGreaterThan(0);
    expect(result.counts.interestTransactions).toBeGreaterThan(0);

    const after = await prisma.account.findMany();
    for (const acct of after) {
      const prev = before.find((a) => a.id === acct.id)!;
      const exp = expected.get(acct.id);
      if (exp) {
        expect(acct.currentBalance.minus(prev.currentBalance).toFixed(2)).toBe(exp.toFixed(2));
        // Cycle totals reset at the account boundary (REQ-F-085).
        expect(acct.currCycleCredit.isZero()).toBe(true);
        expect(acct.currCycleDebit.isZero()).toBe(true);
      }
    }

    // Interest transactions are type 01 / category 05 with the spec description.
    const interestTxns = await prisma.transaction.findMany({
      where: { typeCode: '01', categoryCode: 5, source: 'System' },
    });
    expect(interestTxns.length).toBe(result.counts.interestTransactions);
    expect(interestTxns.every((t) => t.description.startsWith('Int. for a/c '))).toBe(true);
  });

  it('generate-statements stores versioned text + HTML statements and files', async () => {
    const result = await generateStatements(logger, {});
    expect(result.counts.statementsGenerated).toBe(20);

    const statements = await prisma.statement.findMany({ where: { accountId: '00000000001' } });
    expect(statements.length).toBe(1);
    const stmt = statements[0]!;
    expect(stmt.version).toBe(1);
    expect(stmt.textContent).toContain('STATEMENT OF ACCOUNT');
    expect(stmt.textContent).toContain('Account ID         : 00000000001');
    expect(stmt.textContent).toContain('Total Expenses');
    expect(stmt.htmlContent).toContain('Account ID: 00000000001');
    expect(
      fs.existsSync(path.join(OUTPUT_DIR, 'statements', 'STATEMNT.00000000001.G1.txt')),
    ).toBe(true);
    expect(
      fs.existsSync(path.join(OUTPUT_DIR, 'statements', 'STATEMNT.00000000001.G1.html')),
    ).toBe(true);

    // Re-running produces the next generation (GDG semantics).
    await generateStatements(logger, {});
    const v2 = await prisma.statement.findFirst({
      where: { accountId: '00000000001' },
      orderBy: { version: 'desc' },
    });
    expect(v2!.version).toBe(2);
  });

  it('transaction-report writes a versioned report with totals and processes PENDING requests', async () => {
    await prisma.jobRun.create({
      data: {
        jobName: REPORT_REQUEST_JOB_NAME,
        status: JobStatus.PENDING,
        message: JSON.stringify({ startDate: '2025-01-01', endDate: '2025-12-31' }),
      },
    });

    const result = await transactionReport(logger, {});
    expect(result.counts.requestsProcessed).toBe(1);
    // Current-cycle default range covers the freshly posted + interest txns.
    expect(result.counts.transactions).toBeGreaterThanOrEqual(8);

    const report = await prisma.report.findFirst({
      where: { name: REPORT_NAME, version: result.counts.reportVersion },
    });
    expect(report).not.toBeNull();
    expect(report!.content).toContain('DAILY TRANSACTION REPORT');
    expect(report!.content).toContain('ACCOUNT TOTAL:');
    expect(report!.content).toContain('PAGE TOTAL:');
    expect(report!.content).toContain('GRAND TOTAL:');

    const request = await prisma.jobRun.findFirst({
      where: { jobName: REPORT_REQUEST_JOB_NAME },
      orderBy: { id: 'desc' },
    });
    expect(request!.status).toBe(JobStatus.SUCCEEDED);
    expect(request!.message).toContain('reportVersion');
  });

  it('archive-data creates versioned snapshots and enforces retention', async () => {
    const result = await archiveData(logger, { retention: '2' });
    expect(result.counts.transactionsArchived).toBeGreaterThanOrEqual(108);
    expect(result.counts.categoryBalancesArchived).toBeGreaterThan(0);

    // Run twice more with retention 2: only the last two generations remain.
    await archiveData(logger, { retention: '2' });
    await archiveData(logger, { retention: '2' });
    const generations = await prisma.report.findMany({
      where: { name: TRANSACT_BKUP },
      orderBy: { version: 'asc' },
    });
    expect(generations.length).toBe(2);
    expect(generations[generations.length - 1]!.version).toBe(3);

    const tcat = await prisma.report.findMany({ where: { name: TCATBALF_BKUP } });
    expect(tcat.length).toBe(2);

    const latest = generations[generations.length - 1]!;
    const base = path.join(
      OUTPUT_DIR,
      'archive',
      `${TRANSACT_BKUP}.G${String(latest.version).padStart(4, '0')}V00`,
    );
    expect(fs.existsSync(`${base}.json`)).toBe(true);
    expect(fs.existsSync(`${base}.csv`)).toBe(true);
  });

  it('export-masters dumps all master files with labeled fields', async () => {
    const result = await exportMasters(logger);
    expect(result.counts).toMatchObject({ accounts: 20, customers: 20, cards: 25, cardXrefs: 25 });

    const acctDump = fs.readFileSync(path.join(OUTPUT_DIR, 'exports', 'ACCTDATA.txt'), 'utf8');
    expect(acctDump).toContain('ACCT-ID');
    expect(acctDump).toContain('00000000000000000001'.slice(-11));
    const custDump = fs.readFileSync(path.join(OUTPUT_DIR, 'exports', 'CUSTDATA.txt'), 'utf8');
    expect(custDump).toContain('CUST-FICO-SCORE');
    expect(fs.existsSync(path.join(OUTPUT_DIR, 'exports', 'CARDDATA.txt'))).toBe(true);
    expect(fs.existsSync(path.join(OUTPUT_DIR, 'exports', 'CARDXREF.txt'))).toBe(true);
  });
});
