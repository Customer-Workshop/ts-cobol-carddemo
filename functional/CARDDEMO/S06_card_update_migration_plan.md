# S-06 Card Update — Migration Plan (`!mf_stream_migration_plan`)

Status: DRAFT for STOP C approval (2026-09-16, Java engagement — supersedes the 2026-09-02
.NET plan). Inputs: `S06_card_update_analysis.md`, `S06_functional_requirement.md`,
`programs/COCRDUPC_functional_requirement.md`, `CardDemo_target_state.md`
(CORE + ONLINE + DATA/BOUNDARY), baseline = `spring-boot/` on
`devin/1789516557-carddemo-java-engagement` (S-01 signed off; `mvn clean verify` green).

## 1. Goal and scope
Migrate the CardDemo card-update screen (COCRDUPC; CICS transaction CCUP; screen CCRDUPA)
to Java 21 + Spring Boot 3.4.5 + Thymeleaf server-rendered UI + PostgreSQL 16 in the
`spring-boot/` module. Definition of done: FR-S06-01..29 pass. Hard stop: PF3 returns to the
menu shell (S-01); return-to-list after a completed/failed update (COCRDLIC, S-04) is not
migrated. Process type: ONLINE (screen write, single program, six-state screen machine).

**Baseline position (FACT, verified this engagement):** the adopted port already implements a
partial save path — `CardController` (`PUT /api/cards/{cardNumber}`) → `CardService.update()` →
`CardRepository` in one `@Transactional` unit, taking a `CardUpdateRequest` with an `original`
`CardSnapshot` (the stateless CCUP-OLD/NEW analog) and a field-by-field `equalsIgnoreCase`
concurrency compare. What it lacks vs the FR matrix:
- The **six-state screen machine** (not-fetched / S details / E edit-errors / N confirm /
  C done / L-F failed) and the search edits on account + card keys.
- **Read semantics**: the source reads the card by card number only and never matches the
  account (`:1379-1380` commented out); the baseline rejects when `accountId !=
  card.cardAcctId` — an added validation the FRs don't have.
- **Row locking + compare order**: baseline saves without a `FOR UPDATE` lock and clamps the
  day via `Math.min` to the last day of month; source keeps the old day and fails the rewrite
  when the combination is not a calendar date (deviation D3 surface = `Update of record failed`).
- **Verbatim messages**: baseline strings differ (`Card name can only contain letters and
  spaces` vs `Card name can only contain alphabets and spaces`; `Record not found.` vs
  `Did not find cards for this search condition`; no file-error RESP template; different
  failure texts).
- **Stateless round-trip**: baseline has no `aid`/`cursorField`/state fields; no Thymeleaf
  screen or `UiController` seam; `MenuService.UI_ROUTES` has no `COCRDUPC` entry → "not
  installed" (option 05 unavailable on the menu).
The wave closes those deltas and proves API + UI against the FRs; it does not re-derive
behavior from the baseline's choices.

## 2. Target-state mapping
Profiles applied: CORE + ONLINE + DATA/BOUNDARY (`CardDemo_target_state.md`).
- UI: `templates/card-update.html` under the `layout.html` shell (CCUP / COCRDUPC header,
  INFOMSG neutral line + `message-line` red line, `F5=Save F12=Cancel` legend only in state N),
  `UiController` `GET/POST /cards/update`; `GET /cards/update?acctId=&cardNum=` pre-fetches
  (list-entry seam, FR-S06-28). PF keys via the `aid` hidden field + keydown handler
  (`menu.html` idiom); **`aid` values other than ENTER/PF3/PF5-in-N/PF12-after-fetch are
  remapped to ENTER** (`:422-424`, source-faithful — differs from S-01's "Invalid key" and from
  S-03's message). `autofocus` on account (search) / name (details).
- API: REST under `/api`: `POST /api/cards/lookup` (search → screen state or not-found),
  `POST /api/cards/validate` (edits → validated/invalid + `invalidFields`), and the baseline
  `PUT /api/cards/{cardNumber}` (save → committed/changedByOther/lockError/updateFailed/
  noChange). OLD/NEW images + change-action ride the request = the ported
  `WS-THIS-PROGCOMMAREA` (S06-B2 handles the list-entry variant); the server re-runs edits +
  compare on save.
- Conversational state: stateless form-field round-trip; only the S-01 session server-side.
  PF3 → `/menu`; PF12 → re-read + state S (message kept); ENTER in C/L/F resets to a fresh
  search screen; unauthenticated → `SecurityConfig` redirect to `/signon`.
- Layers: Controller → Service → Repository; `CardService` carries the six-state machine +
  four-edit ladder verbatim (source-faithful order name → status → month → year, all four run);
  verbatim message text in `CobolMessages`; records for DTOs; JUnit 5 + AssertJ + MockMvc.
- Persistence: JPA `cards`; save path takes `FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`) read on
  the card inside one `@Transactional` method; lock failure → `Could not lock record for
  update` (state L); mismatch vs snapshot → `Record changed by some one else. Please review`
  (state S, refreshed OLD); write failure / non-calendar date (D3) → `Update of record failed`
  (state F). Read errors render the RESP file-error template (`RESP 000000017`, `RESP2
  000000000`).

## 3. Boundary decision table (decide mode over S06-B1..B4)
| ID | Class | Decision | Seam in target | Error/idempotency | Owner | Lead-time request | Routing point / cutover |
|---|---|---|---|---|---|---|---|
| S06-B1 | B4 data-access leaf (read/lock/rewrite) | `findById` READ → six-state machine; `FOR UPDATE` locked read + 5-field compare + `save` in one `@Transactional` = READ UPDATE/REWRITE | `CardRepository` + `CardService` | NOTFND → `Did not find cards for this search condition`; other read errors → RESP `000000017` template; lock fail → state L; compare fail → state S; save fail / non-calendar date → state F; F5 retries, no replay | S-06 wave | none | — |
| S06-B2 | B5 inbound switch (list → detail) | `GET /cards/update?acctId=&cardNum=` auto-fetches = list-entry read; return-to-list unmigrated | `UiController` | bad/missing params → fresh search state; exit → `/menu`; post-update ENTER resets | S-06 wave | none | — |
| S06-B3 | B5 outbound switch | PF3 → redirect `/menu` (S01-B3) | `UiController` | none (nav) | S-06 wave | none | — |
| S06-B4 | B10 shared data contract | shared `cards` table (V2 baseline) + `CardRepository`; this stream the only online writer; single-writer decision stays with the data-layer owner | `Card` entity | — | — | none | — |
Menu-side routing point: **flag flip** — `MenuService.UI_ROUTES["COCRDUPC"]` →
`/cards/update` added at wave merge (S01-B1 registry); legacy CCUP unreachable once menus
route to web. No stored-procedure boundaries; no external enablement lead times — all seams
inside the `spring-boot/` module + Postgres. Recorded explicitly: no requests fired.

## 4. Data and persistence
- Table (already in `V2__baseline_domain_tables.sql`, seeded by `DataSeeder`): `cards`
  (`card_number` varchar(16) PK, `card_acct_id`, `card_cvv_code`, `card_embossed_name`,
  `card_expiration_date` `date`, `card_active_status`). Every COCRDUPC field already exists —
  **no schema change**; reserved Flyway range `V140x` unused unless a lock/index artifact is
  needed.
- Type mapping (FR §6): expiry stored `date` (`LocalDate`); rewrite composes
  `LocalDate.of(year, month, oldDay)` — a non-calendar combination is rejected by the store
  and surfaces as `Update of record failed` (deviation D3); name/status/month/year written as
  typed; CVV + account id **preserved** (deviation D1 — the source's CVV-`000` / typed-acct
  defect is not propagated). Concurrency compare covers the five displayed fields, CVV omitted
  (deviation D2 — the browser never receives it).
- Data target pinned: PostgreSQL 16, Flyway, Testcontainers integration tests; H2 for fast
  service tests.

## 5. Phase 0 scaffolding deltas
**None — S-01 owns the module Phase 0** (Thymeleaf shell, server session, Postgres + Flyway,
CI). This stream adds a template, service methods, controller endpoints, repository lock
method, and one `UI_ROUTES` entry.

## 6. Waves (from analysis DAG; single program → single wave, one child session, one PR)
| Order | Content | Boundary seams |
|---|---|---|
| 1 | **Service + persistence:** `CardService` — six-state machine, search edits (account 11-digit, card 16-digit, `*` = blank, `No input received` override, first-message wins), fetch (card-number-only read, upper-cased OLD image), change-detect + four-edit ladder (name → status → month → year), `FOR UPDATE` lock + 5-field compare + `save`, verbatim `CobolMessages` incl. RESP file-error template; `CardController` adds `POST /api/cards/lookup` + `POST /api/cards/validate` beside baseline `PUT` | S06-B1, B4 |
| 2 | **Screen:** `templates/card-update.html` (per-state protection, `*` blank-invalid + red flagging, F5/F12 legend only in N, day read-only, BMS maxlengths, `ENTER=Process F3=Exit` legend) + `UiController` `GET/POST /cards/update` (incl. `?acctId=&cardNum=` pre-fetch) + `aid` remap-to-ENTER semantics | S06-B1, B2, B3 |
| 3 | **Menu flag flip:** `MenuService.UI_ROUTES["COCRDUPC"]` → `/cards/update`; option 05 becomes available | S01-B1 routing point |
| 4 | **FR parity tests:** `CardUpdateServiceTest`/`CardServiceTest`, `CardUpdateIntegrationTest` (Testcontainers: seed lookups, lock failure, concurrent-write mismatch, D3 date failure, D1 field preservation), `CardUpdateUiIntegrationTest` (MockMvc state machine + `aid` remap) | all |

Program FR consumed: `programs/COCRDUPC_functional_requirement.md` (carried over; Java target
refs updated in this doc-refresh).

## 7. Per-program FR generation
Already produced by the prior engagement and carried over — target references re-expressed for
Java in this doc-refresh. No program enters the wave without its doc — satisfied.

## 8. Testing and verification
- `!mf_program_parity_test` — JUnit/MockMvc tests mapped one-to-one to FR-S06 rows
  (`subject_verbed_frS06nn` naming, per S-01 convention); Testcontainers for lookup/save/lock/
  compare/rollback paths (FR-S06-10..12, 22..25, 27).
- Stream E2E: sign-on → menu → option 5 → search (found + not-found + file-error template) →
  edits (each message, `*` flagging, first-fail ordering) → N confirm → F5 save → C done →
  ENTER reset; PF12 cancel keeps message; PF3 exit; invalid AID acts as ENTER (no message);
  unauthenticated bounce to `/signon`.
- CI regression gate: the `spring-boot` GitHub Actions workflow green on the wave PR.
- UI-bearing: optional `!mf_online_ui_testing` recorded pass over the screen (STOP D window).

## 9. Sign-off gate
`!mf_stream_signoff` executes FR §8 acceptance criteria (FR-S06-01..29) one-by-one against the
running app; every FR row gets pass/fail evidence; then an independent audit by a fresh
session; STOP E for merge authorization.

## 10. Risks
Carried from the analysis (§7) plus the baseline deltas:
1. **AID parity differs from S-01**: unmapped keys act as ENTER — kept source-faithful; the
   S-01 invalid-key helper must not be reused here. LOW.
2. **D1 source defect** (rewrite stores CVV `000` + typed acct id): target preserves CVV and
   account id; tests assert preservation. MEDIUM (data corruption if blindly ported).
3. Account id never matched to the card — kept as-is; the baseline's added
   `accountId.equals(card.cardAcctId)` check must be removed to reach parity. LOW.
4. PF12 keeps the edit-pass message on the refreshed screen — kept as-is. LOW.
5. **D2/D3**: CVV omitted from compare (never sent to the browser); non-calendar month/day
   reaches `Update of record failed` instead of being stored as text — the baseline's
   `Math.min` day-clamp must be replaced by the real D3 failure path. LOW.
6. **Baseline drift** — non-verbatim messages and missing states must be reconciled to the
   FRs, not preserved; covered by parity tests. MEDIUM.

## 11. Effort and sequencing
Program FR exists; one sequential wave child after STOP C. No external lead-time waits.
Independent of S-03/S-09 (distinct table, distinct template/route); can run in any order among
the three streams.

## Validation
Single wave follows the DAG (depth 1, one program) — trivially topological; every FR-S06-01..29
is covered by a wave step and a named covering test; all four boundaries have decision + seam +
owner; none need external requests; the S01-B1 routing point (UI_ROUTES flip) is named;
scaffolding deltas explicit (§5: none — S-01 owns Phase 0); ONLINE surfaces match
`CardDemo_target_state.md` and the verification mode names the UI test surface; no shared
programs are ported by this stream.
