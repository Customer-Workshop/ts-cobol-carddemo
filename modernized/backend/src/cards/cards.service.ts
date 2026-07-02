// Card list / view / update, forward-engineered from legacy COCRDLIC /
// COCRDSLC / COCRDUPC (docs/spec/InteractiveNavigationandMenuControl §19-§27).

import { Injectable, NotFoundException } from '@nestjs/common';
import { accountIdSchema, cardNumberSchema, cardUpdateSchema } from '@carddemo/shared';
import { PrismaService } from '../prisma/prisma.service';
import { badRequest, parseWith, FieldIssue } from '../common/zod-validation';
import { PageResult, pageResult, parsePageParams } from '../common/pagination';
import { serializeCard, toIsoDate } from '../common/serializers';

const ALPHA_WITH_SPACES = /^[A-Za-z ]+$/;

@Injectable()
export class CardsService {
  constructor(private readonly prisma: PrismaService) {}

  // REQ-F-177, REQ-F-182, REQ-F-183: numeric account filter, filtered paginated browse.
  async listCards(
    accountId?: string,
    page?: string,
    pageSize?: string,
  ): Promise<PageResult<Record<string, unknown>>> {
    const params = parsePageParams(page, pageSize);
    if (accountId !== undefined && !accountIdSchema.safeParse(accountId).success) {
      badRequest('Account number must be an 11-digit number', [
        { field: 'accountId', message: 'must be an 11-digit number' },
      ]);
    }
    const where = accountId ? { accountId } : {};
    const [totalItems, rows] = await this.prisma.$transaction([
      this.prisma.card.count({ where }),
      this.prisma.card.findMany({
        where,
        orderBy: { cardNumber: 'asc' },
        skip: params.skip,
        take: params.take,
      }),
    ]);
    return pageResult(params, totalItems, rows.map(serializeCard));
  }

  // REQ-F-216, REQ-F-221: card number must be a 16-digit number; not-found surfaces as 404.
  async getCard(cardNumber: string): Promise<Record<string, unknown>> {
    this.validateCardNumber(cardNumber);
    const card = await this.prisma.card.findUnique({ where: { cardNumber } });
    if (!card) {
      throw new NotFoundException('Card number not found');
    }
    return serializeCard(card);
  }

  // REQ-F-217..REQ-F-225: validate embossed name / status / expiry then rewrite the card record.
  async updateCard(cardNumber: string, body: unknown): Promise<Record<string, unknown>> {
    this.validateCardNumber(cardNumber);
    const update = parseWith(cardUpdateSchema, body);
    const card = await this.prisma.card.findUnique({ where: { cardNumber } });
    if (!card) {
      throw new NotFoundException('Card number not found');
    }

    const issues: FieldIssue[] = [];
    if (update.embossedName !== undefined && !ALPHA_WITH_SPACES.test(update.embossedName.trim())) {
      // REQ-F-217: card name can only contain alphabets and spaces.
      issues.push({ field: 'embossedName', message: 'Card name can only contain alphabets and spaces' });
    }
    if (update.expiryDate !== undefined) {
      // REQ-F-219, REQ-F-220: month 1-12 (enforced by IsoDate) and year 1950-2099.
      const year = Number(update.expiryDate.slice(0, 4));
      if (year < 1950 || year > 2099) {
        issues.push({ field: 'expiryDate', message: 'Card expiry year must be between 1950 and 2099' });
      }
    }
    if (issues.length > 0) {
      badRequest('Validation failed', issues);
    }

    const changed =
      (update.embossedName !== undefined && update.embossedName.trim() !== card.embossedName.trim()) ||
      (update.expiryDate !== undefined && update.expiryDate !== toIsoDate(card.expiryDate)) ||
      (update.activeStatus !== undefined && update.activeStatus !== card.activeStatus);
    if (!changed) {
      // REQ-F-226/REQ-F-229 change-detection semantics: unchanged input is rejected.
      badRequest('Please modify to update: no changes were detected');
    }

    const updated = await this.prisma.card.update({
      where: { cardNumber },
      data: {
        ...(update.embossedName !== undefined ? { embossedName: update.embossedName.trim() } : {}),
        ...(update.expiryDate !== undefined
          ? { expiryDate: new Date(`${update.expiryDate}T00:00:00.000Z`) }
          : {}),
        ...(update.activeStatus !== undefined ? { activeStatus: update.activeStatus } : {}),
      },
    });
    return serializeCard(updated);
  }

  private validateCardNumber(cardNumber: string): void {
    if (!cardNumberSchema.safeParse(cardNumber).success) {
      badRequest('Card number must be a 16-digit number', [
        { field: 'cardNumber', message: 'must be a 16-digit number' },
      ]);
    }
  }
}
