/**
 * Unit tests for CBTRN01C.cbl / CBTRN03C.cbl - Batch Transaction Processing
 * Tests cross-reference lookup, account read, and date range filtering.
 */
import {
  lookupXref,
  readAccount,
  processDailyTransaction,
  isTransactionInDateRange,
  formatTransactionAmount,
} from '../validators/batch-transaction-processing';
import { AccountRecord, CardXrefRecord } from '../models/records';

function makeXrefMap(
  ...entries: CardXrefRecord[]
): Map<string, CardXrefRecord> {
  const map = new Map<string, CardXrefRecord>();
  for (const e of entries) {
    map.set(e.cardNum, e);
  }
  return map;
}

function makeAccountMap(
  ...entries: AccountRecord[]
): Map<string, AccountRecord> {
  const map = new Map<string, AccountRecord>();
  for (const e of entries) {
    map.set(e.acctId, e);
  }
  return map;
}

const testXref: CardXrefRecord = {
  cardNum: '4111111111111111',
  custId: '000000001',
  acctId: '00000012345',
};

const testAccount: AccountRecord = {
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
};

describe('CBTRN01C / CBTRN03C - Batch Transaction Processing', () => {
  describe('lookupXref', () => {
    it('finds existing card cross-reference', () => {
      const xrefMap = makeXrefMap(testXref);
      const result = lookupXref('4111111111111111', xrefMap);
      expect(result.found).toBe(true);
      expect(result.xref!.acctId).toBe('00000012345');
      expect(result.xref!.custId).toBe('000000001');
    });

    it('returns not found for unknown card', () => {
      const xrefMap = makeXrefMap(testXref);
      const result = lookupXref('9999999999999999', xrefMap);
      expect(result.found).toBe(false);
      expect(result.xref).toBeUndefined();
    });
  });

  describe('readAccount', () => {
    it('reads existing account', () => {
      const accountMap = makeAccountMap(testAccount);
      const result = readAccount('00000012345', accountMap);
      expect(result.found).toBe(true);
      expect(result.account!.currBal).toBe(5000.0);
    });

    it('returns not found for unknown account', () => {
      const accountMap = makeAccountMap(testAccount);
      const result = readAccount('99999999999', accountMap);
      expect(result.found).toBe(false);
    });
  });

  describe('processDailyTransaction', () => {
    it('processes valid transaction end-to-end', () => {
      const xrefMap = makeXrefMap(testXref);
      const accountMap = makeAccountMap(testAccount);
      const result = processDailyTransaction(
        '4111111111111111',
        '0000000000000001',
        xrefMap,
        accountMap
      );
      expect(result.status).toBe('processed');
      expect(result.account).toBeDefined();
      expect(result.account!.acctId).toBe('00000012345');
    });

    it('fails with xref_failed for unknown card', () => {
      const xrefMap = makeXrefMap(testXref);
      const accountMap = makeAccountMap(testAccount);
      const result = processDailyTransaction(
        '0000000000000000',
        '0000000000000001',
        xrefMap,
        accountMap
      );
      expect(result.status).toBe('xref_failed');
      expect(result.message).toContain('COULD NOT BE VERIFIED');
      expect(result.message).toContain('0000000000000000');
    });

    it('fails with account_not_found when account missing', () => {
      const orphanXref: CardXrefRecord = {
        cardNum: '5555444433332222',
        custId: '000000002',
        acctId: '99999999999',
      };
      const xrefMap = makeXrefMap(orphanXref);
      const accountMap = makeAccountMap(testAccount);
      const result = processDailyTransaction(
        '5555444433332222',
        '0000000000000002',
        xrefMap,
        accountMap
      );
      expect(result.status).toBe('account_not_found');
      expect(result.message).toContain('NOT FOUND');
    });

    it('includes transaction ID in xref failure message', () => {
      const xrefMap = makeXrefMap();
      const accountMap = makeAccountMap();
      const result = processDailyTransaction(
        '0000000000000000',
        'TRAN-ID-123',
        xrefMap,
        accountMap
      );
      expect(result.message).toContain('TRAN-ID-123');
    });
  });

  describe('isTransactionInDateRange', () => {
    it('returns true for date within range', () => {
      expect(
        isTransactionInDateRange(
          '2024-06-15-10.30.00.000000',
          '2024-06-01',
          '2024-06-30'
        )
      ).toBe(true);
    });

    it('returns true for date on start boundary', () => {
      expect(
        isTransactionInDateRange(
          '2024-06-01-00.00.00.000000',
          '2024-06-01',
          '2024-06-30'
        )
      ).toBe(true);
    });

    it('returns true for date on end boundary', () => {
      expect(
        isTransactionInDateRange(
          '2024-06-30-23.59.59.999999',
          '2024-06-01',
          '2024-06-30'
        )
      ).toBe(true);
    });

    it('returns false for date before range', () => {
      expect(
        isTransactionInDateRange(
          '2024-05-31-23.59.59.999999',
          '2024-06-01',
          '2024-06-30'
        )
      ).toBe(false);
    });

    it('returns false for date after range', () => {
      expect(
        isTransactionInDateRange(
          '2024-07-01-00.00.00.000000',
          '2024-06-01',
          '2024-06-30'
        )
      ).toBe(false);
    });
  });

  describe('formatTransactionAmount', () => {
    it('formats positive amount', () => {
      expect(formatTransactionAmount(100.5)).toBe('+00000100.50');
    });

    it('formats negative amount', () => {
      expect(formatTransactionAmount(-250.75)).toBe('-00000250.75');
    });

    it('formats zero', () => {
      expect(formatTransactionAmount(0)).toBe('+00000000.00');
    });

    it('formats large amount', () => {
      expect(formatTransactionAmount(99999999.99)).toBe('+99999999.99');
    });
  });
});
