# S-07 Transaction List — Migration Plan (`!mf_stream_migration_plan`)

Status: executed (2026-09-02). Inputs: `S07_tran_list_analysis.md`, `S07_functional_requirement.md`, `CardDemo_target_state.md` (CORE + ONLINE + DATA/BOUNDARY, CONFIRMED at STOP A), S-01 shell + Batch A shared data layer (commit 468e17d).

## 1. Goal and scope
Migrate COTRN00C (CT00) to the confirmed stack. Definition of done: FR-S07-01..21 pass. Hard stop: the COTRN01C hand-off stays behind the disabled route registry. Process type: ONLINE. Single program, single wave.

## 2. Target-state mapping
- API: `POST /api/v1/transactions/list` (`[Authorize]`) — one call per AID (ENTER / PF7 / PF8) carrying the screen inputs and the CT00 paging state; response carries message, replaced rows (or `null` = keep), new state, clear-search flag, and the resolved hand-off target. OpenAPI via existing Swagger.
- Layers: `TransactionsController` → `TransactionListService` (Application, port of PROCESS-ENTER-KEY / PF7 / PF8 / PAGE-FORWARD / PAGE-BACKWARD) → shared `ITransactionRepository` (Infrastructure). DTOs as records, explicit mapping (`TransactionListRow` from `Transaction`).
- Conversational state: `CDEMO-CT00-INFO` → `TransactionListState` record round-tripped by the client (no server session); user identity from the JWT (`SessionContext`).
- UI: standalone `TransactionListComponent` at `/transactions/list` (`authGuard`), Angular Material; 16-char search field, 10 rows × (1-char Sel, 16-char id, 8-char date, 26-char description, 12-char amount), 8-digit page, red message area, instruction/footer text; F3 = Exit to `/menu`, F7/F8 = paging, other F-keys = invalid-key parity.
- Menu registry: `Main[06] COTRN00C` stays `Enabled: false` (route left empty) — integration stage flips it.

## 3. Boundary decision table (S07-B1..B5)
| ID | Class | Decision | Seam in target | Error/idempotency | Owner |
|---|---|---|---|---|---|
| S07-B1 | B5 out (COTRN01C) | Resolve `ProgramKey=COTRN01C` in the route registry; disabled → coming-soon idiom (info), enabled → `navigate` + selected id | `TransactionListService` + `MenuRouteRegistryOptions` | pure nav | S-07 (consumer), S-08 flips flag |
| S07-B2 | B5 out (COMEN01C) | `router.navigateByUrl('/menu')` on F3/Exit | component | — | S-01 route contract |
| S07-B3 | B4 leaf (TRANSACT browse) | Shared `ITransactionRepository.BrowseAsync` (GTEQ + peek) / `BrowseBackwardAsync`; exceptions → `Unable to lookup transaction...` | Infrastructure (unchanged) | reads idempotent | Batch A layer |
| S07-B4 | B5 entry guard | `authGuard` → `/signin` | frontend/src/app/auth | — | S-01 |
| S07-B5 | B10 state contract | `TransactionListState(FirstTranId, LastTranId, PageNumber, NextPageAvailable)` | Application DTO + Angular component state | stateless server | S-07 |
No schema extension, no EF migration, no external lead time.

## 4. Data and persistence
Reads `transactions` (tran_id PK, collation "C") only. Seeds: `app/data/ASCII/dailytran.txt` via the shared importer (dev), synthetic rows in tests. No writes.

## 5. Wave
| Wave | Programs/seams | Repos | Consumes FR docs | Boundary seams |
|---|---|---|---|---|
| 1 | COTRN00C service/controller/component + tests | backend/, frontend/ | `programs/COTRN00C_functional_requirement.md` | S07-B1..B5 |

## 6. Testing and verification
- Unit (`TransactionListServiceTests`, fake repository): every service-owned FR row (02-17, 20, 21 ordering via fake key order), message text exact, state transitions, row formatting.
- Integration (`TransactionListIntegrationTests`, Testcontainers Postgres 16): real repository over seeded rows — first page, GTEQ search, byte ordering, PF8/PF7 round trip, bottom/top messages, NOTFND; API smoke through `WebApplicationFactory` with JWT (401 anonymous, 200 authorised).
- Frontend specs: FR-S07-01 (guard, existing), 02 (initial load), 05/06 (field lengths, row rendering), 11/13 (message area), 15 (selection payload), 18 (F3/Exit), 19 (invalid key), F7/F8 dispatch.
- Commands: `dotnet test backend/CardDemo.slnx`; `npx ng test --watch=false --browsers=ChromeHeadless`; `npm run build` (frontend/).

## 7. Risks
1. Preserved source quirks may read as defects to reviewers — documented in FR §7. LOW.
2. Route registry flag flip (integration stage) is the only remaining step to reach the screen from the menu. LOW.

## Validation
One wave covering FR-S07-01..21; all five boundaries decided against existing seams; no `.migration/` edits; no source edits; branch `devin/batch-a-s07-tran-list` off `devin/1787242078-carddemo-premigration`.
