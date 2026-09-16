# S-16 Statement Generation — Stream Functional Requirements (`!mf_stream_fr_generation`)

Status: complete (2026-09-16). Derived from `S16_statements_analysis.md` and
source. Language: English. Process type: BATCH.

## 1. Purpose and scope

Generate customer card statements: for every card cross-reference row, resolve
customer + account, and emit an 80-column text statement (`STATEMNT.PS`) and a
100-column HTML statement (`STATEMNT.HTML`) in parallel — header block
(name/address/account/balance/FICO), transaction summary detail lines, and a
running total. Process type BATCH. Trigger: Control-M/CA-7 statement chain
(CLOSEFIL→CREASTMT→TXT2PDF1→WAITSTEP→OPENFIL). Hard stop: both output files
complete; PDF conversion + wait step are runbook context.

## 2. Actors and preconditions

- Actor: statement-cycle scheduler; ops may rerun CREASTMT standalone.
- Preconditions: transactions loaded (incl. interest txns from S-15);
  `accounts`, `customers`, `card_xrefs` populated; statement output location
  writable. `PARM='12'` on STEP040 is **vestigial** — the program ignores it.

## 3. Job surface specification

| Job | Step | Program/util | Inputs | Outputs | Codes |
|---|---|---|---|---|---|
| CREASTMT | DELDEF01 | IDCAMS | — | TRXFL KSDS defined KEYS(32 0) | COND on later steps |
| CREASTMT | STEP010 | SORT | TRANSACT.BKUP(+1) | TRXFL.SEQ (recut 330B, card+id sort) | feeds STEP020 |
| CREASTMT | STEP020 | REPRO | TRXFL.SEQ | TRXFL KSDS load | COND=(0,NE) |
| CREASTMT | STEP030 | IEFBR14 | — | TRXFL.SEQ deleted | COND=(0,NE) |
| CREASTMT | STEP040 | CBSTM03A PARM='12' | TRXFL/XREFFILE/CUSTFILE/ACCTFILE | STATEMNT.PS 80B; STATEMNT.HTML 100B | RC 0 / abend |
| — | TXT2PDF1 | external ctlcard | statement text | PDF | chain context |

## 4. Functional requirements (KEEP)

| ID | Flow | Business trigger | Observable result | Program(s) | Cite | Boundary | Covering test |
|---|---|---|---|---|---|---|---|
| FR-S16-01 | preload | job start | all TRXFL transactions read into memory keyed (card,txn-order) | CBSTM03A | :818-854 | S16-B1/B2 | TBD |
| FR-S16-02 | sweep | per XREFFILE row | one statement emitted per card — zero-transaction cards included | CBSTM03A | :317-330 | S16-B1 | TBD |
| FR-S16-03 | resolve | per card | CUSTFILE keyed by xref cust-id; ACCTFILE keyed by xref acct-id | CBSTM03B dispatch | CBSTM03A:319-322; CBSTM03B:183-226 | S16-B1 | TBD |
| FR-S16-04 | header | per statement | ST-LINE0 asterisk 'START OF STATEMENT'; name (FIRST+MIDDLE+LAST), addr-1/2/3 (city+state+country+zip) | CBSTM03A | :458-481 | S16-B3 | TBD |
| FR-S16-05 | basic details | per statement | 'Basic Details' block: 'Account ID :', 'Current Balance :' (`9(9).99-`), 'FICO Score :' | CBSTM03A | :483-495; layouts :112-129 | S16-B3 | TBD |
| FR-S16-06 | summary | per statement | 'TRANSACTION SUMMARY' header + column line 'Tran ID / Tran Details / Tran Amount' | CBSTM03A | :130-135; :484-495 | S16-B3 | TBD |
| FR-S16-07 | detail | per transaction for card | ST-LINE14 `id X(16) | details X(49) | $ Z(9).99-` | CBSTM03A | :136-142, :675+ | S16-B3 | TBD |
| FR-S16-08 | total | per statement | 'Total EXP:$Z(9).99-' + 'END OF STATEMENT' frame | CBSTM03A | :143-149, :437-442 | S16-B3 | TBD |
| FR-S16-09 | html | per statement | DOCTYPE/table skeleton + 'Bank of XYZ'/'410 Terry Ave N'/'Seattle WA 99999' + 'Statement for Account Number: <acct>' + Basic Details + Transaction Summary + 'End of Statement' | CBSTM03A | :151-233, :5100/5200/5300 paras | S16-B3 | TBD |
| FR-S16-10 | ordering | file order | statements in XREFFILE order; txns within card in TRXFL order | CBSTM03A | :317-330, :416-432 | S16-B2 | TBD |
| FR-S16-11 | caps | >51 cards or >10 txns/card | **legacy defect**: table overrun, undefined output — do NOT preserve; target streams all rows | CBSTM03A | :248-252, :818-854 | — | TBD |
| FR-S16-12 | IO error | any file op RC ≠ 00/04 | DISPLAY + CEE3ABD → step fails | CBSTM03A | :733-920, :922 | S16-B8 | TBD |
| FR-S16-13 | prep steps | CREASTMT | del/define+sort+repro+delete produce sorted TRXFL | JCL | CREASTMT.JCL | S16-B2 | TBD |
| FR-S16-14 | chain | statement cycle | CREASTMT before TXT2PDF1 + wait + reopen; runbook order | scheduler | controlm; ca7:468-520 | S16-B5/B7 | TBD |
| FR-S16-15 | parm | `PARM='12'` | ignored — no observable effect | CBSTM03A | no USING PARM (verified) | S16-B4 | TBD |

## 5. Validation and error catalogue

| Code/message | Trigger | Cite | Blocking? | Resulting state |
|---|---|---|---|---|
| 'ERROR OPENING/READING/CLOSING <file>' + RC | file op RC ≠ 00/04 | :733-920 pattern | job | abend via CEE3ABD |
| 'ABENDING PROGRAM' | all above | :920-922 | job | step fails |
| 'Running JCL : <job> Step <step>' + 'DD Names from TIOT:' | startup | :265-288 | diagnostic only | — |

## 6. Field and data derivations

- Statement driver is the **xref file** (sequential sweep), not the transaction
  file — statements exist even when the card has no transactions (empty
  summary, total 0).
- `ST-NAME` = `CUST-FIRST-NAME + ' ' + MIDDLE + ' ' + LAST` (STRING delimited
  by space); `ST-ADD3` = `ADDR-LINE-3 + ' ' + STATE + ' ' + COUNTRY + ' ' + ZIP`
  (STRING). Both from `CUSTREC.cpy` (500B layout), **not** CVCUS01Y — the
  customer copybook differs from the online path's; field mapping recorded
  above.
- Total = sum of that card's transaction amounts (`WS-TOTAL-AMT` COMP-3
  S9(9)V99 — truncation semantics per MOVE into `Z(9).99-`).
- TRXFL is a transient pre-sorted extract built inside the job — the target
  equivalent is a repository query sorted by (card, id); no temp store needed.
- HTML literals 'Bank of XYZ', address lines, 'Statement for Account Number:'
  are FACT literals — verbatim in target.

## 7. Mechanics (demoted, cited)

TIOT/PSA/TCB walk + DD-name DISPLAY (:265-288); ALTER-TO state-machine dispatch
(:296-315); M03B call plumbing; 88-level HTML line selectors; array counters.

## 8. Acceptance criteria (Given/When/Then) — one per FR

- FR-S16-01/02: Given xref rows A,B and transactions only on A, When job runs,
  Then two statements emitted — B's has an empty summary and total $0.00.
- FR-S16-03: Given xref cust/acct ids, Then name/address block shows the
  customer record's fields and Basic Details show the account row.
- FR-S16-04/05/06: Given a card, Then header/banner/basic-details/summary
  lines match the ST-LINE layouts byte-for-byte (80 cols).
- FR-S16-07/08: Given 3 txns (10.00, −2.50, 1.00), Then detail lines and
  'Total EXP:$     8.50' (format `Z(9).99-`).
- FR-S16-09: Given a card, Then the HTML block matches the literal skeleton
  with interpolated acct/customer fields.
- FR-S16-10: Given xref order C2 then C1, Then statement file order follows
  xref order (not card sort).
- FR-S16-11: Given >51 cards, Then target emits all statements correctly
  (documented legacy-cap removal).
- FR-S16-12: Given a repository failure, Then step fails.
- FR-S16-13: Given transactions in arbitrary insert order, Then statements
  list them in card+id order.
- FR-S16-14: Given the runbook, Then statement job precedes wait/reopen steps.
- FR-S16-15: Given any parm value, Then output is unchanged (vestigial).

## 9. Traceability matrix

FR-S16-01..13 → CBSTM03A(+CBSTM03B) → `Cbstm03JobConfiguration` +
`BatchJobService.statement*` + `DualStatementWriter` → TBD. FR-S16-14 →
runbook. FR-S16-15 → no-op note.

## 10. Program index

| Program | Role | Requirements | Program FR doc |
|---|---|---|---|
| CBSTM03A | statement writer | FR-S16-01..13,15 | [programs/CBSTM03A_functional_requirement.md](programs/CBSTM03A_functional_requirement.md) |
| CBSTM03B | file-handler subroutine | FR-S16-03 (access seam) | [programs/CBSTM03B_functional_requirement.md](programs/CBSTM03B_functional_requirement.md) |
| CREASTMT | job shell (4 utility steps + STEP040) | FR-S16-13,14 | JCL artifact |

## 11. Open questions and assumptions

1. **PDF**: TXT2PDF1 has no target equivalent — decide at STOP C whether HTML +
   text suffice or a PDF writer joins the job. Assumption A-1 (minimum = PS +
   HTML parity).
2. **Statement bank literals** ('Bank of XYZ' etc.) preserved verbatim even
   though fictional — they are part of the output contract. Assumption A-2.
3. **CUSTREC vs CVCUS01Y**: the statement path uses `CUSTREC.cpy` (500B);
   online paths use `CVCUS01Y.cpy` — both map to `customers`; field dictionary
   keeps them distinct. Assumption A-3 (target maps CUSTREC fields to the same
   columns).
