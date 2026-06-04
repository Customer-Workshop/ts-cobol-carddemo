/**
 * Business logic extracted from CBTRN02C.cbl
 * Batch transaction posting - validation and balance update logic.
 */
import {
  AccountRecord,
  CardXrefRecord,
  DailyTransactionRecord,
  TranCatBalRecord,
  TransactionRecord,
  ValidationResult,
} from '../models/records';

/**
 * Validates a daily transaction by performing cross-reference lookup.
 * CBTRN02C lines 380-392: looks up card number in XREF file.
 *
 * @returns validation fail reason (0 = pass, 100 = invalid card)
 */
export function validateXrefLookup(
  cardNum: string,
  xrefRecords: Map<string, CardXrefRecord>
): ValidationResult {
  const xref = xrefRecords.get(cardNum);
  if (!xref) {
    return {
      failReason: 100,
      failReasonDesc: 'INVALID CARD NUMBER FOUND',
    };
  }
  return { failReason: 0, failReasonDesc: '' };
}

/**
 * Validates a daily transaction against account data.
 * CBTRN02C lines 393-421:
 * - Looks up account by xref acct-id
 * - Checks credit limit (line 407)
 * - Checks expiration date (line 414)
 */
export function validateAccountLookup(
  xref: CardXrefRecord,
  accounts: Map<string, AccountRecord>,
  dailyTran: DailyTransactionRecord
): ValidationResult {
  const account = accounts.get(xref.acctId);

  if (!account) {
    return {
      failReason: 101,
      failReasonDesc: 'ACCOUNT RECORD NOT FOUND',
    };
  }

  // CBTRN02C lines 403-413: Credit limit check
  // COMPUTE WS-TEMP-BAL = ACCT-CURR-CYC-CREDIT - ACCT-CURR-CYC-DEBIT + DALYTRAN-AMT
  const tempBal =
    account.currCycCredit - account.currCycDebit + dailyTran.amt;

  if (account.creditLimit < tempBal) {
    return {
      failReason: 102,
      failReasonDesc: 'OVERLIMIT TRANSACTION',
    };
  }

  // CBTRN02C lines 414-420: Expiration date check
  // IF ACCT-EXPIRAION-DATE >= DALYTRAN-ORIG-TS(1:10)
  const origDate = dailyTran.origTs.substring(0, 10);
  if (account.expirationDate < origDate) {
    return {
      failReason: 103,
      failReasonDesc: 'TRANSACTION RECEIVED AFTER ACCT EXPIRATION',
    };
  }

  return { failReason: 0, failReasonDesc: '' };
}

/**
 * Full validation pipeline for a daily transaction.
 * CBTRN02C lines 370-378: validates xref then account.
 */
export function validateTransaction(
  dailyTran: DailyTransactionRecord,
  xrefRecords: Map<string, CardXrefRecord>,
  accounts: Map<string, AccountRecord>
): ValidationResult {
  // Step 1: XREF lookup
  const xrefResult = validateXrefLookup(dailyTran.cardNum, xrefRecords);
  if (xrefResult.failReason !== 0) {
    return xrefResult;
  }

  // Step 2: Account lookup and validation
  const xref = xrefRecords.get(dailyTran.cardNum)!;
  return validateAccountLookup(xref, accounts, dailyTran);
}

/**
 * Updates account balances after posting a transaction.
 * CBTRN02C lines 545-559:
 * - ADD DALYTRAN-AMT TO ACCT-CURR-BAL
 * - IF positive -> ADD to ACCT-CURR-CYC-CREDIT
 * - IF negative -> ADD to ACCT-CURR-CYC-DEBIT
 */
export function updateAccountBalance(
  account: AccountRecord,
  tranAmt: number
): AccountRecord {
  const updated = { ...account };
  updated.currBal = account.currBal + tranAmt;
  if (tranAmt >= 0) {
    updated.currCycCredit = account.currCycCredit + tranAmt;
  } else {
    updated.currCycDebit = account.currCycDebit + tranAmt;
  }
  return updated;
}

/**
 * Updates or creates a transaction category balance record.
 * CBTRN02C lines 467-501, 503-524, 526-542:
 * - If record exists, add amount to existing balance
 * - If not, create new record with the transaction amount
 */
export function updateTranCatBalance(
  existing: TranCatBalRecord | null,
  acctId: string,
  typeCd: string,
  catCd: number,
  tranAmt: number
): { record: TranCatBalRecord; isNew: boolean } {
  if (existing) {
    return {
      record: {
        ...existing,
        balance: existing.balance + tranAmt,
      },
      isNew: false,
    };
  }

  return {
    record: {
      acctId,
      typeCd,
      catCd,
      balance: tranAmt,
    },
    isNew: true,
  };
}

/**
 * Maps daily transaction fields to the posted transaction record.
 * CBTRN02C lines 425-438.
 */
export function mapDailyTranToPosted(
  dailyTran: DailyTransactionRecord,
  procTimestamp: string
): TransactionRecord {
  return {
    tranId: dailyTran.dalytranId,
    tranTypeCd: dailyTran.typeCd,
    tranCatCd: dailyTran.catCd,
    tranSource: dailyTran.source,
    tranDesc: dailyTran.desc,
    tranAmt: dailyTran.amt,
    merchantId: dailyTran.merchantId,
    merchantName: dailyTran.merchantName,
    merchantCity: dailyTran.merchantCity,
    merchantZip: dailyTran.merchantZip,
    cardNum: dailyTran.cardNum,
    origTs: dailyTran.origTs,
    procTs: procTimestamp,
  };
}

/**
 * Determines if the batch run should set a non-zero return code.
 * CBTRN02C lines 229-231: RC=4 if any rejections.
 */
export function computeReturnCode(rejectCount: number): number {
  return rejectCount > 0 ? 4 : 0;
}
