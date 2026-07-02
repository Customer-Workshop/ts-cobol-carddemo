import { describe, expect, it } from 'vitest';
import {
  accountIdSchema,
  accountUpdateSchema,
  billPayRequestSchema,
  cardNumberSchema,
  customerIdSchema,
  ficoScoreSchema,
  isoDateSchema,
  moneySchema,
  reportRequestSchema,
  signInRequestSchema,
  transactionCreateSchema,
  userCreateSchema,
  userIdSchema,
  validateDate,
} from '../src/validation';

describe('validateDate', () => {
  it('accepts a valid ISO date', () => {
    const r = validateDate('2024-02-29');
    expect(r.valid).toBe(true);
    expect(r.message).toBe('Date is valid');
    expect(r.severity).toBe('OK');
  });

  it('rejects an invalid day', () => {
    const r = validateDate('2023-02-29');
    expect(r.valid).toBe(false);
    expect(r.message).toBe('Datevalue error');
  });

  it('rejects an invalid month', () => {
    expect(validateDate('2023-13-01').message).toBe('Invalid month');
  });

  it('rejects zero year', () => {
    expect(validateDate('0000-01-01').message).toBe('YearInEra is 0');
  });

  it('rejects out-of-range year', () => {
    expect(validateDate('1500-01-01').message).toBe('Unsupp. Range');
  });

  it('rejects non-numeric input', () => {
    expect(validateDate('20AB-01-01').message).toBe('Nonnumeric data');
  });

  it('rejects empty input as Insufficient', () => {
    expect(validateDate('').message).toBe('Insufficient');
  });

  it('rejects unknown format mask as Bad Pic String', () => {
    expect(validateDate('2024-01-01', 'DD.MM.YYYY').message).toBe('Bad Pic String');
  });

  it('supports YYYYMMDD and MM/DD/YYYY masks', () => {
    expect(validateDate('20240115', 'YYYYMMDD').valid).toBe(true);
    expect(validateDate('01/15/2024', 'MM/DD/YYYY').valid).toBe(true);
    expect(validateDate('13/15/2024', 'MM/DD/YYYY').message).toBe('Invalid month');
  });

  it('echoes input date and mask in the diagnostic record', () => {
    const r = validateDate('2024-01-01', 'YYYY-MM-DD');
    expect(r.inputDate).toBe('2024-01-01');
    expect(r.formatMask).toBe('YYYY-MM-DD');
  });
});

describe('identifier schemas', () => {
  it('userIdSchema enforces 1-9 alphanumeric chars', () => {
    expect(userIdSchema.safeParse('ADMIN0001').success).toBe(true);
    expect(userIdSchema.safeParse('WAYTOOLONGID').success).toBe(false);
    expect(userIdSchema.safeParse('BAD ID').success).toBe(false);
    expect(userIdSchema.safeParse('').success).toBe(false);
  });

  it('customerIdSchema enforces 9 digits', () => {
    expect(customerIdSchema.safeParse('000000001').success).toBe(true);
    expect(customerIdSchema.safeParse('1234').success).toBe(false);
  });

  it('accountIdSchema enforces 11 digits', () => {
    expect(accountIdSchema.safeParse('00000000001').success).toBe(true);
    expect(accountIdSchema.safeParse('123').success).toBe(false);
  });

  it('cardNumberSchema enforces 16 digits', () => {
    expect(cardNumberSchema.safeParse('4111111111111111').success).toBe(true);
    expect(cardNumberSchema.safeParse('4111-1111-1111-1111').success).toBe(false);
  });

  it('ficoScoreSchema enforces 300-850', () => {
    expect(ficoScoreSchema.safeParse(700).success).toBe(true);
    expect(ficoScoreSchema.safeParse(200).success).toBe(false);
    expect(ficoScoreSchema.safeParse(900).success).toBe(false);
  });

  it('moneySchema accepts decimal strings', () => {
    expect(moneySchema.safeParse('1234.56').success).toBe(true);
    expect(moneySchema.safeParse('-50.00').success).toBe(true);
    expect(moneySchema.safeParse('12.345').success).toBe(false);
    expect(moneySchema.safeParse('abc').success).toBe(false);
  });
});

describe('request schemas', () => {
  it('signInRequestSchema', () => {
    expect(signInRequestSchema.safeParse({ userId: 'ADMIN001', password: 'PASSWORD' }).success).toBe(true);
    expect(signInRequestSchema.safeParse({ userId: '', password: '' }).success).toBe(false);
  });

  it('accountUpdateSchema accepts partial nested customer updates', () => {
    const r = accountUpdateSchema.safeParse({
      creditLimit: '5000.00',
      customer: { firstName: 'Jane', ficoCreditScore: 720 },
    });
    expect(r.success).toBe(true);
    expect(accountUpdateSchema.safeParse({ expirationDate: 'not-a-date' }).success).toBe(false);
  });

  it('transactionCreateSchema validates a full transaction', () => {
    const r = transactionCreateSchema.safeParse({
      typeCode: '01',
      categoryCode: 1,
      source: 'POS TERM',
      description: 'Purchase at store',
      amount: '42.50',
      merchantId: '123456789',
      merchantName: 'Store',
      merchantCity: 'Springfield',
      merchantZip: '12345',
      cardNumber: '4111111111111111',
      originalTs: '2024-01-15T12:00:00.000Z',
    });
    expect(r.success).toBe(true);
  });

  it('billPayRequestSchema requires confirm=true', () => {
    expect(billPayRequestSchema.safeParse({ accountId: '00000000001', confirm: true }).success).toBe(true);
    expect(billPayRequestSchema.safeParse({ accountId: '00000000001', confirm: false }).success).toBe(false);
  });

  it('reportRequestSchema rejects inverted date ranges', () => {
    expect(
      reportRequestSchema.safeParse({ name: 'TRANREPT', startDate: '2024-01-01', endDate: '2024-01-31' }).success,
    ).toBe(true);
    expect(
      reportRequestSchema.safeParse({ name: 'TRANREPT', startDate: '2024-02-01', endDate: '2024-01-01' }).success,
    ).toBe(false);
  });

  it('userCreateSchema validates role enum', () => {
    expect(
      userCreateSchema.safeParse({
        id: 'USER0002',
        firstName: 'A',
        lastName: 'B',
        password: 'PASSWORD',
        role: 'USER',
      }).success,
    ).toBe(true);
    expect(
      userCreateSchema.safeParse({
        id: 'USER0002',
        firstName: 'A',
        lastName: 'B',
        password: 'PASSWORD',
        role: 'ROOT',
      }).success,
    ).toBe(false);
  });

  it('isoDateSchema uses the diagnostic validator', () => {
    expect(isoDateSchema.safeParse('2024-06-30').success).toBe(true);
    expect(isoDateSchema.safeParse('2024-06-31').success).toBe(false);
  });
});
