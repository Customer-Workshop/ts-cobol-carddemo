// transaction-report — modernizes CBTRN03C / TRANREPT.
// StatementandReportGeneration REQ-F-088..REQ-F-111: date-range-driven detail
// report of posted transactions (filter by process date, sort by card number),
// enriched via transaction_types / transaction_categories / card_xref lookups,
// with page totals, account totals, and a grand total. The report is stored as
// a new version in the reports table (replacing the TRANREPT GDG) and written
// to an output file. Also processes PENDING report requests queued in
// job_runs by the backend.
import * as fs from 'fs';
import * as path from 'path';
import { JobStatus } from '@prisma/client';
import { prisma } from '../lib/prisma';
import { JobResult } from '../lib/jobRun';
import { Logger, OUTPUT_DIR } from '../lib/logger';
import { cycleStart, cycleEnd } from '../lib/timestamp';
import { renderTransactionReport, ReportRow } from '../lib/reportFormat';

export const REPORT_NAME = 'TRANREPT';
export const REPORT_REQUEST_JOB_NAME = 'transaction-report-request';

async function produceReport(
  logger: Logger,
  startDate: Date,
  endDate: Date,
): Promise<{ version: number; rowCount: number }> {
  const endInclusive = new Date(endDate.getTime() + 86_399_999);
  // REQ-F-089/REQ-F-090: filter by process date range, sort by card number.
  const transactions = await prisma.transaction.findMany({
    where: { processedTs: { gte: startDate, lte: endInclusive } },
    orderBy: [{ cardNumber: 'asc' }, { id: 'asc' }],
    include: { type: true, category: true },
  });

  // REQ-F-098/REQ-F-099: card number → account id via the cross-reference.
  const cardNumbers = [...new Set(transactions.map((t) => t.cardNumber))];
  const xrefs = await prisma.cardXref.findMany({ where: { cardNumber: { in: cardNumbers } } });
  const accountByCard = new Map(xrefs.map((x) => [x.cardNumber, x.accountId]));

  // REQ-F-100..REQ-F-103: type and category description lookups.
  const rows: ReportRow[] = transactions.map((t) => ({
    transactionId: t.id,
    accountId: accountByCard.get(t.cardNumber) ?? '***********',
    cardNumber: t.cardNumber,
    typeCode: t.typeCode,
    typeDescription: t.type.description,
    categoryCode: t.categoryCode,
    categoryDescription: t.category.description,
    source: t.source,
    amount: t.amount,
  }));

  const content = renderTransactionReport(rows, startDate, endDate);

  // REQ-F-010 (DataBackup,Archival,andIndexing): versioned report store.
  const latest = await prisma.report.findFirst({
    where: { name: REPORT_NAME },
    orderBy: { version: 'desc' },
  });
  const version = (latest?.version ?? 0) + 1;
  await prisma.report.create({
    data: { name: REPORT_NAME, version, startDate, endDate, content },
  });

  const reportsDir = path.join(OUTPUT_DIR, 'reports');
  fs.mkdirSync(reportsDir, { recursive: true });
  const file = path.join(reportsDir, `${REPORT_NAME}.G${version}.txt`);
  fs.writeFileSync(file, content);
  logger.info(`report ${REPORT_NAME} v${version}: ${rows.length} transactions -> ${file}`);
  return { version, rowCount: rows.length };
}

export async function transactionReport(
  logger: Logger,
  params: Record<string, string>,
): Promise<JobResult> {
  // REQ-F-092/REQ-F-093: date parameters, defaulting to the current cycle.
  const now = new Date();
  const startDate = params.start ? new Date(params.start) : cycleStart(now);
  const endDate = params.end ? new Date(params.end) : cycleEnd(now);
  if (Number.isNaN(startDate.getTime()) || Number.isNaN(endDate.getTime())) {
    throw new Error(`invalid date parameters: start=${params.start} end=${params.end}`);
  }

  const main = await produceReport(logger, startDate, endDate);
  let requestsProcessed = 0;

  // Process PENDING report requests queued by the backend in job_runs.
  const pendingRequests = await prisma.jobRun.findMany({
    where: { jobName: REPORT_REQUEST_JOB_NAME, status: JobStatus.PENDING },
    orderBy: { id: 'asc' },
  });
  for (const request of pendingRequests) {
    let reqStart = startDate;
    let reqEnd = endDate;
    try {
      const parsed: unknown = request.message ? JSON.parse(request.message) : {};
      if (parsed && typeof parsed === 'object') {
        const { startDate: s, endDate: e } = parsed as { startDate?: string; endDate?: string };
        if (s) reqStart = new Date(s);
        if (e) reqEnd = new Date(e);
      }
    } catch {
      logger.error(`report request #${request.id}: unparseable params, using defaults`);
    }
    try {
      const produced = await produceReport(logger, reqStart, reqEnd);
      await prisma.jobRun.update({
        where: { id: request.id },
        data: {
          status: JobStatus.SUCCEEDED,
          completedAt: new Date(),
          message: JSON.stringify({
            params: { startDate: reqStart.toISOString(), endDate: reqEnd.toISOString() },
            reportName: REPORT_NAME,
            reportVersion: produced.version,
            counts: { transactions: produced.rowCount },
          }),
        },
      });
      requestsProcessed += 1;
    } catch (err) {
      await prisma.jobRun.update({
        where: { id: request.id },
        data: {
          status: JobStatus.FAILED,
          completedAt: new Date(),
          message: JSON.stringify({ error: err instanceof Error ? err.message : String(err) }),
        },
      });
      logger.error(`report request #${request.id} failed`);
    }
  }

  return {
    counts: {
      transactions: main.rowCount,
      reportVersion: main.version,
      requestsProcessed,
    },
  };
}
