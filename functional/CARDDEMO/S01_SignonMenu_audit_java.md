# S-01 Sign-on + Menu Shell — Independent Audit (Java / Spring Boot)

Audited branch: `devin/1789516557-carddemo-java-engagement`, HEAD `172985d`.
Auditor: independent (did not perform the migration). Date: 2026-09-16.
Scope: FR traceability, COBOL source parity, scope discipline (S-01 hard stop), test
evidence, boundary decisions S01-B1..B6, CI gate. Prior .NET audit:
`S01_SignonMenu_audit.md`.

## Verdict: **PASS with findings**

All 20 stream FRs trace to implemented behavior and at least one test or evidence item —
no traceability gaps. Message texts, both option catalogues, validation order, role
landing, AID-key semantics, and the unsigned bounce match the COBOL source of truth
byte-for-byte where the FR doc requires it. All three in-scope programs (COSGN00C,
COMEN01C, COADM01C) are fully implemented; no missing, stubbed, or mock-only
implementation exists. The two approved deviations (plaintext-compatible password
encoder, Flyway V2 baseline-all-tables) are implemented as recorded.

Independent re-run: `mvn clean verify` on the engagement branch produced **69 tests,
0 failures, 0 errors**. A hands-on Postgres-profile smoke (fresh `postgres:16`
container, Flyway V1–V3 applied, 10 users seeded from the EBCDIC USRSEC fixture)
reproduced sign-on, role landing, menu render, dispatch, not-installed, invalid-AID and
the unsigned bounce against the real datastore. CI `verify` job is green on the last
merged wave PR. Findings are LOW or INFO; none blocks sign-off.

## 1. Traceability matrix (stream FR → implementation → test/evidence)

| Stream FR | Implementation | Test / evidence | Status |
|---|---|---|---|
| FR-S01-01 blank user id | `AuthService.signon` `AuthService.java:40-41` → 400 `USER_ID_REQUIRED` (`CobolMessages.java:4`) | `SignOnUiIntegrationTest.blankUserIdShowsUserIdRequiredMessage_frS0101`; `ApiIntegrationTest` blank-id case; evidence scenario S1 | PASS |
| FR-S01-02 blank password | `AuthService.java:43-44` → 400 `PASSWORD_REQUIRED` | `...blankPasswordShowsPasswordRequiredMessage_frS0102`; evidence S1 | PASS (cursor note F-3) |
| FR-S01-03 user not found | `AuthService.java:48-49` `findById` miss → 401 `USER_NOT_FOUND` | `...unknownUserShowsUserNotFoundMessage_frS0103`; live Postgres check (`NOPE` → verbatim message) | PASS |
| FR-S01-04 wrong password | `AuthService.java:50-51` encoder mismatch → 401 `WRONG_PASSWORD` | `...wrongPasswordShowsMessageAndClearsPassword_frS0104`; live Postgres check | PASS (cursor note F-3) |
| FR-S01-05 admin landing | `AuthService.java:60` 'A'→`/api/admin/menu`; `UiController.java:82` → `/admin/menu`; SecurityContext carries id+`ROLE_ADMIN` | `...adminSignonRedirectsToAdminMenuWithAdminSession_frS0105`; `AuthSessionSeamIntegrationTest`; live Postgres check | PASS |
| FR-S01-06 regular landing | 'U'→`/api/menu` / `/menu`; `ROLE_USER` | `...regularSignonRedirectsToMainMenuWithUserSession_frS0106`; seam test; live Postgres check | PASS |
| FR-S01-07 store error | `UiController.java:85-86` catch `RuntimeException` → `USER_VERIFY_FAILED` verbatim | `SignOnUiStoreErrorIntegrationTest.storeErrorShowsUnableToVerifyUserMessage_frS0107` (mocked repo failure); evidence coverage note | PASS (REST gap F-2) |
| FR-S01-08 PF3 farewell | `UiController.java:70-74` → `signoff` + `exit.html` `THANK_YOU` (`CobolMessages.java:9`) | `...pf3ShowsFarewellTextAndEndsSession_frS0108`; evidence S8 | PASS |
| FR-S01-09 uppercase | `AuthService.java:46-47` `toUpperCase` on both fields before lookup/compare | `...lowerCaseCredentialsAreUpperCasedBeforeAuth_frS0109`; live check (`admin001`/`password` lower-case succeeds) | PASS |
| FR-S01-10 main menu 11 options | `MenuService` MAIN catalogue (names/order/flags = COMEN02Y); `menu.html` render | `MenuUiIntegrationTest.mainMenuListsAllElevenCatalogueOptionsVerbatim_frS0110`; `MenuServiceTest.cataloguePinsComen02yAndCoadm02yVerbatim`; live `/menu` render | PASS |
| FR-S01-11 invalid option | `MenuService.select` `:77-87` — `\d+` then 1..size | `...invalidOptionRedisplaysMenuWithValidOptionMessage_frS0111` (0/12/AB); REST parity in `ApiIntegrationTest` | PASS |
| FR-S01-12 admin-only gate | `MenuService.requireAccess` `:132-134` → 403 `ADMIN_ONLY`; `canAccess` `:147` | `MenuUiCatalogueIntegrationTest.regularUserSelectingAdminOptionShowsAdminOnlyMessage_frS0112` (injected 'A'-flagged fixture); `MenuServiceTest.regularUserIsDeniedConfiguredAdminOnlyOption` | PASS — unreachable in shipped data (F-5) |
| FR-S01-13 dispatch | `UiController.selectOption` `:157-159` → `menuService.uiRoute` (`UI_ROUTES` `:109`) | `...validOptionRedirectsToBrowsableRouteOrShowsNotInstalled_frS0113`; `MenuServiceTest.uiRouteResolvesOnlyBrowsableEndpoints`; live check (option 3 → 302 `/api/cards`) | PASS (JSON landing, F-4) |
| FR-S01-14 option 11 not installed | `MenuService.selection` `:94-95` `implemented=false` → `optionNotInstalled` | `...pendingAuthorizationShowsNotInstalledMessage_frS0114`; live check verbatim `This option Pending Authorization View is not installed...` | PASS |
| FR-S01-15 coming soon | `MenuService.selection` `:96-97` `available=false` → `optionComingSoon`; `UiController.java:153` `messageStyle="info"` (green) | `MenuUiCatalogueIntegrationTest.placeholderOptionShowsComingSoonMessageInGreen_frS0115` (injected fixture); `MenuServiceTest.placeholderOptionSelectionShowsComingSoon` | PASS — unreachable in shipped data (F-5); text note F-1 |
| FR-S01-16 PF3 to sign-on | `UiController.selectOption` `:137-139` PF3 → signoff + `redirect:/signon` | `...pf3SignsOffAndReturnsToSignon_frS0116` (both menus, session invalidated); live check | PASS |
| FR-S01-17 admin menu 6 options | `MenuService` ADMIN catalogue (names/order/flags = COADM02Y); `admin-menu.html` | `...adminMenuListsAllSixCatalogueOptionsVerbatim_frS0117`; live `/admin/menu` render | PASS |
| FR-S01-18 admin invalid option | same `select` validation, size 6 | `...invalidAdminOptionRedisplaysMenuWithValidOptionMessage_frS0118` (0/7/AB); live check (option 7 → message) | PASS |
| FR-S01-19 admin dispatch | same `uiRoute` path; admin routes → `/api/admin/users` etc.; `COTRTLIC`/`COTRTUPC` not installed | `...validAdminOptionRedirectsOrShowsNotInstalled_frS0119` | PASS |
| FR-S01-20 invalid AID | keydown JS in all three templates (F3→`PF3`, other F-keys→`aid=<key>`); `UiController.java:75-76`, `:143-144` → `INVALID_KEY_PRESSED` redisplay | `SignOnUiIntegrationTest.unmappedAidShowsInvalidKeyMessage_frS0120`; `MenuUiIntegrationTest.unmappedAidShowsInvalidKeyMessageOnBothMenus_frS0120`; live check (F7 → `Invalid key pressed. Please see below...`) | PASS |

Seams also verified: `DataSeeder.parseUsers` slices USRSEC at 0-8/8-28/28-48/48-56/56-57
per CSUSR01Y (IBM037 charset, `../app/data` default dir); `UsersSeedParityIntegrationTest`
asserts all 10 seeded users per-key against the real fixture; `AuthSessionSeamIntegrationTest`
proves the session carries user id + role; `SecurityConfig` guards `/admin/**` and
`/api/admin/**` to `ROLE_ADMIN` and bounces unsigned UI requests to `/signon` (the
EIBCALEN=0 equivalent — verified live: unsigned `GET /menu` → 302 `/signon`, unsigned
`GET /api/menu` → 401 JSON).

## 2. Source parity checks (app/cbl + copybooks vs implementation)

- **Messages** — byte-for-byte against working-storage literals and CSMSG01Y:
  `Please enter User ID ...`, `Please enter Password ...`, `User not found. Try again ...`,
  `Wrong Password. Try again ...`, `Unable to verify the User ...` (COSGN00C.cbl
  :120/:125/:249/:242/:254 → `CobolMessages.java:4-8`); `Please enter a valid option number...`
  (COMEN01C.cbl:131, COADM01C.cbl:135 → `CobolMessages.java:34`); `No access - Admin Only
  option... ` **including the trailing space** (COMEN01C.cbl:140 → `CobolMessages.java:44`);
  `Invalid key pressed. Please see below...` and `Thank you for using CardDemo
  application...` (CSMSG01Y.cpy:18-21 → `CobolMessages.java:9-10`). Not-installed emits the
  full option name with a space before "is" (`CobolMessages.java:111-113`), matching the
  COMEN01C `DELIMITED BY '  '` source behavior — live-verified output: `This option
  Pending Authorization View is not installed...`.
- **Main menu catalogue** — `MenuService` MAIN list matches COMEN02Y.cpy exactly: 11
  options, same names, same program keys COACTVWC..COPAUS0C, same order, all
  `requiredUserType='U'`; pinned verbatim by `containsExactly` in `MenuServiceTest`.
  Slot 12 correctly absent (dead capacity in source).
- **Admin menu catalogue** — matches COADM02Y.cpy: 6 options (COUSR00C..03C, COTRTLIC,
  COTRTUPC), names/keys/order identical.
- **Validation order** — mandatory user id → mandatory password → upper-case → keyed
  read → RESP protocol (found/mismatch/not-found/store-error), matching
  COSGN00C.cbl:118-256. Menu: numeric check → range (1..11 / 1..6) → access gate →
  availability → dispatch, matching COMEN01C.cbl:127-187. Blank/whitespace/non-numeric
  options all yield the valid-option message (blank→zero in source is equivalent — 0 is
  out of range either way).
- **Role landing** — 'A'→admin menu, 'U'→main menu (COSGN00C.cbl:223-240 →
  `AuthService.java:60` + `UiController.java:82`).
- **AID-key semantics** — ENTER submits; PF3 exits (sign-off at sign-on, return to
  sign-on at menus); every other mapped key redisplays with the invalid-key message.
  The web AID set is F1–F12 per FR doc §9 disposition: F3→`PF3`, all others→`aid=<key>`
  → invalid-key path. Verified live (F7 on main menu).
- **Not-installed vs coming-soon** — `implemented=false` → red not-installed (the
  COPAUS0C-probe equivalent); `implemented && !available` → green coming-soon (the
  DUMMY-target equivalent). Distinction preserved; shipped catalogue exercises only the
  not-installed branch (option 11).
- **EIBCALEN/unsigned bounce** — unsigned UI requests redirect to `/signon`; unsigned
  API requests get 401 JSON. Equivalent to the EIBCALEN=0 re-entry check.
- **Field fidelity** — userid `maxlength=8`, password `maxlength=8` `type=password`
  (DRK non-display equivalent), option input `maxlength=2`; labels verbatim
  (`User ID     :`, `Password    :`, `Type your User ID and Password, then press
  ENTER:`); header carries Tran/Prog/title/date/time. Entered user id is repopulated on
  error redisplay. Password field clears on error redisplay (see F-3 for cursor).
- **Approved deviations** — plaintext-compatible password storage (STOP C decision)
  verified: seeded users authenticate with the fixture password; Flyway V2 baselines all
  ten tables plus the users table, as recorded in `.migration/06_decisions.md`.

## 3. Scope discipline (S-01 hard stop)

- UI surfaces are exactly `/`, `/signon`, `/menu`, `/admin/menu` (+ `/exit`); templates:
  `index`, `signon`, `menu`, `admin-menu`, `exit`, `layout`. No screen exists for any
  route program (COACTVWC, COACTUPC, COCRDLIC, COCRDSLC, COCRDUPC, COTRN00C/01C/02C,
  CORPT00C, COBIL00C, COPAUS0C, COUSR00C..03C, COTRTLIC, COTRTUPC).
- Route-program REST controllers (`AccountController`, `CardController`, batch jobs,
  etc.) belong to the adopted pre-migration baseline — they predate all three S-01 wave
  commits and are not S-01 deliverables. The S-01 waves added only `UiController`, menu
  service/registry, security config, templates/css, Flyway migrations, seeder, CI, and
  tests.
- `git diff main..HEAD -- app/` is empty — no COBOL source, copybook, BMS map, or JCL
  was modified.

## 4. Independent execution (run by auditor on this branch, 2026-09-16)

| Check | Command | Result |
|---|---|---|
| Full suite | `cd spring-boot && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn clean verify` | **BUILD SUCCESS — Tests run: 69, Failures: 0, Errors: 0** (per-class: ApiIntegrationTest 18, SignOnUiIntegrationTest 12, MenuUiIntegrationTest 11, BatchJobIntegrationTest 7, MenuServiceTest 5, CobolFieldReaderTest 4, MenuUiCatalogueIntegrationTest 4, AuthSessionSeamIntegrationTest 2, DataSeederIntegrationTest 2, UiShellIntegrationTest 1, SignOnUiStoreErrorIntegrationTest 1, DataSeederForceIntegrationTest 1, UsersSeedParityIntegrationTest 1) |
| Postgres smoke | `docker run postgres:16 -p 5433`; app with `SPRING_PROFILES_ACTIVE=postgres`, `DB_URL=jdbc:postgresql://localhost:5433/carddemo` | Flyway applied V1+V2+V3; seeder loaded all fixture rows (users=10, accounts=50, cards=50, transactions=300, …). REST sign-on: admin→`/api/admin/menu`, wrong pw→`Wrong Password. Try again ...`, unknown→`User not found. Try again ...`, blank→`Please enter User ID ...`. UI: USER0001 sign-on→302 `/menu`, menu render, option 3→302 `/api/cards`, option 11→verbatim not-installed, PF3→`/signon` + session invalidated, F7→invalid-key, unsigned `/menu`→302 `/signon`, unsigned `/api/menu`→401 |
| CI gate | `.github/workflows/ci.yml` runs `mvn -B clean verify` in `spring-boot/` on push and PR | `verify` job green on PR #103 (last merged wave); required to merge |

Note: the test suite runs on the in-memory H2 profile by design; the Postgres path was
proven hands-on above (Flyway + seed parity + full sign-on/menu flow). The playbook's
"no evidence against an in-memory profile" rule applies to the evidence *set* — the
recorded UI evidence used the real running app; the H2 restriction is limited to the
JUnit harness and is noted here for completeness.

## 5. Boundary decisions S01-B1..B6 — compliance

| ID | Decision | Implementation observed | Compliant |
|---|---|---|---|
| S01-B1 | Feature-flagged route registry; per-stream flag flips at merge | `MenuOption.implemented`/`available` flags + `UI_ROUTES` dispatch map in `MenuService`; flags currently set where baseline REST endpoints exist (see F-4) | YES (note F-4) |
| S01-B2 | COPAUS0C availability = flag, default off until S-19 | Option 11 ships `implemented=false` → distinct red not-installed message | YES |
| S01-B3 | Stable return routing; `/signon`, `/menu`, `/admin/menu` routes | PF3 → signoff + `redirect:/signon`; menu URLs stable; `menu.html`/`admin-menu.html` render | YES |
| S01-B4 | Target-owned Postgres `users` + repository; RESP 0/13/other protocol mapped | `V1__users.sql` (CSUSR01Y dictionary), `SecurityUserRepository`; found/not-found/store-error all mapped (UI verbatim; REST generic — F-2) | YES |
| S01-B5 | Single-writer decision deferred to S-12; seed parity meanwhile | `DataSeeder` idempotent import from EBCDIC USRSEC; `UsersSeedParityIntegrationTest` pins all 10 users; re-entry: S-12 adopts the repository as writer | YES (deferred, documented) |
| S01-B6 | Session-state seam ported once, owner S-01 | Spring `SecurityContext` in server session carries user id + role; `/api/auth/session` returns `userType` 'A'/'U'; `AuthSessionSeamIntegrationTest` | YES |

## 6. Findings

| # | Severity | Finding | Recommendation |
|---|---|---|---|
| F-1 | LOW | Coming-soon text: `optionComingSoon` emits `This option <name>is coming soon ...` — no space before "is" (`CobolMessages.java:115-117`). This is faithful to the COBOL `STRING ... DELIMITED BY SPACE` output *shape* (first word + no space), but applied to the full name it matches neither the literal source (`This option Accountis coming soon ...`) nor the FR doc §9 documented generalization (`This option Account View is coming soon ...`). Unreachable in shipped catalogues; pinned only by fixture tests (`This option Future Featureis coming soon ...`). | Either add the space to match the documented generalization, or correct the §9 example to the quirk-faithful form. No runtime impact today. |
| F-2 | LOW | FR-S01-07 verbatim message is produced only on the UI surface. On the REST surface a repository failure in `signon` propagates uncaught (`AuthService.java:48-49`) to `GlobalExceptionHandler.handleUnexpected` → generic 500 `Unable to process request`. The UI path is proven by `SignOnUiStoreErrorIntegrationTest`; the REST path is untested for this case. | Map datastore exceptions during sign-on to `USER_VERIFY_FAILED` on the REST surface too, or record the divergence. |
| F-3 | LOW | Error cursor placement not reproduced: `autofocus` is hardcoded on the USERID input (`signon.html:24`) for every redisplay. Source puts the cursor on PASSWD for password-side errors (FR-S01-02, FR-S01-04; COSGN00C.cbl:123-127, :241-246). Message text and blocking semantics are correct; this is a focus-fidelity gap only. | Optional: drive `autofocus` from a `focusField` model attribute per error class. |
| F-4 | LOW | Route-registry flag semantics diverge from the recorded S01-B1 contract. The register says flags flip "at that stream's merge"; the implementation flips them where a baseline REST endpoint already exists (10/11 main, 4/6 admin implemented), so menu dispatch lands the browser on raw JSON API responses rather than "not installed until migrated". Deliberate interim seam — documented in `evidence/s01/ui_test_results.md` — but the boundary register/plan text understates it. | Amend the B1 decision note (or the plan) to record that flags track endpoint existence on the adopted baseline. |
| F-5 | INFO | FR-S01-12 and FR-S01-15 are unreachable with shipped catalogue data (all 11 main options 'U'-flagged; no `available=false` rows). Both are proven only via injected fixture catalogues (`MenuUiCatalogueIntegrationTest`). Flagged per playbook: requirements verified against synthesized data, not shipped data — same posture as the .NET audit. | None — becomes reachable only if the catalogue gains 'A'-flagged or placeholder rows. |
| F-6 | INFO | PF3 on a menu invalidates the server session before redirecting to `/signon`; the COBOL program had no session to invalidate. Strictly a security improvement (same as .NET audit F-4). | None. |
| F-7 | INFO | REST `signon` with a *missing* JSON field (`null`, not blank) hits `@NotNull` bean validation → `MethodArgumentNotValidException` → default field message, not the verbatim mandatory message. Blank strings produce the verbatim text. Null is unrepresentable on a 3270 screen, so this is an API-only cosmetic gap. | Optional: custom message on `@NotNull`. |
| F-8 | INFO | Admin-menu disabled options lose COADM01C's name-suppressed green `This option is not installed ...` form — unified with the main-menu registry semantics. Now explicitly documented in FR doc §9 (remediates .NET audit F-3). | None — documented disposition. |

## 7. Audit method

Read the stream FR, per-program FRs, analysis, migration plan, `.migration/`
ledger/boundary-register/decisions, and the prior .NET audit; line-level comparison
against `app/cbl/{COSGN00C,COMEN01C,COADM01C}.cbl` and copybooks `COMEN02Y`, `COADM02Y`,
`CSUSR01Y`, `COCOM01Y`, `CSMSG01Y`; full read of `spring-boot` UI/service/security/API
sources, templates, Flyway migrations, seeder, and every test class; independent
`mvn clean verify` plus a hands-on Postgres smoke (fresh `postgres:16` container)
exercising sign-on, role landing, menu render, dispatch, not-installed, invalid AID,
and unsigned bounce; `git diff`/`git log` scope checks on `app/` and the wave commits;
`git_pr_checks` on the last merged wave PR. No application code or `app/` source was
modified by this audit — the only additions are this document and the sign-off.
