# 07_runbook — how to build/run locally (every command executed in the setup session)

## Compile a COBOL program (verified 2026-08-20, GnuCOBOL 3.1.2)
```bash
cd ~/repos/ts-cobol-carddemo
mkdir -p /tmp/cobtest
cobc -I app/cpy -fsign=EBCDIC -x -o /tmp/cobtest/CBACT01C app/cbl/CBACT01C.cbl   # -> COMPILE_OK
```
If a copybook fails on tab indentation, expand tabs first:
```bash
mkdir -p /tmp/cpyfix && for f in app/cpy/*; do expand -t 4 "$f" > /tmp/cpyfix/$(basename "$f"); done
cobc -I /tmp/cpyfix -fsign=EBCDIC -x -o /tmp/cobtest/CBACT01C app/cbl/CBACT01C.cbl   # verified OK
```
Note `-fsign=EBCDIC`: ASCII datasets in `app/data/ASCII` use zoned-decimal overpunch signs.
Online (EXEC CICS) programs cannot fully compile without a CICS translator — compile-check only,
this is a known baseline limitation (B-S03-04).

## Toolchain facts (verified 2026-09-15)
- `cobc` = GnuCOBOL 3.1.2 at `/usr/bin/cobc`
- `java` = OpenJDK 21.0.12; `/usr/bin/java` is Java 8 — always use
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` for Maven work
- `mvn` = Maven 3.9.x
- CICS/DB2/IMS/MQ are NOT runnable locally; online programs compile-check only.

## Backend build & test (verified 2026-09-15, `mvn clean verify` green)
```bash
cd ~/repos/ts-cobol-carddemo/spring-boot
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -q clean verify
```

## Run the app (default profile: H2 in-memory, seeds from app/data)
```bash
cd ~/repos/ts-cobol-carddemo/spring-boot
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -q spring-boot:run
# sign-on users: ADMIN001..005 / USER0001..005 (plaintext-compatible fixture passwords)
```

## PostgreSQL profile (Phase 1 — to be added)
- `docker compose up -d db` will run local Postgres 16; `spring-boot:run -Dspring-boot.run.profiles=postgres`
- Flyway migrations under `spring-boot/src/main/resources/db/migration`
- Integration tests vs real Postgres: Testcontainers (auto-start) — no docker needed in CI beyond the service container
- Write target allowed by the migration guard: catalog `carddemo` only (`.migration/allowed_targets.json`)

## Batch jobs
- `spring.batch.job.enabled=false`; jobs launch explicitly via `POST /api/admin/jobs/{jobName}`
  (BatchAdminController) — the Control-M/CA-7 replacement seam (B-008).

## CI
- GitHub Actions workflow owed in Phase 0: `mvn -B verify` with Java 21 (+ Postgres service
  for integration tests).
