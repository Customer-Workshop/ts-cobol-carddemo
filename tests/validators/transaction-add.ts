/**
 * Business logic extracted from COTRN02C.cbl
 * Online transaction add - input validation rules.
 */
import { validateDate, validateDateFormat } from './date-validation';

export interface TransactionInput {
  acctId: string;
  cardNum: string;
  typeCd: string;
  catCd: string;
  source: string;
  desc: string;
  amount: string;
  origDate: string;
  procDate: string;
  merchantId: string;
  merchantName: string;
  merchantCity: string;
  merchantZip: string;
  confirm: string;
}

export interface FieldValidationError {
  field: string;
  message: string;
}

/**
 * Validates the key fields (Account ID / Card Number).
 * COTRN02C lines 193-230: either acctId or cardNum must be provided;
 * both must be numeric if present.
 */
export function validateKeyFields(
  acctId: string,
  cardNum: string
): FieldValidationError | null {
  const acctProvided = acctId.trim() !== '';
  const cardProvided = cardNum.trim() !== '';

  if (acctProvided) {
    if (!/^\d+$/.test(acctId.trim())) {
      return { field: 'acctId', message: 'Account ID must be Numeric...' };
    }
    return null;
  }

  if (cardProvided) {
    if (!/^\d+$/.test(cardNum.trim())) {
      return { field: 'cardNum', message: 'Card Number must be Numeric...' };
    }
    return null;
  }

  return {
    field: 'acctId',
    message: 'Account or Card Number must be entered...',
  };
}

/**
 * Validates all mandatory data fields on the transaction add screen.
 * COTRN02C lines 251-317: checks each field is non-empty.
 */
export function validateMandatoryFields(
  input: Partial<TransactionInput>
): FieldValidationError | null {
  const mandatoryChecks: Array<{
    field: keyof TransactionInput;
    displayName: string;
  }> = [
    { field: 'typeCd', displayName: 'Type CD' },
    { field: 'catCd', displayName: 'Category CD' },
    { field: 'source', displayName: 'Source' },
    { field: 'desc', displayName: 'Description' },
    { field: 'amount', displayName: 'Amount' },
    { field: 'origDate', displayName: 'Orig Date' },
    { field: 'procDate', displayName: 'Proc Date' },
    { field: 'merchantId', displayName: 'Merchant ID' },
    { field: 'merchantName', displayName: 'Merchant Name' },
    { field: 'merchantCity', displayName: 'Merchant City' },
    { field: 'merchantZip', displayName: 'Merchant Zip' },
  ];

  for (const check of mandatoryChecks) {
    const value = input[check.field];
    if (!value || value.trim() === '') {
      return {
        field: check.field,
        message: `${check.displayName} can NOT be empty...`,
      };
    }
  }
  return null;
}

/**
 * Validates numeric fields: Type CD, Category CD must be numeric.
 * COTRN02C lines 322-337.
 */
export function validateNumericCodes(
  typeCd: string,
  catCd: string
): FieldValidationError | null {
  if (!/^\d+$/.test(typeCd.trim())) {
    return { field: 'typeCd', message: 'Type CD must be Numeric...' };
  }
  if (!/^\d+$/.test(catCd.trim())) {
    return { field: 'catCd', message: 'Category CD must be Numeric...' };
  }
  return null;
}

/**
 * Validates transaction amount format: +/-99999999.99
 * COTRN02C lines 339-351.
 */
export function validateAmountFormat(amount: string): FieldValidationError | null {
  if (amount.length < 12) {
    // Pad or check raw
  }
  const trimmed = amount.trim();

  // Check sign character
  const sign = trimmed.charAt(0);
  if (sign !== '-' && sign !== '+') {
    return {
      field: 'amount',
      message: 'Amount should be in format -99999999.99',
    };
  }

  // Check 8 numeric digits after sign
  const intPart = trimmed.substring(1, 9);
  if (!/^\d{8}$/.test(intPart)) {
    return {
      field: 'amount',
      message: 'Amount should be in format -99999999.99',
    };
  }

  // Check decimal point
  if (trimmed.charAt(9) !== '.') {
    return {
      field: 'amount',
      message: 'Amount should be in format -99999999.99',
    };
  }

  // Check 2 decimal digits
  const decPart = trimmed.substring(10, 12);
  if (!/^\d{2}$/.test(decPart)) {
    return {
      field: 'amount',
      message: 'Amount should be in format -99999999.99',
    };
  }

  return null;
}

/**
 * Validates the origination and processing dates.
 * COTRN02C lines 353-427: checks format (YYYY-MM-DD) and calls CSUTLDTC.
 */
export function validateTransactionDates(
  origDate: string,
  procDate: string
): FieldValidationError | null {
  if (!validateDateFormat(origDate)) {
    return {
      field: 'origDate',
      message: 'Orig Date should be in format YYYY-MM-DD',
    };
  }

  if (!validateDateFormat(procDate)) {
    return {
      field: 'procDate',
      message: 'Proc Date should be in format YYYY-MM-DD',
    };
  }

  // COTRN02C lines 389-407: call CSUTLDTC for orig date
  const origResult = validateDate(origDate, 'YYYY-MM-DD');
  if (origResult.severityCode !== '0000' && origResult.messageNumber !== '2513') {
    return {
      field: 'origDate',
      message: 'Orig Date - Not a valid date...',
    };
  }

  // COTRN02C lines 409-427: call CSUTLDTC for proc date
  const procResult = validateDate(procDate, 'YYYY-MM-DD');
  if (procResult.severityCode !== '0000' && procResult.messageNumber !== '2513') {
    return {
      field: 'procDate',
      message: 'Proc Date - Not a valid date...',
    };
  }

  return null;
}

/**
 * Validates merchant ID is numeric.
 * COTRN02C lines 430-436.
 */
export function validateMerchantId(
  merchantId: string
): FieldValidationError | null {
  if (!/^\d+$/.test(merchantId.trim())) {
    return {
      field: 'merchantId',
      message: 'Merchant ID must be Numeric...',
    };
  }
  return null;
}

/**
 * Validates the confirmation field.
 * COTRN02C lines 169-188: Y/y proceed, N/n/space/low-values prompt, other=error.
 */
export function validateConfirmation(confirm: string): {
  action: 'add' | 'prompt' | 'error';
  message?: string;
} {
  switch (confirm) {
    case 'Y':
    case 'y':
      return { action: 'add' };
    case 'N':
    case 'n':
    case '':
      return {
        action: 'prompt',
        message: 'Confirm to add this transaction...',
      };
    default:
      return {
        action: 'error',
        message: 'Invalid value. Valid values are (Y/N)...',
      };
  }
}
