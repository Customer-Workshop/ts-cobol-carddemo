# CardDemo backend (target state)

Java 21 / Spring Boot 3.4 / Maven / PostgreSQL 16 / Flyway. Target-state contract:
`functional/CardDemo/CardDemo_target_state.md` (CORE + ONLINE + DATA/BOUNDARY). Stream S-01
AccountView plan: `functional/CardDemo/AccountView_migration_plan.md`.

## Build

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B clean verify
```

Unit tests run on H2 (`MODE=PostgreSQL`, profile `test`, unit-only). Repository integration tests run on
PostgreSQL 16 through Testcontainers (Docker required; the container picks its own port).

## Run

Default profile expects PostgreSQL at `jdbc:postgresql://localhost:5433/carddemo`
(`CARDDEMO_DB_USER` / `CARDDEMO_DB_PASSWORD`), `ddl-auto=validate`, Flyway on, no data seeding.

One-shot legacy import (never runs by default):

```bash
CARDDEMO_DB_PASSWORD=... mvn spring-boot:run -Dspring-boot.run.profiles=import \
  -Dspring-boot.run.arguments="--spring.main.web-application-type=none"
```

It loads `app/data/ASCII/{acctdata,custdata,cardxref}.txt` and the IBM037 `app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS`
into `accounts`, `customers`, `card_xrefs`, `users`, and enforces the Q-10 date gate (every date column must be
`YYYY-MM-DD`; any offender aborts the import and is logged).

## Program -> endpoint map (ONLINE drift rule 1)

| Legacy program | Trancode / map | Target endpoint | Status |
|---|---|---|---|
| `CSUTLDTC` | none (CALLed utility) | none — internal `DateValidationService` (B-0014) | wave 1: ported |
| `COSGN00C` | `CC00` / `COSGN00` | `POST /api/auth/signon`, `POST /api/auth/signoff` | wave 2 (not yet) |
| `COMEN01C` | `CM00` / `COMEN01` | `GET /api/menu/options` | wave 2 (not yet) |
| `COACTVWC` | `CAVW` / `COACTVW` | `GET /api/accounts/{acctId}` | wave 3 (not yet) |

Wave 1 delivers no HTTP endpoints: only the data seams (Flyway `V1__account_view_schema.sql`, entities,
repositories), the session-context model (`SessionContext`), the error model (`GlobalExceptionHandler`,
`CobolApiException`, `ErrorResponse`, `CobolMessages`), the `import` profile and the CSUTLDTC port.

## Layout

```
com.carddemo.api         error model, (later) controllers + DTO records
com.carddemo.service     business logic (DateValidationService = CSUTLDTC)
com.carddemo.repository  Spring Data repositories (one per VSAM file)
com.carddemo.model       JPA entities mirroring the copybooks
com.carddemo.security    SecurityConfig (session cookie, JSON 401/403), SessionContext (COMMAREA replacement)
com.carddemo.data        CobolFieldReader, LegacyExtractParser, DateColumnGate, ImportRunner (@Profile("import"))
```
