# Business Rule Extraction (BRE) — CBTRN02C (Daily Transaction Posting)

_Application: AWS CardDemo · Program: `CBTRN02C.CBL` · Type: Batch COBOL · Source: `app/cbl/CBTRN02C.cbl` (731 LOC)_

## 1. Purpose

`CBTRN02C` is the core nightly **transaction posting** engine. It reads the daily
transaction feed, validates each transaction against the card cross-reference and
account master, posts valid transactions (updating category balances, account
balances, and the transaction master), and writes rejects with a reason code.

## 2. Data interfaces

| Logical file | DD name | Organization | Copybook | Access | Role |
| --- | --- | --- | --- | --- | --- |
| DALYTRAN-FILE | `DALYTRAN` | SEQUENTIAL | CVTRA06Y | Read | Input daily transaction feed |
| XREF-FILE | `XREFFILE` | INDEXED (KSDS) | CVACT03Y | Read | Card → Account cross-reference |
| ACCOUNT-FILE | `ACCTFILE` | INDEXED (KSDS) | CVACT01Y | Read + Update | Account master (balances, limits) |
| TCATBAL-FILE | `TCATBALF` | INDEXED (KSDS) | CVTRA01Y | Read + Create + Update | Transaction category balances |
| TRANSACT-FILE | `TRANFILE` | INDEXED (KSDS) | CVTRA05Y | Create | Posted transaction master |
| DALYREJS-FILE | `DALYREJS` | SEQUENTIAL | — | Create | Rejected transactions + trailer |

## 3. Process flow

```
OPEN all files
PERFORM UNTIL end-of-daily-file
    READ next daily transaction               (1000-DALYTRAN-GET-NEXT)
    IF read succeeded
        transaction-count += 1
        VALIDATE transaction                  (1500-VALIDATE-TRAN)
        IF validation passed  -> POST         (2000-POST-TRANSACTION)
        ELSE                  -> reject-count += 1, WRITE reject (2500)
CLOSE all files
IF reject-count > 0 -> set RETURN-CODE = 4
```

## 4. Extracted business rules

| Rule ID | Rule (plain English) | Source | COBOL logic | Reject code |
| --- | --- | --- | --- | --- |
| BR-01 | The card number on every transaction **must exist** in the card cross-reference. | `1500-A-LOOKUP-XREF` (ln 380-392) | `READ XREF-FILE` … `INVALID KEY` | **100** — INVALID CARD NUMBER FOUND |
| BR-02 | The account referenced by the card **must exist** in the account master. | `1500-B-LOOKUP-ACCT` (ln 393-399) | `READ ACCOUNT-FILE` … `INVALID KEY` | **101** — ACCOUNT RECORD NOT FOUND |
| BR-03 | A transaction is rejected if it would exceed the account credit limit. Available credit is `credit-limit ≥ (cycle-credit − cycle-debit + tran-amount)`. | ln 403-413 | `COMPUTE WS-TEMP-BAL = ACCT-CURR-CYC-CREDIT − ACCT-CURR-CYC-DEBIT + DALYTRAN-AMT` then `IF ACCT-CREDIT-LIMIT >= WS-TEMP-BAL` | **102** — OVERLIMIT TRANSACTION |
| BR-04 | A transaction is rejected if received **after** the account expiration date (compares first 10 chars of the origination timestamp to the account expiry date). | ln 414-420 | `IF ACCT-EXPIRAION-DATE >= DALYTRAN-ORIG-TS (1:10)` | **103** — TRANSACTION RECEIVED AFTER ACCT EXPIRATION |
| BR-05 | On posting, the transaction-category balance is **created if absent** (key = account + type + category), otherwise **incremented** by the transaction amount. | `2700-UPDATE-TCATBAL` (ln 467-528) | `READ TCATBAL-FILE` INVALID KEY ⇒ create (`WRITE`), else `ADD DALYTRAN-AMT TO TRAN-CAT-BAL` + `REWRITE` | — |
| BR-06 | Posting updates the account current balance by the transaction amount; **positive** amounts add to cycle credit, **negative** amounts add to cycle debit. | `2800-UPDATE-ACCOUNT-REC` (ln 545-559) | `ADD DALYTRAN-AMT TO ACCT-CURR-BAL`; `IF DALYTRAN-AMT >= 0 … ACCT-CURR-CYC-CREDIT ELSE … ACCT-CURR-CYC-DEBIT` | — (109 if account vanished) |
| BR-07 | Every posted transaction is written to the transaction master with a DB2-format processing timestamp. | `2000-POST-TRANSACTION` / `2900` (ln 424-444, 562-579) | `MOVE DB2-FORMAT-TS TO TRAN-PROC-TS`; `WRITE FD-TRANFILE-REC` | — |
| BR-08 | Rejected transactions are written to the reject file with a 4-digit reason code and 76-char description trailer; the job ends with **RC=4** if any rejects occurred. | `2500-WRITE-REJECT-REC` (ln 446-465) + ln 229-231 | `WRITE FD-REJS-RECORD FROM REJECT-RECORD`; `IF WS-REJECT-COUNT > 0 MOVE 4 TO RETURN-CODE` | — |
| BR-09 | Any I/O status other than success (`'00'`, or `'23'`/not-found where expected) triggers an **abend** (CEE3ABD) after logging the file status. | `9910` / `9999` (throughout) | `PERFORM 9999-ABEND-PROGRAM` → `CALL 'CEE3ABD'` | — |

## 5. Reject reason-code catalog

| Code | Meaning | Triggered by |
| --- | --- | --- |
| 100 | Invalid card number | BR-01 |
| 101 | Account record not found | BR-02 |
| 102 | Overlimit transaction | BR-03 |
| 103 | Transaction after account expiration | BR-04 |
| 109 | Account not found on balance update | BR-06 (rewrite INVALID KEY) |

## 6. Modernization notes

- **Validation** (BR-01…BR-04) is a natural fit for a stateless rules service; the
  reason-code catalog maps directly to structured error responses.
- **Posting** (BR-05…BR-07) is a single logical unit of work across three stores —
  in a relational/target design this becomes one ACID transaction (category balance
  upsert, account update, transaction insert).
- **Balance semantics** (BR-06) encode sign conventions (credit vs debit cycles) that
  must be preserved exactly to reconcile with the legacy general ledger.
- The comment `* ADD MORE VALIDATIONS HERE` (ln 377) marks the documented extension
  point for new rules.

_This BRE was produced from static analysis of the COBOL source; line references are to `app/cbl/CBTRN02C.cbl`._
