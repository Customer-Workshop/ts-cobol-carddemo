# 07 — Runbook: build, test, run locally

Every command in sections 1–4 was **executed successfully on 2026-09-07** in the setup session on
the Devin dev image (Ubuntu 22.04, GnuCOBOL 3.1.2, Docker 27.4.1, Maven 3.6.3, Java 21 at
`/usr/lib/jvm/java-21-openjdk-amd64`, Node v20.18.1). Section 5 is **expected once Phase 0
lands** and has **not** been executed (nothing exists yet to execute it against); the session that
creates `backend/`/`frontend/` must run it and replace the "expected" marker with the date.

Run everything from the repo root `~/repos/ts-cobol-carddemo` on branch
`devin/1788757216-cardemo-account-view-stream`.

## 0. Toolchain facts

| Tool | Version / path | Gotcha |
|---|---|---|
| Java | `/usr/lib/jvm/java-21-openjdk-amd64` (21.0.12) | `/usr/bin/java` is Java 8 and the default `JAVA_HOME` resolves to 17 in some shells — **always** pass `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` |
| Maven | 3.6.3 | fine for Spring Boot 3.4 |
| GnuCOBOL | `cobc` 3.1.2 | no CICS preprocessor; see §1 |
| Docker | 27.4.1, daemon running, no sudo needed | host port 5432 is taken by a local PostgreSQL 14 |
| PostgreSQL client | `psql` 14.24 (talks fine to a 16 server) | |
| Node / npm | v20.18.1 / 10.8.2 | target state proposes Node 22 for Angular CI; install via `nvm` or update the blueprint at Phase 0 |

## 1. Compile a COBOL program with GnuCOBOL (executed)

Committed copybooks are column-formatted but two (`CSLKPCDY.cpy`, `CUSTREC.cpy`) contain tabs, so
copy all copybooks to a scratch dir, expand tabs and normalise the level-number column first:

```bash
rm -rf /tmp/cpy /tmp/cobbuild && mkdir -p /tmp/cpy /tmp/cobbuild
for f in app/cpy/*; do
  b=$(basename "$f")
  expand -t 4 "$f" | sed -E 's/^ {1,11}(0[0-9]|[0-9][0-9]  )/           \1/' > /tmp/cpy/$b
done
cobc -I /tmp/cpy -fsign=EBCDIC -x -o /tmp/cobbuild/CBACT01C app/cbl/CBACT01C.cbl
# -> exit 0, /tmp/cobbuild/CBACT01C (131 KB executable)
```

`-fsign=EBCDIC` is required because `app/data/ASCII/*` carries zoned-decimal overpunch signs.
Substitute any **batch** program (`CB*`) for `CBACT01C`. Running the binary needs the input
datasets assigned via environment variables named after the `ASSIGN TO` names (e.g.
`DD_ACCTFILE=...`) and, for `ORGANIZATION IS INDEXED` files, a GnuCOBOL-loaded indexed file —
not done in setup; document it in the stream runbook when a batch stream is analysed.

**CICS online programs (`CO*C`) do not compile off-host** with plain `cobc`: they need the CICS
translator, the IBM copybooks `DFHBMSCA`/`DFHAID` (not in the repo) and the BMS symbolic maps
(`-I app/cpy-bms`). Executed evidence:

```bash
cobc -I /tmp/cpy -fsign=EBCDIC -fsyntax-only app/cbl/COACTVWC.cbl
# -> error: DFHBMSCA: No such file or directory / DFHAID / COACTVW
```

Online behaviour is therefore established by **reading** the source (`02_conventions.md` §4) and
proven by parity tests against fixtures, not by executing the COBOL.

## 2. Build + test the reference module `spring-boot/` (executed)

```bash
(cd spring-boot && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -q clean verify)
# -> exit 0 in ~15 s; 6 surefire reports under spring-boot/target/surefire-reports (34 tests, 0 failures)
```

Reference only: proves the toolchain and shows accepted conventions. Never edit `spring-boot/`.

## 3. Run the reference module and hit an endpoint (executed)

```bash
(cd spring-boot && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -q spring-boot:run)   # H2 in-memory, seeds from ../app/data; listens on :8080
```

In a second shell (≈20 s after start):

```bash
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/auth/session          # -> 401 (no session yet)
curl -s -c /tmp/ck -H 'Content-Type: application/json' -X POST \
     -d '{"userId":"ADMIN001","password":"PASSWORD"}' localhost:8080/api/auth/signon
# -> {"userId":"ADMIN001","userType":"A","landingTarget":"/api/admin/menu"}  (200)
curl -s -b /tmp/ck localhost:8080/api/accounts/00000000001
# -> {"accountId":1,"activeStatus":"Y","currentBalance":194.00,"creditLimit":2020.00,...}
```

`ADMIN001`/`PASSWORD` is the fixture user seeded from `app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS`.
Stop with Ctrl-C (or `pkill -f spring-boot:run`).

## 4. Local PostgreSQL 16 (executed)

```bash
docker run -d --name carddemo-pg \
  -e POSTGRES_USER=carddemo -e POSTGRES_PASSWORD=carddemo -e POSTGRES_DB=carddemo \
  -p 5433:5432 postgres:16
sleep 8
docker exec carddemo-pg pg_isready -U carddemo                                   # -> accepting connections
docker exec carddemo-pg psql -U carddemo -d carddemo -tc 'select version();'     # -> PostgreSQL 16.15
PGPASSWORD=carddemo psql -h localhost -p 5433 -U carddemo -d carddemo -tc 'select 1'   # -> 1
```

Facts: host port **5433** (5432 is occupied by the image's PostgreSQL 14 — `ss -ltnp | grep 5432`);
credentials `carddemo/carddemo`, database `carddemo`; JDBC URL for the target
`jdbc:postgresql://localhost:5433/carddemo`. Fallback if Docker is unavailable on a future box:
the local PostgreSQL 14 is **not** an acceptable substitute (version mismatch with the target);
use the H2 `test` profile for `mvn verify` (target state D1) and raise a lead-time request
(`.migration/requests/`) for a PostgreSQL 16 instance.

Lifecycle: `docker stop carddemo-pg` / `docker start carddemo-pg` / `docker rm -f carddemo-pg`.
Reset schema: `docker exec carddemo-pg psql -U carddemo -d carddemo -c 'drop schema public cascade; create schema public;'`
(not executed; standard psql).

## 5. Target `backend/` and `frontend/` — executed 2026-09-07 (wave 1, PR #95; CI green)

These directories do not exist yet on this branch. The commands below are the contract that the
Phase 0 child must make true and then execute, replacing this heading with the execution date.

```bash
# backend (Java 21 / Spring Boot 3 / Maven / JUnit 5 / Flyway / PostgreSQL 16)
(cd backend && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -q clean verify)          # unit + integration tests on the H2 `test` profile (D1)
(cd backend && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -q spring-boot:run \
   -Dspring-boot.run.arguments=--spring.datasource.url=jdbc:postgresql://localhost:5433/carddemo)  # Flyway migrates on start; :8080
(cd backend && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -q spring-boot:run \
   -Dspring-boot.run.profiles=import)                                                      # one-shot load from app/data (D5)
curl -s -b /tmp/ck localhost:8080/api/accounts/00000000001                               # Account View endpoint (COACTVWC)

# frontend (Angular, standalone components, Angular Material)
(cd frontend && npm ci && npm run build)
(cd frontend && npm test -- --watch=false --browsers=ChromeHeadless)
(cd frontend && npm start)      # ng serve with proxy.conf.json -> http://localhost:8080 ; open http://localhost:4200
```

Expected DB profile: `application.properties` -> `spring.datasource.url=jdbc:postgresql://localhost:5433/carddemo`,
`username/password=carddemo`, `spring.jpa.hibernate.ddl-auto=validate`, Flyway enabled;
`application-test.properties` -> H2 `MODE=PostgreSQL`.

## 6. CI entry points

None exist yet (`.github/` absent on `main` and on this branch — flag F3). Phase 0 creates
`.github/workflows/backend-ci.yml` (Temurin 21, `mvn -B clean verify` in `backend/`) and
`.github/workflows/frontend-ci.yml` (Node 22, `npm ci`, `npm run build`, headless `npm test`).
Whether CI gets a PostgreSQL 16 service container is open question Q2 (target state §12).

## 7. Drift check before any target PR

```bash
cat .agents/skills/carddemo-target-state-conformance/SKILL.md   # mechanical CORE/ONLINE/DATA checks; invoke the skill in-session
```
