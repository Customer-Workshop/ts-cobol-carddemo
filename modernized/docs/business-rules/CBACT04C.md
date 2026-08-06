# CBACT04C business-rule specification

## Purpose and trigger

**Trigger:** `INTCALC`. monthly interest calculation.

## Inputs and outputs

Reads `ACCTFILE`, `DISCGRP`, `XREFFILE`, `TCATBALF`, and `TRANSACT`; rewrites account/category balances and creates transaction records as coded.

### Exact layouts

`ACCTFILE` uses the 300-byte `CVACT01Y` account record (`ACCT-ID PIC 9(11)`, signed balances/limits/cycle fields `PIC S9(10)V99`, dates `PIC X(10)`, group `PIC X(10)`). `DISCGRP` uses `CVTRA02Y`: group `PIC X(10)`, transaction type `PIC X(02)`, category `PIC 9(04)`, interest rate `PIC S9(04)V99`. `TCATBALF` uses `CVTRA01Y`: account `PIC 9(11)`, type `PIC X(02)`, category `PIC 9(04)`, balance `PIC S9(09)V99`, filler `PIC X(22)`. Transactions use `CVTRA05Y` (`TRAN-AMT PIC S9(09)V99`).

## Validation and error rules

For each account, the source reads the xref by account, reads the account, reads the transaction-category balance, and reads the disclosure-group rate. Missing/failed reads display the corresponding error and abend. The source has no `ROUNDED` phrase in the interest computation.

Source message assignments observed (where present):



Rules are listed in source paragraph order above. Any source behavior not decipherable from declarations is deliberately not guessed.

## Calculations

The exact formula is `WS-MONTHLY-INT = (TRAN-CAT-BAL × DIS-INT-RATE) / 1200`. `TRAN-CAT-BAL` is `PIC S9(09)V99`, `DIS-INT-RATE` is `PIC S9(04)V99`, and `WS-MONTHLY-INT` is source-defined in working storage. There is no `ROUNDED` phrase, so COBOL assignment arithmetic—not JavaScript `number`—determines any truncation/scale effect. Then `WS-TOTAL-INT = WS-TOTAL-INT + WS-MONTHLY-INT`; paragraph `1050-UPDATE-ACCOUNT` adds total interest to `ACCT-CURR-BAL` and resets both current-cycle credit and debit to zero. Paragraph `1400-COMPUTE-FEES` is explicitly “To be implemented” and performs no calculation.

Relevant source excerpt:

```cobol
    PERFORM 1300-COMPUTE-INTEREST
    PERFORM 1400-COMPUTE-FEES
    1300-COMPUTE-INTEREST.
    COMPUTE WS-MONTHLY-INT
    1400-COMPUTE-FEES.
```

## Control flow and failure handling

Batch reject/error paths display file status and abend via `CEE3ABD`; no screen.

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
