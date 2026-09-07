# S-01 AccountView — stream migration plan (`!mf_stream_migration_plan`)

| Item | Value |
|---|---|
| Module | CardDemo core (`functional/CardDemo/CardDemo_inventory.md`) |
| Stream | **S-01 AccountView** — confirmed at STOP B (`.migration/06_decisions.md` D-0019) |
| Process type | **ONLINE** (CICS pseudo-conversational, three screens) |
| Status | **DRAFT — pending STOP C.** Every decision below is recorded as `DECIDED` by this playbook and `pending (STOP C)` for customer approval (`.migration/06_decisions.md` D-0021..D-0032) |
| Inputs consumed (not re-derived) | `AccountView_analysis.md` (scope, DAG, field dictionary, boundary table, risks), `AccountView_functional_requirement.md` (25 FRs, E-01..E-14, acceptance criteria, Q-01..Q-14), `CardDemo_target_state.md` (CORE + ONLINE + DATA/BOUNDARY, CONFIRMED at STOP A), `CardDemo_inventory.md` §7 shared-program map, `.migration/04_boundary_register.md`, `.migration/06_decisions.md` D-0013..D-0020 |
| Deliverables of this playbook | this file; `.migration/04_boundary_register.md` rows appended with state `DECIDED`; `.migration/06_decisions.md` D-0021..D-0032; `.migration/requests/R-0001..R-0003`; `.migration/05_progress.md` row |
| Not produced here | code, scaffolding, per-program FR documents, child sessions, pull requests |
| Branch | `devin/1788757216-cardemo-account-view-stream` |
| Session | https://partner-workshops.devinenterprise.com/sessions/7425b46a9daf4302a8ba5556dede4295 |

---

## 1. Goal and scope

**Goal.** Deliver the Account View business capability — a regular user signs on, reaches the main
menu, selects *Account View*, enters an 11-digit account number and sees the account and its
cardholder's details, then returns to the menu — on Java 21 / Spring Boot 3 / Angular 18 /
PostgreSQL 16, with behaviour derived from the COBOL and from nothing else.

| Scope item | Value | Cite |
|---|---|---|
| Entry | trancode `CAVW` -> `COACTVWC`, map `CACTVWA`, mapset `COACTVW` | `app/csd/CARDDEMO.CSD:181-185,317-318`; `app/bms/COACTVW.bms`; `app/cpy-bms/COACTVW.CPY` |
| Reached via | `CC00` -> `COSGN00C` -> `XCTL 'COMEN01C'` -> option `1` -> `XCTL 'COACTVWC'` | `COSGN00C.cbl:236-239`; `COMEN01C.cbl:184-187`; `app/cpy/COMEN02Y.cpy:25-29` |
| **Hard stop** | `EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM)` back to `COMEN01C` on PF3 | `app/cbl/COACTVWC.cbl:349-352` |
| Programs | `COSGN00C`, `COMEN01C` (shared, owned by S-01), `COACTVWC` (stream-private), `CSUTLDTC` (shared utility, owned by S-01, no in-stream caller) | inventory §7; D-0020 |
| Exclusions | `CAUP`/`COACTUPC`; menu options 2..11; the admin branch `COADM01C`. The menu shell renders all 11 options; only option 1 is live | `COSGN00C.cbl:230-234`; `COMEN01C.cbl:145-188`; `COMEN02Y.cpy:31-90` |
| Definition of done | the 25 FRs and the acceptance criteria of `AccountView_functional_requirement.md` §4, §8, verified per §11 of this plan | FR §8 |

**Shared programs this stream ports on behalf of the module** (port once, ownership recorded —
inventory §7): `COSGN00C` (entry of every online stream, S-02..S-14 inherit), `COMEN01C` + the
`COMEN02Y` option table (S-02..S-10, S-13 inherit), `CSUTLDTC` (S-08, S-09, and S-02 via
`CSUTLDPY` inherit), the COMMAREA/session model `COCOM01Y` (all online streams inherit), and the
four tables `accounts`, `customers`, `card_xrefs`, `users` (S-02..S-11 and the batch streams
inherit).

**FR/analysis reconciliation — blank account message.** The analysis and the FR both trace two
`SET`s on the blank path: `'Account number not provided'` (`COACTVWC.cbl:657-659`) followed by
`'No input received'` (`:640-642`). The second overwrites the first, so **only `No input received`
is ever displayed** (FR §6.3, FACT). This plan adopts that reading: the target emits E-04
`No input received` and never emits `Account number not provided`; the latter string is not added
to `CobolMessages`. Any parity test that expects it is wrong.

---

## 2. Target-state mapping

Profiles applied: **CORE + ONLINE + DATA/BOUNDARY** of `functional/CardDemo/CardDemo_target_state.md`
(CONFIRMED at STOP A, D-0013). **BATCH and SUBTRANSACTION are N/A** for this stream (target state
§6, §7) and no batch or called-contract convention is borrowed — `CSUTLDTC` is ported as an
in-process Spring `@Service`, which is the CORE shape, not a SUBTRANSACTION profile.

### 2.1 CORE -> this stream

| Convention | How it lands in S-01 | Cite |
|---|---|---|
| Java 21, Spring Boot 3 (pin `3.4.x`), Maven, JUnit 5, no Lombok | `backend/pom.xml`, single module, `spring-boot-maven-plugin` | target state §3; C1, C2 |
| Package layout `com.carddemo.{api,service,repository,model,security,data}` | `AccountController`, `AuthController`, `MenuController`; `AccountViewService`, `AuthService`, `MenuService`, `DateValidationService`; 4 repositories; 4 entities | target state §3 |
| One service per COBOL program | `AuthService` = `COSGN00C`, `MenuService` = `COMEN01C`, `AccountViewService` = `COACTVWC`, `DateValidationService` = `CSUTLDTC` | target state §3 naming |
| `record` DTOs, hand-written mapping, constructor injection | `AuthRequest/AuthResponse`, `MenuSelectRequest/MenuSelectionResponse`, `AccountViewResponse`, `DateValidationRequest/Result` | target state §3 |
| Error model | `CobolApiException(HttpStatus,message)` + `@RestControllerAdvice GlobalExceptionHandler` -> `ErrorResponse`; **all 14 legacy texts E-01..E-14 verbatim in `CobolMessages`**, including the two spaces in `Account Filter must  be a non-zero 11 digit number` | target state §3; FR §5 |
| Status mapping | edits -> 400; NOTFND (`RESP 13`) -> 404; bad credentials -> 401; admin-only -> 403; other -> 500 | target state §3 |
| COBOL type mapping | `9(11)`->`Long`/`BIGINT`; `S9(10)V99`->`BigDecimal(19,2)`/`NUMERIC(19,2)`; `X(10)` date->`LocalDate`/`DATE`; `X(n)`->`String`/`VARCHAR(n)`; `9(3)`->`Integer` | target state §3, §5; analysis §4 |
| CI gates | `.github/workflows/backend-ci.yml` (Temurin 21, `mvn -B clean verify`), `frontend-ci.yml` (Node 22, `npm ci`, build, headless `ng test`) | target state §3; F3 |
| Drift check | repo skill `.agents/skills/carddemo-target-state-conformance/SKILL.md` runs before every wave PR | `02_conventions.md` §7 |

### 2.2 ONLINE profile -> this stream (surface-specific decisions the profile mandates)

| Profile field | Decision for S-01 | Cite |
|---|---|---|
| API shape | JSON REST, one resource path per COBOL program: `POST /api/auth/signon`, `GET /api/auth/session`, `POST /api/auth/signoff` (`COSGN00C`); `GET /api/menu`, `POST /api/menu/select` (`COMEN01C`); `GET /api/accounts/{acctId}` (`COACTVWC`). Registered in the program->endpoint table in `backend/README.md` | target state §4; ONLINE drift rule 1 |
| Frontend routes | business names: `/signon`, `/menu`, `/accounts/view`. The trancodes `CC00`/`CM00`/`CAVW` appear only in FR traceability and in the on-screen header fields, never in a URL | D-0016 |
| Conversational state | CICS pseudo-conversation (`RETURN TRANSID` + COMMAREA, `COACTVWC.cbl:402-406`) becomes **stateless HTTP + server-side HTTP session**: the session cookie carries identity only; screen state (entered account id, last result) lives in the Angular component and in the request/response DTOs | target state §4; B-0027, B-0028 |
| Auth | Spring Security, session cookie, `SessionCreationPolicy.IF_REQUIRED`, CSRF disabled (JSON API), JSON 401/403 bodies; roles `USER`/`ADMIN` from `SEC-USR-TYPE` `U`/`A` | target state §4 |
| Password | BCrypt via `DelegatingPasswordEncoder` + upgrade-on-login from the legacy plaintext fixture; input upper-cased first (FR-04) | D-0014; Q-13 below |
| Validation | legacy edits in the service, in the COBOL paragraph order, with verbatim text; Jakarta annotations only for presence | target state §4; ONLINE drift rule 2 |
| PF keys | not represented in the API; navigation is frontend routing plus explicit buttons, with an F3 keyboard shortcut bound to *Exit* (Q-06) | target state §4 |
| Menu dispatch | `POST /api/menu/select` returns `{program, endpoint, implemented, message}`; the UI routes on the result | target state §4; B-0010, B-0033 |
| UI | Angular 18 standalone components + Angular Material, single prebuilt theme, English labels taken verbatim from the BMS maps, WCAG 2.1 AA baseline, `HttpClient` with `withCredentials: true`, `proxy.conf.json` -> `:8080`, Jasmine/Karma specs | target state §4 O3..O9 |
| Screens owing a UI slice | `COSGN00C` (`COSGN0A`), `COMEN01C` (`COMEN1A`), `COACTVWC` (`CACTVWA`). `CSUTLDTC` owes none (surface `none`) | analysis §3 |

### 2.3 DATA / BOUNDARY profile -> this stream

PostgreSQL 16 + Flyway (`V1__account_view_schema.sql`), `ddl-auto=validate` outside tests, Spring
Data JPA repositories, snake_case plural tables, KSDS key -> primary key, AIX -> B-tree index,
**no stored procedures or triggers** (D6 — and the question does not even arise: all four leaves
are VSAM, no DB2/IMS/`EXEC SQL` anywhere in the stream, analysis §4.1, §5.2), one-shot `import`
profile for the initial load (D5), H2 only in `application-test.properties`, real PostgreSQL 16 via
Testcontainers from wave 1 and a `postgres:16` service container in CI (D-0015).

---

## 3. Boundary resolution — `!mf_boundary_resolution`, decide mode

Every boundary the stream touches is decided here. The full resolution record for each boundary is
this section (the register rows point at it rather than at per-boundary files). Owner column: the
wave that builds the seam owns it; "module" means the port is inherited by later streams.

Legend for *Cutover / decommission*: the condition that flips this seam from legacy to target and
retires the legacy side. Coexistence policy for the whole engagement is per-stream cutover with
re-import and **no dual-write** (target state §5, D8): until STOP E the VSAM datasets remain the
source of truth and PostgreSQL is refreshed by re-running the `import` profile.

### 3.1 Data boundaries

| Id | Class | Contract (direction) | Decision | Seam | Error / retry / idempotency | Owner | Request | Strangler routing point | Cutover / decommission | Coexistence verification |
|---|---|---|---|---|---|---|---|---|---|---|
| **B-0001 ACCTDAT** | B4 data access, VSAM KSDS | `READ DATASET('ACCTDAT') RIDFLD(ACCT-ID 9(11))` -> 300-byte `ACCOUNT-RECORD`; RESP 0 / 13 / other (`COACTVWC.cbl:776-819`). Direction OUT (read-only in S-01) | Table `accounts` in PostgreSQL 16, Flyway `V1`; `AccountRepository extends JpaRepository<Account,Long>`; no stored procedure (D6) | `repository/AccountRepository#findById`, called by `AccountViewService` | RESP 13 -> `CobolApiException(404, E-10)`; any other RESP -> E-12 `File Error: READ on ACCTDAT returned RESP <r>,RESP2 <r2>` mapped to 500 with the verbatim literal and a synthesised reason code (Q-09). Read-only: idempotent, no retry (a JDBC failure is a 500, not a retry loop) | wave 1 (table + repository), wave 4 (use) | R-0001 | reads go to PostgreSQL from wave 4; the legacy CICS screen keeps reading VSAM until S-01 sign-off | retire the VSAM read for this stream at STOP E; the dataset itself stays until S-02/S-10 and the batch streams migrate (it has other owners: inventory §8 B-0001) | wave-4 parity tests compare target output against fixture-derived expectations for the same account ids on real PostgreSQL 16 (D-0015) |
| **B-0002 CUSTDAT** | B4 data access, VSAM KSDS | `READ DATASET('CUSTDAT') RIDFLD(CUST-ID 9(09))` -> 500-byte `CUSTOMER-RECORD`; RESP 0/13/other (`:826-868`). OUT, read-only | Table `customers`; `CustomerRepository` | `repository/CustomerRepository#findById` | RESP 13 -> 404 with E-11 `CustId:<id> not found in customer master.Resp: <r> REAS:<r2>`, account block still rendered (FR-18); other RESP -> E-12 for `CUSTDAT`. Idempotent read, no retry | wave 1 / wave 4 | R-0001 | as B-0001 | as B-0001 (writers S-02 and batch remain) | as B-0001, plus FR-18 case (customer missing, account shown) |
| **B-0007 CXACAIX** | B4 data access, VSAM **AIX path** | `READ DATASET('CXACAIX') RIDFLD(WS-CARD-RID-ACCT-ID X(11))` -> 50-byte `CARD-XREF-RECORD`; first record on the path wins; RESP 0/13/other (`:727-769`). IN/OUT: OUT read-only | Base cluster `CCXREF` -> table `card_xrefs` (PK `xref_card_number`); the AIX becomes a **B-tree index on `card_xrefs(xref_acct_id)`** created in Flyway `V1` (D-0013 D3). **First-row semantics are made deterministic: lowest card number wins** — `findFirstByXrefAcctIdOrderByXrefCardNumberAsc` (Q-04) | `repository/CardXrefRepository#findFirstByXrefAcctIdOrderByXrefCardNumberAsc` | RESP 13 -> 404 E-09 `Account:<id> not found in Cross ref file.  Resp:<r> Reas:<r2>`; other -> E-12 for `CXACAIX`. Idempotent | wave 1 / wave 4 | R-0001 (item 1 asks for the `DEFINE AIX` `UNIQUEKEY` answer) | as B-0001 | as B-0001 | fixture evidence: `app/data/ASCII/cardxref.txt` has 50 rows over 50 distinct account ids (measured), i.e. one card per account today, so ordering is unobservable on current data; the parity test asserts the ordering rule explicitly so a future multi-card row cannot change behaviour silently |
| **B-0006 USRSEC** | B4 data access, VSAM KSDS | `READ DATASET('USRSEC') RIDFLD(WS-USER-ID X(08))` -> 80-byte `SEC-USER-DATA`; RESP 0 (password match/mismatch decided in the program), 13, other (`COSGN00C.cbl:207-257`). OUT, read-only in S-01 | Table `users` (entity `SecurityUser`), PK `sec_usr_id VARCHAR(8)`; password column holds a **BCrypt hash** (see B-0026), plus the legacy plaintext column retained only for the upgrade-on-login path in dev/test profiles | `repository/SecurityUserRepository#findById` behind `SecurityUserDetailsService` | RESP 13 -> E-07 `User not found. Try again ...` (401); password mismatch -> E-06 (401); other RESP -> E-13 `Unable to verify the User ...` (500). No lockout, no retry — legacy has none (deliberate parity) | wave 1 (table), wave 2 (use) | R-0001, R-0002 | sign-on moves to the target at wave 2; the legacy `CC00` screen is untouched | retire when S-11 (UserAdmin, the CRUD owner) migrates or when R-0002 is answered, whichever first | wave-2 parity tests for FR-04..FR-07, FR-22 against the fixture user set loaded from `app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS` |
| **B-0033 menu option table** | B10 data contract | `COPY COMEN02Y`, 11 entries in an `OCCURS 12` table (`COMEN01C.cbl:51`; `app/cpy/COMEN02Y.cpy:19-98`). BOTH | A **static route table in code** owned by the menu shell (a Java `enum`/`List<MenuOption>` in `service/MenuService`), not a database table: it is program constant data, changes only when a stream is migrated, and belongs under version control with the code | `service/MenuService#options()` and `MenuOption` record `{number, name, program, endpoint, implemented, userType}` | n/a (no I/O) | wave 3, module-shared | — | later streams flip `implemented=true` for their option as they land — this is the single switch that opens each option | table retires when every option is migrated (end of module) | wave-3 test asserts the 11 labels verbatim (`01. Account View` .. `11. Pending Authorization View`, FR-09) |

### 3.2 Navigation / program boundaries

| Id | Class | Contract (direction) | Decision | Seam | Error behaviour | Owner | Strangler routing point | Cutover / decommission | Coexistence verification |
|---|---|---|---|---|---|---|---|---|---|
| **B-0009 sign-on XCTL** | B5 transaction switch | `XCTL PROGRAM('COMEN01C') COMMAREA(CARDDEMO-COMMAREA)` for `SEC-USR-TYPE='U'`; `XCTL 'COADM01C'` for `'A'` (excluded) (`COSGN00C.cbl:230-239`). OUT | Replaced by an HTTP response + Angular route: `POST /api/auth/signon` returns `{userId, userType, landingTarget}` and the SPA navigates to `/menu`. **Admin (`A`) authenticates and lands on the same `/menu`** with a banner `Administration is not available in this release` (Q-01); no admin route is created in S-01 | `api/AuthController` + Angular `AuthService`/router guard | 401 with E-06/E-07 text; no redirect on failure | wave 2, module-shared | `landingTarget` in the sign-on response is the routing point: it names `/menu` today and will name `/admin/menu` when S-11 lands | flips when S-11 migrates the admin menu | wave-2 UI pass: regular user reaches the menu; admin user reaches the menu with the banner |
| **B-0010 menu dispatch + hard stop** | B5 transaction switch | dispatch `XCTL PROGRAM(CDEMO-MENU-OPT-PGMNAME(WS-OPTION)) COMMAREA` (`COMEN01C.cbl:177-188`); hard stop `XCTL PROGRAM(CDEMO-TO-PROGRAM)` always `'COMEN01C'` (`COACTVWC.cbl:328-352`); menu PF3 `XCTL 'COSGN00C'` **without** COMMAREA (`COMEN01C.cbl:196-203`) | `POST /api/menu/select` returns `{program, endpoint, implemented, message}`. Option 1 -> `{program:'COACTVWC', endpoint:'/api/accounts/{acctId}', implemented:true}` and the SPA routes to `/accounts/view`. **Options 2..11 -> `{implemented:false, message:'Option not available in this release'}`**; the UI renders those rows greyed with the same verbatim label text and shows that message on selection (Q-12). No deep link to the legacy CICS screens: the two systems have no shared session, so a link would drop the user at an unauthenticated 3270 screen. Hard stop: F3/Exit on `/accounts/view` routes to `/menu`, discarding search state (FR-21). Menu Exit routes to `/signon` with the session invalidated (FR-12) | `api/MenuController`, `service/MenuService`, Angular router | invalid option (blank/`0`/non-numeric/`>11`) -> 400 with E-08 and the normalised 2-digit echo (FR-10) | wave 3, module-shared | **the strangler facade for the whole module**: `implemented` in the route table decides migrated vs not-available; each later stream flips its own row | option rows retire the facade one by one; the facade itself retires when the last option is migrated | wave-3 tests: option 1 routes; options 2..11 return `implemented:false` with the message; F3 paths both directions |
| **B-0028 pseudo-conversation** | B5 | `RETURN TRANSID(...) COMMAREA(...)`: `COSGN00C.cbl:98-102`, `COMEN01C.cbl:107-110`, `COACTVWC.cbl:402-406`; `CDEMO-PGM-CONTEXT` 0=enter/1=re-enter. BOTH | Dropped: each HTTP request is complete in itself. The enter/re-enter distinction becomes the difference between the initial `GET` render (FR-13 entry state) and a subsequent search request | Angular component state + stateless controllers | n/a | wave 1 (session model), waves 2-4 (use) | n/a — no legacy counterpart survives per screen | retires with each screen's wave | acceptance tests FR-13 (entry state) vs FR-14..FR-19 (submitted state) |
| **B-0032 option 11 INQUIRE guard** | B5 | `INQUIRE PROGRAM(...) NOHANDLE` + `'This option <name> is not installed...'` (`COMEN01C.cbl:147-167`) for `COPAUS0C`, which is outside the module | Excluded route, folded into the B-0010 facade: option 11 is `implemented:false` like 2..10. The legacy runtime probe has no target equivalent (nothing is dynamically installed) | `service/MenuService` route table | — | wave 3 | same as B-0010 | when S-13 (PendingAuthView) is unblocked and migrated | wave-3 test asserts option 11 behaves as the other excluded options |

### 3.3 Shared utility and runtime boundaries

| Id | Class | Contract (direction) | Decision | Seam | Error behaviour | Owner | Cutover / decommission | Coexistence verification |
|---|---|---|---|---|---|---|---|---|
| **B-0014 CSUTLDTC** | B2 shared utility | `PROCEDURE DIVISION USING LS-DATE X(10), LS-DATE-FORMAT X(10), LS-RESULT X(80)`; `RETURN-CODE` = severity (`CSUTLDTC.cbl:83-100`); result layout `app/cpy/CSUTLDWY.cpy:60-85`. IN (callers S-08 `COTRN02C.cbl:393,413`, S-09 `CORPT00C.cbl:392,412`, S-02 via `CSUTLDPY.cpy:293-296`) | **Ported once in wave 1 as an in-process Spring `@Service`** with a typed record contract (D-0020): `DateValidationService#validate(DateValidationRequest(String date, String mask)) -> DateValidationResult(int severity, String messageNumber, String reasonText, String formatted80)`. The 80-byte `formatted80` rendering is kept so a caller that reproduces the legacy string comparison can still do so; callers are expected to branch on `severity`/`messageNumber` (`'0000'`, `'2513'` — `COTRN02C.cbl:398-401`). Pure function, no transaction, no I/O | `service/DateValidationService` | never throws for an invalid date: an invalid date is a result with severity `0003` (FR-24). Only a null argument is a programming error (`IllegalArgumentException` -> 500) | wave 1, module-shared (S-08, S-09, S-02 inherit) | the legacy `CSUTLDTC` load module retires when its last caller (S-09) migrates — **not** at S-01 sign-off | wave-1 unit tests against the `CSUTLDWY` layout and the FR §5.4 catalogue; S-01 has no runtime caller (R-10), so this is the whole evidence base until S-08 |
| **B-0015 CEEDAYS** | B11 external runtime | `CALL "CEEDAYS" USING WS-DATE-TO-TEST, WS-DATE-FORMAT, OUTPUT-LILLIAN, FEEDBACK-CODE` (`CSUTLDTC.cbl:116-120`); 12-byte feedback token -> severity + message number (`:122-149`). OUT. **Contract UNRESOLVED**: which input yields which token is z/OS Language Environment behaviour, absent from the repo | **Decided as a deferral with a named re-entry condition.** Substituted by `java.time` (`DateTimeFormatter` built from the mask + `LocalDate.parse` with `ResolverStyle.STRICT`). The mapping is authoritative for the two tokens callers actually branch on — success -> `0000`/`Date is valid  `, and out-of-supported-range -> `2513`/`Unsupp. Range  ` — and **INFERRED** for `2507`, `2508`, `2509`, `2517`, `2518`, `2520`, `2521`, each mapped from a distinguishable `java.time` parse failure and labelled INFERRED in the wave-1 program FR. Anything else -> `Date is invalid` as the COBOL does. **Dependency**: a real LE run or IBM documentation of the CEEDAYS feedback catalogue. **Impact**: S-01 is unaffected (no caller); S-08/S-09 parity for the non-`0000`/`2513` reason texts is unproven. **Re-entry condition**: re-open B-0015 when S-08 TranAdd or S-09 TranReports is planned, or earlier if a CEEDAYS catalogue becomes available | `service/DateValidationService` (private feedback mapper) | as B-0014 | wave 1, module-shared | retires with `CSUTLDTC` | wave-1 tests pin `0000` and `2513`; the INFERRED codes are asserted against the chosen mapping and flagged in the program FR so S-08 re-verifies them |
| **B-0026 sign-on / RACF substitute** | B11 external identity | plaintext 8-char password byte-compare, no lockout, no audit, user type from the record; inputs upper-cased (`COSGN00C.cbl:132-136`, `:221-239`). IN | **Keep the USRSEC demo stub with BCrypt upgrade-on-login; real SSO deferred** (D-0014). Passwords stored as BCrypt hashes; a legacy plaintext row is verified once against the legacy value and rewritten as a hash on the next successful sign-on; input is upper-cased before hashing/compare so FR-04 case-insensitivity survives (Q-13). Deferral: **dependency** = an IdP client registration (R-0002) and a user-id mapping owner; **impact** = the target owns credentials, with no MFA, rotation, lockout or audit — the legacy exposure carried forward; **re-entry condition** = when R-0002 is answered, **or** when S-11 UserAdmin is planned, **or** when any non-demo environment must hold real user data, whichever comes first | `security/SecurityConfig`, `security/SecurityUserDetailsService`, `DelegatingPasswordEncoder` + upgrade-on-login `AuthenticationSuccessHandler` | 401 with the verbatim legacy text (E-06/E-07); E-13 for a store failure. No lockout (parity). Sign-on is not idempotent in the session sense: a second successful sign-on replaces the session | wave 2, module-shared | R-0002 | when the IdP lands, `security/` alone changes: no controller or service outside that package touches credentials | wave-2 tests: upgrade-on-login rewrites the hash exactly once; case-insensitive sign-on; wrong password / unknown user texts |
| **B-0027 COMMAREA** | B10 state contract | `COPY COCOM01Y` in all three online programs; writers `COSGN00C:222-229`, `COMEN01C:180-183`, `COACTVWC:283-288,340-347`; extended by `WS-THIS-PROGCOMMAREA` (`COACTVWC.cbl:213-216`). BOTH | **Replaced by server-side HTTP session** (target state §4): the session holds `userId` and `userType` only. Screen-to-screen context (`CDEMO-ACCT-ID`, `CDEMO-CARD-NUM`, `CDEMO-CUST-ID`, `CDEMO-LAST-MAP*`) travels in request/response DTOs and Angular component state. The legacy user-id wipe on entry to `COACTVWC` (`:283-286`) and the unconditional `CDEMO-USER-TYPE <- 'U'` on PF3 (`:344`) are **not reproduced** — identity comes from the session (Q-05, deviation DV-02) | `security` session + `SessionResponse`; a small `SessionContext` record read from `HttpSession` | session expiry -> 401 and the SPA routes to `/signon` | wave 1 (model), waves 2-4 (use) | — | n/a: no legacy COMMAREA survives in the target | wave-2/3/4 tests assert the user id is still present after navigating menu -> account view -> menu (the legacy quirk's opposite) |
| **B-0029 BMS screen I/O** | B11 presentation | `SEND MAP`/`RECEIVE MAP`/`SEND TEXT` in all three programs; `RESP` on `RECEIVE` never tested (analysis §5.2; R-09). BOTH | 3270 maps become Angular 18 + Material screens, one component per map, field-for-field with the BMS labels verbatim; display-only fields render as read-only text, `UNPROT` fields as form controls; `PICOUT` money formatting (`+ZZZ,ZZZ,ZZZ.99`) is reproduced in the DTO/pipe; `DRK` password field becomes `type=password`; `IC` becomes autofocus. Attribute constants from the absent `DFHBMSCA` (red error field, `DFHRED`) are interpreted from IBM documentation (R-12) and rendered as Material error styling. `SEND TEXT` (FR-08 exit text) becomes a plain confirmation page | Angular components `sign-on`, `menu`, `account-view` | request payloads are validated explicitly, which is what replaces the untested `RECEIVE MAP RESP` (Q-14) | waves 2-4 | — | retires per screen at each wave | Jasmine specs per component + the recorded `!mf_online_ui_testing` pass |
| **B-0030 CICS system services** | B11 | `ASSIGN APPLID/SYSID` (`COSGN00C.cbl:198-204`); `FUNCTION CURRENT-DATE` headers in all three programs. OUT | `APPLID`/`SYSID` become configuration properties (`carddemo.applid`, `carddemo.sysid`) rendered into the header; date/time come from the server clock formatted `mm/dd/yy` / `hh:mm:ss` as the COBOL does | `api/*Response` header fields + Angular header component; `Clock` bean so tests can pin time | n/a | wave 2 (first screen) | retires with the screens | header assertions in the component specs with a fixed `Clock` |
| **B-0031 abend protocol** | B11 | `HANDLE ABEND LABEL(ABEND-ROUTINE)`; `ABEND ABCODE('9999')` after sending `ABEND-DATA` (`COACTVWC.cbl:264-266`, `:916-937`; `app/cpy/CSMSG02Y.cpy:21-29`). OUT | Replaced by `GlobalExceptionHandler`: an unexpected exception becomes HTTP 500 with a generic `ErrorResponse` and no stack trace (CORE drift rule 7). The stream performs reads only and holds no unit of work, so there is nothing to back out | `api/GlobalExceptionHandler` | 500, logged at ERROR with the correlation of the request; no retry | wave 1 (handler), wave 4 (use) | retires with `COACTVWC` | a wave-4 test forces a repository failure and asserts 500 + generic body (and E-12 for the read-error path, FR-20) |

**Boundaries the stream does not touch** and which are therefore *not* decided here: B-0003..B-0005,
B-0008, B-0011..B-0013, B-0016..B-0025 (other streams' datasets, schedulers, batch hand-offs,
absent modules). They stay `OPEN` in the register.

---

## 4. Data and persistence

### 4.1 Tables (Flyway `V1__account_view_schema.sql`)

| Table | Source | Key | Notes |
|---|---|---|---|
| `accounts` | `ACCTDAT` / `app/cpy/CVACT01Y.cpy` (300 bytes) | `acct_id BIGINT PK` | money columns `NUMERIC(19,2)`; three `DATE` columns (see Q-10 gate); `FILLER X(178)` dropped |
| `customers` | `CUSTDAT` / `CVCUS01Y.cpy` (500 bytes) | `cust_id BIGINT PK` | `cust_ssn BIGINT` rendered `nnn-nn-nnnn` in the DTO; `cust_dob DATE`; `FILLER X(168)` dropped |
| `card_xrefs` | `CCXREF` / `CVACT03Y.cpy` (50 bytes) | `xref_card_number VARCHAR(16) PK` | **`CREATE INDEX idx_card_xrefs_acct_id ON card_xrefs(xref_acct_id)`** — the `CXACAIX` AIX substitute (D-0013 D3) |
| `users` | `USRSEC` / `CSUSR01Y.cpy` (80 bytes) | `sec_usr_id VARCHAR(8) PK` | `sec_usr_pwd_hash VARCHAR(60)` (BCrypt) + `sec_usr_pwd_legacy VARCHAR(8)` nullable, cleared on upgrade; `sec_usr_type CHAR(1)` `U`/`A` |

Entity/column naming keeps the COBOL field name in camelCase -> snake_case (`ACCT-CURR-BAL` ->
`acctCurrBal` -> `acct_curr_bal`); DTO field names are business English (`currentBalance`)
(target state §3, §5). The full COBOL-to-target field dictionary is `AccountView_analysis.md` §4.2
to §4.5 and is not restated here; it is the binding input for the `V1` DDL and the entities.

### 4.2 Type decisions carried from the analysis

- Money `S9(10)V99` -> `BigDecimal`/`NUMERIC(19,2)`, never `double` (CORE drift rule 3).
- `PIC X(10)` account/customer dates -> `LocalDate`/`DATE` — **INFERRED** (analysis R-02).
  **Wave-1 gate**: the importer parses every date column across the full extract; if any row is not
  `YYYY-MM-DD`, the column falls back to `VARCHAR(10)` and the DTO returns the raw text, matching
  the legacy screen which moves the text unvalidated (`COACTVWC.cbl:487-489`, `:507`). The decision
  is taken once, in wave 1, and recorded as a decision-log row (Q-10).
- Status/indicator domains `Y`/`N` are INFERRED from screen labels: stored as `VARCHAR(1)` with no
  check constraint, so the target cannot reject data the legacy screen would display.

### 4.3 Initial load and seeding

- **Production-shaped load**: one-shot `--spring.profiles.active=import` CLI run reusing the
  reference's `CobolFieldReader` semantics (zoned-decimal overpunch decode `{`,`}`,`A-I`,`J-R`;
  IBM037 for the EBCDIC USRSEC image) writing into PostgreSQL. **Not** a startup
  `CommandLineRunner` in the default profile (DATA drift rule 3).
- **Test data**: small Flyway `R__seed_test_data.sql` for the H2 unit profile; Testcontainers
  integration tests load the same fixture subset so parity evidence runs on real PostgreSQL 16.
- **Source of the data**: request **R-0001** (extract of ACCTDAT, CUSTDAT, CCXREF, USRSEC, plus the
  `DEFINE AIX` uniqueness answer and the date-format confirmation). Until answered, the committed
  fixtures under `app/data/` are the extract (assumption recorded in R-0001).
- **Coexistence**: VSAM stays the source of truth until STOP E; refresh by re-running `import`;
  no dual-write (target state §5 D8).

---

## 5. Phase 0 scaffolding (document only — nothing is built by this playbook)

Phase 0 **runs**: neither `backend/`, `frontend/` nor `.github/` exists on this branch (target state
§1 flags F1..F3; `07_runbook.md` §5-6). Nothing can be "reused" in the strict sense — the only
existing Java asset is the read-only reference `spring-boot/`, which is evidence, not a base.
"Adapted" below means the same shape and, where the file is pure legacy-derived data, the same
content, re-created under `backend/`; "re-implemented" means written for the target stack.

| Deliverable | Content | Reference relationship |
|---|---|---|
| `backend/pom.xml` | Spring Boot parent pinned `3.4.x`, `java.version=21`, starters: web, data-jpa, security, validation; `flyway-core` + `flyway-database-postgresql`; `postgresql` driver; test: `spring-boot-starter-test`, `spring-security-test`, `testcontainers` + `junit-jupiter` + `postgresql`; H2 test-scope. **No** `spring-boot-starter-batch` (BATCH is N/A), no Lombok, no mapper library | **adapted** from `spring-boot/pom.xml:1-70` minus batch, plus Flyway/PostgreSQL/Testcontainers |
| Package layout | `com.carddemo.{api,service,repository,model,security,data}` + `CardDemoApplication` | adapted (same layout, S-01 classes only) |
| `api/GlobalExceptionHandler`, `api/CobolApiException`, `api/ErrorResponse` | unchanged error model | **adapted** from `spring-boot/src/main/java/com/carddemo/api/{GlobalExceptionHandler,CobolApiException,ErrorResponse}.java` |
| `api/CobolMessages` | only the S-01 texts: E-01..E-14 verbatim (including `Account Filter must  be a non-zero 11 digit number` with two spaces, `Invalid key pressed. Please see below...`, `Thank you for using CardDemo application...`) plus the target-only `Option not available in this release` marked as NEW TEXT, not legacy | **adapted subset**; texts re-verified against `app/cbl/*` and `app/cpy/CSMSG01Y.cpy`, not copied on trust |
| `security/SecurityConfig`, `SecurityUserDetailsService` | session-cookie auth, `SessionCreationPolicy.IF_REQUIRED`, CSRF off, JSON 401/403, `/api/auth/**` permitted, everything else authenticated | **re-implemented**: the reference's `UsrsecPlaintextPasswordEncoder` is explicitly **not** carried over; the target uses `DelegatingPasswordEncoder` (BCrypt) + upgrade-on-login |
| `db/migration/V1__account_view_schema.sql` | `accounts`, `customers`, `card_xrefs` (+ `idx_card_xrefs_acct_id`), `users`; explicit PostgreSQL types | **new**: the reference has no Flyway at all (it uses Hibernate `ddl-auto=create-drop`) |
| `application.properties` | `jdbc:postgresql://localhost:5433/carddemo`, `ddl-auto=validate`, Flyway on, `open-in-view=false`, `carddemo.*` keys | **re-implemented** (reference points at H2) |
| `application-test.properties` | H2 `MODE=PostgreSQL`, Flyway on — unit profile only (D1) | new |
| Testcontainers profile | `@ServiceConnection` `PostgreSQLContainer("postgres:16")` base class for integration tests, active from wave 1 (D-0015) | new |
| `data/CobolFieldReader` + `data/ImportRunner` | fixed-width reader with overpunch decoding; `@Profile("import")` `CommandLineRunner` writing to PostgreSQL | reader **adapted** from `spring-boot/src/main/java/com/carddemo/data/CobolFieldReader.java` (the decoding is legacy semantics, not framework code); the runner is **re-implemented** — the reference's `DataSeeder` runs at startup, which the target forbids |
| `frontend/` | Angular 18 CLI project, standalone components, Angular Material + CDK, single prebuilt theme, `ApiService` with `withCredentials: true`, router with `/signon`, `/menu`, `/accounts/view`, `proxy.conf.json` -> `http://localhost:8080`, Jasmine/Karma | **new**: no Angular reference exists (the prior-run Angular app is on an ignored branch, F2/D-0017) |
| `.github/workflows/backend-ci.yml` | Temurin 21, `mvn -B clean verify` in `backend/`, **`services: postgres:16`** for the Testcontainers/integration stage, path filter `backend/**` | new (F3); depends on R-0003 |
| `.github/workflows/frontend-ci.yml` | Node 22, `npm ci`, `npm run build`, `npm test -- --watch=false --browsers=ChromeHeadless`, path filter `frontend/**` | new (F3) |
| `backend/README.md` | the program -> endpoint table required by ONLINE drift rule 1 | new |
| `.migration/07_runbook.md` §5 | the Phase 0 child replaces the "EXPECTED" marker with the execution date once the commands run | existing file, appended |

**Scaffolding deltas versus anything that exists today: everything above is a delta.** Nothing under
`spring-boot/` or `app/` is modified, and no reference file is copied wholesale — each adapted file
is re-created with only the S-01 surface and re-verified against the COBOL.

---

## 6. Waves

Four waves, taken unchanged from the analysis DAG (`AccountView_analysis.md` §6); the count is
derived from DAG depth, not a template, and is not changed by this plan. Waves run **sequentially**,
one child session each. **PR expectation (recommendation, orchestrator decides): one PR per wave
against the stream branch `devin/1788757216-cardemo-account-view-stream`**, not against `main` —
the stream is not independently releasable until STOP E, and per-wave PRs into the stream branch
keep the review unit small while letting the branch be merged once at sign-off. Backend and
frontend changes of a wave belong to the **same** PR (single repository, single CI run over both
path filters). Every PR runs the repo skill `carddemo-target-state-conformance` before it opens.

**Parity evidence rule for every wave**: integration and parity tests run against **real
PostgreSQL 16** via Testcontainers/Docker; H2 is only the no-Docker unit profile and is never
acceptable as parity evidence (D-0015).

### Wave 1 — data seams, session model, shared utility (depth 0, leaves)

| | |
|---|---|
| Programs / units | `CSUTLDTC` (full port); no screen program |
| Repos / dirs | `backend/` only (plus Phase 0 scaffolding if the orchestrator folds Phase 0 into this wave) |
| Program FRs consumed | `CSUTLDTC` FR (FR-23, FR-24, §5.4 result catalogue) |
| Contents | Flyway `V1` schema + index; entities `Account`, `Customer`, `CardXref`, `SecurityUser`; repositories for the four reads incl. `findFirstByXrefAcctIdOrderByXrefCardNumberAsc`; `import` profile loader; `R__seed_test_data.sql`; session-context model replacing COMMAREA; `DateValidationService` + CEEDAYS substitute; Testcontainers-PostgreSQL harness; `GlobalExceptionHandler`, `CobolApiException`, `CobolMessages` |
| Boundary seams implemented | B-0001, B-0002, B-0006, B-0007 (repositories + index), B-0014, B-0015, B-0027 (model), B-0031 (handler) |
| Strict edges | none inbound; **every** later wave depends on it |
| Ported for the module | `CSUTLDTC` (S-08, S-09, S-02 inherit), the four tables, the session model |
| UI slice | none |
| Parity evidence | `DateValidationService` against the FR §5.4 table; repository reads against the fixture load on Testcontainers PostgreSQL 16; the Q-10 date-format gate is decided and recorded here |

### Wave 2 — sign-on (depth 1)

| | |
|---|---|
| Programs | `COSGN00C` (shared, ported once) |
| Repos / dirs | `backend/`, `frontend/` |
| Program FRs consumed | `COSGN00C` FR (FR-01..FR-08, FR-22, FR-25) |
| Contents | `AuthService`, `AuthController` (`/api/auth/signon|session|signoff`), BCrypt + upgrade-on-login, session cookie, user-type routing, `sign-on` Angular component with the verbatim BMS labels, footer and header |
| Boundary seams | B-0006 (use), B-0026, B-0009, B-0027 (session), B-0029, B-0030 |
| Strict edges | needs wave 1 (`users` repository, session model, error model) |
| UI slice | **yes** — `COSGN0A` -> `/signon` |
| Parity evidence | FR-01..FR-08, FR-22 on Testcontainers PostgreSQL 16, incl. verbatim E-01, E-02, E-06, E-07, E-13, E-14 and the validation order of `COSGN00C.cbl:117-136` |

### Wave 3 — main menu shell (depth 2)

| | |
|---|---|
| Programs | `COMEN01C` (shared, ported once) |
| Repos / dirs | `backend/`, `frontend/` |
| Program FRs consumed | `COMEN01C` FR (FR-09..FR-12, FR-25) |
| Contents | `MenuService` + static route table (B-0033), `GET /api/menu`, `POST /api/menu/select`, option normalisation and validation 1..11 exactly as `COMEN01C.cbl:117-134` (including the `'1 '`->`01` and `'  '`->`00` cases), option 1 live, options 2..11 as the unavailable facade, Exit -> `/signon` with session invalidation, `menu` Angular component |
| Boundary seams | B-0010, B-0032, B-0033, B-0028, B-0027 (use) |
| Strict edges | needs wave 2 (session/user type) and wave 1 (session model) |
| Ported for the module | `COMEN01C` + `COMEN02Y` route table (S-02..S-10, S-13 inherit) |
| UI slice | **yes** — `COMEN1A` -> `/menu` |
| Parity evidence | FR-09..FR-12: the 11 verbatim labels, E-08 with the normalised echo, option-1 routing, Exit path. Note R-07: the legacy out-of-range subscript read is **not** reproduced — validate before indexing |

### Wave 4 — Account View (depth 3)

| | |
|---|---|
| Programs | `COACTVWC` (stream-private) |
| Repos / dirs | `backend/`, `frontend/` |
| Program FRs consumed | `COACTVWC` FR (FR-11 target side, FR-13..FR-21) |
| Contents | `AccountViewService`, `GET /api/accounts/{acctId}`, account-id edits (E-04, E-05) in COBOL order, xref -> account -> customer read chain, `AccountViewResponse` with `+ZZZ,ZZZ,ZZZ.99` money, `nnn-nn-nnnn` SSN, dates and the constant info line, `account-view` Angular component with the account field, error styling and Exit (F3) back to `/menu` |
| Boundary seams | B-0007, B-0001, B-0002 (use), B-0010 hard stop, B-0029, B-0031 |
| Strict edges | needs wave 1 (repositories) and wave 3 (menu entry/exit) |
| UI slice | **yes** — `CACTVWA` -> `/accounts/view` |
| Parity evidence | FR-13..FR-21 on Testcontainers PostgreSQL 16, incl. E-04, E-05, E-09, E-10, E-11, E-12 verbatim literal parts, the deliberate deviations DV-01..DV-05 asserted explicitly |

**Topological check.** Edges: `COSGN00C`->`users`/session (2->1); `COMEN01C`->session/route table
(3->1); `COACTVWC`->repositories (4->1) and ->menu route (4->3). No edge points forward; the
hard-stop edge `COACTVWC`->`COMEN01C` lands on wave 3, already delivered. Valid topological order.

**FR coverage by wave** (every FR is covered at least once): wave 1 — FR-23, FR-24; wave 2 —
FR-01..FR-08, FR-22, FR-25(SGN); wave 3 — FR-09..FR-12, FR-25(MEN); wave 4 — FR-11 (target side),
FR-13..FR-21. FR-08 and FR-25 are UI-only/decision items (FR §4 note, Q-06) and are verified by the
component specs and the recorded UI pass, not by a backend test.

---

## 7. Per-program FR generation

`!mf_program_fr_generation` runs **after STOP C approval and before wave 1**, once per program in
the FR §10 program index: `CSUTLDTC`, `COSGN00C`, `COMEN01C`, `COACTVWC`. Recommended: generate all
four up front (they are independent and small), so each wave child starts with its document in
hand. **No program enters a wave without its program FR** — a wave child that cannot find its
document stops and reports rather than inferring behaviour from the stream FR.

Each program FR inherits from this plan the boundary decisions of §3, the deviations DV-01..DV-05
of §9, and the Q-resolutions of §10 as approved at STOP C.

---

## 8. Testing, verification and sign-off

### 8.1 Per program — `!mf_program_parity_test`

For each of the four programs the parity run must produce: the requirement-by-requirement result
table (FR id -> expected from COBOL -> observed from the target -> pass/fail), the verbatim message
assertions for every error code the program owns, the fixture inputs used and their provenance
(`app/data/...`), the environment (must state PostgreSQL 16 via Testcontainers/Docker — a run on H2
is not evidence, D-0015), and an explicit list of accepted deviations with the decision id that
authorises each. No golden mainframe run exists (R-01), so "expected" is always derived from source
lines cited in the FR.

### 8.2 Stream end-to-end test

One scripted end-to-end pass after wave 4: sign on as a regular user -> main menu with 11 options ->
option 1 -> Account View -> valid account -> full account and customer block -> F3 -> back at the
main menu with identity intact. Plus the negative path set: blank account (E-04), non-numeric/zero
account (E-05), unknown xref (E-09), missing account master (E-10 + DV-01), missing customer
(E-11), wrong password (E-06), unknown user (E-07), invalid menu option (E-08), excluded option
2..11 facade message.

### 8.3 CI gates

`backend-ci.yml` must be green (`mvn -B clean verify`, unit + Testcontainers integration tests, on a
`postgres:16` service container per R-0003) and `frontend-ci.yml` must be green (`npm ci`,
`npm run build`, headless `ng test`) on every wave PR. The conformance skill's mechanical checks are
run in-session before the PR opens; a failing check is a drift-rule violation, cited by rule number.

### 8.4 UI verification (ONLINE — required)

`!mf_online_ui_testing` produces a **recorded pass** over the sign-off set — VC-01..VC-25 of FR §9,
driven through the Angular app against a running backend on PostgreSQL 16. Because the process type
is ONLINE, this is part of the sign-off set and not optional; the batch/subtransaction
output-evidence mode does not apply to this stream.

### 8.5 Sign-off gate

`!mf_stream_signoff` at STOP E, tied to the FR §8 acceptance criteria: all 25 FRs pass or carry an
approved deviation; all 14 error texts verified verbatim; the recorded UI pass exists; both CI
workflows green; the boundary register shows every S-01 row `IMPLEMENTED`; the open questions of
§10 are answered rather than assumed. An **independent audit child** (a session that did not build
any wave) re-runs the parity and conformance evidence and reports independently before sign-off.

---

## 9. Deliberate deviations from legacy behaviour

Each requires STOP C approval; each is asserted explicitly in the parity tests so it cannot drift
silently.

| Id | Deviation | Legacy behaviour | Target behaviour | Rationale |
|---|---|---|---|---|
| DV-01 | Account-master-missing shows no customer data | after `ACCTDAT` NOTFND the dead 88-levels leave the flow running, so `CUSTDAT` is read and the customer block is displayed under E-10 (`COACTVWC.cbl:704-715`, `:792`) | E-10 only, HTTP 404, no account or customer payload | the legacy behaviour is a coding defect (commented-out `SET`s), it displays an unread `ACCOUNT-RECORD` area, and reproducing it would ship undefined data (Q-03) |
| DV-02 | Identity survives navigation | COMMAREA may be wiped on entry to `COACTVWC` and `CDEMO-USER-TYPE` is forced to `'U'` on PF3 (`:283-286`, `:344`) | user id and type come from the HTTP session and are unchanged by navigation | the wipe depends on residual storage (INFERRED) and is not a business rule (Q-05) |
| DV-03 | No "invalid key" message | E-03 on any AID other than ENTER/PF3 (FR-25) | buttons and an F3 shortcut; no invalid-key state exists | a browser form has no AID; the message has no trigger (Q-06) |
| DV-04 | `CSUTLDTC` `TstDate:` segment holds the date as passed | the whole `WS-DATE-TO-TEST` group (2 binary length bytes + text) is moved into `WS-DATE X(10)`, corrupting the segment (`CSUTLDTC.cbl:122`) | the intended 10-character date | callers never parse that segment; reproducing binary length bytes in a Java string is meaningless (Q-07) |
| DV-05 | Full ZIP and phone values | the map truncates ZIP to 5 of 10 and phones to 13 of 15 (`COACTVW.bms:290-294`, `:318-321`) | full stored values | a display-width artefact of the 3270 map, not a business rule (Q-11) |

---

## 10. Proposed resolutions to FR §11 open questions (all **PROPOSED**, for confirmation at STOP C)

| Q | Proposed resolution | Impact if the customer disagrees |
|---|---|---|
| **Q-01** Admin users | Admin (`SEC-USR-TYPE='A'`) **authenticates normally and lands on the same `/menu`** with the banner `Administration is not available in this release`; no admin route exists in S-01. Rejected alternative: refusing admin sign-on — it would invent a rejection the legacy does not have and would break when S-11 lands | swap the banner for a hard 403 at sign-on; a wave-2 change only |
| **Q-02** Short account numbers | fewer than 11 digits -> **E-05** (`Account Filter must  be a non-zero 11 digit number`), same as non-numeric/zero. Target validates `^\d{11}$` and non-zero in the service, in COBOL order | if <11 digits should be accepted and left-padded, wave 4 changes one regex and adds a parity case |
| **Q-03** Account-missing quirk | **Fix, do not reproduce** — DV-01. Error only, 404, no data blocks | reproducing it means rendering an unread record area; if required, wave 4 must define what the "account block" contains in that state |
| **Q-04** Multiple cards per account (B-0007) | **Deterministic contract: lowest `xref_card_number` wins** — `findFirstByXrefAcctIdOrderByXrefCardNumberAsc`. Parity implication: legacy returns the first record on the AIX path, an order that is not defined in the repo; on the current fixture (50 xrefs / 50 distinct accounts, measured) the two are identical, so the deviation is unobservable today and becomes observable only if a multi-card account appears. R-0001 asks for the `DEFINE AIX` uniqueness | if the AIX turns out to be `UNIQUEKEY`, the `ORDER BY` is harmless and the contract stands; if the customer wants "most recently issued card", the repository method and one parity case change |
| **Q-05** Lost user id | **Session carries identity** — DV-02 | reproducing the wipe would require deliberately blanking the session, which no target mechanism justifies |
| **Q-06** PF keys | Buttons `Sign on`/`Exit`, `Continue`/`Exit`, `Search`/`Exit`, plus **`F3` and `Esc` bound to Exit** for muscle memory and full keyboard operability (WCAG 2.1 AA). FR-25 (invalid key) is dropped — DV-03. "Any key acts as ENTER" on Account View is not reproduced: only the Search button and Enter-in-form submit | if the customer wants the full PF-key map, it is a frontend-only change per screen |
| **Q-07** `CSUTLDTC` `TstDate:` defect | **Port the intended value** — DV-04 | byte-exact reproduction is possible but would require emitting two binary bytes into a text field; only worth it if a downstream consumer parses the string, and none does |
| **Q-08** CEEDAYS mapping | `java.time` substitute; **`0000` (valid) and `2513` (unsupported range) are contractual**, the other seven tokens are mapped from distinguishable parse failures and marked **INFERRED** in the wave-1 program FR; anything unmapped -> `Date is invalid`. B-0015 stays deferred with the re-entry condition of §3.3 | affects S-08/S-09 parity only; no S-01 requirement changes |
| **Q-09** RESP/RESP2 text | **Accepted as stated in the FR**: reproduce the literal parts of E-09..E-12 verbatim and render the numeric tail as the reference does (`Resp:13 Reas:0` style, no zero padding); byte parity with the COBOL move rules is explicitly not a goal (R-06) | if byte parity is demanded, it cannot be verified off-host (R-01) and would need a mainframe run |
| **Q-10** Date field format | `DATE` columns and `LocalDate`, **subject to a wave-1 gate**: the importer validates every date value in the full extract; a single non-ISO value flips the column to `VARCHAR(10)` and the DTO to raw text, matching the legacy unvalidated move. Decision recorded in the decision log at wave 1 | none — the gate is designed to absorb the answer |
| **Q-11** Truncated zip/phone | **Show full stored values** — DV-05 | if the 5/13-character truncation must be preserved, it is a frontend formatting change |
| **Q-12** Menu options 2..11 | Rendered with their verbatim legacy labels, visually disabled, and on selection `POST /api/menu/select` returns `implemented:false` with the target-only text **`Option not available in this release`** (marked NEW TEXT in `CobolMessages`, not a legacy literal). No link to the legacy CICS screens: no shared session exists between the two systems, so a link would strand the user. This route table is the module's strangler facade (B-0010) | alternatives (hide the rows entirely, or deep-link to a 3270 emulator) are single-file changes in `MenuService` plus the menu component |
| **Q-13** Password store | BCrypt hash column; legacy plaintext accepted **once** per user and rewritten as a hash on successful sign-on; **input upper-cased before hashing and comparison** so FR-04 case-insensitivity is preserved exactly; legacy plaintext column cleared on upgrade and never returned by any endpoint | if case-sensitivity is wanted going forward, it is a deliberate behaviour change and needs its own decision row |
| **Q-14** Unchecked `RECEIVE MAP RESP` | **Accepted**: the target validates the request payload explicitly (`@NotNull` presence + service-level legacy edits); there is no MAPFAIL analogue | none |

---

## 11. Risks and blockers (carried forward from the analysis, with the plan's answer)

| Id | Risk (analysis §7) | Plan response | Residual |
|---|---|---|---|
| R-01 | CICS programs cannot be compiled or run off-host; no golden run | parity expectations derive from cited source lines and fixture data; the audit child re-derives independently | accepted; the ceiling on parity confidence for this engagement |
| R-02 / Q-10 | INFERRED date types | wave-1 date gate (§4.2) | low |
| R-03 / B-0007 | AIX uniqueness unknown | deterministic `ORDER BY` contract + R-0001 question | low; unobservable on current data |
| R-04 | Bad fixture dates would throw on `LocalDate` parse | importer validates and reports per-row; the gate can flip the column to text | low |
| R-05 | Dead code in `COACTVWC` (the source of the FR-17 quirk) | DV-01; dead paragraphs are not ported | closed by decision |
| R-06 / Q-09 | RESP/RESP2 rendering is compiler-dependent | literal parts verbatim, numeric tail as a code | accepted |
| R-07 | `COMEN01C` indexes the option table before the range check | not reproduced; validate then index | closed |
| R-08 / Q-05 | COMMAREA wipe / forced user type | DV-02 | closed by decision |
| R-09 / Q-14 | `RECEIVE MAP RESP` never checked | explicit payload validation | closed |
| R-10 | `CSUTLDTC` ported with no in-stream caller | unit tests against the `CSUTLDWY` layout and the values callers branch on; S-08 re-verifies | medium until S-08 |
| R-11 / Q-01, Q-12 | Admin branch and 10 options excluded but reachable from the shared shell | the B-0010 facade + the admin banner | closed by decision |
| R-12 | `DFHAID`/`DFHBMSCA` absent, attribute semantics interpreted | red error field and cursor behaviour rendered per IBM documentation; the UI pass is the evidence | low |
| R-13 / Q-11 | Zip/phone truncation | DV-05 | closed by decision |
| **New — lead time** | data extract, IdP, CI PostgreSQL are external | R-0001, R-0002, R-0003 fired **now**, each with a documented assumption so no wave is blocked | see §12 |
| **New — Phase 0 size** | `backend/`, `frontend/` and `.github/` all start empty | Phase 0 is scoped in §5 and can be folded into wave 1 or run as its own PR | low |
| **New — Node version** | the dev image has Node 20.18.1; the target state proposes Node 22 (`07_runbook.md` §0) | Phase 0 installs Node 22 (nvm) and proposes a blueprint update; CI pins Node 22 | low |

**Blockers held open at STOP C**: none that stop execution. B-0007 and B-0015 were the analysis's
two unresolved contracts; both are now decided — B-0007 by a deterministic contract, B-0015 by an
explicit deferral with dependency, impact and a named re-entry condition (§3.3).

---

## 12. Lead-time requests (simulated, fired now — not at cutover)

| Id | Team | Subject | Blocking | Assumption in force | File |
|---|---|---|---|---|---|
| R-0001 | Data Management | Initial extract of ACCTDAT, CUSTDAT, CCXREF, USRSEC + AIX uniqueness + date-format confirmation | no | committed `app/data/` fixtures are the extract | `.migration/requests/R-0001_initial_data_extract_vsam_to_postgresql.md` |
| R-0002 | Security/SSO | OIDC client registration for the migrated sign-on (B-0026 deferral) | no | USRSEC stub + BCrypt upgrade-on-login stays | `.migration/requests/R-0002_sso_idp_enablement_signon.md` |
| R-0003 | Infrastructure | PostgreSQL 16 service container + Docker for Testcontainers in CI | no | GitHub-hosted runners with a `postgres:16` service | `.migration/requests/R-0003_ci_postgresql16_service_container.md` |

All three run as a **parallel track** alongside execution: they are sent at the start of Phase 0, and
no wave waits on them because each carries a documented fallback. R-0001 and R-0003 must be answered
before **sign-off** (STOP E) if their assumptions are to be retired; R-0002 is answered whenever the
B-0026 re-entry condition fires.

---

## 13. Effort and sequencing overview

Sequential waves, one child session each, on the same stream branch.

| Step | Unit | Depends on | Parallel track |
|---|---|---|---|
| STOP C | customer approves this plan | — | R-0001/2/3 already sent |
| Program FRs | `!mf_program_fr_generation` ×4 | STOP C | — |
| Phase 0 | scaffolding per §5 (own PR, or folded into wave 1) | program FRs | requests outstanding |
| Wave 1 | data seams + session model + `CSUTLDTC` | Phase 0 | — |
| Wave 2 | sign-on (backend + UI) | wave 1 | — |
| Wave 3 | menu shell (backend + UI) | wave 2 | — |
| Wave 4 | Account View (backend + UI) | waves 1, 3 | — |
| Verification | parity per program, stream end-to-end, recorded UI pass, CI green | wave 4 | — |
| STOP E | `!mf_stream_signoff` + independent audit child | verification | requests R-0001/R-0003 answered or their assumptions accepted |

Sizing, in agent sessions rather than calendar time: Phase 0 ≈ 1 session; wave 1 ≈ 1 session;
waves 2, 3 ≈ 1 session each; wave 4 ≈ 1-2 sessions (largest surface: 30+ display fields, 6 error
codes); verification and audit ≈ 1 session. Total ≈ 6-7 sessions if nothing external blocks. The
only true external dependencies are the three simulated requests, and none of them gates a wave.

---

## 14. Divergence from cross-check branches

Not consulted. This plan is derived from `app/` source on the work branch and from the committed
`AccountView_analysis.md`, `AccountView_functional_requirement.md`, `CardDemo_target_state.md`,
`CardDemo_inventory.md` and `.migration/` only. The prior-run branches carry a .NET backend and a
JWT auth model that the confirmed target state contradicts (D-0017, target state §9), so consulting
them would add noise, not evidence.

---

## 15. Validation checklist

1. **Waves and DAG match the analysis** — 4 waves, same contents and same strict edges as
   `AccountView_analysis.md` §6; count unchanged and derived from DAG depth — **pass**.
2. **Wave order is a valid topological sort** — checked in §6; no forward edge; the hard stop lands
   on an already-delivered wave — **pass**.
3. **Every stream FR requirement is covered by at least one wave** — 25/25 mapped in §6 (FR-08 and
   FR-25 by UI slice/decision) — **pass**.
4. **Every boundary has a decision, a seam, an owner, and where relevant a fired request and a
   cutover condition** — 16 rows in §3 (B-0001, B-0002, B-0006, B-0007, B-0009, B-0010, B-0014,
   B-0015, B-0026, B-0027, B-0028, B-0029, B-0030, B-0031, B-0032, B-0033); no row left undecided;
   the one deferral (B-0015) carries dependency, impact and a named re-entry condition — **pass**.
5. **Scaffolding deltas are explicit** — §5 lists every Phase 0 artefact and marks it adapted,
   re-implemented or new; nothing is copied wholesale from `spring-boot/` — **pass**.
6. **The plan matches the process type's surfaces and verification mode and cites the surface
   profile it followed** — ONLINE profile (`CardDemo_target_state.md` §4) mapped in §2.2; three UI
   slices; recorded UI pass required in the sign-off set; BATCH/SUBTRANSACTION conventions not
   borrowed — **pass**.
7. **Shared programs are ported once with ownership recorded** — `COSGN00C` (wave 2), `COMEN01C` +
   `COMEN02Y` (wave 3), `CSUTLDTC` (wave 1), COMMAREA/session model and the four tables (wave 1),
   each with the inheriting streams named in §1 and §6 — **pass**.

Feeds: STOP C, then `!mf_program_fr_generation` ×4, then waves 1..4 via the orchestrator.
