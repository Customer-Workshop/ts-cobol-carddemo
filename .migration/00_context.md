# 00 — Engagement context: CardDemo core (CICS/COBOL/VSAM -> Java/Spring Boot/Angular/PostgreSQL)

Created 2026-09-07 by `!mf_migration_setup` (Devin session
https://partner-workshops.devinenterprise.com/sessions/d1dc2eb1b45a4ec8973a2c08782d6970).
This directory is the **single source of truth** for how the engagement runs. Knowledge notes
mirror it but point here; when they disagree, this tree wins. Re-run `!mf_migration_setup` at
the start of a resumed engagement to re-verify paths, branches and the runbook.

## 1. Module in scope

| Item | Value |
|---|---|
| Module | **CardDemo core** — the CICS/COBOL/VSAM credit-card application under `app/` (sign-on, menus, account/card/transaction/user maintenance, reports; plus batch posting/interest chains) |
| Out of scope | the optional extensions `app/app-authorization-ims-db2-mq/`, `app/app-transaction-type-db2/`, `app/app-vsam-mq/` (IMS/DB2/MQ) unless a later decision adds them |
| Working language | English (artifacts, UI labels, commit messages) |
| Legacy access | **None.** No mainframe, CICS region, or DB2 is reachable. All legacy behaviour is **source-derived** (`app/`) and verified with GnuCOBOL where a program compiles off-host. |

## 2. Repository topology (single repo, base branch `main`)

Repo: `Cognition-Partner-Workshops/ts-cobol-carddemo`. Engagement work branch for the first
stream: `devin/1788757216-cardemo-account-view-stream` (exists on origin).

| Role | Route | State on this branch | Notes |
|---|---|---|---|
| SOURCE | `app/` — `app/cbl` (programs), `app/cpy` (copybooks), `app/cpy-bms` (BMS symbolic maps), `app/bms` (map source), `app/jcl`, `app/proc`, `app/ctl`, `app/csd/CARDDEMO.CSD`, `app/scheduler/`, `app/data/{ASCII,EBCDIC}`, `app/asm`, `app/maclib`, `app/catlg` | present | **read-only for the whole engagement** |
| BACKEND (target) | `backend/` | **does not exist yet** — Phase 0 of the first stream creates it | Java 21 / Spring Boot 3 / Maven / JUnit 5 / no Lombok / PostgreSQL 16 + Flyway |
| FRONTEND (target) | `frontend/` | **does not exist yet** — Phase 0 creates it | Angular (standalone components) |
| DOCS | `functional/` ; module folder `functional/CardDemo/` | present (holds the target state) | see artifact contract below |
| REFERENCE | `spring-boot/` | present | already-migrated module used only as evidence for the CORE / ONLINE / DATA profiles. **Never the target; never edited.** |
| CI | `.github/workflows/` | does not exist yet | GitHub Actions; created in Phase 0 |

Topology flags carried over from the target state: **F1** prior-run branches use
`functional/CARDDEMO/` (case-variant of `functional/CardDemo/`; never merge both); **F2** the
prior-run `backend/` is C#/.NET and must not be used as a starting point; **F3** no `.github/` on
`main`.

## 3. Target state

`functional/CardDemo/CardDemo_target_state.md` (same branch), produced by
`!mf_ingest_target_state`. Summary and link in `01_target_state.md`. Profiles: CORE, ONLINE,
DATA/BOUNDARY in scope; **BATCH and SUBTRANSACTION are N/A by decision** for this engagement's
first stream. Status: **DRAFT, awaiting customer confirmation at STOP A** (see `06_decisions.md`).

## 4. First stream (registered only — analysis happens in `!mf_stream_analysis`)

| Item | Value |
|---|---|
| Stream | **Account View** |
| Process type | ONLINE |
| Entry | trancode `CAVW` -> program `COACTVWC`, screen/mapset `COACTVW` (`app/csd/CARDDEMO.CSD` `DEFINE TRANSACTION(CAVW) PROGRAM(COACTVWC)`) |
| Hard stop | `EXEC CICS XCTL` back to `COMEN01C` (main menu) |
| Shared programs owned by this stream | `COSGN00C` (sign-on, `CC00`), `COMEN01C` (main menu, `CM00`), `CSUTLDTC` (date utility, called) |
| Datasets touched | `CXACAIX` (card-xref AIX by account), `ACCTDAT`, `CUSTDAT` |

## 5. Environments

| Environment | Availability | Notes |
|---|---|---|
| Local COBOL | GnuCOBOL `cobc` 3.1.2 | batch programs compile and link; CICS programs (`EXEC CICS`, `DFHBMSCA`, `DFHAID`, BMS symbolic maps) compile **syntax-check only after** copying `app/cpy-bms/*` and stubbing `DFH*` copybooks — see `07_runbook.md` |
| Local database | **Docker PostgreSQL 16** (`postgres:16` image), container `carddemo-pg`, host port **5433** | host port 5432 is occupied by a pre-installed PostgreSQL 14 (`psql` 14.24) — do not use it as the target DB |
| Reference module | `spring-boot/` builds and runs (H2 in-memory) with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` | `/usr/bin/java` is Java 8 |
| CI | GitHub Actions (to be created in Phase 0) | no PostgreSQL service container decided yet (open question Q2 in the target state) |
| Node | v20.18.1 installed on the dev image | target state proposes Node 22 for `frontend/` CI; a blueprint change or `nvm` is needed when Phase 0 lands |
| Mainframe / DB2 | **none** | behaviour source-derived |

## 6. Autonomy defaults (inherited by every later playbook)

| Stop | Meaning | Mode |
|---|---|---|
| STOP A | target state + engagement setup confirmed | **block** |
| STOP B | stream analysis / boundary register confirmed | **block** |
| STOP C | stream FRs + migration plan confirmed | **block** |
| STOP D | per-program migration PR ready / parity result | **notify** (do not wait) |
| STOP E | stream sign-off / cutover | **block** |

Blocking stops: the child posts its final message and stops; the orchestrator relays to the
customer. Notify stops: message and continue.

## 7. Lead-time requests to external teams

Simulated in this engagement. One file per request under `.migration/requests/`, status
`SENT (simulated)`; convention in `.migration/requests/README.md`.

## 8. Artifact contract (every later playbook routes by this)

All paths relative to repo root; module folder is `functional/CardDemo/` (exact case).
`<Stream>` is the stream's short name in PascalCase without spaces (first stream: `AccountView`);
`<Program>` is the 8-character COBOL program id in upper case.

| Deliverable | Path | Producer |
|---|---|---|
| Target state | `functional/CardDemo/CardDemo_target_state.md` | `!mf_ingest_target_state` |
| Engagement context (this tree) | `.migration/00..07_*.md`, `.migration/requests/` | `!mf_migration_setup` |
| Module inventory | `functional/CardDemo/CardDemo_inventory.md` | `!mf_module_inventory_analysis` |
| Stream analysis | `functional/CardDemo/<Stream>_analysis.md` | `!mf_stream_analysis` |
| Stream functional requirements | `functional/CardDemo/<Stream>_functional_requirement.md` | `!mf_stream_fr_generation` |
| Stream migration plan | `functional/CardDemo/<Stream>_migration_plan.md` | `!mf_stream_migration_plan` |
| Program FR | `functional/CardDemo/programs/<Program>_functional_requirement.md` | `!mf_program_fr_generation` |
| Program migration record | `functional/CardDemo/programs/<Program>_migration.md` | `!mf_program_migration` |
| Parity evidence | `functional/CardDemo/evidence/<Stream>/<Program>/` (fixtures, expected vs actual, logs) | `!mf_program_parity_test` |
| UI test evidence | `functional/CardDemo/evidence/<Stream>/ui/` (recordings, screenshots) | `!mf_online_ui_testing` |
| Boundary resolution notes | `functional/CardDemo/boundaries/<BoundaryId>.md` + row in `.migration/04_boundary_register.md` | `!mf_boundary_resolution` |
| Stream sign-off | `functional/CardDemo/<Stream>_signoff.md` | `!mf_stream_signoff` |
| Target code | `backend/` (Maven root `backend/pom.xml`, package `com.carddemo`), `frontend/` (Angular CLI root) | `!mf_program_migration` |
| CI | `.github/workflows/backend-ci.yml`, `.github/workflows/frontend-ci.yml` | Phase 0 |
| Registers (append-only) | `.migration/04_boundary_register.md`, `.migration/05_progress.md`, `.migration/06_decisions.md` | every playbook appends; never rewrites |

Naming rules: Markdown files `snake_case` after the `<Stream>`/`<Program>` prefix; evidence
folders mirror the program id; no personal names or emails in any artifact or commit message (a
Devin session link is fine). Legacy `app/` is never modified. `spring-boot/` is never modified.

## 9. Macro chain (how to resume from any point)

```
!mf_ingest_target_state   -> functional/CardDemo/CardDemo_target_state.md        [done, pending STOP A]
!mf_migration_setup       -> .migration/                                           [done, pending STOP A]
!mf_module_inventory_analysis -> functional/CardDemo/CardDemo_inventory.md
per stream:
  !mf_stream_analysis         -> <Stream>_analysis.md            (STOP B blocks)
  !mf_stream_fr_generation    -> <Stream>_functional_requirement.md
  !mf_stream_migration_plan   -> <Stream>_migration_plan.md      (STOP C blocks)
  !mf_program_fr_generation   -> programs/<Program>_functional_requirement.md
  !mf_program_migration       -> backend/, frontend/, programs/<Program>_migration.md  (STOP D notifies)
  !mf_program_parity_test     -> evidence/<Stream>/<Program>/
  !mf_online_ui_testing       -> evidence/<Stream>/ui/            (optional, ONLINE only)
  !mf_stream_signoff          -> <Stream>_signoff.md             (STOP E blocks)
!mf_boundary_resolution       -> invoked per flagged boundary; appends to 04_boundary_register.md
```

Current position: see `05_progress.md`.

## 10. Existing assets — registered as CROSS-CHECK ONLY (never trusted over `app/` source, never copied)

| Asset | Where | Use |
|---|---|---|
| Prior run `.migration/`, `functional/CARDDEMO/*` (inventory, S01/S02/S03 analyses, FRs, plans), `backend/` (C#/.NET), `frontend/` (Angular), `.github/workflows/target-ci.yml` | branches `devin/1787242078-carddemo-premigration`, `devin/batch-a-s02-account-view` | compare findings; different stack — no code reuse |
| Origin of `spring-boot/` | branch `devin/1787158883-cobol-to-spring-boot` | provenance of the reference module |
| Repo `README.md`, `diagrams/` (menu/flow PNGs, `CARDDEMO-DataModel.drawio`) | `main` | orientation; screens and flows must still be read from `app/bms` and `app/cbl` |
| `app/csd/CARDDEMO.CSD` | `app/csd/` | authoritative CICS resource definitions (transactions, programs, files, mapsets) — this one **is** source |
| `app/scheduler/CardDemo.controlm`, `CardDemo.ca7` | `app/scheduler/` | scheduler definitions (batch only; N/A for the first stream) |
| Auto-generated repo index knowledge note | Devin knowledge | orientation only |
