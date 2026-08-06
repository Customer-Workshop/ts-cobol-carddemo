# COBIL00C business-rule specification

## Purpose and trigger

CICS transaction `CB00` presents bill payment. On confirmation it posts a payment equal to the account's current balance and rewrites the account to zero balance.

## Inputs and outputs

Input/output map is `COBIL0A` in mapset `COBIL00`. CICS files are `ACCTDAT` (account), `CXACAIX` (card-to-account AIX), and `TRANSACT` (transaction KSDS).

### Exact layouts

#### Account `CVACT01Y` (300 bytes)
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

#### Xref `CVACT03Y` (50 bytes)
| Offset | Field | PIC / representation | Length |
|---:|---|---|---:|
| 1 | XREF-CARD-NUM | `PIC X(16)` display | 16 |
| 17 | XREF-CUST-ID | `PIC 9(09)` unsigned display numeric | 9 |
| 26 | XREF-ACCT-ID | `PIC 9(11)` unsigned display numeric | 11 |
| 37 | FILLER | `PIC X(14)` | 14 |

#### Transaction `CVTRA05Y` (350 bytes)
| Offset | Field | PIC / representation | Length |
|---:|---|---|---:|
| 1 | TRAN-ID | `PIC X(16)` display | 16 |
| 17 | TRAN-TYPE-CD | `PIC X(02)` display | 2 |
| 19 | TRAN-CAT-CD | `PIC 9(04)` unsigned display numeric | 4 |
| 23 | TRAN-SOURCE | `PIC X(10)` display | 10 |
| 33 | TRAN-DESC | `PIC X(100)` display | 100 |
| 133 | TRAN-AMT | `PIC S9(09)V99` signed zoned/display, 2 decimals | 11 |
| 144 | TRAN-MERCHANT-ID | `PIC 9(09)` unsigned display numeric | 9 |
| 153 | TRAN-MERCHANT-NAME | `PIC X(50)` display | 50 |
| 203 | TRAN-MERCHANT-CITY | `PIC X(50)` display | 50 |
| 253 | TRAN-MERCHANT-ZIP | `PIC X(10)` display | 10 |
| 263 | TRAN-CARD-NUM | `PIC X(16)` display | 16 |
| 279 | TRAN-ORIG-TS | `PIC X(26)` display | 26 |
| 305 | TRAN-PROC-TS | `PIC X(26)` display | 26 |
| 331 | FILLER | `PIC X(20)` | 20 |


`WS-TRAN-ID-NUM` is the numeric working item used to increment the last transaction ID; timestamps are 26-character display values.

## Validation and error rules (source order)

1. On Enter, blank account ID sets `Acct ID can NOT be empty...`, positions the cursor at account ID, and redisplays `COBIL0A`; no file access occurs.
2. Confirmation `Y`/`y` sets `CONF-PAY-YES` and reads `ACCTDAT`. `N`/`n` clears the screen and sets an error flag. Blank confirmation reads the account to display its balance but, after the read, sets `Confirm to make a bill payment...` when payment is not confirmed. Any other value sets `Invalid value. Valid values are (Y/N)...` and positions the cursor at confirmation.
3. Account `NOTFND` sets `Account ID NOT found...`; another read response sets `Unable to lookup Account...`. No transaction is written on either path.
4. If the fetched balance is `<= 0`, set `You have nothing to pay...`, position the account field, and do not read xref or write anything.
5. For confirmed positive balance, read `CXACAIX`. Missing xref sets `Unable to lookup XREF AIX file...`; other response errors use the same source error path and stop before transaction write.
6. Start at `HIGH-VALUES` in `TRANSACT`, `READPREV`, and add one to the last ID. A missing/failed browse sets `Transaction ID NOT found...` or `Unable to lookup Transaction...` as coded. `WRITE` duplicate sets `Tran ID already exist...`; another write response sets `Unable to Add Bill pay Transaction...`.
7. After successful transaction write, subtract transaction amount from the account and `REWRITE ACCTDAT`; failed rewrite sets `Unable to Update Account...` and does not claim success.

## Calculations and source excerpt

The payment amount is exactly the fetched `ACCT-CURR-BAL`, so with a positive balance `B`, the rewrite computes `B - B = 0.00`. No `ROUNDED` phrase is present and both account balance and transaction amount have two decimals; no additional fraction is introduced.

```cobol
MOVE ACCT-CURR-BAL TO TRAN-AMT
...
PERFORM WRITE-TRANSACT-FILE
COMPUTE ACCT-CURR-BAL = ACCT-CURR-BAL - TRAN-AMT
PERFORM UPDATE-ACCTDAT-FILE
```

The generated transaction has type `02`, category `2`, source `POS TERM`, description `BILL PAYMENT - ONLINE`, merchant ID `999999999`, merchant name `BILL PAYMENT`, city/ZIP `N/A`, xref card number, and timestamp `YYYY-MM-DDHH:MM:SS` followed by six zero milliseconds in both timestamp fields.

## Control flow and failure handling

PF3 calls `RETURN-TO-PREV-SCREEN`, XCTLs to the COMMAREA target (default `COSGN00C`), and preserves `CARDDEMO-COMMAREA`. PF4 calls `CLEAR-CURRENT-SCREEN`. Normal Enter redisplays the map with `EXEC CICS RETURN TRANSID('CB00') COMMAREA(...)`; errors populate `ERRMSGO` and use `SEND ... ERASE CURSOR`.

## Test cases

| # | Concrete input | Expected output |
|---:|---|---|
| 1 | Account `00000000001` fixture balance `194.00`, confirmation `Y`, xref/card available | New type `02`/category `2` transaction amount `194.00`; account balance rewritten to `0.00`; description exactly `BILL PAYMENT - ONLINE`. |
| 2 | Account field all spaces, confirmation `Y` | `Acct ID can NOT be empty...`; cursor on account ID; no reads/writes. |
| 3 | Account `00000000001`, confirmation blank | Account balance displayed; exact message `Confirm to make a bill payment...`; no transaction or rewrite. |
| 4 | Account `00000000001`, confirmation `X` | Exact `Invalid value. Valid values are (Y/N)...`; cursor on confirmation; no payment. |
| 5 | Account balance `0.00`, confirmation `Y` | Exact `You have nothing to pay...`; no xref lookup, transaction write, or account rewrite. |
| 6 | Account balance `-0.01`, confirmation `Y` | Same `You have nothing to pay...` branch because comparison is `<= ZEROS`. |
| 7 | Positive account whose xref is absent | `Unable to lookup XREF AIX file...`; no transaction write. |
| 8 | Positive account and xref, but last transaction key is maximum representable value | Source `ADD 1` overflow/error path; transaction is not claimed successful. |
