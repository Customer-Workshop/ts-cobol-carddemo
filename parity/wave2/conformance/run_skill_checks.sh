#!/usr/bin/env bash
# Static checks of .agents/skills/carddemo-target-state-conformance/SKILL.md (build/test steps logged separately).
cd "$(git rev-parse --show-toplevel)" || exit 1
c() { local n="$1"; shift; if eval "$@" >/dev/null 2>&1; then echo "PASS  $n"; else echo "FAIL  $n"; fi; }
c "java 21" 'grep -q "<java.version>21</java.version>" backend/pom.xml'
c "boot parent" 'grep -q "<artifactId>spring-boot-starter-parent</artifactId>" backend/pom.xml'
c "boot 3.x" 'grep -A1 "spring-boot-starter-parent" backend/pom.xml | grep -q "<version>3\."'
c "no lombok/mapstruct/modelmapper" '! grep -rqi "lombok\|mapstruct\|modelmapper" backend/pom.xml backend/src'
c "no float/double in main" '! grep -rnE "\b(double|float|Double|Float)\b" backend/src/main/java --include=*.java | grep -iv "test" | grep .'
c "no @Autowired field injection" '! grep -rn "@Autowired" backend/src/main/java | grep .'
c "DTOs are records" '! (for f in backend/src/main/java/com/carddemo/api/*Request.java backend/src/main/java/com/carddemo/api/*Response.java; do grep -q "public record" "$f" || echo "NOT A RECORD: $f"; done | grep .)'
echo "INFO  legacy diff vs origin/main: $(git diff --name-only origin/main -- app/ | wc -l) files"
echo "INFO  endpoints:"; grep -rhoE '@(Get|Post|Put|Delete)Mapping\("[^"]*"' backend/src/main/java/com/carddemo/api | sort -u
echo "INFO  README table rows: $(grep -c '| `' backend/README.md)"
c "no standalone:false" '! grep -rq "standalone: false" frontend/src'
c "no ngrx" '! grep -q "@ngrx" frontend/package.json'
c "material present" 'grep -q "@angular/material" frontend/package.json'
c "withCredentials true" 'grep -rq "withCredentials: true" frontend/src/app'
c "ddl-auto=validate" 'grep -q "spring.jpa.hibernate.ddl-auto=validate" backend/src/main/resources/application.properties'
c "no h2 in main props" '! grep -q "h2" backend/src/main/resources/application.properties'
c "jdbc:postgresql in main props" 'grep -q "jdbc:postgresql" backend/src/main/resources/application.properties'
c "BigDecimal precision" '! (grep -rn "precision = 19, scale = 2" -L backend/src/main/java/com/carddemo/model/*.java | xargs -r grep -ln "BigDecimal" | grep .)'
c "CommandLineRunner only under import profile" '! (grep -rln "CommandLineRunner" backend/src/main/java | xargs -r grep -L "@Profile(\"import\")" | grep .)'
c "no @Transactional on controllers" '! grep -rn "@Transactional" backend/src/main/java/com/carddemo/api | grep .'
