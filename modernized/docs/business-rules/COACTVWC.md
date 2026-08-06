# COACTVWC business-rule specification

## Purpose and trigger

**Trigger:** `CAVW`. CICS transaction CAVW.

## Inputs and outputs

Displays account, customer and linked card information.

### Exact layouts

Reads account (`CVACT01Y`), customer (`CVCUS01Y`), card/xref (`CVACT03Y`/`CVCRD01Y`) through CICS datasets.

## Validation and error rules

READ sequence is account → xref/customer/card; NOTFND and other RESP branches set map messages.

Source message assignments observed (where present):

- `MOVE 'READ' TO ERROR-OPNAME`
- `MOVE 'READ' TO ERROR-OPNAME`
- `MOVE 'READ' TO ERROR-OPNAME`

Rules are listed in source paragraph order above. Any source behavior not decipherable from declarations is deliberately not guessed.

## Calculations

No update calculation; preserve source formatting of balances and dates.

Relevant source excerpt:

```cobol
    MOVE WS-FILE-ERROR-MESSAGE      TO WS-RETURN-MSG
    MOVE WS-FILE-ERROR-MESSAGE      TO WS-RETURN-MSG
    MOVE WS-FILE-ERROR-MESSAGE      TO WS-RETURN-MSG
```

## Control flow and failure handling

Enter/PF navigation uses COMMAREA context; PF3 returns to prior screen and other invalid keys use common error message.

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
