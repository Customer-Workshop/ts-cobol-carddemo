# CardDemo Target State (`!mf_ingest_target_state`) — Java engagement

Status: **PENDING STOP A CONFIRMATION (2026-09-15)**. Fields marked **FACT** are cited from an
authoritative source (owner instruction 2026-09-15, the adopted code baseline, or repo
evidence); **PROPOSED** fields are orchestrator defaults awaiting owner confirmation at
STOP A — replying "approve" accepts every PROPOSED field as written.

Prior-engagement note: a .NET 8 + Angular target state was confirmed 2026-08-20 for a
different run (see `.migration/00_context.md`). This document replaces it for the Java
engagement; it shares only the source-side facts.

## Sources per surface

| Surface | Source | Strength |
|---|---|---|
| all | Owner instruction 2026-09-15 (stack, topology, autonomy, boundary policy) | directive — FACT |
| all | Adopted code baseline `devin/1787158883-cobol-to-spring-boot` (PR #83): `spring-boot/pom.xml`, package layout, `application.properties`, controllers, job configs — `mvn clean verify` green 2026-09-15 | reference repo — FACT |
| ONLINE | Baseline REST controllers + owner "simple web UI" instruction | reference + PROPOSED UI layer |
| BATCH | Baseline `com.carddemo.batch.*` job configurations + `BatchAdminController` | reference repo — FACT |
| DATA/BOUNDARY | Baseline JPA entities/repositories/DataSeeder + decided boundary register | reference + PROPOSED deltas |
| SUBTRANSACTION | No reference exists → CORE + nearest profile | PROPOSED throughout |

---

## CORE — applies to everything

| Field | Value | FACT/PROPOSED |
|---|---|---|
| Language + version | Java 21 (`<java.version>21</java.version>` in `spring-boot/pom.xml`) | FACT (baseline + owner) |
| Build tool | Maven 3.9+, `spring-boot-starter-parent` 3.4.5 | FACT (baseline `pom.xml`) |
| Framework | Spring Boot 3.4.5: `spring-boot-starter-web`, `-data-jpa`, `-security`, `-batch`, `-validation` | FACT (baseline `pom.xml`) |
| Layering / packages | `com.carddemo` root; `api/` (controllers + request/response DTOs), `service/`, `repository/`, `model/` (JPA entities), `batch/`, `data/` (seeding, COBOL field readers), `security/` | FACT (baseline layout) |
| Naming | Classes `PascalCase`, endpoints `kebab`/REST nouns under `/api/**`; COBOL program ↔ artifact mapping recorded per stream FR (e.g. `COACTVWC` → `AccountController`/`AccountViewService`) | FACT (baseline convention) |
| DTO/mapping | Hand-written request/response records/classes in `api/` (e.g. `AccountViewResponse`, `CardListRow`); no MapStruct/Lombok | FACT (baseline) |
| COBOL type mapping | `PIC X(n)`→String (fixed width preserved, space-padded in file formats); `PIC 9(n)`/`COMP`→long/BigInteger or String for display codes (account/customer ids are JSON numbers; CVV, tran type/category codes stay strings); `COMP-3` packed decimal→`BigDecimal` (scale preserved); zoned-decimal overpunch handled by `CobolFieldReader` with `-fsign=EBCDIC` semantics; REDEFINES→explicit per-layout parsers; OCCURS→List/array | FACT (baseline `CobolFieldReader`, `DataSeeder`, models) |
| Error handling | `CobolApiException` + `GlobalExceptionHandler` → `ErrorResponse` JSON; legacy message text preserved verbatim (RESP-code fidelity per boundary register); store/infra failures map to the legacy error string, never a generic 500 | FACT (baseline) |
| Logging/observability | Spring Boot logging (SLF4J); batch job executions via Spring Batch metadata; no external APM (demo) | FACT (baseline) / PROPOSED |
| Test framework | JUnit 5 + `spring-boot-starter-test` + `spring-batch-test`; `@SpringBootTest` integration tests; naming `XxxTest`/`XxxIntegrationTest` | FACT (baseline `src/test`) |
| Test data | Seed fixture files under `src/test/resources/seed/{ASCII,EBCDIC}` + `DataSeeder` | FACT (baseline) |
| CI gates | `mvn -B clean verify` must pass; PROPOSED GitHub Actions workflow (Java 21, Postgres service for integration tests) | PROPOSED |
| Forbidden | No changes under `app/` (legacy source read-only); no secrets in code/artifacts; no Lombok/MapStruct; no external brokers (MQ→in-repo seam); no stubbed/unimplemented endpoints presented as migrated | FACT (owner + guardrails) |

## ONLINE profile — screens → web UI + REST

| Field | Value | FACT/PROPOSED |
|---|---|---|
| API style | REST JSON under `/api/**` (existing endpoints mapped 1:1 to COBOL programs — baseline table), versioning flat (`/api/`), not `/api/v1` (match baseline) | FACT (baseline README mapping) |
| Request/response | Small DTOs per endpoint; screen-state DTOs round-trip COMMAREA-equivalent state (stateless carriage, no hidden server session beyond auth) | FACT (baseline) + decided per-boundary rows |
| Session/conversation | Spring Security form login + server session carrying the COMMAREA-equivalent (S01-B6); sign-off clears session | PROPOSED (baseline has auth + session endpoint) |
| UI framework | **Thymeleaf server-rendered pages** in the same app (`templates/` + `static/`), one screen per BMS map, PF-key/AID buttons preserved as form actions | PROPOSED (owner: "simple web UI") |
| Component library / theming | Plain HTML + one CSS file, 3270-faithful layout (fixed-width fields, monochrome panel, function-key footer) — demo-grade | PROPOSED |
| i18n / a11y | English only; semantic HTML labels; not a certified-a11y target | PROPOSED |
| Validation/error surfacing | Bean Validation on request DTOs; over-width inputs rejected before legacy edits (S09-B6); legacy message text rendered verbatim in the screen's message line | FACT (baseline) |
| Frontend tests | Template + controller tests via `@WebMvcTest`/MockMvc; no JS test toolchain | PROPOSED |

## BATCH profile — JCL/Control-M → Spring Batch

| Field | Value | FACT/PROPOSED |
|---|---|---|
| Runtime | Spring Batch chunk-oriented jobs inside the same app; `spring.batch.job.enabled=false`, explicit launch only | FACT (baseline `application.properties`) |
| Job/step model | One `JobConfiguration` per JCL job (`Cbtrn02JobConfiguration` ↔ POSTTRAN, `Cbact04JobConfiguration` ↔ INTCALC, `Cbstm03JobConfiguration` ↔ CREASTMT, `Cbexport`/`Cbimport`); `ItemReader`/`ItemProcessor`/`ItemWriter` per JCL step | FACT (baseline `batch/` package) |
| Job-launch seam | `POST /api/admin/jobs/{jobName}` → `BatchJobLauncherService` (Control-M/CA-7 and TDQ `JOBS` replacement — B-001, B-008) | FACT (baseline `BatchAdminController`) |
| Params/control cards | JobParameters (String/typed) replacing JCL PARM and SYSIN cards; defaults match committed JCL | FACT+PROPOSED |
| Restart/checkpoint | Spring Batch restart semantics + JobRepository metadata; rerun = new execution, idempotent writers | PROPOSED (baseline has JobRepository) |
| Scheduler conditions | INCOND/OUTCOND dependency chains → documented job-order config in `BatchJobService`; exit-code contract preserved (COBOL return code → step exit status → process/exit code) — B-004, B-008 | PROPOSED (aligned to baseline launcher) |
| Dataset mapping | VSAM→Postgres (B-009); PS/GDG flat files→Spring `Resource` files under a job output dir, filenames carry the generation role (B-010); report/statement files written byte-faithful (fixed-width, overpunch where source produces it) | FACT+PROPOSED |
| Reports/output | Report jobs produce the same fixed-width content the COBOL writes; online report request returns 202 + job id (B-001) | FACT (baseline `ReportController` 202) |
| Logging | Job-level SLF4J + Spring Batch execution metadata; per-step counts logged | PROPOSED |

## SUBTRANSACTION profile — called flows

| Field | Value | FACT/PROPOSED |
|---|---|---|
| Contract exposure | Internal Spring services with an explicit in/out parameter record mirroring the linkage copybook (e.g. CSUTLDTC → `DateValidationService` taking the CALL USING fields) | PROPOSED (no reference exists; nearest = CORE + ONLINE) |
| Parameter/status mapping | Linkage-area result codes → returned result object (never a boolean); exact 80-byte result text where the copybook defines it (S09-B4) | PROPOSED |
| Idempotency | Called services pure/stateless unless the source performs a write; writes participate in the caller's transaction | PROPOSED |
| Transaction participation | `@Transactional(propagation=REQUIRED)` joining the caller's UoW | PROPOSED |

## DATA / BOUNDARY profile — all streams

| Field | Value | FACT/PROPOSED |
|---|---|---|
| Persistence | Spring Data JPA repositories, one entity per VSAM file/IMS segment/DB2 table (B-005, B-006, B-009); natural keys preserved as IDs | FACT (baseline `model/`,`repository/`) |
| Data target | **PostgreSQL 16** (owner); local via `docker compose up -d db`; H2 retained ONLY for fast unit tests; integration parity vs real Postgres via Testcontainers | FACT (owner) + PROPOSED mechanics |
| Migrations | Flyway (`db/migration`) as the schema source of truth; `spring.jpa.hibernate.ddl-auto=validate` | PROPOSED (baseline uses H2 autoddl today) |
| Unit-of-work | `@Transactional` service boundaries matching each CICS UoW/VSAM rewrite group; READ UPDATE/REWRITE → `SELECT … FOR UPDATE` with compare-before-write parity | FACT (decided per-boundary rows) |
| Stored procedures | None — all data access is JPA (no SP family exists in CardDemo; DBA registration N/A) | FACT |
| Outbound integration | MQ → in-process `BlockingQueue`-backed adapter preserving request/reply semantics (B-007); no external broker in demo | FACT (owner) |
| Inbound exposure | REST + server-rendered UI only; batch launch via admin endpoint | FACT |
| Coexistence/strangler | Feature-flagged route registry (S01-B1): menu options registered but disabled until their stream merges | FACT (decided) |
| Encoding/data | EBCDIC USRSEC decoded via `IBM037`; ASCII datasets carry zoned-decimal overpunch (`CobolFieldReader`); fixed-width byte fidelity in file writers | FACT (baseline) |

## Cross-profile reconciliation

- Type mapping, error model, persistence live in CORE/DATA — profiles do not restate them.
- ONLINE and BATCH share the same repositories and entities (single `model/`); deliberate —
  the legacy programs share VSAM files.
- UI is server-rendered deliberately: the owner asked for "a simple web UI"; a second
  toolchain (SPA) was rejected as unjustified weight (06_decisions 2026-09-15).

## Drift rules (what gets a wave PR rejected)

1. Any edit under `app/` (legacy source is read-only).
2. An endpoint/job that returns stubbed, hard-coded, or partial data presented as migrated.
3. Legacy message text, field widths, or RESP-code surfaces paraphrased or "improved" (parity
   is byte/message-faithful; deviations need a logged decision).
4. New persistence outside `carddemo` write targets; schema changes without a Flyway
   migration.
5. An external dependency for MQ/IMS/DB2 (in-repo equivalents only) — deferral needs a logged
   decision, not silent scope-drop.
6. Packages outside `com.carddemo` or layers skipping `service`/`repository` (no controller →
   entity shortcuts).
7. Tests that pin UI text different from the legacy message text.

## Open questions for STOP A (all pre-answered; "approve" accepts)

1. UI = Thymeleaf server-rendered in-app (PROPOSED) — alternative React SPA available if preferred.
2. Postgres mechanics: docker-compose local + Testcontainers tests + Flyway migrations (PROPOSED).
3. Extension streams S-19..S-22 in scope with in-repo equivalents; S-13 descoped (source absent).
4. Baseline adoption: PR #83 code as the starting implementation, verified per stream.
