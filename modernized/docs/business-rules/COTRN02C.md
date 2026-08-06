# COTRN02C business-rule specification

## Purpose and trigger

**Trigger:** `CT02`. CICS transaction CT02.

## Inputs and outputs

Adds a transaction after validating account/card and transaction fields.

### Exact layouts

Reads `WS-CXACAIX-FILE` and `WS-CCXREF-FILE`; writes `WS-TRANSACT-FILE`. The transaction record (`CVTRA05Y`) is: ID `PIC X(16)`, type `PIC X(02)`, category `PIC 9(04)`, source `PIC X(10)`, description `PIC X(100)`, amount `PIC S9(09)V99` (zoned signed decimal), merchant ID `PIC 9(09)`, merchant name/city/ZIP `PIC X(50)/X(50)/X(10)`, card `PIC X(16)`, and origin/process timestamps `PIC X(26)`. Account/xref keys retain `PIC 9(11)` and `PIC X(16)` representations; date conversion calls `CSUTLDTC`.

## Validation and error rules

Validation order: account/card key presence and numeric checks; required type/category/source/description/amount/date/merchant fields; numeric/date shape checks; date utility validation; then lookup and write. Exact messages are moved to `ERRMSGO` in source.

Source message assignments observed (where present):

- `MOVE 'Confirm to add this transaction...'`
- `MOVE 'Invalid value. Valid values are (Y/N)...'`
- `MOVE 'Account ID must be Numeric...' TO`
- `MOVE 'Card Number must be Numeric...' TO`
- `MOVE 'Account or Card Number must be entered...' TO`
- `MOVE 'Type CD must be Numeric...' TO`
- `MOVE 'Category CD must be Numeric...' TO`
- `MOVE 'Merchant ID must be Numeric...' TO`
- `MOVE 'Account ID NOT found...' TO`
- `MOVE 'Unable to lookup Acct in XREF AIX file...' TO`
- `MOVE 'Card Number NOT found...' TO`
- `MOVE 'Unable to lookup Card # in XREF file...' TO`
- `MOVE 'Transaction ID NOT found...' TO`
- `MOVE 'Unable to lookup Transaction...' TO`
- `MOVE 'Unable to lookup Transaction...' TO`
- `MOVE 'Tran ID already exist...' TO`
- `MOVE 'Unable to Add Transaction...' TO`

Rules are listed in source paragraph order above. Any source behavior not decipherable from declarations is deliberately not guessed.

## Calculations

Amount is parsed with `NUMVAL-C` into `PIC S9(09)V99`; transaction ID is incremented from the prior key (`ADD 1`). No implicit rounding is stated; keep decimal scale exact.

Relevant source excerpt:

```cobol
    MOVE SPACES TO WS-MESSAGE
    MOVE CCDA-MSG-INVALID-KEY      TO WS-MESSAGE
    COMPUTE WS-ACCT-ID-N = FUNCTION NUMVAL(ACTIDINI OF
    COMPUTE WS-CARD-NUM-N = FUNCTION NUMVAL(CARDNINI OF
    COMPUTE WS-TRAN-AMT-N = FUNCTION NUMVAL-C(TRNAMTI OF
    COMPUTE WS-TRAN-AMT-N = FUNCTION NUMVAL-C(TRNAMTI OF
    MOVE WS-MESSAGE TO ERRMSGO OF COTRN2AO
    MOVE SPACES             TO WS-MESSAGE
```

## Control flow and failure handling

Enter validates and writes; duplicate transaction ID shows “Tran ID already exist...”; NOTFND/other reads redisplay. PF navigation transfers with COMMAREA.

## Test cases

| # | Concrete input | Expected output/error |
|---:|---|---|
| 1 | Valid source record/account with all required fields populated and an existing referenced key | Successful read/update/write; exact success path from source. |
| 2 | Missing required key field (blank/LOW-VALUES) | Validation error attached to that field; no data write. |
| 3 | Non-numeric value in a numeric PIC input | Numeric validation error; no data write. |
| 4 | Referenced account/card/xref absent | CICS NOTFND or batch lookup failure; source error message and no partial update. |
| 5 | Duplicate output key (transaction/user/card as applicable) | DUPKEY/DUPREC branch and source error message. |
| 6 | Maximum representable amount for the declared PIC | Accepted only if source numeric validation permits; preserve declared scale and sign. |
| 7 | Negative/zero boundary value where business validation applies | Follow the explicit source comparison; reject when the rule above says so. |
