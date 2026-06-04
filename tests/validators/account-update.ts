/**
 * Business logic extracted from COACTUPC.cbl
 * Account update - field validation for account maintenance.
 */

/**
 * Validates US phone number format: (AAA)BBB-CCCC
 * COACTUPC lines 82-115: parses phone into 3-digit area code,
 * 3-digit exchange, 4-digit subscriber.
 */
export function validateUSPhoneNumber(phone: string): {
  valid: boolean;
  parts?: { area: string; exchange: string; subscriber: string };
  error?: string;
} {
  if (!phone || phone.trim() === '') {
    return { valid: false, error: 'Phone number is blank' };
  }

  // Expected format: (AAA)BBB-CCCC  (15 chars per COBOL PIC X(15))
  const phoneRegex = /^\((\d{3})\)(\d{3})-(\d{4})$/;
  const match = phone.trim().match(phoneRegex);

  if (!match) {
    return { valid: false, error: 'Phone number format invalid' };
  }

  return {
    valid: true,
    parts: {
      area: match[1],
      exchange: match[2],
      subscriber: match[3],
    },
  };
}

/**
 * Validates US Social Security Number.
 * COACTUPC lines 117-146: 9-digit SSN split into 3 parts.
 * Invalid SSN part 1 values: 0, 666, 900-999 (line 121-123).
 */
export function validateSSN(ssn: string): {
  valid: boolean;
  error?: string;
} {
  if (!ssn || ssn.trim() === '') {
    return { valid: false, error: 'SSN is blank' };
  }

  if (!/^\d{9}$/.test(ssn.trim())) {
    return { valid: false, error: 'SSN must be 9 digits' };
  }

  const part1 = parseInt(ssn.substring(0, 3), 10);
  const part2 = parseInt(ssn.substring(3, 5), 10);
  const part3 = parseInt(ssn.substring(5, 9), 10);

  // COACTUPC lines 121-123: INVALID-SSN-PART1 VALUES 0, 666, 900 THRU 999
  if (part1 === 0 || part1 === 666 || (part1 >= 900 && part1 <= 999)) {
    return { valid: false, error: 'SSN area number is invalid' };
  }

  if (part2 === 0) {
    return { valid: false, error: 'SSN group number is invalid' };
  }

  if (part3 === 0) {
    return { valid: false, error: 'SSN serial number is invalid' };
  }

  return { valid: true };
}

/**
 * Validates a signed decimal number in format S9(9)V99.
 * COACTUPC lines 55-59: validates signed number edit.
 */
export function validateSignedNumber(value: string): {
  valid: boolean;
  error?: string;
} {
  if (!value || value.trim() === '') {
    return { valid: false, error: 'Value is blank' };
  }

  const trimmed = value.trim();
  // Match optional sign, up to 9 digits, optional decimal, up to 2 decimal digits
  const numRegex = /^[+-]?\d{1,9}(\.\d{1,2})?$/;
  if (!numRegex.test(trimmed)) {
    return { valid: false, error: 'Not a valid signed number' };
  }

  return { valid: true };
}

/**
 * Validates a Yes/No field.
 * COACTUPC lines 76-80.
 */
export function validateYesNo(value: string): {
  valid: boolean;
  error?: string;
} {
  if (!value || value.trim() === '') {
    return { valid: false, error: 'Value is blank' };
  }

  const upper = value.toUpperCase().trim();
  if (upper !== 'Y' && upper !== 'N') {
    return { valid: false, error: 'Value must be Y or N' };
  }

  return { valid: true };
}

/**
 * Detects if a data change has occurred.
 * COACTUPC lines 168-170.
 */
export function hasDataChanged(
  original: Record<string, unknown>,
  modified: Record<string, unknown>
): boolean {
  for (const key of Object.keys(original)) {
    if (original[key] !== modified[key]) {
      return true;
    }
  }
  return false;
}

/**
 * Validates a leap year for date checking.
 * Used by COACTUPC date validation logic (CSUTLDWY copybook).
 */
export function isLeapYear(year: number): boolean {
  if (year % 4 !== 0) return false;
  if (year % 100 !== 0) return true;
  if (year % 400 !== 0) return false;
  return true;
}
