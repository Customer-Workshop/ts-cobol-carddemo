// post-transactions — modernizes CBTRN02C / POSTTRAN.
// TransactionProcessingandValidation REQ-F-013..REQ-F-026, REQ-F-029..REQ-F-043:
// read pending daily transactions in order, validate each against the card
// cross-reference, account master, and reference data, then either post
// (transactions + account balances + category balances) or write a daily
// reject. Each record is processed in its own DB transaction, and only
// PENDING staging rows are picked up, so the job is idempotent/re-runnable.
import { DailyTransactionStatus, Prisma } from '@prisma/client';
import { prisma } from '../lib/prisma';
import { JobResult } from '../lib/jobRun';
import { Logger } from '../lib/logger';
import { validateDailyTransaction } from '../lib/validation';

export async function postTransactions(logger: Logger): Promise<JobResult> {
  // REQ-F-014/REQ-F-031: sequential read of remaining (PENDING) records, in order.
  const pending = await prisma.dailyTransaction.findMany({
    where: { status: DailyTransactionStatus.PENDING },
    orderBy: { id: 'asc' },
  });
  logger.info(`${pending.length} pending daily transactions`);

  let posted = 0;
  let rejected = 0;

  for (const txn of pending) {
    // REQ-F-016: keyed card lookup in the cross-reference.
    const xref = await prisma.cardXref.findUnique({ where: { cardNumber: txn.cardNumber } });
    // REQ-F-017: keyed account lookup.
    const account = xref
      ? await prisma.account.findUnique({ where: { id: xref.accountId } })
      : null;
    const typeExists =
      (await prisma.transactionType.findUnique({ where: { code: txn.typeCode } })) !== null;
    const categoryExists =
      (await prisma.transactionCategory.findUnique({
        where: { typeCode_categoryCode: { typeCode: txn.typeCode, categoryCode: txn.categoryCode } },
      })) !== null;

    const reject = validateDailyTransaction(txn, { xref, account, typeExists, categoryExists });
    const now = new Date();

    if (reject) {
      // REQ-F-020/REQ-F-037: write reject record with reason code + description.
      await prisma.$transaction([
        prisma.dailyReject.create({
          data: {
            dailyTransactionId: txn.id,
            rejectReason: `${reject.code} ${reject.description}`,
          },
        }),
        prisma.dailyTransaction.update({
          where: { id: txn.id },
          data: { status: DailyTransactionStatus.REJECTED, processedTs: now },
        }),
      ]);
      logger.info(`rejected ${txn.id} card=${txn.cardNumber}: ${reject.code} ${reject.description}`);
      rejected += 1;
      continue;
    }

    if (!account) continue; // unreachable: validation rejects missing accounts
    const acct = account;
    // REQ-F-023/REQ-F-039: balance + cycle credit/debit updates.
    const isCredit = txn.amount.greaterThanOrEqualTo(0);
    // REQ-F-021/REQ-F-022, REQ-F-024..REQ-F-026: post atomically per record.
    await prisma.$transaction([
      prisma.transaction.create({
        data: {
          id: txn.id,
          typeCode: txn.typeCode,
          categoryCode: txn.categoryCode,
          source: txn.source,
          description: txn.description,
          amount: txn.amount,
          merchantId: txn.merchantId,
          merchantName: txn.merchantName,
          merchantCity: txn.merchantCity,
          merchantZip: txn.merchantZip,
          cardNumber: txn.cardNumber,
          originalTs: txn.originalTs,
          processedTs: now,
        },
      }),
      prisma.account.update({
        where: { id: acct.id },
        data: {
          currentBalance: { increment: txn.amount },
          ...(isCredit
            ? { currCycleCredit: { increment: txn.amount } }
            : { currCycleDebit: { increment: txn.amount } }),
        },
      }),
      prisma.transactionCategoryBalance.upsert({
        where: {
          accountId_typeCode_categoryCode: {
            accountId: acct.id,
            typeCode: txn.typeCode,
            categoryCode: txn.categoryCode,
          },
        },
        create: {
          accountId: acct.id,
          typeCode: txn.typeCode,
          categoryCode: txn.categoryCode,
          balance: txn.amount,
        },
        update: { balance: { increment: txn.amount } },
      }),
      prisma.dailyTransaction.update({
        where: { id: txn.id },
        data: { status: DailyTransactionStatus.POSTED, processedTs: now },
      }),
    ] as Prisma.PrismaPromise<unknown>[]);
    logger.info(`posted ${txn.id} card=${txn.cardNumber} amount=${txn.amount.toFixed(2)}`);
    posted += 1;
  }

  return { counts: { read: pending.length, posted, rejected } };
}
