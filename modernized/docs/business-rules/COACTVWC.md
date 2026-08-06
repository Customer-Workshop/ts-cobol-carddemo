# COACTVWC business-rule specification

## Purpose and trigger

CICS transaction `CAVW` displays an account, its customer, and linked card details. It does not rewrite data.

## Inputs and outputs

Map `CACTVWA`/mapset `COACTVW`; CICS files are `ACCTDAT` (account), `CARDDAT` (card), `CUSTDAT` (customer), `CARDAIX` (card-to-account path), and `CXACAIX` (account-to-card/customer xref).

### Exact layouts

Account record is the 300-byte `CVACT01Y`:
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


Customer record is 500-byte `CVCUS01Y`: `CUST-ID PIC 9(09)`; first/middle/last names `PIC X(25)` each; address lines `PIC X(50)` each; state `PIC X(02)`; country `PIC X(03)`; ZIP `PIC X(10)`; phones `PIC X(15)` each; SSN `PIC 9(09)`; government ID `PIC X(20)`; DOB `PIC X(10)`; EFT account `PIC X(10)`; primary-card indicator `PIC X(01)`; FICO `PIC 9(03)`; filler `PIC X(168)`.

Card is 150-byte `CVACT02Y`: card `PIC X(16)`, account `PIC 9(11)`, CVV `PIC 9(03)`, embossed name `PIC X(50)`, expiry `PIC X(10)`, active status `PIC X(01)`, filler `PIC X(59)`. Xref is:
| Offset | Field | PIC / representation | Length |
|---:|---|---|---:|
| 1 | XREF-CARD-NUM | `PIC X(16)` display | 16 |
| 17 | XREF-CUST-ID | `PIC 9(09)` unsigned display numeric | 9 |
| 26 | XREF-ACCT-ID | `PIC 9(11)` unsigned display numeric | 11 |
| 37 | FILLER | `PIC X(14)` | 14 |


## Validation and error rules (source order)

1. On first entry from `COMEN01C`, initialize local area and send `CACTVWA` with search fields empty. On re-entry, copy the common COMMAREA and local `CA` context from `DFHCOMMAREA`.
2. Validate account and customer search filters using the source edit routines. A nonnumeric/invalid account search sets the account field length and rejects the input; a nonnumeric/invalid customer search does the same. Both blank means `No input received`.
3. With a valid account filter, read `ACCTDAT`; `NOTFND` means `Account not found` in the map message path and no customer/card data is shown. Other RESP values construct `File Error: READ on <dataset> returned RESP <...>,RESP2 <...>` and stop.
4. Use xref/AIX reads to locate customer/card. Missing account xref uses `Did not find this account in account card xref file`; missing account master uses `Did not find this account in account master file`; missing customer uses `Did not find associated customer in master file`; no update occurs.
5. On successful reads, populate account/customer/card display fields, including the signed balance and dates without arithmetic conversion.

## Calculations

No business calculation or rounding is performed; values are moved from the VSAM records into BMS output fields.

## Control flow and failure handling

PF3 resolves `CDEMO-TO-PROGRAM`/transaction from the incoming COMMAREA (default menu `COMEN01C`/`CM00`), sets this program as the return context, and XCTLs with `CARDDEMO-COMMAREA`. Enter from another context sends the map; re-enter processes filters then reads data. The pseudo-conversational return uses transaction `CAVW`, a 2,000-byte local area, and the common COMMAREA prefix.

## Test cases

| # | Concrete input | Expected output |
|---:|---|---|
| 1 | Account filter `00000000001` from `acctdata.txt` | Shows account balance `194.00`, active `Y`, open `2014-11-20`, expiry `2025-05-20`; linked customer/card fields are populated. |
| 2 | Account filter `0000000ABC` | Account filter validation fails; account field is marked for correction; no VSAM read. |
| 3 | Both account and customer filters spaces | Exact source information message `No input received`; no data read. |
| 4 | Account `00000000050` absent from `ACCTDAT` | `NOTFND` path; account-not-found message; no card/customer display. |
| 5 | Valid account with missing xref/card link | Exact source link message `Did not find this account in account card xref file`; no update. |
| 6 | PF3 after displaying account `00000000001` | XCTL to incoming caller (or `COMEN01C` when no caller), preserving common context and clearing the local search context as coded. |
