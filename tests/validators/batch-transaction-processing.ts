/**
 * Business logic extracted from CBTRN01C.cbl and CBTRN03C.cbl
 * Batch transaction processing - validation and reporting.
 */
import {
  AccountRecord,
  CardXrefRecord,
  CustomerRecord,
  TransactionRecord,
} from '../models/records';

/**
 * Looks up a card cross-reference by card number.
 * CBTRN01C lines 227-239: reads XREF file by card number key.
 */
export function lookupXref(
  cardNum: string,
  xrefRecords: Map<string, CardXrefRecord>
): { found: boolean; xref?: CardXrefRecord } {
  const xref = xrefRecords.get(cardNum);
  if (!xref) {
    return { found: false };
  }
  return { found: true, xref };
}

/**
 * Reads an account record by account ID.
 * CBTRN01C lines 241-250.
 */
export function readAccount(
  acctId: string,
  accounts: Map<string, AccountRecord>
): { found: boolean; account?: AccountRecord } {
  const account = accounts.get(acctId);
  if (!account) {
    return { found: false };
  }
  return { found: true, account };
}

/**
 * Processes a single daily transaction through the full validation pipeline.
 * CBTRN01C lines 164-186: reads tran, looks up xref, reads account.
 */
export function processDailyTransaction(
  cardNum: string,
  tranId: string,
  xrefRecords: Map<string, CardXrefRecord>,
  accounts: Map<string, AccountRecord>
): {
  status: 'processed' | 'xref_failed' | 'account_not_found';
  message: string;
  account?: AccountRecord;
} {
  const xrefResult = lookupXref(cardNum, xrefRecords);
  if (!xrefResult.found) {
    return {
      status: 'xref_failed',
      message: `CARD NUMBER ${cardNum} COULD NOT BE VERIFIED. SKIPPING TRANSACTION ID-${tranId}`,
    };
  }

  const acctResult = readAccount(xrefResult.xref!.acctId, accounts);
  if (!acctResult.found) {
    return {
      status: 'account_not_found',
      message: `ACCOUNT ${xrefResult.xref!.acctId} NOT FOUND`,
    };
  }

  return {
    status: 'processed',
    message: 'SUCCESSFUL READ OF ACCOUNT FILE',
    account: acctResult.account,
  };
}

/**
 * Report date range filter for CBTRN03C.
 * CBTRN03C reads start/end dates from DATEPARM file and filters transactions.
 */
export function isTransactionInDateRange(
  tranOrigTs: string,
  startDate: string,
  endDate: string
): boolean {
  const tranDate = tranOrigTs.substring(0, 10);
  return tranDate >= startDate && tranDate <= endDate;
}

/**
 * Format a transaction amount for report display.
 * Mirrors the COBOL MOVE ... TO WS-TRAN-AMT pattern.
 */
export function formatTransactionAmount(amount: number): string {
  const sign = amount >= 0 ? '+' : '-';
  const absAmount = Math.abs(amount);
  const formatted = absAmount.toFixed(2);
  return sign + formatted.padStart(11, '0');
}
