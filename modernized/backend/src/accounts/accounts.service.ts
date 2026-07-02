// Combined account + customer view/update, forward-engineered from legacy
// COACTVWC / COACTUPC (docs/spec/InteractiveNavigationandMenuControl §1-§8).

import { Injectable, NotFoundException } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { accountIdSchema, accountUpdateSchema } from '@carddemo/shared';
import type { z } from 'zod';
import { PrismaService } from '../prisma/prisma.service';
import { badRequest, parseWith, FieldIssue } from '../common/zod-validation';
import { serializeAccount, serializeCustomer, toIsoDate, toMoney } from '../common/serializers';
import {
  validateCountryCode,
  validateDateOfBirth,
  validateEftAccountId,
  validateFicoScore,
  validateIsoDateField,
  validateMoneyField,
  validateOptionalAlphabetic,
  validatePhoneNumber,
  validateRequiredAlphabetic,
  validateSsn,
  validateStateCode,
  validateZipCode,
} from './account-field-validation';

type AccountUpdate = z.infer<typeof accountUpdateSchema>;

interface AccountDetail {
  account: Record<string, unknown>;
  customer: Record<string, unknown>;
}

@Injectable()
export class AccountsService {
  constructor(private readonly prisma: PrismaService) {}

  // REQ-F-064..REQ-F-068: read xref by account id -> customer id, then account
  // and customer masters; missing records surface as not-found.
  async getAccount(accountId: string): Promise<AccountDetail> {
    this.validateAccountId(accountId);
    const account = await this.prisma.account.findUnique({ where: { id: accountId } });
    if (!account) {
      throw new NotFoundException('Account ID NOT found...');
    }
    const xref = await this.prisma.cardXref.findFirst({
      where: { accountId },
      include: { customer: true },
    });
    if (!xref) {
      throw new NotFoundException('Account ID NOT found in cross-reference...');
    }
    return { account: serializeAccount(account), customer: serializeCustomer(xref.customer) };
  }

  async updateAccount(accountId: string, body: unknown): Promise<AccountDetail> {
    this.validateAccountId(accountId);
    const update = parseWith(accountUpdateSchema, body);

    const account = await this.prisma.account.findUnique({ where: { id: accountId } });
    if (!account) {
      throw new NotFoundException('Account ID NOT found...');
    }
    const xref = await this.prisma.cardXref.findFirst({
      where: { accountId },
      include: { customer: true },
    });
    if (!xref) {
      throw new NotFoundException('Account ID NOT found in cross-reference...');
    }
    const customer = xref.customer;

    // REQ-F-009..REQ-F-063: field-level validation beyond structural zod checks.
    const issues = this.validateBusinessRules(update);
    if (issues.length > 0) {
      badRequest('Validation failed', issues);
    }

    // REQ-F-005..REQ-F-008: change detection — compare submitted values to the
    // current master records; unchanged submissions are not written.
    const accountData = this.buildAccountChanges(update, {
      activeStatus: account.activeStatus,
      creditLimit: toMoney(account.creditLimit),
      cashCreditLimit: toMoney(account.cashCreditLimit),
      expirationDate: toIsoDate(account.expirationDate),
      reissueDate: account.reissueDate ? toIsoDate(account.reissueDate) : null,
      groupId: account.groupId,
    });
    const customerData = this.buildCustomerChanges(update.customer, customer);

    if (Object.keys(accountData).length === 0 && Object.keys(customerData).length === 0) {
      badRequest('Please modify to update: no changes were detected');
    }

    // REQ-F-069..REQ-F-076, REQ-N-001: account + customer writes are atomic.
    const [updatedAccount, updatedCustomer] = await this.prisma.$transaction([
      this.prisma.account.update({ where: { id: accountId }, data: accountData }),
      this.prisma.customer.update({ where: { id: customer.id }, data: customerData }),
    ]);
    return { account: serializeAccount(updatedAccount), customer: serializeCustomer(updatedCustomer) };
  }

  // REQ-F-010, REQ-F-080: account id must be supplied, numeric, non-zero, 11 digits.
  private validateAccountId(accountId: string): void {
    const parsed = accountIdSchema.safeParse(accountId);
    if (!parsed.success || Number(accountId) === 0) {
      badRequest('Account number must be an 11-digit non-zero number', [
        { field: 'accountId', message: 'must be an 11-digit non-zero number' },
      ]);
    }
  }

  private validateBusinessRules(update: AccountUpdate): FieldIssue[] {
    const issues: FieldIssue[] = [];
    const push = (issue: FieldIssue | null): void => {
      if (issue) issues.push(issue);
    };

    if (update.creditLimit !== undefined) push(validateMoneyField('creditLimit', update.creditLimit)); // REQ-F-014
    if (update.cashCreditLimit !== undefined) push(validateMoneyField('cashCreditLimit', update.cashCreditLimit)); // REQ-F-015
    if (update.expirationDate !== undefined) push(validateIsoDateField('expirationDate', update.expirationDate)); // REQ-F-022
    if (update.reissueDate !== undefined) push(validateIsoDateField('reissueDate', update.reissueDate)); // REQ-F-023

    const c = update.customer;
    if (c) {
      if (c.firstName !== undefined) push(validateRequiredAlphabetic('customer.firstName', c.firstName)); // REQ-F-031
      if (c.lastName !== undefined) push(validateRequiredAlphabetic('customer.lastName', c.lastName)); // REQ-F-032
      if (c.middleName !== undefined) push(validateOptionalAlphabetic('customer.middleName', c.middleName)); // REQ-F-033
      if (c.addressLine1 !== undefined && c.addressLine1.trim().length === 0) {
        issues.push({ field: 'customer.addressLine1', message: 'Address line 1 must be supplied' }); // REQ-F-035
      }
      if (c.stateCode !== undefined) push(validateStateCode(c.stateCode)); // REQ-F-037, REQ-F-038
      if (c.countryCode !== undefined) push(validateCountryCode(c.countryCode)); // REQ-F-036
      if (c.zipCode !== undefined) push(validateZipCode(c.zipCode)); // REQ-F-039
      if (c.phoneNumber1 !== undefined) push(validatePhoneNumber('customer.phoneNumber1', c.phoneNumber1)); // REQ-F-051
      if (c.phoneNumber2 !== undefined) push(validatePhoneNumber('customer.phoneNumber2', c.phoneNumber2)); // REQ-F-052
      if (c.ssn !== undefined) push(validateSsn(c.ssn)); // REQ-F-046..REQ-F-050
      if (c.dateOfBirth !== undefined) push(validateDateOfBirth(c.dateOfBirth)); // REQ-F-028..REQ-F-030
      if (c.eftAccountId !== undefined) push(validateEftAccountId(c.eftAccountId)); // REQ-F-042
      if (c.ficoCreditScore !== undefined) push(validateFicoScore(c.ficoCreditScore)); // REQ-F-043..REQ-F-045
    }
    return issues;
  }

  // REQ-F-006: account field comparison against the values fetched from master.
  private buildAccountChanges(
    update: AccountUpdate,
    current: {
      activeStatus: boolean;
      creditLimit: string;
      cashCreditLimit: string;
      expirationDate: string;
      reissueDate: string | null;
      groupId: string | null;
    },
  ): Prisma.AccountUpdateInput {
    const data: Prisma.AccountUpdateInput = {};
    if (update.activeStatus !== undefined && update.activeStatus !== current.activeStatus) {
      data.activeStatus = update.activeStatus;
    }
    if (update.creditLimit !== undefined && Number(update.creditLimit) !== Number(current.creditLimit)) {
      data.creditLimit = new Prisma.Decimal(update.creditLimit);
    }
    if (
      update.cashCreditLimit !== undefined &&
      Number(update.cashCreditLimit) !== Number(current.cashCreditLimit)
    ) {
      data.cashCreditLimit = new Prisma.Decimal(update.cashCreditLimit);
    }
    if (update.expirationDate !== undefined && update.expirationDate !== current.expirationDate) {
      data.expirationDate = new Date(`${update.expirationDate}T00:00:00.000Z`);
    }
    if (update.reissueDate !== undefined && update.reissueDate !== current.reissueDate) {
      data.reissueDate = new Date(`${update.reissueDate}T00:00:00.000Z`);
    }
    if (update.groupId !== undefined && update.groupId !== current.groupId) {
      data.groupId = update.groupId;
    }
    return data;
  }

  // REQ-F-007: customer field comparison (trimmed) against master values.
  private buildCustomerChanges(
    c: AccountUpdate['customer'],
    current: {
      firstName: string;
      middleName: string | null;
      lastName: string;
      addressLine1: string;
      addressLine2: string | null;
      addressLine3: string | null;
      stateCode: string;
      countryCode: string;
      zipCode: string;
      phoneNumber1: string | null;
      phoneNumber2: string | null;
      ssn: string;
      governmentIssuedId: string | null;
      dateOfBirth: Date;
      eftAccountId: string | null;
      primaryCardHolder: boolean;
      ficoCreditScore: number;
    },
  ): Prisma.CustomerUpdateInput {
    const data: Prisma.CustomerUpdateInput = {};
    if (!c) return data;
    const changedString = (next: string | undefined, prev: string | null): boolean =>
      next !== undefined && next.trim() !== (prev ?? '').trim();

    if (changedString(c.firstName, current.firstName)) data.firstName = c.firstName!.trim();
    if (changedString(c.middleName, current.middleName)) data.middleName = c.middleName!.trim();
    if (changedString(c.lastName, current.lastName)) data.lastName = c.lastName!.trim();
    if (changedString(c.addressLine1, current.addressLine1)) data.addressLine1 = c.addressLine1!.trim();
    if (changedString(c.addressLine2, current.addressLine2)) data.addressLine2 = c.addressLine2!.trim();
    if (changedString(c.addressLine3, current.addressLine3)) data.addressLine3 = c.addressLine3!.trim();
    if (c.stateCode !== undefined && c.stateCode.trim().toUpperCase() !== current.stateCode) {
      data.stateCode = c.stateCode.trim().toUpperCase();
    }
    if (changedString(c.countryCode, current.countryCode)) data.countryCode = c.countryCode!.trim();
    if (changedString(c.zipCode, current.zipCode)) data.zipCode = c.zipCode!.trim();
    if (changedString(c.phoneNumber1, current.phoneNumber1)) data.phoneNumber1 = c.phoneNumber1!.trim();
    if (changedString(c.phoneNumber2, current.phoneNumber2)) data.phoneNumber2 = c.phoneNumber2!.trim();
    if (c.ssn !== undefined && c.ssn !== current.ssn) data.ssn = c.ssn;
    if (changedString(c.governmentIssuedId, current.governmentIssuedId)) {
      data.governmentIssuedId = c.governmentIssuedId!.trim();
    }
    if (c.dateOfBirth !== undefined && c.dateOfBirth !== toIsoDate(current.dateOfBirth)) {
      data.dateOfBirth = new Date(`${c.dateOfBirth}T00:00:00.000Z`);
    }
    if (changedString(c.eftAccountId, current.eftAccountId)) data.eftAccountId = c.eftAccountId!.trim();
    if (c.primaryCardHolder !== undefined && c.primaryCardHolder !== current.primaryCardHolder) {
      data.primaryCardHolder = c.primaryCardHolder;
    }
    if (c.ficoCreditScore !== undefined && c.ficoCreditScore !== current.ficoCreditScore) {
      data.ficoCreditScore = c.ficoCreditScore;
    }
    return data;
  }
}
