# S-16 Statement Generation — Migration Plan (`!mf_stream_migration_plan`)

Status: DRAFT for STOP C approval (2026-09-16, Java engagement). Inputs:
`S16_statements_analysis.md`, `S16_functional_requirement.md`,
`CardDemo_target_state.md` (CORE + BATCH + DATA/BOUNDARY), baseline =
`spring-boot/` on the engagement branch. Reserved Flyway range: **V230x**.

## 1. Goal and scope

Migrate statement generation — CREASTMT (TRXFL prep chain + CBSTM03A with its
CBSTM03B file-handler subroutine) — to Java 21 + Spring Boot batch. Definition
of done: FR-S16-01..15 pass. Hard stop: `STATEMNT.PS` + `STATEMNT.HTML`
equivalents complete; TXT2PDF1 PDF step is a STOP C decision. Process type:
BATCH.

**Baseline position (FACT, verified this engagement):** `cbstm03Job` exists —
`Cbstm03JobConfiguration` iterates `CardXref` sorted, calls `statementFor` →
`DualStatementWriter` emitting `STATEMNT.PS` + `STATEMNT.HTML` under the job
output dir; `BatchJobService` resolves customer/account/transaction data via
repositories. Per-xref semantics (every card gets a statement) match legacy.
**Gaps/deviations:** (a) `statementPlain` uses a 'Bank of XYZ' banner — legacy
ST-LINE0 is an asterisk 'START OF STATEMENT' frame: layout parity unverified
against the 17 ST-LINE + HTML-Lxx layouts; (b) 51×10 preload caps exist in
legacy and are intentionally dropped; (c) no PDF step (TXT2PDF1 is external);
(d) no documented statement job-order runbook; (e) legacy `PARM='12'` vestigial
— target needs no parm (decision, not a gap).

## 2. Target-state mapping

Profiles applied: CORE + BATCH + DATA/BOUNDARY.

- Job: `cbstm03Job` stays as one pass over sorted `card_xrefs`; repository
  queries replace the TRXFL prep chain (DELDEF01+SORT+REPRO+delete all
  eliminated — target streams `findByTranCardNum`/`tranNum`-sorted rows or a
  single sorted read).
- CBSTM03B handler subroutine → repositories (no sub-program ported; the
  LK-M03B-AREA contract dissolves into repository calls).
- Outputs: `STATEMNT.PS` 80B + `STATEMNT.HTML` 100B flat files under
  `carddemo.batch.output-dir`; layouts to COSTM01 ST-LINE byte-parity.
- Launch: `POST /api/admin/jobs/cbstm03Job`; no parm required.
- Chain: documented statement order `close → cbstm03Job → (pdf optional) →
  wait → reopen`.

## 3. Boundary decision table (decide mode over S16-B1..B9)

| ID | Class | Decision | Seam in target | Error/idempotency | Owner | Lead-time | Routing point / cutover |
|---|---|---|---|---|---|---|---|
| S16-B1 | B-009 VSAM | **DECIDED:** `CardXref/Customer/Account/Transaction` repositories; physical layer resolved (VSAM, no SP) | `repository/*` | read errors → step FAILED | S-16 | none | — |
| S16-B2 | B-010 dataset chain | **DECIDED:** sorted repository read replaces SORT→REPRO→KSDS load; TRXFL eliminated | `JpaPagingItemReader`/list | — | S-16 | none | — |
| S16-B3 | B-010 outputs | **DECIDED:** `STATEMNT.PS` + `STATEMNT.HTML` flat files per run dir; no GDG versioning | `DualStatementWriter` | rerun-safe (`shouldDeleteIfExists`) | S-16 | none | — |
| S16-B4 | B-008 params | **DECIDED:** no parameter — legacy PARM vestigial (never read); document | — | — | S-16 | none | — |
| S16-B5 | B-008 chain | **DECIDED:** documented statement job order + exit codes | `docs/runbook` + admin launch | rerun = overwrite (idempotent output side) | S-16 | none | — |
| S16-B6 | external tool | **DECIDED (default):** skip PDF — PS+HTML parity is the contract; open question Q1 if ops requires PDF → add a lightweight PDF writer later | — | — | S-16 | none | — |
| S16-B7 | B-002 wait | **DECIDED:** consume S-14 `WaitStepTasklet` | existing tasklet | — | S-14 (owner) | none | — |
| S16-B8 | B-004 errors | **DECIDED:** exception → step FAILED → exit code | Spring Batch | DISPLAY → log | S-16 | none | — |
| S16-B9 | sub-program seam | **DECIDED:** CBSTM03B not ported — repositories stand in for the file-handler | `repository/*` | — | S-16 | none | — |

No stored-procedure boundaries; no external lead times — recorded explicitly.

## 4. Data and persistence

- Reads: `card_xrefs` (sorted sweep), `customers`, `accounts`, `transactions`
  (sorted card+id within card). All existing tables.
- V230x reserved: optional `statement_run_log`; none required for parity.
- Files: `STATEMNT.PS`/`STATEMNT.HTML` per-run outputs.

## 5. Phase 0 deltas

None.

## 6. Waves

| Wave | Programs/seams | Repo area | Consumes FR docs | Boundary seams | Strict edge to next |
|---|---|---|---|---|---|
| 1 | `cbstm03Job` layout parity closure (ST-LINE0..15 + HTML-Lxx golden compare), zero-txn-card statements, document 51×10 cap removal + vestigial parm + runbook | spring-boot/ | CBSTM03A/CBSTM03B program FRs + stream FR | S16-B1..B9 | — |

## 7. Per-program FR generation

- `programs/CBSTM03A_functional_requirement.md` — new (this doc set).
- `programs/CBSTM03B_functional_requirement.md` — new (this doc set) — documents
  the handler subroutine as a demoted seam (not ported).
- `CREASTMT` is a JCL shell — inline in stream FR.

## 8. Testing and verification

- Unit: ST-LINE field mapping (name/addr/acct/bal/FICO), 'Total EXP:' sum,
  HTML literal skeleton, zero-txn statement, ordering (xref order not card
  sort).
- Integration: `cbstm03Job` over seeded xref/cust/acct/trans → golden-file
  compare of both outputs vs hand-derived legacy layouts; rerun overwrite.
- Runbook: statement order via admin endpoint.

## 9. Sign-off gate

`!mf_stream_signoff` runs FR-S16 §8 one-by-one; independent audit; STOP E.

## 10. Risks

1. Layout parity effort — the 80-col PS and HTML literals need exact golden
   fixtures; the baseline's banner differs from ST-LINE0. MEDIUM.
2. PDF decision (A-1) — if ops needs PDFs, scope grows; flagged now. LOW.
3. CUSTREC vs CVCUS01Y copybook drift — statement fields mapped via CUSTREC;
   a mapper column mismatch would corrupt names/addresses. LOW.

## 11. Effort and sequencing

1 wave child after STOP C, ~1 session. Depends on S-14's WaitStepTasklet.

## Validation

Wave matches DAG; FR-S16-01..15 all assigned; all 9 boundaries decided (none
unresolved); scaffolding deltas explicit (none); BATCH surfaces covered;
shared-program rule honored (COBSWAIT consumed from S-14); sub-program seam
demoted (no CBSTM03B port).
