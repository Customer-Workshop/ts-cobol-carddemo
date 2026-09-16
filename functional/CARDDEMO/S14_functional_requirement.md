# S-14 Daily Posting — Stream Functional Requirements (`!mf_stream_fr_generation`)

Status: complete (2026-09-16). Derived from `S14_daily_posting_analysis.md` and
source. Language: English. Process type: BATCH.

## 1. Purpose and scope

Post the day's captured card transactions into the account store: validate each
daily-transaction record (card→xref→account, credit limit, expiry), write valid
transactions to the transaction store, update transaction-category balances and
account balances/cycle totals, and emit a reject file for the rest. Then run the
settle-time wait step before online files reopen. Process type BATCH. Triggers:
scheduler daily chain (CLOSEFIL→POSTTRAN→TRANBKP→WAITSTEP→OPENFIL per the merged
controlm+ca7 view — see analysis §1 for the two-source discrepancy). Hard stop:
DALYREJS written + TRANFILE/ACCTFILE/TCATBALF committed + WAITSTEP elapsed.
Exclusions: DALYTRAN producers, CLOSEFIL/OPENFIL internals, TRANBKP housekeeping,
CBPAUP0J.

## 2. Actors and preconditions

- Actor: the batch scheduler (no human); ops can rerun POSTTRAN standalone.
- Preconditions: DALYTRAN feed present (may be empty); accounts/xrefs/category
  balances populated; TRANSACT writable.

## 3. Job surface specification

| Job | Step | Program | Inputs | Outputs | Codes |
|---|---|---|---|---|---|
| POSTTRAN | STEP15 | CBTRN02C | DALYTRAN PS; XREFFILE, ACCTFILE, TCATBALF KSDS | TRANFILE write; ACCTFILE/TCATBALF updates; DALYREJS(+1) 430B | RC 0, RC 4 (rejects>0, :229-231); abend 999 on IO errors |
| WAITSTEP | WAIT | COBSWAIT→MVSWAIT | SYSIN 00003600 (36 s) | none | RC 0 |

## 4. Functional requirements (KEEP)

| ID | Flow | Business trigger | Observable result | Program(s) | Cite | Boundary | Covering test |
|---|---|---|---|---|---|---|---|
| FR-S14-01 | validate | DALYTRAN rec, card not in XREFFILE | reject rec (350B + trailer 0100 'INVALID CARD NUMBER FOUND') → DALYREJS; no writes | CBTRN02C | :380-392, :446-465 | S14-B2/B3 | TBD |
| FR-S14-02 | validate | card ok, acct not in ACCTFILE | reject 0101 'ACCOUNT RECORD NOT FOUND' | CBTRN02C | :394-401 | S14-B2 | TBD |
| FR-S14-03 | validate | `ACCT-CURR-CYC-CREDIT − CYC-DEBIT + AMT > ACCT-CREDIT-LIMIT` | reject 0102 'OVERLIMIT TRANSACTION' | CBTRN02C | :403-412 | S14-B2 | TBD |
| FR-S14-04 | validate | `ACCT-EXPIRAION-DATE < DALYTRAN-ORIG-TS(1:10)` | reject 0103 'TRANSACTION RECEIVED AFTER ACCT EXPIRATION' | CBTRN02C | :414-419 | S14-B2 | TBD |
| FR-S14-05 | post | all validations pass | TRAN-RECORD written: all DALYTRAN fields moved, PROC-TS = current DB2-format timestamp (:692-705) | CBTRN02C | :424-442 | S14-B2 | TBD |
| FR-S14-06 | post | TCATBAL rec exists for (acct,type,cat) | `ADD DALYTRAN-AMT TO TRAN-CAT-BAL` + REWRITE | CBTRN02C | :527-528 | S14-B2 | TBD |
| FR-S14-07 | post | TCATBAL rec absent | build TRAN-CAT-BAL-RECORD (key + amt) + WRITE | CBTRN02C | :467-526 | S14-B2 | TBD |
| FR-S14-08 | post | account update | `ADD AMT TO ACCT-CURR-BAL`; amt≥0 → ADD to CURR-CYC-CREDIT; amt<0 → ADD to CURR-CYC-DEBIT; REWRITE | CBTRN02C | :545-554 | S14-B2 | TBD |
| FR-S14-09 | post failure | ACCTFILE REWRITE invalid key | reject 0109 'UNABLE TO UPDATE ACCOUNT' appended to that record | CBTRN02C | :554-559 | S14-B2 | TBD |
| FR-S14-10 | run end | any rejects | `TRANSACTIONS REJECTED :<n>` displayed; **job RC = 4** | CBTRN02C | :228-231 | S14-B5 | TBD |
| FR-S14-11 | run end | zero rejects | RC = 0 | CBTRN02C | :229 | S14-B5 | TBD |
| FR-S14-12 | IO error | any file open/read/write status ≠ 00 | DISPLAY IO-STATUS + CEE3ABD abend → step failed | CBTRN02C | :249-268, abend para | S14-B5 | TBD |
| FR-S14-13 | reject file | per reject | record = 350B original + 80B trailer (`9(04)` reason + `X(76)` desc) = 430B | CBTRN02C | :180-182, :446-465 | S14-B3 | TBD |
| FR-S14-14 | wait step | WAITSTEP runs | process sleeps SYSIN centiseconds (`00003600` = 36.00 s) then RC 0 | COBSWAIT | :36-38; WAITSTEP.jcl:7-8 | S14-B4 | TBD |
| FR-S14-15 | chain | daily chain | POSTTRAN after CLOSEFIL (close online VSAM); WAITSTEP before OPENFIL — order preserved | scheduler | controlm:3-24; ca7:69-124 | S14-B1/B6 | TBD |
| FR-S14-16 | orphan utility | cbtrn01-style run | per rec: verify card→xref→acct, write diagnostic lines only; **no updates** | CBTRN01C | :156-230 | S14-B8 | TBD |
| FR-S14-17 | idempotency | POSTTRAN rerun on same input | transactions/dalycat updates re-apply (legacy semantics: additive — rerun double-posts; runbook marks rerun as fix-forward) | CBTRN02C | :202-219 | S14-B1 | TBD |
| FR-S14-18 | empty input | empty DALYTRAN | no writes, 0 rejects, RC 0 | CBTRN02C | :202-231 | S14-B7 | TBD |

## 5. Validation and error catalogue

| Code/message | Trigger | Cite | Blocking? | Resulting state |
|---|---|---|---|---|
| 0100 'INVALID CARD NUMBER FOUND' | XREF NOTFND | :385-388 | per-record | → DALYREJS |
| 0101 'ACCOUNT RECORD NOT FOUND' | ACCT NOTFND | :397-399 | per-record | → DALYREJS |
| 0102 'OVERLIMIT TRANSACTION' | projected cyc > limit | :410-412 | per-record | → DALYREJS |
| 0103 'TRANSACTION RECEIVED AFTER ACCT EXPIRATION' | orig-date > expiry | :417-419 | per-record | → DALYREJS |
| 0109 'UNABLE TO UPDATE ACCOUNT' | REWRITE invalid key | :556-558 | per-record | → DALYREJS |
| abend 999 via CEE3ABD | any file-status error | :249-268 | job | step FAILS, chain stops |
| RC 4 | rejects > 0 | :229-231 | non-blocking | chain continues (no COND gating on POSTTRAN) |

## 6. Field and data derivations

- TRAN-RECORD fields copied verbatim from DALYTRAN except TRAN-PROC-TS =
  `Z-GET-DB2-FORMAT-TIMESTAMP` (current, `YYYY-MM-DD-HH.MM.SS.SSSSSS` —
  CBTRN02C.cbl:692-705).
- Account updates are additive; `ACCT-CURR-CYC-CREDIT` only grows on positive
  (credit) amounts and `CURR-CYC-DEBIT` on negative — reset monthly by S-15's
  INTCALC (:CBACT04C 1050-UPDATE-ACCOUNT zeroing).
- Overlimit test uses the **projected** cycle net (`CREDIT − DEBIT + AMT`), not
  current balance — a reject guard distinct from ACCT-CURR-BAL.
- Reject file preserves the full input record (350B) + appended trailer (80B).

## 7. Mechanics (demoted, cited)

Sequential-read AT END loop (:202-219); VSAM status-code dispatch tables;
CEE3ABD abend wrapper; DISPLAY counters; SYSIN ACCEPT; MVSWAIT binary
centisecond parm.

## 8. Acceptance criteria (Given/When/Then) — one per FR

- FR-S14-01: Given a tran with unknown card, When POSTTRAN runs, Then reject
  0100 in rejects file; no transaction/account/balance rows written.
- FR-S14-02: Given card→acct XREF but missing account, Then reject 0101.
- FR-S14-03: Given projection over limit, Then reject 0102.
- FR-S14-04: Given orig date after expiry, Then reject 0103.
- FR-S14-05: Given a valid tran, Then a `transactions` row exists with all fields
  preserved and proc timestamp = run time.
- FR-S14-06/07: Given valid tran for existing/absent cat-bal key, Then the row is
  incremented / created with the amount.
- FR-S14-08: Given valid credit tran A and debit tran −D, Then acct bal += A, −D
  and cyc_credit += A, cyc_debit += −D respectively (sign-aware).
- FR-S14-09: Given account row deleted mid-run, Then reject 0109 recorded.
- FR-S14-10/11: Given N>0 / 0 rejects, Then job exit = 4 / 0 (mapped to
  JobExecution exit status).
- FR-S14-12: Given a read failure, Then the step fails (no silent skip).
- FR-S14-13: Given rejects, Then each reject line is 430 bytes: input record +
  reason + description.
- FR-S14-14: Given wait step with 3600 cs, Then step completes after ~36 s.
- FR-S14-15: Given the documented daily order, Then posting precedes the wait and
  file-reopen steps (ops runbook).
- FR-S14-16: Given the validation job over the same feed, Then diagnostics only,
  zero writes.
- FR-S14-17: Given a second run over the same feed, Then balances double-post
  (source-faithful: no idempotency key exists).
- FR-S14-18: Given empty feed, Then RC 0, no rejects.

## 9. Traceability matrix

FR-S14-01..13,17,18 → CBTRN02C → `Cbtrn02JobConfiguration` +
`BatchJobService.postDaily`/`validate` → TBD. FR-S14-14 → COBSWAIT → wait
tasklet → TBD. FR-S14-15 → runbook. FR-S14-16 → CBTRN01C → `cbtrn01Job`.

## 10. Program index

| Program | Role | Requirements | Program FR doc |
|---|---|---|---|
| CBTRN02C | posting program | FR-S14-01..13,17,18 | [programs/CBTRN02C_functional_requirement.md](programs/CBTRN02C_functional_requirement.md) |
| COBSWAIT | wait-step driver | FR-S14-14 | [programs/COBSWAIT_functional_requirement.md](programs/COBSWAIT_functional_requirement.md) |
| CBTRN01C | orphan validator | FR-S14-16 | [programs/CBTRN01C_functional_requirement.md](programs/CBTRN01C_functional_requirement.md) |

## 11. Open questions and assumptions

1. **Chain discrepancy** (Control-M vs CA-7, analysis §1): target documents a
   single daily order `POSTTRAN → (backup) → wait → reopen` — assumes the CA-7
   view plus the Control-M backup step. Assumption A-1.
2. **Idempotency**: legacy re-runs double-post; no dedupe key. Kept faithful —
   rerun is an ops action. Assumption A-2.
3. **RC=4 → job exit**: baseline maps rejects to JobExecution without failing the
   job; confirm the admin-launch response surfaces the reject count. Assumption A-3.
