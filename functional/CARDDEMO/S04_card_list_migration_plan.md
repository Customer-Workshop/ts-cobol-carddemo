# S-04 Card List — Migration Plan (`!mf_stream_migration_plan`)

> Java-engagement rewrite (2026-09-15): this plan supersedes the .NET plan for this stream. Structure and
> conventions follow `S01_SignonMenu_migration_plan.md`; the target stack is the confirmed Java 21 / Spring Boot
> 3.4.5 / Thymeleaf / PostgreSQL 16 baseline, not an assumed one.

Inputs: [S04_card_list_analysis.md](S04_card_list_analysis.md), [S04_functional_requirement.md](S04_functional_requirement.md), [CardDemo_target_state.md](CardDemo_target_state.md), S-01 conventions ([S01_SignonMenu_migration_plan.md](S01_SignonMenu_migration_plan.md)).

## 1. Goal and scope
Port COCRDLIC (transaction CCLI) to the Java target with source parity for FR-S04-01..23. One program, one
wave. Downstream COCRDSLC/COCRDUPC are not implemented; they resolve through the S-01 route registry
(`MenuService` `UI_ROUTES`).

**Baseline position (FACT, verified this engagement):** `spring-boot` already contains a partial port:
`CardController` `GET /api/cards` + `CardService` (offset `Pageable`, `COBOL_PAGE_SIZE=7`, verbatim catalogue
messages `CARD_ACCOUNT_FILTER_INVALID` / `CARD_FILTER_INVALID` / `NO_MORE_RECORDS` / `NO_PREVIOUS_PAGES`,
`CardListRow` DTO carrying `select S`/`U` codes and detail/update URLs). `MenuService` registers
`UI_ROUTES["COCRDLIC"] = "/api/cards"` (implemented option 03 → API JSON). The wave closes the remaining
parity gap: keyed browse state machine (anchors, look-ahead/look-behind, page number, last-page-shown),
S/U selection processing, AID dispatch (ENTER/PF3/PF7/PF8, unmapped→ENTER per S04-B3), verbatim message
completion, and the Thymeleaf `card-list.html` screen at `/cards/list` via the `ui/` web surface.

## 2. Target-state mapping
Profiles applied: **CORE + ONLINE + DATA/BOUNDARY** from `CardDemo_target_state.md`.

| Legacy | Target |
|---|---|
| CICS transaction CCLI / pseudo-conversation with `WS-THIS-PROGCOMMAREA` | `GET /api/cards` + `POST /api/cards/list` — one call per AID press; the paging COMMAREA becomes the explicit flow-state record `CardListPageState` echoed back by the client (stateless server, server-session auth) |
| `COCRDLIC` procedure (edits, dispatch EVALUATE, message setup) | `com.carddemo.service.CardService` — pure browse state machine over `CardRepository`, extended from the existing baseline |
| STARTBR/READNEXT/READPREV on CARDDAT | additive `CardRepository` verbs: key-ordered forward browse (`findByCardNumberGreaterThanEqual` + filters, limit 8 for look-ahead), backward browse (descending + limit 8); read-only |
| XCTL COCRDSLC / COCRDUPC | `MenuService` `UI_ROUTES` lookup by program key → navigate (route present) or registry "not installed" idiom (absent) |
| XCTL COMEN01C | outcome `exit`; UI redirects to `/menu` |
| Map CCRDLIA | Thymeleaf `templates/card-list.html` (extends `layout.html`), route `GET/POST /cards/list` in `com.carddemo.ui` web surface, session-guarded by `SecurityConfig` |
| AID keys | hidden `aid` form field + F-key JS from `layout.html`: ENTER/PF3/PF7/PF8 honoured; all other AIDs remapped to ENTER by the service (FR-S04-18 / S04-B3) |
| COMMAREA round-trip | `pageState` fields round-tripped as hidden form fields / request record |

## 3. Boundary decision table (decide mode over S04-B1..B4)
| ID | Class | Decision | Seam in target | Error/idempotency | Owner | Lead-time request | Routing point / cutover |
|---|---|---|---|---|---|---|---|
| S04-B1 | XCTL hand-off (off-stream targets COCRDSLC/CCDL, COCRDUPC/CCUP) | **DECIDED — registry-resolved hand-off.** S → card detail route, U → card update route, resolved via `MenuService` `UI_ROUTES` | `CardService` emits `CardListNavigationTarget{accountId, cardNumber, action S|U}`; the `ui/` controller redirects when the target route exists, else renders the registry not-installed idiom on the list screen | no write; idempotent | S-04 implements the seam; targets owned by S-05/S-06 | none — internal seam | routing point = `UI_ROUTES` entries for COCRDSLC/COCRDUPC; cutover when S-05/S-06 merge their UI routes |
| S04-B2 | behavioural quirk | **DECIDED — parity.** On selection error the service re-lists from the file start (browse key reset to spaces) while keeping the page number; flagged to the owner | `CardService` selection-error branch issues a fresh browse from first key | read-only, idempotent | stream | none; owner flag recorded as risk | cutover optional at integration stage — owner may elect re-list from current first anchor |
| S04-B3 | AID policy | **DECIDED — parity.** Any unmapped AID treated as ENTER; the S-01 invalid-key helper is **not** applied to this screen | `CardService` AID normaliser: only PF3/PF7/PF8 branch; everything else falls through to ENTER | read-only, idempotent | stream | none | n/a |
| S04-B4 | file-error surfacing | **DECIDED — parity of message layout**, RESP `20` / RESP2 `90` (`File Error: READ     on CARDDAT   returned RESP 000000020 ,RESP2 000000090`) on READPREV exhaustion | `CobolMessages` constant + `CardService` backward-exhaustion branch (partial rows bottom-aligned, first anchor unchanged) | read-only, idempotent | stream | none | n/a |

DATA/BOUNDARY note: CARDDAT browse is part of the module-level persistence register — `cards` keyed browse maps
to `CardRepository` key-ordered Spring Data queries; no SP, no external service, no lead-time requests.

## 4. Data and persistence
No schema change expected. `cards` (`card_number varchar(16) PK`, `card_acct_id bigint`, `card_active_status`)
exists from the V2 baseline and satisfies every access path; filter predicates push down as equality on
`card_acct_id` / `card_number`; seeded card numbers are all digits so `ORDER BY card_number` matches the VSAM
key order under any collation. **Reserved Flyway range V120x (V1200–V1209)** — used only if a genuine
schema gap appears during the wave (ALTER, never re-create); otherwise untouched.

## 5. Phase 0 scaffolding deltas
**None.** S-01 already built the Phase 0 shell this stream needs: `layout.html` fragment (Tran/Prog/date/time
header, `message-line`), `carddemo.css`, `UiController` aid-dispatch idiom, server-session auth seam,
`MenuService` route registry, `CobolMessages` catalogue, Flyway + Postgres + Testcontainers CI. The wave
reuses all of it and only adds the stream's own pieces.

## 6. Waves (from analysis DAG, leaf-first; one child session per wave, sequential)
| Wave | Programs/seams | Repo area | Consumes FR docs | Boundary seams | Strict edge to next |
|---|---|---|---|---|---|
| 1 | COCRDLIC — `CardService` full parity state machine + `CardController` extension; additive `CardRepository` browse verbs; `ui/` card-list controller + `templates/card-list.html`; `CobolMessages` verbatim additions; `UI_ROUTES["COCRDLIC"]="/cards/list"` flag flip | `spring-boot` (single repo) | `programs/COCRDLIC_functional_requirement.md` | S04-B1, B2, B3, B4 | none — leaf stream |

Wave 1 work items (all in one PR): replace offset `Pageable` with keyed browse + `CardListPageState` round-trip;
selection edit + S/U dispatch; AID normalisation; verbatim messages
(`PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE`, `INVALID ACTION CODE`,
`NO RECORDS FOUND FOR THIS SEARCH CONDITION.`, `NO MORE PAGES TO DISPLAY`,
`TYPE S FOR DETAIL, U TO UPDATE ANY RECORD`, file-error layout); Thymeleaf screen (filters, 7 rows, page
number, info/error lines, footer `F3=Exit F7=Backward F8=Forward`, protected Select on empty/error states);
menu flag flip so option 03 routes to `/cards/list`.

## 7. Per-program FR generation
`programs/COCRDLIC_functional_requirement.md` already exists and is updated to Java target refs in this
artifact set — no generation step needed.

## 8. Testing and verification
- Unit: `spring-boot/src/test/java/com/carddemo/service/CardListServiceTest.java` — fake `CardRepository`
  reproducing key-ordered browse verbs; every FR-S04 row.
- Integration: `spring-boot/src/test/java/com/carddemo/CardListIntegrationTest.java` — Testcontainers
  PostgreSQL 16 seeded from `app/data/ASCII/carddata.txt`; browse semantics both directions, filters,
  backward exhaustion (B4), S/U navigation payloads, 401/session guard.
- UI: `spring-boot/src/test/java/com/carddemo/CardListUiIntegrationTest.java` — MockMvc over
  `card-list.html`; layout, messages, AID posts, protected/echoed fields.
- Stream e2e: sign-on → main menu option 03 → list → filter + PF7/PF8 → S/U dispatch → PF3 back to menu.
- Optional `!mf_online_ui_testing` recorded pass (UI-bearing stream).
- CI: `mvn -q clean verify` green on the wave PR.

## 9. Sign-off gate
`!mf_stream_signoff` over FR-S04-01..23 acceptance criteria: every row covered by a green test; verbatim
messages byte-equal; downstream programs still behind the registry; menu flag flipped; docs cite source lines.

## 10. Risks
- S04-B2 stays flagged: the selection-error re-browse-from-start is ported for parity but is a deliberate
  mainframe defect lookalike; owner may rule to re-list from the current anchor instead.
- S04-B4 RESP2 `90` is from CICS docs, not source (`:1361-1369`); kept verbatim.
- Faithful quirks retained: PIC 9(1) page number (10→0 wrap), unfiltered look-ahead can announce an empty
  next page under a filter, PF8-after-last re-list.
- S/U hand-off targets (S-05/S-06) unmigrated at this wave — registry not-installed idiom shown until their
  `UI_ROUTES` entries exist.
- Baseline rework: offset `Pageable` in `CardService` is replaced by keyset browse; existing REST callers
  (`page`/`direction` params) are superseded by the COMMAREA-state request shape — noted for the wave owner.

## 11. Effort and sequencing
1 wave, one child session, single repo, single PR. No external lead times; sequenced after S-01 (done) and
before S-05/S-06 (which consume the S04-B1 seam).

## Validation
Waves match the analysis DAG (depth 1 → single wave; topological order trivially valid). Every FR-S04-01..23
is covered by wave 1. Every boundary has decision, seam, owner, and cutover flag where relevant; no external
lead-time requests required. Scaffolding deltas explicit (none — Phase 0 complete at S-01). Plan matches the
ONLINE surface profile (REST + Thymeleaf + server session) and cites it in §2. Shared programs: none owned by
this stream.
