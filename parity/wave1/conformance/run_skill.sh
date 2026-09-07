#!/usr/bin/env bash
# Mechanical run of .agents/skills/carddemo-target-state-conformance/SKILL.md, line by line, from repo root.
# Each check prints PASS/FAIL with the drift-rule area. Long build/test steps are run separately (see conformance_output.txt).
cd "$(git rev-parse --show-toplevel)"
chk() { local name="$1"; shift; if eval "$@" >/dev/null 2>&1; then echo "PASS  $name"; else echo "FAIL  $name  [$*]"; fi; }
echo "## CORE"
chk "CORE java.version 21"                  'grep -q "<java.version>21</java.version>" backend/pom.xml'
chk "CORE spring-boot-starter-parent"       'grep -q "<artifactId>spring-boot-starter-parent</artifactId>" backend/pom.xml'
chk "CORE parent version 3.x"               'grep -A1 "spring-boot-starter-parent" backend/pom.xml | grep -q "<version>3\."'
chk "CORE no lombok/mapstruct/modelmapper"  '! grep -rqi "lombok\|mapstruct\|modelmapper" backend/pom.xml backend/src'
chk "CORE no float/double in main"          '! grep -rnE "\b(double|float|Double|Float)\b" backend/src/main/java --include=*.java | grep -iv "test" | grep -q .'
chk "CORE no @Autowired field injection"    '! grep -rqn "@Autowired" backend/src/main/java'
echo "-- DTOs are records:"; for f in backend/src/main/java/com/carddemo/api/*Request.java backend/src/main/java/com/carddemo/api/*Response.java; do [ -e "$f" ] || continue; grep -q "public record" "$f" && echo "PASS  record $f" || echo "FAIL  NOT A RECORD: $f"; done
chk "CORE no edits to legacy app/ vs origin/main" '! git diff --name-only origin/main -- app/ | grep -q .'
echo "## ONLINE"
echo "-- controller mappings:"; grep -rhoE '@(Get|Post|Put|Delete)Mapping\("[^"]*"' backend/src/main/java/com/carddemo/api | sort -u || true
echo "-- README endpoint table rows: $(grep -c '| `' backend/README.md)"
chk "ONLINE no standalone:false"            '! grep -rq "standalone: false" frontend/src'
chk "ONLINE no @ngrx"                       '! grep -q "@ngrx" frontend/package.json'
chk "ONLINE @angular/material present"      'grep -q "@angular/material" frontend/package.json'
chk "ONLINE withCredentials: true"          'grep -rq "withCredentials: true" frontend/src/app'
echo "## DATA / BOUNDARY"
chk "DATA ddl-auto=validate"                'grep -q "spring.jpa.hibernate.ddl-auto=validate" backend/src/main/resources/application.properties'
chk "DATA no h2 in application.properties"  '! grep -q "h2" backend/src/main/resources/application.properties'
chk "DATA jdbc:postgresql"                  'grep -q "jdbc:postgresql" backend/src/main/resources/application.properties'
chk "DATA Flyway V*__*.sql exists"          'ls backend/src/main/resources/db/migration/V*__*.sql'
chk "DATA money columns precision 19 scale 2" '! grep -rn "precision = 19, scale = 2" -L backend/src/main/java/com/carddemo/model/*.java | xargs -r grep -ln "BigDecimal" | grep -q .'
chk "DATA no default-profile CommandLineRunner seeding" '! grep -rln "CommandLineRunner" backend/src/main/java | xargs -r grep -L "@Profile(\"import\")" | grep -q .'
chk "DATA no @Transactional on controllers" '! grep -rn "@Transactional" backend/src/main/java/com/carddemo/api | grep -q .'
