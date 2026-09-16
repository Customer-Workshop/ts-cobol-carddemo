# S-01 Sign-on + Menu Shell — Migration Plan (`!mf_stream_migration_plan`)

Status: DRAFT for STOP C approval (2026-09-15, Java engagement — supersedes the 2026-08-20
.NET draft). Inputs: `S01_SignonMenu_analysis.md`, `S01_SignonMenu_functional_requirement.md`,
`CardDemo_target_state.md` (CORE + ONLINE + DATA/BOUNDARY, all CONFIRMED at STOP A),
baseline = `spring-boot/` port adopted from the open baseline PR (`mvn clean verify` green,
34 tests, 2026-09-15).

## 1. Goal and scope
Migrate the CardDemo sign-on + menu shell (COSGN00C, COMEN01C, COADM01C; transactions
CC00/CM00/CA00) to Java 21 + Spring Boot 3.4.5 + server-rendered web UI + PostgreSQL, in the
single repo (`spring-boot/`). Definition of done: FR-S01-01..20 pass. Hard stop: dispatch to
route programs is out of scope — targets render as navigable-but-unmigrated ("not installed")
entries. Process type: ONLINE.

**Baseline position (FACT, verified this engagement):** the adopted port already implements
the whole stream as a JSON API — `AuthController`/`AuthService` (signon/signoff, uppercase
rule, RESP-protocol error mapping, role-based landing menu id), `MenuService`/`MenuController`
(11 main + 6 admin options matching `COMEN02Y`/`COADM02Y` catalogues incl. order and required
user type, option validation, admin gate, `implemented`/`available` flags = "not installed"/
"coming soon" idioms), `SecurityUserRepository` + `SecurityUser` entity + `DataSeeder`
(USRSEC ASCII fixture), Spring Security session context. What it lacks: **web UI, Postgres
(default H2), Flyway, CI**. S-01's work is closing those deltas and proving the API layer
against the FR matrix — not rewriting it.

## 2. Target-state mapping
Profiles applied: CORE + ONLINE + DATA/BOUNDARY (`CardDemo_target_state.md`).
- API: REST under `/api` (baseline, keep) — `POST /api/auth/signon`, `POST /api/auth/signoff`,
  `GET /api/menu`, `GET /api/admin/menu`, `POST /api/menu/select`, `POST /api/admin/menu/select`.
- Conversational state (COMMAREA) → server-side HTTP session + Spring Security context
  carrying user id/type; menu navigation context carried by web routes (replaces
  CDEMO-TO-PROGRAM/FROM-PROGRAM plumbing).
- UI: **Thymeleaf server-rendered screens** inside the same app (`templates/`, `static/`):
  `signon.html`, `menu.html`, `admin-menu.html`; verbatim COBOL messages; PF-key semantics as
  buttons/links (PF3 = back to sign-on). A thin `UiController` serves the screens and calls
  the same services the REST API uses — one logic path, two surfaces.
- Layers: Controller → Service → Repository; records for DTOs; JUnit 5 + AssertJ.
- Persistence: Spring Data JPA + **Flyway** migrations; PostgreSQL 16 via docker-compose
  locally and Testcontainers in tests; H2 remains only for fast unit tests.

## 3. Boundary decision table (decide mode over S01-B1..B6)
| ID | Class | Decision | Seam in target | Error/idempotency | Owner | Lead-time request | Routing point / cutover |
|---|---|---|---|---|---|---|---|
| S01-B1 | B5 out | **Feature-flagged route registry.** Menu options carry `implemented`/`available` flags until each stream lands; selecting an unavailable one yields the "not installed"-style message (FR-S01-14 idiom generalized) | `MenuService` option catalogue + UI/REST menu select | none needed (pure nav) | S-01 wave 3 | none | per-stream flag flips when that stream's wave merges; legacy menu decommissioned at module end |
| S01-B2 | B5 probe | Same registry: COPAUS0C availability = `available=false` flag (extension stream S-19) | same as S01-B1 | — | S-01 wave 3 | none | flag flips if/when S-19 migrates |
| S01-B3 | B5 in | Web routes replace CDEMO-TO-PROGRAM; menu exposes stable UI paths `/signon`, `/menu`, `/admin/menu` plus the REST menu endpoints | route contract documented in FR shell doc | — | S-01 wave 3 | none | later streams consume routes as-is |
| S01-B4 | B4 leaf | **Port to target-owned Postgres.** `users` table + `SecurityUserRepository` (Spring Data JPA). No stored procedures (target owns data; logical layer = CICS READ). RESP 0/13/other → found/not-found/store-error results | `spring-boot/.../repository/SecurityUserRepository` + Flyway `users` migration | store-error → FR-S01-07 message; reads idempotent | S-01 wave 1 | none (Postgres is ours) | strangler point = sign-on API; legacy CICS authoritative until stream sign-off; decommission USRSEC read path at module cutover |
| S01-B5 | B10 | **Deferred single-writer decision** — dependency: S-12 (User Admin); impact: until then Postgres `users` is seeded from USRSEC ASCII data (`app/data/ASCII`) and user CRUD under S-12 writes Postgres only; re-entry: decided in S-12's plan | `DataSeeder` import + documented parity check | import re-runnable (upsert) | S-01 wave 1 (seed), S-12 (writer) | none now | S-12 plan flips writer to Postgres |
| S01-B6 | B10 | **Port COMMAREA contract once, here**: server session + Spring Security context carrying user id/user type (from/to program = web nav state); module property owned by S-01 | `security/` config + session; consumed by menu authorization | session expiry → re-sign-on | S-01 wave 1 | none | all later online streams consume |
No stored-procedure boundaries. No external enablement lead times exist for this stream (all
seams are inside the confirmed single repo + Postgres); therefore no requests fired — recorded
explicitly.

**Password storage decision (re-presented at this STOP C):** the baseline keeps a
plaintext-compatible encoder (`UsrsecPlaintextPasswordEncoder`) that matches the USRSEC
fixture exactly — exact source parity, demo fixture users only. The prior (.NET) engagement
stored hashes as an approved deviation. **Recommendation: keep plaintext-compatible**
(fixture data only, simplest parity; note in SecurityConfig points at this row). Alternative
available on request: switch seed to BCrypt hashes, comparison outcomes unchanged.

## 4. Data and persistence
- Table `users` (from CSUSR01Y.cpy dictionary): `user_id varchar(8) PK, first_name
  varchar(20), last_name varchar(20), password varchar(8), user_type char(1) CHECK in
  ('A','U')`. The rest of the JPA model's tables get their Flyway migrations in each owning
  stream's Phase 1 — S-01 creates the Flyway seam plus the `users` migration only.
- Seeding: existing `DataSeeder` (USRSEC ASCII fixture) unchanged; wave 1 adds a parity
  check (row count + per-key compare vs fixture, 10 users ADMIN001-005/USER0001-005).
- Data target pinned: PostgreSQL 16, Flyway migrations, Testcontainers for integration
  tests; `postgres` Spring profile; `docker-compose.yml` at repo root; H2 remains default
  dev/test profile so `mvn spring-boot:run` works without Docker.

## 5. Phase 0 scaffolding deltas (first stream — largest Phase 0 of the module)
Reuse the baseline; do not fork it. Missing pieces this stream builds:
- `spring-boot-starter-thymeleaf` + base layout template (header with app title/date/time,
  message line) — becomes the module UI shell.
- Postgres driver + Flyway deps; `application-postgres.properties`; root `docker-compose.yml`
  (postgres:16); `src/main/resources/db/migration/V1__users.sql`.
- GitHub Actions CI: `.github/workflows/ci.yml` — Java 21 `mvn clean verify` (this is the
  CI gate every later stream reuses).
- Not scaffolded here (belong to later streams): other tables' migrations, batch Postgres
  schema (S-14 Phase 1), extension tables (S-19..S-22).

## 6. Waves (from analysis DAG, leaf-first; one child session per wave, sequential)
| Wave | Programs/seams | Repo area | Consumes FR docs | Boundary seams | Strict edge to next |
|---|---|---|---|---|---|
| 1 | Phase 0/1: Thymeleaf shell + layout, Postgres profile + docker-compose + Flyway V1 users, CI workflow, seed-parity check, session seam verification | spring-boot/ | COSGN00C program FR (data/session sections) | S01-B4, B5(seed), B6 | wave 2 needs UI shell + Postgres seam |
| 2 | COSGN00C: `signon.html` + `UiController` sign-on flow, verbatim messages, PF3/invalid-key semantics, role landing, FR-S01-01..09 test matrix | spring-boot/ | COSGN00C FR | S01-B4, B6 | wave 3 needs sign-on flow |
| 3 | COMEN01C + COADM01C: `menu.html` + `admin-menu.html`, option validation incl. admin gate + not-installed/coming-soon, route registry pointing at UI routes, PF3 exit, FR-S01-10..20 test matrix | spring-boot/ | COMEN01C FR, COADM01C FR | S01-B1, B2, B3 | — |
Single-repo topology: one PR per wave into the engagement branch; wave N+1 starts only after
wave N's PR is merged and CI green.

## 7. Per-program FR generation
Already produced by the prior engagement and carried over with Java terminology updates
(`programs/COSGN00C_functional_requirement.md`, `COMEN01C`, `COADM01C`). Verified complete
against the three sources this session; no regeneration needed. No program enters a wave
without its doc — satisfied.

## 8. Testing and verification
- Per wave: `!mf_program_parity_test` — JUnit/MockMvc behavioral tests mapped to FR-S01 rows
  (auth outcomes incl. RESP 0/13/other protocol, uppercasing, option validation 1..11/1..6,
  admin gate, not-installed/coming-soon, PF3 semantics); Testcontainers Postgres
  integration test for the users table + seed parity.
- Stream E2E: sign-on → menu → option select (flagged-off ⇒ not-installed message) →
  PF3 back-outs, exercised over both REST and UI surfaces.
- CI regression gate: the new GitHub Actions workflow must be green on every wave PR.
- UI-bearing: `!mf_online_ui_testing` recorded pass over the three screens (STOP D window).

## 9. Sign-off gate
`!mf_stream_signoff` executes FR §8 acceptance criteria one-by-one against the running app;
every FR row gets pass/fail evidence; then an independent audit by a fresh session; STOP E
for merge authorization.

## 10. Risks
1. Plaintext-compatible password encoder (demo fixture only) — flagged decision above, LOW
   as proposed; MEDIUM if hardened later (affects S-12 user writes).
2. USRSEC dual-writer window until S-12 (deferred decision S01-B5 with re-entry). MEDIUM.
3. First-stream Phase 0 sets the module's UI/CI/Postgres conventions — template drift risk
   for later streams; mitigate by treating wave-1 output as the module standard. MEDIUM.
4. Option catalogue must match `COMEN02Y`/`COADM02Y` exactly (11+6, order + admin flags);
   baseline matches — keep a test pinning it. LOW.
5. FR-S01-12 admin-only rejection unreachable with shipped catalogue (all 'U'); test via
   catalogue fixture. LOW.

## 11. Effort and sequencing
Program FRs already exist; 3 sequential wave children after STOP C. No external lead-time
waits. S-01 is the heaviest single stream (carries module Phase 0), later streams reuse it.

## Validation
Waves match analysis DAG (topological); FR-S01-01..09 → wave 2, 10..20 → wave 3, data
derivations → wave 1 (all 20 covered); all 6 boundaries decided (1 deferral with
dependency/impact/re-entry recorded); scaffolding deltas explicit (§5); ONLINE surfaces +
UI verification mode per ONLINE profile; shared seams (session, shell, CI, Flyway, Postgres)
ported once, owner S-01.
