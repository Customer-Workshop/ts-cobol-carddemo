import { describe, expect, it } from 'vitest';
import { Prisma } from '@prisma/client';
import { monthlyInterest } from '../src/lib/interest';
import { toDb2Timestamp } from '../src/lib/timestamp';

const D = (v: string | number) => new Prisma.Decimal(v);

describe('monthlyInterest (CBACT04C, REQ-F-083)', () => {
  it('computes balance * rate / 1200', () => {
    expect(monthlyInterest(D(1200), D(15)).toFixed(2)).toBe('15.00');
    expect(monthlyInterest(D(500), D(25)).toFixed(2)).toBe('10.42');
    expect(monthlyInterest(D('999.99'), D('18.5')).toFixed(2)).toBe('15.42');
  });

  it('is zero for a zero rate (ZEROAPR group)', () => {
    expect(monthlyInterest(D(1000), D(0)).isZero()).toBe(true);
  });
});

describe('toDb2Timestamp (REQ-F-043/REQ-F-086)', () => {
  it('formats YYYY-MM-DD-HH.MM.SS.MIL0000', () => {
    const d = new Date(Date.UTC(2026, 6, 2, 3, 4, 5, 67));
    expect(toDb2Timestamp(d)).toBe('2026-07-02-03.04.05.0670000');
  });
});
