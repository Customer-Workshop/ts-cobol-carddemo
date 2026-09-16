# 01_target_state — pointer

Authoritative artifact: `functional/CARDDEMO/CardDemo_target_state.md` (same repo/branch).

Summary (pending STOP A re-confirmation under the Java engagement): Java 21 + Spring Boot
3.4.5 on all surfaces; server-rendered Thymeleaf "simple web UI" replacing the 3270 screens;
PostgreSQL via Spring Data JPA + Flyway; single-repo topology (`spring-boot/` subdir).
Batch = Spring Batch chunk jobs launched via admin REST seam, preserving legacy exit-code
and scheduler-condition contracts. IMS/DB2/MQ/Assembler boundaries replaced with in-repo
equivalents (JPA entities, Postgres, in-process queue, Java logic) per owner instruction.
