# CBTRN02C business-rule specification

## Purpose and trigger

`POSTTRAN` runs `CBTRN02C` as a batch transaction poster. It opens the daily input, creates a new transaction master, and updates account/category balances for accepted records.

## Inputs and outputs

- `DALYTRAN` input: 350-byte `CVTRA06Y`; `DALYTRAN-AMT` is signed display `PIC S9(09)V99` (two decimals).
- `TRANFILE` output: 350-byte `CVTRA05Y`; source explicitly uses `OPEN OUTPUT`, therefore the prior transaction master is replaced for this run.
- `XREFFILE` input: 50-byte `CVACT03Y`; `ACCTFILE` and `TCATBALF` are opened `I-O` for updates. `DALYREJS` is fixed 430 bytes: original 350-byte daily transaction followed by an 80-byte reject trailer.

### Exact record layouts

#### `CVTRA05Y`/`CVTRA06Y`
| Offset | Field | PIC / representation | Length |
|---:|---|---|---:|
| 1 | TRAN-ID | `PIC X(16)` display | 16 |
| 17 | TRAN-TYPE-CD | `PIC X(02)` display | 2 |
| 19 | TRAN-CAT-CD | `PIC 9(04)` unsigned display numeric | 4 |
| 23 | TRAN-SOURCE | `PIC X(10)` display | 10 |
| 33 | TRAN-DESC | `PIC X(100)` display | 100 |
| 133 | TRAN-AMT | `PIC S9(09)V99` signed zoned/display, 2 decimals | 11 |
| 144 | TRAN-MERCHANT-ID | `PIC 9(09)` unsigned display numeric | 9 |
| 153 | TRAN-MERCHANT-NAME | `PIC X(50)` display | 50 |
| 203 | TRAN-MERCHANT-CITY | `PIC X(50)` display | 50 |
| 253 | TRAN-MERCHANT-ZIP | `PIC X(10)` display | 10 |
| 263 | TRAN-CARD-NUM | `PIC X(16)` display | 16 |
| 279 | TRAN-ORIG-TS | `PIC X(26)` display | 26 |
| 305 | TRAN-PROC-TS | `PIC X(26)` display | 26 |
| 331 | FILLER | `PIC X(20)` | 20 |

#### `CVACT03Y`
| Offset | Field | PIC / representation | Length |
|---:|---|---|---:|
| 1 | XREF-CARD-NUM | `PIC X(16)` display | 16 |
| 17 | XREF-CUST-ID | `PIC 9(09)` unsigned display numeric | 9 |
| 26 | XREF-ACCT-ID | `PIC 9(11)` unsigned display numeric | 11 |
| 37 | FILLER | `PIC X(14)` | 14 |

#### `CVACT01Y`
| Offset | Field | PIC / representation | Length |
|---:|---|---|---:|
| 1 | ACCT-ID | `PIC 9(11)` unsigned display numeric | 11 |
| 12 | ACCT-ACTIVE-STATUS | `PIC X(01)` display | 1 |
| 13 | ACCT-CURR-BAL | `PIC S9(10)V99` signed zoned/display, 2 decimals | 12 |
| 25 | ACCT-CREDIT-LIMIT | `PIC S9(10)V99` signed zoned/display, 2 decimals | 12 |
| 37 | ACCT-CASH-CREDIT-LIMIT | `PIC S9(10)V99` signed zoned/display, 2 decimals | 12 |
| 49 | ACCT-OPEN-DATE | `PIC X(10)` display `YYYY-MM-DD` | 10 |
| 59 | ACCT-EXPIRAION-DATE | `PIC X(10)` display `YYYY-MM-DD` | 10 |
| 69 | ACCT-REISSUE-DATE | `PIC X(10)` display `YYYY-MM-DD` | 10 |
| 79 | ACCT-CURR-CYC-CREDIT | `PIC S9(10)V99` signed zoned/display, 2 decimals | 12 |
| 91 | ACCT-CURR-CYC-DEBIT | `PIC S9(10)V99` signed zoned/display, 2 decimals | 12 |
| 103 | ACCT-ADDR-ZIP | `PIC X(10)` display | 10 |
| 113 | ACCT-GROUP-ID | `PIC X(10)` display | 10 |
| 123 | FILLER | `PIC X(178)` | 178 |


All numeric fields above are display/zoned unless a source declaration says otherwise; no `COMP-3` is used in these copybook records. `WS-TEMP-BAL` is signed display `PIC S9(09)V99`.

## Validation and error rules (source order)

1. Read the next `DALYTRAN` record. Status `10` means EOF and ends processing; any other non-`00` status displays `ERROR READING DALYTRAN FILE`, displays status, and abends.
2. Lookup `DALYTRAN-CARD-NUM` in `XREFFILE`. Missing key sets reason `100`, exact text `INVALID CARD NUMBER FOUND`; no account lookup or posting follows.
3. Read the xref-derived account ID in `ACCTFILE`. Missing key sets reason `101`, exact text `ACCOUNT RECORD NOT FOUND`; no posting follows.
4. Compute `WS-TEMP-BAL = ACCT-CURR-CYC-CREDIT - ACCT-CURR-CYC-DEBIT + DALYTRAN-AMT`. The source then executes `IF ACCT-CREDIT-LIMIT >= WS-TEMP-BAL`; the true branch continues, while the false branch (equivalently, `ACCT-CREDIT-LIMIT < WS-TEMP-BAL`) sets reason `102`, exact text `OVERLIMIT TRANSACTION`.
5. Compare `ACCT-EXPIRAION-DATE` with `DALYTRAN-ORIG-TS(1:10)`. If the account date is earlier, set reason `103`, exact text `TRANSACTION RECEIVED AFTER ACCT EXPIRATION`.
6. Only reason zero enters posting. A failed record increments `WS-REJECT-COUNT` and writes the original record plus the reason/description trailer to `DALYREJS`; it does not update the master. Accepted records increment the transaction count and enter the post paragraphs.

## Calculations and rounding

The receiving field has two decimal places and the operands also have two decimal places. `COMPUTE` has no `ROUNDED`; COBOL truncates the result toward zero to the receiving item scale. For the source formula, `12.34 - 1.20 + 5.67 = 16.81`, so `WS-TEMP-BAL` is `16.81`. A hypothetical excess fractional intermediate such as `12.345` would be stored as `12.34` in `PIC S9(09)V99`; no cent rounding occurs. The source excerpt is contiguous:

```cobol
MOVE DALYTRAN-CARD-NUM TO FD-XREF-CARD-NUM
READ XREF-FILE INTO CARD-XREF-RECORD
   INVALID KEY
      MOVE 100 TO WS-VALIDATION-FAIL-REASON
      MOVE 'INVALID CARD NUMBER FOUND'
        TO WS-VALIDATION-FAIL-REASON-DESC
END-READ

COMPUTE WS-TEMP-BAL = ACCT-CURR-CYC-CREDIT
                    - ACCT-CURR-CYC-DEBIT
                    + DALYTRAN-AMT
```

## Control flow and failure handling

Files open in order `DALYTRAN` input, `TRANFILE` output, `XREFFILE` input, `DALYREJS` output, `ACCTFILE` I-O, `TCATBALF` I-O. Open/read/write failures display a specific file message and call the abend paragraph (`CEE3ABD`). After EOF all files close. `RETURN-CODE` is `0` unless at least one reject occurred, then it is `4`.

## Test cases

| # | Concrete input | Expected output |
|---:|---|---|
| 1 | Fixture daily record card `0683586198171516`, amount `919.00`, origin date `2022-06-10`; xref maps it to account `00000000027`; account credit limit `613.00` | Reject reason `102`, exact text `OVERLIMIT TRANSACTION`; one 430-byte reject record; no account/transaction write. |
| 2 | Card `0500024453765740` from fixture, account `00000000005`, cycle credit `0.00`, cycle debit `0.00`, amount `0.00`, limit `202.00`, expiration `2025-05-20`, origin `2022-06-10` | Accepted; temporary balance `0.00`; no reject. |
| 3 | Card value `9999999999999999` absent from `CARDXREF` | Reject reason `100`, `INVALID CARD NUMBER FOUND`; no account read/post. |
| 4 | Existing xref points to account `00000000100`, but that account key is absent | Reject reason `101`, `ACCOUNT RECORD NOT FOUND`. |
| 5 | Limit `613.00`, cycle credit `0.00`, cycle debit `0.00`, amount `613.00` | Accepted exactly at limit because the source `IF ACCT-CREDIT-LIMIT >= WS-TEMP-BAL` takes its true branch. |
| 6 | Same account and cycles, amount `613.01` | Reject reason `102`, `OVERLIMIT TRANSACTION`. |
| 7 | Account expiration `2022-06-09`, origin date `2022-06-10`, otherwise valid | Reject reason `103`, `TRANSACTION RECEIVED AFTER ACCT EXPIRATION`. |
| 8 | Input status `10` after the last fixture record | Normal EOF; close files; return code remains `0` if no earlier rejects. |
