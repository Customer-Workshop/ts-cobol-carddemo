# 00_context — CardDemo migration engagement

Status: STOP A CONFIRMED 2026-08-20 (the project owner): C#/.NET 8 + ASP.NET Core, Angular 17+,
PostgreSQL, single-repo topology.

## Modules in scope
- **CARDDEMO** core module (`app/`): 31 COBOL programs (`app/cbl`), 17 BMS maps, 38 JCL members,
  Control-M/CA-7 schedules, CSD.
- Optional extensions (scope TBD at STOP B): `app/app-authorization-ims-db2-mq`,
  `app/app-transaction-type-db2`, `app/app-vsam-mq`.

## Repository topology (CONFIRMED at STOP A)
| Role | Repo | Base branch | Evidence / note |
|---|---|---|---|
| SOURCE | Cognition-Partner-Workshops/ts-cobol-carddemo | main | COBOL/copybooks/JCL/BMS/CSD/scheduler under `app/` (FACT) |
| DOCS | Cognition-Partner-Workshops/ts-cobol-carddemo | main | `functional/CARDDEMO/**` + `.migration/**` (CONFIRMED: co-locate) |
| BACKEND | same repo, `backend/` subdirectory (.NET 8 solution `backend/CardDemo.sln`) | main | CONFIRMED at STOP A; to be scaffolded in Phase 0 |
| FRONTEND | same repo, `frontend/` subdirectory (Angular 17+) | main | CONFIRMED at STOP A; to be scaffolded in Phase 0 |

Look-alike risk noted: `~/repos/ts-cobol-acas-java` exists locally but belongs to the ACAS
engagement, NOT CardDemo. Do not route CardDemo artifacts there.

## Environments
- Local: GnuCOBOL 3.1.2 (`cobc`) available; Java 21 available (`openjdk 21.0.11`); no mainframe,
  CICS, DB2, IMS, or MQ access — all behavior is source-derived.
- CI: GitHub Actions available on the repos (gates to be added with the backend scaffold).

## Artifact contract (all in DOCS repo, branch per deliverable, merged to main)
- Target state: `functional/CARDDEMO/CardDemo_target_state.md`
- Module inventory: `functional/CARDDEMO/CardDemo_inventory.md`
- Stream analysis: `functional/CARDDEMO/<Stream>_analysis.md`
- Stream FR: `functional/CARDDEMO/<Stream>_functional_requirement.md`
- Program FRs: `functional/CARDDEMO/programs/<Program>_functional_requirement.md`
- Migration plan: `functional/CARDDEMO/<Stream>_migration_plan.md`
- Evidence: `functional/CARDDEMO/evidence/<stream>/**`
- Engagement ledger: `.migration/*` (this tree)

## Autonomy defaults
STOP A, B, C, E: **blocking**. STOP D (per-wave review): notify. Verification depth: checklist for
every requirement + captured evidence for the sign-off set.

## Working languages
English (source labels and comments are English — FACT).

## Macro chain
`!mf_ingest_target_state` -> `!mf_migration_setup` -> `!mf_module_inventory_analysis` -> per
stream: `!mf_stream_analysis` -> `!mf_stream_fr_generation` -> `!mf_stream_migration_plan` ->
`!mf_program_fr_generation` -> `!mf_program_migration` (one child per wave) ->
`!mf_program_parity_test` -> optional `!mf_online_ui_testing` -> `!mf_stream_signoff`;
`!mf_boundary_resolution` per flagged boundary.

## Existing assets registered (cross-check only, never source of truth)
- `README.md` — application inventory, screens, user/admin function list.
- `diagrams/` — architecture diagrams shipped with CardDemo.
- Remote branches `aws-transform*`, `devin/*` — prior experiment artifacts; NOT part of this
  engagement's baseline (main has no migrated code).
