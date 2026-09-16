# S-01 Sign-on + Menu Shell — Independent Audit

Audited branch: `devin/1787242078-carddemo-premigration` (PR #88), HEAD `468c046`.
Auditor: independent (did not perform the migration). Date: 2026-08-21.
Scope: FR traceability, COBOL source parity, scope discipline (S-01 hard stop), test evidence, boundary decisions S01-B1..B6.

## Verdict: **PASS with findings**

All 20 stream FRs trace to program FRs, implemented behavior, and at least one test
(FR-S01-20 is the single traceability gap — see F-1). Implemented messages, catalogues,
validation ranges, and guards match the COBOL source of truth. The one approved deviation
(hashed password storage, STOP C) is implemented as approved. No downstream route program
was migrated; the route registry ships with every route disabled. Backend and frontend
builds and full test suites pass (49/49 xUnit incl. Testcontainers Postgres; 30/30 Karma).
Findings are LOW severity and none blocks sign-off.

## 1. Traceability matrix (stream FR → program FR → implementation → test)

| Stream FR | Program FR | Implementation | Test evidence | Status |
|---|---|---|---|---|
| FR-S01-01 blank user id | COSGN00C-01 | `SignInService.SignInAsync` (MissingUserId, `Please enter User ID ...`); `SignOnComponent.submit` pre-check | `SignInServiceTests.BlankUserId_...FrS0101`; `sign-on.component.spec.ts` FR-S01-01 (no API call) | PASS |
| FR-S01-02 blank password | COSGN00C-02 | MissingPassword, `Please enter Password ...` | `SignInServiceTests...FrS0102`; spec FR-S01-02 | PASS |
| FR-S01-03 user not found | COSGN00C-03 | repo miss → UserNotFound, `User not found. Try again ...`, HTTP 401 | `SignInServiceTests...FrS0103`; `SignInIntegrationTests.UnknownUserAgainstSeededStore_IsNotFound` (real Postgres) | PASS |
| FR-S01-04 wrong password | COSGN00C-04 | hash verify fail → WrongPassword, `Wrong Password. Try again ...`, 401; frontend clears password field | `SignInServiceTests...FrS0104`; `SignInIntegrationTests.WrongPasswordAgainstSeedCredential_IsRejected`; spec FR-S01-04 | PASS |
| FR-S01-05 admin sign-in | COSGN00C-05 | landing `/admin`, session UserId/UserType='A', FromProgram=COSGN00C, JWT issued | `SignInServiceTests...FrS0105`; `SignInIntegrationTests.AdminSeedCredential_...`; spec FR-S01-05 | PASS |
| FR-S01-06 regular sign-in | COSGN00C-06 | landing `/menu`, UserType='U' | `SignInServiceTests...FrS0106`; integration + spec FR-S01-06 | PASS |
| FR-S01-07 store error | COSGN00C-07 | repository exception → StoreError, `Unable to verify the User ...`, HTTP 500 | `SignInServiceTests.StoreError_...FrS0107` | PASS |
| FR-S01-08 PF3 farewell | COSGN00C-08 | `SignOnComponent.exit()` → `Thank you for using CardDemo application...`, form removed | spec FR-S01-08 | PASS |
| FR-S01-09 uppercase | COSGN00C-09 | `ToUpperInvariant` on id+password before lookup/verify | `SignInServiceTests...FrS0109`; `SignInIntegrationTests.AdminSeedCredential` (lower-case input) | PASS |
| FR-S01-10 main menu 11 options | COMEN01C | `MenuService.GetMenu(Main)` over `MenuRoutes:Main` (appsettings.json) | `MenuServiceTests.MainMenu_Lists11CatalogueOptionsInComen02yOrder`; main-menu spec FR-S01-10 | PASS |
| FR-S01-11 invalid option | COMEN01C-0x | non-numeric/0/>11 → `Please enter a valid option number...` | `MenuServiceTests.MainMenu_InvalidOption_...` (AB/0/99/empty/null); spec FR-S01-11 | PASS |
| FR-S01-12 admin-only gate | COMEN01C | `MenuService.Select` AdminOnly check (main menu, userType≠'A') → `No access - Admin Only option... ` | `MenuServiceTests.RegularUser_SelectingAdminFlaggedOption_IsDenied` (fixture per plan risk #5); `MenuApiIntegrationTests` (403 for 'U' on admin menu); spec FR-S01-12 | PASS |
| FR-S01-13 dispatch | COMEN01C | Navigate outcome with target route + program key; Angular navigates | `MenuServiceTests.EnabledOption_YieldsNavigationTargetDescriptor`; spec FR-S01-13 | PASS |
| FR-S01-14 COPAUS0C not installed | COMEN01C | option 11 `NotInstalledWhenDisabled=true` → `This option Pending Authorization View is not installed...` (error severity = red) | `MenuServiceTests.Copaus0cOption11_NotInstalled_...`; spec FR-S01-14 | PASS |
| FR-S01-15 coming soon | COMEN01C | disabled non-COPAUS0C → `This option <name> is coming soon ...` (info severity = green) | `MenuServiceTests.DisabledUnmigratedOption_...`; spec FR-S01-15 | PASS (see F-2) |
| FR-S01-16 PF3 back to sign-on | COMEN01C/COADM01C | Exit buttons → signOut + `/signin` | main-menu + admin-menu specs FR-S01-16 | PASS (see F-4) |
| FR-S01-17 admin menu 6 options | COADM01C | `GetMenu(Admin)` over `MenuRoutes:Admin`; GET guarded to 'A' | `MenuServiceTests.AdminMenu_Lists6CatalogueOptionsInCoadm02yOrder`; `MenuApiIntegrationTests.Admin_SeesTheAdminMenu`; admin spec FR-S01-17 | PASS |
| FR-S01-18 admin invalid option | COADM01C | 0/>6/non-numeric → valid-option message | `MenuServiceTests.AdminMenu_InvalidOption_...`; admin spec FR-S01-18 | PASS |
| FR-S01-19 admin dispatch | COADM01C | Navigate outcome (fixture) | `MenuServiceTests.EnabledOption_...` (FR-S01-13/19); admin spec FR-S01-19 | PASS |
| FR-S01-20 invalid AID key | COSGN00C-10/COMEN01C-08/COADM01C-05 | **no target equivalent, no test** | none | GAP (F-1) |

Wave 1 seams also verified: `UsrsecRecordParser` slices per CSUSR01Y (8/20/20/8/1) with tests; `UsrsecImportService` idempotent upsert with Testcontainers integration test; `SessionContext`/JWT claims tested (`SessionContextTests`, `MenuApiIntegrationTests`).

## 2. Source parity checks (app/cbl + copybooks vs implementation)

- **Messages** — byte-for-byte matches against working-storage literals: `Please enter User ID ...`, `Please enter Password ...`, `User not found. Try again ...`, `Wrong Password. Try again ...`, `Unable to verify the User ...` (COSGN00C.cbl:120/125/249/242/254); `Please enter a valid option number...` (COMEN01C.cbl:131, COADM01C.cbl:135); `No access - Admin Only option... ` incl. trailing space (COMEN01C.cbl:140); `This option Pending Authorization View is not installed...` (COMEN01C.cbl:163-167, `DELIMITED BY '  '` yields the full name — matches); farewell `Thank you for using CardDemo application...` (CSMSG01Y).
- **Main menu catalogue** — appsettings `MenuRoutes:Main` matches COMEN02Y.cpy exactly: 11 options, same names (double-space-trimmed), same program keys COACTVWC..COPAUS0C, same order; slot 12 correctly absent (dead capacity in source). Admin catalogue matches COADM02Y.cpy: 6 options, names/keys/order identical.
- **Validation ranges** — option must be numeric, >0, ≤ catalogue count (11 main / 6 admin) = COMEN01C.cbl:127-134 / COADM01C.cbl:131-138; input trimmed, mirroring the BMS right-justify/zero-fill idiom (tested with `" 1 "`).
- **Validation order** — matches COBOL: numeric/range → admin-only gate (main menu only) → availability (COPAUS0C probe / DUMMY idiom) → dispatch.
- **Admin-only guard** — `menu == Main && userType != 'A' && AdminOnly` mirrors COMEN01C.cbl:136-143 (`CDEMO-USRTYP-USER AND ...USRTYPE='A'`); correctly unreachable with the shipped catalogue (all 'U') and tested via fixture, exactly as plan risk #5 prescribes. Admin menu additionally guarded at HTTP (403) and Angular route (adminGuard) levels.
- **Uppercase handling** — id + password upper-cased before lookup/compare = COSGN00C.cbl:132-136; session carries upper-cased id.
- **PF3** — sign-on Exit shows the farewell and ends the session (COSGN00C.cbl:88-90); menu Exit returns to `/signin` (COMEN01C.cbl:96-98, COADM01C.cbl:100-102; S01-B3 route contract).
- **COPAUS0C distinction** — the INQUIRE PROGRAM probe (COMEN01C.cbl:147-168) maps to `NotInstalledWhenDisabled` on option 11 only: "not installed" (error/red) vs "coming soon" (info/green) for other disabled options — the red/green DFHRED/DFHGREEN distinction is preserved as severity → CSS class.
- **USRSEC read protocol** — RESP 0/13/other → found / not-found / store-error via EF Core repository (S01-B4), with the store-error path exercised by test.
- **Approved deviation (STOP C)** — passwords stored hashed (`IdentityPasswordHashingService` = ASP.NET Identity PasswordHasher); seed importer hashes SEC-USR-PWD values; comparison outcomes identical to COSGN00C.cbl:223. Implemented exactly as recorded in plan §3 / FR §11. No other undocumented deviations found beyond the LOW findings below.

## 3. Scope discipline (S-01 hard stop)

- Backend exposes only `POST /api/v1/auth/signin`, `GET /api/v1/menu`, `POST /api/v1/menu/select`. No controller, service, or entity for any route program (COACTVWC, COACTUPC, COCRDLIC, COCRDSLC, COCRDUPC, COTRN00C/01C/02C, CORPT00C, COBIL00C, COPAUS0C, COUSR00C..03C, COTRTLIC, COTRTUPC) exists.
- Route registry: all 17 entries (11 main + 6 admin) ship `"Enabled": false`; no `Route` values populated. Selecting any option yields coming-soon / not-installed — verified by tests.
- Angular routes are exactly `/signin`, `/menu`, `/admin` (+ default redirect). No downstream screens.
- Branch diff vs main touches only `.migration/`, `functional/`, `backend/`, `frontend/`, CI — no COBOL source modified.

## 4. Test evidence (run by auditor on this branch, 2026-08-21)

| Check | Command | Result |
|---|---|---|
| Backend build | `dotnet build backend/CardDemo.slnx` | Build succeeded, 0 warnings, 0 errors |
| Backend tests | `dotnet test backend/CardDemo.slnx` (Docker available; Testcontainers Postgres used) | **Passed 49 / Failed 0 / Skipped 0** (12 s) |
| Frontend build | `npm run build` (frontend/) | Succeeded; WARNING: initial bundle 513.72 kB exceeds 512 kB budget by 1.72 kB (see F-5) |
| Frontend tests | `ng test --watch=false --browsers=ChromeHeadless` | **30 of 30 SUCCESS** (Chrome Headless 137) |

## 5. Boundary decisions S01-B1..B6 — compliance

| ID | Decision | Implementation observed | Compliant |
|---|---|---|---|
| S01-B1 | Feature-flagged route registry; per-stream flag flips at merge | `MenuRouteRegistryOptions` bound from `MenuRoutes` config; `Enabled`/`Route` per option; all off | YES |
| S01-B2 | COPAUS0C availability = flag, default off | option 11 `NotInstalledWhenDisabled: true`, `Enabled: false`; distinct not-installed message | YES |
| S01-B3 | Angular router replaces CDEMO-TO-PROGRAM; stable `/signin`, `/menu`, `/admin` | `app.routes.ts` exposes exactly those routes; menu Exit navigates `/signin` | YES |
| S01-B4 | Target-owned Postgres `users` + EF Core repository, no SP; RESP protocol mapped | `UserRepository` (EF Core/Npgsql), `users` table migration matching CSUSR01Y dictionary; found/not-found/store-error | YES |
| S01-B5 | Deferred to S-12; meanwhile idempotent seed import from USRSEC source | `UsrsecImportService` upserts (re-runnable, tested); seed read from DUSRSECJ.jcl in-stream SYSUT1 records (see F-3) | YES (note F-3) |
| S01-B6 | SessionContext record + JWT claims, ported once, owner S-01 | `SessionContext(UserId, UserType, FromProgram, ToProgram)` + `JwtTokenIssuer` claims; consumed by menu authorization | YES |

## 6. Findings

| # | Severity | Finding | Recommendation |
|---|---|---|---|
| F-1 | LOW | FR-S01-20 (invalid AID key → `Invalid key pressed. Please see below...`) has no target implementation, test, or documented disposition. In the web target only ENTER/Exit interactions exist, so the trigger is arguably unreachable, but the FR is listed as KEEP and its trace ends nowhere. | Record an explicit N/A-in-target disposition (or a keydown handler decision) in the stream FR traceability section before sign-off. |
| F-2 | LOW | Coming-soon message wording: COBOL (COMEN01C.cbl:172-176) delimits the option name `BY SPACE`, so the source would emit only the first word and no space before "is" (e.g. `This option Account is coming soon ...`). Target emits the full name (`This option Account View is coming soon ...`). The source path is unreachable in the shipped catalogue (no DUMMY main-menu entries), and the generalization is deliberate (S01-B1 idiom), but it is an undocumented text deviation. | Note the deliberate generalization in the FR/plan. |
| F-3 | LOW | Admin disabled-option message: COADM01C (:150-157) emits green `This option is not installed ...` (name suppressed) for DUMMY targets; target emits `This option <name> is coming soon ...` (info). Unreachable in the shipped admin catalogue (no DUMMY entries); registry semantics are consistent across menus, but this too is an undocumented deviation. Also, seeding reads DUSRSECJ.jcl in-stream data whereas plan §4 says "import from app/data/ASCII" — same records, different carrier; the parser supports both. | Document both in the plan/FR notes. |
| F-4 | INFO | Menu Exit (PF3 equivalent) clears the JWT session before navigating to `/signin`; the COBOL XCTL back to COSGN00C did not invalidate anything (no session concept). Strictly a security improvement, consistent with re-sign-on semantics (S01-B6 token expiry → re-sign-on). | None. |
| F-5 | INFO | Frontend production build warns: initial bundle 513.72 kB exceeds the 512 kB budget by 1.72 kB (build still succeeds; CI treats it as warning). | Raise the budget or trim Material imports in a later wave. |
| F-6 | INFO | Frontend password field clears on every auth failure (COBOL clears/repositions on the wrong-password path only). Behaviorally benign. | None. |

## 7. Audit method

Read stream FR, program FRs, analysis, migration plan, ledger, and `.migration/04_boundary_register.md`; line-level comparison against `app/cbl/COSGN00C.cbl`, `COMEN01C.cbl`, `COADM01C.cbl` and copybooks `COMEN02Y.cpy`, `COADM02Y.cpy`, `CSUSR01Y.cpy`, `COCOM01Y.cpy`; full read of backend Application/Api/Infrastructure sources, appsettings route registry, Angular sign-on/menu components, guards, and all test files; independent execution of backend and frontend builds and test suites (results above). No application code was modified by this audit.
