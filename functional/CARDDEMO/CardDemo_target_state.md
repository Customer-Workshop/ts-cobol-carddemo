# CardDemo Target State (CORE + per-surface profiles)

Status: **DRAFT — awaiting STOP A confirmation.** No target stack has been decided for this
engagement. Every field below is **PROPOSED** unless marked FACT (cited from source). The
engagement has **no reference repository, no stack document, and no skills** for any surface
(confirmed by the engagement kickoff: "no target stack has been decided yet"), so each profile
enumerates candidate options with a recommended default. Nothing downstream may proceed until
the user selects a stack per surface at STOP A.

Sources inspected:
- FACT: CardDemo estate at `Cognition-Partner-Workshops/ts-cobol-carddemo` (`app/cbl` — 31 COBOL
  programs; `app/bms` — 17 BMS maps; `app/jcl` — 38 JCL members; `app/scheduler/CardDemo.controlm`,
  `app/scheduler/CardDemo.ca7`; `app/csd/CARDDEMO.CSD`; optional extensions under
  `app/app-authorization-ims-db2-mq`, `app/app-transaction-type-db2`, `app/app-vsam-mq`).
- FACT: source is ASCII-encoded fixed-format COBOL (verified: `app/cbl/COSGN00C.cbl` reads clean as
  ASCII); `app/data` holds both `ASCII/` and `EBCDIC/` dataset copies.
- No customer reference repo / architecture document / skill exists for ONLINE, BATCH,
  SUBTRANSACTION, or DATA/BOUNDARY. All profile fields are therefore PROPOSED.

## Surfaces in scope

| Surface | In scope? | Evidence |
|---|---|---|
| ONLINE | Yes | CICS transactions CC00 (sign-on), CM00 (menu), admin menu CA00 etc. defined in `app/csd/CARDDEMO.CSD`; 17 BMS maps in `app/bms/` |
| BATCH | Yes | 38 JCL members in `app/jcl/`; Control-M/CA-7 schedules in `app/scheduler/` (POSTTRAN, INTCALC, statement chain, export/import) |
| SUBTRANSACTION | Yes (small) | called utility programs, e.g. `CSUTLDTC.cbl` (date validation, CALLed by online + batch); XCTL-based screen-flow hand-offs |
| DATA / BOUNDARY | Yes | VSAM KSDS w/ AIX (core), optional DB2 (`app/app-transaction-type-db2/ddl`), IMS DB + MQ (`app/app-authorization-ims-db2-mq`), EBCDIC/ASCII PS datasets, GDGs |

## CORE profile (applies to everything) — all PROPOSED

| Field | Options (pick at STOP A) | Recommended default |
|---|---|---|
| Language + runtime | **A. Java 21 LTS** · B. C# / .NET 8 LTS | A — Java 21 (toolchain already provisioned in the dev environment) |
| Framework | A1. Spring Boot 3.x · B1. ASP.NET Core 8 | A1 — Spring Boot 3.x |
| Build tool | Maven (if Java) · dotnet SDK (if .NET) | Maven |
| Layering | Controller/Service/Repository with per-domain packages (`com.carddemo.<domain>`) or .NET equivalent | 3-layer, package-per-domain |
| DTO / mapping style | Java records + MapStruct-free manual mappers; explicit COBOL-field-to-DTO dictionaries per program FR | manual mappers, records |
| COBOL type mapping | PIC 9 COMP-3 → `BigDecimal`/`decimal`; PIC X(n) → trimmed `String` with max-length validation; dates (COBDATFT semantics) → `LocalDate`; signed zoned-decimal per EBCDIC overpunch rules | as stated |
| Error handling | Exceptions + problem-details (RFC 7807) at API edge; COBOL return-code protocols mapped to typed results at seams | as stated |
| Logging / observability | SLF4J/Logback (Java) or Microsoft.Extensions.Logging (.NET); structured JSON logs; job/txn correlation id | as stated |
| Test framework | JUnit 5 + AssertJ + Testcontainers (Java) · xUnit + FluentAssertions ((.NET) | JUnit 5 |
| CI gates | build + unit tests + integration tests green; coverage on migrated business logic; no stubbed FR behavior | as stated |
| Forbidden | code generation from COBOL AST without FR review; `Object`/`dynamic` typed maps for records; silent truncation of PIC fields; hardcoded EBCDIC literals | as stated |

## ONLINE profile — all PROPOSED

| Field | Options | Recommended default |
|---|---|---|
| API style | REST + OpenAPI 3, URI-versioned (`/api/v1`) | REST + OpenAPI |
| Session / conversational state | Stateless JWT auth replacing RACF sign-on (CC00); CICS COMMAREA pseudo-conversational state → server-side per-flow state objects or client-held view state | JWT + explicit flow-state DTOs |
| Validation & errors | Bean Validation (Java) mirroring BMS field edits; screen error messages preserved verbatim in FRs | as stated |
| UI | A. Angular 17+ · B. React 18+ · C. API-only (defer UI) | ask — no evidence either way |
| Component library / theming / i18n / a11y | decide with UI choice; English labels (source labels are English — FACT, e.g. `app/bms/COSGN00.bms`) | with UI choice |
| Frontend tests | framework-native (Jest/Karma/Vitest) + Playwright e2e | with UI choice |

## BATCH profile — all PROPOSED (thinnest surface; forced decisions below)

| Field | Options | Recommended default |
|---|---|---|
| Batch runtime | A. Spring Batch (chunked steps) · B. plain Spring Boot CLI jobs · C. .NET worker services | A — Spring Batch, mirrors JCL step model |
| Job-launch seam | CLI runner per job (`java -jar batch.jar --job=POSTTRAN`) so any scheduler can drive it | as stated |
| Parameters / control cards | `app/ctl` control cards → typed job parameters; PARM= → CLI args | as stated |
| Restart / checkpoint | Spring Batch job repository restart-from-failed-step; chunk commit interval per file volume | as stated — must be confirmed |
| Scheduler integration | Keep Control-M semantics: INCOND/OUTCOND → exit codes + completion events; SMART-folder cyclic logic documented per job in FRs (FACT: `app/scheduler/CardDemo.controlm`) | as stated |
| Dataset mapping | VSAM KSDS/AIX → RDBMS tables + secondary indexes; PS/GDG files → object storage or filesystem with generation suffixes; EBCDIC → decode at ingestion only | as stated — must be confirmed |
| Reports / output | TXT2PDF & report writers → templated text/PDF generation preserving layouts | as stated |
| Job observability | per-step metrics, job exit codes matching legacy condition-code contract | as stated |

## SUBTRANSACTION profile — all PROPOSED

| Field | Options | Recommended default |
|---|---|---|
| Exposure | shared internal module (library/service class) within the backend, not a network hop; e.g. `CSUTLDTC` → `DateValidationService` | internal module |
| Parameter/status mapping | COBOL linkage + return codes → typed request/response objects, no magic codes | as stated |
| Idempotency / transactions | participate in caller's transaction (same JVM/process) | as stated |
| XCTL/LINK screen hand-offs | XCTL flows become navigation/routing in the online layer; LINK utility calls become service calls | as stated |

## DATA / BOUNDARY profile — all PROPOSED

| Field | Options | Recommended default |
|---|---|---|
| Data target | A. PostgreSQL · B. AWS Aurora PostgreSQL · C. SQL Server (if .NET) · dev-time H2/containers | ask — A recommended |
| Persistence style | Spring Data JPA repositories (or EF Core); no stored procedures unless a family is mandated | JPA, no SPs |
| Transactions / unit of work | `@Transactional` service-level boundaries mirroring CICS syncpoints / batch commit intervals | as stated |
| Migration tooling | Flyway (or Liquibase) versioned migrations derived from copybooks + DDL (`app/app-transaction-type-db2/ddl`, DCLGEN in `dcl/`) | Flyway |
| Stored-procedure conventions | N/A unless customer mandates; register would-be SP families as boundaries | N/A |
| Outbound integration seam | MQ (authorization extension) → messaging abstraction (JMS/ActiveMQ/SQS) with timeout+retry+DLQ; decision deferred until that module's stream is picked | defer, register boundary |
| Inbound exposure | REST controllers only; no direct DB exposure | as stated |
| Coexistence / strangler routing | per-stream cutover; legacy datasets remain source of truth until a stream signs off; export/import (CBEXPORT/CBIMPORT) used as data-bridge during coexistence | as stated — must be confirmed |

## Cross-profile reconciliation
No conflicts yet possible (no per-surface references exist); all shared conventions are pushed
into CORE. Any surface-specific deviation must be added here with a reason when discovered.

## Drift rules (PR-rejection list, once stack confirmed)
- Wrong stack/framework/version vs the confirmed profile for the wave's surface.
- Numeric COBOL fields mapped to float/double instead of decimal types.
- Business logic stubbed, TODO'd, or copied as pseudo-COBOL comments instead of implemented.
- Missing FR coverage: any FR requirement without a test.
- New DB objects not introduced via the confirmed migration tooling.
- Batch jobs without restart semantics or without legacy-compatible exit codes.
- Online endpoints diverging from the confirmed API style/versioning.

## Open questions for STOP A
1. Language/runtime per surface: Java/Spring Boot vs C#/.NET (one stack for all surfaces, or split?).
2. UI: Angular vs React vs API-only for the ONLINE surface.
3. Data target: PostgreSQL vs Aurora vs SQL Server.
4. Repository topology: single repo vs separate BACKEND/FRONTEND repos (see `.migration/00_context.md`).
5. Batch restart model + dataset mapping policy confirmation.
