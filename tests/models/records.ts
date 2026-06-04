/**
 * TypeScript representations of COBOL copybook record layouts.
 * These mirror the data structures defined in app/cpy/*.cpy.
 */

/** CVACT01Y.cpy - Account record (RECLN 300) */
export interface AccountRecord {
  acctId: string;            // PIC 9(11)
  activeStatus: string;      // PIC X(01)
  currBal: number;           // PIC S9(10)V99
  creditLimit: number;       // PIC S9(10)V99
  cashCreditLimit: number;   // PIC S9(10)V99
  openDate: string;          // PIC X(10) YYYY-MM-DD
  expirationDate: string;    // PIC X(10) YYYY-MM-DD
  reissueDate: string;       // PIC X(10) YYYY-MM-DD
  currCycCredit: number;     // PIC S9(10)V99
  currCycDebit: number;      // PIC S9(10)V99
  addrZip: string;           // PIC X(10)
  groupId: string;           // PIC X(10)
}

/** CVTRA05Y.cpy - Transaction record (RECLN 350) */
export interface TransactionRecord {
  tranId: string;            // PIC X(16)
  tranTypeCd: string;        // PIC X(02)
  tranCatCd: number;         // PIC 9(04)
  tranSource: string;        // PIC X(10)
  tranDesc: string;          // PIC X(100)
  tranAmt: number;           // PIC S9(09)V99
  merchantId: number;        // PIC 9(09)
  merchantName: string;      // PIC X(50)
  merchantCity: string;      // PIC X(50)
  merchantZip: string;       // PIC X(10)
  cardNum: string;           // PIC X(16)
  origTs: string;            // PIC X(26)
  procTs: string;            // PIC X(26)
}

/** CVTRA06Y.cpy - Daily transaction record (RECLN 350) */
export interface DailyTransactionRecord {
  dalytranId: string;        // PIC X(16)
  typeCd: string;            // PIC X(02)
  catCd: number;             // PIC 9(04)
  source: string;            // PIC X(10)
  desc: string;              // PIC X(100)
  amt: number;               // PIC S9(09)V99
  merchantId: number;        // PIC 9(09)
  merchantName: string;      // PIC X(50)
  merchantCity: string;      // PIC X(50)
  merchantZip: string;       // PIC X(10)
  cardNum: string;           // PIC X(16)
  origTs: string;            // PIC X(26)
  procTs: string;            // PIC X(26)
}

/** CVACT03Y.cpy - Card cross-reference record (RECLN 50) */
export interface CardXrefRecord {
  cardNum: string;           // PIC X(16)
  custId: string;            // PIC 9(09)
  acctId: string;            // PIC 9(11)
}

/** CVCUS01Y.cpy - Customer record (RECLN 500) */
export interface CustomerRecord {
  custId: string;            // PIC 9(09)
  firstName: string;         // PIC X(25)
  middleName: string;        // PIC X(25)
  lastName: string;          // PIC X(25)
  addrLine1: string;         // PIC X(50)
  addrLine2: string;         // PIC X(50)
  addrLine3: string;         // PIC X(50)
  addrStateCd: string;       // PIC X(02)
  addrCountryCd: string;     // PIC X(03)
  addrZip: string;           // PIC X(10)
  phoneNum1: string;         // PIC X(15)
  phoneNum2: string;         // PIC X(15)
  ssn: string;               // PIC 9(09)
  govtIssuedId: string;      // PIC X(20)
  dobYyyyMmDd: string;       // PIC X(10)
  eftAccountId: string;      // PIC X(10)
  priCardHolderInd: string;  // PIC X(01)
  ficoCreditScore: number;   // PIC 9(03)
}

/** CSUSR01Y.cpy - Security/user record */
export interface SecUserData {
  usrId: string;             // PIC X(08)
  usrFname: string;          // PIC X(20)
  usrLname: string;          // PIC X(20)
  usrPwd: string;            // PIC X(08)
  usrType: string;           // PIC X(01) - 'A' for admin, 'U' for user
}

/** CVTRA01Y.cpy - Transaction category balance (RECLN 50) */
export interface TranCatBalRecord {
  acctId: string;            // PIC 9(11)
  typeCd: string;            // PIC X(02)
  catCd: number;             // PIC 9(04)
  balance: number;           // PIC S9(09)V99
}

/** COCOM01Y.cpy - Communication area */
export interface CardDemoCommarea {
  fromTranId: string;
  fromProgram: string;
  toTranId: string;
  toProgram: string;
  userId: string;
  userType: 'A' | 'U';
  pgmContext: number;
  custId: string;
  custFname: string;
  custMname: string;
  custLname: string;
  acctId: string;
  acctStatus: string;
  cardNum: string;
  lastMap: string;
  lastMapset: string;
}

/** Validation result from CBTRN02C */
export interface ValidationResult {
  failReason: number;       // 0 = pass, 100+ = fail
  failReasonDesc: string;
}
