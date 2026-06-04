/**
 * Unit tests for CSUTLDTC.cbl - Date Validation Utility
 * Tests date format validation and edge cases.
 */
import {
  validateDate,
  validateDateFormat,
} from '../validators/date-validation';

describe('CSUTLDTC - Date Validation Utility', () => {
  describe('validateDate', () => {
    it('accepts valid date 2024-01-15', () => {
      const result = validateDate('2024-01-15', 'YYYY-MM-DD');
      expect(result.severityCode).toBe('0000');
      expect(result.message).toBe('Date is valid');
    });

    it('accepts valid date 2024-12-31', () => {
      const result = validateDate('2024-12-31', 'YYYY-MM-DD');
      expect(result.severityCode).toBe('0000');
    });

    it('accepts valid leap year date 2024-02-29', () => {
      const result = validateDate('2024-02-29', 'YYYY-MM-DD');
      expect(result.severityCode).toBe('0000');
    });

    it('rejects invalid leap year date 2023-02-29', () => {
      const result = validateDate('2023-02-29', 'YYYY-MM-DD');
      expect(result.severityCode).toBe('0003');
      expect(result.message).toBe('Datevalue error');
    });

    it('rejects month 13', () => {
      const result = validateDate('2024-13-01', 'YYYY-MM-DD');
      expect(result.severityCode).toBe('0003');
      expect(result.message).toBe('Invalid month');
    });

    it('rejects month 00', () => {
      const result = validateDate('2024-00-15', 'YYYY-MM-DD');
      expect(result.severityCode).toBe('0003');
      expect(result.message).toBe('Invalid month');
    });

    it('rejects day 32 in January', () => {
      const result = validateDate('2024-01-32', 'YYYY-MM-DD');
      expect(result.severityCode).toBe('0003');
      expect(result.message).toBe('Datevalue error');
    });

    it('rejects day 00', () => {
      const result = validateDate('2024-01-00', 'YYYY-MM-DD');
      expect(result.severityCode).toBe('0003');
      expect(result.message).toBe('Datevalue error');
    });

    it('reports year 0 as YearInEra is 0', () => {
      const result = validateDate('0000-01-15', 'YYYY-MM-DD');
      expect(result.severityCode).toBe('0003');
      expect(result.messageNumber).toBe('2513');
      expect(result.message).toBe('YearInEra is 0');
    });

    it('rejects empty date string', () => {
      const result = validateDate('', 'YYYY-MM-DD');
      expect(result.severityCode).toBe('0003');
      expect(result.message).toBe('Insufficient');
    });

    it('rejects non-numeric characters in date', () => {
      const result = validateDate('20XX-01-15', 'YYYY-MM-DD');
      expect(result.severityCode).toBe('0003');
      expect(result.message).toBe('Nonnumeric data');
    });

    it('rejects unsupported format', () => {
      const result = validateDate('2024-01-15', 'MM/DD/YYYY');
      expect(result.severityCode).toBe('0003');
      expect(result.message).toBe('Bad Pic String');
    });

    it('rejects malformed date structure', () => {
      const result = validateDate('20240115', 'YYYY-MM-DD');
      expect(result.severityCode).toBe('0003');
    });

    it('accepts valid end-of-month dates', () => {
      expect(validateDate('2024-04-30', 'YYYY-MM-DD').severityCode).toBe('0000');
      expect(validateDate('2024-06-30', 'YYYY-MM-DD').severityCode).toBe('0000');
      expect(validateDate('2024-09-30', 'YYYY-MM-DD').severityCode).toBe('0000');
    });

    it('rejects April 31', () => {
      const result = validateDate('2024-04-31', 'YYYY-MM-DD');
      expect(result.severityCode).toBe('0003');
      expect(result.message).toBe('Datevalue error');
    });

    it('handles century leap year 2000-02-29', () => {
      const result = validateDate('2000-02-29', 'YYYY-MM-DD');
      expect(result.severityCode).toBe('0000');
    });

    it('rejects non-century-leap 1900-02-29', () => {
      const result = validateDate('1900-02-29', 'YYYY-MM-DD');
      expect(result.severityCode).toBe('0003');
    });
  });

  describe('validateDateFormat', () => {
    it('accepts YYYY-MM-DD format', () => {
      expect(validateDateFormat('2024-01-15')).toBe(true);
    });

    it('rejects slash separators', () => {
      expect(validateDateFormat('2024/01/15')).toBe(false);
    });

    it('rejects missing separators', () => {
      expect(validateDateFormat('20240115xx')).toBe(false);
    });

    it('rejects short string', () => {
      expect(validateDateFormat('2024-01')).toBe(false);
    });

    it('rejects non-numeric year', () => {
      expect(validateDateFormat('YYYY-01-15')).toBe(false);
    });

    it('rejects non-numeric month', () => {
      expect(validateDateFormat('2024-XX-15')).toBe(false);
    });

    it('rejects non-numeric day', () => {
      expect(validateDateFormat('2024-01-XX')).toBe(false);
    });
  });
});
