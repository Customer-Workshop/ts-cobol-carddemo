# S-09 Transaction Add — Migration Plan (`!mf_stream_migration_plan`)

Status: DRAFT for STOP C approval (2026-09-16, Java engagement — supersedes the 2026-09-02
.NET plan). Inputs: `S09_tran_add_analysis.md`, `S09_functional_requirement.md`,
`programs/COTRN02C_functional_requirement.md`, `programs/CSUTLDTC_functional_requirement.md`,
`CardDemo_target_state.md` (CORE + ONLINE + SUBTRANSACTION + DATA/BOUNDARY),
baseline = `spring-boot/` on `devin/1789516557-carddemo-java-engagement`
(S-01 signed off; `mvn clean verify` green).

## 1. Goal and scope
Migrate the CardDemo transaction-add screen (COTRN02C; CICS transaction CT02; screen COTRN2A)
to Java 21 + Spring Boot 3.4.5 + Thymeleaf server-rendered UI + PostgreSQL 16, in the
`spring-boot/` module — **plus the shared CSUTLDTC date-validation utility, which this stream
ports once for the module** (consumed by S-10 `CORPT00C` later; `CardDemo_inventory.md` §6
row CSUTLDTC: "first of S-09/S-10 migrated owns it"). Definition of done: FR-S09-01..32 pass.
Hard stop: PF3 returns to the menu shell (S-01); the no-COMMAREA bounce to sign-on is the
server-session redirect; downstream readers of the written row (S-07/S-08 online, CBTRN*
batch) are other streams. Process type: ONLINE (screen write) + one SUBTRANSACTION leaf.

**Baseline position (FACT, verified this engagement):** the adopted port already implements
the add path as JSON — `TransactionController` (`GET`/`POST /api/transactions`) →
`TransactionService.add()` → `TransactionRepository`, `CardRepository`, `CardXrefRepository`
+ `TransactionIdGenerator` (`findTopByOrderByTranIdDesc` + `%016d` — the STARTBR
HIGH-VALUES/READPREV idiom, already FR-S09-20 correct). What it lacks vs the FR matrix:
- **Thymeleaf screen** `tran-add.html` + `UiController` seam, and the **`UI_ROUTES` flag
  flip** (`MenuService` currently points menu row 08 `COTRN02C` at the JSON list `/api/transactions`).
- **CSUTLDTC**: baseline inlines `LocalDate.parse` (`TransactionService.parseDate`), collapsing
  the layout edit and the semantic `- Not a valid date...` edit into one message and never
  producing the CEEDAYS-style verdicts or the 80-byte result contract.
- **Padded-field edit semantics**: baseline regexes accept short input the full-width NUMERIC
  tests reject (e.g. `123` passes `\d{1,11}` but fails the padded 11-position test).
- **CXACAIX/CCXREF resolution**: baseline resolves account→card through `cards`, not the
  `card_xrefs` AIX path, so the not-found/lookup message semantics differ.
- **PF5 copy-last** seam (none today), the **Confirm `Y/N` screen machine**, **`cursorField`**
  placement, **`aid` carry-through**, and verbatim `CobolMessages` text (several baseline
  strings differ, e.g. `Resp:13 Reas:0` vs `Resp:000000013  Reas:0000`).
- A **type/category existence check** the source never performs (COTRN02C edits are numeric-only);
  the wave drops it — FRs are the spec.
The wave closes those deltas and proves API + UI against the FRs; it does not re-derive behavior
from the baseline's choices.

## 2. Target-state mapping
Profiles applied: CORE + ONLINE + SUBTRANSACTION + DATA/BOUNDARY (`CardDemo_target_state.md`).
- UI: `templates/tran-add.html` under the `layout.html` shell (Tran/Prog/date/time header,
  `message-line` div, red/green per severity), served by `UiController` `GET /transactions/add`
  (tran label `CT02`); PF keys + ENTER via the `aid` form field + keydown handler
  (`menu.html` idiom). 14 inputs carry the BMS `maxlength`s + hints.
- API: REST under `/api` (baseline, extended): `POST /api/transactions` = ENTER processing;
  `POST /api/transactions/copy-last` = PF5 seam (new). Request carries the 14 fields plus
  `aid`/`confirmation`; the response is the screen-state DTO (field values + message +
  severity color + `cursorField`); `cursorField` drives template autofocus.
- Conversational state: stateless screen round-trip — the template re-submits all field values
  every post (the ported pseudo-conversational COMMAREA); the only server session state is the
  S-01 session (user id/type). PF3 → `/menu`; PF4 → cleared redisplay; unauthenticated →
  `SecurityConfig` entry point redirects UI navigation to `/signon` (the `EIBCALEN=0` bounce);
  JSON callers get the 401 `ErrorResponse`.
- SUBTRANSACTION: CSUTLDTC → `com.carddemo.service.DateValidationService` taking the CALL
  USING fields (`validate(String date, String mask)`) returning a `DateValidationResult`
  record — `severity` (`0000`/`0003`), `messageNumber` (`9(4)` text), `verdict` (15-char),
  `resultText` (the exact 80-byte layout), `valid`, parsed `LocalDate` — per
  `programs/CSUTLDTC_functional_requirement.md` (covers the mask tokens, Lillian range
  1582-10-15..9999-12-31, the eight 88-level feedback numbers, and the year-0000 → 2513 rule;
  verified complete). Callers inspect `severity` + the `2513` exemption exactly like
  `COTRN02C.cbl:397-400`.
- Layers: Controller → Service → Repository; records for DTOs; JUnit 5 + AssertJ + MockMvc;
  verbatim message text in `CobolMessages`.

## 3. Boundary decision table (decide mode over S09-B1..B6)
| ID | Class | Decision | Seam in target | Error/idempotency | Owner | Lead-time request | Routing point / cutover |
|---|---|---|---|---|---|---|---|
| S09-B1 | B5 cross-program (in + return) | **UI route + session guard.** `GET /transactions/add` behind `anyRequest().authenticated()`; PF3 → `/menu`; optional `?cardNumber=` = the `CDEMO-CT02-TRN-SELECTED` pre-fill (no shipped caller — live code kept) | `UiController` + `templates/tran-add.html` + `SecurityConfig` | bounce = redirect to `/signon` (no message), matching XCTL COSGN00C | S-09 wave | none | **Menu flag flip:** `MenuService.UI_ROUTES["COTRN02C"]` retargeted `/api/transactions` → `/transactions/add` at wave merge; legacy CT02 unreachable once menus route to web |
| S09-B2 | B4 data-access leaf (read) | Baseline repositories, keyed reads: `CardXrefRepository.findByXrefAcctId` (CXACAIX), `findById` (CCXREF) | `com.carddemo.repository.CardXrefRepository` | empty → `Account ID NOT found...` / `Card Number NOT found...`; `DataAccessException` → `Unable to lookup ...` store-error messages; reads idempotent | S-09 wave | none | — |
| S09-B3 | B4 data-access leaf (browse + write) | Baseline `TransactionRepository.findTopByOrderByTranIdDesc` + `save` via `TransactionIdGenerator` | repository + `TransactionService` | `DataIntegrityViolationException` (PK/23505) → `Tran ID already exist...`; other `DataAccessException` → `Unable to Add Transaction...` / browse errors → `Transaction ID NOT found...`/`Unable to lookup Transaction...`; **not** idempotent (each ENTER writes a row; concurrent adders collide on PK and get the DUPREC message, same observable as CICS) | S-09 wave | none | — |
| S09-B4 | B10 shared utility | **Port CSUTLDTC once, here:** `com.carddemo.service.DateValidationService` + `DateValidationResult`; CEEDAYS feedback emulated per the 88-level table (order: non-digit → 2520, month → 2517, day → 2508, range → 2513, mask → 2518, short → 2507). Replaces `TransactionService.parseDate`; S-10 `ReportService.parse` (currently also `LocalDate.parse`) consumes it later | `service/DateValidationService` | pure/stateless; severity in the result, not an exception | S-09 wave | none | module property; S-10 adopts at its wave |
| S09-B5 | B10 shared data contract | `transactions` already in the V2 Flyway baseline — no schema change; reserved range `V170x` unused unless a new artifact appears (e.g. an `xref_acct_id` index if lookup timing demands it) | `db/migration/` (only if needed) | — | S-09 wave | none | — |
| S09-B6 | B10 storage-type contract | `tran_origin_timestamp`/`tran_process_timestamp` (`timestamp`) store the validated `LocalDate` at midnight `00:00:00`; year `0000` (reachable only via the 2513 exemption) rejected with the source's own `... - Not a valid date...` (deviation D-2) | service mapping | — | S-09 wave | none | — |
No stored-procedure boundaries; no external enablement lead times — all seams are inside the
confirmed `spring-boot/` module + Postgres. Recorded explicitly: no requests fired.

## 4. Data and persistence
- Tables (all exist in `V2__baseline_domain_tables.sql`; seeding via `DataSeeder`):
  `card_xrefs` (PK `xref_card_number`, `xref_acct_id` = AIX path), `transactions`
  (PK `tran_id varchar(16)`, all §6 record fields present), `transaction_types` /
  `transaction_categories` (reference data only — **not** consulted by COTRN02C; the baseline's
  existence check is removed).
- Type mapping (FR §6): `TRAN-AMT S9(9)V99` → `BigDecimal` (2 dp, echoed `+99999999.99`);
  `TRAN-ORIG/PROC-TS X(26)` → `LocalDateTime` at midnight; `TRAN-ID X(16)` → `String` `%016d`.
- Seeding: existing seed covers the happy path (`00000000050`/`0500024453765740`); tests add
  fixture rows for the not-found/concurrency cases via Testcontainers, not migration changes.
- Data target pinned: PostgreSQL 16, Flyway, Testcontainers integration tests; H2 only for the
  fast service-level tests.

## 5. Phase 0 scaffolding deltas
**None — S-01 owns the module Phase 0** (Thymeleaf `layout.html` shell, server session,
Postgres profile + docker-compose + Flyway, GitHub Actions CI, `mvn clean verify` gate).
This stream only adds a template, a service, controller methods, and a `UI_ROUTES` entry.

## 6. Waves (from analysis DAG, leaf-first; one child session, sequential within the wave)
Single wave — single PR into `devin/1789516557-carddemo-java-engagement`:

| Order | Content | Boundary seams |
|---|---|---|
| 1 | **CSUTLDTC port (DAG leaf):** `service/DateValidationService` + `DateValidationResult` + `DateValidationServiceTest` — mask tokens, Lillian range, eight feedback numbers + verdict text + 80-char result text | S09-B4 |
| 2 | **COTRN02C service/API:** `TransactionService.add` reworked to the FR edit order — 14 padded-field edits, `CardXrefRepository` xref resolution, `DateValidationService` calls (severity `0000` or `2513` accept), `+99999999.99` echo, Confirm `Y/N`/`Invalid value` machine, copy-last via `findTopByOrderByTranIdDesc`, verbatim `CobolMessages`, `cursorField`; `TransactionController` adds the `copy-last` POST | S09-B2, B3, B5, B6 |
| 3 | **Screen:** `templates/tran-add.html` (14 inputs, BMS maxlengths, `(-99999999.99)`/`(YYYY-MM-DD)` hints, legend `ENTER=Continue  F3=Back  F4=Clear  F5=Copy Last-Tran.`, red/green `message-line`, autofocus on `cursorField`) + `UiController` `GET/POST /transactions/add` + `aid` handling (ENTER/PF3/PF4/PF5/invalid) | S09-B1 |
| 4 | **Menu flag flip:** `MenuService.UI_ROUTES["COTRN02C"]` → `/transactions/add` | S09-B1 |
| 5 | **FR parity tests:** `TransactionAddServiceTest`, `DateValidationServiceTest`, `TranAddUiIntegrationTest` (MockMvc), `TransactionAddIntegrationTest` (Testcontainers — happy-path write, next-id, duplicate-PK, concurrent adders) | all |

Program FRs consumed: `programs/COTRN02C_functional_requirement.md`,
`programs/CSUTLDTC_functional_requirement.md` (both carried over with Java target refs updated
in this doc-refresh).

## 7. Per-program FR generation
Already produced by the prior engagement and carried over — target references re-expressed for
Java in this doc-refresh; verified complete against source (CSUTLDTC's doc covers the
Lillian/mask rules: token table, 1582-10-15..9999-12-31 range, all eight 88-level numbers,
year-0000 → 2513). No program enters the wave without its doc — satisfied.

## 8. Testing and verification
- `!mf_program_parity_test` — JUnit/MockMvc tests mapped one-to-one to FR-S09 rows (naming
  `subject_verbed_frS09nn`, per S-01 convention); Testcontainers for write/browse/dup-PK paths.
- Stream E2E: sign-on → menu → option 8 → screen lifecycle (empty display; ENTER error ladder;
  PF5 copy; confirm `Y` → green message + row written; PF3/PF4; invalid AID; `?cardNumber=`
  pre-fill; unauthenticated bounce to `/signon`).
- CI regression gate: the `spring-boot` GitHub Actions workflow green on the wave PR.
- UI-bearing: optional `!mf_online_ui_testing` recorded pass over the screen (STOP D window).

## 9. Sign-off gate
`!mf_stream_signoff` executes FR §8 acceptance criteria (FR-S09-01..32) one-by-one against the
running app; every FR row gets pass/fail evidence; then an independent audit by a fresh
session; STOP E for merge authorization.

## 10. Risks
Carried from the analysis (§7) plus the baseline deltas:
1. **CEEDAYS emulation** — no LE source; the accepted/rejected set is reconstructed from the
   88-level feedback table and documented CEEDAYS semantics (Lillian range). Only pre-1582
   dates carry residual ambiguity, and the source *accepts* those (2513 exempted). MEDIUM —
   mitigated by the A-1 emulation order and `DateValidationServiceTest` coverage.
2. **Id generation race** — read-then-insert collides on the PK; surfaces as the source's own
   `Tran ID already exist...` message; no retry (none in source). LOW.
3. **Timestamp storage** — date-only stored at midnight; downstream streams render `yyyy-MM-dd`
   identically; year 0000 unrepresentable (D-2). LOW.
4. **Baseline semantic drift** — short-input regexes, `cards`-table account resolution, missing
   copy-last/confirm/cursor semantics, non-verbatim messages, extra type/category check: all
   must be reconciled to the FR matrix, not preserved. Covered by the FR parity tests. MEDIUM.
5. `Tran ID already exist...` positions the cursor on Acct # (`:740`) — kept as-is. LOW.

## 11. Effort and sequencing
Program FRs already exist; one sequential wave child after STOP C. No external lead-time waits.
S-10 depends on this stream's `DateValidationService` landing first — recorded ownership: S-09.

## Validation
Single wave follows the DAG (CSUTLDTC leaf before COTRN02C, inside the wave) — valid
topological order; every FR-S09-01..32 is covered by a wave step and a named covering test;
all six boundaries have decision + seam + owner + (none needed) request; S09-B1 carries the
routing point (UI_ROUTES flip) and cutover note; scaffolding deltas explicit (§5: none — S-01
owns Phase 0); ONLINE + SUBTRANSACTION surfaces match `CardDemo_target_state.md` profiles and
the verification mode names the UI test surface; the shared program CSUTLDTC is ported once
with ownership recorded (S-09, reused by S-10).
