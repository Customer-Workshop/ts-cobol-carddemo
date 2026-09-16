# S-19 Pending Authorization View — Migration Plan (`!mf_stream_migration_plan`)

Stream: S-19 (ONLINE, greenfield). Analysis: `S19_pending_auth_view_analysis.md`; FR: `S19_functional_requirement.md`.
Target: `spring-boot/` — Java 21, Spring Boot 3.4.5, Thymeleaf, Postgres 16, Flyway (`ddl-auto=validate`).

## 1. Goal and scope

Port the pending-authorization view chain: menu option 11 → list screen (summary + 5-row detail list + paging) → detail screen → fraud mark/remove with AUTHFRDS journal. 3 programs, 2 waves. Scope is exactly the analysis pin; S-20 owns the producer/purge side of the same IMS segments, S-22 owns the MQ seam.

## 2. Target-state mapping

| Legacy | Target |
|---|---|
| COPAUS0C/CPVS + map COPAU0A | `PendingAuthService` + `GET /api/pending-auth*` + `pending-auth.html` |
| COPAUS1C/CPVD + map COPAU1A | detail endpoint + `pending-auth-detail.html` |
| COPAUS2C (LINK) | `AuthFraudService` bean (same JVM, same tx) |
| IMS PAUTSUM0/PAUTDTL1 | `pending_auth_summary`/`pending_auth_detail` + JPA |
| DB2 CARDDEMO.AUTHFRDS | `auth_frauds` + JPA |
| COMMAREA ext CDEMO-CPVS-INFO | Spring session + keyset cursor |
| COMEN01C INQUIRE guard | `MenuService` implemented flag + UI_ROUTES |

## 3. Boundary decision table (S19-B1..B8 — all DECIDED in analysis §5)

| Decision | Seam | Error/idempotency | Owner | Lead time | Routing cutover |
|---|---|---|---|---|---|
| Menu option 11 flag flip + UI route | S19-B1 | n/a — flag already exists in `MenuService` (:46), flips `false→true` | this wave 2 | none | merges with screen |
| Screen-to-screen nav for select/fraud | S19-B2,B7 | authKey param; POST /fraud is idempotent (re-run = update) | wave 2 | none | session-backed |
| PAUTSUM0/PAUTDTL1 → 2 JPA tables + repos | S19-B3 | complement-key order; 'GE/GB'→empty; REPL→save | wave 1 | V2601 | n/a |
| AUTHFRDS upsert (-803→UPDATE) | S19-B5 | duplicate-key on (card_num,auth_ts)→update | wave 1 | V2601 | n/a |
| VSAM reuse (CXACAIX/ACCTDAT/CUSTDAT) | S19-B6 | existing repos; `findByAcctId` direction needed on xref | wave 1 | none | n/a |
| Fraud txn | S19-B7 | single @Transactional: journal write + segment flag commit/rollback together | wave 1 | none | n/a |
| 20-deep page-key stack | S19-B8 | session list of page-start keys, or forward `?after=` | wave 2 | none | n/a |

## 4. Data and persistence

- **V2601__pending_auth_tables.sql**: `pending_auth_summary` (acct_id bigint PK + 10 cols), `pending_auth_detail` ((acct_id, auth_date_9c, auth_time_9c) PK, FK → summary, 25 cols), `auth_frauds` ((card_num, auth_ts) PK per `ddl/AUTHFRDS.ddl:28`, 26 cols). Column mapping = analysis §4.
- 9's-complement ordering: `ORDER BY auth_date_9c ASC, auth_time_9c ASC` = newest-first. Keep raw complement columns (ISRT order compatibility for S-20); expose a derived real timestamp in the DTO.
- AUTHFRDS `fraud_rpt_date` = `CURRENT_DATE` at write time.
- Seed fixture: a handful of summary/detail rows + one account/cust/card chain so screens render before S-20 exists.

## 5. Phase 0 scaffolding deltas

None beyond the shared shell (S-01 already provides session, menus, auth). Reuses `MenuService`/`UiController`/templates.

## 6. Waves

| Wave | Deliverables | Depends on | Tests |
|---|---|---|---|
| 1 (data) | V2601 migration; `PendingAuthSummary`/`PendingAuthDetail`/`AuthFraud` entities + repositories; `AuthFraudService` upsert (fraud journal + flag); xref `findByAcctId` if missing; seed data | — | repository ITs, upsert test (FR-S19-12) |
| 2 (online) | `PendingAuthService` (list+detail+page+fraud-toggle orchestration), `PendingAuthController` API, Thymeleaf screens, MenuService flag + UI_ROUTES | wave 1 | controller/service tests for FR-S19-01..11, UI walkthrough |

## 7. Per-program FR generation

`programs/COPAUS0C_functional_requirement.md` (list), `programs/COPAUS1C_functional_requirement.md` (detail+fraud), `programs/COPAUS2C_functional_requirement.md` (journal). All written.

## 8. Testing and verification

- Unit: service-level tests for paging order (complement key), A/D derivation, fraud toggle both directions, -803→update retry.
- Integration: MockMvc/`@SpringBootTest` over `/api/pending-auth*` against seeded Postgres.
- UI: browser pass over option 11 → list → select → F5 → F8 → F3.

## 9. Sign-off gate

S-19 sign-off per `.migration/05_progress.md` procedure: all FR-S19-nn mapped to tests passing; evidence in `functional/CARDDEMO/evidence/`.

## 10. Risks

1. First-lander conflict with S-20 over the pending-auth entities — whichever wave-1 lands first owns V26/27 schema; second reuses (analysis §6 note). MEDIUM.
2. Complement-key ordering bugs produce silent wrong-order lists — cover with an order test. MEDIUM.
3. xref-by-acct direction: multiple cards per account collapse to first hit, same as source. LOW.

## 11. Effort and sequencing

Two waves, sequential; wave 1 ~half session (schema+repos+service), wave 2 ~one session (screens+flags+tests). Can run parallel to S-20/S-22 but must sequence against S-20 wave 1 for entity ownership.

## Validation

(1) scope = analysis pin; (2) every boundary has a decision + owner + lead time; (3) every FR maps to a wave and a test; (4) Flyway range V260x respected; (5) no `.migration/` or `app/` writes.
