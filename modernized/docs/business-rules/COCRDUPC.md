# COCRDUPC business-rule specification

## Purpose and trigger

**Trigger:** `CCUP`. CICS transaction CCUP.

## Inputs and outputs

Updates a credit-card record from `COCRDUP`.

### Exact layouts

Card/account/customer layouts are `CVCRD01Y`, `CVACT01Y`, `CVACT03Y`, `CVCUS01Y`; update uses CICS file control.

## Validation and error rules

Upper-case and numeric checks are applied before READ/REWRITE; duplicate/not-found/other CICS responses redisplay errors.

Source message assignments observed (where present):

- `MOVE 'READ' TO ERROR-OPNAME`

Rules are listed in source paragraph order above. Any source behavior not decipherable from declarations is deliberately not guessed.

## Calculations

Card patch endpoint must preserve active-status, CVV, embossed name and expiry field semantics.

Relevant source excerpt:

```cobol
    MOVE WS-FILE-ERROR-MESSAGE         TO WS-RETURN-MSG
```

## Control flow and failure handling

Enter submits; PF3/PF4 navigation is COMMAREA-driven; invalid attention keys show the common invalid-key message.

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
