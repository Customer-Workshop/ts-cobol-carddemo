// Field validation helpers mirroring the legacy edit rules from
// docs/spec/InteractiveNavigationandMenuControl (COACTUPC, COCRDUPC, COTRN02C).

import { validateDate } from '@carddemo/shared';

// REQ-F-038: valid US state or territory postal abbreviations
export const US_STATE_CODES = new Set([
  'AL', 'AK', 'AZ', 'AR', 'CA', 'CO', 'CT', 'DE', 'FL', 'GA', 'HI', 'ID', 'IL', 'IN', 'IA',
  'KS', 'KY', 'LA', 'ME', 'MD', 'MA', 'MI', 'MN', 'MS', 'MO', 'MT', 'NE', 'NV', 'NH', 'NJ',
  'NM', 'NY', 'NC', 'ND', 'OH', 'OK', 'OR', 'PA', 'RI', 'SC', 'SD', 'TN', 'TX', 'UT', 'VT',
  'VA', 'WA', 'WV', 'WI', 'WY', 'DC', 'AS', 'GU', 'MP', 'PR', 'VI',
]);

// REQ-F-040/REQ-F-041: state + first-2-digits-of-zip consistency (USPS ranges).
const STATE_ZIP2: Record<string, [number, number][]> = {
  AL: [[35, 36]], AK: [[99, 99]], AZ: [[85, 86]], AR: [[71, 72]], CA: [[90, 96]],
  CO: [[80, 81]], CT: [[6, 6]], DE: [[19, 19]], FL: [[32, 34]], GA: [[30, 31]],
  HI: [[96, 96]], ID: [[83, 83]], IL: [[60, 62]], IN: [[46, 47]], IA: [[50, 52]],
  KS: [[66, 67]], KY: [[40, 42]], LA: [[70, 71]], ME: [[3, 4]], MD: [[20, 21]],
  MA: [[1, 2], [5, 5]], MI: [[48, 49]], MN: [[55, 56]], MS: [[38, 39]], MO: [[63, 65]],
  MT: [[59, 59]], NE: [[68, 69]], NV: [[88, 89]], NH: [[3, 3]], NJ: [[7, 8]],
  NM: [[87, 88]], NY: [[0, 0], [10, 14]], NC: [[27, 28]], ND: [[58, 58]], OH: [[43, 45]],
  OK: [[73, 74]], OR: [[97, 97]], PA: [[15, 19]], RI: [[2, 2]], SC: [[29, 29]],
  SD: [[57, 57]], TN: [[37, 38]], TX: [[75, 79], [88, 88]], UT: [[84, 84]], VT: [[5, 5]],
  VA: [[20, 20], [22, 24]], WA: [[98, 99]], WV: [[24, 26]], WI: [[53, 54]], WY: [[82, 83]],
  DC: [[20, 20]], AS: [[96, 96]], GU: [[96, 96]], MP: [[96, 96]], PR: [[0, 0]], VI: [[0, 0]],
};

export function isAlpha(value: string): boolean {
  return /^[A-Za-z ]+$/.test(value.trim());
}

export function isMoney(value: string): boolean {
  return /^-?\d{1,10}(\.\d{1,2})?$/.test(value.trim());
}

export function validateStateZip(stateCode: string, zipCode: string): string | null {
  const state = stateCode.trim().toUpperCase();
  if (!US_STATE_CODES.has(state)) return 'State code is not valid';
  if (!/^\d{5}(-?\d{4})?$/.test(zipCode.trim())) return 'Zip code must be numeric';
  const ranges = STATE_ZIP2[state];
  if (ranges) {
    const zip2 = Number(zipCode.trim().slice(0, 2));
    if (!ranges.some(([lo, hi]) => zip2 >= lo && zip2 <= hi)) {
      return 'Zip code is invalid for the state';
    }
  }
  return null;
}

// REQ-F-046..REQ-F-050
export function validateSsn(ssn: string): string | null {
  const digits = ssn.replace(/-/g, '').trim();
  if (!/^\d{9}$/.test(digits)) return 'SSN must be 9 digits';
  const p1 = Number(digits.slice(0, 3));
  const p2 = Number(digits.slice(3, 5));
  const p3 = Number(digits.slice(5, 9));
  if (p1 === 0 || p1 === 666 || (p1 >= 900 && p1 <= 999)) {
    return 'SSN part 1 cannot be 000, 666, or 900-999';
  }
  if (p2 === 0) return 'SSN part 2 must be 01-99';
  if (p3 === 0) return 'SSN part 3 must be 0001-9999';
  return null;
}

// REQ-F-051..REQ-F-056: US phone number, blank allowed
export function validatePhone(phone: string): string | null {
  const trimmed = phone.trim();
  if (trimmed === '') return null;
  const digits = trimmed.replace(/[()\-. ]/g, '');
  if (!/^\d{10}$/.test(digits)) return 'Phone must have area code, prefix, and line number';
  const area = digits.slice(0, 3);
  if (area === '000') return 'Area code cannot be zero';
  if (!/^[2-9]\d{2}$/.test(area) || area[1] === '1' && area[2] === '1') {
    return 'Not a valid North America general purpose area code';
  }
  if (Number(digits.slice(3, 6)) === 0) return 'Prefix code cannot be zero';
  if (Number(digits.slice(6)) === 0) return 'Line number code cannot be zero';
  return null;
}

// REQ-F-043..REQ-F-045
export function validateFico(value: string): string | null {
  if (!/^\d+$/.test(value.trim())) return 'FICO score must be numeric';
  const n = Number(value.trim());
  if (n < 300 || n > 850) return 'FICO score should be between 300 and 850';
  return null;
}

// REQ-F-021..REQ-F-027: ISO date validation via the shared diagnostic service
export function validateIsoDate(value: string): string | null {
  const result = validateDate(value.trim(), 'YYYY-MM-DD');
  return result.valid ? null : result.message;
}

// REQ-F-028..REQ-F-030
export function validateDateOfBirth(value: string): string | null {
  const dateErr = validateIsoDate(value);
  if (dateErr) return dateErr;
  const today = new Date().toISOString().slice(0, 10);
  if (value.trim() >= today) return 'Date of birth cannot be in the future';
  return null;
}

// REQ-F-010/REQ-F-080/REQ-F-127
export function validateAccountId(value: string): string | null {
  if (value.trim() === '') return 'Account number not provided';
  if (!/^\d{11}$/.test(value.trim()) || Number(value.trim()) === 0) {
    return 'Account number must be an 11-digit non-zero number';
  }
  return null;
}

// REQ-F-206/REQ-F-216
export function validateCardNumber(value: string): string | null {
  if (value.trim() === '') return 'Card number not provided';
  if (!/^\d{16}$/.test(value.trim()) || Number(value.trim()) === 0) {
    return 'Card number must be a 16-digit non-zero number';
  }
  return null;
}
