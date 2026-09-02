# S-02 Account View — Migration Plan (`!mf_stream_migration_plan`)

## 1. Goal and scope
Port `COACTVWC` (transaction `CAVW`) — read-only account/customer inquiry — to the confirmed target
(`CardDemo_target_state.md`): ASP.NET Core 8 API under `/api/v1` + Angular 18 standalone screen.
In scope: FR-S02-01..15 (`S02_functional_requirement.md`). Out of scope: Account Update (S-03), card
programs, any menu-flag change (integration stage), header date/time/APPLID plumbing.

## 2. Target mapping

| Legacy | Target |
|---|---|
| `CAVW` / `COACTVWC` | `GET /api/v1/accounts/view?accountId=<text>` → `AccountViewController` → `AccountViewService` (`CardDemo.Application/Accounts`) |
| `CXACAIX` READ by account | `ICardXrefRepository.GetFirstByAccountIdAsync` (shared, commit 468e17d) |
| `ACCTDAT` READ | `IAccountRepository.GetByIdAsync` |
| `CUSTDAT` READ | `ICustomerRepository.GetByIdAsync` |
| `1200-SETUP-SCREEN-VARS` | `AccountViewScreenMapper` (explicit mapper; `LegacyScreenFormat.EditedAmount` for `+ZZZ,ZZZ,ZZZ.99`) |
| Map `CACTVWA` | `AccountViewComponent` (`frontend/src/app/account-view`), route `/accounts/view` guarded by `authGuard` |
| PF3 XCTL (B-012) | `router.navigateByUrl('/menu')` |
| Other AIDs → ENTER | F-keys other than F3 call `submit()` (S02-B1) |
| COMMAREA | JWT `SessionContext` (S01-B6); the per-request screen state is the response DTO (stateless) |

API contract (all business outcomes are a redisplay in the source, so they are `200 OK` with the
screen DTO; the store-failure path returns `500` with the same DTO shape, as S-01 does for store
errors):

```
AccountViewResponse {
  outcome: 'initial'|'noInput'|'invalidFilter'|'accountNotInXref'|'accountNotInMaster'|'customerNotFound'|'found'|'storeError',
  accountId: string,            // screen echo ('*' for blank re-entry)
  accountFieldState: 'blank'|'invalid'|'valid',
  infoMessage: string, errorMessage: string,
  account: AccountDetails|null, customer: CustomerDetails|null   // pre-formatted screen strings
}
```

## 3. Boundary decisions
B-009 REUSE shared repositories (no migration needed — analysis §7). B-012 exit → `/menu`.
S02-B1 source AID behavior wins over the S-01 invalid-key convention (documented in FR-S02-12).
S02-B2 file-error RESP/RESP2 rendered as fixed `000000017 `/`000000120 ` (IOERR) — the only
behavioral generalization; technical path only.

## 4. Persistence
None added. Tables `accounts`, `customers`, `card_xref` from the shared layer; seed via the shared
importer (`app/data/ASCII/*.txt`).

## 5. Waves
Single wave: docs → backend (TDD: unit tests for edit/messages/formatting, Testcontainers integration
tests over seeded data) → frontend (component + specs) → gates.

## 6. Testing
- `dotnet test backend/CardDemo.slnx` — `AccountViewServiceTests` (unit, fake repositories),
  `LegacyScreenFormatTests`, `AccountViewIntegrationTests`
  (Postgres 16 Testcontainer, seeded ASCII data, real repositories, `WebApplicationFactory` for auth).
- `npx ng test --watch=false --browsers=ChromeHeadless` — `account-view.component.spec.ts` covers
  FR-S02-01..07, 11, 12, 13, 14 and asserts the `authGuard` on the route (FR-S02-15).
- `npm run build`.

## 7. Sign-off gates
All FR rows in `S02_functional_requirement.md` §4 green; no test weakened; menu flag for option 01
left disabled (integration stage sets `Route`/`Enabled`).

## 8. Risks
Exact-string messages with doubled spaces and CICS codes (mitigated by literal tests); amount picture
truncation (formatter unit test).

## 9. Effort
One session (single read-only program).
