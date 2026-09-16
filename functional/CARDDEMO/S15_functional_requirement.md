# S-15 Interest Calculation — Stream Functional Requirements (`!mf_stream_fr_generation`)

Status: complete (2026-09-16). Derived from `S15_interest_calc_analysis.md` and
source. Language: English. Process type: BATCH.

## 1. Purpose and scope

Monthly interest run: sweep transaction-category balances grouped by account,
look up the disclosure-group interest rate (per account group + tran type +
category, with 'DEFAULT' group fallback), accrue interest = balance × rate / 1200
per category, emit an interest transaction per accruing category, then update the
account (balance += total interest; reset cycle credit/debit to zero). Process
type BATCH. Trigger: Control-M MONTHLY-InterestCalculation chain
(CLOSEFIL→INTCALC→COMBTRAN→WAITSTEP→OPENFIL). Hard stop: interest transactions
persisted (legacy: SYSTRAN GDG merged into TRANSACT) + accounts updated.

## 2. Actors and preconditions

- Actor: the monthly scheduler; ops may rerun INTCALC standalone with a PARM.
- Preconditions: `transaction_category_balances` populated (by S-14 posting);
  `accounts`, `card_xrefs` (with acct-id index), `disclosure_groups` seeded
  (weekly refresh outside this stream); a run date param (legacy PARM
  'yyyymmddhh').

## 3. Job surface specification

| Job | Step | Program | Inputs | Outputs | Codes |
|---|---|---|---|---|---|
| INTCALC | STEP15 | CBACT04C PARM='yyyymmddhh' | TCATBALF, XREFFIL1 (AIX), ACCTFILE, DISCGRP, PARM | SYSTRAN(+1) interest txns; ACCTFILE updates | RC 0 / abend 999 |

## 4. Functional requirements (KEEP)

| ID | Flow | Business trigger | Observable result | Program(s) | Cite | Boundary | Covering test |
|---|---|---|---|---|---|---|---|
| FR-S15-01 | sweep | job start | TCATBALF read sequentially in account order (keys acct/type/cat) | CBACT04C | :188-222 | S15-B1 | TBD |
| FR-S15-02 | acct break | TRANCAT-ACCT-ID changes | previous account updated first (1050-UPDATE-ACCOUNT) then new acct/xref read | CBACT04C | :196-206 | S15-B1/B2 | TBD |
| FR-S15-03 | rate lookup | per tcatbal row | DISCGRP read key (ACCT-GROUP-ID + type + cat) | CBACT04C | :210-213, :415-435 | S15-B1 | TBD |
| FR-S15-04 | fallback | DISCGRP NOTFND (status 23) | retry with group 'DEFAULT' | CBACT04C | :436-444 | S15-B8 | TBD |
| FR-S15-05 | fallback | 'DEFAULT' also absent | error → abend path | CBACT04C | :444-457 | S15-B7 | TBD |
| FR-S15-06 | interest | DIS-INT-RATE ≠ 0 | `WS-MONTHLY-INT = (TRAN-CAT-BAL × DIS-INT-RATE) / 1200`; accumulated into account total | CBACT04C | :463-469 | — | TBD |
| FR-S15-07 | interest | DIS-INT-RATE = 0 | no interest txn written for that category | CBACT04C | :214-216 | — | TBD |
| FR-S15-08 | txn write | per accruing category | TRAN-RECORD: id = `PARM-DATE`(10)+6-digit run suffix; type '01'; cat '05'; source 'System'; desc 'Int. for a/c <acct>'; amt=interest; card=xref card; orig=proc=now | CBACT04C | :473-514 | S15-B3/B4 | TBD |
| FR-S15-09 | acct update | per account end/EOF | `ACCT-CURR-BAL += WS-TOTAL-INT`; `CYC-CREDIT=0`, `CYC-DEBIT=0`; REWRITE | CBACT04C | :343-369 | S15-B1 | TBD |
| FR-S15-10 | EOF | last account | final 1050-UPDATE-ACCOUNT runs for the last group | CBACT04C | :221-223 | — | TBD |
| FR-S15-11 | IO error | any file-status ≠ 00 | DISPLAY + CEE3ABD abend → step fails | CBACT04C | :326-340, :350-369, :455-457 | S15-B7 | TBD |
| FR-S15-12 | fees | DIS-INT-RATE ≠ 0 | 1400-COMPUTE-FEES invoked — **stub, no effect** (do not implement) | CBACT04C | :216, :518-520 | — | TBD |
| FR-S15-13 | merge-back | SYSTRAN(+1) written | COMBTRAN merges into TRANSACT (target: direct insert — no merge step) | COMBTRAN.jcl | full | S15-B3 | TBD |
| FR-S15-14 | chain | monthly chain | INTCALC between CLOSEFIL and COMBTRAN; wait before OPENFIL | scheduler | controlm:64-90 | S15-B5/B6 | TBD |

## 5. Validation and error catalogue

| Code/message | Trigger | Cite | Blocking? | Resulting state |
|---|---|---|---|---|
| abend via CEE3ABD | any file-status error | :326-340, :350-369, :444-457, :502-514 | job | step FAILS, chain stops |
| 'TRY WITH DEFAULT GROUP CODE' | DISCGRP status 23 | :419 | non-blocking | fallback retry |
| 'ACCOUNT NOT FOUND: <id>' then abend | ACCTFILE/XREFFILE INVALID KEY (status 23 ≠ '00' → APPL-RESULT 12 → 9999-ABEND) | :373-374, :395-411 | job | step FAILS |
| 'ERROR READING DEFAULT DISCLOSURE GROUP' | DEFAULT fallback also fails | :455 | job | abend |

## 6. Field and data derivations

- Interest formula: `(TRAN-CAT-BAL × DIS-INT-RATE) / 1200` — annual rate %
  divided by 1200 (percent-to-month). S9(09)V99 arithmetic; rounding per COBOL
  COMPUTE (no ROUNDED clause → truncation at 2 dp). Baseline uses HALF_UP —
  **deviation**: HALF_UP(2) vs COBOL truncation; decide at STOP C (recommend
  matching COBOL truncation for byte-parity).
- TRAN-ID: `PARM-DATE` (10 chars 'yyyymmddhh') + `WS-TRANID-SUFFIX` 9(06)
  counter (:173, :473-480) — deterministic per run (e.g. `2022071800000001`).
- Category balance grouping requires ordered input — the VSAM KSDS key order is
  (acct,type,cat); target reader must sort identically.
- Accounts with no tcatbal rows: never visited → no update, no reset (legacy:
  UPDATE-ACCOUNT only fires on a break or EOF after at least one row).

## 7. Mechanics (demoted, cited)

END-OF-FILE flag; APPL-RESULT status plumbing; DISPLAY trace; EXTERNAL-PARMS
linkage; DB2-format timestamp routine; PARM-LENGTH halfword.

## 8. Acceptance criteria (Given/When/Then) — one per FR

- FR-S15-01/02: Given tcatbal rows for 2 accounts interleaved-but-sorted, When
  job runs, Then each account is updated exactly once, in order.
- FR-S15-03/04: Given group 'G1' missing a (type,cat) rate but 'DEFAULT' has it,
  Then the DEFAULT rate applies.
- FR-S15-05: Given neither group-specific nor DEFAULT rate, Then the step fails.
- FR-S15-05a: Given a tcatbal whose account has no XREF card entry, Then the job
  abends (INVALID KEY → status 23 → CEE3ABD) — missing data is fatal in legacy.
- FR-S15-06: Given bal=1000.00, rate=18.00, Then interest txn amt = 15.00
  (truncated to 2dp — pending the rounding decision §6).
- FR-S15-07: Given rate 0, Then no txn for that category.
- FR-S15-08: Given a run with PARM 2022071800, Then interest ids are
  `2022071800` + 000001..N (per derivation chosen in plan).
- FR-S15-09/10: Given 3 accruing categories totalling T on acct A, Then
  A.bal += T and A.cyc_credit = A.cyc_debit = 0, including the last account.
- FR-S15-11: Given a repository failure, Then step fails (no partial silent run).
- FR-S15-12: Given any run, Then no fee field changes occur.
- FR-S15-13: Given interest txns, Then they appear as normal `transactions` rows
  (type 01, cat 05) queryable by S-07/S-10.
- FR-S15-14: Given the documented monthly order, Then interest runs before the
  reopen step in the runbook.

## 9. Traceability matrix

FR-S15-01..13 → CBACT04C → `Cbact04JobConfiguration` + `AccountInterestReader` +
`BatchJobService.calculateInterest`/`writeInterest` → TBD. FR-S15-14 → runbook.

## 10. Program index

| Program | Role | Requirements | Program FR doc |
|---|---|---|---|
| CBACT04C | interest calculator | FR-S15-01..13 | [programs/CBACT04C_functional_requirement.md](programs/CBACT04C_functional_requirement.md) |
| COBSWAIT | wait step (shared, S-14-owned) | FR-S15-14 | [programs/COBSWAIT_functional_requirement.md](programs/COBSWAIT_functional_requirement.md) |
| INTCALC/COMBTRAN | job shells | FR-S15-13,14 | JCL artifacts |

## 11. Open questions and assumptions

1. **TRAN-ID scheme**: legacy parm-date+counter vs baseline
   `TransactionIdGenerator`. Decide at STOP C — recommend keeping
   `nextId`/`nextIdAfter` (unique, avoids colliding with online-allocated ids)
   but recording the derivation difference. Assumption A-1.
2. **Rounding**: COBOL COMPUTE truncates; baseline HALF_UP. Decide parity or
   document deviation. Assumption A-2.
3. **Card-less/missing account**: legacy **abends** — INVALID KEY sets status
   '23' ≠ '00', APPL-RESULT=12 → CEE3ABD (:395-411, :367-375). Baseline instead
   skips the txn when no card resolves (`BatchJobService.calculateInterest`).
   Deviation D-1: keep skip-or-abend decision at STOP C — recommend treating
   missing xref as a data-integrity failure equivalent to the abend (fail the
   step), since legacy is loud here. Assumption A-3.
4. Accounts untouched by tcatbal keep their cycle figures — monthly reset is
   *only* for accounts with category balances (source-faithful).
