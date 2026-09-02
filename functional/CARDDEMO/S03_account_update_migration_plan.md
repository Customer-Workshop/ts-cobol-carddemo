# S-03 Account Update — Migration Plan

Base: `devin/1787242078-carddemo-premigration` @ `468e17ded0be830785246d6e3cf2d4ede915f609` (shared data layer). Work branch:
`devin/batch-a-s03-account-update`. No PR/merge from this stream; the integration stage flips the `Account Update` registry flag.

## 1. Scope
| In | Out (stay disabled in `MenuRoutes`) |
|---|---|
| `COACTUPC` / `CAUP` / `CACTUPA`: lookup, edit rules, change detection, confirm, atomic account+customer update | `COCRDUPC`, `COCRDLIC`, `COCRDSLC` (card streams), any other menu option |

## 2. Target design (reuses S-01 layering; no new frameworks)
| Layer | Artefact | Notes |
|---|---|---|
| Domain | existing `Account`, `Customer`, `CardXref` | no schema change needed — every COACTUPC field already exists (analysis §4) |
| Application | `AccountUpdate/AccountUpdateFields` (record of screen-shaped strings = ACUP-OLD/NEW), `AccountUpdateEditRules` (1200-series edits, 1205 compare), `LegacyLookupCodes` (CSLKPCDY lists), `LegacyDateEdit` (CSUTLDPY), `AccountUpdateService` (lookup / validate / save), `IAccountUpdateRepository` | pure C#, no EF reference |
| Infrastructure | `Persistence/AccountUpdateRepository` | one transaction; `SELECT … FOR UPDATE NOWAIT` per row (READ UPDATE parity); `55P03` → lock failure; `DbUpdateException` → rollback + write failure |
| API | `AccountUpdateController` `[Authorize]` `api/v1/account-update` — `POST lookup`, `POST validate`, `POST save` | DTOs are records; explicit `AccountUpdateMapper` |
| Frontend | `account-update/account-update.component.ts` (standalone) + `account-update.service.ts`; route `accounts/update` with `authGuard` | mirrors CACTUPA lengths; state machine per analysis §3; AID keys via `shared/invalid-key.ts` |

API contract (stateless COMMAREA replacement, S03-B3):
```
POST /api/v1/account-update/lookup   { accountId }                      -> { outcome: search|details, infoMessage, errorMessage, fields? }
POST /api/v1/account-update/validate { accountId, original, updated }   -> { outcome: noChange|invalid|validated, infoMessage, errorMessage, invalidFields[] }
POST /api/v1/account-update/save     { accountId, original, updated }   -> { outcome: committed|lockError|updateFailed|changedByOther|noChange|invalid, infoMessage, errorMessage, invalidFields[], fields? }
```

## 3. Sequence
1. Docs (this folder) — done before code.
2. Backend TDD: edit-rule unit tests (FR-S03-02/03/08-22) → service/repository → Testcontainers Postgres 16 integration tests (FR-S03-04-07, 23, 25-29, 31, 34).
3. Frontend: component + Karma specs (FR-S03-01, 07, 23, 24, 30-33), route.
4. Gates: `dotnet test backend/CardDemo.slnx`, `npx ng test --watch=false --browsers=ChromeHeadless`, `npm run build`, `cobc` syntax check of the source, python `unittest` + `ruff` (repo-wide gates unchanged).

## 4. Risks and mitigations
| Risk | Mitigation |
|---|---|
| Legacy quirks (analysis §7) | recorded as deviations D1-D6 in the FR doc; tests assert the target behaviour |
| Lookup-table transcription | lists generated from `CSLKPCDY.cpy` by reading the copybook; unit tests on members/non-members |
| Stateless snapshot tampering | server re-runs compare + edits on save; concurrency check compares the client snapshot to stored rows, so a stale snapshot is rejected exactly like the COMMAREA one |
| Host port 5432 busy | Testcontainers maps a random port; no local Postgres needed |

## 5. Rollback
Feature is dark (registry flag false; route only reachable by URL behind `authGuard`). Reverting = dropping the branch; no schema migration is introduced.
