# 01 — Target state (link + summary)

**Authoritative artifact:** [`functional/CardDemo/CardDemo_target_state.md`](../functional/CardDemo/CardDemo_target_state.md)
on branch `devin/1788757216-cardemo-account-view-stream` (produced by `!mf_ingest_target_state`,
commit `48cfe02`). This file is a convenience summary; do not edit the profiles here — edit the
artifact and bump this summary.

**Status:** DRAFT — awaiting customer confirmation at STOP A (see `06_decisions.md` D-0002).
The PROPOSED fields (C1–C6, O1–O9, D1–D8) and topology flags F1–F3 listed in section 11 of the
artifact are what STOP A confirms or corrects.

## Profiles in scope

| Profile | Status | One-line summary |
|---|---|---|
| CORE | in scope | Java 21, Spring Boot 3 (3.4.x proposed), Maven single module `backend/pom.xml`, root package `com.carddemo`, layers `api/service/repository/model/security/data`; Java `record` DTOs with hand-written mapping; **no Lombok/MapStruct**; constructor injection; `BigDecimal` for money, `LocalDate` for `PIC X(10)` dates, `Long` for `PIC 9(n)` ids; `CobolApiException` + `GlobalExceptionHandler` -> `ErrorResponse`; legacy messages verbatim in `CobolMessages`; JUnit 5 + Mockito + MockMvc; `mvn -q clean verify`; GitHub Actions CI |
| ONLINE | in scope | JSON REST, one resource per COBOL program (`GET /api/accounts/{acctId}` -> `COACTVWC`), unversioned `/api`; validation in the service preserving COBOL order and message text; server-side HTTP session via Spring Security (roles ADMIN/USER from USRSEC); Angular standalone components (18 LTS proposed) + Angular Material, `HttpClient` with `withCredentials`, Jasmine/Karma, dev proxy to `:8080` |
| DATA / BOUNDARY | in scope | PostgreSQL 16 + Flyway `V<n>__<desc>.sql`, `ddl-auto=validate`; Spring Data JPA; snake_case plural tables, one per VSAM base cluster, KSDS key = PK, AIX -> B-tree index; `NUMERIC(19,2)` money; `@Transactional` on writing service methods only; one-shot `import` profile instead of a startup seeder; H2 only in the `test` profile; no stored procedures/triggers; per-stream cutover, VSAM stays source of truth until sign-off |
| BATCH | **N/A** | Account View has no batch chain. Re-run `!mf_ingest_target_state` with a BATCH reference before any batch stream is planned. |
| SUBTRANSACTION | **N/A** | Account View has no CALLed contract of its own. Re-run when a stream that CALLs/LINKs a utility (e.g. `CSUTLDTC`) is selected. |

## Drift rules
Section 10 of the artifact lists what gets a PR rejected per profile. Wave children must read it
in full; the summary above is not a substitute.

## Open questions carried into STOP A
- Q1 frontend route ids: trancode (`/cavw`) vs business name (`/accounts/view`).
- Q2 PostgreSQL 16 available to CI (service container) or H2 test profile only.
