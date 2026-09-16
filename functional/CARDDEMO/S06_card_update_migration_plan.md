# S-06 Card Update — Migration Plan (`!mf_stream_migration_plan`)

Status: executed in batch A (2026-09-02). Inputs: `S06_card_update_analysis.md`, `S06_functional_requirement.md`, `programs/COCRDUPC_functional_requirement.md`, `CardDemo_target_state.md` (CORE + ONLINE + DATA/BOUNDARY).

## 1. Goal and scope
Migrate COCRDUPC (transaction CCUP) to the confirmed stack on top of the shared data layer. Definition of done: FR-S06-01..29 pass. Hard stop: transfers to COMEN01C (S-01, `/menu`) and COCRDLIC (S-04, disabled registry entry). Process type ONLINE.

## 2. Target-state mapping
- API: `POST /api/v1/cards/update` — one endpoint that receives the AID key, the current screen state (the ported `WS-THIS-PROGCOMMAREA`: state + OLD image + typed NEW values) and returns the next screen (state, info/error message, displayed values, flagged fields, cursor, editability). Stateless, JWT-protected (`[Authorize]`, any user type).
- Layers: `CardUpdateController` → `CardUpdateService` (state machine + edits, exact messages) → `ICardRepository` (shared; extended with `RewriteAsync`). DTOs as records, explicit mapping; no AutoMapper.
- UI: `CardUpdateComponent` (standalone, Angular Material), route `/cards/update` guarded by `authGuard`; optional `acctId`/`cardNum` query parameters for the list-entry seam. Menu registry row 05 stays `Enabled: false` until the integration stage.
- Persistence: shared `cards` table, no new migration (all COCRDUPC fields already present in `CardConfiguration`).

## 3. Boundary decision table
| ID | Class | Decision | Seam in target | Error / idempotency | Owner |
|---|---|---|---|---|---|
| S06-B1 | B4 leaf | Shared Postgres `cards`; `GetByCardNumberAsync` for the read, new `RewriteAsync` = `SELECT … FOR UPDATE` + compare + `UPDATE` in one transaction | `CardRepository.RewriteAsync` | lock failure → FR-S06-24; save failure / invalid calendar date → FR-S06-25; read exception → FR-S06-12 template | S-06 |
| S06-B2 | B5 inbound | Query-parameter entry `/cards/update?acctId=&cardNum=`; return-to-list deferred to S-04 (screen resets, exit → `/menu`) | route + component | idempotent fetch | S-06 (re-entry: S-04 plan) |
| S06-B3 | B5 outbound | PF3 → `/menu` (S-01 stable route) | component | — | S-06 |
| S06-B4 | B10 shared data | `cards` single table; S-06 is the only online writer today | shared data layer | — | data-layer owner |

Deviations carried from FR §11: D1 (CVV and account id preserved on rewrite), D2 (CVV omitted from the change compare), D3 (date column rejects non-calendar dates via FR-S06-25). No stored procedures, no external lead times.

## 4. Data and persistence
No schema change. Row lock via Npgsql `FOR UPDATE`; EF Core tracked update inside the same transaction.

## 5. Waves
| Wave | Content | Repos |
|---|---|---|
| 1 | `RewriteAsync`, `CardUpdateService`, `CardUpdateController`, unit tests, Testcontainers integration tests | backend/ |
| 2 | `CardUpdateComponent` + route + specs | frontend/ |
Both waves delivered in branch `devin/batch-a-s06-card-update` (no PR, integration stage merges).

## 6. Testing and verification
- Unit: `CardUpdateServiceTests` — every FR-S06 row with a fake repository (messages, flags, cursor, state transitions, AID remap, deviation D1).
- Integration: `CardUpdateIntegrationTests` (Testcontainers `postgres:16`) — fetch, rewrite, concurrent change, lock failure, plus the HTTP contract (401 anonymous, 200 authorised) through `WebApplicationFactory`.
- Frontend specs: `card-update.component.spec.ts` — initial render, field lengths, F3/F5/F12/other keys, state-driven editability, query-parameter entry, reset after completion.
- Gate: `dotnet test backend/CardDemo.slnx`, `npx ng test --watch=false --browsers=ChromeHeadless`, `npm run build`.

## 7. Risks
1. AID convention differs from S-01 (unmapped keys act as ENTER) — documented in FR-S06-02; integration stage should not "fix" it to the S-01 helper. LOW.
2. Source defects D1 (CVV/account overwrite) — deviation must be confirmed at sign-off. MEDIUM.
3. Return-to-list edge (S06-B2) re-enters when S-04 lands. LOW.
