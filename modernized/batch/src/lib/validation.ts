// Daily transaction validation rules — modernizes CBTRN02C validation.
// Reject reason codes 100–103 come directly from the spec
// (TransactionProcessingandValidation REQ-F-016..REQ-F-019 / REQ-F-033..REQ-F-036);
// 104–108 extend them for referential/consistency checks the relational
// model requires before posting.
import { Prisma } from '@prisma/client';
import { toIsoDate } from './timestamp';

export interface RejectReason {
  code: number;
  description: string;
}

export const REJECT_INVALID_CARD: RejectReason = {
  code: 100,
  description: 'INVALID CARD NUMBER FOUND',
};
export const REJECT_ACCOUNT_NOT_FOUND: RejectReason = {
  code: 101,
  description: 'ACCOUNT RECORD NOT FOUND',
};
export const REJECT_OVERLIMIT: RejectReason = {
  code: 102,
  description: 'OVERLIMIT TRANSACTION',
};
export const REJECT_EXPIRED: RejectReason = {
  code: 103,
  description: 'TRANSACTION RECEIVED AFTER ACCT EXPIRATION',
};
export const REJECT_ACCOUNT_INACTIVE: RejectReason = {
  code: 104,
  description: 'ACCOUNT NOT ACTIVE',
};
export const REJECT_INVALID_TYPE: RejectReason = {
  code: 105,
  description: 'TRANSACTION TYPE NOT FOUND',
};
export const REJECT_INVALID_CATEGORY: RejectReason = {
  code: 106,
  description: 'TRANSACTION CATEGORY NOT FOUND',
};
export const REJECT_INVALID_AMOUNT: RejectReason = {
  code: 107,
  description: 'INVALID TRANSACTION AMOUNT',
};
export const REJECT_INVALID_DATE: RejectReason = {
  code: 108,
  description: 'INVALID TRANSACTION DATE',
};

export interface DailyTransactionInput {
  amount: Prisma.Decimal;
  originalTs: Date;
}

export interface ValidationContext {
  /** Xref row for the transaction's card number, or null when not found. REQ-F-016 */
  xref: { accountId: string } | null;
  /** Account row for the xref's account id, or null when not found. REQ-F-017 */
  account: {
    activeStatus: boolean;
    creditLimit: Prisma.Decimal;
    currCycleCredit: Prisma.Decimal;
    currCycleDebit: Prisma.Decimal;
    expirationDate: Date;
  } | null;
  /** Whether the transaction's type code exists in transaction_types. */
  typeExists: boolean;
  /** Whether (typeCode, categoryCode) exists in transaction_categories. */
  categoryExists: boolean;
}

/**
 * Validate one daily transaction. Returns null when it may be posted,
 * otherwise the reject reason (code + description).
 * Check order mirrors CBTRN02C: card xref → account → credit limit →
 * expiration, then relational-integrity checks (type, category, amount, date).
 */
export function validateDailyTransaction(
  txn: DailyTransactionInput,
  ctx: ValidationContext,
): RejectReason | null {
  // REQ-F-016: card number must resolve through the cross-reference.
  if (!ctx.xref) return REJECT_INVALID_CARD;
  // REQ-F-017: account record must exist.
  if (!ctx.account) return REJECT_ACCOUNT_NOT_FOUND;
  if (!ctx.account.activeStatus) return REJECT_ACCOUNT_INACTIVE;
  // REQ-F-018: projected balance = cycle credits - cycle debits + amount.
  const projected = ctx.account.currCycleCredit
    .minus(ctx.account.currCycleDebit)
    .plus(txn.amount);
  if (projected.greaterThan(ctx.account.creditLimit)) return REJECT_OVERLIMIT;
  // REQ-F-019: account must not be expired at the transaction date.
  if (Number.isNaN(txn.originalTs.getTime())) return REJECT_INVALID_DATE;
  // Date-only comparison ("first 10 characters" of the legacy timestamp).
  if (toIsoDate(ctx.account.expirationDate) < toIsoDate(txn.originalTs)) return REJECT_EXPIRED;
  if (!ctx.typeExists) return REJECT_INVALID_TYPE;
  if (!ctx.categoryExists) return REJECT_INVALID_CATEGORY;
  if (!txn.amount.isFinite()) return REJECT_INVALID_AMOUNT;
  return null;
}
