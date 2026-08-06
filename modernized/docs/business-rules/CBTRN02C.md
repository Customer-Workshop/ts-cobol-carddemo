# CBTRN02C business-rule specification

## Purpose and trigger

**Trigger:** `POSTTRAN`. post daily transaction records.

## Inputs and outputs

Batch input `DALYTRAN`; outputs `TRANFILE` and rejects `DALYREJS`, updates `ACCTFILE` and `TCATBALF`.

### Exact layouts

CVTRA06Y/DALYTRAN-RECORD: `DALYTRAN-ID PIC X(16)`, type `PIC X(02)`, category `PIC 9(04)`, source `PIC X(10)`, description `PIC X(100)`, amount `PIC S9(09)V99`, merchant-id `PIC 9(09)`, merchant-name `PIC X(50)`, city `PIC X(50)`, zip `PIC X(10)`, card `PIC X(16)`, origin/process timestamps `PIC X(26)`. Exact FD framing is 16-byte ID + 334-byte data + 80-byte validation trailer.

## Validation and error rules

Validation is ordered as implemented in `1500-VALIDATE-TRAN`: (1) read `XREF-FILE` by `DALYTRAN-CARD-NUM`; on invalid key set reason `0100`, text `INVALID CARD NUMBER FOUND`; (2) only when that succeeds, read `ACCOUNT-FILE` by the xref account ID; on invalid key set reason `0101`, text `ACCOUNT RECORD NOT FOUND`; (3) calculate the temporary balance; (4) reject when credit limit is below it with reason `0102`, text `OVERLIMIT TRANSACTION`; (5) reject when account expiration date is earlier than the first 10 bytes of the origin timestamp with reason `0103`, text `TRANSACTION RECEIVED AFTER ACCT EXPIRATION`. The source comment says “ADD MORE VALIDATIONS HERE”, but no further validation is implemented. Invalid records increment reject count and are written with a four-digit reason and 76-byte description. On any reject, `RETURN-CODE` is set to 4 after all records; open/read/write/close failures call `CEE3ABD`.

Source message assignments observed (where present):

- `MOVE 'INVALID CARD NUMBER FOUND'`
- `MOVE 'ACCOUNT RECORD NOT FOUND'`
- `MOVE 'ACCOUNT RECORD NOT FOUND'`

Rules are listed in source paragraph order above. Any source behavior not decipherable from declarations is deliberately not guessed.

## Calculations

For an account lookup that succeeds, the source computes `WS-TEMP-BAL` as:

```cobol
COMPUTE WS-TEMP-BAL = ACCT-CURR-CYC-CREDIT
                    - ACCT-CURR-CYC-DEBIT
                    + DALYTRAN-AMT
```

`WS-TEMP-BAL` is `PIC S9(09)V99` and no `ROUNDED` phrase is present, so the intermediate has two decimal places and no additional rounding instruction. A valid record updates the category balance, account record, then writes the transaction; the exact update statements are in paragraphs `2700`, `2800`, and `2900`. Preserve the source’s numeric PICs and do not use binary floating point.

Relevant source excerpt:

```cobol
    COMPUTE WS-TEMP-BAL = ACCT-CURR-CYC-CREDIT
```

## Control flow and failure handling

Batch has no screen/PF behavior. EOF is file status `10`; other read/open/update/write/close statuses set an application result (`12`, with open/read-specific status handling) and abend. Reject handling is explicit; this source opens `TRANSACT-FILE` as `OUTPUT`, not append, which is a migration-critical semantic.

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
