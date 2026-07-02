// Zod validation helpers shared by backend, batch, and frontend.
// Date validation mirrors the legacy CSUTLDTC date validation service
// (docs/spec/Security,Validation,andApplicationSetup REQ-F-019..REQ-F-022):
// callers get a structured diagnostic result, not just a boolean.

import { z } from 'zod';

export const userIdSchema = z
  .string()
  .min(1)
  .max(9)
  .regex(/^[A-Za-z0-9]+$/, 'User ID must be alphanumeric');

export const passwordSchema = z.string().min(1).max(8);

export const customerIdSchema = z.string().regex(/^\d{9}$/, 'Customer ID must be 9 digits');

export const accountIdSchema = z.string().regex(/^\d{11}$/, 'Account ID must be 11 digits');

export const cardNumberSchema = z.string().regex(/^\d{16}$/, 'Card number must be 16 digits');

export const transactionIdSchema = z
  .string()
  .regex(/^\d{16}$/, 'Transaction ID must be 16 digits');

export const tranTypeCodeSchema = z.string().regex(/^\d{2}$/, 'Type code must be 2 digits');

export const tranCategoryCodeSchema = z
  .number()
  .int()
  .min(0)
  .max(9999);

export const ficoScoreSchema = z.number().int().min(300).max(850);

export const moneySchema = z
  .string()
  .regex(/^-?\d{1,10}(\.\d{1,2})?$/, 'Amount must be a decimal with up to 2 fraction digits');

/** Diagnostic result of a date validation request (legacy CSUTLDTC semantics). */
export interface DateValidationResult {
  valid: boolean;
  /** Human-readable result message, e.g. "Date is valid" */
  message: string;
  /** The input date that was validated */
  inputDate: string;
  /** The format mask the date was validated against */
  formatMask: string;
  severity: 'OK' | 'ERROR';
}

const DATE_MASK_PATTERNS: Record<string, RegExp> = {
  'YYYY-MM-DD': /^(\d{4})-(\d{2})-(\d{2})$/,
  YYYYMMDD: /^(\d{4})(\d{2})(\d{2})$/,
  'MM/DD/YYYY': /^(\d{2})\/(\d{2})\/(\d{4})$/,
};

function result(
  valid: boolean,
  message: string,
  inputDate: string,
  formatMask: string,
): DateValidationResult {
  return { valid, message, inputDate, formatMask, severity: valid ? 'OK' : 'ERROR' };
}

/**
 * Validate a date string against a format mask, returning a full diagnostic
 * record (REQ-F-019, REQ-F-021, REQ-F-022).
 */
export function validateDate(inputDate: string, formatMask = 'YYYY-MM-DD'): DateValidationResult {
  const pattern = DATE_MASK_PATTERNS[formatMask];
  if (!pattern) {
    return result(false, 'Bad Pic String', inputDate, formatMask);
  }
  if (inputDate.trim().length === 0) {
    return result(false, 'Insufficient', inputDate, formatMask);
  }
  const match = pattern.exec(inputDate);
  if (!match) {
    if (/[^0-9/-]/.test(inputDate)) {
      return result(false, 'Nonnumeric data', inputDate, formatMask);
    }
    return result(false, 'Date is invalid', inputDate, formatMask);
  }
  let year: number;
  let month: number;
  let day: number;
  if (formatMask === 'MM/DD/YYYY') {
    month = Number(match[1]);
    day = Number(match[2]);
    year = Number(match[3]);
  } else {
    year = Number(match[1]);
    month = Number(match[2]);
    day = Number(match[3]);
  }
  if (year === 0) {
    return result(false, 'YearInEra is 0', inputDate, formatMask);
  }
  if (year < 1601 || year > 9999) {
    return result(false, 'Unsupp. Range', inputDate, formatMask);
  }
  if (month < 1 || month > 12) {
    return result(false, 'Invalid month', inputDate, formatMask);
  }
  const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate();
  if (day < 1 || day > daysInMonth) {
    return result(false, 'Datevalue error', inputDate, formatMask);
  }
  return result(true, 'Date is valid', inputDate, formatMask);
}

/** Zod schema for ISO dates (YYYY-MM-DD) using the diagnostic validator. */
export const isoDateSchema = z.string().refine((v) => validateDate(v, 'YYYY-MM-DD').valid, {
  message: 'Invalid date (expected YYYY-MM-DD)',
});

export const signInRequestSchema = z.object({
  userId: userIdSchema,
  password: passwordSchema,
});

export const accountUpdateSchema = z.object({
  activeStatus: z.boolean().optional(),
  creditLimit: moneySchema.optional(),
  cashCreditLimit: moneySchema.optional(),
  expirationDate: isoDateSchema.optional(),
  reissueDate: isoDateSchema.optional(),
  groupId: z.string().max(10).optional(),
  customer: z
    .object({
      firstName: z.string().min(1).max(25).optional(),
      middleName: z.string().max(25).optional(),
      lastName: z.string().min(1).max(25).optional(),
      addressLine1: z.string().min(1).max(50).optional(),
      addressLine2: z.string().max(50).optional(),
      addressLine3: z.string().max(50).optional(),
      stateCode: z.string().length(2).optional(),
      countryCode: z.string().min(2).max(3).optional(),
      zipCode: z.string().min(1).max(10).optional(),
      phoneNumber1: z.string().max(15).optional(),
      phoneNumber2: z.string().max(15).optional(),
      ssn: z.string().regex(/^\d{9}$/).optional(),
      governmentIssuedId: z.string().max(20).optional(),
      dateOfBirth: isoDateSchema.optional(),
      eftAccountId: z.string().max(10).optional(),
      primaryCardHolder: z.boolean().optional(),
      ficoCreditScore: ficoScoreSchema.optional(),
    })
    .optional(),
});

export const cardUpdateSchema = z.object({
  embossedName: z.string().min(1).max(50).optional(),
  expiryDate: isoDateSchema.optional(),
  activeStatus: z.boolean().optional(),
});

export const transactionCreateSchema = z.object({
  typeCode: tranTypeCodeSchema,
  categoryCode: tranCategoryCodeSchema,
  source: z.string().min(1).max(10),
  description: z.string().min(1).max(100),
  amount: moneySchema,
  merchantId: z.string().regex(/^\d{1,9}$/),
  merchantName: z.string().min(1).max(50),
  merchantCity: z.string().min(1).max(50),
  merchantZip: z.string().min(1).max(10),
  cardNumber: cardNumberSchema,
  originalTs: z.string().datetime(),
});

export const billPayRequestSchema = z.object({
  accountId: accountIdSchema,
  confirm: z.literal(true),
});

export const reportRequestSchema = z
  .object({
    name: z.string().min(1).max(50),
    startDate: isoDateSchema,
    endDate: isoDateSchema,
  })
  .refine((v) => v.startDate <= v.endDate, {
    message: 'startDate must not be after endDate',
  });

export const userCreateSchema = z.object({
  id: userIdSchema,
  firstName: z.string().min(1).max(20),
  lastName: z.string().min(1).max(20),
  password: passwordSchema,
  role: z.enum(['USER', 'ADMIN']),
});

export const userUpdateSchema = userCreateSchema.omit({ id: true }).partial();
