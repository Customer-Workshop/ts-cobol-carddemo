# S-07 Transaction List — Migration Plan (`!mf_stream_migration_plan`)

Status: DRAFT for STOP C approval (2026-09-16, Java engagement — supersedes the 2026-08-20
.NET draft). Inputs: `S07_tran_list_analysis.md`, `S07_functional_requirement.md`,
`CardDemo_target_state.md` (CORE + ONLINE + DATA/BOUNDARY, all CONFIRMED at STOP A),
baseline = `spring-boot/` port adopted from the open baseline PR, extended by the merged
S-01 waves (`mvn clean verify` green, 69 tests, engagement HEAD `df05ebc`).

## 1. Goal and scope
Migrate the CardDemo transaction-list browse (COTRN00C; transaction CT00, BMS map
`COTRN0A`) to Java 21 + Spring Boot 3.4.5 + server-rendered Thymeleaf UI + PostgreSQL, in
the single repo (`spring-boot/`). Definition of done: FR-S07-01..21 pass. Hard stop:
`XCTL COTRN01C` on row selection is S-08's entry — resolved through the route registry,
never implemented here. Process type: ONLINE.

**Baseline position (FACT, verified this engagement):** the adopted port already implements
the browse as a JSON API — `TransactionController` `GET /api/transactions?filter=&page=&direction=`
→ `TransactionService.list` → `TransactionRepository` `findByTranIdGreaterThanEqual` /
`findByTranIdLessThanEqual` with `PageRequest` + `Sort` (10 rows/page, `tranId` ASC/DESC —
the STARTBR/READNEXT/READPREV equivalent), returning `TransactionListResponse`. What it
lacks or deviates on vs the source FRs — the deltas this stream closes:

- **No web UI**: no `transaction-list.html`, no `UiController` endpoints, `UI_ROUTES` has
  no COTRN00C entry (option 06 lands on the REST list route today).
- **Paging semantics**: the baseline answers pages via `Page` (`page`/`direction`), while
  the source is cursor-based over TRNID-FIRST/LAST with peek rules — FR-S07-07..14 pin the
  quirks (STARTBR-NOTFND → `You are at the top of the page...` wording, PF7-at-top forcing
  NEXT-PAGE-YES, page number incremented only when ≥1 row shown, `already at top/bottom`
  vs `reached the top/bottom` message distinctions). The screen path carries a client-held
  paging-state record (S07-B5), not `Page<>`.
- **Search edit**: baseline accepts `\d{1,16}` and zero-pads (`%016d`); the source's
  `IS NUMERIC` over the full 16-char field rejects any short entry — e.g. `12` →
  `Tran ID must be Numeric ...`, not a zero-padded browse (FR-S07-05).
- **Verbatim messages**: `TRANSACTION_ID_INVALID` is `Tran ID must be Numeric...` vs
  source `Tran ID must be Numeric ...` (space before `...`); `You are at the top of the
  page...`, `You are already at the top/bottom of the page...`, `Unable to lookup
  transaction...`, `Invalid selection. Valid value is S` do not exist in `CobolMessages`
  yet. The `optionComingSoon` name-concatenation quirk (name emitted `DELIMITED BY SPACE`
  in source) is called out in FR-S07-15's disposition.
- **Row selection / AID**: 10 `SEL` fields, first-non-blank-wins + `S`-only validation
  (`Invalid selection. Valid value is S` — non-blocking, processing continues), and the
  ENTER/PF3/PF7/PF8 + invalid-key AID table are UI-side behavior the REST surface does
  not carry.

## 2. Target-state mapping
Profiles applied: CORE + ONLINE + DATA/BOUNDARY (`CardDemo_target_state.md`).
- API: REST under `/api` (baseline, keep) — `GET /api/transactions` unchanged in contract;
  the screen uses the same repository browse but drives the source's cursor state machine.
- Conversational state (COMMAREA + `CDEMO-CT00-INFO`) → server-side HTTP session +
  Spring Security context (S01-B6, built) for identity; the **paging state is client-held**
  (S07-B5): a record of TRNID-FIRST/LAST, page number, next-page flag round-tripped as
  hidden form fields — the pseudo-conversational COMMAREA extension faithfully carried.
- UI: Thymeleaf screen `transaction-list.html` on the S-01 `layout.html` shell (header
  `CT00`/`COTRN00C`/titles/date/time, `List Transactions` title, `Page:` 8-digit zero-filled,
  instruction + `ENTER=Continue  F3=Back  F7=Backward  F8=Forward` footer, red ERRMSG line).
  `UiController` `GET /transactions/list` (first display / optional search seed) + `POST
  /transactions/list` (`aid` + `trnIdIn` + 10 `sel` fields + hidden paging state).
- Layers: Controller → Service → Repository; records for DTOs; JUnit 5 + AssertJ.
- Persistence: Spring Data JPA + Flyway (Postgres 16, Testcontainers in tests); H2 remains
  the fast unit-test profile.

## 3. Boundary decision table (decide mode over S07-B1..B5)
| ID | Class | Decision | Seam in target | Error/idempotency | Owner | Lead-time request | Routing point / cutover |
|---|---|---|---|---|---|---|---|
| S07-B1 | B5 cross-program switch | **Registry-resolved hand-off**: row selection `S` resolves COTRN01C through `MenuService`/`UI_ROUTES` (option 07): not browsable → coming-soon idiom (verbatim per `COMEN01C.cbl:172-176`, see FR-S07-15 disposition); browsable → `redirect:/transactions/view?tranId=<selected>` (S08-B2 contract) | `UiController` select branch + `MenuService.uiRoute` | none (pure nav); invalid selection char → non-blocking `Invalid selection. Valid value is S` | S-07 wave | none | route flag flips when S-08's wave merges; legacy XCTL decommissioned at module cutover |
| S07-B2 | B5 outbound return | **PF3 → `redirect:/menu`** (S01-B3 idiom) | `UiController` PF3 branch | none | S-07 wave | none | — |
| S07-B3 | B4 data-access leaf | **Reuse `TransactionRepository` browse** — `findByTranIdGreaterThanEqual` / `findByTranIdLessThanEqual` + `PageRequest`/`Sort` over `tran_id` implement STARTBR(GTEQ)/READNEXT/READPREV/ENDBR; cursor cursors (first/last displayed id) replace page numbers | `spring-boot/.../repository/TransactionRepository` + list screen-state service | store failure → `Unable to lookup transaction...`, rows/state unchanged; reads idempotent | S-07 wave | none | service layer is the strangler point; VSAM TRANSACT read path decommissioned at module cutover |
| S07-B4 | B5 entry guard | **Spring Security entry point** — unsigned navigation to `/transactions/list` bounces to `/signon` (the EIBCALEN=0 → XCTL COSGN00C equivalent); API keeps 401 `ErrorResponse` | `security/SecurityConfig` (built S-01) | session expiry → bounce | S-01 (built), consumed here | none | — |
| S07-B5 | B10 shared data contract | **Client-held paging-state record** (first/last displayed id, page number, next-page flag) round-tripped as hidden form fields — mirrors `CDEMO-CT00-INFO` being rebuilt per pseudo-conversational turn; no server session for browse state | request DTO/record + hidden inputs | tampered/missing state → treat as fresh entry (LOW-VALUES start) — matches EIBCALEN=0 re-init behavior | S-07 wave | none | — |

All seams are inside the confirmed single repo + Postgres; **no external lead-time requests
exist for this stream** (recorded explicitly). No stored-procedure boundaries.

## 4. Data and persistence
- Table consumed (read-only, baselined by Flyway V2): `transactions` (PK `tran_id`,
  `tran_description`, `tran_amount`, `tran_origin_timestamp`). Field mapping per analysis §4:
  `X(n)` → `String`, `S9(09)V99` → `BigDecimal`, timestamp → `LocalDateTime`.
- **No schema change needed** — `ORDER BY tran_id` already reproduces VSAM key order
  (16-char digit strings sort identically under byte order and the default collation);
  the reserved Flyway range **V150x stays unused**.
- Seeding: unchanged — `DataSeeder` loads `app/data/ASCII/dailytran.txt`; integration
  tests use Testcontainers Postgres seeded from the fixture.

## 5. Phase 0 scaffolding deltas
**NONE.** S-01 built the module's Phase 0: Thymeleaf + `layout.html` shell, `UiController`
idiom, `aid` field convention, Postgres profile + `docker-compose.yml`, Flyway seam, CI
(`mvn -B clean verify` gate), session/security seam. S-07 reuses, never forks.

## 6. Waves (from analysis DAG, leaf-first; one child session per wave, sequential)
| Wave | Programs/seams | Repo area | Consumes FR docs | Boundary seams | Strict edge to next |
|---|---|---|---|---|---|
| 1 | COTRN00C (single program): `transaction-list.html` + `UiController` `/transactions/list` GET/POST; cursor-based paging state machine over `TransactionRepository` browse reproducing the STARTBR/peek quirks (FR-S07-07..14, incl. page-number rules and the five paging wordings); row-scan selection + `S`-only validation; exact-width search edit (all-16-digit NUMERIC, no zero-pad acceptance); verbatim `CobolMessages` additions/corrections (`Tran ID must be Numeric ...`, `You are at the top of the page...`, `You are already at ...`, `Unable to lookup transaction...`, `Invalid selection. Valid value is S`); amount `+99999999.99` + `mm/dd/yy` + 26-char-desc row formatting; FR-S07-01..21 test matrix; `UI_ROUTES` COTRN00C → `/transactions/list` flag flip (menu option 06) | `spring-boot/` | COTRN00C program FR | S07-B1..B5 | — (terminal wave) |

Single-repo topology: one PR into the engagement branch. The REST contract is unchanged;
the screen drives the source state machine on top of the same repository browse.

## 7. Per-program FR generation
Already produced by the prior engagement and carried over with Java terminology updates
(`programs/COTRN00C_functional_requirement.md`, FR-S07-01..21 = COTRN00C-01..21). Verified
complete against the analysis this session; no regeneration needed.

## 8. Testing and verification
- `!mf_program_parity_test`: JUnit/MockMvc tests mapped to FR-S07-01..21 — the FR §8
  acceptance criteria verbatim (each Given/When/Then row becomes a named test), incl. the
  paging-quirk matrix (full page +1, ENDFILE bottom wording, STARTBR-NOTFND top wording,
  PF7-at-top forced flag, partial-page rules, `already at` vs `reached` wordings) and the
  non-blocking invalid-selection behavior. Service unit tests + `TransactionListUiIntegrationTest`.
- Testcontainers Postgres integration: seeded `dailytran.txt` — first-page ordering
  (byte-order ids `0009`/`0010`/`0100`), GTEQ start key, row formatting, ordering test.
- Stream E2E: sign-on → menu option 06 → `/transactions/list` → PF8/PF7 round trip → `S`
  select (coming-soon until S-08 lands, then navigate) → PF3 back to `/menu`.
- CI regression gate: `mvn -B clean verify` green on the wave PR.
- UI-bearing: `!mf_online_ui_testing` recorded pass over the screen (STOP D window).

## 9. Sign-off gate
`!mf_stream_signoff` executes FR §8 acceptance criteria one-by-one against the running app;
every FR row gets pass/fail evidence; then an independent audit by a fresh session; STOP E
for merge authorization.

## 10. Risks
1. **Paging fidelity is the stream's hard part**: the source's peek/skip and page-number
   rules (analysis §3, FR §7 preserved quirks) don't map onto `Page<>`-style semantics —
   the wave must reproduce the cursor state machine exactly; mitigated by the per-row
   FR matrix. MEDIUM.
2. FSET redisplay semantics — rows/selections persist on screen when a redisplay doesn't
   repopulate them (`already at top/bottom` cases); the template must echo state rather
   than clear. LOW-MEDIUM.
3. Message-text drift in baseline (`Tran ID must be Numeric...` missing its space; missing
   `already at`/`at the top` variants) — pinned verbatim by tests. LOW.
4. `optionComingSoon` name-truncation quirk (see FR-S07-15 disposition) — shared constant;
   fix affects S-01's menu too, coordinate the correction there (it's source-parity).
   LOW.
5. Search-edit tightening (`\d{1,16}` → all-16 NUMERIC) changes REST behavior for short
   numeric filters (currently zero-padded and browsed); required for FR-S07-05 parity —
   a divergence fix, not a contract break. LOW.
6. Concurrent delete of the displayed first/last record shifts the cursor — mirrors VSAM
   GTEQ via `>=`/`<` comparisons; accepted source-faithful behavior. LOW.

## 11. Effort and sequencing
Program FR already exists; 1 sequential wave child after STOP C. No external lead-time
waits. All Phase-0 dependencies satisfied by S-01. Ordering vs S-08: either order works —
if S-07 lands first, selection yields coming-soon until S-08 merges (registry flag); if
S-08 lands first, its `/transactions/view?tranId=` contract is consumed immediately.

## Validation
Waves match analysis DAG (single-program stream, single wave — topological trivially);
FR-S07-01..21 all covered by wave 1; all 5 boundaries decided in Java terms (0 deferrals,
0 undecided); scaffolding deltas explicit (NONE, §5); ONLINE surfaces + UI verification
mode per ONLINE profile; shared seams (session, shell, `TransactionRepository`, CI)
consumed from S-01/baseline, none re-ported; `transactions` table read-only, no ALTER;
hard stop respected (COTRN01C hand-off is registry routing, not an implementation).
