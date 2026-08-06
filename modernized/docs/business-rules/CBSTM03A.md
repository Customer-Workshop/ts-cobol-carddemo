# CBSTM03A business-rule specification

## Purpose and trigger

**Trigger:** `CREASTMT`. statement production.

## Inputs and outputs

Reads account/customer/transaction/xref data through `CBSTM03B`; writes statement and HTML outputs.

### Exact layouts

`CUSTREC` customer record; `CVACT01Y` account; `CVACT03Y` card/account/customer xref; outputs are fixed records `FD-STMTFILE-REC PIC X(80)` and `FD-HTMLFILE-REC PIC X(100)`. Working totals include `WS-TOTAL-AMT PIC S9(9)V99` under `COMP-3`; statement fields include account ID `PIC X(20)`, current balance display `PIC 9(9).99-`, transaction ID `PIC X(16)`, transaction amount display `PIC Z(9).99-`, and a 16-byte saved card key.

## Validation and error rules

Calls `CBSTM03B` using `WS-M03B-AREA`: DD name `PIC X(08)`, operation `PIC X(01)` (`O` open, `C` close, `R` sequential read, `K` keyed read, `W` write, `Z` rewrite), return code `PIC X(02)`, key `PIC X(25)`, key length `PIC S9(4)`, and file-data buffer `PIC X(1000)`. Source has no CICS screen.

Source message assignments observed (where present):



Rules are listed in source paragraph order above. Any source behavior not decipherable from declarations is deliberately not guessed.

## Calculations

Statement totals and formatting are delegated partly to `CBSTM03B`; do not substitute SQL aggregation until field-level source comparison is complete.

Relevant source excerpt:

```cobol
    COMPUTE BUMP-TIOT = BUMP-TIOT + LENGTH OF TIOT-BLOCK.
    COMPUTE BUMP-TIOT = BUMP-TIOT + LENGTH OF TIOT-SEG
    COMPUTE WS-M03B-KEY-LN = LENGTH OF XREF-CUST-ID.
    COMPUTE WS-M03B-KEY-LN = LENGTH OF XREF-ACCT-ID.
```

## Control flow and failure handling

Batch file failures call `CEE3ABD`; no reject-record file is declared in this program.

## Test cases

| # | Concrete input | Expected output/error |
|---:|---|---|
| 1 | Existing customer/account/card with two transactions totaling `25.50` | Writes 80-byte statement lines and 100-byte HTML lines; total is `25.50` in the statement total field. |
| 2 | Missing required key field (blank/LOW-VALUES) | Validation error attached to that field; no data write. |
| 3 | Non-numeric value in a numeric PIC input | Numeric validation error; no data write. |
| 4 | Referenced account/card/xref absent | CICS NOTFND or batch lookup failure; source error message and no partial update. |
| 5 | Duplicate output key (transaction/user/card as applicable) | DUPKEY/DUPREC branch and source error message. |
| 6 | Maximum representable amount for the declared PIC | Accepted only if source numeric validation permits; preserve declared scale and sign. |
| 7 | Negative/zero boundary value where business validation applies | Follow the explicit source comparison; reject when the rule above says so. |
