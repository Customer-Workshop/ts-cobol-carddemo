# Monthly interest chain — ops runbook (target)

Replaces the Control-M `MONTHLY-InterestCalculation` folder
(`app/scheduler/CardDemo.controlm:64-90`):
`CLOSEFIL → INTCALC → COMBTRAN → WAITSTEP → OPENFIL`.
INTCALC is `//STEP15 EXEC PGM=CBACT04C,PARM='2022071800'`
(`app/jcl/INTCALC.jcl:3`) — the monthly interest calculator over
transaction-category balances.

## Documented job order (S15-B5)

| Order | Legacy step | Target job | Launch |
|---|---|---|---|
| 1 | CLOSEFIL — close online VSAM files | — | **No-op in target** (Postgres is always on). The order matters instead: run interest after the online cutover window, not concurrently with it. |
| 2 | INTCALC (`STEP15 EXEC PGM=CBACT04C,PARM='yyyymmddhh'`) | `cbact04Job` | `POST /api/admin/jobs/cbact04Job?parmDate=yyyymmddhh` |
| 3 | COMBTRAN — SORT SYSTRAN(0)+BKUP(0) → COMBINED(+1), REPRO → TRANSACT | — | **Eliminated** (S15-B3): interest transactions insert directly into `transactions`; there is no GDG merge step. |
| 4 | WAITSTEP (`EXEC PGM=COBSWAIT`, SYSIN `00003600`) | `waitStepJob` | `POST /api/admin/jobs/waitStepJob` (+ optional `waitCentiseconds`; default `3600` = 36.00 s, matching the committed SYSIN card) |
| 5 | OPENFIL — reopen online files | — | **No-op in target.** |

The same order is also kept machine-readable in code as
`BatchJobService.MONTHLY_INTEREST_CHAIN` = `[cbact04Job, waitStepJob]`
(in-scope steps only; the close/open bracket does not exist in target).

## `parmDate` contract (S15-B4)

Required. Format `yyyymmddhh` — 10 digits, month 01-12, day 01-31, hour 00-23 —
matching the JCL PARM `PARM-DATE X(10)` (CBACT04C.cbl:176-180). It seeds the
interest TRAN-ID prefix: `TRAN-ID = parmDate + 6-digit run suffix`
(`2022071800` → `2022071800000001`, `…000002`, …). A missing or malformed
`parmDate` is rejected at launch with HTTP 400 before the job runs — the
equivalent of a JES PARM error.

## Exit-code contract (COBOL RC → job outcome)

| Legacy | Target | Meaning |
|---|---|---|
| RC 0 | `COMPLETED` | Clean sweep: every tcatbal group resolved account, xref, and a rate. |
| Abend 999 via CEE3ABD | `FAILED` + exit code `999` | Data-integrity failure; the chain stops. Legacy DISPLAY diagnostics are carried in the failure message (see below). |
| JCL PARM error | HTTP 400 on launch | `parmDate` missing or malformed — the job never runs. |

Keyed-read misses abend rather than skip (S15-B2/B7/B8, STOP C decision):

- `ACCOUNT NOT FOUND: <acct> - ACCTFILE read status 23` — a tcatbal group has
  no `accounts` row (`1100-GET-ACCT-DATA`, CBACT04C.cbl:372-391).
- `ACCOUNT NOT FOUND: <acct> - XREFFIL1 read status 23` — no `card_xrefs` row
  resolves a card for the account (`1110-GET-XREF-DATA`, :393-413).
- `ERROR READING DEFAULT DISCLOSURE GROUP - …` — neither the account's group
  nor `DEFAULT` holds a rate for (type, category) (`1200-A`, :443-460).

Fix the data and relaunch; the step does not continue past the failure.

## File contract

| Legacy DD | Target |
|---|---|
| `TCATBALF` (KSDS seq, key acct/type/cat) | `transaction_category_balances` ordered reader — same key order |
| `XREFFIL1` (CARDXREF AIX path, keyed by acct) | `card_xrefs` via `findByXrefAcctId` + `idx_card_xrefs_acct_id` (V2201) |
| `ACCTFILE` (KSDS keyed I-O) | `accounts` read + update |
| `DISCGRP` (KSDS keyed) | `disclosure_groups` keyed read + `'DEFAULT'` fallback |
| `TRANSACT` = `SYSTRAN(+1)` GDG 350B | `transactions` direct insert (type `01`, cat `05`, source `System`) |
| `PARM='yyyymmddhh'` | `parmDate` job parameter |

## Rerun / restart semantics

- Rerunning INTCALC with the same `parmDate` is **additive and non-idempotent**
  (source-faithful): interest posts again into `acct_curr_bal`. The interest
  TRAN-IDs are identical per run (`parmDate` + suffix restarting at 1), so the
  `transactions` rows are overwritten rather than duplicated — legacy abends
  on the duplicate KSDS write; the target completing is a documented,
  strictly-safer deviation. Treat rerun as an ops action (fix-forward).
- Accounts with no tcatbal rows are untouched — no balance change, no cycle
  reset (the legacy UPDATE-ACCOUNT only fires on a break or EOF after at
  least one record).
- Interest math truncates at 2dp (`RoundingMode.DOWN`), matching COBOL
  COMPUTE without ROUNDED — not HALF_UP.
- Categories with `DIS-INT-RATE = 0` write nothing, and the account is still
  updated once per group (balance += other categories' interest, cycle
  credit/debit reset to 0).
- `1400-COMPUTE-FEES` is a legacy dead stub ('To be implemented',
  CBACT04C.cbl:518-520) — deliberately not implemented.
