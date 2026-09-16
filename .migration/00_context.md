# 00_context — CardDemo migration engagement (Java / Spring Boot track)

Status: pending STOP A re-confirmation (new engagement, 2026-09-15). User-directed target:
Java 21 / Spring Boot + PostgreSQL, single repo, Spring Batch, simple web UI.

This is the SECOND engagement on this repo. A prior engagement (branch
`devin/1787242078-carddemo-premigration`, PR #88) confirmed C#/.NET 8 + Angular at its own
STOP A (2026-08-20) and migrated the online streams S-01..S-12 to .NET. This engagement
supersedes that target decision per the current owner's instruction (Java 21 / Spring Boot);
it **reuses the prior engagement's source-side analysis artifacts** (inventory, stream
analyses, boundary register, conventions, glossary, program FR business rules) with
provenance noted, and re-decides every target-side choice for Java.

## Modules in scope
- **CARDDEMO** core module (`app/`): 31 COBOL programs (`app/cbl`), 17 BMS maps, 38 JCL
  members, Control-M/CA-7 schedules, CSD.
- Optional extensions (counted in denominator per inventory; EXTENSION streams):
  `app/app-authorization-ims-db2-mq`, `app/app-transaction-type-db2`, `app/app-vsam-mq`.
  Owner instruction: replace VSAM/DB2/IMS/MQ/Assembler boundaries with in-repo equivalents
  (defer only what truly needs an outside request).

## Repository topology (proposed for STOP A)
| Role | Repo | Base branch | Evidence / note |
|---|---|---|---|
| SOURCE | Cognition-Partner-Workshops/ts-cobol-carddemo | main | COBOL/copybooks/JCL/BMS/CSD/scheduler under `app/` (FACT) |
| DOCS | same repo | main | `functional/CARDDEMO/**` + `.migration/**` (prior engagement co-located; CONFIRMED pattern) |
| BACKEND | same repo, `spring-boot/` subdir (Maven, `com.carddemo`) | main | Java 21 / Spring Boot 3.4.5 baseline already exists on `devin/1787158883-cobol-to-spring-boot` (PR #83) — adopted as code baseline this engagement |
| FRONTEND | same repo, server-rendered inside `spring-boot/` (Thymeleaf) | main | PROPOSED: "simple web UI" per owner; no separate SPA toolchain |

Engagement branch: `devin/1789516557-carddemo-java-engagement` — all artifacts and wave work
integrate here; final STOP E merge targets `main`.

## Environments
- Local: GnuCOBOL 3.1.2 (`cobc`, `-fsign=EBCDIC`), Java 21 (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`),
  Maven 3.9+, Docker for local Postgres. No mainframe/CICS/DB2/IMS/MQ access — source-derived.
- CI: GitHub Actions (to be added with Phase 0).

## Artifact contract (all in DOCS repo, on the engagement branch, merged to main at STOP E)
- Target state: `functional/CARDDEMO/CardDemo_target_state.md`
- Module inventory: `functional/CARDDEMO/CardDemo_inventory.md`
- Stream analysis: `functional/CARDDEMO/<Stream>_analysis.md`
- Stream FR: `functional/CARDDEMO/<Stream>_functional_requirement.md`
- Program FRs: `functional/CARDDEMO/programs/<Program>_functional_requirement.md`
- Migration plan: `functional/CARDDEMO/<Stream>_migration_plan.md`
- Evidence: `functional/CARDDEMO/evidence/<stream>/**`
- Engagement ledger: `.migration/*` (this tree)

## Autonomy defaults (per owner instruction 2026-09-15)
STOP A, B, C, E: blocking — but present artifacts **with recommendations pre-filled** so a
bare "approve" suffices; batch stops where possible (A+B together). STOP D: notify-only.
All other choices: pick the sensible option, log in `06_decisions.md`, keep going.
Verification depth: checklist for every requirement + captured evidence for the sign-off set.
One child session per wave, sequential.

## Working languages
English (source labels and comments are English — FACT).

## Macro chain
`!mf_ingest_target_state` -> `!mf_migration_setup` -> `!mf_module_inventory_analysis` -> per
stream: `!mf_stream_analysis` -> `!mf_stream_fr_generation` -> `!mf_stream_migration_plan` ->
`!mf_program_fr_generation` -> `!mf_program_migration` (one child per wave) ->
`!mf_program_parity_test` -> optional `!mf_online_ui_testing` -> `!mf_stream_signoff`;
`!mf_boundary_resolution` per flagged boundary.

## Existing assets registered (cross-check only, never source of truth)
- `README.md`, `diagrams/` — shipped CardDemo documentation.
- Prior engagement branch `devin/1787242078-carddemo-premigration` (PR #88): full .migration/
  and functional/CARDDEMO/ artifact set for the .NET target — **source-side analysis reused**
  into this tree; target-side decisions re-made for Java.
- `devin/1787158883-cobol-to-spring-boot` (PR #83): existing Java 21/Spring Boot 3.4.5 port —
  controllers for all core online programs, Spring Batch job configs for CBTRN01/02/03,
  CBACT04, CBSTM03, CBEXPORT/CBIMPORT, JPA data layer, H2 seed-from-app/data. **Adopted as
  this engagement's code baseline** (decision logged 2026-09-15).
- `devin/1781095556-cobol-to-java-migration` (`app-java/`): earlier partial port — superseded
  by the PR #83 baseline; not reused.
- `aws-transform*` branches: prior experiment artifacts; not part of baseline.
