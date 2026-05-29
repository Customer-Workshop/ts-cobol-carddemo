# CardDemo COBOL-to-TypeScript Migration Specification

## 1. Overview

This document is a **self-contained migration specification** for converting the CardDemo COBOL mainframe application into a modern TypeScript/Node.js application. It is designed so that a developer or AI agent can pick up any single copybook migration task and execute it independently.

**Source repository:** `Cognition-Partner-Workshops/ts-cobol-carddemo`
**Source language:** COBOL (with CICS, VSAM, BMS, JCL)
**Target language:** TypeScript (Node.js, with PostgreSQL, Express/Fastify, React)

---

## 2. Target Architecture

| Layer | COBOL Concept | TypeScript Target |
|-------|---------------|-------------------|
| Data Definitions (Copybooks) | `.cpy` files with `PIC`, `REDEFINES`, `OCCURS` | TypeScript interfaces/types in `src/types/` |
| Data Storage | VSAM KSDS/AIX files | PostgreSQL tables + TypeORM/Prisma entities in `src/entities/` |
| Online Programs | CICS COBOL programs (`.cbl`) | Express/Fastify route handlers in `src/routes/` |
| Batch Programs | JCL + COBOL batch | Node.js CLI scripts in `src/batch/` |
| Screen Maps | BMS maps | React components in `src/ui/` (future phase) |
| Inter-program Comm | COMMAREA | TypeScript shared state / function parameters |

---

## 3. COBOL-to-TypeScript Type Mapping Rules

Every copybook migration MUST follow these type-mapping rules consistently:

| COBOL PIC Clause | TypeScript Type | Notes |
|---|---|---|
| `PIC X(n)` | `string` | Fixed-length alphanumeric → trimmed string |
| `PIC 9(n)` | `number` | Unsigned integer |
| `PIC S9(n)` | `number` | Signed integer |
| `PIC S9(n)V99` / `PIC S9(n)V9(m)` | `number` | Decimal — use `number` (or `Decimal` from `decimal.js` for financial precision) |
| `PIC 9(n) COMP` / `COMP-3` | `number` | Binary/packed decimal → number |
| `PIC X(01)` with `88`-level values | `enum` or union type | Map 88-levels to TypeScript enum members or string literal unions |
| `FILLER` | *(omit)* | Do not include FILLER fields in TypeScript types |
| `REDEFINES` | Union type or discriminated union | Use TypeScript discriminated unions when the REDEFINES represents variant records |
| `OCCURS n TIMES` | `Array<T>` (length `n`) | Fixed-size arrays become typed arrays; add length validation in runtime |
| `OCCURS DEPENDING ON` | `Array<T>` | Variable-length array |
| `VALUE` clauses | Default values | Provide defaults in factory functions, not in the type itself |

### Naming Conventions

- COBOL field `CUST-FIRST-NAME` → TypeScript `custFirstName` (camelCase, drop hyphens)
- COBOL record `01 CUSTOMER-RECORD` → TypeScript `interface CustomerRecord`
- COBOL copybook `CVCUS01Y.cpy` → TypeScript file `src/types/customer-record.types.ts`
- Enum from 88-levels: `CDEMO-USRTYP-ADMIN VALUE 'A'` → `enum UserType { Admin = 'A', User = 'U' }`

### File Naming Convention

Each copybook `XXXX.cpy` produces a TypeScript file at:
```
src/types/<descriptive-kebab-name>.types.ts
```

---

## 4. Complete Copybook Inventory

Below is the full inventory of all 30 copybooks in `app/cpy/`, grouped by functional domain. Each entry contains the **complete COBOL data structure** so that a migration agent can work without access to the original files.

---

### 4.1 — Entity/Record Copybooks (Data Storage Layer)

These define the core business data records stored in VSAM files. Each becomes a TypeScript interface AND a database entity.

---

#### 4.1.1 `CVACT01Y.cpy` — Account Record (RECLN 300)

**Purpose:** Defines the Account entity — the primary financial account.
**VSAM File:** `AWS.M2.CARDDEMO.ACCTDATA.PS` (FB 300)
**Target file:** `src/types/account-record.types.ts`
**Target entity:** `src/entities/account.entity.ts`
**Target DB table:** `accounts`

**COBOL Structure:**
```cobol
01  ACCOUNT-RECORD.
    05  ACCT-ID                           PIC 9(11).
    05  ACCT-ACTIVE-STATUS                PIC X(01).
    05  ACCT-CURR-BAL                     PIC S9(10)V99.
    05  ACCT-CREDIT-LIMIT                 PIC S9(10)V99.
    05  ACCT-CASH-CREDIT-LIMIT            PIC S9(10)V99.
    05  ACCT-OPEN-DATE                    PIC X(10).
    05  ACCT-EXPIRAION-DATE               PIC X(10).
    05  ACCT-REISSUE-DATE                 PIC X(10).
    05  ACCT-CURR-CYC-CREDIT              PIC S9(10)V99.
    05  ACCT-CURR-CYC-DEBIT               PIC S9(10)V99.
    05  ACCT-ADDR-ZIP                     PIC X(10).
    05  ACCT-GROUP-ID                     PIC X(10).
    05  FILLER                            PIC X(178).
```

**Expected TypeScript output:**
```typescript
export interface AccountRecord {
  acctId: number;              // PIC 9(11)
  acctActiveStatus: string;    // PIC X(01)
  acctCurrBal: number;         // PIC S9(10)V99
  acctCreditLimit: number;     // PIC S9(10)V99
  acctCashCreditLimit: number; // PIC S9(10)V99
  acctOpenDate: string;        // PIC X(10) — consider Date type
  acctExpiraionDate: string;   // PIC X(10) — note: original has typo "EXPIRAION"
  acctReissueDate: string;     // PIC X(10)
  acctCurrCycCredit: number;   // PIC S9(10)V99
  acctCurrCycDebit: number;    // PIC S9(10)V99
  acctAddrZip: string;         // PIC X(10)
  acctGroupId: string;         // PIC X(10)
}
```

---

#### 4.1.2 `CVACT02Y.cpy` — Card Record (RECLN 150)

**Purpose:** Defines the Credit Card entity.
**VSAM File:** `AWS.M2.CARDDEMO.CARDDATA.PS` (FB 150)
**Target file:** `src/types/card-record.types.ts`
**Target entity:** `src/entities/card.entity.ts`
**Target DB table:** `cards`

**COBOL Structure:**
```cobol
01  CARD-RECORD.
    05  CARD-NUM                          PIC X(16).
    05  CARD-ACCT-ID                      PIC 9(11).
    05  CARD-CVV-CD                       PIC 9(03).
    05  CARD-EMBOSSED-NAME                PIC X(50).
    05  CARD-EXPIRAION-DATE               PIC X(10).
    05  CARD-ACTIVE-STATUS                PIC X(01).
    05  FILLER                            PIC X(59).
```

**Expected TypeScript output:**
```typescript
export interface CardRecord {
  cardNum: string;             // PIC X(16)
  cardAcctId: number;          // PIC 9(11)
  cardCvvCd: number;           // PIC 9(03)
  cardEmbossedName: string;    // PIC X(50)
  cardExpiraionDate: string;   // PIC X(10)
  cardActiveStatus: string;    // PIC X(01)
}
```

---

#### 4.1.3 `CVACT03Y.cpy` — Card Cross-Reference (RECLN 50)

**Purpose:** Links Cards to Customers and Accounts (XREF).
**VSAM File:** `AWS.M2.CARDDEMO.CARDXREF.PS` (FB 50)
**Target file:** `src/types/card-xref-record.types.ts`
**Target entity:** `src/entities/card-xref.entity.ts`
**Target DB table:** `card_xref`

**COBOL Structure:**
```cobol
01 CARD-XREF-RECORD.
    05  XREF-CARD-NUM                     PIC X(16).
    05  XREF-CUST-ID                      PIC 9(09).
    05  XREF-ACCT-ID                      PIC 9(11).
    05  FILLER                            PIC X(14).
```

**Expected TypeScript output:**
```typescript
export interface CardXrefRecord {
  xrefCardNum: string;   // PIC X(16)
  xrefCustId: number;    // PIC 9(09)
  xrefAcctId: number;    // PIC 9(11)
}
```

---

#### 4.1.4 `CVCUS01Y.cpy` — Customer Record (RECLN 500)

**Purpose:** Defines the Customer entity — personal information, addresses, contact details.
**VSAM File:** `AWS.M2.CARDDEMO.CUSTDATA.PS` (FB 500)
**Target file:** `src/types/customer-record.types.ts`
**Target entity:** `src/entities/customer.entity.ts`
**Target DB table:** `customers`

**COBOL Structure:**
```cobol
01  CUSTOMER-RECORD.
    05  CUST-ID                                 PIC 9(09).
    05  CUST-FIRST-NAME                         PIC X(25).
    05  CUST-MIDDLE-NAME                        PIC X(25).
    05  CUST-LAST-NAME                          PIC X(25).
    05  CUST-ADDR-LINE-1                        PIC X(50).
    05  CUST-ADDR-LINE-2                        PIC X(50).
    05  CUST-ADDR-LINE-3                        PIC X(50).
    05  CUST-ADDR-STATE-CD                      PIC X(02).
    05  CUST-ADDR-COUNTRY-CD                    PIC X(03).
    05  CUST-ADDR-ZIP                           PIC X(10).
    05  CUST-PHONE-NUM-1                        PIC X(15).
    05  CUST-PHONE-NUM-2                        PIC X(15).
    05  CUST-SSN                                PIC 9(09).
    05  CUST-GOVT-ISSUED-ID                     PIC X(20).
    05  CUST-DOB-YYYY-MM-DD                     PIC X(10).
    05  CUST-EFT-ACCOUNT-ID                     PIC X(10).
    05  CUST-PRI-CARD-HOLDER-IND                PIC X(01).
    05  CUST-FICO-CREDIT-SCORE                  PIC 9(03).
    05  FILLER                                  PIC X(168).
```

**Expected TypeScript output:**
```typescript
export interface CustomerRecord {
  custId: number;                  // PIC 9(09)
  custFirstName: string;           // PIC X(25)
  custMiddleName: string;          // PIC X(25)
  custLastName: string;            // PIC X(25)
  custAddrLine1: string;           // PIC X(50)
  custAddrLine2: string;           // PIC X(50)
  custAddrLine3: string;           // PIC X(50)
  custAddrStateCd: string;         // PIC X(02)
  custAddrCountryCd: string;       // PIC X(03)
  custAddrZip: string;             // PIC X(10)
  custPhoneNum1: string;           // PIC X(15)
  custPhoneNum2: string;           // PIC X(15)
  custSsn: number;                 // PIC 9(09)
  custGovtIssuedId: string;        // PIC X(20)
  custDobYyyyMmDd: string;        // PIC X(10) — consider Date type
  custEftAccountId: string;        // PIC X(10)
  custPriCardHolderInd: string;    // PIC X(01)
  custFicoCreditScore: number;     // PIC 9(03)
}
```

---

#### 4.1.5 `CSUSR01Y.cpy` — User Security Record (RECLN 80)

**Purpose:** Defines the User/Security entity for authentication.
**VSAM File:** `AWS.M2.CARDDEMO.USRSEC.PS` (FB 80)
**Target file:** `src/types/user-security.types.ts`
**Target entity:** `src/entities/user-security.entity.ts`
**Target DB table:** `user_security`

**COBOL Structure:**
```cobol
01 SEC-USER-DATA.
   05 SEC-USR-ID                 PIC X(08).
   05 SEC-USR-FNAME              PIC X(20).
   05 SEC-USR-LNAME              PIC X(20).
   05 SEC-USR-PWD                PIC X(08).
   05 SEC-USR-TYPE               PIC X(01).
   05 SEC-USR-FILLER             PIC X(23).
```

**Expected TypeScript output:**
```typescript
export enum UserType {
  Admin = 'A',
  User = 'U',
}

export interface SecUserData {
  secUsrId: string;        // PIC X(08)
  secUsrFname: string;     // PIC X(20)
  secUsrLname: string;     // PIC X(20)
  secUsrPwd: string;       // PIC X(08) — note: in production, hash passwords
  secUsrType: UserType;    // PIC X(01) with 88-level values
}
```

---

#### 4.1.6 `CUSTREC.cpy` — Customer Record (Alternative Layout)

**Purpose:** Alternative customer record layout (same structure as CVCUS01Y but with slight field-name differences). Used by specific programs.
**Target file:** `src/types/custrec.types.ts`

**COBOL Structure:**
```cobol
01  CUSTOMER-RECORD.
    05  CUST-ID                                 PIC 9(09).
    05  CUST-FIRST-NAME                         PIC X(25).
    05  CUST-MIDDLE-NAME                        PIC X(25).
    05  CUST-LAST-NAME                          PIC X(25).
    05  CUST-ADDR-LINE-1                        PIC X(50).
    05  CUST-ADDR-LINE-2                        PIC X(50).
    05  CUST-ADDR-LINE-3                        PIC X(50).
    05  CUST-ADDR-STATE-CD                      PIC X(02).
    05  CUST-ADDR-COUNTRY-CD                    PIC X(03).
    05  CUST-ADDR-ZIP                           PIC X(10).
    05  CUST-PHONE-NUM-1                        PIC X(15).
    05  CUST-PHONE-NUM-2                        PIC X(15).
    05  CUST-SSN                                PIC 9(09).
    05  CUST-GOVT-ISSUED-ID                     PIC X(20).
    05  CUST-DOB-YYYYMMDD                       PIC X(10).
    05  CUST-EFT-ACCOUNT-ID                     PIC X(10).
    05  CUST-PRI-CARD-HOLDER-IND                PIC X(01).
    05  CUST-FICO-CREDIT-SCORE                  PIC 9(03).
    05  FILLER                                  PIC X(168).
```

**Migration note:** This is nearly identical to `CVCUS01Y.cpy`. The only difference is the DOB field name (`CUST-DOB-YYYYMMDD` vs `CUST-DOB-YYYY-MM-DD`). Migrate this as a **re-export or type alias** of `CustomerRecord` from `customer-record.types.ts`. Add a note documenting the field-name difference.

---

### 4.2 — Transaction Copybooks

---

#### 4.2.1 `CVTRA01Y.cpy` — Transaction Category Balance (RECLN 50)

**Purpose:** Tracks aggregated balances by transaction category per account.
**VSAM File:** `AWS.M2.CARDDEMO.TCATBALF.PS` (FB 50)
**Target file:** `src/types/tran-cat-bal-record.types.ts`
**Target entity:** `src/entities/tran-cat-bal.entity.ts`
**Target DB table:** `tran_cat_balances`

**COBOL Structure:**
```cobol
01  TRAN-CAT-BAL-RECORD.
    05  TRAN-CAT-KEY.
       10 TRANCAT-ACCT-ID                       PIC 9(11).
       10 TRANCAT-TYPE-CD                       PIC X(02).
       10 TRANCAT-CD                            PIC 9(04).
    05  TRAN-CAT-BAL                            PIC S9(09)V99.
    05  FILLER                                  PIC X(22).
```

**Expected TypeScript output:**
```typescript
export interface TranCatKey {
  trancatAcctId: number;   // PIC 9(11)
  trancatTypeCd: string;   // PIC X(02)
  trancatCd: number;       // PIC 9(04)
}

export interface TranCatBalRecord {
  tranCatKey: TranCatKey;
  tranCatBal: number;      // PIC S9(09)V99
}
```

---

#### 4.2.2 `CVTRA02Y.cpy` — Disclosure Group (RECLN 50)

**Purpose:** Defines interest rate disclosure groups linked to account groups and transaction types.
**VSAM File:** `AWS.M2.CARDDEMO.DISCGRP.PS` (FB 50)
**Target file:** `src/types/disclosure-group-record.types.ts`
**Target entity:** `src/entities/disclosure-group.entity.ts`
**Target DB table:** `disclosure_groups`

**COBOL Structure:**
```cobol
01  DIS-GROUP-RECORD.
    05  DIS-GROUP-KEY.
       10 DIS-ACCT-GROUP-ID                     PIC X(10).
       10 DIS-TRAN-TYPE-CD                      PIC X(02).
       10 DIS-TRAN-CAT-CD                       PIC 9(04).
    05  DIS-INT-RATE                            PIC S9(04)V99.
    05  FILLER                                  PIC X(28).
```

**Expected TypeScript output:**
```typescript
export interface DisGroupKey {
  disAcctGroupId: string;   // PIC X(10)
  disTranTypeCd: string;    // PIC X(02)
  disTranCatCd: number;     // PIC 9(04)
}

export interface DisGroupRecord {
  disGroupKey: DisGroupKey;
  disIntRate: number;       // PIC S9(04)V99
}
```

---

#### 4.2.3 `CVTRA03Y.cpy` — Transaction Type (RECLN 60)

**Purpose:** Reference data for transaction types (e.g., purchase, cash advance).
**VSAM File:** `AWS.M2.CARDDEMO.TRANTYPE.PS` (FB 60)
**Target file:** `src/types/tran-type-record.types.ts`
**Target entity:** `src/entities/tran-type.entity.ts`
**Target DB table:** `tran_types`

**COBOL Structure:**
```cobol
01  TRAN-TYPE-RECORD.
    05  TRAN-TYPE                               PIC X(02).
    05  TRAN-TYPE-DESC                          PIC X(50).
    05  FILLER                                  PIC X(08).
```

**Expected TypeScript output:**
```typescript
export interface TranTypeRecord {
  tranType: string;        // PIC X(02) — primary key
  tranTypeDesc: string;    // PIC X(50)
}
```

---

#### 4.2.4 `CVTRA04Y.cpy` — Transaction Category Type (RECLN 60)

**Purpose:** Reference data for transaction category sub-types.
**VSAM File:** `AWS.M2.CARDDEMO.TRANCATG.PS` (FB 60)
**Target file:** `src/types/tran-cat-record.types.ts`
**Target entity:** `src/entities/tran-cat.entity.ts`
**Target DB table:** `tran_categories`

**COBOL Structure:**
```cobol
01  TRAN-CAT-RECORD.
    05  TRAN-CAT-KEY.
       10  TRAN-TYPE-CD                         PIC X(02).
       10  TRAN-CAT-CD                          PIC 9(04).
    05  TRAN-CAT-TYPE-DESC                      PIC X(50).
    05  FILLER                                  PIC X(04).
```

**Expected TypeScript output:**
```typescript
export interface TranCatKey {
  tranTypeCd: string;     // PIC X(02)
  tranCatCd: number;      // PIC 9(04)
}

export interface TranCatRecord {
  tranCatKey: TranCatKey;
  tranCatTypeDesc: string;  // PIC X(50)
}
```

---

#### 4.2.5 `CVTRA05Y.cpy` — Transaction Record (RECLN 350)

**Purpose:** Defines an individual transaction (online transaction data).
**VSAM File:** `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS` (FB 350)
**Target file:** `src/types/tran-record.types.ts`
**Target entity:** `src/entities/transaction.entity.ts`
**Target DB table:** `transactions`

**COBOL Structure:**
```cobol
01  TRAN-RECORD.
    05  TRAN-ID                                 PIC X(16).
    05  TRAN-TYPE-CD                            PIC X(02).
    05  TRAN-CAT-CD                             PIC 9(04).
    05  TRAN-SOURCE                             PIC X(10).
    05  TRAN-DESC                               PIC X(100).
    05  TRAN-AMT                                PIC S9(09)V99.
    05  TRAN-MERCHANT-ID                        PIC 9(09).
    05  TRAN-MERCHANT-NAME                      PIC X(50).
    05  TRAN-MERCHANT-CITY                      PIC X(50).
    05  TRAN-MERCHANT-ZIP                       PIC X(10).
    05  TRAN-CARD-NUM                           PIC X(16).
    05  TRAN-ORIG-TS                            PIC X(26).
    05  TRAN-PROC-TS                            PIC X(26).
    05  FILLER                                  PIC X(20).
```

**Expected TypeScript output:**
```typescript
export interface TranRecord {
  tranId: string;            // PIC X(16)
  tranTypeCd: string;        // PIC X(02)
  tranCatCd: number;         // PIC 9(04)
  tranSource: string;        // PIC X(10)
  tranDesc: string;          // PIC X(100)
  tranAmt: number;           // PIC S9(09)V99
  tranMerchantId: number;    // PIC 9(09)
  tranMerchantName: string;  // PIC X(50)
  tranMerchantCity: string;  // PIC X(50)
  tranMerchantZip: string;   // PIC X(10)
  tranCardNum: string;       // PIC X(16)
  tranOrigTs: string;        // PIC X(26) — consider Date type
  tranProcTs: string;        // PIC X(26) — consider Date type
}
```

---

#### 4.2.6 `CVTRA06Y.cpy` — Daily Transaction Record (RECLN 350)

**Purpose:** Daily batch transaction record (same layout as CVTRA05Y but used for batch posting).
**VSAM File:** `AWS.M2.CARDDEMO.DALYTRAN.PS` (FB 350)
**Target file:** `src/types/daily-tran-record.types.ts`
**Target entity:** `src/entities/daily-transaction.entity.ts`
**Target DB table:** `daily_transactions`

**COBOL Structure:**
```cobol
01  DALYTRAN-RECORD.
    05  DALYTRAN-ID                             PIC X(16).
    05  DALYTRAN-TYPE-CD                        PIC X(02).
    05  DALYTRAN-CAT-CD                         PIC 9(04).
    05  DALYTRAN-SOURCE                         PIC X(10).
    05  DALYTRAN-DESC                           PIC X(100).
    05  DALYTRAN-AMT                            PIC S9(09)V99.
    05  DALYTRAN-MERCHANT-ID                    PIC 9(09).
    05  DALYTRAN-MERCHANT-NAME                  PIC X(50).
    05  DALYTRAN-MERCHANT-CITY                  PIC X(50).
    05  DALYTRAN-MERCHANT-ZIP                   PIC X(10).
    05  DALYTRAN-CARD-NUM                       PIC X(16).
    05  DALYTRAN-ORIG-TS                        PIC X(26).
    05  DALYTRAN-PROC-TS                        PIC X(26).
    05  FILLER                                  PIC X(20).
```

**Migration note:** Structurally identical to `CVTRA05Y.cpy`. Migrate as a type alias or re-export: `export type DailyTranRecord = TranRecord;` with a comment noting its batch context.

---

#### 4.2.7 `CVTRA07Y.cpy` — Transaction Report Layout

**Purpose:** Defines the print layout for daily transaction reports (headers, detail lines, totals).
**Target file:** `src/types/tran-report.types.ts`

**COBOL Structure:**
```cobol
01  REPORT-NAME-HEADER.
    05  REPT-SHORT-NAME                  PIC X(38) VALUE 'DALYREPT'.
    05  REPT-LONG-NAME                   PIC X(41) VALUE 'Daily Transaction Report'.
    05  REPT-DATE-HEADER                 PIC X(12) VALUE 'Date Range: '.
    05  REPT-START-DATE                  PIC X(10) VALUE SPACES.
    05  FILLER                           PIC X(04) VALUE ' to '.
    05  REPT-END-DATE                    PIC X(10) VALUE SPACES.

01  TRANSACTION-DETAIL-REPORT.
    05  TRAN-REPORT-TRANS-ID             PIC X(16).
    05  FILLER                           PIC X(01) VALUE SPACES.
    05  TRAN-REPORT-ACCOUNT-ID           PIC X(11).
    05  FILLER                           PIC X(01) VALUE SPACES.
    05  TRAN-REPORT-TYPE-CD              PIC X(02).
    05  FILLER                           PIC X(01) VALUE '-'.
    05  TRAN-REPORT-TYPE-DESC            PIC X(15).
    05  FILLER                           PIC X(01) VALUE SPACES.
    05  TRAN-REPORT-CAT-CD               PIC 9(04).
    05  FILLER                           PIC X(01) VALUE '-'.
    05  TRAN-REPORT-CAT-DESC             PIC X(29).
    05  FILLER                           PIC X(01) VALUE SPACES.
    05  TRAN-REPORT-SOURCE               PIC X(10).
    05  FILLER                           PIC X(04) VALUE SPACES.
    05  TRAN-REPORT-AMT                  PIC -ZZZ,ZZZ,ZZZ.ZZ.
    05  FILLER                           PIC X(02) VALUE SPACES.

01  TRANSACTION-HEADER-1.
    05  FILLER  PIC X(17) VALUE 'Transaction ID'.
    05  FILLER  PIC X(12) VALUE 'Account ID'.
    05  FILLER  PIC X(19) VALUE 'Transaction Type'.
    05  FILLER  PIC X(35) VALUE 'Tran Category'.
    05  FILLER  PIC X(14) VALUE 'Tran Source'.
    05  FILLER  PIC X VALUE SPACES.
    05  FILLER  PIC X(16) VALUE '        Amount'.

01  TRANSACTION-HEADER-2  PIC X(133) VALUE ALL '-'.

01  REPORT-PAGE-TOTALS.
    05  FILLER              PIC X(11) VALUE 'Page Total'.
    05  FILLER              PIC X(86) VALUE ALL '.'.
    05  REPT-PAGE-TOTAL     PIC +ZZZ,ZZZ,ZZZ.ZZ.

01  REPORT-ACCOUNT-TOTALS.
    05  FILLER              PIC X(13) VALUE 'Account Total'.
    05  FILLER              PIC X(84) VALUE ALL '.'.
    05  REPT-ACCOUNT-TOTAL  PIC +ZZZ,ZZZ,ZZZ.ZZ.

01  REPORT-GRAND-TOTALS.
    05  FILLER              PIC X(11) VALUE 'Grand Total'.
    05  FILLER              PIC X(86) VALUE ALL '.'.
    05  REPT-GRAND-TOTAL    PIC +ZZZ,ZZZ,ZZZ.ZZ.
```

**Expected TypeScript output:**
```typescript
export interface ReportNameHeader {
  reptShortName: string;    // default: 'DALYREPT'
  reptLongName: string;     // default: 'Daily Transaction Report'
  reptDateHeader: string;   // default: 'Date Range: '
  reptStartDate: string;
  reptEndDate: string;
}

export interface TransactionDetailReport {
  tranReportTransId: string;
  tranReportAccountId: string;
  tranReportTypeCd: string;
  tranReportTypeDesc: string;
  tranReportCatCd: number;
  tranReportCatDesc: string;
  tranReportSource: string;
  tranReportAmt: number;
}

export interface ReportTotals {
  pageTotal: number;
  accountTotal: number;
  grandTotal: number;
}
```

---

#### 4.2.8 `COSTM01.CPY` — Transaction Altered Layout for Reporting

**Purpose:** Rearranged transaction record with card number as part of the primary key (for VSAM KSDS keyed by card+tranId).
**Target file:** `src/types/trnx-record.types.ts`

**COBOL Structure:**
```cobol
01  TRNX-RECORD.
    05  TRNX-KEY.
        10  TRNX-CARD-NUM                       PIC X(16).
        10  TRNX-ID                             PIC X(16).
    05  TRNX-REST.
        10  TRNX-TYPE-CD                        PIC X(02).
        10  TRNX-CAT-CD                         PIC 9(04).
        10  TRNX-SOURCE                         PIC X(10).
        10  TRNX-DESC                           PIC X(100).
        10  TRNX-AMT                            PIC S9(09)V99.
        10  TRNX-MERCHANT-ID                    PIC 9(09).
        10  TRNX-MERCHANT-NAME                  PIC X(50).
        10  TRNX-MERCHANT-CITY                  PIC X(50).
        10  TRNX-MERCHANT-ZIP                   PIC X(10).
        10  TRNX-ORIG-TS                        PIC X(26).
        10  TRNX-PROC-TS                        PIC X(26).
        10  FILLER                              PIC X(20).
```

**Expected TypeScript output:**
```typescript
export interface TrnxKey {
  trnxCardNum: string;  // PIC X(16)
  trnxId: string;       // PIC X(16)
}

export interface TrnxRecord {
  trnxKey: TrnxKey;
  trnxTypeCd: string;
  trnxCatCd: number;
  trnxSource: string;
  trnxDesc: string;
  trnxAmt: number;
  trnxMerchantId: number;
  trnxMerchantName: string;
  trnxMerchantCity: string;
  trnxMerchantZip: string;
  trnxOrigTs: string;
  trnxProcTs: string;
}
```

---

### 4.3 — Application Infrastructure Copybooks

---

#### 4.3.1 `COCOM01Y.cpy` — COMMAREA (Inter-Program Communication)

**Purpose:** The central communication area passed between all CICS programs. Contains session context, user info, navigation state.
**Target file:** `src/types/commarea.types.ts`

**COBOL Structure:**
```cobol
01 CARDDEMO-COMMAREA.
   05 CDEMO-GENERAL-INFO.
      10 CDEMO-FROM-TRANID             PIC X(04).
      10 CDEMO-FROM-PROGRAM            PIC X(08).
      10 CDEMO-TO-TRANID               PIC X(04).
      10 CDEMO-TO-PROGRAM              PIC X(08).
      10 CDEMO-USER-ID                 PIC X(08).
      10 CDEMO-USER-TYPE               PIC X(01).
         88 CDEMO-USRTYP-ADMIN         VALUE 'A'.
         88 CDEMO-USRTYP-USER          VALUE 'U'.
      10 CDEMO-PGM-CONTEXT             PIC 9(01).
         88 CDEMO-PGM-ENTER            VALUE 0.
         88 CDEMO-PGM-REENTER          VALUE 1.
   05 CDEMO-CUSTOMER-INFO.
      10 CDEMO-CUST-ID                 PIC 9(09).
      10 CDEMO-CUST-FNAME              PIC X(25).
      10 CDEMO-CUST-MNAME              PIC X(25).
      10 CDEMO-CUST-LNAME              PIC X(25).
   05 CDEMO-ACCOUNT-INFO.
      10 CDEMO-ACCT-ID                 PIC 9(11).
      10 CDEMO-ACCT-STATUS             PIC X(01).
   05 CDEMO-CARD-INFO.
      10 CDEMO-CARD-NUM                PIC 9(16).
   05 CDEMO-MORE-INFO.
      10  CDEMO-LAST-MAP               PIC X(7).
      10  CDEMO-LAST-MAPSET            PIC X(7).
```

**Expected TypeScript output:**
```typescript
export enum UserType {
  Admin = 'A',
  User = 'U',
}

export enum ProgramContext {
  Enter = 0,
  Reenter = 1,
}

export interface CardDemoCommarea {
  generalInfo: {
    fromTranId: string;
    fromProgram: string;
    toTranId: string;
    toProgram: string;
    userId: string;
    userType: UserType;
    pgmContext: ProgramContext;
  };
  customerInfo: {
    custId: number;
    custFname: string;
    custMname: string;
    custLname: string;
  };
  accountInfo: {
    acctId: number;
    acctStatus: string;
  };
  cardInfo: {
    cardNum: number;
  };
  moreInfo: {
    lastMap: string;
    lastMapset: string;
  };
}
```

---

#### 4.3.2 `CSMSG01Y.cpy` — Common Messages

**Purpose:** Application-wide user-facing messages.
**Target file:** `src/types/common-messages.types.ts`

**COBOL Structure:**
```cobol
01 CCDA-COMMON-MESSAGES.
   05 CCDA-MSG-THANK-YOU         PIC X(50) VALUE
        'Thank you for using CardDemo application...      '.
   05 CCDA-MSG-INVALID-KEY       PIC X(50) VALUE
        'Invalid key pressed. Please see below...         '.
```

**Expected TypeScript output:**
```typescript
export const COMMON_MESSAGES = {
  thankYou: 'Thank you for using CardDemo application...',
  invalidKey: 'Invalid key pressed. Please see below...',
} as const;

export type CommonMessageKey = keyof typeof COMMON_MESSAGES;
```

---

#### 4.3.3 `CSMSG02Y.cpy` — Abend Data

**Purpose:** Work areas for the abend (abnormal end) error-handling routine.
**Target file:** `src/types/abend-data.types.ts`

**COBOL Structure:**
```cobol
01  ABEND-DATA.
  05  ABEND-CODE                            PIC X(4) VALUE SPACES.
  05  ABEND-CULPRIT                         PIC X(8) VALUE SPACES.
  05  ABEND-REASON                          PIC X(50) VALUE SPACES.
  05  ABEND-MSG                             PIC X(72) VALUE SPACES.
```

**Expected TypeScript output:**
```typescript
export interface AbendData {
  abendCode: string;     // max 4 chars
  abendCulprit: string;  // max 8 chars — the program that caused the abend
  abendReason: string;   // max 50 chars
  abendMsg: string;      // max 72 chars
}

export function createAbendData(): AbendData {
  return { abendCode: '', abendCulprit: '', abendReason: '', abendMsg: '' };
}
```

---

#### 4.3.4 `COTTL01Y.cpy` — Screen Title Constants

**Purpose:** Title strings displayed on all application screens.
**Target file:** `src/types/screen-title.types.ts`

**COBOL Structure:**
```cobol
01 CCDA-SCREEN-TITLE.
   05 CCDA-TITLE01    PIC X(40) VALUE
      '      AWS Mainframe Modernization       '.
   05 CCDA-TITLE02    PIC X(40) VALUE
      '              CardDemo                  '.
   05 CCDA-THANK-YOU  PIC X(40) VALUE
      'Thank you for using CCDA application... '.
```

**Expected TypeScript output:**
```typescript
export const SCREEN_TITLES = {
  title01: 'AWS Mainframe Modernization',
  title02: 'CardDemo',
  thankYou: 'Thank you for using CCDA application...',
} as const;
```

---

#### 4.3.5 `CSDAT01Y.cpy` — Date/Time Working Storage

**Purpose:** Defines working-storage fields for date/time formatting and timestamp generation.
**Target file:** `src/types/date-time.types.ts`

**COBOL Structure:**
```cobol
01 WS-DATE-TIME.
   05 WS-CURDATE-DATA.
     10  WS-CURDATE.
       15  WS-CURDATE-YEAR         PIC 9(04).
       15  WS-CURDATE-MONTH        PIC 9(02).
       15  WS-CURDATE-DAY          PIC 9(02).
     10 WS-CURDATE-N REDEFINES WS-CURDATE PIC 9(08).
     10  WS-CURTIME.
       15  WS-CURTIME-HOURS        PIC 9(02).
       15  WS-CURTIME-MINUTE       PIC 9(02).
       15  WS-CURTIME-SECOND       PIC 9(02).
       15  WS-CURTIME-MILSEC       PIC 9(02).
     10 WS-CURTIME-N REDEFINES WS-CURTIME PIC 9(08).
   05 WS-CURDATE-MM-DD-YY.
     10  WS-CURDATE-MM             PIC 9(02).
     10  FILLER                    PIC X(01) VALUE '/'.
     10  WS-CURDATE-DD             PIC 9(02).
     10  FILLER                    PIC X(01) VALUE '/'.
     10  WS-CURDATE-YY             PIC 9(02).
   05 WS-CURTIME-HH-MM-SS.
     10  WS-CURTIME-HH             PIC 9(02).
     10  FILLER                    PIC X(01) VALUE ':'.
     10  WS-CURTIME-MM             PIC 9(02).
     10  FILLER                    PIC X(01) VALUE ':'.
     10  WS-CURTIME-SS             PIC 9(02).
   05 WS-TIMESTAMP.
     10  WS-TIMESTAMP-DT-YYYY      PIC 9(04).
     10  FILLER                    PIC X(01) VALUE '-'.
     10  WS-TIMESTAMP-DT-MM        PIC 9(02).
     10  FILLER                    PIC X(01) VALUE '-'.
     10  WS-TIMESTAMP-DT-DD        PIC 9(02).
     10  FILLER                    PIC X(01) VALUE ' '.
     10  WS-TIMESTAMP-TM-HH        PIC 9(02).
     10  FILLER                    PIC X(01) VALUE ':'.
     10  WS-TIMESTAMP-TM-MM        PIC 9(02).
     10  FILLER                    PIC X(01) VALUE ':'.
     10  WS-TIMESTAMP-TM-SS        PIC 9(02).
     10  FILLER                    PIC X(01) VALUE '.'.
     10  WS-TIMESTAMP-TM-MS6       PIC 9(06).
```

**Expected TypeScript output:**
```typescript
export interface WsDateTime {
  curDate: {
    year: number;
    month: number;
    day: number;
  };
  curTime: {
    hours: number;
    minute: number;
    second: number;
    milsec: number;
  };
  curDateFormatted: string;  // MM/DD/YY
  curTimeFormatted: string;  // HH:MM:SS
  timestamp: string;         // YYYY-MM-DD HH:MM:SS.ffffff
}

export function getCurrentDateTime(): WsDateTime {
  const now = new Date();
  return {
    curDate: { year: now.getFullYear(), month: now.getMonth() + 1, day: now.getDate() },
    curTime: { hours: now.getHours(), minute: now.getMinutes(), second: now.getSeconds(), milsec: now.getMilliseconds() },
    curDateFormatted: `${String(now.getMonth()+1).padStart(2,'0')}/${String(now.getDate()).padStart(2,'0')}/${String(now.getFullYear()).slice(-2)}`,
    curTimeFormatted: `${String(now.getHours()).padStart(2,'0')}:${String(now.getMinutes()).padStart(2,'0')}:${String(now.getSeconds()).padStart(2,'0')}`,
    timestamp: now.toISOString().replace('T', ' ').replace('Z', ''),
  };
}
```

---

#### 4.3.6 `CODATECN.cpy` — Date Conversion Record

**Purpose:** Input/output structure for date format conversions (YYYYMMDD ↔ YYYY-MM-DD).
**Target file:** `src/types/date-conversion.types.ts`

**COBOL Structure:**
```cobol
01  CODATECN-REC.
    05  CODATECN-IN-REC.
        10  CODATECN-TYPE             PIC X.
            88  YYYYMMDD-IN           VALUE "1".
            88  YYYY-MM-DD-IN         VALUE "2".
        10  CODATECN-INP-DATE         PIC X(20).
        10  CODATECN-1INP REDEFINES CODATECN-INP-DATE.
            15  CODATECN-1YYYY    PIC XXXX.
            15  CODATECN-1MM      PIC XX.
            15  CODATECN-1DD      PIC XX.
            15  CODATECN-1FIL     PIC X(12).
        10  CODATECN-2INP REDEFINES CODATECN-INP-DATE.
            15  CODATECN-1O-YYYY  PIC XXXX.
            15  CODATECN-1I-S1    PIC X.
            15  CODATECN-1MM      PIC XX.
            15  CODATECN-1I-S2    PIC X.
            15  CODATECN-2YY      PIC XX.
            15  CODATECN-2FIL     PIC X(10).
    05  CODATECN-OUT-REC.
        10  CODATECN-OUTTYPE          PIC X.
            88  YYYY-MM-DD-OP         VALUE "1".
            88  YYYYMMDD-OP           VALUE "2".
        10  CODATECN-0UT-DATE         PIC X(20).
        10  CODATECN-1OUT REDEFINES CODATECN-0UT-DATE.
            15  CODATECN-1O-YYYY  PIC XXXX.
            15  CODATECN-1O-S1    PIC X.
            15  CODATECN-1O-MM    PIC XX.
            15  CODATECN-1O-S2    PIC X.
            15  CODATECN-1O-DD    PIC XX.
            15  CODATECN-1OFIl    PIC X(10).
        10  CODATECN-2OUT REDEFINES CODATECN-0UT-DATE.
            15  CODATECN-2O-YYYY  PIC XXXX.
            15  CODATECN-2O-MM    PIC XX.
            15  CODATECN-2O-DD    PIC XX.
            15  CODATECN-2OFIl    PIC X(12).
    05  CODATECN-ERROR-MSG        PIC X(38).
```

**Expected TypeScript output:**
```typescript
export enum DateInputType {
  YYYYMMDD = '1',
  YYYY_MM_DD = '2',
}

export enum DateOutputType {
  YYYY_MM_DD = '1',
  YYYYMMDD = '2',
}

export interface DateConversionRequest {
  inputType: DateInputType;
  inputDate: string;
}

export interface DateConversionResponse {
  outputType: DateOutputType;
  outputDate: string;
  errorMsg: string;
}

export function convertDate(req: DateConversionRequest): DateConversionResponse {
  // Implementation: parse input based on inputType, format to outputType
  // This replaces the COBOL REDEFINES-based conversion logic
  const { inputType, inputDate } = req;
  let yyyy: string, mm: string, dd: string;

  if (inputType === DateInputType.YYYYMMDD) {
    yyyy = inputDate.substring(0, 4);
    mm = inputDate.substring(4, 6);
    dd = inputDate.substring(6, 8);
  } else {
    yyyy = inputDate.substring(0, 4);
    mm = inputDate.substring(5, 7);
    dd = inputDate.substring(8, 10);
  }

  return {
    outputType: DateOutputType.YYYY_MM_DD,
    outputDate: `${yyyy}-${mm}-${dd}`,
    errorMsg: '',
  };
}
```

---

### 4.4 — Menu/Navigation Copybooks

---

#### 4.4.1 `COMEN02Y.cpy` — Main Menu Options

**Purpose:** Defines all main menu options available to regular users, including option numbers, names, program names, and user-type restrictions.
**Target file:** `src/types/main-menu-options.types.ts`

**COBOL Structure (simplified — 11 menu options with REDEFINES):**
```cobol
01 CARDDEMO-MAIN-MENU-OPTIONS.
   05 CDEMO-MENU-OPT-COUNT           PIC 9(02) VALUE 11.
   05 CDEMO-MENU-OPTIONS-DATA.
     10 FILLER PIC 9(02) VALUE 1.
     10 FILLER PIC X(35) VALUE 'Account View'.
     10 FILLER PIC X(08) VALUE 'COACTVWC'.
     10 FILLER PIC X(01) VALUE 'U'.
     ... (10 more entries)
   05 CDEMO-MENU-OPTIONS REDEFINES CDEMO-MENU-OPTIONS-DATA.
     10 CDEMO-MENU-OPT OCCURS 12 TIMES.
       15 CDEMO-MENU-OPT-NUM           PIC 9(02).
       15 CDEMO-MENU-OPT-NAME          PIC X(35).
       15 CDEMO-MENU-OPT-PGMNAME       PIC X(08).
       15 CDEMO-MENU-OPT-USRTYPE       PIC X(01).
```

**Full menu entries:**
| # | Name | Program | Type |
|---|------|---------|------|
| 1 | Account View | COACTVWC | U |
| 2 | Account Update | COACTUPC | U |
| 3 | Credit Card List | COCRDLIC | U |
| 4 | Credit Card View | COCRDSLC | U |
| 5 | Credit Card Update | COCRDUPC | U |
| 6 | Transaction List | COTRN00C | U |
| 7 | Transaction View | COTRN01C | U |
| 8 | Transaction Add | COTRN02C | U |
| 9 | Transaction Reports | CORPT00C | U |
| 10 | Bill Payment | COBIL00C | U |
| 11 | Pending Authorization View | COPAUS0C | U |

**Expected TypeScript output:**
```typescript
export interface MenuOption {
  optNum: number;
  optName: string;
  optPgmName: string;
  optUsrType: string;
}

export const MAIN_MENU_OPTIONS: MenuOption[] = [
  { optNum: 1, optName: 'Account View', optPgmName: 'COACTVWC', optUsrType: 'U' },
  { optNum: 2, optName: 'Account Update', optPgmName: 'COACTUPC', optUsrType: 'U' },
  { optNum: 3, optName: 'Credit Card List', optPgmName: 'COCRDLIC', optUsrType: 'U' },
  { optNum: 4, optName: 'Credit Card View', optPgmName: 'COCRDSLC', optUsrType: 'U' },
  { optNum: 5, optName: 'Credit Card Update', optPgmName: 'COCRDUPC', optUsrType: 'U' },
  { optNum: 6, optName: 'Transaction List', optPgmName: 'COTRN00C', optUsrType: 'U' },
  { optNum: 7, optName: 'Transaction View', optPgmName: 'COTRN01C', optUsrType: 'U' },
  { optNum: 8, optName: 'Transaction Add', optPgmName: 'COTRN02C', optUsrType: 'U' },
  { optNum: 9, optName: 'Transaction Reports', optPgmName: 'CORPT00C', optUsrType: 'U' },
  { optNum: 10, optName: 'Bill Payment', optPgmName: 'COBIL00C', optUsrType: 'U' },
  { optNum: 11, optName: 'Pending Authorization View', optPgmName: 'COPAUS0C', optUsrType: 'U' },
];
```

---

#### 4.4.2 `COADM02Y.cpy` — Admin Menu Options

**Purpose:** Defines admin-only menu options (user CRUD, DB2 transaction type management).
**Target file:** `src/types/admin-menu-options.types.ts`

**Full menu entries:**
| # | Name | Program |
|---|------|---------|
| 1 | User List (Security) | COUSR00C |
| 2 | User Add (Security) | COUSR01C |
| 3 | User Update (Security) | COUSR02C |
| 4 | User Delete (Security) | COUSR03C |
| 5 | Transaction Type List/Update (Db2) | COTRTLIC |
| 6 | Transaction Type Maintenance (Db2) | COTRTUPC |

**COBOL Structure:**
```cobol
01 CARDDEMO-ADMIN-MENU-OPTIONS.
   05 CDEMO-ADMIN-OPT-COUNT           PIC 9(02) VALUE 6.
   05 CDEMO-ADMIN-OPTIONS-DATA.
     ... (6 entries with PIC 9(02), PIC X(35), PIC X(08))
   05 CDEMO-ADMIN-OPTIONS REDEFINES CDEMO-ADMIN-OPTIONS-DATA.
     10 CDEMO-ADMIN-OPT OCCURS 9 TIMES.
       15 CDEMO-ADMIN-OPT-NUM           PIC 9(02).
       15 CDEMO-ADMIN-OPT-NAME          PIC X(35).
       15 CDEMO-ADMIN-OPT-PGMNAME       PIC X(08).
```

**Expected TypeScript output:**
```typescript
export interface AdminMenuOption {
  optNum: number;
  optName: string;
  optPgmName: string;
}

export const ADMIN_MENU_OPTIONS: AdminMenuOption[] = [
  { optNum: 1, optName: 'User List (Security)', optPgmName: 'COUSR00C' },
  { optNum: 2, optName: 'User Add (Security)', optPgmName: 'COUSR01C' },
  { optNum: 3, optName: 'User Update (Security)', optPgmName: 'COUSR02C' },
  { optNum: 4, optName: 'User Delete (Security)', optPgmName: 'COUSR03C' },
  { optNum: 5, optName: 'Transaction Type List/Update (Db2)', optPgmName: 'COTRTLIC' },
  { optNum: 6, optName: 'Transaction Type Maintenance (Db2)', optPgmName: 'COTRTUPC' },
];
```

---

### 4.5 — UI/Screen Working-Storage Copybooks

---

#### 4.5.1 `CVCRD01Y.cpy` — Credit Card Work Areas

**Purpose:** Working-storage for credit card screens. Contains AID key mappings (PFKey detection), navigation fields, error/return messages, and account/card/customer ID work areas.
**Target file:** `src/types/cc-work-areas.types.ts`

**COBOL Structure:**
```cobol
01  CC-WORK-AREAS.
  05 CC-WORK-AREA.
     10 CCARD-AID                         PIC X(5).
        88  CCARD-AID-ENTER                VALUE 'ENTER'.
        88  CCARD-AID-CLEAR                VALUE 'CLEAR'.
        88  CCARD-AID-PA1                  VALUE 'PA1  '.
        88  CCARD-AID-PA2                  VALUE 'PA2  '.
        88  CCARD-AID-PFK01                VALUE 'PFK01'.
        ... (through PFK12)
     10  CCARD-NEXT-PROG                  PIC X(8).
     10  CCARD-NEXT-MAPSET                PIC X(7).
     10  CCARD-NEXT-MAP                   PIC X(7).
     10  CCARD-ERROR-MSG                  PIC X(75).
     10  CCARD-RETURN-MSG                 PIC X(75).
       88  CCARD-RETURN-MSG-OFF           VALUE LOW-VALUES.
     10 CC-ACCT-ID                        PIC X(11) VALUE SPACES.
     10 CC-ACCT-ID-N REDEFINES CC-ACCT-ID PIC 9(11).
     10 CC-CARD-NUM                       PIC X(16) VALUE SPACES.
     10 CC-CARD-NUM-N REDEFINES CC-CARD-NUM PIC 9(16).
     10 CC-CUST-ID                        PIC X(09) VALUE SPACES.
     10 CC-CUST-ID-N REDEFINES CC-CUST-ID PIC 9(9).
```

**Expected TypeScript output:**
```typescript
export enum AidKey {
  Enter = 'ENTER',
  Clear = 'CLEAR',
  PA1 = 'PA1',
  PA2 = 'PA2',
  PFK01 = 'PFK01', PFK02 = 'PFK02', PFK03 = 'PFK03',
  PFK04 = 'PFK04', PFK05 = 'PFK05', PFK06 = 'PFK06',
  PFK07 = 'PFK07', PFK08 = 'PFK08', PFK09 = 'PFK09',
  PFK10 = 'PFK10', PFK11 = 'PFK11', PFK12 = 'PFK12',
}

export interface CcWorkAreas {
  ccardAid: AidKey;
  ccardNextProg: string;
  ccardNextMapset: string;
  ccardNextMap: string;
  ccardErrorMsg: string;
  ccardReturnMsg: string;
  ccAcctId: string;
  ccCardNum: string;
  ccCustId: string;
}
```

---

### 4.6 — Date Validation Copybooks (Procedure Division + Working Storage)

---

#### 4.6.1 `CSUTLDWY.cpy` — Date Validation Working Storage

**Purpose:** Working-storage fields for the date validation paragraphs in CSUTLDPY. Contains date fields, century/month/day flags, and validation result structure.
**Target file:** `src/types/date-validation.types.ts`

**COBOL Structure (key data items):**
```cobol
10 WS-EDIT-DATE-CCYYMMDD.
   20 WS-EDIT-DATE-CCYY.
      25 WS-EDIT-DATE-CC                PIC X(2).
      25 WS-EDIT-DATE-CC-N REDEFINES WS-EDIT-DATE-CC PIC 9(2).
         88 THIS-CENTURY                VALUE 20.
         88 LAST-CENTURY                VALUE 19.
      25 WS-EDIT-DATE-YY                PIC X(2).
      25 WS-EDIT-DATE-YY-N REDEFINES WS-EDIT-DATE-YY PIC 9(2).
   20 WS-EDIT-DATE-CCYY-N REDEFINES WS-EDIT-DATE-CCYY PIC 9(4).
   20 WS-EDIT-DATE-MM                   PIC X(2).
   20 WS-EDIT-DATE-MM-N REDEFINES WS-EDIT-DATE-MM PIC 9(2).
      88 WS-VALID-MONTH                 VALUES 1 THROUGH 12.
      88 WS-31-DAY-MONTH                VALUES 1, 3, 5, 7, 8, 10, 12.
      88 WS-FEBRUARY                    VALUE 2.
   20 WS-EDIT-DATE-DD                   PIC X(2).
   20 WS-EDIT-DATE-DD-N REDEFINES WS-EDIT-DATE-DD PIC 9(2).
      88 WS-VALID-DAY                   VALUES 1 THROUGH 31.
10 WS-EDIT-DATE-FLGS.
      88 WS-EDIT-DATE-IS-VALID          VALUE LOW-VALUES.
      88 WS-EDIT-DATE-IS-INVALID        VALUE '000'.
   20 WS-EDIT-YEAR-FLG                 PIC X(01).
   20 WS-EDIT-MONTH                    PIC X(01).
   20 WS-EDIT-DAY                      PIC X(01).
```

**Expected TypeScript output:**
```typescript
export interface DateValidationState {
  editDate: {
    century: number;
    year: number;
    month: number;
    day: number;
    fullYear: number;    // CCYY as number
    fullDate: number;    // CCYYMMDD as number
  };
  currentDate: {
    yyyymmdd: number;
  };
  flags: {
    isValid: boolean;
    yearFlag: 'valid' | 'invalid' | 'blank';
    monthFlag: 'valid' | 'invalid' | 'blank';
    dayFlag: 'valid' | 'invalid' | 'blank';
  };
  validationResult: {
    severity: number;
    msgNo: number;
    result: string;
    date: string;
    dateFmt: string;
  };
}
```

---

#### 4.6.2 `CSUTLDPY.cpy` — Date Validation Procedure Division

**Purpose:** Reusable COBOL paragraphs for validating CCYYMMDD dates (year, month, day, leap year, date-of-birth, future date checks). This is a **procedure-division copybook** (contains executable code, not data definitions).
**Target file:** `src/utils/date-validation.ts`

**Migration approach:** Convert each COBOL paragraph into a TypeScript function:

| COBOL Paragraph | TypeScript Function |
|---|---|
| `EDIT-DATE-CCYYMMDD` | `validateDate(dateStr: string): ValidationResult` |
| `EDIT-YEAR-CCYY` | `validateYear(year: number): ValidationResult` |
| `EDIT-MONTH` | `validateMonth(month: number): ValidationResult` |
| `EDIT-DAY` | `validateDay(year: number, month: number, day: number): ValidationResult` |
| `EDIT-DATE-OF-BIRTH` | `validateDateOfBirth(dateStr: string): ValidationResult` |

**Expected TypeScript output:**
```typescript
export interface ValidationResult {
  isValid: boolean;
  errorMessage: string;
}

export function validateDate(dateStr: string): ValidationResult {
  // Parse CCYYMMDD, validate year (19xx/20xx), month (1-12), day (1-31 with month rules)
  // Handle leap year for February
}

export function validateYear(year: number): ValidationResult {
  const century = Math.floor(year / 100);
  if (century !== 19 && century !== 20) {
    return { isValid: false, errorMessage: 'Century is not valid.' };
  }
  return { isValid: true, errorMessage: '' };
}

export function validateMonth(month: number): ValidationResult {
  if (month < 1 || month > 12) {
    return { isValid: false, errorMessage: 'Month must be between 01 and 12.' };
  }
  return { isValid: true, errorMessage: '' };
}

export function validateDay(year: number, month: number, day: number): ValidationResult {
  // Check day is valid for the given month, accounting for leap years
}
```

---

### 4.7 — Utility/Infrastructure Copybooks

---

#### 4.7.1 `CSSTRPFY.cpy` — Store PFKey Procedure

**Purpose:** Procedure-division copybook that maps CICS EIBAID values to the CCARD-AID working-storage field. Used in every online program to detect which key the user pressed.
**Target file:** `src/utils/pfkey-mapper.ts`

**Migration approach:** Convert the EVALUATE/WHEN block to a TypeScript function:

```typescript
import { AidKey } from '../types/cc-work-areas.types';

export function mapEibaidToAidKey(eibaid: string): AidKey {
  const mapping: Record<string, AidKey> = {
    DFHENTER: AidKey.Enter,
    DFHCLEAR: AidKey.Clear,
    DFHPA1: AidKey.PA1,
    DFHPA2: AidKey.PA2,
    DFHPF1: AidKey.PFK01,
    DFHPF2: AidKey.PFK02,
    // ... through DFHPF12
  };
  return mapping[eibaid] ?? AidKey.Enter;
}
```

---

#### 4.7.2 `CSSETATY.cpy` — Set Attribute Macro

**Purpose:** A parameterized copybook (used with `COPY ... REPLACING`) to set screen field attributes (color to red if error, asterisk if blank). Used by online programs for BMS map field validation.
**Target file:** `src/utils/field-attribute-setter.ts`

**COBOL Structure (with replacement tokens):**
```cobol
IF (FLG-(TESTVAR1)-NOT-OK
OR  FLG-(TESTVAR1)-BLANK)
AND CDEMO-PGM-REENTER
    MOVE DFHRED TO (SCRNVAR2)C OF (MAPNAME3)O
    IF FLG-(TESTVAR1)-BLANK
        MOVE '*' TO (SCRNVAR2)O OF (MAPNAME3)O
    END-IF
END-IF
```

**Expected TypeScript output:**
```typescript
export interface FieldValidationState {
  isValid: boolean;
  isBlank: boolean;
}

export interface FieldAttribute {
  color: 'red' | 'default';
  displayValue: string | null;  // '*' for blank fields
}

export function setFieldAttribute(
  fieldState: FieldValidationState,
  isReenter: boolean
): FieldAttribute {
  if ((! fieldState.isValid || fieldState.isBlank) && isReenter) {
    return {
      color: 'red',
      displayValue: fieldState.isBlank ? '*' : null,
    };
  }
  return { color: 'default', displayValue: null };
}
```

---

#### 4.7.3 `CSLKPCDY.cpy` — Lookup Code Repository

**Purpose:** Massive lookup table containing:
1. North American phone area codes (88-level validation)
2. US state codes
3. US state + first 2 digits of ZIP code

**Target file:** `src/utils/lookup-codes.ts`

**Migration approach:** This is a data-heavy copybook with hundreds of 88-level values. Convert to TypeScript constant arrays/Sets:

```typescript
export const VALID_PHONE_AREA_CODES = new Set([
  '201','202','203','204','205','206','207','208','209','210',
  '212','213','214','215','216','217','218','219','220','223',
  // ... (all ~350 area codes)
]);

export const VALID_US_STATE_CODES = new Set([
  'AL','AK','AZ','AR','CA','CO','CT','DE','FL','GA',
  // ... (all 50 states + territories)
]);

export function isValidPhoneAreaCode(code: string): boolean {
  return VALID_PHONE_AREA_CODES.has(code);
}

export function isValidStatCode(code: string): boolean {
  return VALID_US_STATE_CODES.has(code);
}
```

---

### 4.8 — Export/Migration Copybooks

---

#### 4.8.1 `CVEXPORT.cpy` — Multi-Record Export Layout

**Purpose:** Defines a polymorphic export record (500 bytes) with REDEFINES for Customer, Account, Transaction, Card XREF, and Card data. Used by the branch migration batch programs (CBEXPORT/CBIMPORT). Includes COMP/COMP-3 fields for storage optimization.
**Target file:** `src/types/export-record.types.ts`

**COBOL Structure (header + 5 variant records via REDEFINES):**
```cobol
01  EXPORT-RECORD.
    05  EXPORT-REC-TYPE                         PIC X(1).
    05  EXPORT-TIMESTAMP                        PIC X(26).
    05  EXPORT-TIMESTAMP-R REDEFINES EXPORT-TIMESTAMP.
        10  EXPORT-DATE                         PIC X(10).
        10  EXPORT-DATE-TIME-SEP                PIC X(1).
        10  EXPORT-TIME                         PIC X(15).
    05  EXPORT-SEQUENCE-NUM                     PIC 9(9) COMP.
    05  EXPORT-BRANCH-ID                        PIC X(4).
    05  EXPORT-REGION-CODE                      PIC X(5).
    05  EXPORT-RECORD-DATA                      PIC X(460).
    -- REDEFINES for Customer, Account, Transaction, Card XREF, Card
```

**Expected TypeScript output:**
```typescript
export enum ExportRecordType {
  Customer = 'C',
  Account = 'A',
  Transaction = 'T',
  CardXref = 'X',
  Card = 'D',
}

export interface ExportRecordHeader {
  recType: ExportRecordType;
  timestamp: string;
  sequenceNum: number;
  branchId: string;
  regionCode: string;
}

// Discriminated union for the polymorphic record data
export type ExportRecord =
  | (ExportRecordHeader & { recType: ExportRecordType.Customer; data: CustomerExportData })
  | (ExportRecordHeader & { recType: ExportRecordType.Account; data: AccountExportData })
  | (ExportRecordHeader & { recType: ExportRecordType.Transaction; data: TransactionExportData })
  | (ExportRecordHeader & { recType: ExportRecordType.CardXref; data: CardXrefExportData })
  | (ExportRecordHeader & { recType: ExportRecordType.Card; data: CardExportData });

export interface CustomerExportData {
  custId: number;
  custFirstName: string;
  custMiddleName: string;
  custLastName: string;
  custAddrLines: string[];  // OCCURS 3 TIMES
  custAddrStateCd: string;
  custAddrCountryCd: string;
  custAddrZip: string;
  custPhoneNums: string[];  // OCCURS 2 TIMES
  custSsn: number;
  custGovtIssuedId: string;
  custDobYyyyMmDd: string;
  custEftAccountId: string;
  custPriCardHolderInd: string;
  custFicoCreditScore: number;
}

// ... similar interfaces for Account, Transaction, CardXref, Card
```

---

### 4.9 — Unused/Deprecated Copybooks

---

#### 4.9.1 `UNUSED1Y.cpy` — Unused Data Structure

**Purpose:** A deprecated/placeholder copybook. Not referenced by any program.
**Target file:** *(skip — do not migrate)*

**COBOL Structure:**
```cobol
01 UNUSED-DATA.
   05 UNUSED-ID                 PIC X(08).
   05 UNUSED-FNAME              PIC X(20).
   05 UNUSED-LNAME              PIC X(20).
   05 UNUSED-PWD                PIC X(08).
   05 UNUSED-TYPE               PIC X(01).
   05 UNUSED-FILLER             PIC X(23).
```

---

## 5. Cross-Reference: Copybook Usage by Program

| Copybook | Used By (Programs) | Category |
|----------|---------------------|----------|
| COCOM01Y | All online programs | Infrastructure |
| CVCRD01Y | COACTUPC, COACTVWC, COCRDLIC, COCRDSLC, COCRDUPC, COTRN00C, COTRN01C, COTRN02C, COBIL00C, COMEN01C, COSGN00C, COUSR00C-03C, COADM01C, CORPT00C | UI Work Areas |
| CSDAT01Y | All online programs | Date/Time |
| CSMSG01Y | All online programs | Messages |
| CSMSG02Y | All online programs | Error Handling |
| COTTL01Y | All online programs | Screen Title |
| CSSTRPFY | All online programs | PFKey Mapping |
| CSSETATY | COACTUPC, COCRDUPC, COUSR01C, COUSR02C (via REPLACING) | Field Attributes |
| CSLKPCDY | COACTUPC, COCRDUPC | Lookup Validation |
| CSUTLDWY + CSUTLDPY | COACTUPC, COCRDUPC, COTRN02C | Date Validation |
| CODATECN | CBSTM03A, CBSTM03B | Date Conversion |
| CSUSR01Y | COSGN00C, COUSR00C-03C | User Security |
| CVACT01Y | COACTUPC, COACTVWC, CBACT01C-04C | Account Data |
| CVACT02Y | COCRDLIC, COCRDSLC, COCRDUPC, CBACT02C | Card Data |
| CVACT03Y | COCRDLIC, COCRDSLC, COCRDUPC, CBACT03C | Card XREF |
| CVCUS01Y / CUSTREC | COACTUPC, COACTVWC, CBCUS01C | Customer Data |
| CVTRA01Y | CBTRN01C, CBTRN03C | Tran Cat Balance |
| CVTRA02Y | CBTRN01C | Disclosure Groups |
| CVTRA03Y | CBTRN01C, COTRN01C, COTRN02C | Tran Types |
| CVTRA04Y | CBTRN01C, COTRN01C, COTRN02C | Tran Categories |
| CVTRA05Y | COTRN00C, COTRN01C, COTRN02C, CBTRN01C-03C | Transactions |
| CVTRA06Y | CBTRN01C, CBTRN02C, CBTRN03C | Daily Trans |
| CVTRA07Y | CBTRN03C | Report Layout |
| COMEN02Y | COMEN01C | Main Menu |
| COADM02Y | COADM01C | Admin Menu |
| COSTM01 | CBSTM03A, CBSTM03B | Trnx Record |
| CVEXPORT | CBEXPORT, CBIMPORT | Export/Import |
| UNUSED1Y | *(none)* | Deprecated |

---

## 6. Migration Task Checklist Per Copybook

For **each** copybook migration, the agent MUST:

1. **Read** this specification document (Section 4 entry for the assigned copybook)
2. **Create** the TypeScript file at the specified `Target file` path
3. **Implement** the interface/type/enum following the type-mapping rules in Section 3
4. **Add JSDoc comments** on each field referencing the original COBOL PIC clause
5. **Create a factory function** (e.g., `createAccountRecord(): AccountRecord`) with sensible defaults
6. **Add Zod validation schema** (e.g., `accountRecordSchema`) to validate runtime data:
   - String max lengths matching the original PIC X(n) sizes
   - Number ranges matching PIC 9(n) limits
   - Enum validation for 88-level fields
7. **Export** all types from a barrel file `src/types/index.ts`
8. **Write unit tests** in `src/types/__tests__/<name>.test.ts`:
   - Test factory function returns valid defaults
   - Test Zod schema accepts valid data
   - Test Zod schema rejects invalid data (too long, wrong type, etc.)
9. **For entity copybooks** (Section 4.1 + 4.2): also create the database entity file at `src/entities/<name>.entity.ts`
10. **Commit** with message: `feat(types): migrate <COPYBOOK_NAME> copybook to TypeScript`

---

## 7. Project Setup (Pre-Migration)

Before any copybook migration can begin, the target project must be initialized:

```bash
mkdir -p src/types/__tests__ src/entities src/utils src/routes src/batch src/ui
npm init -y
npm install typescript zod
npm install -D @types/node jest ts-jest @types/jest
npx tsc --init --strict --target ES2022 --module NodeNext --moduleResolution NodeNext --outDir dist --rootDir src
```

**tsconfig.json** (key settings):
```json
{
  "compilerOptions": {
    "strict": true,
    "target": "ES2022",
    "module": "NodeNext",
    "moduleResolution": "NodeNext",
    "outDir": "dist",
    "rootDir": "src",
    "declaration": true,
    "esModuleInterop": true,
    "skipLibCheck": true
  },
  "include": ["src/**/*"]
}
```

---

## 8. Dependency Graph (Migration Order)

Some copybooks depend on types from others. Recommended migration order:

**Phase 1 — Foundation (no dependencies):**
1. `COTTL01Y` — Screen titles (constants)
2. `CSMSG01Y` — Common messages (constants)
3. `CSMSG02Y` — Abend data
4. `CSDAT01Y` — Date/time types

**Phase 2 — Core Entities (no inter-copybook dependencies):**
5. `CSUSR01Y` — User security
6. `CVACT01Y` — Account record
7. `CVACT02Y` — Card record
8. `CVACT03Y` — Card XREF
9. `CVCUS01Y` — Customer record
10. `CUSTREC` — Customer record (alt layout, depends on #9)
11. `CVTRA03Y` — Transaction types
12. `CVTRA04Y` — Transaction categories

**Phase 3 — Transaction Records:**
13. `CVTRA01Y` — Transaction category balance
14. `CVTRA02Y` — Disclosure groups
15. `CVTRA05Y` — Transaction record
16. `CVTRA06Y` — Daily transaction record (depends on #15)
17. `CVTRA07Y` — Report layout
18. `COSTM01` — Transaction altered layout

**Phase 4 — Application Infrastructure:**
19. `COCOM01Y` — COMMAREA (depends on UserType from #5)
20. `CVCRD01Y` — CC work areas
21. `COMEN02Y` — Main menu options
22. `COADM02Y` — Admin menu options
23. `CODATECN` — Date conversion

**Phase 5 — Utilities (procedure-division copybooks):**
24. `CSUTLDWY` — Date validation working storage
25. `CSUTLDPY` — Date validation procedures (depends on #24)
26. `CSSTRPFY` — PFKey mapper (depends on #20)
27. `CSSETATY` — Field attribute setter
28. `CSLKPCDY` — Lookup codes

**Phase 6 — Complex/Composite:**
29. `CVEXPORT` — Export record (depends on #6, #7, #8, #9, #15)

**Skip:**
30. `UNUSED1Y` — Deprecated, do not migrate

---

## 9. Barrel File Template

After all copybooks are migrated, `src/types/index.ts` should re-export everything:

```typescript
// Entity types
export * from './account-record.types';
export * from './card-record.types';
export * from './card-xref-record.types';
export * from './customer-record.types';
export * from './user-security.types';

// Transaction types
export * from './tran-cat-bal-record.types';
export * from './disclosure-group-record.types';
export * from './tran-type-record.types';
export * from './tran-cat-record.types';
export * from './tran-record.types';
export * from './daily-tran-record.types';
export * from './tran-report.types';
export * from './trnx-record.types';

// Infrastructure types
export * from './commarea.types';
export * from './common-messages.types';
export * from './abend-data.types';
export * from './screen-title.types';
export * from './date-time.types';
export * from './date-conversion.types';
export * from './cc-work-areas.types';
export * from './main-menu-options.types';
export * from './admin-menu-options.types';
export * from './date-validation.types';
export * from './export-record.types';
```

---

## 10. Quality Checklist

Before considering any copybook migration complete:

- [ ] TypeScript compiles with `--strict` and zero errors
- [ ] All COBOL fields mapped (except FILLER)
- [ ] 88-level conditions mapped to enums or union types
- [ ] REDEFINES mapped to discriminated unions where applicable
- [ ] OCCURS mapped to typed arrays
- [ ] Zod schema validates field lengths and numeric ranges
- [ ] Factory function provides valid defaults
- [ ] Unit tests pass
- [ ] JSDoc comments reference original COBOL PIC clauses
- [ ] File is exported from barrel file
