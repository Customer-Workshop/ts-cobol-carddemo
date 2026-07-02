// Transaction list / view / add, forward-engineered from legacy COTRN00C /
// COTRN01C / COTRN02C (docs/spec/InteractiveNavigationandMenuControl §34-§42).

import { Injectable, NotFoundException } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import {
  accountIdSchema,
  cardNumberSchema,
  transactionCreateSchema,
  transactionIdSchema,
} from '@carddemo/shared';
import { PrismaService } from '../prisma/prisma.service';
import { badRequest, parseWith } from '../common/zod-validation';
import { PageResult, pageResult, parsePageParams } from '../common/pagination';
import { serializeTransaction } from '../common/serializers';
import { nextTransactionId } from './transaction-id';

@Injectable()
export class TransactionsService {
  constructor(private readonly prisma: PrismaService) {}

  // REQ-F-389..REQ-F-408: paginated browse, numeric filters.
  async listTransactions(
    cardNumber?: string,
    accountId?: string,
    page?: string,
    pageSize?: string,
  ): Promise<PageResult<Record<string, unknown>>> {
    const params = parsePageParams(page, pageSize);
    if (cardNumber !== undefined && !cardNumberSchema.safeParse(cardNumber).success) {
      // REQ-F-407: transaction filters must be numeric.
      badRequest('Card number must be a 16-digit number', [
        { field: 'cardNumber', message: 'must be a 16-digit number' },
      ]);
    }
    if (accountId !== undefined && !accountIdSchema.safeParse(accountId).success) {
      badRequest('Account number must be an 11-digit number', [
        { field: 'accountId', message: 'must be an 11-digit number' },
      ]);
    }

    let cardNumbers: string[] | undefined;
    if (accountId !== undefined) {
      const xrefs = await this.prisma.cardXref.findMany({ where: { accountId } });
      cardNumbers = xrefs.map((x) => x.cardNumber);
    }
    const where: Prisma.TransactionWhereInput = {
      ...(cardNumber !== undefined ? { cardNumber } : {}),
      ...(cardNumbers !== undefined ? { cardNumber: { in: cardNumbers } } : {}),
      ...(cardNumber !== undefined && cardNumbers !== undefined
        ? { AND: [{ cardNumber }, { cardNumber: { in: cardNumbers } }] }
        : {}),
    };
    const [totalItems, rows] = await this.prisma.$transaction([
      this.prisma.transaction.count({ where }),
      this.prisma.transaction.findMany({
        where,
        orderBy: { id: 'asc' },
        skip: params.skip,
        take: params.take,
      }),
    ]);
    return pageResult(params, totalItems, rows.map(serializeTransaction));
  }

  // REQ-F-414..REQ-F-420 (§37 transaction detail view).
  async getTransaction(transactionId: string): Promise<Record<string, unknown>> {
    if (!transactionIdSchema.safeParse(transactionId).success) {
      badRequest('Tran ID must be Numeric ...', [
        { field: 'transactionId', message: 'must be a 16-digit number' },
      ]);
    }
    const txn = await this.prisma.transaction.findUnique({ where: { id: transactionId } });
    if (!txn) {
      throw new NotFoundException('Transaction ID NOT found...');
    }
    return serializeTransaction(txn);
  }

  // REQ-F-474..REQ-F-494 (§42 transaction entry and validation workflow).
  async createTransaction(body: unknown): Promise<Record<string, unknown>> {
    // REQ-F-487, REQ-F-488: required fields, numeric type/category/merchant id,
    // amount format -99999999.99, timestamps well-formed (zod schema).
    const input = parseWith(transactionCreateSchema, body);

    // REQ-F-480..REQ-F-482: the card must exist in the card cross-reference.
    const xref = await this.prisma.cardXref.findUnique({
      where: { cardNumber: input.cardNumber },
    });
    if (!xref) {
      badRequest('Card number not found in cross-reference', [
        { field: 'cardNumber', message: 'Card number not found' },
      ]);
    }

    const type = await this.prisma.transactionType.findUnique({ where: { code: input.typeCode } });
    if (!type) {
      badRequest('Transaction type code not found', [
        { field: 'typeCode', message: 'unknown transaction type code' },
      ]);
    }
    const category = await this.prisma.transactionCategory.findUnique({
      where: { typeCode_categoryCode: { typeCode: input.typeCode, categoryCode: input.categoryCode } },
    });
    if (!category) {
      badRequest('Transaction category code not found', [
        { field: 'categoryCode', message: 'unknown transaction category for the given type' },
      ]);
    }

    // REQ-F-486, REQ-F-491: locate the highest transaction id and increment it;
    // the write and id generation run in one transaction for uniqueness.
    const created = await this.prisma.$transaction(async (tx) => {
      const highest = await tx.transaction.findFirst({
        orderBy: { id: 'desc' },
        select: { id: true },
      });
      return tx.transaction.create({
        data: {
          id: nextTransactionId(highest?.id ?? null),
          typeCode: input.typeCode,
          categoryCode: input.categoryCode,
          source: input.source,
          description: input.description,
          amount: new Prisma.Decimal(input.amount),
          merchantId: input.merchantId,
          merchantName: input.merchantName,
          merchantCity: input.merchantCity,
          merchantZip: input.merchantZip,
          cardNumber: input.cardNumber,
          originalTs: new Date(input.originalTs),
          processedTs: new Date(),
        },
      });
    });
    // REQ-F-492: success response carries the new transaction identifier.
    return serializeTransaction(created);
  }
}
