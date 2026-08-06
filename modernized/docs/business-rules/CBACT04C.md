# CBACT04C business-rule specification

## Purpose and trigger

`INTCALC` executes this batch program monthly. It reads transaction-category balances and disclosure-group rates, writes interest transactions, adds total interest to each account, resets cycle totals, and updates the category balance file.

## Inputs and outputs

`ACCTFILE` (`ACCTDATA.VSAM.KSDS`) is I-O; `TCATBALF` is I-O; `DISCGRP` and `XREFFILE` are input; `TRANFILE` is output. Record layouts are source copybooks, not inferred SQL schemas.

### Exact record layouts

#### `CVACT01Y` account (300 bytes)
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

#### `CVTRA01Y` category balance (50 bytes)
| Offset | Field | PIC / representation | Length |
|---:|---|---|---:|
| 1 | TRANCAT-ACCT-ID | `PIC 9(11)` unsigned display | 11 |
| 12 | TRANCAT-TYPE-CD | `PIC X(02)` | 2 |
| 14 | TRANCAT-CD | `PIC 9(04)` unsigned display | 4 |
| 18 | TRAN-CAT-BAL | `PIC S9(09)V99` signed display, 2 decimals | 11 |
| 29 | FILLER | `PIC X(22)` | 22 |
#### `CVTRA02Y` disclosure group (50 bytes)
| Offset | Field | PIC / representation | Length |
|---:|---|---|---:|
| 1 | DIS-ACCT-GROUP-ID | `PIC X(10)` | 10 |
| 11 | DIS-TRAN-TYPE-CD | `PIC X(02)` | 2 |
| 13 | DIS-TRAN-CAT-CD | `PIC 9(04)` | 4 |
| 17 | DIS-INT-RATE | `PIC S9(04)V99` signed display, 2 decimals | 6 |
| 23 | FILLER | `PIC X(28)` | 28 |
#### Working storage
`WS-MONTHLY-INT` and `WS-TOTAL-INT` are both `PIC S9(09)V99` (display signed, two decimals). `APPL-RESULT` is `PIC S9(9) COMP`; `TWO-BYTES-BINARY` is `PIC 9(4) BINARY`.

## Validation and error rules (source order)

1. Open `TCATBALF`, `XREFFILE`, `DISCGRP`, `ACCTFILE` I-O, and `TRANFILE` output. Any nonzero open status displays the corresponding exact message (`ERROR OPENING TRANSACTION BALANCE FILE`, `ERROR OPENING CROSS REF FILE`, `ERROR OPENING DISCLOSURE GROUP FILE`, `ERROR OPENING ACCOUNT MASTER FILE`, or `ERROR OPENING TRANSACTION FILE`) and abends.
2. For each category balance, lookup the account. A missing disclosure-group lookup uses group `DEFAULT` and calls the default-rate read; a failed default read displays `ERROR READING DEFAULT DISCLOSURE GROUP` and abends.
3. Read the account and category balance. Any failed read displays the source file-specific error/status and abends; there is no reject file.
4. Compute and write one interest transaction per qualifying category, then add the accumulated total to the account and reset `ACCT-CURR-CYC-CREDIT` and `ACCT-CURR-CYC-DEBIT` to zero.

## Calculations and rounding conclusion

The exact source paragraph is:

```cobol
1300-COMPUTE-INTEREST.
    COMPUTE WS-MONTHLY-INT
     = ( TRAN-CAT-BAL * DIS-INT-RATE) / 1200
    ADD WS-MONTHLY-INT TO WS-TOTAL-INT
    PERFORM 1300-B-WRITE-TX.
```

`TRAN-CAT-BAL` has scale 2 and `DIS-INT-RATE` has scale 2. `WS-MONTHLY-INT` has scale 2. With no `ROUNDED` phrase, assignment truncates toward zero to cents. Example: balance `1,234.56` and rate `15.25` produce `(1234.56 × 15.25) / 1200 = 15.6884`; stored `WS-MONTHLY-INT` is **15.68**, not `15.69`. Negative `-15.6884` stores **-15.68**. `WS-TOTAL-INT` is accumulated from these already-truncated monthly amounts. Fees paragraph `1400-COMPUTE-FEES` is explicitly unimplemented and does not change totals.

## Control flow and failure handling

Interest transaction IDs are `PARM-DATE` concatenated with an incrementing six-digit suffix. Each generated record has type `01`, category `05`, source `System`, description `Int. for a/c ` followed by account ID, merchant ID zero, blank merchant fields, xref card number, and the DB2-format timestamp copied to both timestamps. Transaction write/status and account rewrite failures display status and abend through `CEE3ABD`.

## Test cases

| # | Concrete input | Expected output |
|---:|---|---|
| 1 | Account `00000000001`, category balance `1,234.56`, rate `15.25`, account balance `194.00` | Monthly interest `15.68`; generated type `01`/category `0005` transaction; account balance `209.68`; cycle credit/debit reset to `0.00`. |
| 2 | Fixture account group `A000000000`, category balance `0.00`, rate `15.00` | Monthly interest `0.00`; transaction amount `0.00`; account balance unchanged. |
| 3 | Balance `-1,234.56`, rate `15.25` | Mathematical result `-15.6884`; stored interest `-15.68` by truncation toward zero. |
| 4 | Balance `1,200.00`, rate `0.00` | Stored interest `0.00`; no rounding artifact. |
| 5 | Disclosure group key absent, default group exists | Reads `DEFAULT` rate and continues; no abend. |
| 6 | Disclosure group key absent and `DEFAULT` read returns status `23`/other failure | Display `ERROR READING DEFAULT DISCLOSURE GROUP`; abend; no account rewrite. |
