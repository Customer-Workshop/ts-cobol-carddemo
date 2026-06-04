/**
 * Unit tests for COACTUPC.cbl - Account Update
 * Tests field validation for SSN, phone numbers, and data change detection.
 */
import {
  validateUSPhoneNumber,
  validateSSN,
  validateSignedNumber,
  validateYesNo,
  hasDataChanged,
  isLeapYear,
} from '../validators/account-update';

describe('COACTUPC - Account Update Validation', () => {
  describe('validateUSPhoneNumber', () => {
    it('accepts valid phone (212)555-1234', () => {
      const result = validateUSPhoneNumber('(212)555-1234');
      expect(result.valid).toBe(true);
      expect(result.parts!.area).toBe('212');
      expect(result.parts!.exchange).toBe('555');
      expect(result.parts!.subscriber).toBe('1234');
    });

    it('rejects blank phone', () => {
      const result = validateUSPhoneNumber('');
      expect(result.valid).toBe(false);
      expect(result.error).toContain('blank');
    });

    it('rejects phone without parentheses', () => {
      const result = validateUSPhoneNumber('212-555-1234');
      expect(result.valid).toBe(false);
    });

    it('rejects phone with letters', () => {
      const result = validateUSPhoneNumber('(ABC)555-1234');
      expect(result.valid).toBe(false);
    });

    it('rejects phone missing dash', () => {
      const result = validateUSPhoneNumber('(212)5551234');
      expect(result.valid).toBe(false);
    });

    it('rejects phone with too few digits in area code', () => {
      const result = validateUSPhoneNumber('(21)555-1234');
      expect(result.valid).toBe(false);
    });

    it('rejects phone with too many digits', () => {
      const result = validateUSPhoneNumber('(2122)555-1234');
      expect(result.valid).toBe(false);
    });
  });

  describe('validateSSN', () => {
    it('accepts valid SSN 123456789', () => {
      const result = validateSSN('123456789');
      expect(result.valid).toBe(true);
    });

    it('rejects blank SSN', () => {
      const result = validateSSN('');
      expect(result.valid).toBe(false);
      expect(result.error).toContain('blank');
    });

    it('rejects non-9-digit SSN', () => {
      const result = validateSSN('12345');
      expect(result.valid).toBe(false);
      expect(result.error).toContain('9 digits');
    });

    it('rejects SSN with area number 000', () => {
      const result = validateSSN('000456789');
      expect(result.valid).toBe(false);
      expect(result.error).toContain('area number');
    });

    it('rejects SSN with area number 666', () => {
      const result = validateSSN('666456789');
      expect(result.valid).toBe(false);
      expect(result.error).toContain('area number');
    });

    it('rejects SSN with area number 900', () => {
      const result = validateSSN('900456789');
      expect(result.valid).toBe(false);
      expect(result.error).toContain('area number');
    });

    it('rejects SSN with area number 999', () => {
      const result = validateSSN('999456789');
      expect(result.valid).toBe(false);
      expect(result.error).toContain('area number');
    });

    it('accepts SSN with area number 899 (just below invalid range)', () => {
      const result = validateSSN('899456789');
      expect(result.valid).toBe(true);
    });

    it('rejects SSN with group number 00', () => {
      const result = validateSSN('123006789');
      expect(result.valid).toBe(false);
      expect(result.error).toContain('group number');
    });

    it('rejects SSN with serial number 0000', () => {
      const result = validateSSN('123450000');
      expect(result.valid).toBe(false);
      expect(result.error).toContain('serial number');
    });

    it('accepts SSN with area number 001', () => {
      const result = validateSSN('001011001');
      expect(result.valid).toBe(true);
    });

    it('accepts SSN with area number 665', () => {
      const result = validateSSN('665011001');
      expect(result.valid).toBe(true);
    });

    it('accepts SSN with area number 667', () => {
      const result = validateSSN('667011001');
      expect(result.valid).toBe(true);
    });

    it('rejects SSN with non-numeric characters', () => {
      const result = validateSSN('12345678A');
      expect(result.valid).toBe(false);
    });
  });

  describe('validateSignedNumber', () => {
    it('accepts positive number +123.45', () => {
      const result = validateSignedNumber('+123.45');
      expect(result.valid).toBe(true);
    });

    it('accepts negative number -123.45', () => {
      const result = validateSignedNumber('-123.45');
      expect(result.valid).toBe(true);
    });

    it('accepts number without sign', () => {
      const result = validateSignedNumber('123.45');
      expect(result.valid).toBe(true);
    });

    it('accepts whole number', () => {
      const result = validateSignedNumber('12345');
      expect(result.valid).toBe(true);
    });

    it('rejects blank value', () => {
      const result = validateSignedNumber('');
      expect(result.valid).toBe(false);
    });

    it('rejects alphabetic input', () => {
      const result = validateSignedNumber('ABC');
      expect(result.valid).toBe(false);
    });

    it('rejects too many decimal digits', () => {
      const result = validateSignedNumber('123.456');
      expect(result.valid).toBe(false);
    });
  });

  describe('validateYesNo', () => {
    it('accepts Y', () => {
      expect(validateYesNo('Y').valid).toBe(true);
    });

    it('accepts N', () => {
      expect(validateYesNo('N').valid).toBe(true);
    });

    it('accepts lowercase y', () => {
      expect(validateYesNo('y').valid).toBe(true);
    });

    it('accepts lowercase n', () => {
      expect(validateYesNo('n').valid).toBe(true);
    });

    it('rejects other characters', () => {
      expect(validateYesNo('X').valid).toBe(false);
    });

    it('rejects blank', () => {
      expect(validateYesNo('').valid).toBe(false);
    });
  });

  describe('hasDataChanged', () => {
    it('returns false when no changes', () => {
      const original = { name: 'John', age: 30 };
      const modified = { name: 'John', age: 30 };
      expect(hasDataChanged(original, modified)).toBe(false);
    });

    it('returns true when a field changes', () => {
      const original = { name: 'John', age: 30 };
      const modified = { name: 'Jane', age: 30 };
      expect(hasDataChanged(original, modified)).toBe(true);
    });

    it('returns true when multiple fields change', () => {
      const original = { name: 'John', age: 30 };
      const modified = { name: 'Jane', age: 31 };
      expect(hasDataChanged(original, modified)).toBe(true);
    });
  });

  describe('isLeapYear', () => {
    it('returns true for 2024 (divisible by 4)', () => {
      expect(isLeapYear(2024)).toBe(true);
    });

    it('returns false for 2023 (not divisible by 4)', () => {
      expect(isLeapYear(2023)).toBe(false);
    });

    it('returns false for 1900 (divisible by 100 but not 400)', () => {
      expect(isLeapYear(1900)).toBe(false);
    });

    it('returns true for 2000 (divisible by 400)', () => {
      expect(isLeapYear(2000)).toBe(true);
    });

    it('returns false for 2100', () => {
      expect(isLeapYear(2100)).toBe(false);
    });
  });
});
