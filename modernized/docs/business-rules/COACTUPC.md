# COACTUPC business-rule specification

## Purpose and trigger

**Trigger:** `CAUP`. CICS transaction CAUP.

## Inputs and outputs

Receives account number, active status, credit limit, cash limit and address/account fields on `COACTUP`; updates account data and returns through COMMAREA.

### Exact layouts

Account input/output is `CVACT01Y`; xref/customer structures are `CVACT03Y`/`CVCUS01Y`; numeric limits are source PIC-defined, including signed decimal fields.

## Validation and error rules

Validates numeric input with `TEST-NUMVAL-C`; source error messages are moved into map error fields. CICS `READ UPDATE` then `REWRITE`; NOTFND and other RESP paths redisplay with field cursor.

Source message assignments observed (where present):

- `MOVE 'READ' TO ERROR-OPNAME`
- `MOVE 'READ' TO ERROR-OPNAME`
- `MOVE 'READ' TO ERROR-OPNAME`

Rules are listed in source paragraph order above. Any source behavior not decipherable from declarations is deliberately not guessed.

## Calculations

Credit-limit and cash-limit validation is sequential and source-specific; preserve exact PIC conversion and error text rather than JavaScript number coercion.

Relevant source excerpt:

```cobol
    COMPUTE ACUP-NEW-CREDIT-LIMIT-N =
    COMPUTE ACUP-NEW-CASH-CREDIT-LIMIT-N =
    COMPUTE ACUP-NEW-CURR-BAL-N =
    COMPUTE ACUP-NEW-CURR-CYC-CREDIT-N =
    COMPUTE ACUP-NEW-CURR-CYC-DEBIT-N =
    MOVE WS-FILE-ERROR-MESSAGE      TO WS-RETURN-MSG
    MOVE WS-FILE-ERROR-MESSAGE      TO WS-RETURN-MSG
    MOVE WS-FILE-ERROR-MESSAGE      TO WS-RETURN-MSG
```

## Control flow and failure handling

Enter validates/updates; PF3 returns to prior program, PF4 returns/menu according to COMMAREA; exact PF handling is in `EIBAID` evaluation and must remain pseudo-conversational.

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
