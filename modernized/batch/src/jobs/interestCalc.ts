// interest-calc — modernizes CBACT04C / INTCALC.
// StatementandReportGeneration REQ-F-077..REQ-F-087, REQ-N-001:
// for each account's transaction category balances, look up the disclosure
// group rate (falling back to the DEFAULT group), compute monthly interest,
// write system-generated interest transactions, add accumulated interest to
// the account balance, and reset the cycle credit/debit totals at the
// account boundary (single atomic operation per account).
import { Prisma } from '@prisma/client';
import { prisma } from '../lib/prisma';
import { JobResult } from '../lib/jobRun';
import { Logger } from '../lib/logger';
import { monthlyInterest } from '../lib/interest';

const D = Prisma.Decimal;

const p = (n: number, w: number): string => String(n).padStart(w, '0');

export async function interestCalc(logger: Logger): Promise<JobResult> {
  // REQ-F-078: read category balances grouped by account (account boundaries).
  const balances = await prisma.transactionCategoryBalance.findMany({
    orderBy: [{ accountId: 'asc' }, { typeCode: 'asc' }, { categoryCode: 'asc' }],
  });

  const byAccount = new Map<string, typeof balances>();
  for (const b of balances) {
    const group = byAccount.get(b.accountId) ?? [];
    group.push(b);
    byAccount.set(b.accountId, group);
  }

  const now = new Date();
  // REQ-F-084: unique transaction id = processing date + incremented suffix.
  const datePrefix = `${p(now.getUTCFullYear(), 4)}${p(now.getUTCMonth() + 1, 2)}${p(now.getUTCDate(), 2)}`;
  // Continue the suffix from any interest transactions already generated
  // today so same-day re-runs never collide on transaction ids.
  const lastToday = await prisma.transaction.findFirst({
    where: { id: { startsWith: datePrefix } },
    orderBy: { id: 'desc' },
  });
  let suffix = lastToday ? Number(lastToday.id.slice(datePrefix.length)) : 0;
  let accountsProcessed = 0;
  let interestTxns = 0;

  for (const [accountId, group] of byAccount) {
    // REQ-F-079: account master lookup at the account boundary.
    const account = await prisma.account.findUnique({ where: { id: accountId } });
    if (!account) {
      logger.error(`account ${accountId} not found for category balances; skipping`);
      continue;
    }
    // REQ-F-080: card cross-reference lookup by account id.
    const xref = await prisma.cardXref.findFirst({
      where: { accountId },
      orderBy: { cardNumber: 'asc' },
    });
    if (!xref) {
      logger.error(`no card xref for account ${accountId}; skipping`);
      continue;
    }

    let totalInterest = new D(0);
    const creates: Prisma.TransactionCreateManyInput[] = [];

    for (const bal of group) {
      // REQ-F-081: disclosure group lookup by (group, type, category).
      let disc = await prisma.disclosureGroup.findUnique({
        where: {
          accountGroupId_typeCode_categoryCode: {
            accountGroupId: account.groupId ?? 'DEFAULT',
            typeCode: bal.typeCode,
            categoryCode: bal.categoryCode,
          },
        },
      });
      // REQ-F-082: fall back to the DEFAULT group when not found.
      if (!disc) {
        disc = await prisma.disclosureGroup.findUnique({
          where: {
            accountGroupId_typeCode_categoryCode: {
              accountGroupId: 'DEFAULT',
              typeCode: bal.typeCode,
              categoryCode: bal.categoryCode,
            },
          },
        });
      }
      if (!disc || disc.interestRate.isZero()) continue;

      // REQ-F-083: monthly interest = balance * rate / 1200.
      const interest = monthlyInterest(bal.balance, disc.interestRate);
      if (interest.isZero()) continue;
      totalInterest = totalInterest.plus(interest);
      suffix += 1;
      // REQ-F-084: system-generated interest transaction (type 01, category 05).
      creates.push({
        id: `${datePrefix}${p(suffix, 8)}`,
        typeCode: '01',
        categoryCode: 5,
        source: 'System',
        description: `Int. for a/c ${accountId}`,
        amount: interest,
        merchantId: '000000000',
        merchantName: '',
        merchantCity: '',
        merchantZip: '',
        cardNumber: xref.cardNumber,
        originalTs: now,
        processedTs: now,
      });
    }

    // REQ-F-085 / REQ-N-001: atomic per-account settlement — add interest,
    // reset cycle credit/debit, write interest transactions.
    await prisma.$transaction([
      ...creates.map((data) => prisma.transaction.create({ data })),
      prisma.account.update({
        where: { id: accountId },
        data: {
          currentBalance: { increment: totalInterest },
          currCycleCredit: new D(0),
          currCycleDebit: new D(0),
        },
      }),
    ]);
    interestTxns += creates.length;
    accountsProcessed += 1;
    logger.info(`account ${accountId}: interest ${totalInterest.toFixed(2)} (${creates.length} txns)`);
  }

  return {
    counts: {
      categoryBalancesRead: balances.length,
      accountsProcessed,
      interestTransactions: interestTxns,
    },
  };
}
