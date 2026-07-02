import { describe, expect, it } from 'vitest';
import { Prisma } from '@prisma/client';
import {
  validateDailyTransaction,
  ValidationContext,
  REJECT_INVALID_CARD,
  REJECT_ACCOUNT_NOT_FOUND,
  REJECT_OVERLIMIT,
  REJECT_EXPIRED,
  REJECT_ACCOUNT_INACTIVE,
  REJECT_INVALID_TYPE,
  REJECT_INVALID_CATEGORY,
} from '../src/lib/validation';

const D = (v: string | number) => new Prisma.Decimal(v);

const account = {
  activeStatus: true,
  creditLimit: D(1000),
  currCycleCredit: D(0),
  currCycleDebit: D(0),
  expirationDate: new Date(Date.UTC(2030, 0, 1)),
};

const okCtx: ValidationContext = {
  xref: { accountId: '00000000001' },
  account,
  typeExists: true,
  categoryExists: true,
};

const txn = { amount: D('100.00'), originalTs: new Date(Date.UTC(2026, 5, 10)) };

describe('validateDailyTransaction (CBTRN02C rules)', () => {
  it('accepts a valid transaction', () => {
    expect(validateDailyTransaction(txn, okCtx)).toBeNull();
  });

  it('rejects 100 when the card is not in the cross-reference (REQ-F-016)', () => {
    expect(validateDailyTransaction(txn, { ...okCtx, xref: null })).toEqual(REJECT_INVALID_CARD);
  });

  it('rejects 101 when the account record is missing (REQ-F-017)', () => {
    expect(validateDailyTransaction(txn, { ...okCtx, account: null })).toEqual(
      REJECT_ACCOUNT_NOT_FOUND,
    );
  });

  it('rejects 102 when projected balance exceeds the credit limit (REQ-F-018)', () => {
    const ctx = { ...okCtx, account: { ...account, creditLimit: D(50) } };
    expect(validateDailyTransaction(txn, ctx)).toEqual(REJECT_OVERLIMIT);
  });

  it('uses cycle credits minus debits plus amount as projected balance (REQ-F-018)', () => {
    // 900 credit - 0 debit + 100 = 1000 → not over a 1000 limit
    const at = { ...okCtx, account: { ...account, currCycleCredit: D(900) } };
    expect(validateDailyTransaction(txn, at)).toBeNull();
    // 901 credit + 100 = 1001 → over
    const over = { ...okCtx, account: { ...account, currCycleCredit: D(901) } };
    expect(validateDailyTransaction(txn, over)).toEqual(REJECT_OVERLIMIT);
  });

  it('rejects 103 when the account expired before the transaction date (REQ-F-019)', () => {
    const ctx = { ...okCtx, account: { ...account, expirationDate: new Date(Date.UTC(2026, 5, 9)) } };
    expect(validateDailyTransaction(txn, ctx)).toEqual(REJECT_EXPIRED);
  });

  it('accepts a transaction dated exactly on the expiration date (REQ-F-019)', () => {
    const ctx = { ...okCtx, account: { ...account, expirationDate: new Date(Date.UTC(2026, 5, 10)) } };
    expect(validateDailyTransaction(txn, ctx)).toBeNull();
  });

  it('rejects 104 for an inactive account', () => {
    const ctx = { ...okCtx, account: { ...account, activeStatus: false } };
    expect(validateDailyTransaction(txn, ctx)).toEqual(REJECT_ACCOUNT_INACTIVE);
  });

  it('rejects 105/106 for unknown type or category codes', () => {
    expect(validateDailyTransaction(txn, { ...okCtx, typeExists: false })).toEqual(
      REJECT_INVALID_TYPE,
    );
    expect(validateDailyTransaction(txn, { ...okCtx, categoryExists: false })).toEqual(
      REJECT_INVALID_CATEGORY,
    );
  });
});
