#!/usr/bin/env bash
# .agents/skills/carddemo-target-state-conformance/SKILL.md executed verbatim (read-only), output -> conformance.out.
# `mvn clean verify` result is taken from backend_suite.log (same head, same JVM run) to avoid a second Testcontainers run.
cd "$(git rev-parse --show-toplevel)" || exit 1
c() { local n="$1"; shift; if eval "$@" >/dev/null 2>&1; then echo "PASS  $n"; else echo "FAIL  $n"; fi; }
echo "head: $(git rev-parse --short HEAD)  branch: $(git branch --show-current)"
echo "--- CORE"
c "java.version 21"                     'grep -q "<java.version>21</java.version>" backend/pom.xml'
c "spring-boot-starter-parent"          'grep -q "<artifactId>spring-boot-starter-parent</artifactId>" backend/pom.xml'
c "spring boot 3.x"                     'grep -A1 "spring-boot-starter-parent" backend/pom.xml | grep -q "<version>3\."'
c "no lombok/mapstruct/modelmapper"     '! grep -rqi "lombok\|mapstruct\|modelmapper" backend/pom.xml backend/src'
c "no float/double in main"             '! grep -rnE "\b(double|float|Double|Float)\b" backend/src/main/java --include=*.java | grep -iv "test"'
c "no @Autowired field injection"       '! grep -rn "@Autowired" backend/src/main/java'
c "Request/Response DTOs are records"   '[ -z "$(for f in backend/src/main/java/com/carddemo/api/*Request.java backend/src/main/java/com/carddemo/api/*Response.java; do grep -q "public record" "$f" || echo "$f"; done)"]'
c "mvn clean verify (backend_suite.log)" 'grep -q "^EXIT 0" parity/wave3/comen01c/backend_suite.log && grep -q "BUILD SUCCESS" parity/wave3/comen01c/backend_suite.log'
c "no legacy source modified vs origin/main" '! git diff --name-only origin/main -- app/ | grep -q .'
echo "--- ONLINE"
echo "endpoints:"; grep -rhoE '@(Get|Post|Put|Delete)Mapping\("[^"]*"' backend/src/main/java/com/carddemo/api | sort -u | sed 's/^/  /'
echo "README endpoint-table rows: $(grep -c "| \`" backend/README.md)"
c "no standalone:false"                 '! grep -rq "standalone: false" frontend/src'
c "no @ngrx"                            '! grep -q "@ngrx" frontend/package.json'
c "@angular/material present"           'grep -q "@angular/material" frontend/package.json'
c "withCredentials: true"               'grep -rq "withCredentials: true" frontend/src/app'
c "frontend build"                      '(cd frontend && npm run build > ../parity/wave3/comen01c/frontend_build.log 2>&1)'
c "frontend karma (ChromeHeadless)"     '(cd frontend && npx ng test --watch=false --browsers=ChromeHeadless > ../parity/wave3/comen01c/frontend_test.log 2>&1)'
echo "--- DATA/BOUNDARY"
c "ddl-auto=validate"                   'grep -q "spring.jpa.hibernate.ddl-auto=validate" backend/src/main/resources/application.properties'
c "no h2 in application.properties"     '! grep -q "h2" backend/src/main/resources/application.properties'
c "jdbc:postgresql in application.properties" 'grep -q "jdbc:postgresql" backend/src/main/resources/application.properties'
c "BigDecimal columns precision 19,2"   '! grep -rn "precision = 19, scale = 2" -L backend/src/main/java/com/carddemo/model/*.java | xargs -r grep -ln "BigDecimal" | grep -q .'
c "CommandLineRunner only under import profile" '! grep -rln "CommandLineRunner" backend/src/main/java | xargs -r grep -L "@Profile(\"import\")" | grep -q .'
c "no @Transactional in controllers"    '! grep -rn "@Transactional" backend/src/main/java/com/carddemo/api'
