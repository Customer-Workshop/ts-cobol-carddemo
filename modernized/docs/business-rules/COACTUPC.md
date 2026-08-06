# COACTUPC business-rule specification

## Purpose and trigger

CICS transaction `CAUP` searches and updates the account and customer portions of an account view. It performs CICS reads, validates edited screen fields, and rewrites the account/customer records only after valid changes.

## Inputs and outputs

Map `CACTUPA`/mapset `COACTUP`; files are `ACCTDAT`, `CUSTDAT`, `CARDDAT`, `CARDAIX`, and `CXACAIX`.

### Exact persisted layouts

Account update record is 300 bytes:
| Offset | Field | PIC / representation | Length |
|---:|---|---|---:|
| 1 | ACCT-ID | `PIC 9(11)` unsigned display numeric | 11 |
| 12 | ACCT-ACTIVE-STATUS | `PIC X(01)` display | 1 |
| 13 | ACCT-CURR-BAL | `PIC S9(10)V99` signed zoned/display, 2 decimals | 12 |
| 25 | ACCT-CREDIT-LIMIT | `PIC S9(10)V99` signed zoned/display, 2 decimals | 12 |
| 37 | ACCT-CASH-CREDIT-LIMIT | `PIC S9(10)V99` signed zoned/display, 2 decimals | 12 |
| 49 | ACCT-OPEN-DATE | `PIC X(10)` display `YYYY-MM-DD` | 10 |
| 59 | ACCT-EXPIRAION-DATE | `PIC X(10)` display `YYYY-MM-DD` | 10 |
| 69 | ACCT-REISSUE-DATE | `PIC X(10)` display `YYYY-MM-DD` | 10 |
| 79 | ACCT-CURR-CYC-CREDIT | `PIC S9(10)V99` signed zoned/display, 2 decimals | 12 |
| 91 | ACCT-CURR-CYC-DEBIT | `PIC S9(10)V99` signed zoned/display, 2 decimals | 12 |
| 103 | ACCT-ADDR-ZIP | `PIC X(10)` display | 10 |
| 113 | ACCT-GROUP-ID | `PIC X(10)` display | 10 |
| 123 | FILLER | `PIC X(178)` | 178 |


Customer update record is 500 bytes: `CUST-UPDATE-ID PIC 9(09)`; first/middle/last names `PIC X(25)`; three address lines `PIC X(50)`; state `PIC X(02)`; country `PIC X(03)`; ZIP `PIC X(10)`; two phones `PIC X(15)`; SSN `PIC 9(09)`; government ID `PIC X(20)`; DOB `PIC X(10)`; EFT account `PIC X(10)`; primary-card indicator `PIC X(01)`; FICO `PIC 9(03)`; filler `PIC X(168)`. Numeric edit work fields are character `PIC X(15)` and converted to signed `PIC S9(10)V99`; date edits are 10-character display strings.

## Validation and error rules (source order)

1. On initial entry, clear local COMMAREA and show the search prompt `Enter or update id of account to update`; on re-entry, restore common/local areas and PF state.
2. Account/customer search filters are validated with `TEST-NUMVAL-C`/source edit flags. A nonnumeric account or customer key is rejected before CICS reads; no update occurs.
3. Read account through `ACCTDAT`, then customer/card/xref data required for the screen. `NOTFND` sets the source “not found” message; other responses use `File Error: READ on <file> returned RESP <resp>,RESP2 <resp2>` and stop.
4. For changed account fields, validate active status as `Y`/`N`; signed money fields as signed decimal text at two decimals; dates as `YYYY-MM-DD`; FICO as three numeric digits. For changed customer fields, validate required alphabetic/name/address, state/country/ZIP, phone shape, SSN, DOB, EFT ID, primary-card indicator, and FICO according to the individual source edit flags. The first failing field is marked with BMS length `-1`, error is returned, and no rewrite occurs.
5. If no changes are detected, use `No change detected with respect to values fetched.` and do not rewrite. Otherwise enter confirmation state; confirmed valid changes rewrite the account/customer record.
6. Lock/read conflicts use `Could not lock record for update`; changed-before-update uses `Record changed by some one else. Please review`; failed rewrite uses `Update of record failed`.

## Calculations

Numeric strings are parsed into signed decimal working values and moved into the `CVACT01Y` fields without business arithmetic. `PIC S9(10)V99` permits 10 integer and 2 fractional digits; no `COMP-3` is declared for persisted account fields.

## Control flow and failure handling

PF3 XCTLs to the caller or menu with `CARDDEMO-COMMAREA`; PF12/fetch reads details; PF5 validates/confirm-updates; PF4 follows the common clear/return convention coded by the map flow. Local `WS-THIS-PROGCOMMAREA` stores old/new account/customer data and action values `S`, `E`, `N`, `C`, `L`, `F`. Each pseudo-conversational return uses transaction `CAUP` and the combined common/local COMMAREA.

## Test cases

| # | Concrete input | Expected output |
|---:|---|---|
| 1 | Account `00000000001`, change current credit limit from `202.00` to `250.00`, status `Y`, confirm update | `ACCTDAT` rewrite retains ID/balance and stores credit limit `250.00`; state `C`. |
| 2 | Account filter `0000000ABC` | Numeric account-filter validation fails before read; cursor/error is on account key. |
| 3 | Account `00000000001`, credit limit `12.3` | Signed-decimal edit rejects malformed scale; no rewrite. |
| 4 | Account `00000000001`, active status `X` | Active-status validation rejects value other than `Y`/`N`; no rewrite. |
| 5 | Account `00000000001`, expiry `2025-02-30` | Date edit rejects invalid calendar date; no rewrite. |
| 6 | Account `00000000001`, all displayed values unchanged, confirm | Exact `No change detected with respect to values fetched.`; no rewrite. |
| 7 | Account key `00000000100` absent from `ACCTDAT` | Source not-found message; customer/card reads and writes are skipped. |
| 8 | Account `00000000001`, credit limit `9999999999.99` | Maximum `PIC S9(10)V99` magnitude `9999999999.99` is represented exactly to cents when supplied through the numeric edit field; no floating-point rounding is involved. |
