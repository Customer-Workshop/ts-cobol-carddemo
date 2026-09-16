# S-12 User Admin — Migration Plan (`!mf_stream_migration_plan`)

> Java-engagement rewrite (2026-09-15): this plan supersedes the .NET plan for this stream. Structure and
> conventions follow `S01_SignonMenu_migration_plan.md`; the target stack is the confirmed Java 21 / Spring Boot
> 3.4.5 / Thymeleaf / PostgreSQL 16 baseline, not an assumed one.

Inputs: [S12_user_admin_analysis.md](S12_user_admin_analysis.md), [S12_functional_requirement.md](S12_functional_requirement.md), [CardDemo_target_state.md](CardDemo_target_state.md), S-01 conventions ([S01_SignonMenu_migration_plan.md](S01_SignonMenu_migration_plan.md)).

## 1. Goal and scope
Port COUSR00C/COUSR01C/COUSR02C/COUSR03C (transactions CU00–CU03) to the Java target with source parity for
FR-S12-01..40. Four programs, one wave (leaf-first implementation order: COUSR01C, COUSR02C/03C, COUSR00C).

**Baseline position (FACT, verified this engagement):** `spring-boot` already contains REST CRUD:
`AdminUserController` `GET/POST /api/admin/users` + `PUT/DELETE /api/admin/users/{userId}` +
`AdminUserService` over `SecurityUserRepository`; the admin gate exists (`SecurityConfig`: `/admin/**` and
`/api/admin/**` require `ROLE_ADMIN`; `SecurityUserDetailsService` maps `user_type 'A'` → `ROLE_ADMIN`).
`MenuService` `UI_ROUTES` points COUSR00C/COUSR01C at `/api/admin/users`. Parity gaps the wave closes:
keyed browse (USRID-FIRST/LAST anchors, page number, NEXT-PAGE flag, look-ahead/look-behind) instead of
offset paging; no upper-casing of id/password (baseline normalises — the source stores as typed);
search-key restart; U/D row-selection dispatch with `from` context (S12-B4); fetch-then-act two-step
update/delete (baseline PUT/DELETE are single-call); `Please modify to update ...` no-change check;
PF3/PF4/PF5/PF12 AID semantics; verbatim message set (baseline adds a non-source `USER_TYPE_INVALID`
message and uses a `confirmation` param instead of the PF5 flow); the four Thymeleaf screens.

**S01-B5 closure (deferred single-writer decision — DECIDED this stream):** user CRUD writes go to
PostgreSQL `users` — the baseline already does this and no coexistence path is needed for a demo fixture.
USRSEC remains seed-only: `DataSeeder` imports `app/data/ASCII/usrsec.txt` into `users` at startup, and
`UsersSeedParityIntegrationTest` continues to guard the fixture (row count + per-key compare for
ADMIN001–005/USER0001–005). The orchestrator records this decision in `.migration/06_decisions.md`
(off-limits to this child); this plan is the authoritative write-up for S-12.

## 2. Target-state mapping
Profiles applied: **CORE + ONLINE + DATA/BOUNDARY** from `CardDemo_target_state.md`.

| Legacy | Target |
|---|---|
| CU00–CU03, pseudo-conversational COMMAREA | `GET/POST /api/admin/users`, `PUT/DELETE /api/admin/users/{userId}` + Thymeleaf screens under `/admin/users` (list/add/update/delete) via `com.carddemo.ui`; server-session + `ROLE_ADMIN` |
| STARTBR/READNEXT/READPREV/ENDBR USRSEC | additive `SecurityUserRepository` browse verbs: forward (`user_id >= key`, limit 11 for look-ahead), backward (`user_id < key` desc, limit 11); read-only |
| WRITE/REWRITE/DELETE USRSEC | `SecurityUserRepository.save`/`deleteById` inside service transactions; 23505 duplicate → `User ID already exist...` |
| XCTL COUSR02C/COUSR03C from COUSR00C | `ui/` redirects to `/admin/users/update?userId=&from=list` / `/admin/users/delete?userId=&from=list` (S12-B4) |
| XCTL COADM01C (PF3/PF12/exit) | redirect `/admin/menu` |
| Maps COUSR0A–COUSR3A | `templates/user-list.html`, `user-add.html`, `user-update.html`, `user-delete.html` on `layout.html`; verbatim footers (`ENTER=Continue F3=Back F7=Backward F8=Forward`; `ENTER=Add User F3=Back F4=Clear F12=Exit`; `ENTER=Fetch F3=Save&Exit F4=Clear F5=Save F12=Cancel`; `ENTER=Fetch F3=Back F4=Clear F5=Delete`) |
| AID keys | `aid` field: ENTER/PF3/PF4/PF5/PF12 per program; unmapped → `Invalid key pressed. Please see below...` |
| PASSWD DRK fields | `type=password` inputs; update fetch echoes the stored password (S12-B2 plaintext parity) |
| COMMAREA `CDEMO-CU00-*` / `CDEMO-CU0n-USR-SELECTED` | `pageState` (first/last anchors, page num, next flag) + `userId`/`from` params round-tripped per request |

## 3. Boundary decision table (decide mode over S12-B1..B5 + S01-B5 closure)
| ID | Class | Decision | Seam in target | Error/idempotency | Owner | Lead-time request | Routing point / cutover |
|---|---|---|---|---|---|---|---|
| S12-B1 | domain constraint | **DECIDED** — keep `user_type` CHECK ('A','U'); out-of-domain value fails the write (constraint violation) → source OTHER-path message (`Unable to Add User...`/`Unable to Update User...`); no new message | `AdminUserService` maps `DataIntegrityViolationException` on save to the verbatim message | write fails atomically; nothing persisted | stream | none | n/a |
| S12-B2 | field behaviour | **DECIDED (re-decided)** — plaintext storage permits full parity: fetch echoes stored password into a `type=password` input; "modified" = byte-wise compare vs stored | `AdminUserService` fetch-for-update returns `password`; update compares fields incl. password | read-only fetch; compare in service | stream | none | supersedes .NET hash deviation; flagged if hardening reintroduces hashing |
| S12-B3 | display artifact | **DECIDED — parity of data, not the artifact**: render exactly the rows returned (empty/partial) with the same message + page number | `user-list.html` renders the returned row set; no retained rows | read-only, idempotent | stream | none | n/a |
| S12-B4 | XCTL in-stream | **DECIDED** — `from` route param carries caller (`list`/`admin menu`); `userId` param drives immediate fetch (CDEMO-CU0n-USR-SELECTED parity) | `ui/` controller entry params → service fetch path | navigation only; idempotent | stream | none | cutover at wave merge |
| S12-B5 | auth gate | **DECIDED** — `SecurityConfig` `/admin/**`, `/api/admin/**` → `ROLE_ADMIN` (403 otherwise); S-01 admin-menu gate retained; programs themselves stay ungated per source | existing `SecurityConfig` + `SecurityUserDetailsService` role mapping | n/a | stream | none | already enforced in baseline |
| S01-B5 | data-writer ownership (deferred from S-01) | **DECIDED — closed**: Postgres `users` is the single writer for user CRUD; USRSEC is seed-only input to `DataSeeder`; no dual-write, no read-back | `SecurityUserRepository` write path (already live); `UsersSeedParityIntegrationTest` guards the fixture | CRUD writes commit in service transactions | stream (recorded here; orchestrator logs in `.migration/06_decisions.md`) | none | closed at wave merge |

## 4. Data and persistence
No schema change expected. `users` (`user_id varchar(8) PK`, `first_name varchar(20)`,
`last_name varchar(20)`, `password varchar(8)`, `user_type varchar(1) CHECK IN ('A','U')`) exists from V1
and supports keyed browse in both directions; fixture ids are upper-case ASCII so `ORDER BY user_id`
matches VSAM key order. **Reserved Flyway range V200x (V2000–V2009)** — ALTER only if a genuine gap
appears; otherwise untouched.

## 5. Phase 0 scaffolding deltas
**None.** Reuses the S-01 shell: `layout.html`/`carddemo.css`, `UiController` aid idiom, `SecurityConfig`
admin seam, `MenuService` registry, `CobolMessages`, Flyway/Testcontainers CI.

## 6. Waves (from analysis DAG, leaf-first; one child session per wave, sequential)
| Wave | Programs/seams | Repo area | Consumes FR docs | Boundary seams | Strict edge to next |
|---|---|---|---|---|---|
| 1 | COUSR01C → COUSR02C + COUSR03C → COUSR00C (leaf-first order, one wave): `AdminUserService`/`AdminUserController` parity extension; keyed browse verbs on `SecurityUserRepository`; `ui/` controllers + `user-list`/`user-add`/`user-update`/`user-delete` templates; `CobolMessages` verbatim additions; `UI_ROUTES` flips for COUSR00C/01C/02C/03C to `/admin/users*` | `spring-boot` (single repo) | `programs/COUSR00C_functional_requirement.md`, `COUSR01C`, `COUSR02C`, `COUSR03C` | S12-B1..B5, S01-B5 closure | none |

## 7. Per-program FR generation
The four program FR docs already exist and are updated to Java target refs in this artifact set — no
generation step needed.

## 8. Testing and verification
- Unit: `spring-boot/src/test/java/com/carddemo/service/UserAdminServiceTest.java` — fake repository;
  every FR-S12 row (edit order, modified-check, AID branches).
- Integration: `spring-boot/src/test/java/com/carddemo/UserAdminIntegrationTest.java` — Testcontainers
  Postgres seeded from `app/data/ASCII/usrsec.txt`; browse/paging both directions, duplicate add →
  `User ID already exist...`, delete, 401/403 seams; `UserAdminApiIntegrationTest` for the REST surface.
- UI: `UserListUiIntegrationTest`, `UserAddUiIntegrationTest`, `UserUpdateUiIntegrationTest`,
  `UserDeleteUiIntegrationTest` — MockMvc over the four screens; footers, AID, messages, password echo,
  `from`/`userId` prefill.
- `UsersSeedParityIntegrationTest` keeps guarding the USRSEC→`users` fixture (unchanged).
- Stream e2e: admin menu → option 01 (list) → select `U`/`D` → update/delete screens → PF3 back; option 02
  (add); PF7/PF8 paging.
- Optional `!mf_online_ui_testing` recorded pass (UI-bearing stream).
- CI: `mvn -q clean verify` green on the wave PR.

## 9. Sign-off gate
`!mf_stream_signoff` over FR-S12-01..40 acceptance criteria: every row covered by a green test; verbatim
messages byte-equal (incl. `Unable to Update User...` on COUSR03C DELETE error); admin gate enforced;
menu flags flipped; S01-B5 closure recorded.

## 10. Risks
- COUSR03C DELETE OTHER path text `Unable to Update User...` preserved verbatim — flagged, not a bug.
- Baseline `AdminUserService` upper-cases id/password and adds a non-source `USER_TYPE_INVALID` message —
  superseded by this wave (store as typed; out-of-domain type rides the OTHER-path message); noted for the
  wave owner.
- S12-B2 parity now echoes stored passwords into a `type=password` input — secure presentation matches the
  DRK field, but a future hardening decision that reintroduces hashing must re-open this boundary.
- `user_id` browse order relies on all-upper-case ASCII fixture ids; documented, no collation work needed.

## 11. Effort and sequencing
1 wave, one child session, single repo, single PR. No external lead times; sequenced after S-01 (done).
The four programs ship together; implementation order inside the wave is leaf-first
(COUSR01C → COUSR02C/03C → COUSR00C) so the list lands on already-migrated leaves.

## Validation
Waves match the analysis DAG (leaves + depth-1 dispatcher collapsed into the agreed single wave; internal
leaf-first order preserved). Every FR-S12-01..40 is covered by wave 1. Every boundary — including the
deferred S01-B5 — has decision, seam, owner, and cutover flag; no external lead-time requests required.
Scaffolding deltas explicit (none — Phase 0 complete at S-01). Plan matches the ONLINE surface profile and
cites it in §2. Shared programs: none ported on behalf of other streams.
