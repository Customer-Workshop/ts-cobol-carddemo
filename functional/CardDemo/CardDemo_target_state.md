# CardDemo Target State (CORE + per-surface profiles)

Status: **DRAFT — awaiting STOP A confirmation.** Every field is marked **FACT** (cited from a
source in this repo) or **PROPOSED** (a default that must be explicitly confirmed or corrected
before `!mf_migration_setup`, `!mf_stream_migration_plan` or `!mf_program_migration` may run).

- Engagement: CardDemo core (CICS/COBOL/VSAM) -> Java 21 / Spring Boot 3 / Angular / PostgreSQL 16.
- First stream: **Account View** (`COACTVWC`, transaction `CAVW`), an ONLINE CICS transaction.
- Working language: English. No mainframe/DB2 access: all legacy behaviour is source-derived.
- Repo: `Cognition-Partner-Workshops/ts-cobol-carddemo`, branch
  `devin/1788757216-cardemo-account-view-stream`, this file at
  `functional/CardDemo/CardDemo_target_state.md`.

Reference module cites below are into the `spring-boot/` module as committed at `38ee98d`
("Bring spring-boot reference module onto Account View stream branch"). `spring-boot/` is a
**reference only**; the target implementation lives in `backend/` and `frontend/` (to be created
in Phase 0).

---

## 0. Sources

| # | Source | Kind | Covers | Cite |
|---|---|---|---|---|
| S1 | Customer architecture board non-negotiables (given in the orchestrator brief) | mandate | CORE, DATA | brief, section "Non-negotiables" |
| S2 | `spring-boot/` migrated Spring Boot module | reference repo | CORE, ONLINE, DATA/BOUNDARY | `spring-boot/pom.xml`, `spring-boot/README.md`, `spring-boot/src/**` |
| S3 | Repo root `README.md` | stack doc (legacy) | SOURCE inventory | `README.md:1-60` |
| S4 | Legacy source `app/` | source | type mapping, screen/flow semantics | `app/cbl/COACTVWC.cbl`, `app/cpy/CVACT01Y.cpy`, `app/csd/CARDDEMO.CSD` |
| S5 | Reference build run in this session | evidence | test/CI conventions | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -q clean verify` -> exit 0, 34 tests, 0 failures (6 surefire reports: ApiIntegrationTest 18, BatchJobIntegrationTest 7, DataSeederForceIntegrationTest 1, DataSeederIntegrationTest 2, CobolFieldReaderTest 4, MenuServiceTest 2) |
| X1 | Branch `devin/1787242078-carddemo-premigration` | cross-check only | all | `functional/CARDDEMO/CardDemo_target_state.md`, `.migration/`, `backend/`, `frontend/` on that branch |
| X2 | Branch `devin/batch-a-s02-account-view` | cross-check only | all | same paths on that branch |

No stack document or skill exists for any surface; the reference repository (S2) is the primary
evidence and outranks any document.

---

## 1. Repository topology (roles to routes)

Single repository, base branch `main`, work branch `devin/1788757216-cardemo-account-view-stream`.

| Role | Route | Status | Evidence |
|---|---|---|---|
| SOURCE | `app/` (`app/cbl`, `app/cpy`, `app/cpy-bms`, `app/bms`, `app/jcl`, `app/proc`, `app/ctl`, `app/csd/CARDDEMO.CSD`, `app/scheduler`, `app/data/{ASCII,EBCDIC}`) | FACT | `ls app` in this session lists exactly these plus `asm`, `catlg`, `maclib` and the optional extensions `app-authorization-ims-db2-mq`, `app-transaction-type-db2`, `app-vsam-mq` |
| REFERENCE (read-only) | `spring-boot/` | FACT | `spring-boot/pom.xml:1-70` (Maven, Spring Boot parent 3.4.5, `java.version=21`); `spring-boot/README.md:1-8` |
| BACKEND (target) | `backend/` | FACT (route given) / **does not exist yet** on this branch | `git ls-tree HEAD` has no `backend/`; Phase 0 creates it |
| FRONTEND (target) | `frontend/` | FACT (route given) / **does not exist yet** on this branch | `git ls-tree HEAD` has no `frontend/` or `angular.json`; Phase 0 creates it |
| DOCS | `functional/` | FACT (route given); directory created by this artifact | this file |
| CI | `.github/workflows/` | **does not exist yet** on `main` or this branch (`git ls-tree HEAD` shows no `.github`) | must be created in Phase 0 (see CORE "CI gates") |

Flags:
- **F1 — case-variant look-alike path.** Prior-run branches X1/X2 use `functional/CARDDEMO/` (upper
  case); this engagement's brief mandates `functional/CardDemo/`. Both cannot coexist on a
  case-insensitive filesystem. This artifact uses `functional/CardDemo/` as instructed; if X1/X2
  are ever merged, the `CARDDEMO` tree must be renamed or dropped, not merged alongside.
- **F2 — `backend/` on prior-run branches is C#/.NET**, not Java (see section 9). It must not be
  taken as a starting point for the Java `backend/`.
- **F3 — no `.github/` on `main`.** GitHub Actions CI is a non-negotiable but no workflow exists yet;
  a Phase 0 deliverable.

---

## 2. Surfaces in scope

| Surface | In scope for this engagement? | Reason / evidence |
|---|---|---|
| CORE | Yes | applies to everything |
| ONLINE | Yes | first stream Account View = CICS transaction `CAVW` -> `COACTVWC` (`app/csd/CARDDEMO.CSD:181-185,317` defines PROGRAM(COACTVWC) TRANSID(CAVW); `app/cbl/COACTVWC.cbl:211-292` COMMAREA handling; `app/bms/COACTVW.bms` map) |
| DATA / BOUNDARY | Yes | Account View reads `CXACAIX` (card xref AIX by account), `ACCTDAT`, `CUSTDAT` (`app/csd/CARDDEMO.CSD:1-2,50-52,63`) |
| BATCH | **N/A** | the first stream (Account View) has no batch chain. Not invented here; re-run this playbook with a BATCH reference before any batch stream is planned. The reference module does contain Spring Batch jobs (`spring-boot/src/main/java/com/carddemo/batch/*`) that may serve as evidence at that time, but they were not profiled. |
| SUBTRANSACTION | **N/A** | Account View has no CALLed sub-flow of its own (its only cross-program hand-offs are `EXEC CICS XCTL` returns to the caller/menu, `app/cbl/COACTVWC.cbl:349-351`, which are ONLINE navigation, not a called contract). Re-run this playbook when a stream that CALLs/LINKs a utility (e.g. `CSUTLDTC`) is selected. |

---

## 3. CORE profile (applies to every surface)

| Field | Value | Status | Cite |
|---|---|---|---|
| Language + version | Java 21 | FACT | S1; `spring-boot/pom.xml:21` `<java.version>21</java.version>`; build ran on `Java 21.0.12` (S5) |
| Framework | Spring Boot 3 (reference pins 3.4.5 via `spring-boot-starter-parent`) | FACT (Spring Boot 3) / PROPOSED (pin `3.4.x` line, latest patch at Phase 0) | S1; `spring-boot/pom.xml:8-12` |
| Build tool | Maven, single module per repo role (`backend/pom.xml`), `spring-boot-maven-plugin` | FACT (Maven) / PROPOSED (single module) | S1; `spring-boot/pom.xml:63-68` |
| Java toolchain on dev boxes | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` (system `/usr/bin/java` is Java 8) | FACT | `spring-boot/README.md:6-9`; blueprint notes |
| Root package | `com.carddemo` | FACT (reference) / PROPOSED (reuse for `backend/`) | `spring-boot/src/main/java/com/carddemo/CardDemoApplication.java` |
| Layering + packages | `api` (controllers + request/response records + error model), `service` (business rules, one service per COBOL program), `repository` (Spring Data JPA interfaces), `model` (JPA entities), `security`, `data` (seed/fixed-width readers) | FACT | directory layout of `spring-boot/src/main/java/com/carddemo/{api,service,repository,model,security,data,batch}` |
| Naming | Controller `<Noun>Controller`; service `<Noun><Verb>Service` (e.g. `AccountViewService`); DTOs `<Noun><Verb>Request` / `<Noun><Verb>Response`; entities singular nouns; entity fields keep the COBOL field name in camelCase (`ACCT-CURR-BAL` -> `acctCurrBal`); DTO fields are business English (`currentBalance`) | FACT | `AccountController.java:8`, `AccountViewService.java:18`, `AccountViewResponse.java:6-14`, `model/Account.java:13-24` |
| DTO / mapping style | Java `record` for every request/response; hand-written mapping in the service (`AccountViewService.map(...)`); **no** MapStruct/ModelMapper/AutoMapper | FACT | `api/AccountViewResponse.java:6`; `service/AccountViewService.java:51-63`; `pom.xml` has no mapper dependency |
| Lombok | **Forbidden.** Entities use explicit getters/setters | FACT | S1; `grep -rl lombok spring-boot` -> none; `model/Account.java:26-49` |
| Constructor injection | constructor injection of final fields; no `@Autowired` on fields in main code | FACT | `AccountController.java:9-13`, `AccountViewService.java:19-28` |
| COBOL type mapping | `PIC 9(n)` identifiers -> `Long` (JSON number); `PIC S9(n)V99` money -> `BigDecimal` (`precision=19, scale=2`), never `double`/`float`; `PIC X(10)` dates -> `LocalDate`; `PIC X(n)` codes/flags -> `String` with `@Column(length=n)`, trailing spaces stripped, blank -> `null`; `PIC 9(3)` FICO -> `Integer`; SSN stored as `Long`, rendered `###-##-####` in DTOs; fixed-width display codes (CVV, type/category codes) stay `String` | FACT | `model/Account.java:13-24` vs `app/cpy/CVACT01Y.cpy:5-17`; `model/Customer.java:13-30`; `AccountViewService.formatSsn` (`:65-71`); `data/CobolFieldReader.java:17-96`; `spring-boot/README.md` "Account/customer identifiers are JSON numbers..." |
| Signed zoned-decimal decoding | overpunch `{`,`}`,`A-I`,`J-R` decoded exactly as in `CobolFieldReader.signedDecimal` | FACT | `data/CobolFieldReader.java:71-96` |
| Error handling | one `CobolApiException(HttpStatus, message)` thrown from services; `@RestControllerAdvice GlobalExceptionHandler` maps it and framework exceptions to `ErrorResponse(message, status, timestamp)`; legacy screen messages preserved **verbatim** in a `CobolMessages` constants class (COBOL `WS-RETURN-MSG` text) | FACT | `api/CobolApiException.java:5-15`, `api/GlobalExceptionHandler.java:16-49`, `api/ErrorResponse.java:5`, `api/CobolMessages.java:3-127` |
| Status-code mapping | validation failure -> 400; entity not found (VSAM `RESP 13 NOTFND`) -> 404; bad credentials -> 401; admin-only -> 403; unexpected -> 500 with generic message (no stack trace leak) | FACT | `AccountViewService.java:33,39,43,46`; `AuthService.java:41-53`; `GlobalExceptionHandler.java:41-44`; `SecurityConfig.java:55-63` |
| Logging / observability | SLF4J via `LoggerFactory.getLogger` with Spring Boot default Logback; `INFO` for lifecycle, no secrets/PII in logs | FACT (SLF4J usage) / PROPOSED (add Spring Boot Actuator `health`,`info` endpoints only, no Micrometer registry until asked) | `data/DataSeeder.java:23-24,47,103-151` |
| Test framework | JUnit 5 (Jupiter) via `spring-boot-starter-test`; Mockito for unit tests; `@SpringBootTest @AutoConfigureMockMvc` + `MockMvc` + `jsonPath` for API integration tests; Hamcrest matchers | FACT | S1; `test/.../service/MenuServiceTest.java:8-17`; `test/.../ApiIntegrationTest.java:10-34` |
| Test conventions | unit tests mirror the main package (`com.carddemo.service.MenuServiceTest`); integration tests at `com.carddemo.*IntegrationTest`; one test asserts legacy validation order **and** verbatim messages (`signonPreservesCobolValidationOrderAndMessages`); fixtures under `src/test/resources/seed/{ASCII,EBCDIC}` | FACT | `ApiIntegrationTest.java:68-108`; `spring-boot/src/test/resources/seed/**` |
| Test data | tiny fixture copy of `app/data` under test resources, selected with `carddemo.seed.data-dir=classpath:seed` | FACT (reference) / PROPOSED (in target: Flyway `R__seed_*.sql` test-only migrations replace the seeder; see DATA) | `ApiIntegrationTest.java:30-33` |
| Build command | `mvn -q clean verify` (compiles, runs unit + integration tests) | FACT | `spring-boot/README.md:12-13`; S5 |
| CI gates | GitHub Actions workflow `.github/workflows/backend-ci.yml`: `actions/setup-java@v4` (Temurin 21), `mvn -B clean verify` in `backend/`; frontend workflow `frontend-ci.yml`: `setup-node` (Node 22 LTS), `npm ci`, `npm run build`, `npm test -- --watch=false --browsers=ChromeHeadless`; both required on PRs touching their paths | FACT (GitHub Actions mandated, S1) / PROPOSED (file names, steps, Node 22) | S1; no workflow exists on `main` (F3) |
| Code style | 4-space indent, 120-col lines, `import x.y.*` tolerated in controllers only; no checkstyle/spotless plugin in the reference | FACT (observed) / PROPOSED (do **not** add a formatter plugin in Phase 0; revisit at wave 1) | all `spring-boot/src` files |
| Configuration | `application.properties` (not YAML); custom keys under `carddemo.*` | FACT | `spring-boot/src/main/resources/application.properties:1-17` |
| Forbidden libraries / patterns | Lombok; MapStruct/ModelMapper; `double`/`float` for money; field `@Autowired`; `spring.jpa.hibernate.ddl-auto=create|update` in non-test profiles; stack traces or COBOL-internal RESP codes leaking beyond the verbatim legacy message; business logic in controllers; stubbed/TODO behaviour where the FR defines it | FACT (Lombok, S1) / PROPOSED (rest, derived from reference practice) | see cites above |

---

## 4. ONLINE profile (ONLINE streams; first stream: Account View)

| Field | Value | Status | Cite |
|---|---|---|---|
| API style | JSON REST, one resource path per COBOL program, mapped in a table kept in `backend/README.md` (reference: `GET /api/accounts/{acctId}` -> `COACTVWC`) | FACT | `spring-boot/README.md` "REST endpoints mapped to COBOL"; `api/AccountController.java:7-16` |
| Versioning | path prefix `/api/...` **without** a version segment (reference has none) | FACT (reference) / PROPOSED (keep unversioned for the strangler period; introduce `/api/v2` only on a breaking change) | `AccountController.java:7` |
| Request DTO shape | `record` with Jakarta Validation annotations for presence (`@NotNull`); legacy business edits (blank, numeric, range) are done in the service so the **COBOL validation order and message text are preserved** | FACT | `api/AuthRequest.java:5`; `service/AuthService.java:40-45`; `service/AccountViewService.java:31-34` |
| Response DTO shape | flat `record`, one per screen (all BMS output fields of the map become fields; entity graph flattened, e.g. account + customer in `AccountViewResponse`) | FACT | `api/AccountViewResponse.java:6-14` |
| Path/ID handling | account id accepted as `String` path variable, validated `\d{1,11}` non-zero (legacy: "Account Filter must  be a non-zero 11 digit number"), normalised to 11 digits for messages, returned as JSON number | FACT | `AccountViewService.java:30-36`; `CobolMessages.java:43-44` |
| Session / conversational state | server-side HTTP session (`HttpSessionSecurityContextRepository`, `SessionCreationPolicy.IF_REQUIRED`); sign-on `POST /api/auth/signon` establishes the session cookie; `GET /api/auth/session`, `POST /api/auth/signoff`. CICS COMMAREA pseudo-conversational state is **not** persisted server-side beyond the session; screen-to-screen context travels in request/response DTOs | FACT | `security/SecurityConfig.java:29-52`; `api/AuthController.java:19-33`; `service/AuthService.java:54-73`; `spring-boot/README.md` "Deviations" bullet 1 |
| Authentication / authorization | Spring Security; users from USRSEC (`SecurityUser`); roles `ADMIN`/`USER` from `SEC-USR-TYPE` `A`/`U`; `/api/admin/**` -> `hasRole("ADMIN")`; everything else authenticated; CSRF disabled (JSON API); form login and HTTP basic disabled; JSON 401/403 bodies | FACT | `SecurityConfig.java:37-51,55-63`; `AuthService.java:60` |
| Password handling | reference keeps `UsrsecPlaintextPasswordEncoder` for the legacy fixture only | FACT (reference) / **PROPOSED for target: BCrypt via `DelegatingPasswordEncoder`, USRSEC plaintext accepted only through a one-time upgrade-on-login path in dev profiles** | `security/UsrsecPlaintextPasswordEncoder.java`; `spring-boot/README.md` "Sign-on" |
| Validation & error surfacing | 400 + verbatim legacy message for edits; 404 + verbatim `xrefNotFound / accountNotFound / customerNotFound` text (incl. `Resp:13 Reas:0` suffix as in the COBOL) | FACT | `CobolMessages.java:117-127`; `ApiIntegrationTest.java:105-107` |
| Paging (list screens) | legacy page size preserved (e.g. 7 rows for card list), `pageSize` in the response, top/bottom messages verbatim | FACT | `ApiIntegrationTest.java:113-118`; `CobolMessages.java:34-41` |
| PF keys / BMS attributes / cursor | not represented in the API; screen navigation becomes frontend routing | FACT | `spring-boot/README.md` "Deviations" bullet 2 |
| Menu -> program routing | `POST /api/menu/select` returns `{program, endpoint, implemented, message}` so the UI routes by COBOL program id | FACT | `ApiIntegrationTest.java:85-96`; `api/MenuSelectionResponse.java` |
| UI framework | Angular, **standalone components**, Angular CLI project at `frontend/` | FACT (Angular mandated, S1) / **PROPOSED: Angular 18 LTS line (`^18.2`), TypeScript ~5.5, Node 22** | no Angular reference on `main`; version choice is a default |
| Component library | **PROPOSED: Angular Material + CDK (same major as Angular)**; no third-party grid | S1 leaves open; default |
| Theming | **PROPOSED:** single Material theme (indigo/pink prebuilt) in Phase 0; a "3270 look" is explicitly **not** a goal — screens are re-laid-out as forms/tables with the same field set and labels as the BMS map | default |
| i18n | **PROPOSED:** English only; labels hard-coded from the BMS map text (`app/bms/*.bms`) with Angular `i18n` attributes added but no extraction pipeline yet | source labels are English (`app/bms/COACTVW.bms`) |
| Accessibility | **PROPOSED:** WCAG 2.1 AA baseline via Material components, labelled form fields, keyboard-only operability (replaces PF-key navigation) | default |
| Frontend state | **PROPOSED:** plain services + RxJS/Signals; no NgRx in Phase 0 | default |
| Frontend HTTP | **PROPOSED:** `HttpClient` with `withCredentials: true` (session cookie), one `ApiService` per backend resource, DTO interfaces generated by hand from the backend `record`s (no OpenAPI codegen in Phase 0) | default consistent with session-cookie auth (FACT above) |
| Frontend tests | **PROPOSED:** Angular CLI default Jasmine + Karma (ChromeHeadless in CI); one spec per component/service; no e2e framework in Phase 0 | default |
| Dev proxy | **PROPOSED:** `frontend/proxy.conf.json` forwarding `/api` to `http://localhost:8080` | default |

---

## 5. DATA / BOUNDARY profile (all streams)

| Field | Value | Status | Cite |
|---|---|---|---|
| Data target | **PostgreSQL 16** | FACT | S1 |
| Deliberate divergence from reference | the reference runs on **H2 in-memory** with `ddl-auto=create-drop` and seeds from `../app/data` at startup (`DataSeeder`). The target profile is PostgreSQL 16 + Flyway. **PROPOSED:** H2 remains **only** as a `test` profile (`src/test/resources/application-test.properties`, `spring.flyway.enabled=true` against H2 in PostgreSQL compatibility mode, `MODE=PostgreSQL`) so `mvn verify` needs no Docker; integration tests against real PostgreSQL 16 via Testcontainers are added at wave 1 if the customer wants them | FACT (reference behaviour) / PROPOSED (H2 test-only) | `application.properties:2-5,11-17`; `data/DataSeeder.java:43-45,98-106` |
| Persistence style | Spring Data JPA (`JpaRepository<Entity, Id>`), derived query methods for AIX-style lookups (`findByXrefAcctId`), entities in `model`, `spring.jpa.open-in-view=false` | FACT | `repository/CardXrefRepository.java:5-6`; `application.properties:6` |
| Schema management | **Flyway** versioned migrations `backend/src/main/resources/db/migration/V<n>__<desc>.sql`; `spring.jpa.hibernate.ddl-auto=validate` in every non-test profile | FACT (Flyway mandated, S1) / PROPOSED (`validate`, file naming) | S1 |
| Table / column naming | snake_case plural tables (`accounts`, `customers`, `card_xrefs`), one table per VSAM base cluster; columns = entity field names in snake_case (Spring default naming strategy), so `ACCT-CURR-BAL` -> `acct_curr_bal` | FACT | `model/Account.java:11`, `model/Customer.java:10`, `model/CardXref.java:9` |
| Dataset mapping (VSAM -> tables) | KSDS -> table with the KSDS key as primary key (`acct_id`, `cust_id`, `xref_card_number`); AIX (`CXACAIX` by account id) -> **PROPOSED:** a B-tree index on `card_xrefs(xref_acct_id)` created in the Flyway migration (the reference relies on Hibernate DDL, no explicit index) | FACT (key mapping) / PROPOSED (explicit index) | `model/*.java` `@Id` fields; `app/csd/CARDDEMO.CSD:63` |
| Column types (PostgreSQL) | `PIC 9(11)` -> `BIGINT`; `PIC S9(10)V99` -> `NUMERIC(19,2)`; `PIC X(10)` date -> `DATE`; `PIC X(n)` -> `VARCHAR(n)`; `PIC 9(3)` -> `INTEGER` | FACT (JPA column metadata) / PROPOSED (concrete DDL) | `model/Account.java:13-24` |
| Transaction / unit-of-work boundary | `@Transactional` on service methods that write (one CICS task = one service method = one DB transaction); read-only view services carry no transaction annotation | FACT | `service/AccountUpdateService.java:38-39`; `service/AccountViewService.java:30` |
| Initial data load | reference: `DataSeeder` `CommandLineRunner` parsing `app/data/ASCII/*` and EBCDIC `USRSEC` (IBM037). **PROPOSED for target:** keep a `CobolFieldReader`-based importer as a **one-shot CLI profile** (`--spring.profiles.active=import`) writing into PostgreSQL, not a startup seeder; test profile uses small Flyway `R__seed_test_data.sql` instead | FACT (reference) / PROPOSED | `data/DataSeeder.java`, `data/CobolFieldReader.java`; `spring-boot/README.md` "Seeding" |
| Known data quirk | sample `acctdata` stores `ACCT-GROUP-ID` in the ZIP slot (`carddemo.seed.acctdata-group-id-in-zip-slot=true`) | FACT | `application.properties:15-17` |
| Stored procedures | none; **PROPOSED:** no stored procedures/triggers for any family — logic stays in Java services | FACT (reference has none) / PROPOSED (policy) | `spring-boot/src` |
| Outbound integration seam | none in the core module (MQ/IMS/DB2 belong to optional extensions under `app/app-*`, out of scope). **PROPOSED:** when first needed, a `client` package using `RestClient`/`JmsTemplate` with explicit connect/read timeouts and Spring Retry; no circuit breaker library until a second consumer exists | N/A now / PROPOSED | `ls app` |
| Inbound exposure | REST controllers only; no direct DB exposure; H2 console disabled (`spring.h2.console.enabled=false`) | FACT | `application.properties:9`; `SecurityConfig.java:44` |
| Coexistence / strangler routing | **PROPOSED:** per-stream cutover; legacy `app/` untouched (additive port, S2 README: "Original COBOL ... under `app/` are not modified"); until a stream is signed off the VSAM datasets remain source of truth and the importer re-loads PostgreSQL from `app/data` exports; no live dual-write | FACT (additive) / PROPOSED (cutover policy) | `spring-boot/README.md:3-4` |

---

## 6. BATCH profile — **N/A**

Not in scope for this engagement's first stream (Account View is an ONLINE transaction with no
batch chain). No batch runtime, job model, restart/checkpoint, scheduler or dataset-mapping
decisions are made here. When a BATCH stream is selected, re-run `!mf_ingest_target_state` with a
BATCH reference; the reference module's Spring Batch jobs (`spring-boot/src/main/java/com/carddemo/batch/`,
`spring-boot/README.md` "Batch jobs, origins, and launch") are candidate evidence but were **not**
profiled or confirmed.

## 7. SUBTRANSACTION profile — **N/A**

Not in scope: Account View performs no `CALL`/`LINK` to a sub-program; its `XCTL` hand-offs are
screen navigation (ONLINE). When a stream that calls a utility such as `CSUTLDTC` is selected,
re-run this playbook; the expected shape (CORE + nearest sibling) would be an in-process Spring
`@Service` with a typed request/response record, participating in the caller's `@Transactional`
scope — recorded here only as the likely analogue, **not** as a decision.

---

## 8. Cross-profile reconciliation

| Topic | Finding | Verdict |
|---|---|---|
| Type mapping | identical in ONLINE DTOs and DATA entities (BigDecimal/LocalDate/Long) | shared -> lives in CORE |
| Error model | single `CobolApiException` + `ErrorResponse` used by controllers and by the security JSON handlers | shared -> CORE |
| Test conventions | same JUnit 5 / MockMvc stack for API and data tests | shared -> CORE |
| Persistence | reference = H2 + Hibernate DDL; mandate = PostgreSQL 16 + Flyway | **deliberate divergence** (customer mandate outranks reference); H2 survives only as test profile (PROPOSED) |
| Session model vs SPA | server-side session cookie (FACT, backend) requires `withCredentials` and a same-site dev proxy in the Angular app (PROPOSED) | consistent; no JWT introduced |
| Seeding | reference startup seeder vs target Flyway + one-shot importer | deliberate: startup seeding is incompatible with Flyway-owned schema in a shared PostgreSQL |
| Versioning | reference unversioned `/api`; prior-run X1 used `/api/v1` | kept unversioned to follow the reference repo (outranks the prior-run doc) — PROPOSED |

---

## 9. Cross-check against prior-run artifacts (X1, X2 — input only, not reused)

Read: `functional/CARDDEMO/CardDemo_target_state.md`, `.migration/01_target_state.md`,
`.github/workflows/target-ci.yml`, `frontend/package.json`, `backend/**` on both branches.

| Item | Prior-run (X1/X2) | This artifact | Divergence |
|---|---|---|---|
| Backend stack | **C# 12 / .NET 8 / ASP.NET Core 8**, EF Core 8 + Npgsql, xUnit, Serilog ("CONFIRMED at STOP A 2026-08-20") | Java 21 / Spring Boot 3 / JPA / Flyway / JUnit 5 (S1 mandate) | **Total.** The prior `backend/` (.csproj, Controllers/*.cs) cannot be reused or referenced. |
| Auth | stateless JWT | server-side HTTP session (reference FACT) | divergent |
| API versioning | `/api/v1` + Swashbuckle OpenAPI | unversioned `/api`, no OpenAPI tooling in Phase 0 | divergent (PROPOSED here) |
| Frontend | Angular `^18.2` + Angular Material `^18.2.14`, Jasmine/Karma, Node 22 | same defaults proposed here | **agrees** (independent corroboration of the PROPOSED Angular defaults) |
| DOCS path | `functional/CARDDEMO/` | `functional/CardDemo/` | case-variant clash (flag F1) |
| Surfaces | BATCH and SUBTRANSACTION profiled | N/A for this engagement | scoping difference by instruction |
| Restart/checkpoint, scheduler | PostgreSQL job-execution table, exit-code contract | not decided (N/A) | none to reconcile |
| Fields I had missed and added after cross-check | explicit AIX -> index policy; frontend dev proxy; Node version in CI | added as PROPOSED | — |

---

## 10. Drift rules (what gets a PR rejected), per profile

### CORE
1. Any Java version other than 21, Spring Boot major other than 3, or build tool other than Maven.
2. Lombok, MapStruct, ModelMapper or any annotation-processor-based mapping/boilerplate library.
3. `double`/`float` for any COBOL numeric with a `V` (money) or for balances/limits.
4. Request/response types that are classes instead of `record`s; entities that use Lombok or public fields.
5. Field `@Autowired`; business logic in a `@RestController`.
6. Legacy screen message text altered, translated, or reworded (must match the COBOL `WS-RETURN-MSG`/`CCARD-ERROR-MSG` literal, incl. spacing).
7. Stack traces, SQL, or Hibernate messages in any HTTP response body.
8. Test framework other than JUnit 5; a new endpoint or service without an integration test that asserts status **and** message.
9. `mvn -q clean verify` not green, or no GitHub Actions workflow covering the changed path.
10. Any edit under `app/` (legacy source is read-only).

### ONLINE
1. Endpoint not registered in the program -> endpoint table in `backend/README.md`.
2. Validation order differing from the COBOL paragraph order, or validation done only via Jakarta annotations where the COBOL emits a specific message.
3. JWT/bearer tokens or any auth scheme other than the session-cookie model without a confirmed change to this document.
4. Frontend code outside `frontend/`, non-standalone Angular components, a component library other than Angular Material, or a state library (NgRx etc.) not in this document.
5. Frontend calling the backend without `withCredentials`, or hard-coding the backend host.
6. Any screen shipped without its Jasmine spec, or CI not running `ng test` headless.

### DATA / BOUNDARY
1. Schema change without a Flyway `V<n>__*.sql`; `ddl-auto` other than `validate` in a non-test profile.
2. Any datasource other than PostgreSQL 16 in `application.properties` (H2 permitted only in `application-test.properties`).
3. Startup-time seeding (`CommandLineRunner`) in the default profile.
4. Native SQL in repositories where a derived query or JPQL suffices; stored procedures or triggers.
5. Money columns not `NUMERIC(19,2)`; date columns stored as text.
6. Writes outside a `@Transactional` service method; `@Transactional` on controllers.
7. Direct database or H2-console exposure over HTTP.

---

## 11. PROPOSED fields requiring confirmation at STOP A

**CORE**
- C1 Pin Spring Boot to the `3.4.x` line (latest patch at Phase 0).
- C2 Single-module Maven project at `backend/pom.xml`, root package `com.carddemo`, same package layout as the reference.
- C3 Observability: Spring Boot Actuator `health`/`info` only; no Micrometer registry yet.
- C4 CI files `backend-ci.yml` / `frontend-ci.yml` with Temurin 21 and Node 22 as described.
- C5 No formatter/checkstyle plugin in Phase 0.
- C6 The "rest of" forbidden list beyond Lombok (mappers, float money, field injection, ddl-auto, leaks).

**ONLINE**
- O1 Keep `/api/...` unversioned during coexistence.
- O2 BCrypt password storage with upgrade-on-login for USRSEC plaintext (reference keeps plaintext).
- O3 Angular 18 LTS line, TypeScript ~5.5, Node 22.
- O4 Angular Material + CDK; single prebuilt theme; no "3270 look".
- O5 English-only, `i18n` attributes without extraction pipeline.
- O6 WCAG 2.1 AA baseline.
- O7 Services + RxJS/Signals, no NgRx.
- O8 `HttpClient` with `withCredentials`, hand-written DTO interfaces, `proxy.conf.json` to `:8080`.
- O9 Jasmine + Karma (ChromeHeadless); no e2e framework in Phase 0.

**DATA / BOUNDARY**
- D1 H2 kept only as a `test` profile (PostgreSQL compatibility mode) so `mvn verify` runs without Docker; Testcontainers-PostgreSQL deferred to wave 1.
- D2 `ddl-auto=validate` everywhere except tests; Flyway file naming `V<n>__<desc>.sql`.
- D3 Explicit B-tree index for the `CXACAIX` alternate index (`card_xrefs(xref_acct_id)`).
- D4 Concrete PostgreSQL DDL types (`BIGINT`, `NUMERIC(19,2)`, `DATE`, `VARCHAR(n)`, `INTEGER`).
- D5 Initial load via a one-shot `import` profile reusing `CobolFieldReader`, not a startup seeder; test data via `R__seed_test_data.sql`.
- D6 No stored procedures/triggers for any family.
- D7 Outbound seam policy (RestClient/JmsTemplate + timeouts + Spring Retry) recorded for future use only.
- D8 Coexistence: per-stream cutover, VSAM stays source of truth until sign-off, re-import instead of dual-write.

**Topology flags to acknowledge:** F1 (`functional/CARDDEMO` vs `functional/CardDemo`), F2 (prior `.NET backend/` not reusable), F3 (no `.github/` on `main`).

## 12. Open questions
- Q1 Should Account View's frontend route ids follow the CICS transaction id (`/cavw`) or the business name (`/accounts/view`)? (Not decided by any source; needed by the stream plan, not by this document.)
- Q2 Is a PostgreSQL 16 instance available to CI (service container) or must CI stay on the H2 test profile (D1) for the whole engagement?
