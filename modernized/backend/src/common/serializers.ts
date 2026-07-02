// Maps Prisma rows to the wire shapes defined in modernized/shared/openapi.yaml.
// Dates serialize as YYYY-MM-DD, timestamps as ISO 8601, Decimal money as fixed
// 2-fraction-digit strings (Money schema).

import { Prisma } from '@prisma/client';
import type {
  Account as AccountRow,
  Card as CardRow,
  Customer as CustomerRow,
  JobRun as JobRunRow,
  Report as ReportRow,
  Transaction as TransactionRow,
  User as UserRow,
} from '@prisma/client';

export const toIsoDate = (d: Date): string => d.toISOString().slice(0, 10);
export const toMoney = (d: Prisma.Decimal): string => d.toFixed(2);

export function serializeUser(u: UserRow): {
  id: string;
  firstName: string;
  lastName: string;
  role: string;
} {
  return { id: u.id, firstName: u.firstName, lastName: u.lastName, role: u.role };
}

export function serializeCustomer(c: CustomerRow): Record<string, unknown> {
  return {
    id: c.id,
    firstName: c.firstName,
    middleName: c.middleName,
    lastName: c.lastName,
    addressLine1: c.addressLine1,
    addressLine2: c.addressLine2,
    addressLine3: c.addressLine3,
    stateCode: c.stateCode,
    countryCode: c.countryCode,
    zipCode: c.zipCode,
    phoneNumber1: c.phoneNumber1,
    phoneNumber2: c.phoneNumber2,
    ssn: c.ssn,
    governmentIssuedId: c.governmentIssuedId,
    dateOfBirth: toIsoDate(c.dateOfBirth),
    eftAccountId: c.eftAccountId,
    primaryCardHolder: c.primaryCardHolder,
    ficoCreditScore: c.ficoCreditScore,
  };
}

export function serializeAccount(a: AccountRow): Record<string, unknown> {
  return {
    id: a.id,
    activeStatus: a.activeStatus,
    currentBalance: toMoney(a.currentBalance),
    creditLimit: toMoney(a.creditLimit),
    cashCreditLimit: toMoney(a.cashCreditLimit),
    openDate: toIsoDate(a.openDate),
    expirationDate: toIsoDate(a.expirationDate),
    reissueDate: a.reissueDate ? toIsoDate(a.reissueDate) : null,
    currCycleCredit: toMoney(a.currCycleCredit),
    currCycleDebit: toMoney(a.currCycleDebit),
    addressZip: a.addressZip,
    groupId: a.groupId,
  };
}

export function serializeCard(c: CardRow): Record<string, unknown> {
  return {
    cardNumber: c.cardNumber,
    accountId: c.accountId,
    embossedName: c.embossedName,
    expiryDate: toIsoDate(c.expiryDate),
    activeStatus: c.activeStatus,
  };
}

export function serializeTransaction(t: TransactionRow): Record<string, unknown> {
  return {
    id: t.id,
    typeCode: t.typeCode,
    categoryCode: t.categoryCode,
    source: t.source,
    description: t.description,
    amount: toMoney(t.amount),
    merchantId: t.merchantId,
    merchantName: t.merchantName,
    merchantCity: t.merchantCity,
    merchantZip: t.merchantZip,
    cardNumber: t.cardNumber,
    originalTs: t.originalTs.toISOString(),
    processedTs: t.processedTs.toISOString(),
  };
}

export function serializeReport(r: ReportRow): Record<string, unknown> {
  return {
    id: r.id,
    name: r.name,
    version: r.version,
    startDate: toIsoDate(r.startDate),
    endDate: toIsoDate(r.endDate),
    content: r.content,
    createdAt: r.createdAt.toISOString(),
  };
}

export function serializeJobRun(j: JobRunRow): Record<string, unknown> {
  return {
    id: j.id,
    jobName: j.jobName,
    status: j.status,
    startedAt: j.startedAt.toISOString(),
    completedAt: j.completedAt ? j.completedAt.toISOString() : null,
    message: j.message,
  };
}
