# CardDemo Modernization Notes

This document maps the original mainframe COBOL/CICS CardDemo application to the modern Angular frontend.

## 1. COBOL Copybook → TypeScript Interface Mapping

### Account Record — `CVACT01Y.cpy` → `account.model.ts`

| COBOL Field              | PIC Clause      | TypeScript Field       | Type     | Notes                            |
|--------------------------|-----------------|------------------------|----------|----------------------------------|
| `ACCT-ID`                | `9(11)`         | `accountId`            | `string` | Preserved as string for leading zeros |
| `ACCT-ACTIVE-STATUS`     | `X(01)`         | `activeStatus`         | `string` | `'Y'` or `'N'`                  |
| `ACCT-CURR-BAL`          | `S9(10)V99`     | `currentBalance`       | `number` | Packed decimal → JavaScript float |
| `ACCT-CREDIT-LIMIT`      | `S9(10)V99`     | `creditLimit`          | `number` | Packed decimal → JavaScript float |
| `ACCT-CASH-CREDIT-LIMIT` | `S9(10)V99`     | `cashCreditLimit`      | `number` | Packed decimal → JavaScript float |
| `ACCT-OPEN-DATE`         | `X(10)`         | `openDate`             | `string` | ISO date format `YYYY-MM-DD`     |
| `ACCT-EXPIRAION-DATE`    | `X(10)`         | `expirationDate`       | `string` | ISO date format `YYYY-MM-DD`     |
| `ACCT-REISSUE-DATE`      | `X(10)`         | `reissueDate`          | `string` | ISO date format `YYYY-MM-DD`     |
| `ACCT-CURR-CYC-CREDIT`   | `S9(10)V99`     | `currentCycleCredit`   | `number` | Packed decimal → JavaScript float |
| `ACCT-CURR-CYC-DEBIT`    | `S9(10)V99`     | `currentCycleDebit`    | `number` | Packed decimal → JavaScript float |
| `ACCT-ADDR-ZIP`          | `X(10)`         | `addressZip`           | `string` |                                  |
| `ACCT-GROUP-ID`          | `X(10)`         | `groupId`              | `string` |                                  |
| `FILLER`                 | `X(178)`        | —                      | —        | Dropped (padding)                |

### Customer Record — `CUSTREC.cpy` → `customer.model.ts`

| COBOL Field                  | PIC Clause  | TypeScript Field            | Type     | Notes                          |
|------------------------------|-------------|-----------------------------|----------|--------------------------------|
| `CUST-ID`                    | `9(09)`     | `customerId`                | `string` | Preserved as string            |
| `CUST-FIRST-NAME`            | `X(25)`     | `firstName`                 | `string` | Trimmed                        |
| `CUST-MIDDLE-NAME`           | `X(25)`     | `middleName`                | `string` | Trimmed                        |
| `CUST-LAST-NAME`             | `X(25)`     | `lastName`                  | `string` | Trimmed                        |
| `CUST-ADDR-LINE-1`           | `X(50)`     | `addressLine1`              | `string` | Trimmed                        |
| `CUST-ADDR-LINE-2`           | `X(50)`     | `addressLine2`              | `string` | Trimmed                        |
| `CUST-ADDR-LINE-3`           | `X(50)`     | `addressLine3`              | `string` | Trimmed                        |
| `CUST-ADDR-STATE-CD`         | `X(02)`     | `stateCode`                 | `string` | 2-char state code              |
| `CUST-ADDR-COUNTRY-CD`       | `X(03)`     | `countryCode`               | `string` | 3-char country code            |
| `CUST-ADDR-ZIP`              | `X(10)`     | `zipCode`                   | `string` |                                |
| `CUST-PHONE-NUM-1`           | `X(15)`     | `phoneNumber1`              | `string` |                                |
| `CUST-PHONE-NUM-2`           | `X(15)`     | `phoneNumber2`              | `string` |                                |
| `CUST-SSN`                   | `9(09)`     | `ssn`                       | `string` | Sensitive — masked in UI       |
| `CUST-GOVT-ISSUED-ID`        | `X(20)`     | `governmentIssuedId`        | `string` |                                |
| `CUST-DOB-YYYYMMDD`          | `X(10)`     | `dateOfBirth`               | `string` | ISO date format                |
| `CUST-EFT-ACCOUNT-ID`        | `X(10)`     | `eftAccountId`              | `string` |                                |
| `CUST-PRI-CARD-HOLDER-IND`   | `X(01)`     | `primaryCardHolderIndicator`| `string` | `'Y'` or `'N'`                |
| `CUST-FICO-CREDIT-SCORE`     | `9(03)`     | `ficoCreditScore`           | `number` | Numeric range 0–999            |
| `FILLER`                     | `X(168)`    | —                           | —        | Dropped (padding)              |

### Card Record — `CVACT02Y.cpy` → `card.model.ts`

| COBOL Field              | PIC Clause  | TypeScript Field    | Type     | Notes                     |
|--------------------------|-------------|---------------------|----------|---------------------------|
| `CARD-NUM`               | `X(16)`     | `cardNumber`        | `string` | 16-digit card number      |
| `CARD-ACCT-ID`           | `9(11)`     | `accountId`         | `string` | FK to Account             |
| `CARD-CVV-CD`            | `9(03)`     | `cvvCode`           | `string` | Masked in UI display      |
| `CARD-EMBOSSED-NAME`     | `X(50)`     | `embossedName`      | `string` | Name on card              |
| `CARD-EXPIRAION-DATE`    | `X(10)`     | `expirationDate`    | `string` | ISO date format           |
| `CARD-ACTIVE-STATUS`     | `X(01)`     | `activeStatus`      | `string` | `'Y'` or `'N'`           |
| `FILLER`                 | `X(59)`     | —                   | —        | Dropped (padding)         |

### Card Cross-Reference — `CVACT03Y.cpy` → `card.model.ts` (`CardCrossReference`)

| COBOL Field        | PIC Clause | TypeScript Field | Type     | Notes               |
|--------------------|------------|------------------|----------|----------------------|
| `XREF-CARD-NUM`    | `X(16)`    | `cardNumber`     | `string` | FK to Card           |
| `XREF-CUST-ID`     | `9(09)`    | `customerId`     | `string` | FK to Customer       |
| `XREF-ACCT-ID`     | `9(11)`    | `accountId`      | `string` | FK to Account        |
| `FILLER`           | `X(14)`    | —                | —        | Dropped (padding)    |

### Transaction Record — `CVTRA05Y.cpy` → `transaction.model.ts`

| COBOL Field            | PIC Clause    | TypeScript Field      | Type     | Notes                       |
|------------------------|---------------|-----------------------|----------|-----------------------------|
| `TRAN-ID`              | `X(16)`       | `transactionId`       | `string` | Unique transaction ID       |
| `TRAN-TYPE-CD`         | `X(02)`       | `typeCode`            | `string` | Maps to `TransactionType`   |
| `TRAN-CAT-CD`          | `9(04)`       | `categoryCode`        | `string` | Maps to `TransactionCategory` |
| `TRAN-SOURCE`          | `X(10)`       | `source`              | `string` | POS TERM, E-COMM, ATM, etc. |
| `TRAN-DESC`            | `X(100)`      | `description`         | `string` |                             |
| `TRAN-AMT`             | `S9(09)V99`   | `amount`              | `number` | Signed decimal → float      |
| `TRAN-MERCHANT-ID`     | `9(09)`       | `merchantId`          | `string` |                             |
| `TRAN-MERCHANT-NAME`   | `X(50)`       | `merchantName`        | `string` |                             |
| `TRAN-MERCHANT-CITY`   | `X(50)`       | `merchantCity`        | `string` |                             |
| `TRAN-MERCHANT-ZIP`    | `X(10)`       | `merchantZip`         | `string` |                             |
| `TRAN-CARD-NUM`        | `X(16)`       | `cardNumber`          | `string` | FK to Card                  |
| `TRAN-ORIG-TS`         | `X(26)`       | `originTimestamp`     | `string` | ISO-style timestamp         |
| `TRAN-PROC-TS`         | `X(26)`       | `processedTimestamp`  | `string` | ISO-style timestamp         |
| `FILLER`               | `X(20)`       | —                     | —        | Dropped (padding)           |

---

## 2. BMS Screen → Angular Component Mapping

### Sign-On Screen — `COSGN00.bms` / `COSGN00C.cbl` → `LoginComponent`

| 3270 Element       | BMS Field  | Angular Element                           | Notes                        |
|--------------------|------------|-------------------------------------------|------------------------------|
| User ID input      | `USERID`   | `<input matInput [(ngModel)]="userId">`   | Max 8 chars                  |
| Password input     | `PASSWD`   | `<input matInput type="password">`        | Dark attribute → password    |
| Error message      | `ERRMSG`   | `@if (errorMessage) { ... }`              | Red text                     |
| ENTER key          | —          | Submit button                             | Replaces F-key navigation    |
| F3=Exit            | —          | Not applicable                            | Browser handles exit         |

### Account View — `COACTVW.bms` / `COACTVWC.cbl` → `AccountDetailComponent` (view mode)

| 3270 Element        | BMS Field  | Angular Element                  | Notes                          |
|---------------------|------------|----------------------------------|--------------------------------|
| Account Number      | `ACCTSID`  | Route parameter `:id`            | Lookup by account ID           |
| Active Y/N          | `ACSTTUS`  | `<mat-chip>` Active/Inactive     | Color-coded status chip        |
| Opened date         | `ADTOPEN`  | Detail grid field                |                                |
| Credit Limit        | `ACRDLIM`  | Formatted with `CurrencyPipe`    | `+ZZZ,ZZZ,ZZZ.99` → currency  |
| Expiry date         | `AEXPDT`   | Detail grid field                |                                |
| Cash Credit Limit   | `ACSHLIM`  | Formatted with `CurrencyPipe`    |                                |
| Reissue date        | `AREISDT`  | Detail grid field                |                                |
| Current Balance     | `ACURBAL`  | Highlighted balance field        | Blue, larger font              |

### Account Update — `COACTUP.bms` / `COACTUPC.cbl` → `AccountDetailComponent` (edit mode)

| 3270 Element         | BMS Field  | Angular Element                     | Notes                       |
|----------------------|------------|-------------------------------------|-----------------------------|
| Editable fields      | Various    | `<mat-form-field>` with `ngModel`   | Two-way binding             |
| Date fields (Y-M-D)  | `OPNYEAR`/`OPNMON`/`OPNDAY` | Single date input    | Combined into single field  |
| ENTER=Confirm         | —          | Save button                        | Replaces ENTER key          |
| F3=Back               | —          | Cancel / Back button               | Replaces F-key navigation   |

### Card List — `COCRDLI.bms` / `COCRDLIC.cbl` → `CardListComponent`

| 3270 Element          | BMS Field  | Angular Element                     | Notes                      |
|-----------------------|------------|-------------------------------------|----------------------------|
| Card list rows        | Repeated   | `<table mat-table>` rows            | Sortable columns           |
| Selection (Sel field)  | `SEL`      | View button with routerLink         | Click replaces selection   |
| Page Up/Down           | PF7/PF8    | Client-side sorting                 | No pagination needed (mock) |

### Card Detail — `COCRDSL.bms` / `COCRDSLC.cbl` → `CardDetailComponent` (view mode)

| 3270 Element       | BMS Field  | Angular Element                     | Notes                        |
|--------------------|------------|-------------------------------------|------------------------------|
| Card Number        | `CRDNUM`   | Masked display + full in detail     | `****-****-****-1234`        |
| Account ID         | `CRDACID`  | Read-only field                     |                              |
| Embossed Name      | `CRDNAME`  | Card visual + detail                | Credit card UI mockup        |
| Expiration         | `CRDEXP`   | Card visual + detail                |                              |
| Status             | `CRDSTS`   | `<mat-chip>` Active/Inactive        |                              |

### Card Update — `COCRDUP.bms` / `COCRDUPC.cbl` → `CardDetailComponent` (edit mode)

| 3270 Element        | BMS Field  | Angular Element                    | Notes                        |
|---------------------|------------|------------------------------------|------------------------------|
| Editable fields     | Various    | `<mat-form-field>` + `ngModel`     | Two-way binding              |
| Card Number (RO)    | `CRDNUM`   | Disabled input                     | Not editable (primary key)   |
| ENTER=Confirm       | —          | Save button                        |                              |

### Transaction List — `COTRN00.bms` / `COTRN00C.cbl` → `TransactionListComponent`

| 3270 Element          | BMS Field  | Angular Element                    | Notes                       |
|-----------------------|------------|------------------------------------|-----------------------------|
| Filter by Acct/Card   | Input      | Card number + type filter          | Real-time filtering         |
| Transaction rows      | Repeated   | `<table mat-table>` rows           | Sortable columns            |
| Amount display        | `TAMT`     | `CurrencyPipe` + color coding      | Red for debits              |
| F5=Add Transaction    | —          | "Add Transaction" button           | Replaces F-key              |

### Transaction View — `COTRN01.bms` / `COTRN01C.cbl` → `TransactionDetailComponent`

| 3270 Element        | BMS Field  | Angular Element                    | Notes                        |
|---------------------|------------|------------------------------------|------------------------------|
| All detail fields   | Various    | Detail grid with labeled fields    | Read-only view               |
| Merchant info       | Multiple   | Grouped section with divider       | Merchant block               |
| Timestamps          | `TOPTS`    | Formatted with `DatePipe`          |                              |

### Transaction Add — `COTRN02.bms` / `COTRN02C.cbl` → `TransactionAddComponent`

| 3270 Element       | BMS Field  | Angular Element                     | Notes                       |
|--------------------|------------|-------------------------------------|-----------------------------|
| Type selection     | `TTYPE`    | `<mat-select>` dropdown             | From `trantype.txt`         |
| Category           | `TCAT`     | `<mat-select>` dropdown             | From `trancatg.txt`         |
| Source             | `TSRC`     | `<mat-select>` dropdown             | Predefined sources          |
| Card Number        | `TCARDNUM` | Text input with validation           |                            |
| Amount             | `TAMT`     | Number input with dollar prefix      |                            |
| Description        | `TDESC`    | Text input (max 100 chars)           |                            |

### Bill Payment — `COBIL00.bms` / `COBIL00C.cbl` → `BillPaymentComponent`

| 3270 Element          | BMS Field  | Angular Element                    | Notes                       |
|-----------------------|------------|------------------------------------|-----------------------------|
| Account ID input      | `ACTIDIN`  | `<input matInput>` with lookup     | Account ID entry            |
| Current Balance       | `CURBAL`   | Large formatted display            | Prominent balance display   |
| Confirm Y/N           | `CONFIRM`  | `<input>` with Y/N + submit button | Preserves mainframe UX      |
| Error message         | `ERRMSG`   | Conditional error text             | Red text                    |
| Success feedback      | —          | Success section with icon          | Modern success state        |

---

## 3. Data Source Mapping

| Feed File         | COBOL Copybook   | TypeScript Service       | Mock Data Source             |
|-------------------|------------------|--------------------------|------------------------------|
| `acctdata.txt`    | `CVACT01Y.cpy`   | `AccountService`         | `MOCK_ACCOUNTS` (5 records)  |
| `custdata.txt`    | `CUSTREC.cpy`    | `CustomerService`        | `MOCK_CUSTOMERS` (5 records) |
| `carddata.txt`    | `CVACT02Y.cpy`   | `CardService`            | `MOCK_CARDS` (8 records)     |
| `cardxref.txt`    | `CVACT03Y.cpy`   | `CardService` (xrefs)    | `MOCK_CARD_XREFS` (8 records)|
| `dailytran.txt`   | `CVTRA05Y.cpy`   | `TransactionService`     | `MOCK_TRANSACTIONS` (8 records)|
| `trantype.txt`    | —                | `TransactionService`     | `MOCK_TRANSACTION_TYPES`     |
| `trancatg.txt`    | —                | `TransactionService`     | `MOCK_TRANSACTION_CATEGORIES`|

---

## 4. Navigation Mapping

The mainframe CardDemo application uses a CICS menu system with transaction IDs and PF keys for navigation. The Angular frontend replaces this with a sidebar navigation and URL routing.

| Mainframe Navigation       | Angular Equivalent         | Route               |
|----------------------------|----------------------------|----------------------|
| Transaction ID `CDSG`      | Login page                 | `/login`             |
| Main Menu                  | Dashboard                  | `/dashboard`         |
| Menu Option → Account View | Accounts list              | `/accounts`          |
| Account detail lookup      | Account detail page        | `/accounts/:id`      |
| Menu Option → Cards        | Cards list                 | `/cards`             |
| Card detail selection      | Card detail page           | `/cards/:cardNumber` |
| Menu Option → Transactions | Transactions list          | `/transactions`      |
| Transaction detail (F1)    | Transaction detail page    | `/transactions/:id`  |
| F5=Add Transaction         | Add transaction page       | `/transactions/add`  |
| Menu Option → Bill Pay     | Bill payment page          | `/bill-payment`      |
| PF3=Back / PF12=Cancel     | Back button / browser nav  | `routerLink` back    |
| PF7/PF8 = Page Up/Down     | Material table sorting     | Client-side sort     |

---

## 5. Technology Decisions

| Concern                   | Mainframe                  | Angular Frontend                     |
|---------------------------|----------------------------|--------------------------------------|
| UI Framework              | BMS 3270 Maps              | Angular 17+ with standalone components |
| Component Library         | 3270 Field attributes      | Angular Material                     |
| Data Binding              | COMMAREA / VSAM            | TypeScript services with mock data   |
| State Management          | CICS pseudo-conversational | Angular signals + service state      |
| Navigation                | Transaction IDs + PF keys  | Angular Router with lazy loading     |
| Authentication            | USRSEC subsystem           | AuthService with guard               |
| Styling                   | 3270 color attributes      | SCSS + Material theming              |
| Data Format               | EBCDIC fixed-width         | JSON/TypeScript objects              |
| Numeric Handling           | COMP-3 packed decimal      | JavaScript `number` with currency pipe |

---

## 6. Architecture Notes

- **Standalone Components**: All components use the Angular 17+ standalone API — no NgModules.
- **Lazy Loading**: All routes use `loadComponent()` for code splitting.
- **Mock Data**: Services use in-memory arrays derived from the ASCII feed files. Replace with HTTP calls to a real API backend when available.
- **Responsive Design**: Layout adapts from sidebar navigation (desktop) to hamburger menu (mobile) using `BreakpointObserver`.
- **FILLER Fields**: All COBOL FILLER fields (padding) are omitted from TypeScript interfaces.
- **Signed Numerics**: COBOL `S9(n)V99` fields (overpunched sign in EBCDIC) are mapped to JavaScript `number` type. The original EBCDIC sign encoding (`{` = +0, `}` = -0, etc.) is parsed at the data layer, not in the frontend.
