/**
 * Business logic extracted from CSUTLDTC.cbl
 * Date validation utility - mirrors the CEEDAYS API wrapper.
 */

export interface DateValidationResult {
  severityCode: string;   // '0000' = OK
  messageNumber: string;
  message: string;
}

/**
 * Validates a date string against a given format.
 * Mirrors CSUTLDTC.cbl which wraps the LE CEEDAYS API.
 *
 * Supported format: 'YYYY-MM-DD'
 *
 * Feedback codes from CSUTLDTC lines 128-148:
 * - FC-INVALID-DATE     -> 'Date is valid'  (sev 0000)
 * - FC-INSUFFICIENT-DATA-> 'Insufficient'
 * - FC-BAD-DATE-VALUE   -> 'Datevalue error'
 * - FC-INVALID-ERA      -> 'Invalid Era'
 * - FC-UNSUPP-RANGE     -> 'Unsupp. Range'
 * - FC-INVALID-MONTH    -> 'Invalid month'
 * - FC-BAD-PIC-STRING   -> 'Bad Pic String'
 * - FC-NON-NUMERIC-DATA -> 'Nonnumeric data'
 * - FC-YEAR-IN-ERA-ZERO -> 'YearInEra is 0'
 * - OTHER               -> 'Date is invalid'
 */
export function validateDate(
  dateStr: string,
  format: string = 'YYYY-MM-DD'
): DateValidationResult {
  if (!dateStr || dateStr.trim() === '') {
    return {
      severityCode: '0003',
      messageNumber: '2507',
      message: 'Insufficient',
    };
  }

  if (format !== 'YYYY-MM-DD') {
    return {
      severityCode: '0003',
      messageNumber: '2510',
      message: 'Bad Pic String',
    };
  }

  // Check format structure: YYYY-MM-DD
  const dateRegex = /^(\d{4})-(\d{2})-(\d{2})$/;
  const match = dateStr.match(dateRegex);

  if (!match) {
    // Contains non-numeric data in expected positions
    if (/[^\d\-]/.test(dateStr)) {
      return {
        severityCode: '0003',
        messageNumber: '2512',
        message: 'Nonnumeric data',
      };
    }
    return {
      severityCode: '0003',
      messageNumber: '2508',
      message: 'Datevalue error',
    };
  }

  const year = parseInt(match[1], 10);
  const month = parseInt(match[2], 10);
  const day = parseInt(match[3], 10);

  if (year === 0) {
    return {
      severityCode: '0003',
      messageNumber: '2513',
      message: 'YearInEra is 0',
    };
  }

  if (month < 1 || month > 12) {
    return {
      severityCode: '0003',
      messageNumber: '2509',
      message: 'Invalid month',
    };
  }

  // Check day validity for the given month/year
  const daysInMonth = new Date(year, month, 0).getDate();
  if (day < 1 || day > daysInMonth) {
    return {
      severityCode: '0003',
      messageNumber: '2508',
      message: 'Datevalue error',
    };
  }

  return {
    severityCode: '0000',
    messageNumber: '0000',
    message: 'Date is valid',
  };
}

/**
 * Validates date format structure (YYYY-MM-DD).
 * Used by COTRN02C lines 353-381 for Orig Date and Proc Date.
 */
export function validateDateFormat(dateStr: string): boolean {
  if (dateStr.length < 10) return false;

  const yyyy = dateStr.substring(0, 4);
  const sep1 = dateStr.charAt(4);
  const mm = dateStr.substring(5, 7);
  const sep2 = dateStr.charAt(7);
  const dd = dateStr.substring(8, 10);

  if (sep1 !== '-' || sep2 !== '-') return false;
  if (!/^\d{4}$/.test(yyyy)) return false;
  if (!/^\d{2}$/.test(mm)) return false;
  if (!/^\d{2}$/.test(dd)) return false;

  return true;
}
