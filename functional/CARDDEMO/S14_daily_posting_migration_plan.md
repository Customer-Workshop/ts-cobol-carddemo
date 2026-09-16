# S-14 Daily Posting — Migration Plan (`!mf_stream_migration_plan`)

Status: DRAFT for STOP C approval (2026-09-16, Java engagement). Inputs:
`S14_daily_posting_analysis.md`, `S14_functional_requirement.md`,
`CardDemo_target_state.md` (CORE + BATCH + DATA/BOUNDARY), baseline =
`spring-boot/` on the engagement branch. Reserved Flyway range: **V210x**.

## 1. Goal and scope

Migrate the daily posting chain — POSTTRAN (CBTRN02C) + WAITSTEP (COBSWAIT→
MVSWAIT) + the CBTRN01C validation orphan — to Java 21 + Spring Boot batch.
Definition of done: FR-S14-01..18 pass. Hard stop: rejects file + committed
writes + wait-step completion; scheduler orchestration itself is documented
job-order, not migrated software. Process type: BATCH.

**Baseline position (FACT, verified this engagement):** `cbtrn01Job` (validation
sweep via `BatchJobService.validateDaily` → `cbtrn01-validation.txt`),
`cbtrn02Job` (`dailytran.txt`/param file → `DailyTransactionReader` →
`postDaily` → `cbtrn02-rejects.txt`) exist; `postDaily` implements 100/101/
102/103 checks, TCATBAL upsert, acct bal + sign-aware cyc update, tran write
(`BatchJobService.java:75-105, :346-365`). `BatchJobLauncherService` +
`POST /api/admin/jobs/{jobName}` give explicit launch. **Gaps:** no wait-step
tasklet (COBSWAIT seam missing); no reject-count → exit-code surfacing (RC=4);
reject trailer is a text dump not the 430B binary layout; no documented daily
job-order runbook; CBTRN02C's reason-109 REWRITE-failure path and VSAM key-order
edge cases unverified vs FR.

## 2. Target-state mapping

Profiles applied: CORE + BATCH + DATA/BOUNDARY.

- Jobs: `cbtrn02Job` = POSTTRAN; new `waitStep` tasklet = COBSWAIT/MVSWAIT
  (duration param `waitCentiseconds`, default 3600); `cbtrn01Job` = orphan
  validator, documented as "not in the daily chain".
- Launch: `POST /api/admin/jobs/cbtrn02Job` (+ optional `dailyFile` param) —
  replaces JCL submission; JobParameters replace SYSIN/PARM.
- Scheduler: Control-M/CA-7 conditions → documented job order + job exit codes
  (module B-008 decision); the "file close/open" bracket is a no-op in target
  (Postgres has no VSAM open/close).
- Persistence: VSAM→Postgres JPA (module B-009); DALYTRAN PS→flat file,
  DALYREJS GDG→flat file per run dir (module B-010).
- Errors: CEE3ABD → exception → step FAILED (module B-004); RC=4 →
  `JobExecution` exit description `REJECTS:<n>` (surface, don't fail).

## 3. Boundary decision table (decide mode over S14-B1..B9)

| ID | Class | Decision | Seam in target | Error/idempotency | Owner | Lead-time | Routing point / cutover |
|---|---|---|---|---|---|---|---|
| S14-B1 | B-008 chain | **DECIDED:** documented job order `cbtrn02Job → (backup optional) → waitStep`; exit codes 0/4/FAIL drive ops | `docs/runbook` + admin endpoint | rerun = re-apply (source-faithful, non-idempotent) | S-14 | none | legacy chain decommissioned at module cutover |
| S14-B2 | B-009 VSAM | **DECIDED:** repositories — `TransactionRepository` write, `CardXref/Account/TransactionCategoryBalance` I-O | `repository/*` | read/write errors → step failure | S-14 | none | — |
| S14-B3 | B-010 datasets | **DECIDED:** `dailyFile` param in; `cbtrn02-rejects.txt` out (430B layout: 350B record + 4-digit reason + 76-char desc) | flat readers/writers | `shouldDeleteIfExists` → rerun-safe file side | S-14 | none | — |
| S14-B4 | B-002 MVSWAIT | **DECIDED:** Java tasklet `Thread.sleep(waitCentiseconds × 10 ms)`; keep as explicit step for runbook parity | new `WaitStepTasklet` + step in chain order | interruptible; interruption → step failure | S-14 | none | S-15 consumes the same tasklet |
| S14-B5 | B-004 errors | **DECIDED:** exceptions → step FAILED; reject count → exit description (not failure) | Spring Batch | partial chunk commits roll back per chunk (better than legacy; noted deviation — legacy had no chunking) | S-14 | none | — |
| S14-B6 | B10 file-open timing | **DECIDED:** drop close/open bracketing — Postgres is always-on; document that daily post must run after online cutover window per ops runbook | runbook | — | S-14 | none | — |
| S14-B7 | feed contract | **DECIDED:** flat-file `dailyFile` drop is the contract; absent file → empty run RC 0 | reader resource | missing file → empty/zero-count run | S-14 | none | — |
| S14-B8 | orphan | **DECIDED:** keep `cbtrn01Job` as standalone validation utility; label orphan; not in chain order | existing job | — | S-14 | none | — |
| S14-B9 | housekeeping | **DECIDED:** TRANBKP eliminated (no define/delete cycle in Postgres); backup = managed DB backup outside stream scope | — | — | S-14 | none | — |

No stored-procedure boundaries; no external lead times — recorded explicitly.

## 4. Data and persistence

- Tables: `transactions` write; `accounts` update; `transaction_category_balances`
  upsert; `card_xrefs` read — all in Flyway V2 baseline already.
- V210x reserved: expected usage = reject-file/exit-code support or an optional
  `batch_reject` audit row only; none required for parity. No new domain tables.
- Files: input `dailytran.txt` layout = CVTRA06Y 350B; reject output = 430B
  (350+4+76). `BatchFileSupport.pad` already produces the right framing.

## 5. Phase 0 deltas

None — batch scaffold (JobRepository schema V3, launcher, admin API) landed
already.

## 6. Waves

| Wave | Programs/seams | Repo area | Consumes FR docs | Boundary seams | Strict edge to next |
|---|---|---|---|---|---|
| 1 | `cbtrn02Job` parity closure (reject-trailer 430B, reason 109 path, RC=4 exit surfacing, sign-aware cyc semantics, empty-feed RC0), `WaitStepTasklet` + chain runbook, `cbtrn01Job` orphan labeling, FR-S14 test matrix | spring-boot/ | CBTRN02C/COBSWAIT/CBTRN01C program FRs + stream FR | S14-B1..B9 | — |

## 7. Per-program FR generation

- `programs/CBTRN02C_functional_requirement.md` — new (this doc set).
- `programs/COBSWAIT_functional_requirement.md` — new (this doc set).
- `programs/CBTRN01C_functional_requirement.md` — new (this doc set).
- `POSTTRAN`/`WAITSTEP`/`TRANBKP` are JCL shells — inline in stream FR.

## 8. Testing and verification

- Unit: each reject reason (100/101/102/103/109) vs seeded fixtures; posting
  writes (tran row, cat-bal create/update, account update + sign-aware cyc);
  430B reject framing; RC mapping.
- Integration: `cbtrn02Job` over `dailytran.txt` fixture → row counts +
  reject-file content golden compare; `waitStep` duration param honored
  (short value in tests); empty file → RC 0.
- Runbook test: documented order executes via admin endpoint in sequence.

## 9. Sign-off gate

`!mf_stream_signoff` runs FR-S14 §8 one-by-one; independent audit; STOP E.

## 10. Risks

1. Chunk commits make target more failure-granular than legacy (legacy all-or-
   nothing per file pair, effectively). Deviation — strictly safer; documented
   in FR-S14-17 note. LOW.
2. Reject-count exit surfacing requires a small listener — risk that ops read it
   as failure; document exit-description convention. LOW.
3. Wait-step real duration (36 s) is mainframe settle time; in target it is a
   configurable no-op-ish delay — risk someone "optimizes" it to 0; keep default
   faithful. LOW.
4. Scheduler-source discrepancy (POSTTRAN absent from Control-M DAILY folder) —
   resolved by CA-7 being authoritative for the posting chain; flagged for the
   engagement owner. MEDIUM.

## 11. Effort and sequencing

1 wave child after STOP C, ~1 session. Consumes S-01 session-free (batch);
produces the COBSWAIT wait tasklet that S-15 inherits.

## Validation

Wave matches analysis DAG; FR-S14-01..18 all assigned; all 9 boundaries decided;
no scaffolding deltas; BATCH surfaces (DD contracts, return codes, scheduler
conditions, restart semantics) covered; shared program COBSWAIT ported here and
inherited by S-15 — owner recorded.
