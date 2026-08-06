# COBIL00C business-rule specification

## Purpose and trigger

**Trigger:** `CB00`. CICS transaction CB00.

## Inputs and outputs

Confirms and posts a bill payment against an account.

### Exact layouts

Reads account via `WS-ACCTDAT-FILE`, account/card xref via `WS-CXACAIX-FILE`; writes transaction and rewrites account. Transaction uses `CVTRA05Y`: `TRAN-ID PIC X(16)`, `TRAN-TYPE-CD PIC X(02)`, `TRAN-CAT-CD PIC 9(04)`, `TRAN-SOURCE PIC X(10)`, `TRAN-DESC PIC X(100)`, `TRAN-AMT PIC S9(09)V99`, `TRAN-MERCHANT-ID PIC 9(09)`, merchant name/city/zip `PIC X(50)/X(50)/X(10)`, card `PIC X(16)`, and origin/process timestamps `PIC X(26)`.

## Validation and error rules

Validation order is: account ID must be nonblank; confirmation is interpreted (`Y/y` proceeds, `N/n` clears, blank redisplays `Confirm to make a bill payment...`, any other value displays `Invalid value. Valid values are (Y/N)...`); account is read; current balance is displayed; balance `<= 0` displays `You have nothing to pay...`; only then is xref read and transaction ID generated. On confirmation, transaction amount is current balance, then account balance is reduced by amount; duplicate transaction IDs and CICS NOTFND/other responses redisplay exact messages.

Source message assignments observed (where present):

- `MOVE 'Invalid value. Valid values are (Y/N)...'`
- `MOVE 'BILL PAYMENT - ONLINE' TO TRAN-DESC`
- `MOVE 'BILL PAYMENT' TO TRAN-MERCHANT-NAME`
- `MOVE 'Confirm to make a bill payment...' TO`
- `MOVE 'Account ID NOT found...' TO`
- `MOVE 'Unable to lookup Account...' TO`
- `MOVE 'Account ID NOT found...' TO`
- `MOVE 'Unable to Update Account...' TO`
- `MOVE 'Account ID NOT found...' TO`
- `MOVE 'Unable to lookup XREF AIX file...' TO`
- `MOVE 'Transaction ID NOT found...' TO`
- `MOVE 'Unable to lookup Transaction...' TO`
- `MOVE 'Unable to lookup Transaction...' TO`
- `MOVE 'Tran ID already exist...' TO`
- `MOVE 'Unable to Add Bill pay Transaction...' TO`

Rules are listed in source paragraph order above. Any source behavior not decipherable from declarations is deliberately not guessed.

## Calculations

`ACCT-CURR-BAL = ACCT-CURR-BAL - TRAN-AMT`; `TRAN-AMT` receives the current account balance, so resulting balance is zero. No ROUNDED clause is present in the visible computation. Transaction timestamps are `YYYY-MM-DDHH:MM:SS` plus six zero milliseconds.

Relevant source excerpt:

```cobol
    MOVE SPACES TO WS-MESSAGE
    MOVE CCDA-MSG-INVALID-KEY      TO WS-MESSAGE
    COMPUTE ACCT-CURR-BAL = ACCT-CURR-BAL - TRAN-AMT
    MOVE WS-MESSAGE TO ERRMSGO OF COBIL0AO
    MOVE SPACES             TO WS-MESSAGE
```

## Control flow and failure handling

Enter performs validation/confirmation; PF3 returns to previous COMMAREA program; PF4 clears screen. The success message includes generated transaction ID.

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
