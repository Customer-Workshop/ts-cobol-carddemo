# S-05 Card View — Migration Plan (`!mf_stream_migration_plan`)

Status: executed in Batch A (2026-09-02). Inputs: `S05_card_view_analysis.md`, `S05_functional_requirement.md`, `CardDemo_target_state.md`, S-01 shell conventions, shared data layer (commit 468e17d).

## 1. Goal and scope
Migrate COCRDSLC (transaction CCDL, main-menu option 04 "Credit Card View") to C#/.NET 8 + ASP.NET Core + Angular 18 + PostgreSQL in the single repo. Definition of done: FR-S05-01..16 pass. Hard stop: PF3 transfer back to the caller. Out of scope: COCRDLIC (S-04), COCRDUPC (S-06), dead account-path read.

## 2. Target-state mapping
- API: `GET /api/v1/cards/view?accountId=&cardNumber=&fromCardList=` (JWT `[Authorize]`, any user type). Response = screen state record: outcome, error message, info message, echoed account/card, per-field filter state (`valid|blank|notOk`), cursor field, card details or null. Store error → HTTP 500 with the same body (as `POST /auth/signin` does).
- Layers: `CardViewController` → `CardViewService` (`CardDemo.Application/Cards`) → shared `ICardRepository` (`CardDemo.Infrastructure/Persistence/CardRepository`). DTOs as records, explicit mapping in the controller.
- UI: standalone `CardViewComponent` at `/cards/view` (authGuard), Angular Material; BMS field lengths 11/16; info + error areas; Exit/F3; other F-keys behave as ENTER (`classifyAidKey` reuse).
- Menu registry: option 04 (`COCRDSLC`) stays `Enabled: false` in `appsettings.json`; the integration stage flips it and adds the `/cards/view` route target.

## 3. Boundary decision table
| ID | Class | Decision | Seam in target | Error/idempotency | Owner |
|---|---|---|---|---|---|
| S05-B1 | B4 leaf | **Reuse** shared `cards` table + `ICardRepository.GetByCardNumberAsync`; no schema change, no migration added | `CardRepository` | null → FR-S05-10; exception → FR-S05-11; reads idempotent | Batch A shared layer |
| S05-B2 | B5 in | Query-parameter contract `accountId`+`cardNumber` (+`returnUrl`) = COCRDLIC COMMAREA hand-off; `fromCardList=true` skips edits like `:339-348` | Angular route `/cards/view`, `CardViewComponent.ngOnInit` | — | S-05 (consumer S-04) |
| S05-B3 | B5 out | Exit → `returnUrl` else `/menu` (S01-B3 idiom) | `CardViewComponent.exit()` | — | S-05 |
| S05-B4 | diagnostic | File-error frame verbatim; RESP = unsigned HResult, RESP2 = 0 | `CardViewService.FileErrorMessage` | — | S-05 |
No stored procedures; no external lead times.

## 4. Data and persistence
No new tables, columns, indexes or EF migrations: every field COCRDSLC reads (`CARD-NUM`, `CARD-EMBOSSED-NAME`, `CARD-EXPIRAION-DATE`, `CARD-ACTIVE-STATUS`) already exists in `cards`. Integration tests seed from `app/data/ASCII/carddata.txt` via the shared `LegacyDataImportService`.

## 5. Waves
| Wave | Programs/seams | Repos | Consumes | Boundary seams |
|---|---|---|---|---|
| 1 | COCRDSLC: service + controller + Angular screen + tests | backend/, frontend/ | `programs/COCRDSLC_functional_requirement.md` | S05-B1..B4 |
Single wave; branch `devin/batch-a-s05-card-view` off `devin/1787242078-carddemo-premigration`.

## 6. Testing and verification
- Unit (`CardViewServiceTests`): edit order, message precedence, zero/`*` handling, found/not-found/store-error, from-card-list path, cursor/flag state — FR-S05-02..13.
- Integration (`CardViewIntegrationTests`, Testcontainers Postgres 16): seeded CARDDAT parity (`0500024453765740` → Aniya Von / 03 / 2023 / Y), unknown card, foreign-account read — FR-S05-09/10/12.
- API (`CardViewApiIntegrationTests`, WebApplicationFactory): 401 without token, 200 contract shape with token.
- Frontend (`card-view.component.spec.ts`): FR-S05-01, 13, 14, 15, 16 and rendering of the screen state for 02..11.
- Gates: `dotnet test backend/CardDemo.slnx`, `npx ng test --watch=false --browsers=ChromeHeadless`, `npm run build`.

## 7. Risks
1. Account not cross-checked against the card (source defect ported verbatim, FR-S05-12) — customer decision pending. MEDIUM.
2. AID semantics differ from S-01 (unmapped key = ENTER). LOW, documented.
3. S-04 must adopt the S05-B2 query contract. LOW.

## Validation
Single wave covers all 16 FRs; 4 boundaries decided; no schema delta; shared seams (JWT, registry, invalid-key helper, repositories) reused, none forked.
