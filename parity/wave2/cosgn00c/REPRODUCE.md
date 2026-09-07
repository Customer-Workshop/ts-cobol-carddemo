# Reproduce the COSGN00C parity run

All commands from the repo root on branch `devin/1788757216-w2-parity` (code under test = `01a7138210c1b9114961b8992a517c058f5f4ed2`, PR #96).
Nothing under `backend/`, `frontend/`, `app/`, `functional/`, `.migration/` is modified by this run.

```bash
# 1. Real PostgreSQL 16 (runbook .migration/07_runbook.md)
docker run -d --name carddemo-pg -e POSTGRES_USER=carddemo -e POSTGRES_PASSWORD=carddemo \
  -e POSTGRES_DB=carddemo -p 5433:5432 postgres:16
docker exec carddemo-pg pg_isready -U carddemo
docker exec carddemo-pg psql -U carddemo -d carddemo -c 'drop schema public cascade; create schema public;'

# 2. Seed = the real USRSEC extract (app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS, 10 rows) via the wave-1 import profile.
#    NOTE: the README/runbook form with --spring.main.web-application-type=none FAILS on this head
#    (see pg/import_run.log); the web-enabled form below is the workaround used for evidence.
(cd backend && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 CARDDEMO_DB_USER=carddemo CARDDEMO_DB_PASSWORD=carddemo \
  mvn -B -q spring-boot:run -Dspring-boot.run.profiles=import -Dspring-boot.run.arguments="--server.port=8099")
#    ... wait for "Import complete: accounts=50 customers=50 card_xrefs=50 users=10", then stop it (Ctrl-C).

# 3. Backend on the default (PostgreSQL) profile, then the HTTP cases
(cd backend && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 CARDDEMO_DB_USER=carddemo CARDDEMO_DB_PASSWORD=carddemo mvn -B -q spring-boot:run &)
parity/wave2/cosgn00c/run_http_cases.sh > parity/wave2/cosgn00c/http_cases.out

# 4. Frontend for the browser pass
(cd frontend && npm ci && npx ng serve --port 4200 --proxy-config proxy.conf.json &)
#    browser procedure + observed values: ui/ui_report.md, recording ui/cosgn00c_parity.webp

# 5. Full backend suite (Testcontainers PostgreSQL 16) and conformance skill lines
(cd backend && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B clean verify)     # conformance/backend_mvn_verify.log
#    static skill lines: conformance/skill_checks.txt ; frontend build+karma: conformance/frontend_build_test.log

# 6. Static-container lifecycle probe (temporary file, NOT committed to backend/src; copy kept as
#    ProbeSecondSubclassIT.java.txt). Drop it into backend/src/test/java/com/carddemo/parityprobe/ and run:
(cd backend && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B -q test \
  -Dtest='AccountViewRepositoriesIntegrationTest,ProbeSecondSubclassIT' -Dsurefire.failIfNoSpecifiedTests=false)
#    -> probe_static_container.log ; remove the probe afterwards.
```

Fixture ids used (from the imported `users` table, legacy password `PASSWORD`, no secrets):
`USER0001..USER0005` (type `U`), `ADMIN001..ADMIN005` (type `A`). The HTTP script upgrades
USER0001/0003/0004/0005/ADMIN001 to BCrypt; re-import (step 2) to reset.
