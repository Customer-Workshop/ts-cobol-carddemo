# S-09 Transaction Add — Migration Plan (`!mf_stream_migration_plan`)

Status: executed on branch `devin/batch-a-s09-tran-add` (2026-09-02). Inputs: `S09_tran_add_analysis.md`, `S09_functional_requirement.md`, `CardDemo_target_state.md` (CORE + ONLINE + SUBTRANSACTION + DATA/BOUNDARY), S-01 shell conventions, shared data layer on `devin/1787242078-carddemo-premigration`.

## 1. Goal and scope
Migrate CT02 / COTRN02C (add transaction) and the shared date utility CSUTLDTC to C#/.NET 8 + ASP.NET Core + Angular 18 + PostgreSQL. Definition of done: FR-S09-01..32 pass in backend unit + Testcontainers integration tests and Angular specs; `dotnet test`, `ng test`, `ng build` green. Hard stop: PF3 return to the S-01 menu shell; no downstream program is touched; the menu registry flag for option 08 stays **disabled** (integration stage flips it).

## 2. Target-state mapping
- API (`/api/v1`, OpenAPI): `POST /api/v1/transactions/add` = ENTER processing; `POST /api/v1/transactions/add/copy-last` = PF5 processing. Both take the full screen state (`TransactionAddScreenDto`, 14 fields as strings) and return the resulting screen state + message + severity + cursor field + outcome. PF4 (clear) and PF3 (back) need no server call — they are pure screen actions, as in the source.
- Auth: `[Authorize]` with the S-01 JWT (`SessionContext` claims); no admin gate (COBOL has none) → `authGuard` only.
- Layers: `TransactionAddController` → `TransactionAddService` (state machine, exact messages, cursor) → shared `ICardXrefRepository` + `ITransactionRepository` (extended additively). CSUTLDTC → `CardDemo.Domain.Dates.DateValidationService` (in-process, typed `DateValidationResult`, still exposes severity / message number / 80-byte result for S-10 parity).
- DTOs as records; explicit mapping `TransactionAddService.BuildRecord` screen → `Transaction` entity; `decimal` amount; `DateOnly` for the validated dates (stored as midnight `DateTime` per the shared schema, S09-B6).
- UI: standalone `TranAddComponent` at `/transactions/add` mirroring COTRN2A (14 inputs with BMS `maxlength`, hints `(-99999999.99)`/`(YYYY-MM-DD)`, red/green message area, ENTER / Exit(F3) / Clear(F4) / Copy Last(F5) buttons, keyboard F-keys, focus on `cursorField`).

## 3. Boundary decision table (S09-B1..B6)
| ID | Class | Decision | Seam in target | Error/idempotency | Lead-time request | Cutover |
|---|---|---|---|---|---|---|
| S09-B1 | B5 in/return | Route `/transactions/add` + `authGuard`; Exit → `/menu`; registry row 08 gets its route string, flag left off | `app.routes.ts`, `MenuRoutes.Main[07].Route` | none | none | integration flips `Enabled` |
| S09-B2 | B4 read leaf | Shared `ICardXrefRepository.GetFirstByAccountIdAsync` (CXACAIX) / `GetByCardNumberAsync` (CCXREF); null → NOTFND message; exception → store-error message | Application service | reads idempotent | none | — |
| S09-B3 | B4 browse+write leaf | Additive `ITransactionRepository.GetLastAsync()` (highest `tran_id`) and `AddAsync(Transaction)`; `DbUpdateException` with Npgsql `23505` → `DuplicateTransactionIdException` → `Tran ID already exist...`; other → `Unable to Add Transaction...` | `TransactionRepository` | not idempotent by design (each confirmed ENTER adds one row, as in source); race → DUPREC message | none | — |
| S09-B4 | B10 shared utility | Port once here; S-10 consumes `DateValidationService` | `CardDemo.Domain.Dates` | pure function | none | — |
| S09-B5 | B10 shared data | No schema change; all TRAN-RECORD fields exist in `transactions` | — | — | none | — |
| S09-B6 | B10 storage type | Dates stored as midnight timestamps; year 0000 rejected (D-2) | mapping in service | — | none | — |
No stored procedures, no external systems, no lead times. No EF migration needed (nothing missing for this stream).

## 4. Data and persistence
Uses the landed `transactions` (PK `tran_id` varchar(16)), `card_xref` (PK `xref_card_num`, index `ix_card_xref_xref_acct_id`). `GetLastAsync` = `ORDER BY tran_id DESC LIMIT 1` (READPREV from HIGH-VALUES). Seed data for tests: `app/data/ASCII` via the shared `LegacyDataImportService` (300 transactions, highest id known by query).

## 5. Waves
| Wave | Programs/seams | Repos | Consumes | Boundary seams |
|---|---|---|---|---|
| 1 | CSUTLDTC → `DateValidationService` + tests; `GetLastAsync`/`AddAsync` + duplicate mapping | backend/ | CSUTLDTC program FR | S09-B3, B4 |
| 2 | COTRN02C → `TransactionAddService`, models, controller, unit + integration tests | backend/ | COTRN02C program FR | S09-B1..B3, B6 |
| 3 | `TranAddComponent` + `TransactionAddService` (http) + route + specs | frontend/ | COTRN02C program FR §3 | S09-B1 |

## 6. Testing and verification
- Unit (`TransactionAddServiceTests`): in-memory fakes for both repositories; one test (or theory row) per FR-S09-02..24, 27, 28 including validation order, padded-field numeric semantics, amount normalisation, cursor field, id generation, duplicate/store-error mapping, copy-last truncations.
- Unit (`DateValidationServiceTests`): FR-S09-31/32 — valid, 2508, 2517, 2520, 2513, 2518, 2507, 80-byte layout, return code.
- Integration (Testcontainers Postgres, `TransactionAddIntegrationTests`): seeded ASCII data; account/card lookups; add writes highest+1 with the §6 mapping; success message; concurrent duplicate → DUPREC message; copy-last from the real highest transaction.
- API (`TransactionAddApiIntegrationTests`, WebApplicationFactory + Postgres): 401 without JWT; message parity over HTTP; 400 for over-length fields.
- Angular specs: FR-S09-01, 25, 26, 27 (F5 call), 29, 30, maxlengths, message severity classes, focus on cursor field, error fallback.
- Commands: `dotnet test backend/CardDemo.slnx`; `npx ng test --watch=false --browsers=ChromeHeadless`; `npm run build` (frontend/).
