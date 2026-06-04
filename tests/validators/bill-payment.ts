/**
 * Business logic extracted from COBIL00C.cbl
 * Bill payment - account balance validation and payment processing.
 */
import { AccountRecord, TransactionRecord } from '../models/records';

export interface BillPaymentResult {
  success: boolean;
  errorMessage?: string;
  transaction?: TransactionRecord;
  updatedAccount?: AccountRecord;
}

/**
 * Validates the account ID is non-empty.
 * COBIL00C lines 158-167.
 */
export function validateAccountId(acctId: string): string | null {
  if (!acctId || acctId.trim() === '') {
    return 'Acct ID can NOT be empty...';
  }
  return null;
}

/**
 * Validates the confirmation field for bill payment.
 * COBIL00C lines 173-191.
 */
export function validateConfirmation(confirm: string): {
  action: 'pay' | 'cancel' | 'showBalance' | 'error';
  message?: string;
} {
  switch (confirm) {
    case 'Y':
    case 'y':
      return { action: 'pay' };
    case 'N':
    case 'n':
      return { action: 'cancel' };
    case '':
      return { action: 'showBalance' };
    default:
      return {
        action: 'error',
        message: 'Invalid value. Valid values are (Y/N)...',
      };
  }
}

/**
 * Validates that account has a positive balance to pay.
 * COBIL00C lines 197-206: rejects if ACCT-CURR-BAL <= ZEROS.
 */
export function validateBalanceForPayment(
  currBal: number
): string | null {
  if (currBal <= 0) {
    return 'You have nothing to pay...';
  }
  return null;
}

/**
 * Creates a bill payment transaction record.
 * COBIL00C lines 218-240: builds transaction with type '02', cat 2,
 * source 'POS TERM', desc 'BILL PAYMENT - ONLINE'.
 */
export function createBillPaymentTransaction(
  nextTranId: string,
  account: AccountRecord,
  cardNum: string,
  currentDate: string
): TransactionRecord {
  return {
    tranId: nextTranId,
    tranTypeCd: '02',
    tranCatCd: 2,
    tranSource: 'POS TERM',
    tranDesc: 'BILL PAYMENT - ONLINE',
    tranAmt: account.currBal,
    merchantId: 999999999,
    merchantName: 'BILL PAYMENT',
    merchantCity: '',
    merchantZip: '',
    cardNum,
    origTs: currentDate,
    procTs: currentDate,
  };
}

/**
 * Generates the next transaction ID by incrementing from the last one.
 * COBIL00C lines 216-218: reads last tran ID, adds 1.
 */
export function generateNextTranId(lastTranId: string): string {
  const num = parseInt(lastTranId, 10) || 0;
  return String(num + 1).padStart(16, '0');
}
