# S-08 Transaction View — Migration Plan (`!mf_stream_migration_plan`)

Status: executed in batch A (2026-09-02). Inputs: `S08_tran_view_analysis.md`, `S08_functional_requirement.md`, `CardDemo_target_state.md` (CORE + ONLINE + DATA/BOUNDARY, CONFIRMED at STOP A), S-01 plan (conventions reused).

## 1. Goal and scope
Migrate COTRN01C (transaction CT01) to C#/.NET 8 + ASP.NET Core + Angular 18 + PostgreSQL 16 in the single repo. Definition of done: FR-S08-01..17 pass in `dotnet test backend/CardDemo.slnx` and `npx ng test`. Hard stop: XCTL targets (COSGN00C/COMEN01C from S-01, COTRN00C from S-07) are consumed as routes / registry entries, never re-implemented. Process type ONLINE. No batch, no schema delta.

## 2. Target-state mapping
- API: `GET /api/v1/transactions/view?tranId=<id>` (`[Authorize]`, JWT from S-01). Outcomes → HTTP: found 200 with screen-shaped DTO; blank id 400; not found 404; store error 500 — each error body `{ message }` carries the verbatim legacy message (same idiom as `POST /api/v1/auth/signin`).
- Layers: `TransactionViewController` → `TransactionViewService` (Application) → shared `ITransactionRepository` (Infrastructure, EF Core). `TransactionViewMapper` is the explicit mapper Domain.Transaction → `TransactionViewDetail` (13 screen fields, already edited/truncated as the COBOL MOVEs do, so parity is asserted server-side).
- UI: standalone `TransactionViewComponent` at `/transactions/view` (`authGuard`); Angular Material form field (maxlength 16) + read-only detail grid with map lengths; 78-char message area; buttons Enter / Back (F3) / Clear (F4) / Browse Tran. (F5); `window:keydown` AID handling reusing `classifyAidKey`/`MSG_INVALID_KEY` for the unmapped keys.
- Session/COMMAREA: S-01 `SessionContext` (JWT). `CDEMO-CT01-TRN-SELECTED` → query `tranId`; `CDEMO-FROM-PROGRAM` → query `returnUrl` (internal paths only, default `/menu`).
- Persistence: shared `transactions` table (landed with the data layer); read-only here.

## 3. Boundary decision table (decide mode over S08-B1..B4)
| ID | Class | Decision | Seam in target | Error/idempotency | Owner | Lead-time request | Routing point / cutover |
|---|---|---|---|---|---|---|---|
| S08-B1 | B4 leaf | **Port to shared Postgres read.** `ITransactionRepository.GetByIdAsync` (already landed); RESP 0/13/other → found / not-found / store-error results | `TransactionViewService` | store error → FR-S08-07 message + structured log; reads idempotent | S-08 | none | strangler point = view API; legacy CICS authoritative until stream sign-off |
| S08-B2 | B5 in | **Route contract** `/transactions/view?tranId=` replaces the COMMAREA pre-selection; auto-fetch on init | `TransactionViewComponent.ngOnInit` | invalid id → normal not-found path | S-08 (producer S-07 later) | none | S-07 navigates with `tranId` when it lands |
| S08-B3 | B5 out | **`returnUrl` query param** (S01-B3 idiom), default `/menu`; non-internal values ignored | component `back()` | — | S-08 | none | callers pass their own route |
| S08-B4 | B5 out | **Registry-resolved transfer**: F5 calls `POST /api/v1/menu/select {main, 06}` (option 06 = Transaction List/COTRN00C, `app/cpy/COMEN02Y.cpy`); disabled → coming-soon info message, no navigation | `MenuService.select` (S-01) | pure nav | S-08 (flag flip: integration stage / S-07) | none | flag `MenuRoutes:Main[06].Enabled` |
No stored procedures, no external systems, no lead-time requests fired (recorded explicitly). Route registry row 07 (`COTRN01C`) stays `Enabled=false` in this stream; the integration stage flips it and sets `Route=/transactions/view`.

## 4. Data and persistence
No new tables, columns, indexes or migrations. Field dictionary in analysis §4. Seed parity: `app/data/ASCII/dailytran.txt` via the shared `LegacyDataImportService` (300 rows) is the integration-test fixture.

## 5. Scaffolding deltas
None beyond new files: `CardDemo.Application/Transactions/TransactionView*.cs`, `CardDemo.Api/Controllers/TransactionViewController.cs`, `CardDemo.Tests/Transactions/*`, `frontend/src/app/transactions/*`, one route line in `app.routes.ts`, one DI line in `Program.cs`.

## 6. Waves
| Wave | Programs | Content | Exit criteria |
|---|---|---|---|
| 1 | COTRN01C | backend service/controller/mapper + tests; Angular component/route/specs | all S-08 tests green; `npm run build` green; registry flag untouched |

## 7. Per-program FR generation
`programs/COTRN01C_functional_requirement.md` (done before implementation).

## 8. Testing and verification
- Unit: `TransactionViewServiceTests` (blank / not-found / store-error / found / key verbatim), `TransactionViewMapperTests` (amount edit picture, date slice, truncations).
- Integration (Testcontainers `postgres:16`): `TransactionViewIntegrationTests` over the seeded transactions table; `TransactionViewApiIntegrationTests` over `WebApplicationFactory` + JWT + the same container (401 / 400 / 404 / 200).
- Frontend: `transaction-view.component.spec.ts` (one `it` per UI-owned FR), `app.routes.spec.ts` (guard on the new route).
- Commands: `dotnet test backend/CardDemo.slnx`; `npx ng test --watch=false --browsers=ChromeHeadless`; `npm run build`.

## 9. Sign-off gate
All FR-S08-01..17 covered by a named test (traceability §9 of the FR doc); zero behavioral deviations from source; registry flag not flipped; no `.migration/` edits (integration stage appends S08-B1..B4 to the register and the progress ledger).

## 10. Risks
Carried from analysis §7 (amount 9th-digit truncation reproduced; PF5 target pending S-07; 3270 truncations kept for parity, UX review at STOP D).

## 11. Effort and sequencing
Single session, single wave; no external waits.
