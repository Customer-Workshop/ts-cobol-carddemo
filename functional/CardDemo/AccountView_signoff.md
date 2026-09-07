# S-01 AccountView — STOP E sign-off package (independent audit)

| | |
|---|---|
| Stream | S-01 AccountView (CardDemo core), process type ONLINE. Entry `CAVW` -> `COACTVWC` (map `CACTVWA`/`COACTVW`). Hard stop: `EXEC CICS XCTL PROGRAM('COMEN01C')` (`app/cbl/COACTVWC.cbl:328-352`). Programs: `COSGN00C`, `COMEN01C`, `COACTVWC`, `CSUTLDTC` (D-0019, D-0020) |
| Audited revision | stream branch `devin/1788757216-cardemo-account-view-stream`, head `5624ddeec6f9f6bb431f2f6b168fabf6e316a993` (waves 1-4 = PRs #95, #96, #97, #98 merged into it) |
| Auditor | independent session (did not build any wave, did not edit `app/`, `backend/`, `frontend/`, `parity/`, `.migration/`); this document is the only file added |
| Audit date | 2026-09-07 |
| Inputs | `AccountView_functional_requirement.md` (FR-01..FR-25), `AccountView_migration_plan.md`, `programs/*_functional_requirement.md`, `.migration/04_boundary_register.md`, `06_decisions.md`, `05_progress.md`, `parity/wave1..4/`, `parity/ui/`, raw `app/data/ASCII/*.txt`, COBOL sources |

## 1. Verdict

**READY FOR SIGN-OFF**, with one register correction to be appended before the merge (finding M-1, documentation only, no code change).

Basis: all 184 backend tests and 83 Karma specs re-executed green on this machine against real PostgreSQL 16 (Testcontainers) and ChromeHeadless; a fresh import into a throwaway PostgreSQL 16 loaded 50/50/50/10 rows; a live end-to-end run over the whole state machine (sign-on -> menu -> option 1 -> account view -> return -> sign-off) behaved as the FR specifies at the API boundary and in the persisted rows; every FR-01..FR-25 traces to at least one passing test that cites COBOL lines, to a parity case and (where UI-bearing) to a recorded UI scenario; all 21 target-state mechanical checks pass; the legacy `app/` tree is untouched; no in-scope program is missing, stubbed, mock-only or empty; the only deferred boundary (B-0015 CEEDAYS) carries dependency, impact and re-entry condition (D-0028).

Findings by severity: **0 high, 1 medium (M-1 register hygiene), 4 low, 3 informational.** None changes runtime behaviour. Details in §7.

## 2. Independent rebuild of the in-scope program list vs what is implemented

Rebuilt from the plan §1, D-0019/D-0020, and `inventory/call_graph.md` (CAVW -> COACTVWC; COSGN00C -> COMEN01C via XCTL; COMEN01C option 1 -> COACTVWC; CSUTLDTC ported once as shared utility):

| COBOL program | Target implementation | Behaviour present? | Test classes (re-run, all green) | Audit note |
|---|---|---|---|---|
| `COSGN00C` | `service/AuthService`, `api/AuthController`, `security/*` (BCrypt upgrade-on-login), Angular `features/signon` | yes — live run: E-06/E-07 verbatim 401s, lower-case id accepted (FR-04), session cookie issued, `landingTarget=/menu`, sign-off text verbatim, hash written and legacy column cleared on first sign-on (verified in PG) | `AuthServiceTest` 20, `AuthControllerWebMvcTest` 11, `AuthControllerH2Test` 6, `AuthUpgradeOnLoginIntegrationTest` 5, `signon.component.spec.ts` | not a stub |
| `COMEN01C` | `service/MenuService` (static route table = `COMEN02Y`), `api/MenuController`, Angular `features/menu` | yes — live run: `GET /api/menu` 11 options verbatim, option 1 -> `COACTVWC`/`/accounts/view`, option 5 -> facade `Option not available in this release`, option 12 -> E-08 with 2-digit echo | `MenuServiceTest` 44, `MenuControllerWebMvcTest` 8, `MenuFlowIntegrationTest` 4, `menu.component.spec.ts` | not a stub; options 2..11 are the *decided* strangler facade (B-0010, Q-12/D-0025), not an omission |
| `COACTVWC` | `service/AccountViewService` (CXACAIX -> ACCTDAT -> CUSTDAT chain), `api/AccountController`, `api/CobolPicture` (money/SSN edits), Angular `features/account-view` | yes — live run: 401 without cookie, E-04/E-05/E-09 verbatim (75-byte truth), account 1 and 27 full payloads equal to raw-byte derivation (§4.5) | `AccountViewServiceTest` 23, `AccountControllerWebMvcTest` 12, `AccountViewFlowIntegrationTest` 6, `AccountViewRepositoriesIntegrationTest` 6, `CobolPictureTest` 7, `account-view.component.spec.ts` | not a stub |
| `CSUTLDTC` | `service/DateValidationService` (+ `DateValidationRequest/Result`) | yes — pure function, 80-byte `CSUTLDWY` layout reproduced, `0000`/`2513` contractual, 7 codes INFERRED (Q-08) | `DateValidationServiceTest` 19 | no runtime caller in S-01 (plan R-10) — evidence is unit-level only, by design |
| Data seams | `data/ImportRunner`, `CobolFieldReader`, Flyway `V1__account_view_schema.sql`, entities `Account/Customer/CardXref/SecurityUser` | yes — import 50/50/50/10, Q-10 date gate 200/0 | `CobolFieldReaderTest` 5, `LegacyExtractQ10GateTest` 4, `H2UnitProfileContextTest` 3, `ScreenHeaderServiceTest` 1 | — |

`grep -rniE "TODO|FIXME|not implemented|UnsupportedOperation|stub" backend/src/main frontend/src/app` -> no hits. Smallest main classes are records/repository interfaces (5-12 lines), as expected. Diff vs implementation: **none missing, none stubbed, none mock-only.**

Test-quality review: the 14 legacy error literals are asserted as string literals (not via `CobolMessages` constants) in 9 test classes (`AccountViewFlowIntegrationTest` ×7, `AccountControllerWebMvcTest` ×8, `AccountViewServiceTest` ×8, `AuthControllerWebMvcTest` ×4 …). `AccountViewServiceTest` also asserts 40 `CobolMessages.*` references — acceptable because the same texts are pinned literally elsewhere (L-3 notes the residual risk). Fixture expectations for account/customer 27 are hard-coded from `acctdata.txt:27` / `custdata.txt:27` in the test source, not computed by the code under test. No tautological assertion found.

## 3. Traceability matrix summary (FR-01..FR-25)

Method: every stream FR id was grepped in `backend/src/test`, `frontend/src/**/*.spec.ts`, `parity/**`, and `parity/ui/AccountView_ui_test_report.md`; the covering test names were then read to confirm they assert the FR's observable (not merely mention it).

| FR | Program | Covering test(s) (all passed in this re-run) | COBOL cite in test | Parity case | UI scenario |
|---|---|---|---|---|---|
| FR-01 initial sign-on screen | COSGN00C | `signon.component.spec.ts` header/no-message; `AuthControllerWebMvcTest#b0027SessionMissing` | `COSGN00C.cbl:110-114`, `:178-195` | wave2 | FR-01 (PASS) |
| FR-02 blank user id -> E-01 | COSGN00C | `AuthServiceTest#fr02BlankUserId`, `#fr02NullUserId`, `#fr02WinsOverFr03WhenBothBlank`; spec `FR-02 — blank user id` | `:117-120` | wave2 | FR-02 |
| FR-03 blank password -> E-02 | COSGN00C | `AuthServiceTest#fr03BlankPassword`; `AuthControllerWebMvcTest#fr03BlankPasswordPayload`; spec | `:123-127` | wave2 | FR-03 |
| FR-04 upper-casing | COSGN00C | `AuthServiceTest#fr04UpperCasesUserIdAndPassword`; spec `upper-cases input`; live run (lower-case id accepted) | `:132-136` | wave2 | FR-04 |
| FR-05 success routing U/A | COSGN00C | `AuthServiceTest#fr05RegularUser`, `#fr05AdminUserLandsOnMenu`; `AuthControllerH2Test#fr05SessionWritten`; `MenuFlowIntegrationTest#q01AdminUsesTheSameMenu` | `:221-239` | wave2 | FR-05, FR-05b |
| FR-06 wrong password / not found | COSGN00C | `AuthServiceTest#fr06WrongPassword`, `#fr06UserNotFound`; `AuthUpgradeOnLoginIntegrationTest#fr06WrongPasswordDoesNotUpgrade`; specs | `:241-257` | wave2 | FR-06, FR-06b |
| FR-07 datastore error -> E-13 | COSGN00C | `AuthServiceTest#fr07RepositoryFailure`; spec `FR-07 — technical error` | `:207-219` | wave2 | FR-07 |
| FR-08 exit text (SEND TEXT) | COSGN00C | `AuthServiceTest#fr08SignOffMessage`; live run `Thank you for using CardDemo application...` | `:164-169` | wave2 | FR-08 |
| FR-09 menu render, 11 labels | COMEN01C | `MenuServiceTest#b0033RouteTableEqualsCopybook`; `MenuControllerWebMvcTest#fr09Menu`; spec `renders the static BMS text verbatim` | `COMEN01C.cbl:215-256`, `COMEN02Y.cpy:19-98` | wave3 | FR-09 |
| FR-10 invalid option -> E-08 | COMEN01C | `MenuServiceTest#fr10BlankOption/#fr10ZeroOption/#fr10NonNumericOption/#fr10OutOfRangeOption/#fr10Normalisation`; `MenuFlowIntegrationTest#fr10AndQ12OverHttp` | `:127-143` | wave3 (incl. T-3/DV-07) | FR-10 |
| FR-11 dispatch option 1 | COMEN01C | `MenuServiceTest#fr11DispatchOptionOne`, `#b0010OnlyOptionOneIsImplemented`; `MenuControllerWebMvcTest#q12SelectUnavailableOption` | `:177-188` | wave3 | FR-11 |
| FR-12 menu exit -> sign-on | COMEN01C | `MenuFlowIntegrationTest` exit leg; spec `a disabled row cannot be clicked`; UI | `:196-203` | wave3 | FR-12, FR-12b |
| FR-13 entry state | COACTVWC | `account-view.component.spec.ts` entry; `AccountControllerWebMvcTest#b0027HeaderUnauthenticatedIs401` (header endpoint) | `COACTVWC.cbl:436-458` | wave4 A-01 (re-run PASS) | FR-13 |
| FR-14 blank -> E-04 | COACTVWC | `AccountViewServiceTest#fr14BlankInput/#fr14NullInput/#fr14AsteriskIsNoInput`; `AccountControllerWebMvcTest#fr14BlankPathSegment`; `AccountViewFlowIntegrationTest#fr14AndFr15InputEditsOverHttp` | `:628-645` | A-02 | FR-14 |
| FR-15 invalid filter -> E-05 | COACTVWC | `AccountViewServiceTest#fr15AllZeroes`, `#q02WrongLength`; `AccountControllerWebMvcTest#fr15InvalidFilterPayload` | `:666-676` | A-03 | FR-15 |
| FR-16 found -> full display | COACTVWC | `AccountViewFlowIntegrationTest#fr16FixtureAccountEndToEnd`; `AccountControllerWebMvcTest#fr16Success`; `CobolPictureTest#moneyPicture*` | `:471-523` | A-07, A-08 (145/145 fields) | FR-16 |
| FR-17 xref NOTFND -> E-09 | COACTVWC | `AccountViewFlowIntegrationTest` (literal `…Cross ref file.  Resp:0000000013 Reas:0000`) | `:741-758` | A-04 | FR-17 |
| FR-18 acct NOTFND -> E-10 (DV-01) | COACTVWC | `AccountViewServiceTest#dv01AccountNotFound`; `AccountControllerWebMvcTest#dv01AccountNotFoundPayload`; `AccountViewFlowIntegrationTest#dv01AccountNotFoundOverHttp` | `:792-806`, `:704-715` | A-05 | FR-18 |
| FR-19 cust NOTFND -> E-11, account kept | COACTVWC | `AccountViewFlowIntegrationTest#fr20CustomerNotFoundOverHttp`; `AccountViewServiceTest` E-11 branch | `:843-856`, `:471-491` | A-06, A-23 | FR-19 (E-11) |
| FR-20 read error -> E-12 | COACTVWC | `AccountViewServiceTest#e12CustomerReadError` (+ xref/acct arms); spec `E-12 — a read failure` | `:86-105`, `:760-766`, `:809-816`, `:858-865` | A-12..A-14 | FR-20 |
| FR-21 PF3 -> menu, identity intact | COACTVWC | `AccountViewFlowIntegrationTest` DV-02 assertion (session unchanged); spec exit; live run (session identical before/after) | `:324-352` | A-15, A-19 | FR-21 |
| FR-22 USRSEC failure -> E-13 | COSGN00C | `AuthServiceTest#fr22DatastoreUnreachable` | `:207-219` | wave2 | FR-22 (UI via forced failure) |
| FR-23 valid date -> severity 0 | CSUTLDTC | `DateValidationServiceTest#fr23_*` (3) | `CSUTLDTC.cbl:83-149` | wave1 23/23 | n/a (utility) |
| FR-24 invalid date -> severity/code | CSUTLDTC | `DateValidationServiceTest#fr24_*` (12; `2513` contractual, others INFERRED) | `:122-149`, `CSUTLDWY.cpy:60-85` | wave1 | n/a (utility) |
| FR-25 invalid key -> E-03 | all online | dropped by DV-03 (Q-06): `signon`/`menu`/`account-view` specs assert unbound keys issue no request and no E-03 text | `CSMSG01Y.cpy` | A-16 (PASS-WITH-RISK) | X-03/FR-25 |

**Result: 25/25 FRs traced to at least one passing test with COBOL cites, 25/25 to a parity case, 23/23 UI-bearing FRs to a UI scenario (FR-23/24 are non-UI).**

Deviations and open-question resolutions:

| Id | Asserted by | Status |
|---|---|---|
| DV-01 (no customer block on ACCTDAT NOTFND) | `AccountViewServiceTest#dv01AccountNotFound`, Flow/WebMvc dv01 tests; A-05 | asserted |
| DV-02 (identity survives navigation) | `AccountViewFlowIntegrationTest` (`// DV-02` assertion), `AuthControllerH2Test#b0027SessionAuthenticatesProtectedRequests`; A-19; live run | asserted |
| DV-03 (no invalid-key message; F3/Esc = Exit) | 3 component specs; A-15/A-16 | asserted; browser-native F5 caveat (L-2) |
| DV-04 (`TstDate:` holds the date as passed) | `DateValidationServiceTest` (DV-04 tagged) | asserted |
| DV-05 (full ZIP/phone) | `AccountViewServiceTest`, spec; A-09 | asserted |
| DV-06 (GROUP-ID from ZIP slot, D-0038) | `AccountViewServiceTest`, `AccountViewFlowIntegrationTest`, `LegacyExtractQ10GateTest`; A-10 | asserted; PASS-WITH-RISK pending STOP D default (L-4) |
| DV-07 (options 12..99 -> E-08 only) | `MenuServiceTest#fr10OutOfRangeOption` (asserts E-08 and nothing else); wave3 T-3 | behaviour asserted, but no test or parity row carries the label `DV-07` (L-1) |
| Q-01 admin lands on `/menu` | `MenuFlowIntegrationTest#q01AdminUsesTheSameMenu`, `AuthServiceTest#fr05AdminUserLandsOnMenu`, spec | asserted |
| Q-02 short account -> E-05 | `AccountViewServiceTest#q02WrongLength`; A-03 | asserted |
| Q-03 = DV-01; Q-05 = DV-02; Q-11 = DV-05; Q-06 = DV-03; Q-07 = DV-04 | see above | asserted |
| Q-04 lowest card wins | `AccountViewServiceTest#q04LowestCardNumberWins`, `AccountViewRepositoriesIntegrationTest`; A-11 (synthetic 2-card account) | asserted on synthetic data; unobservable on the 50-row extract (R-0001, see §6) |
| Q-08 CEEDAYS mapping | `DateValidationServiceTest` `2513_contractual` + 7 `_INFERRED` | asserted as decided |
| Q-09 RESP/RESP2 tail | literal `Resp:0000000013 Reas:0000` in Flow/WebMvc/Service tests; A-24 (75-byte ruling) | asserted |
| Q-10 date columns | `LegacyExtractQ10GateTest` (4), import log `200 values checked, 0 offending` | asserted |
| Q-12 options 2..11 facade | `MenuServiceTest#b0010OnlyOptionOneIsImplemented`, `MenuControllerWebMvcTest#q12SelectUnavailableOption`, `MenuFlowIntegrationTest#fr10AndQ12OverHttp` | asserted |
| Q-13 BCrypt upgrade-on-login | `AuthUpgradeOnLoginIntegrationTest` (5); live run: hash written, legacy column cleared | asserted |
| Q-14 explicit payload validation | `AccountControllerWebMvcTest`, `AuthControllerWebMvcTest#aSgn1OverLengthUserId`; A-18 | asserted; container-level 400 for `%`/`;` (informational) |

Gap list from traceability: L-1 only (DV-07 unlabeled). No FR without a test; no test-less deviation.

## 4. Re-executed evidence (this session, head `5624dde`)

Environment: Linux, Java 21 (`/usr/lib/jvm/java-21-openjdk-amd64`), Maven, Docker (Testcontainers `postgres:16`), Node **v20.18.1** (the requested Node 22 under `~/tools` was not present on this machine; CI runs Node 22 — see I-1), Chrome 133 at `~/.local/bin/google-chrome`.

### 4.1 Backend
```
cd backend && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B clean verify
-> Tests run: 184, Failures: 0, Errors: 0, Skipped: 0 ; BUILD SUCCESS ; exit 0
```
17 test classes, one JVM, Testcontainers PostgreSQL 16 for the integration classes (`AccountViewFlowIntegrationTest`, `AccountViewRepositoriesIntegrationTest`, `AuthUpgradeOnLoginIntegrationTest`, `MenuFlowIntegrationTest`, `AuthControllerH2Test` is the H2 unit-profile check only). No test or source file modified.

### 4.2 Frontend
```
cd frontend && npm ci && npm run build            -> exit 0 (dist/frontend)
CHROME_BIN=~/.local/bin/google-chrome npx ng test --watch=false --browsers=ChromeHeadless
-> Chrome Headless 133: Executed 83 of 83 SUCCESS ; exit 0
```

### 4.3 Fresh import into a throwaway PostgreSQL 16 (per `backend/README.md`)
```
docker run -d --name audit-pg -e POSTGRES_USER=carddemo -e POSTGRES_PASSWORD=carddemo -e POSTGRES_DB=carddemo -p 5599:5432 postgres:16
cd backend && JAVA_HOME=… CARDDEMO_DB_USER=carddemo CARDDEMO_DB_PASSWORD=carddemo mvn -q spring-boot:run \
  -Dspring-boot.run.profiles=import -Dspring-boot.run.arguments="--spring.main.web-application-type=none --spring.datasource.url=jdbc:postgresql://localhost:5599/carddemo"
-> ImportRunner: Import complete: accounts=50 customers=50 card_xrefs=50 users=10
-> Q-10 date gate: 200 values checked, 0 offending ; exit 0
psql: accounts|50  customers|50  card_xrefs|50  users|10 ; distinct xref_acct_id = 50 ; users with legacy plaintext = 10, hashed = 0 (pre-first-login)
PostgreSQL 16.15 (Debian 16.15-1.pgdg13+2)
```
**Row counts 50/50/50/10 confirmed.** Container removed after the run (one-way, throwaway).

### 4.4 Live end-to-end run (playbook step 1, API boundary + persisted row)
Backend started against the throwaway database (`--server.port=8099`), exercised with `curl`:

| Step | Request | Observed | FR |
|---|---|---|---|
| 1 | `GET /api/accounts/00000000027` no cookie | 401 | FR-21/B-0027 |
| 2 | signon `user0001`/`wrong` | 401 `Wrong Password. Try again ...` | FR-06 |
| 3 | signon `nobody` | 401 `User not found. Try again ...` | FR-06 |
| 4 | signon `user0001`/`password` (lower case) | 200 `{"userId":"USER0001","userType":"U","landingTarget":"/menu"}` + session cookie | FR-04, FR-05 |
| 5 | `GET /api/menu` | header `CM00`/`COMEN01C`, 11 options verbatim, only option 1 `implemented:true` | FR-09 |
| 6 | select `1` / `5` / `12` | `01`->`COACTVWC`,`/accounts/view`; `05`->`implemented:false`,`Option not available in this release`; `12`-> 400 `Please enter a valid option number...` echo `12` | FR-11, Q-12, FR-10/DV-07 |
| 7 | `GET /api/accounts/00000000001` | 200; `currentBalance "+        194.00"`, `creditLimit "+      2,020.00"`, `groupId A000000000`, `ssn 020-97-3888`, `zipCode 12546`, `ficoScore 274` | FR-16 |
| 8 | `GET /api/accounts/` (blank) / `1234567890A` / `00000000099` | 400 `No input received` / 400 `Account Filter must  be a non-zero 11 digit number` / 404 `Account:00000000099 not found in Cross ref file.  Resp:0000000013 Reas:0000` | FR-14, FR-15, FR-17 |
| 9 | `GET /api/auth/session` after the view | `{"userId":"USER0001","userType":"U"}` — identical to step 4 | FR-21, DV-02 |
| 10 | `POST /api/auth/signoff`; then step 1 again | 200 `Thank you for using CardDemo application...      `; 401 | FR-08, FR-12 |
| DB | `users` row `USER0001` after step 4 | `hashed=t`, `legacy_cleared=t` | Q-13/B-0026 |

### 4.5 Parity spot re-derivation from raw bytes (own derivation, three fields + extras)
Decoded independently with a 12-line Python script from `app/data/ASCII/acctdata.txt` / `custdata.txt` using the `CVACT01Y`/`CVCUS01Y` copybook offsets (zoned-decimal overpunch `{`=+0):

| Record | Raw bytes -> derived | PostgreSQL after import | API (live run) / screen |
|---|---|---|---|
| acct 27 `ACCT-CURR-BAL` | `00000002840{` -> +284.00 | `284.00` | `+        284.00` (PIC `+ZZZ,ZZZ,ZZZ.99`) — match |
| acct 27 `ACCT-CREDIT-LIMIT` | `00000055720{` -> +5572.00 | `5572.00` | `+      5,572.00` — match |
| cust 27 `CUST-ADDR-ZIP` | `07923-8822` | `07923-8822` | `07923-8822` (DV-05 full value) — match |
| cust 27 `CUST-FICO-CREDIT-SCORE` | `078` | `78` (integer) | API `78`; screen `078` per wave-4 re-run `ui/rerun-03` (D-0042 display contract) — match |
| cust 1 `CUST-SSN` | `020973888` | `20973888` (bigint) | `020-97-3888` — leading zero restored by `CobolPicture#ssn` — match |
| acct 1/27/50 bytes 103-112 / 113-122 | `A000000000` / blanks | `acct_group_id=A000000000`, `acct_addr_zip=NULL` | `groupId A000000000` — DV-06/D-0038 as decided |
| acct 1, 50 balances / limits | 194.00, 2020.00, 1020.00 / 492.00, 6169.00, 4587.00 | identical | — |

All re-derived values agree with the wave-4 fixture and with the live API. The wave-4 claim of 145/145 field parity is therefore corroborated on an independent sample.

### 4.6 CI status
`backend-ci.yml` (Java 21, `postgres:16` service, `mvn -B clean verify`) and `frontend-ci.yml` (Node 22, `npm ci`, build, ChromeHeadless Karma) exist on the branch and run on push/PR when `backend/**` / `frontend/**` change. GitHub check-runs: PR #95 head `1fe792b`, #96 `60413a0`, #97 `7ca7906`, #98 `f16c141` — `verify` and `build-test` all `success`; stream-branch runs on `7177926` and `312f2f2` `success`. The stream head `5624dde` itself has no run (its last commits touched only `parity/`/`.migration/`, which are path-filtered out; the code is identical to the last green run). Required-to-merge checks: `backend-ci / verify`, `frontend-ci / build-test`.

## 5. Target-state conformance (`.agents/skills/carddemo-target-state-conformance/SKILL.md`)

| Profile | Check | Result |
|---|---|---|
| CORE | Java 21 (`<java.version>21`) | PASS |
| CORE | `spring-boot-starter-parent` 3.4.5 | PASS |
| CORE | no Lombok / MapStruct / ModelMapper | PASS |
| CORE | no `double`/`float` in `backend/src/main` (money is `BigDecimal`, `precision=19, scale=2`) | PASS |
| CORE | no `@Autowired` field injection | PASS |
| CORE | request/response DTOs are records | PASS |
| CORE | `git diff origin/main -- app/` empty | PASS |
| CORE | `spring-boot/` differs from `origin/main` only by the STOP A reference import (commit `38ee98d`, 108 files, additions only, no modifications afterwards) | PASS (as authorised) |
| ONLINE | routes are business names: `/api/auth/{signon,signoff,session}`, `/api/menu`, `/api/menu/select`, `/api/accounts/{acctId}`, `/api/accounts/view/header`; Angular `signon`, `menu`, `accounts/view`; trancodes `CC00`/`CM00`/`CAVW` appear only in header DTOs (traceability) | PASS |
| ONLINE | Angular standalone (no `standalone: false`), no NgRx, `@angular/material` present, `withCredentials: true` | PASS |
| DATA | `ddl-auto=validate`, no `h2` in `application.properties`, `jdbc:postgresql`, Flyway `V1__account_view_schema.sql` only | PASS |
| DATA | no `CommandLineRunner` outside `@Profile("import")`; no `@Transactional` in `api/` | PASS |

**21/21 mechanical checks PASS** (same count as the wave-4 run, re-executed here).

## 6. Boundary closure (B-0001..B-0033 touched by the plan)

Latest effective row per boundary in `.migration/04_boundary_register.md`, cross-checked against the code:

| Boundary | Register status (latest row) | Code evidence | Closure |
|---|---|---|---|
| B-0001 ACCTDAT | IMPLEMENTED (D-0021) | `AccountRepository`, Flyway V1 | closed |
| B-0002 CUSTDAT | IMPLEMENTED (D-0022) | `CustomerRepository` | closed (row wording says "customer block preserved on account NOTFND" — the opposite of DV-01; see M-1) |
| B-0006 USRSEC | IMPLEMENTED (D-0024/D-0029) | `SecurityUserRepository`, BCrypt column | closed |
| B-0007 CXACAIX | IMPLEMENTED | `findFirstByXrefAcctIdOrderByXrefCardNumberAsc` + index in V1 | closed; wave-4 row was appended under the wrong id **B-0003** (M-1); real-data uniqueness open (R-0001) |
| B-0009 sign-on XCTL | IMPLEMENTED (D-0025) | `landingTarget`, Angular route | closed |
| B-0010 menu dispatch + hard stop | IMPLEMENTED (D-0026) | `MenuService` facade, Exit -> `/menu` | closed; wave-4 PF3 row appended under the wrong id **B-0011** (M-1) |
| B-0014 CSUTLDTC | IMPLEMENTED (D-0027) | `DateValidationService` | closed |
| B-0015 CEEDAYS | **DEFERRED** (D-0028) | `java.time` substitute | explicitly deferred: dependency = LE run / IBM feedback catalogue; impact = S-08/S-09 reason texts unproven, S-01 unaffected; re-entry = when S-08 or S-09 is planned |
| B-0026 sign-on / RACF substitute | IMPLEMENTED (D-0029); **real SSO DEFERRED** | `security/*` | closed for S-01; SSO deferral has dependency (R-0002 IdP registration), impact (no MFA/lockout/audit), re-entry (R-0002 answered, S-11 planned, or real user data) |
| B-0027 COMMAREA | IMPLEMENTED (D-0030) | `HttpSession`, `SessionContext` | closed |
| B-0028 pseudo-conversation | IMPLEMENTED (D-0031) | stateless controllers | closed |
| B-0029 BMS -> Angular | **DECIDED** (D-0031) — never advanced to IMPLEMENTED | 3 Angular components, 83 specs, UI pass | implemented in fact; register stale (M-1) |
| B-0030 CICS system services | **DECIDED** (D-0031) — never advanced | `ScreenHeaderService`, `carddemo.applid/sysid`, `Clock` | implemented in fact; register stale (M-1) |
| B-0031 abend protocol | IMPLEMENTED (D-0032) | `GlobalExceptionHandler` | closed; generic-500 path A-20 UNTESTED (L-4) |
| B-0032 option 11 INQUIRE guard | **DECIDED** (D-0025) — never advanced | folded into B-0010 facade, `MenuServiceTest` | implemented in fact; register stale (M-1) |
| B-0033 menu option table | IMPLEMENTED | `MenuService` static table = `COMEN02Y` | closed |

Untouched boundaries (B-0003..05, 08, 11..13, 16..25) remain OPEN by design (plan §3 last paragraph). No touched boundary is undecided; no deferral is undocumented.

Lead-time track (outlives this stream; must land before the legacy path is decommissioned): R-0001 data extract + AIX `UNIQUEKEY` answer (Data Management, open), R-0002 OIDC client (Security/SSO, open, fires the B-0026 re-entry), R-0003 CI PostgreSQL 16 (Infrastructure — **effectively closed**: the `postgres:16` service container is in `backend-ci.yml` and green).

## 7. Findings and risk / follow-up register

| Id | Severity | Finding | Cite | Owner | Recommended action |
|---|---|---|---|---|---|
| M-1 | Medium (documentation) | Boundary register hygiene: (a) B-0029, B-0030, B-0032 latest status is DECIDED although implemented and tested — plan §8.5 requires every S-01 row IMPLEMENTED; (b) wave-4 rows for CXACAIX and PF3 were appended as **B-0003** and **B-0011** (those ids are CARDDAT and option-11/COPAUS0C in the inventory) instead of B-0007 / B-0010, and `05_progress.md` repeats "B-0003/11 IMPLEMENTED"; (c) decision-id column on several wave-4 rows does not match the decision that governs the boundary (e.g. B-0002 -> D-0022 which is the B-0007 decision; B-0033 -> D-0032 which is abend); (d) B-0002 wave-4 row says "DV-01 customer block preserved" — DV-01 is the opposite. No runtime impact; the register is append-only so the fix is one appended correction row per item | `.migration/04_boundary_register.md:79,84-85,99,102-104`; `05_progress.md` wave-4 row | orchestrator | append correction rows (B-0029/30/32 -> IMPLEMENTED with PR ids; B-0007/B-0010 restated; decision ids corrected) **before** the merge to `main`; no code change |
| L-1 | Low | DV-07 (options 12..99 -> E-08 only) is asserted by `MenuServiceTest#fr10OutOfRangeOption` and wave-3 T-3 but no test or parity row is labelled `DV-07`, so the traceability grep for the deviation id returns nothing | `MenuServiceTest.java:147-152`; `06_decisions.md` D-0041 | wave owner (next touch of `backend/`) | add `DV-07` to the test `@DisplayName`; no behaviour change |
| L-2 | Low | DV-03 wording: "unbound keys do nothing" is true at component level, but a physical F5 is the browser reload and clears the form (wave-4 A-16, R-1). D-0042 already reworded this as "browser-native keys are out of scope" — the FR/register text should carry that footnote | `parity/wave4/COACTVWC_parity_results.md` §5 R-1; D-0042 | orchestrator (FR footnote) | footnote only |
| L-3 | Low | FR wording items from D-0042 not yet folded into the FR document: 75-byte `WS-RETURN-MSG` truncation of E-09/E-10/E-11 (FR §5/§7 quote the untruncated literals), `ficoScore` typed `Integer` at the API while the screen renders `X(3)` `078` (display contract), DV-03 browser-native keys. Code is correct; FR text lags | `COACTVWC_parity_results.md` §3, §6; D-0042 | orchestrator | FR footnotes at next FR revision; no code change |
| L-4 | Low | Two evidence gaps flagged, not hidden: (a) A-20 generic 500 path of `GlobalExceptionHandler` (B-0031) is UNTESTED end-to-end because no failure can be injected without modifying code — unit coverage only; (b) DV-06 `groupId` from the ZIP slot is PASS-WITH-RISK pending the STOP D default reply (D-0038) | wave-4 A-10, A-20 | orchestrator / customer | (a) accept or add a test-profile fault injector in a later wave; (b) confirm D-0038 default at STOP E |
| R-1 | Risk (medium until R-0001 answered) | Q-04 AIX uniqueness untested on real data: the 50-row extract has one card per account, so "lowest card number wins" is proven only on synthetic rows (A-11). If the production AIX is non-unique and the customer expects a different record, the account -> customer resolution changes | plan §3.1 B-0007; `R-0001` | Data Management (R-0001) | answer `DEFINE AIX UNIQUEKEY` question before UAT on real data |
| R-2 | Risk (low for UAT, high before any non-demo environment) | Real SSO deferred (B-0026, D-0029): target holds BCrypt credentials with no MFA, lockout, rotation or audit — legacy exposure carried forward | plan §3.3; `R-0002` | Security/SSO (R-0002) | must be resolved before real user data; not a UAT blocker |
| R-3 | Risk (low, no S-01 impact) | B-0015 CEEDAYS feedback catalogue deferred; 7 of 9 reason codes INFERRED | D-0028 | S-08/S-09 planner | re-open at S-08/S-09 planning |
| D-UI-01 | Minor defect | Menu screen: `01. Account View` button receives keyboard focus before the option input; BMS has only the option field unprotected | `parity/ui/AccountView_ui_test_report.md` X-04 | frontend (next `frontend/` touch) | tabindex fix; not a sign-off blocker |
| I-1 | Informational | Node version: this audit's frontend build/Karma ran on Node 20.18.1 (Node 22 was not available on the audit machine); CI runs Node 22 and is green on every PR head. Both versions pass | `frontend-ci.yml:23` | — | none |
| I-2 | Informational | Q-14 hardening: URL-encoded `;` / `%` in the account path are rejected by Tomcat with a generic 400 before the controller (never 500) — status class right, no program text (wave-4 A-18) | wave-4 R-2 | — | none |
| I-3 | Informational | Wave-4 parity doc names a `GET /api/menu/header` endpoint; the actual menu endpoint is `GET /api/menu` (header is embedded). Doc-only | `COACTVWC_parity_results.md` §8 | — | none |

### 7.1 npm audit (frontend, `npm audit --json`)
57 advisories: 7 low, 17 moderate, 32 high, **1 critical**.

Critical: **`tar` <= 7.5.20** (GHSA — "Arbitrary File Creation/Overwrite via Hardlink Path Traversal" / "Arbitrary File Overwrite and Symlink Poisoning"). Installed `tar@6.2.1`. Dependency chain: `tar` <- `cacache` <- `make-fetch-happen` <- `node-gyp` <- `@npmcli/run-script` <- `pacote` <- **`@angular/cli`** (a `devDependency`). **Dev-only**: the package is used by the Angular CLI's package-manager tooling at build time; nothing from `tar` ships in `dist/frontend` (browser bundle). `npm audit`'s proposed fix downgrades `@angular/cli` to 11.x (semver-major), which is not acceptable. Recommended handling: track under the frontend owner, upgrade `@angular/cli` when 18.2.x/19 picks up a patched `pacote`; not a UAT blocker. The remaining high/moderate advisories are likewise in the CLI/Karma toolchain (`webpack@5.94.0` is the bundler, not a runtime dependency).

## 8. Privacy check

| Scope | Method | Result |
|---|---|---|
| `functional/`, `parity/`, `.migration/` | grep for e-mail patterns and known human names/handles | **no human name, e-mail or handle of any project participant**. Only hits: `i@izs.me` in `parity/wave2/conformance/frontend_npm_ci.log` (the public maintainer address embedded in the npm deprecation warning for `glob`/`tar`, third-party, not a participant) and `@mermaid-js/mermaid-cli` (package name) |
| PR bodies #95, #96, #97, #98 (`gh api .../pulls/N -q .body`) | grep names / e-mails / "Requested by" | clean |
| Commit messages on the stream branch (45 commits `origin/main..HEAD`) | grep subjects + bodies | clean |
| Commit authors | `git log --format='%an %ae'` | `Devin AI`, `Devin`, `devin-ai-integration[bot]` with `noreply`/`devin@cognition.ai` addresses only — no human identity |

Privacy result: **PASS** (one third-party maintainer e-mail in a captured npm log, informational, no action required).

## 9. Cutover posture and recommended STOP E wording

Cutover posture (unchanged by this sign-off):
- **VSAM remains the source of truth.** The PostgreSQL tables are populated by a **one-way import** (`import` profile) from the committed `app/data` extract and are refreshed by re-running it; there is no dual-write and no write path from the target to VSAM.
- **No production cutover is authorised** by STOP E. The legacy `CC00`/`CM00`/`CAVW` CICS transactions keep running unchanged; `app/` is untouched.
- STOP E decides exactly two things: (1) merge the stream branch `devin/1788757216-cardemo-account-view-stream` (head `5624dde`) into `main`, and (2) start UAT of the migrated Account View path (sign-on, menu shell with option 1 live, account view) on the target stack.
- Decommissioning of the legacy path for S-01 is a later decision gated on: UAT exit, R-0001 answered (AIX uniqueness + confirmed extract), and D-0038 default confirmed.

**Recommended STOP E wording (customer approves by replying "Approve STOP E"):**

> STOP E — S-01 AccountView. Approving authorises: (a) merging branch `devin/1788757216-cardemo-account-view-stream` at `5624dde` into `main` after the boundary-register correction rows (finding M-1) have been appended; (b) starting UAT of the migrated sign-on, main menu (option 1 live; options 2-11 show "Option not available in this release") and Account View screens against PostgreSQL 16 loaded by the one-way import. It confirms the approved deviations DV-01..DV-07 and the D-0038 default (Account Group read from the ZIP slot). It does **not** authorise production cutover, any write to VSAM, or decommissioning of the CICS transactions; VSAM stays the source of truth. Open tracks carried into UAT: R-0001 (AIX uniqueness on real data), R-0002 (SSO, before any non-demo environment), B-0015 (CEEDAYS, re-opened at S-08/S-09), D-UI-01 (menu Tab order), FR footnotes per D-0042.

## 10. For the business reader

The first slice of the CardDemo modernisation — signing on, the main menu, and viewing an account with its customer details — has been rebuilt on the new Java/PostgreSQL/Angular platform and independently checked by a reviewer who did not build it. Every one of the 25 documented business rules for this screen flow was traced to an automated test, to a comparison against the original COBOL logic and data files, and — for everything a user can see — to a recorded walk-through in the browser; all 267 automated tests passed again on a fresh machine today. The reviewer also reloaded the 50 accounts, 50 customers, 50 card links and 10 users from the original data files into an empty database and confirmed the totals and a sample of individual values byte for byte. Three known differences from the old screens are deliberate and approved (for example, full ZIP codes and phone numbers are shown instead of truncated ones), and no behaviour was changed to make the checks pass. The remaining items are housekeeping: a few bookkeeping rows in the migration register need correcting, one keyboard-order nit on the menu, and two questions for the data and security teams that only matter once real customer data or a live environment is involved. Nothing here touches the running mainframe: the original data remains the master copy, the new database is a one-way copy, and no production switch is being requested. Approving STOP E means merging this work into the main code line and starting user acceptance testing on the new screens; deciding when to retire the old screens comes later, after UAT.
