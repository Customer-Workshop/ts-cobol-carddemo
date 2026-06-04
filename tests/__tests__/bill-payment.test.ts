/**
 * Unit tests for COBIL00C.cbl - Bill Payment
 * Tests account balance validation and payment creation.
 */
import {
  validateAccountId,
  validateConfirmation,
  validateBalanceForPayment,
  createBillPaymentTransaction,
  generateNextTranId,
} from '../validators/bill-payment';
import { AccountRecord } from '../models/records';

function makeAccount(overrides: Partial<AccountRecord> = {}): AccountRecord {
  return {
    acctId: '00000012345',
    activeStatus: 'Y',
    currBal: 2500.0,
    creditLimit: 10000.0,
    cashCreditLimit: 2000.0,
    openDate: '2020-01-01',
    expirationDate: '2025-12-31',
    reissueDate: '2024-01-01',
    currCycCredit: 3000.0,
    currCycDebit: 1000.0,
    addrZip: '10001',
    groupId: 'GROUP001',
    ...overrides,
  };
}

describe('COBIL00C - Bill Payment', () => {
  describe('validateAccountId', () => {
    it('rejects empty account ID', () => {
      expect(validateAccountId('')).toBe('Acct ID can NOT be empty...');
    });

    it('rejects whitespace-only account ID', () => {
      expect(validateAccountId('   ')).toBe('Acct ID can NOT be empty...');
    });

    it('accepts valid account ID', () => {
      expect(validateAccountId('00000012345')).toBeNull();
    });
  });

  describe('validateConfirmation', () => {
    it('returns pay action for Y', () => {
      expect(validateConfirmation('Y').action).toBe('pay');
    });

    it('returns pay action for lowercase y', () => {
      expect(validateConfirmation('y').action).toBe('pay');
    });

    it('returns cancel action for N', () => {
      expect(validateConfirmation('N').action).toBe('cancel');
    });

    it('returns cancel action for lowercase n', () => {
      expect(validateConfirmation('n').action).toBe('cancel');
    });

    it('shows balance for empty confirmation', () => {
      expect(validateConfirmation('').action).toBe('showBalance');
    });

    it('returns error for invalid input', () => {
      const result = validateConfirmation('X');
      expect(result.action).toBe('error');
      expect(result.message).toContain('Y/N');
    });
  });

  describe('validateBalanceForPayment', () => {
    it('accepts positive balance', () => {
      expect(validateBalanceForPayment(2500.0)).toBeNull();
    });

    it('rejects zero balance', () => {
      expect(validateBalanceForPayment(0)).toBe(
        'You have nothing to pay...'
      );
    });

    it('rejects negative balance', () => {
      expect(validateBalanceForPayment(-100.0)).toBe(
        'You have nothing to pay...'
      );
    });

    it('accepts very small positive balance', () => {
      expect(validateBalanceForPayment(0.01)).toBeNull();
    });
  });

  describe('createBillPaymentTransaction', () => {
    it('creates transaction with correct type code 02', () => {
      const account = makeAccount({ currBal: 2500.0 });
      const tran = createBillPaymentTransaction(
        '0000000000000100',
        account,
        '4111111111111111',
        '2024-06-15'
      );
      expect(tran.tranTypeCd).toBe('02');
    });

    it('creates transaction with category code 2', () => {
      const account = makeAccount();
      const tran = createBillPaymentTransaction(
        '0000000000000100',
        account,
        '4111111111111111',
        '2024-06-15'
      );
      expect(tran.tranCatCd).toBe(2);
    });

    it('sets amount to current account balance', () => {
      const account = makeAccount({ currBal: 3750.5 });
      const tran = createBillPaymentTransaction(
        '0000000000000100',
        account,
        '4111111111111111',
        '2024-06-15'
      );
      expect(tran.tranAmt).toBe(3750.5);
    });

    it('sets source as POS TERM', () => {
      const account = makeAccount();
      const tran = createBillPaymentTransaction(
        '0000000000000100',
        account,
        '4111111111111111',
        '2024-06-15'
      );
      expect(tran.tranSource).toBe('POS TERM');
    });

    it('sets description as BILL PAYMENT - ONLINE', () => {
      const account = makeAccount();
      const tran = createBillPaymentTransaction(
        '0000000000000100',
        account,
        '4111111111111111',
        '2024-06-15'
      );
      expect(tran.tranDesc).toBe('BILL PAYMENT - ONLINE');
    });

    it('sets merchant ID to 999999999', () => {
      const account = makeAccount();
      const tran = createBillPaymentTransaction(
        '0000000000000100',
        account,
        '4111111111111111',
        '2024-06-15'
      );
      expect(tran.merchantId).toBe(999999999);
    });

    it('sets card number from input', () => {
      const account = makeAccount();
      const tran = createBillPaymentTransaction(
        '0000000000000100',
        account,
        '5555444433332222',
        '2024-06-15'
      );
      expect(tran.cardNum).toBe('5555444433332222');
    });
  });

  describe('generateNextTranId', () => {
    it('increments from last transaction ID', () => {
      expect(generateNextTranId('0000000000000099')).toBe(
        '0000000000000100'
      );
    });

    it('handles zero-padded IDs', () => {
      expect(generateNextTranId('0000000000000001')).toBe(
        '0000000000000002'
      );
    });

    it('handles empty/invalid input', () => {
      expect(generateNextTranId('')).toBe('0000000000000001');
    });

    it('pads result to 16 characters', () => {
      const result = generateNextTranId('0000000000000099');
      expect(result.length).toBe(16);
    });
  });
});
