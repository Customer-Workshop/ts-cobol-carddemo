# S-01 Sign-on + Menu Shell — Migration Plan (`!mf_stream_migration_plan`)

Status: DRAFT for STOP C approval (2026-08-20). Inputs: `S01_SignonMenu_analysis.md`, `S01_SignonMenu_functional_requirement.md`, `CardDemo_target_state.md` (CORE + ONLINE + DATA/BOUNDARY, all CONFIRMED at STOP A).

## 1. Goal and scope
Migrate the CardDemo sign-on + menu shell (COSGN00C, COMEN01C, COADM01C; transactions CC00/CM00/CA00) to C#/.NET 8 + ASP.NET Core + Angular 17+ + PostgreSQL, in the single repo (`backend/`, `frontend/`). Definition of done: FR-S01-01..20 pass. Hard stop: dispatch to route programs is out of scope — targets render as navigable-but-unmigrated ("not installed") entries. Process type: ONLINE.

## 2. Target-state mapping
Profiles applied: CORE + ONLINE + DATA/BOUNDARY (`CardDemo_target_state.md`).
- API: REST under `/api/v1` — `POST /api/v1/auth/signin`, `GET /api/v1/menu` (role-filtered options), OpenAPI 3.
- Conversational state (COMMAREA) → server-issued JWT with claims `userId`, `userType`; navigation context lives in the Angular router state (replaces CDEMO-TO-PROGRAM/FROM-PROGRAM plumbing).
- UI: Angular standalone components + Angular Material; screens `SignOnComponent`, `MainMenuComponent`, `AdminMenuComponent`; WCAG AA.
- Layers: Controller → Service → Repository; DTOs as records; explicit mappers; xUnit + FluentAssertions; Testcontainers Postgres.
- Persistence: EF Core 8 + Npgsql, EF migrations.

## 3. Boundary decision table (decide mode over S01-B1..B6)
| ID | Class | Decision | Seam in target | Error/idempotency | Owner | Lead-time request | Routing point / cutover |
|---|---|---|---|---|---|---|---|
| S01-B1 | B5 out | **Feature-flagged route registry.** Menu options carry `migrated=false` until each stream lands; selecting them yields the "not installed"-style message (FR-S01-14 idiom generalized) | `MenuOptionCatalog` config + Angular route guard | none needed (pure nav) | S-01 wave 3 | none | per-stream flag flips when that stream's wave merges; decommission legacy menu at module end |
| S01-B2 | B5 probe | Same registry: COPAUS0C availability = feature flag (default off; extension not migrated) | same as S01-B1 | — | S-01 wave 3 | none | flag flips if/when S-19 migrates |
| S01-B3 | B5 in | Angular router `returnUrl`/nav state replaces CDEMO-TO-PROGRAM; menu exposes stable routes `/menu`, `/admin`, `/signin` | route contract documented in FR shell doc | — | S-01 wave 3 | none | later streams consume routes as-is |
| S01-B4 | B4 leaf | **Port to target-owned Postgres.** `users` table + `IUserSecurityRepository` (EF Core). No stored procedures (target owns data; logical layer = CICS READ). RESP 0/13/other → found/not-found/store-error results | `CardDemo.Infrastructure.UserSecurityRepository` | store-error → FR-S01-07 message; reads idempotent | S-01 wave 1 | none (no external DBA; Postgres is ours) | strangler point = sign-on API; legacy CICS remains authoritative until stream sign-off; decommission USRSEC read path at module cutover |
| S01-B5 | B10 | **Deferred single-writer decision** — dependency: S-12 (User Admin) migration; impact: until then Postgres `users` is seeded/refreshed by import from USRSEC ASCII data (`app/data/ASCII` seed); re-entry: decided in S-12's plan | seed script + documented import parity check | import re-runnable (upsert) | S-01 wave 1 (seed), S-12 (writer) | none now | S-12 plan flips writer to Postgres |
| S01-B6 | B10 | **Port COMMAREA contract once, here**: `SessionContext` record (userId, userType, fromProgram/context fields as claims/route state); module property owned by S-01 | `CardDemo.Application.SessionContext` + JWT claims | token expiry → re-sign-on | S-01 wave 1 | none | all later online streams consume |
No stored-procedure boundaries. No external enablement lead times exist for this stream (all seams are inside the confirmed single repo + Postgres); therefore no requests fired — recorded explicitly.
Deviation carried from FR §11: passwords hashed (ASP.NET Identity PasswordHasher) in target; seed hashes imported values. Requires STOP C confirmation.

## 4. Data and persistence
- Table `users` (from CSUSR01Y.cpy dictionary): user_id varchar(8) PK, first_name varchar(20), last_name varchar(20), password_hash text, user_type char(1) CHECK ('A','U').
- Seeding: import from `app/data/ASCII` USRSEC seed via EF-migration seed/import script; parity check row-count + per-key compare.
- Data target pinned: PostgreSQL 16, EF Core migrations, Testcontainers for integration tests.

## 5. Phase 0 scaffolding (deltas — nothing exists yet, first stream)
Build in Phase 0 (per target state, reuse thereafter): `backend/CardDemo.sln` with Api/Application/Domain/Infrastructure projects (+ test projects), Angular 17 workspace in `frontend/` with Material + app shell, docker-compose Postgres profile, GitHub Actions CI (build, xUnit, Angular lint+test). `CardDemo.Batch` NOT scaffolded (no batch surface in S-01).

## 6. Waves (from analysis DAG, leaf-first; one child session per wave, sequential)
| Wave | Programs/seams | Repos | Consumes FR docs | Boundary seams | Strict edge to next |
|---|---|---|---|---|---|
| 1 | Data + session seams: users schema/repository, seed import, SessionContext/JWT | backend/ | COSGN00C program FR (data sections) | S01-B4, B5(seed), B6 | wave 2 needs repository + session |
| 2 | COSGN00C: auth service, `POST /auth/signin`, Angular sign-on screen | backend/, frontend/ | COSGN00C FR | S01-B4, B6 | wave 3 needs auth + shell |
| 3 | COMEN01C + COADM01C: menu catalog, `GET /menu`, menu screens, route registry/flags | backend/, frontend/ | COMEN01C FR, COADM01C FR | S01-B1, B2, B3 | — |
Single-repo topology: one PR per wave.

## 7. Per-program FR generation
`!mf_program_fr_generation` runs after STOP C approval, before wave 1, for all three programs at once (small stream). No program enters a wave without its doc.

## 8. Testing and verification
- Per program: `!mf_program_parity_test` — xUnit behavioral tests mapped to FR-S01 rows (auth outcomes incl. RESP 0/13/other protocol, option validation 1..11/1..6, admin gate, uppercasing), Testcontainers Postgres, seed-parity check for the import.
- Stream E2E: sign-on → menu → option dispatch (flags off ⇒ not-installed message) → PF3-equivalent back-outs.
- CI regression gate: backend build+tests and frontend lint+tests must be green per wave PR.
- UI-bearing: optional `!mf_online_ui_testing` recorded pass after waves complete (STOP D window).

## 9. Sign-off gate
`!mf_stream_signoff` executes FR §8 acceptance criteria one-by-one against the running target; every FR row gets pass/fail evidence; then independent audit; STOP E for merge authorization.

## 10. Risks (carried from analysis + plan-level)
1. Password hashing deviation (needs STOP C confirmation). MEDIUM.
2. USRSEC dual-writer window until S-12 (deferred decision S01-B5 with re-entry recorded). MEDIUM.
3. First-stream Phase 0 scaffolding is the largest delta; template drift risk for later streams — mitigate by treating Phase 0 output as the module standard. MEDIUM.
4. Feature-flag registry must match `COMEN02Y`/`COADM02Y` catalogues exactly (11+6 options, order and admin flags). LOW.
5. FR-S01-12 currently unreachable with shipped catalogue (all 'U'); test via catalogue fixture. LOW.

## 11. Effort and sequencing
Phase 0 + program FRs + 3 waves + parity + sign-off ≈ 1 execution session with 3 sequential wave children after STOP C. No external lead-time waits.

## Validation
Waves match analysis DAG (topological); FR-S01-01..09→wave 2, 10..20→wave 3, data derivations→wave 1 (all 20 covered); all 6 boundaries decided (1 deferral with dependency/impact/re-entry); scaffolding deltas explicit (§5); ONLINE surfaces + UI verification mode per ONLINE profile; shared seams (session, shell) ported once, owner S-01.
