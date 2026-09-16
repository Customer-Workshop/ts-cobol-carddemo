# S-21 Transaction-Type Maintenance — Migration Plan (`!mf_stream_migration_plan`)

Stream: S-21 (ONLINE + BATCH, greenfield code, existing tables). Analysis: `S21_tran_type_maintenance_analysis.md`; FR: `S21_functional_requirement.md`.
Target: `spring-boot/` — Java 21, Spring Boot 3.4.5, Thymeleaf, Spring Batch, Postgres 16, Flyway.

## 1. Goal and scope

Port the admin transaction-type maintenance: list screen with filters + flagged row updates/deletes (CTLI), single-record add/update/delete state machine (CTTU), the MNTTRDB2 apply job (COBTUPDT) and the TRANEXTR extract layouts. 3 programs + 2 IBM utility steps, 2 waves.

## 2. Target-state mapping

| Legacy | Target |
|---|---|
| COTRTLIC/CTLI + CTRLIA | `TranTypeService` (paged/filtered list, flag actions) + `/api/tran-types*` + `tran-types.html` |
| COTRTUPC/CTTU + CTRTUPA | maint endpoints + `tran-type-maint.html` with server-side change state |
| TRANSACTION_TYPE / TRANSACTION_TYPE_CATEGORY | existing `transaction_types`/`transaction_categories` entities |
| DB2 cursor browse | keyset pagination over `tran_type` PK order |
| DSNTIAC formatting | `Db2ErrorFormatter` (SQLSTATE→legacy message) |
| COBTUPDT + MNTTRDB2 | `tranTypeMaintJob` (INPFILE→Spring Resource) |
| TRANEXTR DSNTIAUL | `tranTypeExtractJob` (60-char layouts) |
| Menu opt 5/6 | `MenuService` ADMIN flags + UI_ROUTES |

## 3. Boundary decision table (S21-B1..B7 — DECIDED in analysis §5)

| Decision | Seam | Error/idempotency | Owner | Lead time | Routing cutover |
|---|---|---|---|---|---|
| Admin flags + UI routes | S21-B1 | idempotent | wave 1 | none | merges w/ screens |
| Screen nav (F2/PF3) | S21-B2 | state param on routes | wave 1 | none | n/a |
| transaction_types reuse + keyset paging | S21-B3 | +100/-911/-532 → DataAccessException→messages | wave 1 | V2801 (FK) | n/a |
| FK RESTRICT for delete guard | S21-B4 | -532→'Please delete associated child records first:' | wave 1 | V2801 | n/a |
| Maint job | S21-B5 | bad record→job FAILED (RC4); file param | wave 2 | none | admin job launch |
| Extract job | S21-B6 | exact 60-char layouts | wave 2 | none | n/a |
| Error formatter | S21-B7 | SQLSTATE→verbatim messages | wave 1 | none | n/a |

## 4. Data and persistence

- `V2801__tran_type_fk.sql`: `ALTER TABLE transaction_categories ADD CONSTRAINT fk_trcat_type FOREIGN KEY (tran_type_code) REFERENCES transaction_types (tran_type) ON DELETE RESTRICT`. No new tables — baseline V2 covers both.
- Zero-pad rendering for category codes (4-char) at the UI/response edge only; storage stays integer.
- Seed parity with `ctl/DB2LTTYP.ctl`/`DB2LTCAT.ctl` contents is already supplied by baseline seeds (verify during wave 1; gaps → V2802 seed delta).

## 5. Phase 0 scaffolding deltas

None — reuses S-01 shell, repositories, `BatchJobService`, `CobolMessages`.

## 6. Waves

| Wave | Deliverables | Depends on | Tests |
|---|---|---|---|
| 1 | V2801 FK; `Db2ErrorFormatter`; `TranTypeService` (list+filters+flag-flow; maint state machine); `/api/tran-types*`; Thymeleaf screens; MenuService flags + UI_ROUTES | — | service/controller tests for FR-S21-01..12 incl. -532/-911/+100 mapping |
| 2 | `tranTypeMaintJob` + `tranTypeExtractJob` | wave 1 (repos) | job ITs for FR-S21-13/14 incl. RC4 failure + golden layouts |

## 7. Per-program FR generation

`programs/COTRTLIC_functional_requirement.md`, `programs/COTRTUPC_functional_requirement.md`, `programs/COBTUPDT_functional_requirement.md` — all written. DSNTIAUL/DSNTIAC are IBM utilities (boundary rows, no FR files).

## 8. Testing and verification

- Unit: state-machine transitions table test; message-text assertions verbatim; keyset paging edges.
- Integration: controller tests over seeded types; job tests w/ temp INPFILEs and golden extracts.
- UI pass: admin menu → list → filter → flag update → F2 add → maint save/delete/cancel.

## 9. Sign-off gate

Per `.migration/05_progress.md`: FR-S21-nn green; evidence in `functional/CARDDEMO/evidence/`.

## 10. Risks

1. Flag-row + F10 precedence edge cases (covered FR-S21-05/06). MEDIUM.
2. Integer vs char(4) category codes in extract — extract writes raw `tran_category_code` padded to 4. LOW.
3. CTTU re-entry contexts (from COADM01C vs COTRTLIC) — `CDEMO-TO-PROGRAM` must round-trip correctly; test both entries. LOW.

## 11. Effort and sequencing

2 waves, sequential; wave 1 ~1 session (two screens + service), wave 2 ~half session (two small jobs). Independent of S-19/S-20/S-22.

## Validation

(1) scope = analysis pin; (2) boundaries decided w/ owner+lead time; (3) FRs → waves+tests; (4) Flyway V280x respected; (5) no `.migration/`/`app/` writes.
