// archive-data — modernizes the GDG/backup JCL (TRANBKP, COMBTRAN, TCATBALF
// backup). DataBackup,Archival,andIndexing REQ-F-001..REQ-F-011: versioned
// snapshots of the transaction master and transaction category balance
// stores, exported as JSON and CSV files with generation-style version
// numbers, catalogued as rows in the reports table, with automatic retention
// enforcement (keep the last N generations, default 5).
import * as fs from 'fs';
import * as path from 'path';
import { prisma } from '../lib/prisma';
import { JobResult } from '../lib/jobRun';
import { Logger, OUTPUT_DIR } from '../lib/logger';

export const DEFAULT_RETENTION = 5; // REQ-F-001..REQ-F-006 (OQ-001)
export const TRANSACT_BKUP = 'TRANSACT.BKUP';
export const TCATBALF_BKUP = 'TCATBALF.BKUP';

const csvEscape = (v: string): string => (/[",\n]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v);

function toCsv(rows: Record<string, unknown>[]): string {
  if (rows.length === 0) return '';
  const first = rows[0];
  if (!first) return '';
  const headers = Object.keys(first);
  const lines = [headers.join(',')];
  for (const row of rows) {
    lines.push(headers.map((h) => csvEscape(String(row[h] ?? ''))).join(','));
  }
  return lines.join('\n') + '\n';
}

async function archiveDataset(
  logger: Logger,
  name: string,
  rows: Record<string, unknown>[],
  retention: number,
  archiveDir: string,
): Promise<number> {
  // GDG-style generation numbering via the versioned reports table.
  const latest = await prisma.report.findFirst({ where: { name }, orderBy: { version: 'desc' } });
  const version = (latest?.version ?? 0) + 1;
  const json = JSON.stringify(rows, null, 2);
  const csv = toCsv(rows);
  const base = path.join(archiveDir, `${name}.G${String(version).padStart(4, '0')}V00`);
  fs.writeFileSync(`${base}.json`, json);
  fs.writeFileSync(`${base}.csv`, csv);

  const today = new Date();
  // REQ-F-011: catalog the backup upon successful completion.
  await prisma.report.create({
    data: {
      name,
      version,
      startDate: today,
      endDate: today,
      content: csv,
    },
  });

  // REQ-F-001..REQ-F-006: retention — remove generations beyond the limit.
  const superseded = await prisma.report.findMany({
    where: { name, version: { lte: version - retention } },
  });
  for (const old of superseded) {
    await prisma.report.delete({ where: { id: old.id } });
    const oldBase = path.join(archiveDir, `${name}.G${String(old.version).padStart(4, '0')}V00`);
    for (const ext of ['.json', '.csv']) {
      fs.rmSync(`${oldBase}${ext}`, { force: true });
    }
    logger.info(`retention: removed ${name} generation ${old.version}`);
  }

  logger.info(`archived ${name} G${version} (${rows.length} records)`);
  return rows.length;
}

export async function archiveData(
  logger: Logger,
  params: Record<string, string>,
): Promise<JobResult> {
  const retention = params.retention ? Number(params.retention) : DEFAULT_RETENTION;
  if (!Number.isInteger(retention) || retention < 1) {
    throw new Error(`invalid retention: ${params.retention}`);
  }
  const archiveDir = path.join(OUTPUT_DIR, 'archive');
  fs.mkdirSync(archiveDir, { recursive: true });

  // REQ-F-011: full unload of the transaction master, in key order.
  const transactions = await prisma.transaction.findMany({ orderBy: { id: 'asc' } });
  const txnRows = transactions.map((t) => ({
    ...t,
    amount: t.amount.toFixed(2),
    originalTs: t.originalTs.toISOString(),
    processedTs: t.processedTs.toISOString(),
  }));
  const txnCount = await archiveDataset(logger, TRANSACT_BKUP, txnRows, retention, archiveDir);

  // REQ-F-008/REQ-F-009: category balances sorted by account, type, category.
  const balances = await prisma.transactionCategoryBalance.findMany({
    orderBy: [{ accountId: 'asc' }, { typeCode: 'asc' }, { categoryCode: 'asc' }],
  });
  const balRows = balances.map((b) => ({ ...b, balance: b.balance.toFixed(2) }));
  const balCount = await archiveDataset(logger, TCATBALF_BKUP, balRows, retention, archiveDir);

  return { counts: { transactionsArchived: txnCount, categoryBalancesArchived: balCount } };
}
