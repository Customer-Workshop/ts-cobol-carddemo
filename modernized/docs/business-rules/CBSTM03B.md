# CBSTM03B business-rule specification

## Purpose and trigger

**Trigger:** `CREASTMT`. statement detail subroutine.

## Inputs and outputs

Subroutine called by `CBSTM03A` to read account/customer/transaction/xref inputs and emit statement detail.

### Exact layouts

File-control names are `ACCTFILE`, `CUSTFILE`, `TRNXFILE`, `XREFFILE`. Local FD layouts are: transaction key card `PIC X(16)` + transaction ID `PIC X(16)` with `PIC X(318)` data; xref card `PIC X(16)` + `PIC X(34)` data; customer ID `PIC X(09)` + `PIC X(491)` data; account ID `PIC 9(11)` + `PIC X(289)` data. Linkage is `LK-M03B-AREA` with DD `PIC X(08)`, operation `PIC X(01)`, return code `PIC X(02)`, key `PIC X(25)`, key length `PIC S9(4)`, and data `PIC X(1000)`.

## Validation and error rules

Called by `CBSTM03A` using COBOL linkage; dispatches by DD name and supports open/read/close (and declared keyed/read-write/rewrite operation codes); no standalone JCL trigger or screen.

Source message assignments observed (where present):



Rules are listed in source paragraph order above. Any source behavior not decipherable from declarations is deliberately not guessed.

## Calculations

Formatting and totals must follow the source paragraphs; source does not expose a REST contract.

Relevant source excerpt:

```cobol

```

## Control flow and failure handling

Any read/status error follows the caller’s batch handling; unresolved linkage details are recorded in open questions.

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
