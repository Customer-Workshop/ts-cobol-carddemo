# S-11 Bill Payment (ONLINE) — Migration Plan (`!mf_stream_migration_plan`)

Inputs: `S11_bill_pay_analysis.md`, `S11_functional_requirement.md`, `CardDemo_target_state.md` (CONFIRMED at STOP A), S-01 shell (`S01_SignonMenu_migration_plan.md`) and the shared data layer at `468e17d`.

## 1. Goal and scope
Migrate COBIL00C (transaction CB00) to the confirmed stack. Definition of done: FR-S11-01..20 pass in xUnit (unit + Testcontainers Postgres) and Angular specs; `dotnet test backend/CardDemo.slnx`, `npx ng test --watch=false --browsers=ChromeHeadless`, `npm run build` green. Hard stop: menu integration (route registry flag for option 10 stays `Enabled: false`; flipped by the integration stage).

## 2. Target-state mapping
- API: `POST /api/v1/bill-payment` (`[Authorize]`, any user type). Request `{ accountId, confirm }` (screen fields as typed). Response `BillPaymentResponse { outcome, message, messageSeverity ('error'|'success'|'none'), currentBalance (14-char string or null), transactionId, cursorField ('accountId'|'confirm'), clearScreen }` — one round trip per ENTER, mirroring PROCESS-ENTER-KEY. Screen outcomes (including store errors, as in `MenuController`) return HTTP 200 with the outcome in the body; 401 without a JWT.
- Layers: `BillPaymentController` → `BillPaymentService` (all validation order, messages, derivations) → `IBillPaymentRepository` (stream-owned seam over the shared `CardDemoDbContext`) + shared `ICardXrefRepository`. DTOs are records; explicit mapping in the controller.
- UI: standalone `BillPaymentComponent` at `/bill-payment` guarded by `authGuard`; Angular Material form; field `maxlength` 11 / 1; balance rendered as the 14-char string; message area `role="alert"` red/green; buttons `Enter`, `Back (F3)`, `Clear (F4)`; `window:keydown` handles F3/F4 and delegates all other F-keys to the shared `classifyAidKey` (invalid-key parity).
- Session: JWT from S-01 (`SessionContext`); no COMMAREA.

## 3. Boundary decision table (S11-B1..B6)
| ID | Class | Decision | Seam | Error/idempotency |
|---|---|---|---|---|
| S11-B1 | B4 write | EF insert into `transactions` via `IBillPaymentRepository.AddTransactionAsync` | `CardDemo.Infrastructure.Persistence.BillPaymentRepository` | 23505 → `Tran ID already exist...`; other → `Unable to Add Bill pay Transaction...`; write is inside the DB transaction opened for S11-B2 |
| S11-B2 | B4 update | `GetAccountForUpdateAsync` runs `SELECT … FOR UPDATE` in an explicit DB transaction (READ UPDATE lock parity); `UpdateAccountAsync` saves and commits | same | 0 rows → `Account ID NOT found...`; other → `Unable to Update Account...`; rollback on any failure (deviation D1) |
| S11-B3 | key allocation | `MAX(tran_id)` (C collation = VSAM byte order) + 1 → `D16` | `GetLastTransactionIdAsync` | collision surfaces as S11-B1 duplicate |
| S11-B4 | routing | PF3 → Angular `/menu` | component | — |
| S11-B5 | pre-selection | `?accountId=` query param → prefill + immediate ENTER | component | — |
| S11-B6 | clock | `TimeProvider` (DI `TimeProvider.System`) → `yyyy-MM-dd HH:mm:ss.000000` | service | tests inject a fake |
No stored procedures; no lead-time requests; no schema extension needed (all fields/indexes exist in the shared layer).

## 4. Data and persistence
No new tables or migrations. Uses `accounts.acct_curr_bal`, `card_xref` (AIX index `ix_card_xref_xref_acct_id`), `transactions` (PK `tran_id`). Seed = shared legacy import.

## 5. Wave (single)
1. Docs (this set).
2. Backend: models/service/repository seam/controller; unit tests with in-memory fakes covering FR-S11-01..15 including the error catalogue; integration tests on Testcontainers Postgres covering the persisted paths (FR-S11-04, 07, 08, 09, 10, 11, 12, 13, 15) and 401 (FR-S11-19).
3. Frontend: component + spec (FR-S11-01..03, 06..08, 12, 16..18, 20), route with `authGuard`.
4. Verify commands; commit to `devin/batch-a-s11-bill-pay`; no PR (integration stage owns merge).

## 6. Testing and verification
- Unit: `CardDemo.Tests/BillPayment/BillPaymentServiceTests.cs` — exact messages, validation order (blank id before confirm check; confirm check before account read; balance check before xref), balance format, id allocation, amount truncation, atomic failure mapping.
- Integration: `CardDemo.Tests/BillPayment/BillPaymentIntegrationTests.cs` — real Postgres: not-found, nothing to pay, confirm prompt, no-xref, id allocation from seeded rows, successful payment persists transaction + zero balance, duplicate key → message with balance unchanged, 401 without token.
- UI: `bill-payment.component.spec.ts`.

## 7. Sign-off gate
FR table §4 fully covered; deviation D1 (blocking + atomic confirmed path) explicitly accepted at stream sign-off; flag flip for menu option 10 deferred to integration.

## 8. Risks
R1 D1 acceptance; R2 TRAN-ID allocation race (accepted, legacy-equivalent message); R3 no numeric edit on Acct ID (parity); R4 TRAN-AMT truncation ≥ 1e9 (parity, flagged).

## 9. Effort
One session (this one).
