// generate-statements — modernizes CBSTM03A/CBSTM03B (statement generation
// pipeline). StatementandReportGeneration REQ-F-057..REQ-F-076: per account,
// gather customer + account + cycle transactions (via the card
// cross-reference), and produce plain-text and HTML statements. Content is
// stored in the versioned statements table (replacing the STATEMNT.PS /
// STATEMNT.HTML GDGs) and also written to output files.
import * as fs from 'fs';
import * as path from 'path';
import { prisma } from '../lib/prisma';
import { JobResult } from '../lib/jobRun';
import { Logger, OUTPUT_DIR } from '../lib/logger';
import { cycleStart, cycleEnd } from '../lib/timestamp';
import { renderTextStatement, renderHtmlStatement, StatementData } from '../lib/statementFormat';

export async function generateStatements(
  logger: Logger,
  params: Record<string, string>,
): Promise<JobResult> {
  const now = new Date();
  const periodStart = params.start ? new Date(params.start) : cycleStart(now);
  const periodEnd = params.end ? new Date(params.end) : cycleEnd(now);
  const statementsDir = path.join(OUTPUT_DIR, 'statements');
  fs.mkdirSync(statementsDir, { recursive: true });

  // REQ-F-067: iterate cross-reference records; keyed customer + account lookups.
  const xrefs = await prisma.cardXref.findMany({
    include: { customer: true, account: true },
    orderBy: { cardNumber: 'asc' },
  });

  // Group cards by account: one statement per account covering all its cards.
  const accounts = new Map<string, typeof xrefs>();
  for (const x of xrefs) {
    const group = accounts.get(x.accountId) ?? [];
    group.push(x);
    accounts.set(x.accountId, group);
  }

  let generated = 0;
  for (const [accountId, group] of accounts) {
    const first = group[0];
    if (!first) continue;
    const cardNumbers = group.map((x) => x.cardNumber);
    // REQ-F-065: transactions grouped by card number, in id order.
    const transactions = await prisma.transaction.findMany({
      where: {
        cardNumber: { in: cardNumbers },
        processedTs: { gte: periodStart, lte: new Date(periodEnd.getTime() + 86_399_999) },
      },
      orderBy: [{ cardNumber: 'asc' }, { id: 'asc' }],
    });

    const data: StatementData = {
      customer: first.customer,
      account: first.account,
      transactions,
      periodStart,
      periodEnd,
    };
    const textContent = renderTextStatement(data);
    const htmlContent = renderHtmlStatement(data);

    // Versioned like the legacy GDG: next generation per account.
    const latest = await prisma.statement.findFirst({
      where: { accountId },
      orderBy: { version: 'desc' },
    });
    const version = (latest?.version ?? 0) + 1;
    await prisma.statement.create({
      data: { accountId, version, periodStart, periodEnd, textContent, htmlContent },
    });
    // REQ-F-062: text + HTML statement output files.
    fs.writeFileSync(path.join(statementsDir, `STATEMNT.${accountId}.G${version}.txt`), textContent);
    fs.writeFileSync(path.join(statementsDir, `STATEMNT.${accountId}.G${version}.html`), htmlContent);
    generated += 1;
    logger.info(`statement v${version} for account ${accountId} (${transactions.length} txns)`);
  }

  return { counts: { accounts: accounts.size, statementsGenerated: generated } };
}
