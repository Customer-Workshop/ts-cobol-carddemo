import {
  validateCountryCode,
  validateDateOfBirth,
  validateEftAccountId,
  validateFicoScore,
  validateIsoDateField,
  validateMoneyField,
  validateOptionalAlphabetic,
  validatePhoneNumber,
  validateRequiredAlphabetic,
  validateSsn,
  validateStateCode,
  validateZipCode,
} from './account-field-validation';

describe('account field validation (COACTUPC rules)', () => {
  describe('money fields (REQ-F-014..REQ-F-020)', () => {
    it('accepts signed numeric with two decimals', () => {
      expect(validateMoneyField('creditLimit', '1234.56')).toBeNull();
      expect(validateMoneyField('creditLimit', '-99.10')).toBeNull();
      expect(validateMoneyField('creditLimit', '0')).toBeNull();
    });
    it('rejects malformed amounts', () => {
      expect(validateMoneyField('creditLimit', 'abc')).not.toBeNull();
      expect(validateMoneyField('creditLimit', '1.234')).not.toBeNull();
      expect(validateMoneyField('creditLimit', '')).not.toBeNull();
    });
  });

  describe('dates (REQ-F-021..REQ-F-030)', () => {
    it('accepts valid ISO dates including leap days', () => {
      expect(validateIsoDateField('expirationDate', '2024-02-29')).toBeNull();
      expect(validateIsoDateField('expirationDate', '2025-12-31')).toBeNull();
    });
    it('rejects invalid month/day and non-leap Feb 29', () => {
      expect(validateIsoDateField('expirationDate', '2025-13-01')).not.toBeNull();
      expect(validateIsoDateField('expirationDate', '2025-02-29')).not.toBeNull();
      expect(validateIsoDateField('expirationDate', '20250101')).not.toBeNull();
    });
    it('rejects future dates of birth (REQ-F-028)', () => {
      expect(validateDateOfBirth('1980-06-15')).toBeNull();
      expect(validateDateOfBirth('2999-01-01')).not.toBeNull();
    });
    it('accepts a date of birth equal to today', () => {
      const now = new Date('2026-07-02T12:00:00.000Z');
      expect(validateDateOfBirth('2026-07-02', now)).toBeNull();
      expect(validateDateOfBirth('2026-07-03', now)).not.toBeNull();
    });
  });

  describe('names (REQ-F-031..REQ-F-033)', () => {
    it('requires first/last name, alphabetic only', () => {
      expect(validateRequiredAlphabetic('customer.firstName', 'John')).toBeNull();
      expect(validateRequiredAlphabetic('customer.firstName', '')).not.toBeNull();
      expect(validateRequiredAlphabetic('customer.firstName', 'J0hn')).not.toBeNull();
    });
    it('middle name may be blank but must be alphabetic', () => {
      expect(validateOptionalAlphabetic('customer.middleName', '')).toBeNull();
      expect(validateOptionalAlphabetic('customer.middleName', 'Lee')).toBeNull();
      expect(validateOptionalAlphabetic('customer.middleName', 'L33')).not.toBeNull();
    });
  });

  describe('state / country / zip (REQ-F-036..REQ-F-039, REQ-F-057, REQ-F-058)', () => {
    it('validates US state codes', () => {
      expect(validateStateCode('CA')).toBeNull();
      expect(validateStateCode('ca')).toBeNull();
      expect(validateStateCode('ZZ')).not.toBeNull();
      expect(validateStateCode('')).not.toBeNull();
    });
    it('validates country code alphabetic', () => {
      expect(validateCountryCode('USA')).toBeNull();
      expect(validateCountryCode('U1A')).not.toBeNull();
    });
    it('validates zip numeric and non-zero', () => {
      expect(validateZipCode('90210')).toBeNull();
      expect(validateZipCode('ABCDE')).not.toBeNull();
      expect(validateZipCode('00000')).not.toBeNull();
    });
  });

  describe('SSN (REQ-F-046..REQ-F-050)', () => {
    it('accepts a valid SSN', () => {
      expect(validateSsn('123456789')).toBeNull();
    });
    it('rejects invalid part 1 values', () => {
      expect(validateSsn('000456789')).not.toBeNull();
      expect(validateSsn('666456789')).not.toBeNull();
      expect(validateSsn('900456789')).not.toBeNull();
    });
    it('rejects zero part 2 / part 3', () => {
      expect(validateSsn('123006789')).not.toBeNull();
      expect(validateSsn('123450000')).not.toBeNull();
    });
    it('rejects non-9-digit input', () => {
      expect(validateSsn('12345678')).not.toBeNull();
      expect(validateSsn('12345678A')).not.toBeNull();
    });
  });

  describe('FICO (REQ-F-043..REQ-F-045)', () => {
    it('accepts 300-850', () => {
      expect(validateFicoScore(300)).toBeNull();
      expect(validateFicoScore(850)).toBeNull();
    });
    it('rejects out-of-range values', () => {
      expect(validateFicoScore(299)).not.toBeNull();
      expect(validateFicoScore(851)).not.toBeNull();
      expect(validateFicoScore(0)).not.toBeNull();
    });
  });

  describe('phone numbers (REQ-F-051..REQ-F-056)', () => {
    it('blank passes', () => {
      expect(validatePhoneNumber('customer.phoneNumber2', '')).toBeNull();
    });
    it('accepts valid NANP numbers', () => {
      expect(validatePhoneNumber('customer.phoneNumber1', '(415)555-0123')).toBeNull();
      expect(validatePhoneNumber('customer.phoneNumber1', '415-555-0123')).toBeNull();
    });
    it('rejects invalid area codes', () => {
      expect(validatePhoneNumber('customer.phoneNumber1', '(011)555-0123')).not.toBeNull();
      expect(validatePhoneNumber('customer.phoneNumber1', '(911)555-0123')).not.toBeNull();
      expect(validatePhoneNumber('customer.phoneNumber1', '(155)555-0123')).not.toBeNull();
    });
    it('rejects zero prefix or line number', () => {
      expect(validatePhoneNumber('customer.phoneNumber1', '(415)000-0123')).not.toBeNull();
      expect(validatePhoneNumber('customer.phoneNumber1', '(415)555-0000')).not.toBeNull();
    });
    it('rejects malformed numbers', () => {
      expect(validatePhoneNumber('customer.phoneNumber1', '555-0123')).not.toBeNull();
    });
  });

  describe('EFT account id (REQ-F-042)', () => {
    it('requires numeric non-zero', () => {
      expect(validateEftAccountId('1234567890')).toBeNull();
      expect(validateEftAccountId('')).not.toBeNull();
      expect(validateEftAccountId('ABC')).not.toBeNull();
      expect(validateEftAccountId('0000000000')).not.toBeNull();
    });
  });
});
