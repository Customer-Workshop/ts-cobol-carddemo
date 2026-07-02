import { nextTransactionId } from './transaction-id';

describe('nextTransactionId (REQ-F-161, REQ-F-491)', () => {
  it('starts at 1 when no transactions exist', () => {
    expect(nextTransactionId(null)).toBe('0000000000000001');
  });

  it('increments the highest existing id, zero-padded to 16 digits', () => {
    expect(nextTransactionId('0000000000000100')).toBe('0000000000000101');
    expect(nextTransactionId('0000000000000999')).toBe('0000000000001000');
  });

  it('treats non-numeric ids as zero', () => {
    expect(nextTransactionId('ABC')).toBe('0000000000000001');
  });
});
