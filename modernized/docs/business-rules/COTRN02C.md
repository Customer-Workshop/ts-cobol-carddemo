# COTRN02C business-rule specification

## Purpose and trigger

CICS transaction `CT02` adds a transaction to `TRANSACT` after validating an account/card key, all required screen fields, numeric/date formats, and reference lookups.

## Inputs and outputs

Map `COTRN2A`/mapset `COTRN02`; files are `CXACAIX` for account-to-card AIX, `CCXREF` for card lookup, and `TRANSACT` for the transaction KSDS.

### Exact persisted layout (`CVTRA05Y`, 350 bytes)
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


Screen numeric input is text. Account ID is converted with `NUMVAL` to working `PIC 9(11)`; card number is converted to a numeric working value before the xref lookup; amount is converted with `NUMVAL-C` to signed `PIC S9(09)V99`. `CSUTLDTC` validates dates in `YYYY-MM-DD` form.

## Validation and error rules (source order)

1. Confirmation is evaluated first: `Y/y` calls add, `N/n` or blank sets `Confirm to add this transaction...`, and any other value sets `Invalid value. Valid values are (Y/N)...`; cursor is confirmation and the map is resent.
2. Key selection is exclusive: a nonblank account ID must be numeric, otherwise `Account ID must be Numeric...`; it is converted and looked up through `CXACAIX`, then card number is populated. If account is blank but card is nonblank, card must be numeric or `Card Number must be Numeric...`; it is looked up through `CCXREF`, then account is populated. If both are blank, `Account or Card Number must be entered...`.
3. Required text fields are checked in this order: type (`Type CD can NOT be empty...`), category (`Category CD can NOT be empty...`), source (`Source can NOT be empty...`), description (`Description can NOT be empty...`), amount (`Amount can NOT be empty...`), origin date (`Orig Date can NOT be empty...`), process date (`Proc Date can NOT be empty...`), merchant ID (`Merchant ID can NOT be empty...`), merchant name (`Merchant Name can NOT be empty...`), merchant city (`Merchant City can NOT be empty...`), merchant ZIP (`Merchant Zip can NOT be empty...`). Each failure sets that field's BMS length to `-1`, sends the map, and does not write.
4. Type and category must then be numeric, with exact messages `Type CD must be Numeric...` and `Category CD must be Numeric...`.
5. Amount must match the source positional shape: optional sign at position 1, eight numeric integer positions, `.` at position 10, and two numeric decimal positions. Failure is `Amount should be in format -99999999.99`.
6. Origin and process dates must match `YYYY-MM-DD`; shape failures use `Orig Date should be in format YYYY-MM-DD` and `Proc Date should be in format YYYY-MM-DD`. `CSUTLDTC` then validates calendar dates; failures use `Orig Date - Not a valid date...` and `Proc Date - Not a valid date...` (except utility message `2513`, which source tolerates).
7. Merchant ID must be numeric; failure is `Merchant ID must be Numeric...`.
8. Add starts transaction browse at `HIGH-VALUES`, reads the previous key, increments it by one, and writes a populated `CVTRA05Y`. Missing/failed browse paths use `Transaction ID NOT found...` or `Unable to lookup Transaction...`; duplicate write uses `Tran ID already exist...`; other write errors use `Unable to Add Transaction...`.

## Calculations and source excerpt

Amount conversion preserves the two-decimal receiving scale. `TRAN-ID` is the previous key plus one; no `ROUNDED` phrase or balance calculation exists.

```cobol
MOVE HIGH-VALUES TO TRAN-ID
PERFORM STARTBR-TRANSACT-FILE
PERFORM READPREV-TRANSACT-FILE
PERFORM ENDBR-TRANSACT-FILE
MOVE TRAN-ID TO WS-TRAN-ID-N
ADD 1 TO WS-TRAN-ID-N
INITIALIZE TRAN-RECORD
MOVE WS-TRAN-ID-N TO TRAN-ID
...
COMPUTE WS-TRAN-AMT-N = FUNCTION NUMVAL-C(TRNAMTI OF COTRN2AI)
MOVE WS-TRAN-AMT-N TO TRAN-AMT
PERFORM WRITE-TRANSACT-FILE
```

## Control flow and failure handling

Enter validates and either redisplays `COTRN2A` or writes the transaction. PF3 XCTLs to `CDEMO-TO-PROGRAM` (default `COSGN00C`) with `CARDDEMO-COMMAREA`; successful return preserves prior navigation context. The source handles only Enter/PF3 as valid attention keys; other keys are treated as Enter.

## Test cases

| # | Concrete input | Expected output |
|---:|---|---|
| 1 | Account `00000000027`, type `01`, category `0001`, source `POS TERM`, amount `+00000123.45`, origin/process `2022-06-10`, merchant ID `000000800`, card `0683586198171516` | New transaction contains amount `123.45`, card `0683586198171516`, and all supplied fields; key is prior maximum key plus one. |
| 2 | Account field `0000000ABC`, card blank | Exact `Account ID must be Numeric...`; no xref read or transaction write. |
| 3 | Both account and card blank | Exact `Account or Card Number must be entered...`; cursor on account. |
| 4 | Valid account, type blank, all later fields populated | Exact `Type CD can NOT be empty...`; category/source/etc. are cleared when error flag is already on; no write. |
| 5 | Type `AA`, category `0001` | Exact `Type CD must be Numeric...`; no write. |
| 6 | Amount `12.3` | Exact `Amount should be in format -99999999.99`; no write. |
| 7 | Origin `2022-02-30`, process `2022-06-10` | Exact `Orig Date - Not a valid date...`; no write. |
| 8 | Amount `-00000000.01` | Accepted by positional sign/scale validation and persisted as `-0.01`; source has no positivity check. |
| 9 | Amount `+99999999.99` | Maximum declared screen shape is accepted if subsequent numeric conversion and write succeed; amount persists at `99,999,999.99`. |
| 10 | Valid fields but transaction browse returns duplicate on generated key | Exact `Tran ID already exist...`; no successful write result. |
