# 06 — Decision log (dated, APPEND-ONLY)

Every decision that a later session must not re-derive: data target, boundary decisions,
deviations from the target state, autonomy changes, who approved. Append rows; never edit.
Reverse a decision with a new row that references the old `Id`.

## Schema

| Column | Definition |
|---|---|
| `Id` | `D-<nnnn>` sequential |
| `Date` | ISO date |
| `Topic` | short label |
| `Decision` | what was decided (one or two lines) |
| `Approved by` | `customer (STOP x)` · `orchestrator` · `engagement brief` · `pending` |
| `Ref` | artifact / boundary id / session link |

## Log

| Id | Date | Topic | Decision | Approved by | Ref |
|---|---|---|---|---|---|
| D-0001 | 2026-09-07 | Topology | Single repo `Cognition-Partner-Workshops/ts-cobol-carddemo`, base `main`; SOURCE `app/`, BACKEND `backend/`, FRONTEND `frontend/`, DOCS `functional/CardDemo/`, REFERENCE `spring-boot/` (never the target) | engagement brief | `00_context.md` §2 |
| D-0002 | 2026-09-07 | Target state | `functional/CardDemo/CardDemo_target_state.md` (CORE + ONLINE + DATA/BOUNDARY; BATCH and SUBTRANSACTION N/A) is treated as the target state. **Customer confirmation of its PROPOSED fields (C1–C6, O1–O9, D1–D8) and flags F1–F3 is PENDING at STOP A**; `!mf_stream_migration_plan` and `!mf_program_migration` must not run before STOP A passes | pending (STOP A) | `01_target_state.md` |
| D-0003 | 2026-09-07 | Target stack | Java 21 / Spring Boot 3 / Maven / JUnit 5 / no Lombok; PostgreSQL 16 + Flyway; Angular frontend; GitHub Actions CI | engagement brief (architecture non-negotiables) | target state §3–5 |
| D-0004 | 2026-09-07 | Surfaces | BATCH and SUBTRANSACTION profiles N/A for this engagement's first stream; re-run `!mf_ingest_target_state` before any batch or called-contract stream | engagement brief | target state §6–7 |
| D-0005 | 2026-09-07 | First stream | Account View (ONLINE, `CAVW` -> `COACTVWC`, hard stop XCTL -> `COMEN01C`), owning shared programs `COSGN00C`, `COMEN01C`, `CSUTLDTC` | engagement brief | `00_context.md` §4 |
| D-0006 | 2026-09-07 | Autonomy | STOP A, B, C, E block; STOP D notifies | engagement brief | `00_context.md` §6 |
| D-0007 | 2026-09-07 | Legacy access | No mainframe/DB2; behaviour is source-derived, verified with GnuCOBOL where compilable; parity evidence is fixture-based | engagement brief | `07_runbook.md` |
| D-0008 | 2026-09-07 | Local DB | Docker `postgres:16` container `carddemo-pg` on host port **5433** (5432 is taken by a pre-installed PostgreSQL 14 that is not to be used) | setup session | `07_runbook.md` §4 |
| D-0009 | 2026-09-07 | Lead-time requests | External-team requests (data extract, SSO, …) are simulated: one file per request in `.migration/requests/`, status `SENT (simulated)` | engagement brief | `.migration/requests/README.md` |
| D-0010 | 2026-09-07 | Cross-check assets | Branches `devin/1787242078-carddemo-premigration`, `devin/batch-a-s02-account-view` (prior `.migration/`, `functional/CARDDEMO/*`, .NET `backend/`, Angular `frontend/`), branch `devin/1787158883-cobol-to-spring-boot`, `README.md`, `diagrams/`, `app/scheduler/` are cross-check only; never trusted over `app/` source, never copied | engagement brief | `00_context.md` §10 |
| D-0011 | 2026-09-07 | Encoding | Source is pure ASCII (no LATIN-1 conversion needed); ASCII data uses zoned overpunch signs -> `cobc -fsign=EBCDIC`; two copybooks contain tabs and must be re-indented before compiling | setup session (verified) | `02_conventions.md` §1 |
| D-0012 | 2026-09-07 | Artifact contract | Paths and naming in `00_context.md` §8 are binding for every later playbook | setup session | `00_context.md` §8 |
| D-0013 | 2026-09-07 | STOP A | STOP A APPROVED. CORE C1–C6, ONLINE O1–O9, DATA D1–D8 confirmed as proposed. Target state status DRAFT -> CONFIRMED | customer (STOP A) | `functional/CardDemo/CardDemo_target_state.md` §11 |
| D-0014 | 2026-09-07 | O2 / RACF boundary | BCrypt with upgrade-on-login accepted; USRSEC demo stub kept; real SSO deferred. This is the RACF/sign-on boundary decision to be registered in `04_boundary_register.md` at boundary registration | customer (STOP A) | target state §4 |
| D-0015 | 2026-09-07 | D1 / Q2 | H2 test profile only for unit `mvn verify`; Testcontainers-PostgreSQL from wave 1; CI uses a PostgreSQL 16 service container for integration tests. Parity evidence must come from real PostgreSQL 16, never H2 | customer (STOP A) | target state §5 D1, §12 Q2 |
| D-0016 | 2026-09-07 | Q1 routes | Frontend routes use business names (`/accounts/view`); the CICS trancode (`CAVW`) is recorded in FR traceability, not in the URL | customer (STOP A) | target state §12 Q1 |
| D-0017 | 2026-09-07 | Flags F1–F3 | F1: canonical DOCS folder is `functional/CardDemo/`; prior `CARDDEMO`-cased branches ignored. F2: .NET `backend/` on prior branches not reused. F3: Phase 0 creates `.github/workflows/` | customer (STOP A) | target state §1 |
| D-0018 | 2026-09-07 | Environments | Local PostgreSQL 16 via Docker on host port 5433; no mainframe, no DB2; source-derived behaviour only; working language English | customer (STOP A) | `07_runbook.md` |
| D-0019 | 2026-09-07 | STOP B | STOP B CONFIRMED. Stream S-01 AccountView = `CAVW` -> `COACTVWC`, process type **ONLINE**, hard stop = XCTL back to `COMEN01C`. Exclusions: `CAUP`/`COACTUPC` and every other menu option; the menu shell renders but only Account View is live. Touched boundaries: B-0001, B-0002, B-0006, B-0007, B-0009, B-0010, B-0014, B-0026, B-0027 | customer (STOP B) | `functional/CardDemo/CardDemo_inventory.md` §5, §8 |
| D-0020 | 2026-09-07 | CSUTLDTC ownership | `CSUTLDTC` stays in S-01 as **wave 1** although `COACTVWC` does not call it: it is a shared utility owned by this stream with no in-stream consumer yet; it gives a leaf-first wave with parity tests against COBOL-derived date expectations and de-risks the later Transaction streams (S-08 TranAdd, S-09 TranReports) which will consume the migrated version | customer (STOP B) | inventory §7, §9 risk 9; B-0014 |
