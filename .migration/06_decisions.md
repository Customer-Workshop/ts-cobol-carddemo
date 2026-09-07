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
