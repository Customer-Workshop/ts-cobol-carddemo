# S-10 Reports — Migration Plan (`!mf_stream_migration_plan`)

Status: DRAFT for STOP C approval (2026-09-16, Java engagement). Inputs:
`S10_reports_analysis.md`, `S10_functional_requirement.md`,
`CardDemo_target_state.md` (CORE + ONLINE + BATCH + DATA/BOUNDARY, all CONFIRMED at
STOP A), baseline = `spring-boot/` port on the engagement branch.
Reserved Flyway range: **V180x**.

## 1. Goal and scope

Migrate the CardDemo transaction-report request flow — screen CORPT0A (transaction
CR00, program CORPT00C) plus the TRANREPT batch chain (REPRO unload → DFSORT →
CBTRN03C) — to Java 21 + Spring Boot 3.4.5 + Thymeleaf + PostgreSQL, inside the
single repo (`spring-boot/`). Definition of done: FR-S10-01..20 pass. Hard stop:
the report file equivalent of `TRANREPT(+1)`; print distribution stays out of
scope. Process type: ONLINE+BATCH.

**Baseline position (FACT, verified this engagement):** the adopted port already
implements the submit seam and the report job — `ReportController`
(`POST /api/reports`, `spring-boot/.../api/ReportController.java`),
`ReportService` (Monthly→`YearMonth.now()` atDay(1)..atEndOfMonth, Yearly→Jan
1..Dec 31, Custom→ISO dates, confirmation='Y' gate, launches `cbtrn03Job`;
`service/ReportService.java`), `Cbtrn03JobConfiguration` (reader
`findByTranProcessTimestampBetween` sorted card+id; writer `cbtrn03-report.txt`;
`batch/Cbtrn03JobConfiguration.java`), and `BatchJobLauncherService` +
`POST /api/admin/jobs/{jobName}`. What it lacks: **the Thymeleaf report-request
screen**, full CVTRA07Y layout fidelity (headers/page-account-grand totals every
20 lines), and FR-mapped parity tests. S-10's work is the UI slice + report-format
parity — not a rewrite.

## 2. Target-state mapping

Profiles applied: CORE + ONLINE + BATCH + DATA/BOUNDARY.

- API: keep `POST /api/reports` (`reportName`,`startDate`,`endDate`,
  `confirmation`) — this is the B-001 seam; 202-style acceptance response carries
  the job execution id.
- UI: `templates/report.html` on the existing Thymeleaf shell — three radio flags,
  MM/DD/YYYY date triplet inputs, confirm checkbox, verbatim messages, PF3→`/menu`.
  `UiController` calls `ReportService` — one logic path, two surfaces.
- Batch: `cbtrn03Job` stays; `ReportLineAggregator` upgraded to emit the full
  CVTRA07Y 133-col layout (name header + Date Range line, TRANSACTION-HEADER-1/2,
  detail, PAGE/ACCOUNT/GRAND totals).
- Persistence: all reads via existing repositories — no new tables expected;
  reserve V180x for any report-catalog/lookup additions if needed.
- Launch: explicit (`spring.batch.job.enabled=false`); screen/API launches go
  through `BatchJobLauncherService` — replaces TDQ 'JOBS' + intrdr.

## 3. Boundary decision table (decide mode over S10-B1..B8)

| ID | Class | Decision | Seam in target | Error/idempotency | Owner | Lead-time | Routing point / cutover |
|---|---|---|---|---|---|---|---|
| S10-B1 | B-001 submit | **DECIDED (keep module decision):** POST /api/reports → `BatchJobLauncherService` → `cbtrn03Job`; screen posts to the same service | `ReportService.request` | launch failure → 400 w/ 'Unable to Write TDQ (JOBS)...' equivalent message; launches not idempotent (each submit = one run, matching legacy) | S-10 | none | legacy TDQ decommissioned when S-10 signs off |
| S10-B2 | B-008 params | **DECIDED:** single `startDate`/`endDate` JobParameters replace SYMNAMES + DATEPARM channels | job param validator (`Cbtrn03JobConfiguration.validateParameters`) | missing/invalid → job reject before open (parity with field edits) | S-10 | none | — |
| S10-B3 | B-009 VSAM | **DECIDED:** repositories `TransactionRepository` (+ Xref/Type/Category) — VSAM→Postgres JPA per module B-009 | `repository/*` | read errors → step failure | S-10 | none | TRANSACT reads leave VSAM at sign-off |
| S10-B4 | B-010 outputs | **DECIDED:** `cbtrn03-report.txt` flat file under `carddemo.batch.output-dir`; BKUP/DALY intermediates eliminated (no GDG versioning — module B-010) | `FlatFileItemWriter` + `ReportLineAggregator` | file error → step failure; rerun safe (`shouldDeleteIfExists`) | S-10 | none | — |
| S10-B5 | shared utility | **DECIDED:** consume S-09's ported date check (CSUTLDTC) — do not re-port; 2513 tolerance preserved in edit logic | `service` date validation | — | S-09 (owner) | none | already landed with S-09 |
| S10-B6 | B5 nav | **DECIDED:** `/report` route behind auth; Back → `/menu`; unauthenticated → `/signon` | Thymeleaf + SecurityContext | — | S-10 | none | — |
| S10-B7 | B-004 errors | **DECIDED:** CEE3ABD → exception → step FAILED → non-zero job exit (module B-004) | Spring Batch fault path | DISPLAY diagnostics → application log | S-10 | none | — |
| S10-B8 | utility steps | **DECIDED:** REPRO+DFSORT eliminated; repository query `WHERE proc_ts BETWEEN start AND end ORDER BY card, id` replaces both | `cbtrn03Reader` | — | S-10 | none | — |

No stored-procedure boundaries. No external enablement lead times (all seams inside
the repo); recorded explicitly.

## 4. Data and persistence

- No new domain tables. Reads: `transactions` (filter proc date, sort card+id),
  `card_xrefs` (card→acct), `transaction_types`, `transaction_categories`.
- Flyway V180x reserved but expected empty — if a report-request audit table is
  desired later it lands here (`V1801__report_requests.sql`), not blocking.
- Output: flat file per run in the job output dir (module B-010 decision; filenames
  carry generation role).

## 5. Phase 0 deltas

None — module Phase 0 (Thymeleaf shell, Flyway, Postgres profile, CI) landed with
S-01; batch scaffolding (JobRepository tables, launcher, admin endpoint) already on
the engagement branch.

## 6. Waves (from analysis DAG; one wave for this stream)

| Wave | Programs/seams | Repo area | Consumes FR docs | Boundary seams | Strict edge to next |
|---|---|---|---|---|---|
| 1 | Report-format parity in `ReportLineAggregator` (CVTRA07Y 133-col), `report.html` + `UiController` wiring, param validator parity, verbatim messages, FR-S10-01..20 test matrix | spring-boot/ | CORPT00C FR, CBTRN03C FR, stream FR | S10-B1..B8 | — |

Single-repo: one PR per wave into the engagement branch; CI green gate.

## 7. Per-program FR generation

- `programs/CORPT00C_functional_requirement.md` — new (this doc set).
- `programs/CBTRN03C_functional_requirement.md` — new (this doc set).
- `programs/CSUTLDTC_functional_requirement.md` — exists (S-09); consume.
- `TRANREPT` is a JCL shell — covered inline in the stream FR, no program doc.

## 8. Testing and verification

- Unit: date-range derivation (monthly/yearly/custom incl. month-rollover edge
  Dec→Jan), confirm semantics, verbatim messages, 2513-tolerance cell
  (documented unreachable-in-target), inverted-range deviation.
- Integration: `POST /api/reports` happy path launches `cbtrn03Job`; admin launch
  with params; report file content vs golden fixture (seed `dailytran.txt`/ASCII
  transaction fixture subset filtered by proc date).
- UI: `!mf_online_ui_testing` over report.html — three radio paths, field edits,
  confirm gate, PF3 back, invalid-key.
- E2E: sign-on → menu → report screen → submit → job completes → inspect output
  file.

## 9. Sign-off gate

`!mf_stream_signoff` executes FR-S10 §8 one-by-one against the running app; every
row gets pass/fail evidence; independent audit by a fresh session; STOP E for merge
authorization.

## 10. Risks

1. CVTRA07Y full-fidelity rewrite of the aggregator is the largest single change —
   golden-file compare against a hand-derived expected report pins it. MEDIUM.
2. Report byte-fidelity sign-off may relax 133-col parity — cheap either way, do the
   faithful version. LOW.
3. 'Submitted for printing' wording on the UI must match verbatim while the
   underlying seam is async launch — cosmetic. LOW.
4. Duplicate STEP05R names in legacy TRANREPT.jcl — no target impact; documented.
   LOW.

## 11. Effort and sequencing

1 wave child after STOP C (report-format parity + screen + tests). ~1 session.
Consumes: S-01 shell/session, S-09 date utility, existing batch scaffold.

## Validation

Wave matches analysis DAG (topological); FR-S10-01..20 all assigned (wave 1); all 8
boundaries decided (all DECIDED against module conventions, none unresolved);
scaffolding deltas explicit (none); ONLINE+BATCH surfaces both covered;
shared-program rule honored (CSUTLDTC consumed, not re-ported).
