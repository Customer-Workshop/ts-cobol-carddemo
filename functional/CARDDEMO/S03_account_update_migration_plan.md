# S-03 Account Update — Migration Plan (`!mf_stream_migration_plan`)

Status: DRAFT for STOP C approval (2026-09-16, Java engagement — supersedes the 2026-09-02
.NET plan). Inputs: `S03_account_update_analysis.md`, `S03_functional_requirement.md`,
`programs/COACTUPC_functional_requirement.md`, `CardDemo_target_state.md`
(CORE + ONLINE + DATA/BOUNDARY), baseline = `spring-boot/` on
`devin/1789516557-carddemo-java-engagement` (S-01 signed off; `mvn clean verify` green).

## 1. Goal and scope
Migrate the CardDemo account-update screen (COACTUPC; CICS transaction CAUP; screen CACTUPA)
to Java 21 + Spring Boot 3.4.5 + Thymeleaf server-rendered UI + PostgreSQL 16 in the
`spring-boot/` module. Definition of done: FR-S03-01..34 pass. Hard stop: PF3 returns to the
menu shell (S-01); card-side programs (COCRDUPC/COCRDLIC/COCRDSLC) stay off-stream (S03-B4).
Process type: ONLINE (screen write, single program, six-state screen machine).

**Baseline position (FACT, verified this engagement):** the adopted port already implements a
single-shot save path — `AccountUpdateController` (`PUT /api/accounts/{accountId}`) →
`AccountUpdateService.update()` → `AccountRepository`/`CardXrefRepository`/`CustomerRepository`
in one `@Transactional` unit, carrying an `original` snapshot on the request (the stateless
ACUP-OLD/ACUP-NEW analog) with a `matches()` concurrency compare. `GET /api/accounts/{acctId}`
(`AccountViewService`) exists as a read view from a different stream's surface. What it lacks
vs the FR matrix:
- The **six-state screen machine** (Search / Details / Edit-error / Confirm / Done / Failed)
  — baseline collapses lookup+validate+save into one call.
- The **24-edit ladder** in the COBOL order (FR-S03-09) — CSUTLDPY structural date edits,
  `CSLKPCDY` lookup tables (410 area codes, 56 state codes, 240 state+zip combos),
  `CSSETATY` field flagging (`*` blank-invalid, red highlights), the numeric-required
  `… must be supplied. / … must be all numeric. / … must not be zero.` idioms.
- **Read order**: baseline reads the account before the xref; source reads CXACAIX first
  (drives different not-found messages).
- **`FOR UPDATE` row locking** (baseline saves without a lock — FR-S03-26/27 need NOWAIT
  semantics per deviation D6).
- **Verbatim messages**: baseline strings differ (e.g. `Account number must be a non zero 11
  digit number` vs the FR's `Account Number if supplied must be a 11 digit Non-Zero Number`;
  `Resp:13 Reas:0` vs `Resp:000000013  Reas:0000`, including the 75-char STRING cut).
- **Thymeleaf screen** `account-update.html` + `UiController` seam and the **`UI_ROUTES` flag
  flip** (menu row 02 `COACTUPC` has no route today → "not installed").
The wave closes those deltas and proves API + UI against the FRs; it does not re-derive
behavior from the baseline's choices.

## 2. Target-state mapping
Profiles applied: CORE + ONLINE + DATA/BOUNDARY (`CardDemo_target_state.md`).
- UI: `templates/account-update.html` under the `layout.html` shell (CAUP / COACTUPC header,
  INFOMSG neutral line + `message-line` red line), `UiController` `GET/POST /accounts/update`;
  PF keys via the `aid` field + keydown handler (`menu.html` idiom). Per-state protection,
  F5/F12 lit rules and `*` blank-invalid rendering come from the response model.
- API: REST under `/api`: `POST /api/accounts/lookup` (search/lookup → screen state),
  `POST /api/accounts/validate` (edits → validated/invalid + `invalidFields`), and the baseline
  `PUT /api/accounts/{accountId}` (save → committed/lockError/updateFailed/changedByOther/
  noChange/invalid). `original` + `updated` snapshots ride the request = the ported
  `WS-THIS-PROGCOMMAREA` (S03-B3); server re-runs compare + edits on save, so a stale or
  tampered snapshot is rejected exactly like the COMMAREA one.
- Conversational state: stateless round-trip (the template re-submits snapshots every post);
  only S-01 session (user id/type) is held server-side. PF3 → `/menu`; F12 → re-read +
  Details; unauthenticated → `SecurityConfig` redirect to `/signon`.
- Layers: Controller → Service → Repository; `service/AccountUpdateEditRules` holds the
  1200-series edit ladder + `CSLKPCDY` lists verbatim (pure Java, JUnit-friendly); records for
  DTOs; JUnit 5 + AssertJ + MockMvc; verbatim message text in `CobolMessages`.
- Persistence: JPA `accounts`/`customers`/`card_xrefs`; save path takes `SELECT … FOR UPDATE
  NOWAIT` row locks (account then customer, source order) inside one `@Transactional` method;
  lock contention → `Could not lock <account|customer> record for update`; write failure →
  rollback + `Update of record failed` (= SYNCPOINT ROLLBACK).

## 3. Boundary decision table (decide mode over S03-B1..B5)
| ID | Class | Decision | Seam in target | Error/idempotency | Owner | Lead-time request | Routing point / cutover |
|---|---|---|---|---|---|---|---|
| S03-B1 | B5 outbound | PF3 → redirect `/menu` (S01-B3 stable UI route) | `UiController` | none (nav) | S-03 wave | none | — |
| S03-B2 | B4 data-access leaf (read-update/rewrite ×2) | One `@Transactional` save: `accounts` then `customers` locked reads (`FOR UPDATE NOWAIT`), compare vs snapshot, write both | `AccountRepository`/`CustomerRepository` lock methods + `AccountUpdateService` | lock failure → `Could not lock … record for update` (Failed state); snapshot mismatch → `Record changed by some one else. Please review` (Details); write failure → rollback + `Update of record failed`; save is retried by F5, not replayed | S-03 wave | none | — |
| S03-B3 | B10 shared state (COMMAREA) | Stateless screen round-trip: template carries `original`/`updated` snapshots + change-action state as form fields; server revalidates | `account-update.html` + request DTO | stale snapshot rejected as changed (same observable) | S-03 wave | none | — |
| S03-B4 | B5 declared-unused literals | COCRDUPC/COCRDLIC/COCRDSLC stay off-stream — no `UI_ROUTES` entries | — | — | — | none | — |
| S03-B5 | LE runtime | `CSUTLDTC` call inside `CSUTLDPY` is unreachable (structural checks run first) — not ported here; S-09 owns the real `DateValidationService` for the module | — | — | — | none | — |
Menu-side routing point: **flag flip** — `MenuService.UI_ROUTES["COACTUPC"]` →
`/accounts/update` added at wave merge (S01-B1 registry); legacy CAUP unreachable once menus
route to web. No stored-procedure boundaries; no external enablement lead times — all seams
inside the `spring-boot/` module + Postgres. Recorded explicitly: no requests fired.

## 4. Data and persistence
- Tables (all in `V2__baseline_domain_tables.sql`, seeded by `DataSeeder`): `card_xrefs`
  (`xref_acct_id` lookup → cust id/card num), `accounts` (acct id PK; status, 5 money amounts,
  3 dates, group id), `customers` (cust id PK; names, address, state, country, zip, phones,
  SSN, DOB, govt id, EFT id, primary-holder ind, FICO). Every COACTUPC field already exists —
  **no schema change**; reserved Flyway range `V110x` unused unless a lock/index artifact is
  needed.
- Type mapping (FR §6): money `S9(10)V99` → `BigDecimal` (`numeric(12,2)`), `TEST-NUMVAL-C`
  accepted forms (sign, separators, `$`, `CR`/`DB`); dates `YYYY-MM-DD` → `LocalDate` (null →
  blank parts); phones stored raw `(aaa)bbb-cccc` (blank phone written `()-`); zip = the 5
  typed chars.
- Data target pinned: PostgreSQL 16, Flyway, Testcontainers integration tests; H2 for fast
  service/edit-rule tests.

## 5. Phase 0 scaffolding deltas
**None — S-01 owns the module Phase 0** (Thymeleaf shell, server session, Postgres + Flyway,
CI). This stream adds a template, service/edit-rule classes, controller methods, repository
lock methods, and one `UI_ROUTES` entry.

## 6. Waves (from analysis DAG; single program → single wave, one child session, one PR)
| Order | Content | Boundary seams |
|---|---|---|
| 1 | **Edit rules + lookups (leaf code):** `service/AccountUpdateEditRules` — the FR-S03-09 ordered ladder, CSUTLDPY structural dates (incl. 19xx/20xx century rule, leap, DOB-future), `TEST-NUMVAL-C` money forms, `CSLKPCDY` area-code/state/state+zip tables verbatim, `CSSETATY` flagging → `invalidFields` | — |
| 2 | **Service + persistence:** `AccountUpdateService.lookup/validate/save` six-state machine; xref-first read order; verbatim `CobolMessages` incl. RESP-rendered not-found text (75-char cut); `@Transactional` save with `FOR UPDATE NOWAIT` locks, snapshot compare, rollback; `AccountUpdateController` adds `POST /api/accounts/lookup` + `POST /api/accounts/validate` beside baseline `PUT` | S03-B2, B3 |
| 3 | **Screen:** `templates/account-update.html` (per-state protection, F5/F12 lit rules, `*` blank-invalid, info + error lines, legend `ENTER=Process F3=Exit`/`F5=Save`/`F12=Cancel`) + `UiController` `GET/POST /accounts/update` + `aid` handling (ENTER/F3/F5/F12/invalid) | S03-B1, B3 |
| 4 | **Menu flag flip:** `MenuService.UI_ROUTES["COACTUPC"]` → `/accounts/update` | S01-B1 routing point |
| 5 | **FR parity tests:** `AccountUpdateEditRulesTest`, `AccountUpdateServiceTest`, `AccountUpdateIntegrationTest` (Testcontainers: seeded Postgres lookups incl. sample-data rejects, lock contention, concurrency, rollback), `AccountUpdateUiIntegrationTest` (MockMvc state machine) | all |

Program FR consumed: `programs/COACTUPC_functional_requirement.md` (carried over; Java target
refs updated in this doc-refresh).

## 7. Per-program FR generation
Already produced by the prior engagement and carried over — target references re-expressed for
Java in this doc-refresh. No program enters the wave without its doc — satisfied.

## 8. Testing and verification
- `!mf_program_parity_test` — JUnit/MockMvc tests mapped one-to-one to FR-S03 rows
  (`subject_verbed_frS03nn` naming, per S-01 convention); Testcontainers for lookup/save/lock/
  rollback paths (FR-S03-04..07, 25-29, 31, 34).
- Stream E2E: sign-on → menu → option 2 → Search → lookup (found + three not-found variants) →
  edits ladder → Confirm → F5 save → Done → ENTER re-fetch; F12 cancel; invalid AID; PF3 exit;
  unauthenticated bounce to `/signon`.
- CI regression gate: the `spring-boot` GitHub Actions workflow green on the wave PR.
- UI-bearing: optional `!mf_online_ui_testing` recorded pass over the screen (STOP D window).

## 9. Sign-off gate
`!mf_stream_signoff` executes FR §8 acceptance criteria (FR-S03-01..34) one-by-one against the
running app; every FR row gets pass/fail evidence; then an independent audit by a fresh
session; STOP E for merge authorization.

## 10. Risks
Carried from the analysis (§7) plus the baseline deltas:
1. **Source quirks** (D1-D6): customer-lock mis-branch, account-miss continue, stale
   post-commit snapshot, no-change-after-failure abend, LOW-VALUES trim, NOWAIT lock parity —
   all recorded deviations; tests assert the target behavior, not the literal source bug.
   MEDIUM (largest single surface in this stream).
2. **`CSLKPCDY` transcription** — 410 + 56 + 240 literal entries ported verbatim; a slip changes
   validation results. Mitigated by member/non-member unit tests. MEDIUM.
3. **Seeded data fails the edits** (customer 1 phone `373`, `NC`+`12` zip) — parity means the
   target rejects them too; integration tests must not assume seed data is editable. LOW.
4. **Baseline drift** — single-shot save without states/locks/edit ladder and non-verbatim
   messages must be reconciled to the FRs, not preserved; covered by parity tests. MEDIUM.

## 11. Effort and sequencing
Program FR exists; one sequential wave child after STOP C. No external lead-time waits.
Independent of S-06/S-09 (distinct tables, distinct template/route); can run in any order among
the three streams.

## Validation
Single wave follows the DAG (depth 1, one program) — trivially topological; every FR-S03-01..34
is covered by a wave step and a named covering test; all five boundaries have decision + seam +
owner; S03-B4/B5 need no requests and carry their no-port rationale; the S01-B1 routing point
(UI_ROUTES flip) is named; scaffolding deltas explicit (§5: none — S-01 owns Phase 0); ONLINE
surfaces match `CardDemo_target_state.md` and the verification mode names the UI test surface;
no shared programs are ported by this stream (S03-B5 stays with S-09's ownership).
