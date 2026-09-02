# S-12 User Admin — Migration Plan (`!mf_stream_migration_plan`)

Status: complete (2026-09-02). Inputs: `S12_user_admin_analysis.md`, `S12_functional_requirement.md`, target state `CardDemo_target_state.md`, S-01 shell (`S01_SignonMenu_migration_plan.md`).

## 1. Goal and scope
Migrate COUSR00C/01C/02C/03C (CU00–CU03) to the .NET 8 + Angular 18 target on top of the S-01 shell and the shared data layer (`468e17d`), with source-derived parity for every FR-S12-01..40. The route registry flags for admin options 01–04 remain disabled; the integration stage flips them.

## 2. Target-state mapping
| Source | Target |
|---|---|
| USRSEC KSDS | existing `users` table (`CardDemoDbContext`, S-01 wave 1); no schema change |
| STARTBR/READNEXT/READPREV | `IUserRepository.BrowseForwardAsync(startKey, inclusive, pageSize)` / `BrowseBackwardAsync(beforeKey, pageSize)` — `KeyedPage<User>` (same idiom as `ICardRepository.BrowseAsync`) |
| WRITE / REWRITE / DELETE | `IUserRepository.AddAsync` (duplicate → `false`) / `UpdateAsync` (missing → `false`) / `DeleteAsync` (missing → `false`) |
| COUSR00C..03C paragraphs | `CardDemo.Application/UserAdmin/UserAdminService` — one method per COBOL paragraph, returns outcome records with exact messages |
| CU00..CU03 transactions | `UserAdminController` under `/api/v1/admin/users`: `POST list`, `POST add`, `POST update/fetch`, `POST update`, `POST delete/fetch`, `POST delete`; every action answers 200 with `{outcome, message, severity, ...}` (the screen is always re-sent in the source); admin-only via JWT `userType` claim (403 otherwise, S-01 idiom) |
| COMMAREA CU00 paging state | request/response fields `pageNum`, `nextPage`, `firstUserId`, `lastUserId` held by the Angular list component (client-side COMMAREA) |
| COMMAREA `CDEMO-CU02/03-USR-SELECTED` + `CDEMO-FROM-PROGRAM` | route query params `userId`, `from` on `/admin/users/update` and `/admin/users/delete` |
| BMS maps COUSR0A..3A | standalone components `user-list`, `user-add`, `user-update`, `user-delete` under `frontend/src/app/user-admin/` with `maxlength` = BMS field lengths, 78-char message area, PF-key handling via `user-admin-screen.ts` (`functionKeyOf`, per-screen EVALUATE EIBAID) reusing the shared invalid-key message from `shared/invalid-key.ts` |
| ERRMSG colour (red/green/neutral) | `severity`: `error` / `success` / `neutral` |

## 3. Boundary decision table
| ID | Mode | Decision |
|---|---|---|
| S12-B1 user type domain | CONSTRAIN | Keep shared `UserType` enum. Non-A/U code → write failure surfaced as `Unable to Add User...` / `Unable to Update User...` (source OTHER path). No new message. |
| S12-B2 password compare/echo | ADAPT (approved hashing deviation) | Fetch returns blank password; update treats password as modified iff it does not verify against the stored hash; passwords hashed with the S-01 `IPasswordHashingService`. |
| S12-B3 stale rows | SIMPLIFY | Render exactly the returned rows. |
| S12-B4 caller return | ADAPT | `from` query param (`COUSR00C` when navigated from the list); PF3 returns to `/admin/users` when `from=COUSR00C`, else `/admin`. |
| S12-B5 admin gate | ADAPT | API 403 for non-admin JWT; Angular routes under `adminGuard`. |

## 4. Data and persistence
No migration. Ordering for browse: `ORDER BY user_id` with `string.CompareTo` (Npgsql → text comparison). Sample USRSEC ids are upper-case alphanumerics, so collation ordering equals VSAM byte order. Password hash column is unbounded text.

## 5. Waves
1. COUSR01C add (leaf) — repository `AddAsync`, `AddAsync` service, `/add`, `UserAddComponent`.
2. COUSR02C/COUSR03C (leaves) — `UpdateAsync`/`DeleteAsync`, fetch/update/delete service methods, `UserUpdateComponent`, `UserDeleteComponent`.
3. COUSR00C list — browse repository methods, `ListAsync`, `UserListComponent` with selection → update/delete navigation.

All waves delivered in the single stream branch `devin/batch-a-s12-user-admin`.

## 6. Per-program FR generation
`programs/COUSR00C_functional_requirement.md`, `COUSR01C_…`, `COUSR02C_…`, `COUSR03C_…` — same template as S-01 (identity, trigger contract, I/O, owned FRs with cites, rules, data/boundaries, errors, hard stop, demoted mechanics, traceability).

## 7. Testing and verification
- Unit (`CardDemo.Tests/UserAdmin/UserAdminServiceTests.cs`): in-memory `IUserRepository` double, every FR with a message or outcome, store-error paths via a throwing double.
- Integration (`UserAdminIntegrationTests.cs`, Testcontainers `postgres:16`): add/duplicate, fetch/update/no-change/not-found, delete, forward/backward paging over 12+ seeded users, search-beyond-end.
- API (`UserAdminApiIntegrationTests.cs`, `WebApplicationFactory`): admin vs regular vs anonymous access on `/api/v1/admin/users/*`.
- Frontend specs per component: field lengths, display of backend messages with severity (blank-field validation is backend-owned; the UI renders the returned message), PF3/PF4/PF5/PF7/PF8/PF12 handling, invalid-key parity, selection precedence, navigation with `userId`/`from`, auto-fetch on entry.
- Commands: `dotnet test backend/CardDemo.slnx`, `npx ng test --watch=false --browsers=ChromeHeadless`, `npm run build` (frontend/).

## 8. Sign-off gate
Stream branch pushed; no PR; `.migration/` untouched; report to the orchestrator with FR ids, test counts, boundaries S12-B1..B5 and the S12-B2 deviation.

## 9. Risks
- Hash-based "modified" detection means an operator retyping the same password with different case (source: byte compare, case-sensitive) is treated as changed only if the hasher rejects it — identical to source since the hasher is case-sensitive.
- Text collation vs byte order for ids containing lower-case or punctuation (S-12 never upper-cases). Sample data is not affected.

## 10. Effort and sequencing
Single session: docs → backend (repository, service, controller, tests) → frontend (4 components, specs, routes) → test runs → push.

## Validation
All 40 FRs mapped to tests in §7; all five boundaries decided in §3; no `.migration/` edits.
