# S-04 Card List — Migration Plan (`!mf_stream_migration_plan`)

Inputs: [S04_card_list_analysis.md](S04_card_list_analysis.md), [S04_functional_requirement.md](S04_functional_requirement.md), [CardDemo_target_state.md](CardDemo_target_state.md), S-01 conventions (`S01_SignonMenu_migration_plan.md`).

## 1. Goal and scope
Port COCRDLIC (CCLI) to the target stack with source parity for FR-S04-01..23. One program, one wave. Downstream COCRDSLC/COCRDUPC are not implemented; they resolve through the S-01 route registry.

## 2. Target-state mapping
| Legacy | Target |
|---|---|
| CICS transaction CCLI / pseudo-conversation with `WS-THIS-PROGCOMMAREA` | `POST /api/v1/cards/list` — one call per AID press; the paging COMMAREA becomes the explicit flow-state DTO `CardListPageStateDto` echoed back by the client (stateless server, JWT session) |
| `COCRDLIC` procedure (edits, dispatch EVALUATE, message setup) | `CardDemo.Application/Cards/CardListService.cs` (pure state machine over `ICardRepository`) |
| STARTBR/READNEXT/READPREV on CARDDAT | additive `ICardRepository` verbs: `BrowseForwardAsync`, `BrowseBackwardAsync`, `ReadNextAsync` (key-ordered SQL over `cards`, filters pushed down as equality predicates) |
| XCTL COCRDSLC / COCRDUPC | `MenuRouteRegistryOptions` lookup by `ProgramKey` → navigate (enabled) or coming-soon (disabled) (`MenuService` message idiom) |
| XCTL COMEN01C | outcome `exit`, route `/menu` |
| Map CCRDLIA | Angular standalone `CardListComponent` (`frontend/src/app/cards/`), route `/cards/list` with `authGuard` |
| AID keys | F3 → PF3, F7 → PF7, F8 → PF8, ENTER → ENTER, other F-keys sent as `PFnn` and remapped to ENTER by the service (FR-S04-18) |

## 3. Boundary decision table
| ID | Decision | Rationale |
|---|---|---|
| S04-B1 | DECIDED — registry-resolved hand-off | Same idiom as S01-B1; targets stay disabled until their streams land |
| S04-B2 | DECIDED — parity (browse from file start on selection error), flagged | Source-derived; no owner ruling yet |
| S04-B3 | DECIDED — parity (unknown AID = ENTER) | `COCRDLIC.cbl:370-380` |
| S04-B4 | DECIDED — parity of message layout, RESP 20 / RESP2 90 | Layout from `:153-171`; codes from CICS docs |

## 4. Data and persistence
No schema change. `cards` table (PK `card_num`, non-unique `ix_cards_card_acct_id`) from the shared data layer satisfies every access path. Repository additions are read-only and additive.

## 5. Waves
Wave 1 — COCRDLIC: backend service/controller/DTOs + unit tests + Testcontainers integration tests; frontend component/route/spec. Registry entry `MenuRoutes:Main[03]` gains `Route: /cards/list` but stays `Enabled: false` (the integration stage flips the flag).

## 6. Testing and verification
- Unit: `CardListServiceTests` over an in-memory `ICardRepository` fake reproducing key-ordered browse verbs — every FR.
- Integration: `CardListIntegrationTests` (PostgreSQL 16 Testcontainers, seeded from `app/data/ASCII/carddata.txt`, 50 cards, plus one three-card account) — repository browse verbs, end-to-end paging in both directions, filtering, backward exhaustion, hand-off; `CardsApiIntegrationTests` — `POST /api/v1/cards/list` over JWT (401 without a session), DTO round trip of the paging state.
- UI: `card-list.component.spec.ts` — layout lengths, messages, AID handling, navigation.
- Commands: `dotnet test backend/CardDemo.slnx`, `npx ng test --watch=false --browsers=ChromeHeadless`, `npm run build` (frontend/).

## 7. Sign-off gate
All FR-S04 rows have a green covering test; no downstream program implemented; registry flag untouched; docs cite source lines.

## 8. Risks
Single-digit page number and unfiltered look-ahead are faithful quirks (analysis §8); S04-B2 may be revisited by the owner.
