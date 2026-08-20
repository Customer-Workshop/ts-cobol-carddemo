# CardDemo Target State (CORE + per-surface profiles)

Status: **CONFIRMED at STOP A (2026-08-20)** by dhrov.subramanian:
**C# / .NET 8 + ASP.NET Core** (all surfaces), **Angular 17+** UI, **PostgreSQL** data target,
**single-repo topology** (backend/frontend as subdirectories of `ts-cobol-carddemo`).
Fields below are marked **CONFIRMED** (user-selected at STOP A), **FACT** (cited from source), or
**PROPOSED-accepted** (stated defaults confirmed implicitly at STOP A; any objection reopens them).

Sources inspected:
- FACT: CardDemo estate at `Cognition-Partner-Workshops/ts-cobol-carddemo` (`app/cbl` — 31 COBOL
  programs; `app/bms` — 17 BMS maps; `app/jcl` — 38 JCL members; `app/scheduler/CardDemo.controlm`,
  `app/scheduler/CardDemo.ca7`; `app/csd/CARDDEMO.CSD`; optional extensions under
  `app/app-authorization-ims-db2-mq`, `app/app-transaction-type-db2`, `app/app-vsam-mq`).
- FACT: source is ASCII-encoded fixed-format COBOL (verified: `app/cbl/COSGN00C.cbl` reads clean as
  ASCII); `app/data` holds both `ASCII/` and `EBCDIC/` dataset copies.
- No customer reference repo / architecture document / skill exists for any surface; the stack was
  selected by the user at STOP A from enumerated options.

## Surfaces in scope

| Surface | In scope? | Evidence |
|---|---|---|
| ONLINE | Yes | CICS transactions CC00 (sign-on), CM00 (menu), admin menu etc. defined in `app/csd/CARDDEMO.CSD`; 17 BMS maps in `app/bms/` |
| BATCH | Yes | 38 JCL members in `app/jcl/`; Control-M/CA-7 schedules in `app/scheduler/` (POSTTRAN, INTCALC, statement chain, export/import) |
| SUBTRANSACTION | Yes (small) | called utility programs, e.g. `CSUTLDTC.cbl` (date validation, CALLed by online + batch); XCTL-based screen-flow hand-offs |
| DATA / BOUNDARY | Yes | VSAM KSDS w/ AIX (core), optional DB2 (`app/app-transaction-type-db2/ddl`), IMS DB + MQ (`app/app-authorization-ims-db2-mq`), EBCDIC/ASCII PS datasets, GDGs |

## CORE profile (applies to everything)

| Field | Value | Status |
|---|---|---|
| Language + runtime | C# 12 on .NET 8 LTS | CONFIRMED |
| Framework | ASP.NET Core 8 | CONFIRMED |
| Build tool | dotnet SDK 8 (`dotnet build/test/publish`); solution `backend/CardDemo.sln` | CONFIRMED (layout PROPOSED-accepted) |
| Layering | Controller / Service / Repository; one project per concern (`CardDemo.Api`, `CardDemo.Application`, `CardDemo.Domain`, `CardDemo.Infrastructure`, `CardDemo.Batch`), namespaces `CardDemo.<Layer>.<Domain>` | PROPOSED-accepted |
| DTO / mapping style | C# records for DTOs; explicit manual mappers (no AutoMapper); COBOL-field-to-DTO dictionary per program FR | PROPOSED-accepted |
| COBOL type mapping | PIC 9/COMP-3 → `decimal` (never float/double); PIC X(n) → trimmed `string` with max-length validation; dates (COBDATFT semantics) → `DateOnly`; signed zoned-decimal decoded per EBCDIC overpunch rules | PROPOSED-accepted |
| Error handling | Exceptions + RFC 7807 ProblemDetails at API edge; COBOL return-code protocols mapped to typed result objects at seams | PROPOSED-accepted |
| Logging / observability | Microsoft.Extensions.Logging + Serilog structured JSON; correlation id per transaction/job | PROPOSED-accepted |
| Test framework | xUnit + FluentAssertions; Testcontainers-dotnet for PostgreSQL integration tests | PROPOSED-accepted |
| CI gates | `dotnet build` + unit tests + integration tests green; frontend build+tests green; no stubbed FR behavior | PROPOSED-accepted |
| Forbidden | AutoMapper/dynamic typed record maps; float/double for money; silent PIC truncation; code-gen from COBOL AST without FR review; hardcoded EBCDIC literals | PROPOSED-accepted |

## ONLINE profile

| Field | Value | Status |
|---|---|---|
| API style | REST + OpenAPI 3 (Swashbuckle), URI-versioned `/api/v1` | PROPOSED-accepted |
| Session / conversational state | Stateless JWT auth (ASP.NET Core Identity-less, custom user store from USRSEC data) replacing RACF sign-on (CC00); CICS COMMAREA pseudo-conversational state → explicit flow-state DTOs | PROPOSED-accepted |
| Validation & errors | DataAnnotations/FluentValidation mirroring BMS field edits; legacy screen error messages preserved verbatim per FR | PROPOSED-accepted |
| UI | Angular 17+ (standalone components) under `frontend/` | CONFIRMED |
| Component library / theming | Angular Material; English labels (FACT: source labels are English, e.g. `app/bms/COSGN00.bms`); i18n via Angular i18n; WCAG AA baseline | PROPOSED-accepted |
| Frontend tests | Jest unit tests + Playwright e2e | PROPOSED-accepted |

## BATCH profile

| Field | Value | Status |
|---|---|---|
| Batch runtime | .NET 8 console host (`CardDemo.Batch`) with a job/step model: each JCL job → a named job class, each EXEC PGM step → a step class; chunked readers/writers for file/DB processing | CONFIRMED (.NET) / PROPOSED-accepted (model) |
| Job-launch seam | CLI runner: `dotnet CardDemo.Batch.dll --job POSTTRAN [--params ...]` so any scheduler can drive it | PROPOSED-accepted |
| Parameters / control cards | `app/ctl` control cards → typed job parameter objects; PARM= → CLI args | PROPOSED-accepted |
| Restart / checkpoint | Job-execution table in PostgreSQL recording step status + checkpoint (last committed key/record count); rerun resumes from failed step; chunk commit interval per file volume | PROPOSED-accepted — long-consequence decision, revisit at STOP C per stream |
| Scheduler integration | Preserve Control-M semantics: legacy condition-code contract mapped to process exit codes; INCOND/OUTCOND documented per job in FRs (FACT: `app/scheduler/CardDemo.controlm`) | PROPOSED-accepted |
| Dataset mapping | VSAM KSDS/AIX → PostgreSQL tables + secondary indexes; PS/GDG files → filesystem/object storage with generation suffixes; EBCDIC decoded at ingestion only | PROPOSED-accepted |
| Reports / output | TXT2PDF & report writers → templated text/PDF generation preserving layouts | PROPOSED-accepted |
| Job observability | per-step metrics + structured logs; exit codes match legacy condition-code contract | PROPOSED-accepted |

## SUBTRANSACTION profile

| Field | Value | Status |
|---|---|---|
| Exposure | shared internal module (class library service) within the backend, not a network hop; e.g. `CSUTLDTC` → `DateValidationService` in `CardDemo.Domain` | PROPOSED-accepted |
| Parameter/status mapping | COBOL linkage + return codes → typed request/response objects; no magic codes | PROPOSED-accepted |
| Idempotency / transactions | participates in caller's transaction (same process/DbContext scope) | PROPOSED-accepted |
| XCTL/LINK hand-offs | XCTL screen flows → Angular routing / API navigation; LINK utility calls → service calls | PROPOSED-accepted |

## DATA / BOUNDARY profile

| Field | Value | Status |
|---|---|---|
| Data target | PostgreSQL (dev via Docker/Testcontainers) | CONFIRMED |
| Persistence style | EF Core 8 + Npgsql; repository pattern over DbContext; no stored procedures unless a family is mandated | PROPOSED-accepted |
| Transactions / unit of work | transaction scope at service level mirroring CICS syncpoints; batch commit intervals per chunk | PROPOSED-accepted |
| Migration tooling | EF Core migrations (versioned, committed) derived from copybooks + DDL (`app/app-transaction-type-db2/ddl`, DCLGEN in `dcl/`) | PROPOSED-accepted |
| Stored-procedure conventions | N/A — none mandated | N/A |
| Outbound integration seam | MQ (authorization extension) → messaging abstraction with timeout+retry+DLQ; concrete broker decided when that stream is picked; registered as boundary | DEFERRED (boundary register) |
| Inbound exposure | REST controllers only; no direct DB exposure | PROPOSED-accepted |
| Coexistence / strangler routing | per-stream cutover; legacy datasets remain source of truth until a stream signs off; CBEXPORT/CBIMPORT usable as data-bridge during coexistence | PROPOSED-accepted |

## Cross-profile reconciliation
Single confirmed stack across all surfaces; all shared conventions live in CORE. No cross-profile
conflicts. The only deliberate surface difference: BATCH runs as a console host (exit-code
contract), ONLINE as ASP.NET Core web host — both share Application/Domain/Infrastructure projects.

## Drift rules (PR-rejection list)
- Not .NET 8 / ASP.NET Core 8 / Angular 17+ / PostgreSQL per the wave's surface.
- Money/numeric COBOL fields mapped to `float`/`double` instead of `decimal`.
- Business logic stubbed, TODO'd, or copied as pseudo-COBOL comments instead of implemented.
- Missing FR coverage: any FR requirement without a test.
- New DB objects not introduced via EF Core migrations.
- Batch jobs without restart semantics or without legacy-compatible exit codes.
- Online endpoints diverging from REST `/api/v1` + OpenAPI conventions.
- AutoMapper or dynamic/untyped record maps.

## Open questions
None blocking. Deferred: concrete MQ broker choice (only when an MQ-touching stream is selected);
batch chunk sizes per job (set at wave time from data volumes).
