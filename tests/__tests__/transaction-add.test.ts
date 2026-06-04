/**
 * Unit tests for COTRN02C.cbl - Online Transaction Add
 * Tests all input validation rules for adding a new transaction.
 */
import {
  validateKeyFields,
  validateMandatoryFields,
  validateNumericCodes,
  validateAmountFormat,
  validateTransactionDates,
  validateMerchantId,
  validateConfirmation,
} from '../validators/transaction-add';

describe('COTRN02C - Transaction Add Validation', () => {
  describe('validateKeyFields', () => {
    it('accepts valid numeric account ID', () => {
      expect(validateKeyFields('00000012345', '')).toBeNull();
    });

    it('accepts valid numeric card number', () => {
      expect(validateKeyFields('', '4111111111111111')).toBeNull();
    });

    it('rejects non-numeric account ID', () => {
      const result = validateKeyFields('ABC12345', '');
      expect(result).not.toBeNull();
      expect(result!.field).toBe('acctId');
      expect(result!.message).toContain('Numeric');
    });

    it('rejects non-numeric card number', () => {
      const result = validateKeyFields('', 'ABCD1234');
      expect(result).not.toBeNull();
      expect(result!.field).toBe('cardNum');
      expect(result!.message).toContain('Numeric');
    });

    it('requires at least one key field', () => {
      const result = validateKeyFields('', '');
      expect(result).not.toBeNull();
      expect(result!.message).toContain('Account or Card Number must be entered');
    });

    it('prefers account ID when both are provided', () => {
      expect(validateKeyFields('12345678901', '4111111111111111')).toBeNull();
    });
  });

  describe('validateMandatoryFields', () => {
    const validInput = {
      typeCd: '01',
      catCd: '0001',
      source: 'POS TERM',
      desc: 'Test purchase',
      amount: '+00000100.00',
      origDate: '2024-01-15',
      procDate: '2024-01-15',
      merchantId: '123456789',
      merchantName: 'ACME STORE',
      merchantCity: 'NEW YORK',
      merchantZip: '10001',
    };

    it('accepts all fields populated', () => {
      expect(validateMandatoryFields(validInput)).toBeNull();
    });

    it('rejects empty Type CD', () => {
      const result = validateMandatoryFields({ ...validInput, typeCd: '' });
      expect(result).not.toBeNull();
      expect(result!.field).toBe('typeCd');
      expect(result!.message).toContain('Type CD');
    });

    it('rejects empty Category CD', () => {
      const result = validateMandatoryFields({ ...validInput, catCd: '' });
      expect(result).not.toBeNull();
      expect(result!.field).toBe('catCd');
    });

    it('rejects empty Source', () => {
      const result = validateMandatoryFields({ ...validInput, source: '' });
      expect(result).not.toBeNull();
      expect(result!.field).toBe('source');
    });

    it('rejects empty Description', () => {
      const result = validateMandatoryFields({ ...validInput, desc: '' });
      expect(result).not.toBeNull();
      expect(result!.field).toBe('desc');
    });

    it('rejects empty Amount', () => {
      const result = validateMandatoryFields({ ...validInput, amount: '' });
      expect(result).not.toBeNull();
      expect(result!.field).toBe('amount');
    });

    it('rejects empty Orig Date', () => {
      const result = validateMandatoryFields({ ...validInput, origDate: '' });
      expect(result).not.toBeNull();
      expect(result!.field).toBe('origDate');
    });

    it('rejects empty Proc Date', () => {
      const result = validateMandatoryFields({ ...validInput, procDate: '' });
      expect(result).not.toBeNull();
      expect(result!.field).toBe('procDate');
    });

    it('rejects empty Merchant ID', () => {
      const result = validateMandatoryFields({
        ...validInput,
        merchantId: '',
      });
      expect(result).not.toBeNull();
      expect(result!.field).toBe('merchantId');
    });

    it('rejects empty Merchant Name', () => {
      const result = validateMandatoryFields({
        ...validInput,
        merchantName: '',
      });
      expect(result).not.toBeNull();
      expect(result!.field).toBe('merchantName');
    });

    it('rejects empty Merchant City', () => {
      const result = validateMandatoryFields({
        ...validInput,
        merchantCity: '',
      });
      expect(result).not.toBeNull();
      expect(result!.field).toBe('merchantCity');
    });

    it('rejects empty Merchant Zip', () => {
      const result = validateMandatoryFields({
        ...validInput,
        merchantZip: '',
      });
      expect(result).not.toBeNull();
      expect(result!.field).toBe('merchantZip');
    });

    it('reports first empty field found in order', () => {
      const result = validateMandatoryFields({
        typeCd: '',
        catCd: '',
        source: '',
      });
      expect(result!.field).toBe('typeCd');
    });
  });

  describe('validateNumericCodes', () => {
    it('accepts numeric type and category codes', () => {
      expect(validateNumericCodes('01', '0001')).toBeNull();
    });

    it('rejects non-numeric type CD', () => {
      const result = validateNumericCodes('AB', '0001');
      expect(result).not.toBeNull();
      expect(result!.field).toBe('typeCd');
    });

    it('rejects non-numeric category CD', () => {
      const result = validateNumericCodes('01', 'XXXX');
      expect(result).not.toBeNull();
      expect(result!.field).toBe('catCd');
    });
  });

  describe('validateAmountFormat', () => {
    it('accepts valid positive amount', () => {
      expect(validateAmountFormat('+00000100.00')).toBeNull();
    });

    it('accepts valid negative amount', () => {
      expect(validateAmountFormat('-00000100.00')).toBeNull();
    });

    it('accepts maximum amount', () => {
      expect(validateAmountFormat('+99999999.99')).toBeNull();
    });

    it('accepts zero amount', () => {
      expect(validateAmountFormat('+00000000.00')).toBeNull();
    });

    it('rejects missing sign', () => {
      const result = validateAmountFormat('000000100.00');
      expect(result).not.toBeNull();
    });

    it('rejects missing decimal point', () => {
      const result = validateAmountFormat('+0000010000');
      expect(result).not.toBeNull();
    });

    it('rejects non-numeric integer part', () => {
      const result = validateAmountFormat('+0000ABC0.00');
      expect(result).not.toBeNull();
    });

    it('rejects non-numeric decimal part', () => {
      const result = validateAmountFormat('+00000100.XX');
      expect(result).not.toBeNull();
    });
  });

  describe('validateTransactionDates', () => {
    it('accepts valid orig and proc dates', () => {
      expect(
        validateTransactionDates('2024-01-15', '2024-01-16')
      ).toBeNull();
    });

    it('rejects invalid orig date format', () => {
      const result = validateTransactionDates('01/15/2024', '2024-01-16');
      expect(result).not.toBeNull();
      expect(result!.field).toBe('origDate');
    });

    it('rejects invalid proc date format', () => {
      const result = validateTransactionDates('2024-01-15', '01/16/2024');
      expect(result).not.toBeNull();
      expect(result!.field).toBe('procDate');
    });

    it('rejects invalid orig date value', () => {
      const result = validateTransactionDates('2024-13-45', '2024-01-16');
      expect(result).not.toBeNull();
      expect(result!.field).toBe('origDate');
    });

    it('rejects invalid proc date value', () => {
      const result = validateTransactionDates('2024-01-15', '2024-02-30');
      expect(result).not.toBeNull();
      expect(result!.field).toBe('procDate');
    });

    it('allows year 0000 dates (treated as YearInEra=0, msg 2513 is exempted)', () => {
      expect(
        validateTransactionDates('0000-01-15', '0000-01-16')
      ).toBeNull();
    });
  });

  describe('validateMerchantId', () => {
    it('accepts numeric merchant ID', () => {
      expect(validateMerchantId('123456789')).toBeNull();
    });

    it('rejects non-numeric merchant ID', () => {
      const result = validateMerchantId('ABC123');
      expect(result).not.toBeNull();
      expect(result!.message).toContain('Numeric');
    });
  });

  describe('validateConfirmation', () => {
    it('returns add action for Y', () => {
      expect(validateConfirmation('Y').action).toBe('add');
    });

    it('returns add action for lowercase y', () => {
      expect(validateConfirmation('y').action).toBe('add');
    });

    it('returns prompt action for N', () => {
      const result = validateConfirmation('N');
      expect(result.action).toBe('prompt');
    });

    it('returns prompt action for empty string', () => {
      const result = validateConfirmation('');
      expect(result.action).toBe('prompt');
    });

    it('returns error for invalid value', () => {
      const result = validateConfirmation('X');
      expect(result.action).toBe('error');
      expect(result.message).toContain('Y/N');
    });
  });
});
