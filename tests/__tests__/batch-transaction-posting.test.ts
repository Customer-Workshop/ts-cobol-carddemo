/**
 * Unit tests for CBTRN02C.cbl - Batch Transaction Posting
 * Tests validation pipeline, balance updates, and reject handling.
 */
import {
  validateXrefLookup,
  validateAccountLookup,
  validateTransaction,
  updateAccountBalance,
  updateTranCatBalance,
  mapDailyTranToPosted,
  computeReturnCode,
} from '../validators/batch-transaction-posting';
import {
  AccountRecord,
  CardXrefRecord,
  DailyTransactionRecord,
  TranCatBalRecord,
} from '../models/records';

// Test fixtures
function makeAccount(overrides: Partial<AccountRecord> = {}): AccountRecord {
  return {
    acctId: '00000012345',
    activeStatus: 'Y',
    currBal: 5000.0,
    creditLimit: 10000.0,
    cashCreditLimit: 2000.0,
    openDate: '2020-01-01',
    expirationDate: '2025-12-31',
    reissueDate: '2024-01-01',
    currCycCredit: 3000.0,
    currCycDebit: 1000.0,
    addrZip: '10001',
    groupId: 'GROUP001',
    ...overrides,
  };
}

function makeDailyTran(
  overrides: Partial<DailyTransactionRecord> = {}
): DailyTransactionRecord {
  return {
    dalytranId: '0000000000000001',
    typeCd: '01',
    catCd: 1,
    source: 'POS TERM',
    desc: 'PURCHASE',
    amt: 100.0,
    merchantId: 123456789,
    merchantName: 'ACME STORE',
    merchantCity: 'NEW YORK',
    merchantZip: '10001',
    cardNum: '4111111111111111',
    origTs: '2024-06-15-10.30.00.000000',
    procTs: '',
    ...overrides,
  };
}

function makeXrefMap(...entries: CardXrefRecord[]): Map<string, CardXrefRecord> {
  const map = new Map<string, CardXrefRecord>();
  for (const e of entries) {
    map.set(e.cardNum, e);
  }
  return map;
}

function makeAccountMap(...entries: AccountRecord[]): Map<string, AccountRecord> {
  const map = new Map<string, AccountRecord>();
  for (const e of entries) {
    map.set(e.acctId, e);
  }
  return map;
}

const defaultXref: CardXrefRecord = {
  cardNum: '4111111111111111',
  custId: '000000001',
  acctId: '00000012345',
};

describe('CBTRN02C - Batch Transaction Posting', () => {
  describe('validateXrefLookup', () => {
    it('passes for known card number', () => {
      const xrefMap = makeXrefMap(defaultXref);
      const result = validateXrefLookup('4111111111111111', xrefMap);
      expect(result.failReason).toBe(0);
    });

    it('fails with reason 100 for unknown card', () => {
      const xrefMap = makeXrefMap(defaultXref);
      const result = validateXrefLookup('9999999999999999', xrefMap);
      expect(result.failReason).toBe(100);
      expect(result.failReasonDesc).toBe('INVALID CARD NUMBER FOUND');
    });
  });

  describe('validateAccountLookup', () => {
    it('fails with reason 101 when account not found', () => {
      const accounts = makeAccountMap();
      const tran = makeDailyTran();
      const result = validateAccountLookup(defaultXref, accounts, tran);
      expect(result.failReason).toBe(101);
      expect(result.failReasonDesc).toBe('ACCOUNT RECORD NOT FOUND');
    });

    it('fails with reason 102 for over-limit transaction', () => {
      const account = makeAccount({
        creditLimit: 5000.0,
        currCycCredit: 4500.0,
        currCycDebit: 0.0,
      });
      const accounts = makeAccountMap(account);
      const tran = makeDailyTran({ amt: 600.0 });
      const result = validateAccountLookup(defaultXref, accounts, tran);
      expect(result.failReason).toBe(102);
      expect(result.failReasonDesc).toBe('OVERLIMIT TRANSACTION');
    });

    it('passes when transaction is within credit limit', () => {
      const account = makeAccount({
        creditLimit: 10000.0,
        currCycCredit: 3000.0,
        currCycDebit: 1000.0,
      });
      const accounts = makeAccountMap(account);
      const tran = makeDailyTran({ amt: 500.0 });
      const result = validateAccountLookup(defaultXref, accounts, tran);
      expect(result.failReason).toBe(0);
    });

    it('passes when transaction amount exactly hits credit limit', () => {
      const account = makeAccount({
        creditLimit: 5000.0,
        currCycCredit: 3000.0,
        currCycDebit: 1000.0,
      });
      const accounts = makeAccountMap(account);
      // tempBal = 3000 - 1000 + 3000 = 5000 == creditLimit
      const tran = makeDailyTran({ amt: 3000.0 });
      const result = validateAccountLookup(defaultXref, accounts, tran);
      expect(result.failReason).toBe(0);
    });

    it('fails with reason 103 when account has expired', () => {
      const account = makeAccount({ expirationDate: '2023-12-31' });
      const accounts = makeAccountMap(account);
      const tran = makeDailyTran({
        origTs: '2024-06-15-10.30.00.000000',
      });
      const result = validateAccountLookup(defaultXref, accounts, tran);
      expect(result.failReason).toBe(103);
      expect(result.failReasonDesc).toContain('EXPIRATION');
    });

    it('passes when transaction date equals expiration date', () => {
      const account = makeAccount({ expirationDate: '2024-06-15' });
      const accounts = makeAccountMap(account);
      const tran = makeDailyTran({
        origTs: '2024-06-15-10.30.00.000000',
      });
      const result = validateAccountLookup(defaultXref, accounts, tran);
      expect(result.failReason).toBe(0);
    });
  });

  describe('validateTransaction (full pipeline)', () => {
    it('validates successfully end-to-end', () => {
      const xrefMap = makeXrefMap(defaultXref);
      const accounts = makeAccountMap(makeAccount());
      const tran = makeDailyTran();
      const result = validateTransaction(tran, xrefMap, accounts);
      expect(result.failReason).toBe(0);
    });

    it('fails at xref step for unknown card', () => {
      const xrefMap = makeXrefMap(defaultXref);
      const accounts = makeAccountMap(makeAccount());
      const tran = makeDailyTran({ cardNum: '0000000000000000' });
      const result = validateTransaction(tran, xrefMap, accounts);
      expect(result.failReason).toBe(100);
    });

    it('fails at account step when account missing', () => {
      const xrefMap = makeXrefMap({
        cardNum: '4111111111111111',
        custId: '000000001',
        acctId: '99999999999',
      });
      const accounts = makeAccountMap();
      const tran = makeDailyTran();
      const result = validateTransaction(tran, xrefMap, accounts);
      expect(result.failReason).toBe(101);
    });
  });

  describe('updateAccountBalance', () => {
    it('adds positive amount to current balance and cycle credit', () => {
      const account = makeAccount({
        currBal: 5000.0,
        currCycCredit: 3000.0,
        currCycDebit: 1000.0,
      });
      const updated = updateAccountBalance(account, 200.0);
      expect(updated.currBal).toBe(5200.0);
      expect(updated.currCycCredit).toBe(3200.0);
      expect(updated.currCycDebit).toBe(1000.0);
    });

    it('adds negative amount to current balance and cycle debit', () => {
      const account = makeAccount({
        currBal: 5000.0,
        currCycCredit: 3000.0,
        currCycDebit: 1000.0,
      });
      const updated = updateAccountBalance(account, -300.0);
      expect(updated.currBal).toBe(4700.0);
      expect(updated.currCycCredit).toBe(3000.0);
      expect(updated.currCycDebit).toBe(700.0);
    });

    it('handles zero amount (goes to credit)', () => {
      const account = makeAccount({
        currBal: 5000.0,
        currCycCredit: 3000.0,
        currCycDebit: 1000.0,
      });
      const updated = updateAccountBalance(account, 0);
      expect(updated.currBal).toBe(5000.0);
      expect(updated.currCycCredit).toBe(3000.0);
      expect(updated.currCycDebit).toBe(1000.0);
    });

    it('does not mutate original account', () => {
      const account = makeAccount({ currBal: 5000.0 });
      updateAccountBalance(account, 200.0);
      expect(account.currBal).toBe(5000.0);
    });
  });

  describe('updateTranCatBalance', () => {
    it('creates new record when none exists', () => {
      const result = updateTranCatBalance(null, '00000012345', '01', 1, 100.0);
      expect(result.isNew).toBe(true);
      expect(result.record.balance).toBe(100.0);
      expect(result.record.acctId).toBe('00000012345');
      expect(result.record.typeCd).toBe('01');
      expect(result.record.catCd).toBe(1);
    });

    it('updates existing record balance', () => {
      const existing: TranCatBalRecord = {
        acctId: '00000012345',
        typeCd: '01',
        catCd: 1,
        balance: 500.0,
      };
      const result = updateTranCatBalance(
        existing,
        '00000012345',
        '01',
        1,
        200.0
      );
      expect(result.isNew).toBe(false);
      expect(result.record.balance).toBe(700.0);
    });

    it('handles negative transaction amount on existing record', () => {
      const existing: TranCatBalRecord = {
        acctId: '00000012345',
        typeCd: '01',
        catCd: 1,
        balance: 500.0,
      };
      const result = updateTranCatBalance(
        existing,
        '00000012345',
        '01',
        1,
        -150.0
      );
      expect(result.record.balance).toBe(350.0);
    });
  });

  describe('mapDailyTranToPosted', () => {
    it('maps all fields correctly', () => {
      const dailyTran = makeDailyTran();
      const posted = mapDailyTranToPosted(
        dailyTran,
        '2024-06-15-11.00.00.000000'
      );
      expect(posted.tranId).toBe(dailyTran.dalytranId);
      expect(posted.tranTypeCd).toBe(dailyTran.typeCd);
      expect(posted.tranCatCd).toBe(dailyTran.catCd);
      expect(posted.tranSource).toBe(dailyTran.source);
      expect(posted.tranDesc).toBe(dailyTran.desc);
      expect(posted.tranAmt).toBe(dailyTran.amt);
      expect(posted.merchantId).toBe(dailyTran.merchantId);
      expect(posted.merchantName).toBe(dailyTran.merchantName);
      expect(posted.merchantCity).toBe(dailyTran.merchantCity);
      expect(posted.merchantZip).toBe(dailyTran.merchantZip);
      expect(posted.cardNum).toBe(dailyTran.cardNum);
      expect(posted.origTs).toBe(dailyTran.origTs);
      expect(posted.procTs).toBe('2024-06-15-11.00.00.000000');
    });
  });

  describe('computeReturnCode', () => {
    it('returns 0 when no rejections', () => {
      expect(computeReturnCode(0)).toBe(0);
    });

    it('returns 4 when there are rejections', () => {
      expect(computeReturnCode(1)).toBe(4);
      expect(computeReturnCode(100)).toBe(4);
    });
  });
});
