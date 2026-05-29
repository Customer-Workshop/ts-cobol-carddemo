# CardDemo Modernization Notes

This document records the field-level mapping decisions, data-type conversion
rules, and screen-to-component correspondence used when generating the Angular
frontend starter from the original COBOL/CICS CardDemo application.

---

## 1. Data-Type Conversion Rules

| COBOL PIC Clause | TypeScript Type | Notes |
|---|---|---|
| `PIC 9(n)` | `number` | Unsigned integer; safe up to 15 digits in JS |
| `PIC S9(n)V99` | `number` | Signed decimal; divide raw value by 100 after decoding overpunch sign |
| `PIC X(n)` | `string` | Fixed-length character field; trim trailing spaces |
| `PIC X(10)` (date) | `string` | ISO-8601 date kept as string (`YYYY-MM-DD`) for serialisation safety |
| `PIC 9(09)` (SSN) | `string` | Stored as string to preserve leading zeros and avoid numeric misuse |
| `PIC 9(03)` (CVV) | `string` | Stored as string to preserve leading zeros |
| `COMP-3` (packed decimal) | `number` | Not present in the four key copybooks; general rule: unpack BCD nibbles, apply sign nibble, scale by implied decimal |
| `COMP` (binary) | `number` | Not present in the four key copybooks; interpret as big-endian 2/4/8-byte integer |
| `FILLER` | *(omitted)* | Padding bytes; not mapped to the TypeScript interface |

### Zoned-Decimal Overpunch (DISPLAY signed numerics)

The ASCII feed files encode the sign of `PIC S9(n)V99` fields in the zone
nibble of the **last byte** using standard EBCDIC-to-ASCII overpunch:

| Last byte char | Digit | Sign |
|---|---|---|
| `{` | 0 | + |
| `A` | 1 | + |
| `B` | 2 | + |
| `C` | 3 | + |
| `D` | 4 | + |
| `E` | 5 | + |
| `F` | 6 | + |
| `G` | 7 | + |
| `H` | 8 | + |
| `I` | 9 | + |
| `}` | 0 | - |
| `J` | 1 | - |
| `K` | 2 | - |
| `L` | 3 | - |
| `M` | 4 | - |
| `N` | 5 | - |
| `O` | 6 | - |
| `P` | 7 | - |
| `Q` | 8 | - |
| `R` | 9 | - |

---

## 2. Copybook-to-TypeScript Field Mapping

### 2.1 Account Record — `CVACT01Y.cpy` -> `account.model.ts`

| COBOL Field | PIC Clause | Offset | Len | TypeScript Property | TS Type |
|---|---|---|---|---|---|
| `ACCT-ID` | `9(11)` | 0 | 11 | `acctId` | `number` |
| `ACCT-ACTIVE-STATUS` | `X(01)` | 11 | 1 | `acctActiveStatus` | `string` |
| `ACCT-CURR-BAL` | `S9(10)V99` | 12 | 12 | `acctCurrBal` | `number` |
| `ACCT-CREDIT-LIMIT` | `S9(10)V99` | 24 | 12 | `acctCreditLimit` | `number` |
| `ACCT-CASH-CREDIT-LIMIT` | `S9(10)V99` | 36 | 12 | `acctCashCreditLimit` | `number` |
| `ACCT-OPEN-DATE` | `X(10)` | 48 | 10 | `acctOpenDate` | `string` |
| `ACCT-EXPIRAION-DATE` | `X(10)` | 58 | 10 | `acctExpirationDate` | `string` |
| `ACCT-REISSUE-DATE` | `X(10)` | 68 | 10 | `acctReissueDate` | `string` |
| `ACCT-CURR-CYC-CREDIT` | `S9(10)V99` | 78 | 12 | `acctCurrCycCredit` | `number` |
| `ACCT-CURR-CYC-DEBIT` | `S9(10)V99` | 90 | 12 | `acctCurrCycDebit` | `number` |
| `ACCT-ADDR-ZIP` | `X(10)` | 102 | 10 | `acctAddrZip` | `string` |
| `ACCT-GROUP-ID` | `X(10)` | 112 | 10 | `acctGroupId` | `string` |
| `FILLER` | `X(178)` | 122 | 178 | *(omitted)* | — |

**Feed file:** `app/data/ASCII/acctdata.txt` (300-byte fixed-width records)

### 2.2 Customer Record — `CUSTREC.cpy` -> `customer.model.ts`

| COBOL Field | PIC Clause | Offset | Len | TypeScript Property | TS Type |
|---|---|---|---|---|---|
| `CUST-ID` | `9(09)` | 0 | 9 | `custId` | `number` |
| `CUST-FIRST-NAME` | `X(25)` | 9 | 25 | `custFirstName` | `string` |
| `CUST-MIDDLE-NAME` | `X(25)` | 34 | 25 | `custMiddleName` | `string` |
| `CUST-LAST-NAME` | `X(25)` | 59 | 25 | `custLastName` | `string` |
| `CUST-ADDR-LINE-1` | `X(50)` | 84 | 50 | `custAddrLine1` | `string` |
| `CUST-ADDR-LINE-2` | `X(50)` | 134 | 50 | `custAddrLine2` | `string` |
| `CUST-ADDR-LINE-3` | `X(50)` | 184 | 50 | `custAddrLine3` | `string` |
| `CUST-ADDR-STATE-CD` | `X(02)` | 234 | 2 | `custAddrStateCd` | `string` |
| `CUST-ADDR-COUNTRY-CD` | `X(03)` | 236 | 3 | `custAddrCountryCd` | `string` |
| `CUST-ADDR-ZIP` | `X(10)` | 239 | 10 | `custAddrZip` | `string` |
| `CUST-PHONE-NUM-1` | `X(15)` | 249 | 15 | `custPhoneNum1` | `string` |
| `CUST-PHONE-NUM-2` | `X(15)` | 264 | 15 | `custPhoneNum2` | `string` |
| `CUST-SSN` | `9(09)` | 279 | 9 | `custSsn` | `string` |
| `CUST-GOVT-ISSUED-ID` | `X(20)` | 288 | 20 | `custGovtIssuedId` | `string` |
| `CUST-DOB-YYYYMMDD` | `X(10)` | 308 | 10 | `custDobYyyymmdd` | `string` |
| `CUST-EFT-ACCOUNT-ID` | `X(10)` | 318 | 10 | `custEftAccountId` | `string` |
| `CUST-PRI-CARD-HOLDER-IND` | `X(01)` | 328 | 1 | `custPriCardHolderInd` | `string` |
| `CUST-FICO-CREDIT-SCORE` | `9(03)` | 329 | 3 | `custFicoCreditScore` | `number` |
| `FILLER` | `X(168)` | 332 | 168 | *(omitted)* | — |

**Feed file:** `app/data/ASCII/custdata.txt` (500-byte fixed-width records)

### 2.3 Card Record — `CVACT02Y.cpy` -> `card.model.ts`

| COBOL Field | PIC Clause | Offset | Len | TypeScript Property | TS Type |
|---|---|---|---|---|---|
| `CARD-NUM` | `X(16)` | 0 | 16 | `cardNum` | `string` |
| `CARD-ACCT-ID` | `9(11)` | 16 | 11 | `cardAcctId` | `number` |
| `CARD-CVV-CD` | `9(03)` | 27 | 3 | `cardCvvCd` | `string` |
| `CARD-EMBOSSED-NAME` | `X(50)` | 30 | 50 | `cardEmbossedName` | `string` |
| `CARD-EXPIRAION-DATE` | `X(10)` | 80 | 10 | `cardExpirationDate` | `string` |
| `CARD-ACTIVE-STATUS` | `X(01)` | 90 | 1 | `cardActiveStatus` | `string` |
| `FILLER` | `X(59)` | 91 | 59 | *(omitted)* | — |

**Feed file:** `app/data/ASCII/carddata.txt` (150-byte fixed-width records)

### 2.4 Card Cross-Reference — `CVACT03Y.cpy` -> `card-xref.model.ts`

| COBOL Field | PIC Clause | Offset | Len | TypeScript Property | TS Type |
|---|---|---|---|---|---|
| `XREF-CARD-NUM` | `X(16)` | 0 | 16 | `xrefCardNum` | `string` |
| `XREF-CUST-ID` | `9(09)` | 16 | 9 | `xrefCustId` | `number` |
| `XREF-ACCT-ID` | `9(11)` | 25 | 11 | `xrefAcctId` | `number` |
| `FILLER` | `X(14)` | 36 | 14 | *(omitted)* | — |

**Feed file:** `app/data/ASCII/cardxref.txt` (50-byte fixed-width records)

---

## 3. BMS Screen-to-Angular Component Mapping

Each BMS map defines a 3270 terminal screen (24 rows x 80 columns). The table
below maps every existing BMS map to a proposed Angular component.

| BMS Map File | CICS Screen Title | Proposed Angular Route | Proposed Component | Status |
|---|---|---|---|---|
| `COSGN00.bms` | Login Screen | `/login` | `LoginComponent` | Planned |
| `COACTVW.bms` | View Account | `/accounts/:id` | `AccountDetailComponent` | Planned |
| `COCRDLI.bms` | List Credit Cards | `/cards` | `CardListComponent` | Planned |
| `COCRDSL.bms` | Card Detail / Selection | `/cards/:num` | `CardDetailComponent` | Planned |
| `COMEN01.bms` | Main Menu | `/` | `MainMenuComponent` | Planned |
| `COACTUP.bms` | Update Account | `/accounts/:id/edit` | `AccountEditComponent` | Planned |
| `COCRDUP.bms` | Update Credit Card | `/cards/:num/edit` | `CardEditComponent` | Planned |
| `COTRN00.bms` | Transaction List | `/transactions` | `TransactionListComponent` | Planned |
| `COTRN01.bms` | Transaction Detail | `/transactions/:id` | `TransactionDetailComponent` | Planned |
| `COTRN02.bms` | Add Transaction | `/transactions/new` | `TransactionAddComponent` | Planned |
| `COBIL00.bms` | Bill Payment | `/billing` | `BillingComponent` | Planned |
| `CORPT00.bms` | Reports | `/reports` | `ReportsComponent` | Planned |
| `COUSR00.bms` | User List | `/admin/users` | `UserListComponent` | Planned |
| `COUSR01.bms` | User Add | `/admin/users/new` | `UserAddComponent` | Planned |
| `COUSR02.bms` | User Update | `/admin/users/:id/edit` | `UserEditComponent` | Planned |
| `COUSR03.bms` | User Delete | `/admin/users/:id/delete` | `UserDeleteComponent` | Planned |
| `COADM01.bms` | Admin Menu | `/admin` | `AdminMenuComponent` | Planned |

### Common BMS Header Fields (present on all screens)

Every BMS map includes a standard header occupying rows 1-3:

| Row | Field | BMS Name | Description |
|---|---|---|---|
| 1 | Transaction name | `TRNNAME` | CICS transaction ID (4 chars) |
| 1 | Title line 1 | `TITLE01` | Screen title (40 chars) |
| 1 | Current date | `CURDATE` | `mm/dd/yy` format |
| 2 | Program name | `PGMNAME` | Active COBOL program (8 chars) |
| 2 | Title line 2 | `TITLE02` | Subtitle (40 chars) |
| 2 | Current time | `CURTIME` | `hh:mm:ss` format |
| 23 | Error message | `ERRMSG` | Red, 78 chars — maps to Angular toast/snackbar |

### COACTVW.bms Field Layout (Account Viewer)

The Account Viewer screen is the richest BMS map and serves as the reference
for a combined Account + Customer detail component. Key data fields:

| Row | Col | BMS Name | Label | Maps to |
|---|---|---|---|---|
| 5 | 38 | `ACCTSID` | Account Number | `AccountRecord.acctId` |
| 5 | 70 | `ACSTTUS` | Active Y/N | `AccountRecord.acctActiveStatus` |
| 6 | 17 | `ADTOPEN` | Opened | `AccountRecord.acctOpenDate` |
| 6 | 61 | `ACRDLIM` | Credit Limit | `AccountRecord.acctCreditLimit` |
| 7 | 17 | `AEXPDT` | Expiry | `AccountRecord.acctExpirationDate` |
| 7 | 61 | `ACSHLIM` | Cash Credit Limit | `AccountRecord.acctCashCreditLimit` |
| 8 | 17 | `AREISDT` | Reissue | `AccountRecord.acctReissueDate` |
| 8 | 61 | `ACURBAL` | Current Balance | `AccountRecord.acctCurrBal` |
| 9 | 61 | `ACRCYCR` | Current Cycle Credit | `AccountRecord.acctCurrCycCredit` |
| 10 | 23 | `AADDGRP` | Account Group | `AccountRecord.acctGroupId` |
| 10 | 61 | `ACRCYDB` | Current Cycle Debit | `AccountRecord.acctCurrCycDebit` |
| 12 | 23 | `ACSTNUM` | Customer ID | `CustomerRecord.custId` |
| 12 | 54 | `ACSTSSN` | SSN | `CustomerRecord.custSsn` |
| 13 | 23 | `ACSTDOB` | Date of Birth | `CustomerRecord.custDobYyyymmdd` |
| 13 | 61 | `ACSTFCO` | FICO Score | `CustomerRecord.custFicoCreditScore` |
| 15 | 1 | `ACSFNAM` | First Name | `CustomerRecord.custFirstName` |
| 15 | 28 | `ACSMNAM` | Middle Name | `CustomerRecord.custMiddleName` |
| 15 | 55 | `ACSLNAM` | Last Name | `CustomerRecord.custLastName` |
| 16 | 10 | `ACSADL1` | Address Line 1 | `CustomerRecord.custAddrLine1` |
| 16 | 73 | `ACSSTTE` | State | `CustomerRecord.custAddrStateCd` |
| 17 | 10 | `ACSADL2` | Address Line 2 | `CustomerRecord.custAddrLine2` |
| 17 | 73 | `ACSZIPC` | Zip | `CustomerRecord.custAddrZip` |
| 18 | 10 | `ACSCITY` | City | `CustomerRecord.custAddrLine3` |
| 18 | 73 | `ACSCTRY` | Country | `CustomerRecord.custAddrCountryCd` |
| 19 | 10 | `ACSPHN1` | Phone 1 | `CustomerRecord.custPhoneNum1` |
| 19 | 58 | `ACSGOVT` | Govt Issued ID | `CustomerRecord.custGovtIssuedId` |
| 20 | 10 | `ACSPHN2` | Phone 2 | `CustomerRecord.custPhoneNum2` |
| 20 | 41 | `ACSEFTC` | EFT Account ID | `CustomerRecord.custEftAccountId` |
| 20 | 78 | `ACSPFLG` | Primary Card Holder Y/N | `CustomerRecord.custPriCardHolderInd` |

---

## 4. What Was Built

This initial iteration delivers:

| Deliverable | Path | Description |
|---|---|---|
| TypeScript interfaces | `frontend/src/models/` | One interface per copybook (4 files + barrel export) |
| Mock data | `frontend/src/data/mock-accounts.ts` | 10 accounts, 5 customers, 5 cards, 5 xrefs from ASCII feeds |
| Account List page | `frontend/src/app/accounts/` | Angular Material table with sort, filter, and pagination |
| Angular scaffold | `frontend/` | `package.json`, `angular.json`, standalone bootstrap, lazy routing |

### Running the frontend

```bash
cd frontend
npm install
ng serve          # http://localhost:4200/accounts
```

---

## 5. Recommended Next Steps

1. **Implement remaining components** from the BMS mapping table above.
2. **Connect to a real API** — replace mock data imports with an `HttpClient`
   service pointing at a REST or GraphQL backend.
3. **Add authentication** — the `COSGN00.bms` login screen maps to an Angular
   auth guard + login form.
4. **Handle VSAM-to-DB migration** — the KSDS/AIX access patterns in the COBOL
   programs imply indexed lookups that map naturally to SQL primary/foreign keys.
5. **Batch job equivalents** — JCL batch programs (`POSTTRAN`, etc.) should
   become scheduled backend jobs or event-driven functions.
