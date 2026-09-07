---
name: carddemo-target-state-conformance
description: Mechanical drift checks for CardDemo migration PRs (CORE, ONLINE, DATA/BOUNDARY profiles). Use before opening or reviewing any PR that touches backend/ or frontend/ in Cognition-Partner-Workshops/ts-cobol-carddemo.
---

# CardDemo target-state conformance

Authoritative document: `functional/CardDemo/CardDemo_target_state.md` (section 10, drift rules).
This skill only automates the checks that can be run mechanically; the rest is review.

## CORE (run from repo root)

```bash
# Java 21 / Spring Boot 3 / Maven
grep -q "<java.version>21</java.version>" backend/pom.xml
grep -q "<artifactId>spring-boot-starter-parent</artifactId>" backend/pom.xml
grep -A1 "spring-boot-starter-parent" backend/pom.xml | grep -q "<version>3\."
# Forbidden libraries
! grep -rqi "lombok\|mapstruct\|modelmapper" backend/pom.xml backend/src
# No float/double money, no field injection, no business logic exceptions from controllers
! grep -rnE "\b(double|float|Double|Float)\b" backend/src/main/java --include=*.java | grep -iv "test"
! grep -rn "@Autowired" backend/src/main/java
# DTOs are records
for f in backend/src/main/java/com/carddemo/api/*Request.java backend/src/main/java/com/carddemo/api/*Response.java; do grep -q "public record" "$f" || echo "NOT A RECORD: $f"; done
# Build + tests green
(cd backend && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -q clean verify)
# No edits to legacy source
git diff --name-only origin/main -- app/ | grep . && echo "LEGACY SOURCE MODIFIED" 
```

## ONLINE

```bash
# every controller mapping appears in the endpoint table
grep -rhoE '@(Get|Post|Put|Delete)Mapping\("[^"]*"' backend/src/main/java/com/carddemo/api | sort -u
grep -c "| \`" backend/README.md
# Angular: standalone components, Material only, no NgRx
! grep -rq "standalone: false" frontend/src
! grep -q "@ngrx" frontend/package.json
grep -q "@angular/material" frontend/package.json
grep -rq "withCredentials: true" frontend/src/app
(cd frontend && npm ci && npm run build && npx ng test --watch=false --browsers=ChromeHeadless)
```

## DATA / BOUNDARY

```bash
# Flyway owns the schema; validate only outside tests; PostgreSQL only outside tests
grep -q "spring.jpa.hibernate.ddl-auto=validate" backend/src/main/resources/application.properties
! grep -q "h2" backend/src/main/resources/application.properties
grep -q "jdbc:postgresql" backend/src/main/resources/application.properties
ls backend/src/main/resources/db/migration/V*__*.sql
# money columns
! grep -rn "precision = 19, scale = 2" -L backend/src/main/java/com/carddemo/model/*.java | xargs -r grep -ln "BigDecimal"
# no startup seeding in default profile
! grep -rln "CommandLineRunner" backend/src/main/java | xargs -r grep -L "@Profile(\"import\")"
# no @Transactional on controllers
! grep -rn "@Transactional" backend/src/main/java/com/carddemo/api
```

Any failing line is a drift-rule violation; cite the rule number from section 10 in the review.
