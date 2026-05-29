/**
 * TypeScript interface derived from COBOL copybook CVACT01Y.cpy
 * Record layout: ACCOUNT-RECORD (RECLN 300)
 * Source: app/cpy/CVACT01Y.cpy
 */
export interface AccountRecord {
  /** @cobol 05 ACCT-ID PIC 9(11) */
  acctId: number;

  /** @cobol 05 ACCT-ACTIVE-STATUS PIC X(01) */
  acctActiveStatus: string;

  /** @cobol 05 ACCT-CURR-BAL PIC S9(10)V99 */
  acctCurrBal: number;

  /** @cobol 05 ACCT-CREDIT-LIMIT PIC S9(10)V99 */
  acctCreditLimit: number;

  /** @cobol 05 ACCT-CASH-CREDIT-LIMIT PIC S9(10)V99 */
  acctCashCreditLimit: number;

  /** @cobol 05 ACCT-OPEN-DATE PIC X(10) */
  acctOpenDate: string;

  /** @cobol 05 ACCT-EXPIRAION-DATE PIC X(10) */
  acctExpirationDate: string;

  /** @cobol 05 ACCT-REISSUE-DATE PIC X(10) */
  acctReissueDate: string;

  /** @cobol 05 ACCT-CURR-CYC-CREDIT PIC S9(10)V99 */
  acctCurrCycCredit: number;

  /** @cobol 05 ACCT-CURR-CYC-DEBIT PIC S9(10)V99 */
  acctCurrCycDebit: number;

  /** @cobol 05 ACCT-ADDR-ZIP PIC X(10) */
  acctAddrZip: string;

  /** @cobol 05 ACCT-GROUP-ID PIC X(10) */
  acctGroupId: string;
}
