// Field-level validation rules for the combined account + customer update
// (legacy COACTUPC), per docs/spec/InteractiveNavigationandMenuControl §4
// (REQ-F-009..REQ-F-063). Pure functions so they are unit-testable.

import { validateDate } from '@carddemo/shared';
import type { FieldIssue } from '../common/zod-validation';

// REQ-F-038: valid US state / territory postal abbreviations.
export const US_STATE_CODES = new Set([
  'AL', 'AK', 'AZ', 'AR', 'CA', 'CO', 'CT', 'DE', 'FL', 'GA', 'HI', 'ID', 'IL', 'IN', 'IA',
  'KS', 'KY', 'LA', 'ME', 'MD', 'MA', 'MI', 'MN', 'MS', 'MO', 'MT', 'NE', 'NV', 'NH', 'NJ',
  'NM', 'NY', 'NC', 'ND', 'OH', 'OK', 'OR', 'PA', 'RI', 'SC', 'SD', 'TN', 'TX', 'UT', 'VT',
  'VA', 'WA', 'WV', 'WI', 'WY', 'DC', 'AS', 'GU', 'MP', 'PR', 'VI',
]);

const ALPHabetic = /^[A-Za-z]+$/;
const ALPHA_WITH_SPACES = /^[A-Za-z ]+$/;
// REQ-F-014..REQ-F-020: signed numeric with up to two decimal places.
const SIGNED_MONEY = /^-?\d{1,10}(\.\d{1,2})?$/;

export function validateMoneyField(field: string, value: string): FieldIssue | null {
  if (!SIGNED_MONEY.test(value)) {
    return { field, message: `${field} must be a valid signed numeric value with two decimal places` };
  }
  return null;
}

export function validateIsoDateField(field: string, value: string): FieldIssue | null {
  // REQ-F-021..REQ-F-027: format, month/day/year range, and leap-year correctness
  // via the shared date validation service (legacy CSUTLDTC).
  const result = validateDate(value, 'YYYY-MM-DD');
  if (!result.valid) {
    return { field, message: `${field}: ${result.message}` };
  }
  return null;
}

export function validateDateOfBirth(value: string, now = new Date()): FieldIssue | null {
  const formatIssue = validateIsoDateField('customer.dateOfBirth', value);
  if (formatIssue) return formatIssue;
  // REQ-F-028..REQ-F-030: date of birth cannot be in the future.
  const today = now.toISOString().slice(0, 10);
  if (value >= today) {
    return { field: 'customer.dateOfBirth', message: 'Date of birth cannot be in the future' };
  }
  return null;
}

export function validateRequiredAlphabetic(field: string, value: string): FieldIssue | null {
  // REQ-F-031, REQ-F-032, REQ-F-060, REQ-F-063: supplied, alphabetic only.
  if (value.trim().length === 0) {
    return { field, message: `${field} must be supplied` };
  }
  if (!ALPHA_WITH_SPACES.test(value.trim())) {
    return { field, message: `${field} can have alphabets only` };
  }
  return null;
}

export function validateOptionalAlphabetic(field: string, value: string): FieldIssue | null {
  // REQ-F-033, REQ-F-061, REQ-F-062: blank passes; otherwise alphabetic only.
  if (value.trim().length === 0) return null;
  if (!ALPHA_WITH_SPACES.test(value.trim())) {
    return { field, message: `${field} can have alphabets only` };
  }
  return null;
}

export function validateStateCode(value: string): FieldIssue | null {
  // REQ-F-037, REQ-F-038: alphabetic, valid two-character US state/territory code.
  const v = value.trim().toUpperCase();
  if (v.length === 0) {
    return { field: 'customer.stateCode', message: 'State code must be supplied' };
  }
  if (!ALPHabetic.test(v) || !US_STATE_CODES.has(v)) {
    return { field: 'customer.stateCode', message: 'State code is not valid' };
  }
  return null;
}

export function validateCountryCode(value: string): FieldIssue | null {
  // REQ-F-036: supplied, alphabetic only.
  const v = value.trim();
  if (v.length === 0) {
    return { field: 'customer.countryCode', message: 'Country code must be supplied' };
  }
  if (!ALPHabetic.test(v)) {
    return { field: 'customer.countryCode', message: 'Country code can have alphabets only' };
  }
  return null;
}

export function validateZipCode(value: string): FieldIssue | null {
  // REQ-F-039, REQ-F-057, REQ-F-058: supplied, numeric, non-zero.
  const v = value.trim();
  if (v.length === 0) {
    return { field: 'customer.zipCode', message: 'Zip code must be supplied' };
  }
  if (!/^\d+$/.test(v)) {
    return { field: 'customer.zipCode', message: 'Zip code must be all numeric' };
  }
  if (Number(v) === 0) {
    return { field: 'customer.zipCode', message: 'Zip code must not be zero' };
  }
  return null;
}

export function validateSsn(value: string): FieldIssue | null {
  // REQ-F-046..REQ-F-050: 3-part SSN rules.
  if (!/^\d{9}$/.test(value)) {
    return { field: 'customer.ssn', message: 'SSN must be 9 digits' };
  }
  const part1 = value.slice(0, 3);
  const part2 = value.slice(3, 5);
  const part3 = value.slice(5, 9);
  const p1 = Number(part1);
  if (p1 === 0 || p1 === 666 || (p1 >= 900 && p1 <= 999)) {
    return { field: 'customer.ssn', message: 'SSN part 1 must not be 000, 666, or 900-999' };
  }
  if (Number(part2) === 0) {
    return { field: 'customer.ssn', message: 'SSN part 2 must be 01-99' };
  }
  if (Number(part3) === 0) {
    return { field: 'customer.ssn', message: 'SSN part 3 must be 0001-9999' };
  }
  return null;
}

export function validateFicoScore(value: number): FieldIssue | null {
  // REQ-F-043..REQ-F-045: numeric, non-zero, between 300 and 850.
  if (!Number.isInteger(value) || value < 300 || value > 850) {
    return { field: 'customer.ficoCreditScore', message: 'FICO score should be between 300 and 850' };
  }
  return null;
}

// REQ-F-051..REQ-F-056: US phone number — blank passes; otherwise area code,
// prefix, and line number must all be supplied, numeric, non-zero, with a
// valid North American general-purpose area code (N11 and 0/1-leading excluded).
export function validatePhoneNumber(field: string, value: string): FieldIssue | null {
  const v = value.trim();
  if (v.length === 0) return null;
  const match = /^\(?(\d{3})\)?[- ]?(\d{3})[- ]?(\d{4})$/.exec(v);
  if (!match) {
    return { field, message: `${field} must have area code, prefix, and line number` };
  }
  const [, area, prefix, line] = match as unknown as [string, string, string, string];
  if (Number(area) === 0) {
    return { field, message: `${field} area code cannot be zero` };
  }
  const first = area.charAt(0);
  const isN11 = area.charAt(1) === '1' && area.charAt(2) === '1';
  if (first === '0' || first === '1' || isN11) {
    return { field, message: `${field} is not a valid North America general purpose area code` };
  }
  if (Number(prefix) === 0) {
    return { field, message: `${field} prefix code cannot be zero` };
  }
  if (Number(line) === 0) {
    return { field, message: `${field} line number code cannot be zero` };
  }
  return null;
}

export function validateEftAccountId(value: string): FieldIssue | null {
  // REQ-F-042: supplied, numeric, non-zero.
  const v = value.trim();
  if (v.length === 0) {
    return { field: 'customer.eftAccountId', message: 'EFT account ID must be supplied' };
  }
  if (!/^\d+$/.test(v)) {
    return { field: 'customer.eftAccountId', message: 'EFT account ID must be all numeric' };
  }
  if (Number(v) === 0) {
    return { field: 'customer.eftAccountId', message: 'EFT account ID must not be zero' };
  }
  return null;
}
