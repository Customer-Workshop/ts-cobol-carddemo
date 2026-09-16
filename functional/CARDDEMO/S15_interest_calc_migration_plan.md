# S-15 Interest Calculation — Migration Plan (`!mf_stream_migration_plan`)

Status: DRAFT for STOP C approval (2026-09-16, Java engagement). Inputs:
`S15_interest_calc_analysis.md`, `S15_functional_requirement.md`,
`CardDemo_target_state.md` (CORE + BATCH + DATA/BOUNDARY), baseline =
`spring-boot/` on the engagement branch. Reserved Flyway range: **V220x**.

## 1. Goal and scope

Migrate the monthly interest calculation — INTCALC (CBACT04C PARM='yyyymmddhh')
with the COMBTRAN merge-back — to Java 21 + Spring Boot batch. Definition of
done: FR-S15-01..14 pass. Hard stop: interest transactions in `transactions` +
account balances updated + cycle totals reset; the surrounding CLOSEFIL/OPENFIL
bracket and WAITSTEP are runbook order only. Process type: BATCH.

**Baseline position (FACT, verified this engagement):** `cbact04Job` exists —
`AccountInterestReader` (all tcatbal rows sorted acct/type/cat, grouped into
per-account `InterestWork`), `calculateInterest` (disclosure lookup +
'DEFAULT' fallback, `bal × rate / 1200` HALF_UP 2dp, builds type-01/cat-05
'System'/'Int. for a/c' interest `Transaction`s, timestamps now), `writeInterest`
(bal += totalInterest, cyc credit/debit := 0, saves account + txns) in
`Cbact04JobConfiguration`/`BatchJobService`. **Gaps/deviations:** (a) TRAN-ID
from `TransactionIdGenerator.nextId()` — not legacy `parmDate`+counter;
(b) no `parmDate` job parameter at all; (c) missing xref/acct → txn silently
skipped, legacy **abends** (INVALID KEY → status 23 → CEE3ABD); (d) rounding
HALF_UP vs COBOL truncation; (e) no documented monthly job-order runbook.

## 2. Target-state mapping

Profiles applied: CORE + BATCH + DATA/BOUNDARY.

- Job: `cbact04Job` stays; add `parmDate` JobParameter (format yyyymmddhh,
  validated); interest txn id = `parmDate` + zero-padded 6-digit run counter —
  restores the legacy derivation (`CBACT04C.cbl:473-480`) with the generator as
  fallback when parm absent.
- Failure semantics: missing xref card or missing account → step fails
  (data-integrity failure), matching the legacy abend — replaces the baseline
  skip.
- Rounding: match COBOL COMPUTE truncation (down, 2dp) — `scaleByPowerOfTen`/
  `setScale(2, RoundingMode.DOWN)` after the multiply-then-divide order.
- Chain: COMBTRAN eliminated (direct `transactions` insert); documented monthly
  order `close → cbact04Job → waitStep → reopen`.
- Persistence: VSAM→Postgres JPA; SYSTRAN GDG→direct table write (B-010);
  XREFFIL1 AIX → `findByXrefAcctId` index read (B-009).

## 3. Boundary decision table (decide mode over S15-B1..B8)

| ID | Class | Decision | Seam in target | Error/idempotency | Owner | Lead-time | Routing point / cutover |
|---|---|---|---|---|---|---|---|
| S15-B1 | B-009 VSAM | **DECIDED:** `TransactionCategoryBalanceRepository` (ordered read), `AccountRepository` (read+update), `DisclosureGroupRepository` (keyed read) | `repository/*` | repository errors → step FAILED | S-15 | none | — |
| S15-B2 | B-009 AIX | **DECIDED:** `findByXrefAcctId` on `card_xrefs.xref_acct_id` index | `CardXrefRepository` | empty → fail step (legacy abend) | S-15 | none | — |
| S15-B3 | B-010 GDG | **DECIDED:** write `transactions` directly; no SYSTRAN/COMBTRAN | `transactionRepository.save` | chunk-transactional | S-15 | none | legacy COMBTRAN obsolete at sign-off |
| S15-B4 | B-008 params | **DECIDED:** `parmDate` JobParameter yyyymmddhh drives TRAN-ID prefix | param validator + id builder | missing/invalid → job reject before run | S-15 | none | — |
| S15-B5 | B-008 chain | **DECIDED:** documented monthly order + exit codes in runbook | `docs/runbook` + admin launch | non-idempotent rerun (legacy additive: re-running adds interest again — faithful) | S-15 | none | — |
| S15-B6 | B-002 wait | **DECIDED:** consume S-14 `WaitStepTasklet` | existing tasklet | — | S-14 (owner) | none | already landed with S-14 |
| S15-B7 | B-004 errors | **DECIDED:** exception → step FAILED → non-zero exit | Spring Batch | DISPLAY diagnostics → log | S-15 | none | — |
| S15-B8 | B10 shared data | **DECIDED:** `disclosure_groups` seeded by data migration (V2xx) or weekly-refresh stream; 'DEFAULT' fallback preserved | repository read | missing DEFAULT → step failure (legacy abend) | S-15 | none | — |

No stored-procedure boundaries; no external lead times — recorded explicitly.

## 4. Data and persistence

- Tables touched: read `transaction_category_balances`, `accounts`,
  `card_xrefs`, `disclosure_groups`; write `accounts` (bal, cyc), `transactions`
  (insert interest rows).
- V220x reserved: optional `interest_run_log` audit table if desired; not
  required for parity. Index on `card_xrefs.xref_acct_id` if not already
  present — check `V2__baseline_domain_tables.sql` (likely covered by unique
  index); add `V2201__xref_acct_idx.sql` only if missing.

## 5. Phase 0 deltas

None.

## 6. Waves

| Wave | Programs/seams | Repo area | Consumes FR docs | Boundary seams | Strict edge to next |
|---|---|---|---|---|---|
| 1 | `cbact04Job` parity closure: `parmDate` param + legacy TRAN-ID, missing-xref/acct → failure, truncation rounding, runbook for monthly order; FR-S15 test matrix | spring-boot/ | CBACT04C program FR + stream FR | S15-B1..B8 | — |

## 7. Per-program FR generation

- `programs/CBACT04C_functional_requirement.md` — new (this doc set).
- `programs/COBSWAIT_functional_requirement.md` — exists (S-14); consume.
- `INTCALC`/`COMBTRAN` are JCL shells — inline in stream FR.

## 8. Testing and verification

- Unit: formula edge cases (rate 0 → no txn; truncation vs rounding cell);
  DEFAULT fallback; missing-xref → step failure; TRAN-ID = parmDate+suffix;
  multi-account ordering incl. last-account update at EOF.
- Integration: `cbact04Job` over seeded tcatbal/accounts → account deltas +
  interest txn rows; monthly chain order via admin endpoint; rerun behavior
  documented (additive, non-idempotent — same as legacy).

## 9. Sign-off gate

`!mf_stream_signoff` runs FR-S15 §8 one-by-one; independent audit; STOP E.

## 10. Risks

1. Rounding decision (truncate vs HALF_UP) changes cents — must be called at
   STOP C, default truncate-for-parity. MEDIUM-low.
2. Missing-xref semantics flip baseline from skip to fail — a data-cleanliness
   question for seeded fixtures. LOW.
3. `parmDate`-based ids can collide across runs with the same parm — legacy had
   the same property (suffix restarts at 1); document, don't "fix". LOW.

## 11. Effort and sequencing

1 wave child after STOP C, ~1 session. Depends on S-14's WaitStepTasklet
(S15-B6) and the seeded `disclosure_groups` data.

## Validation

Wave matches DAG; FR-S15-01..14 all assigned; all 8 boundaries decided (none
unresolved); scaffolding deltas explicit (none); BATCH surfaces covered;
shared-program rule honored (COBSWAIT consumed from S-14).
