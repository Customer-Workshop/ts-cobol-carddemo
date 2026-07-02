// Bill payment, forward-engineered from legacy COBIL00C
// (docs/spec/InteractiveNavigationandMenuControl §17, REQ-F-150..REQ-F-167).

import { ConflictException, Injectable, NotFoundException } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { billPayRequestSchema } from '@carddemo/shared';
import { PrismaService } from '../prisma/prisma.service';
import { parseWith } from '../common/zod-validation';
import { serializeAccount, serializeTransaction } from '../common/serializers';
import { nextTransactionId } from '../transactions/transaction-id';

@Injectable()
export class BillPayService {
  constructor(private readonly prisma: PrismaService) {}

  async payBill(body: unknown): Promise<{
    transaction: Record<string, unknown>;
    account: Record<string, unknown>;
  }> {
    // REQ-F-152, REQ-F-153: account id required; confirmation must be affirmative
    // (the REST contract requires confirm === true).
    const { accountId } = parseWith(billPayRequestSchema, body);

    // REQ-F-154: account lookup by id; not found -> "Account ID NOT found...".
    const account = await this.prisma.account.findUnique({ where: { id: accountId } });
    if (!account) {
      throw new NotFoundException('Account ID NOT found...');
    }

    // REQ-F-156: zero or negative balance -> nothing to pay.
    if (account.currentBalance.lessThanOrEqualTo(0)) {
      throw new ConflictException('You have nothing to pay...');
    }

    // REQ-F-157: card cross-reference lookup by account id to get the card number.
    const xref = await this.prisma.cardXref.findFirst({ where: { accountId } });
    if (!xref) {
      throw new NotFoundException('Account ID NOT found in cross-reference...');
    }

    const now = new Date();
    // REQ-F-158..REQ-F-164, REQ-N-002: id generation, transaction write, and
    // balance update execute as a single atomic operation.
    const [transaction, updatedAccount] = await this.prisma.$transaction(async (tx) => {
      const highest = await tx.transaction.findFirst({
        orderBy: { id: 'desc' },
        select: { id: true },
      });
      // REQ-F-162: bill payment transaction record contents.
      const created = await tx.transaction.create({
        data: {
          id: nextTransactionId(highest?.id ?? null),
          typeCode: '02',
          categoryCode: 2,
          source: 'POS TERM',
          description: 'BILL PAYMENT - ONLINE',
          amount: account.currentBalance,
          merchantId: '999999999',
          merchantName: 'BILL PAYMENT',
          merchantCity: 'N/A',
          merchantZip: 'N/A',
          cardNumber: xref.cardNumber,
          originalTs: now,
          processedTs: now,
        },
      });
      // REQ-F-164: new balance = current balance minus the transaction amount (full payment).
      const updated = await tx.account.update({
        where: { id: accountId },
        data: { currentBalance: new Prisma.Decimal(0) },
      });
      return [created, updated];
    });

    // REQ-F-165: success response carries the new transaction id.
    return { transaction: serializeTransaction(transaction), account: serializeAccount(updatedAccount) };
  }
}
