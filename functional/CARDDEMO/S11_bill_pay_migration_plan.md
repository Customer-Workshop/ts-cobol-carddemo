# S-11 Bill Payment — Migration Plan (`!mf_stream_migration_plan`)

> Java-engagement rewrite (2026-09-15): this plan supersedes the .NET plan for this stream. Structure and
> conventions follow `S01_SignonMenu_migration_plan.md`; the target stack is the confirmed Java 21 / Spring Boot
> 3.4.5 / Thymeleaf / PostgreSQL 16 baseline, not an assumed one.

Inputs: [S11_bill_pay_analysis.md](S11_bill_pay_analysis.md), [S11_functional_requirement.md](S11_functional_requirement.md), [CardDemo_target_state.md](CardDemo_target_state.md), S-01 conventions ([S01_SignonMenu_migration_plan.md](S01_SignonMenu_migration_plan.md)).

## 1. Goal and scope
Port COBIL00C (transaction CB00) to the Java target with source parity for FR-S11-01..20 plus approved
deviation D1 (blocking/atomic write path). One program, one wave.

**Baseline position (FACT, verified this engagement):** `spring-boot` already contains the write path:
`BillingController` `POST /api/billing/payments` + `BillingService.pay()` (`@Transactional`: account lookup,
balance > 0 check, `Y` confirm, first card via `CardXrefRepository`, `TransactionIdGenerator` =
`findTopByOrderByTranIdDesc` + 1 `%016d`, writes `02`/`0002`/`POS TERM`/`BILL PAYMENT - ONLINE`/merchant
`999999999`/`BILL PAYMENT`/`N/A`, subtracts balance). `MenuService` main-menu option 10 is `implemented`
with `UI_ROUTES["COBIL00C"]` → `/api/billing/payments`. The wave closes the remaining parity gap: confirm
edit set `{Y,y,N,n,blank}` + two-step prompt (`Confirm to make a bill payment...`), `N` clear path,
as-typed account-key compare (`%011d`, not numeric parse — `"123"` stays NOTFND), xref-not-found →
`Account ID NOT found...`, `SELECT … FOR UPDATE` lock (S11-B2), `Clock`-injected second-precision timestamp
with `.000000` micros (S11-B6), 23505 → `Tran ID already exist...`, full verbatim message set, green success
message, PF3/PF4/other-AID handling, `?accountId=` prefill + auto-ENTER (S11-B5), and the Thymeleaf
`bill-payment.html` screen at `/bill-payment`.

## 2. Target-state mapping
Profiles applied: **CORE + ONLINE + DATA/BOUNDARY** from `CardDemo_target_state.md`.

| Legacy | Target |
|---|---|
| CICS transaction CB00, pseudo-conversation | `POST /api/billing/payments` (REST) + `GET/POST /bill-payment` (Thymeleaf screen via `com.carddemo.ui`), server-session auth |
| `COBIL00C` procedure (edits, EVALUATE CONFIRMI, read/write paragraphs) | `com.carddemo.service.BillingService` — extended to the full PROCESS-ENTER-KEY order |
| READ UPDATE + REWRITE ACCTDAT | `AccountRepository` `SELECT … FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`) inside `@Transactional` |
| READ CXACAIX (first card for account) | `CardXrefRepository` first-by-account (ordered by card number) |
| STARTBR/READPREV/ENDBR TRANSACT (max key) | `TransactionIdGenerator` (`findTopByOrderByTranIdDesc` + 1, `%016d`) — exists |
| WRITE TRANSACT | `TransactionRepository.save`; 23505 → duplicate outcome |
| Map COBIL0A | Thymeleaf `templates/bill-payment.html` on `layout.html`; footer `ENTER=Continue F3=Back F4=Clear`; green message styling for success |
| AID keys | `aid` form field: ENTER/PF3/PF4 honoured; all other AIDs → `Invalid key pressed. Please see below...` redisplay (S-01 invalid-key convention — unlike S-04, this program does emit it) |
| XCTL COMEN01C / CDEMO-FROM-PROGRAM | UI redirect to `/menu` |

## 3. Boundary decision table (decide mode over S11-B1..B6)
| ID | Class | Decision | Seam in target | Error/idempotency | Owner | Lead-time request | Routing point / cutover |
|---|---|---|---|---|---|---|---|
| S11-B1 | data leaf (write) | **DECIDED** — `TransactionRepository.save` in the service transaction; Postgres unique-violation 23505 → `Tran ID already exist...`; other failure → `Unable to Add Bill pay Transaction...` | `BillingService` write step + `CobolMessages` constants | insert is the only row written before the balance update; whole call atomic per D1 | stream | none | n/a |
| S11-B2 | data leaf (update under lock) | **DECIDED** — `SELECT … FOR UPDATE` on `accounts` + balance rewrite in the same `@Transactional` scope; **deviation D1**: every post-confirm step blocks at first failure (source falls through) | `@Lock(PESSIMISTIC_WRITE)` finder + `save`; miss → `Account ID NOT found...` | lock held to commit; failure rolls back both writes | stream | none | n/a |
| S11-B3 | key allocation | **DECIDED** — `TransactionIdGenerator` = max+1 `%016d`; PK collision surfaces as the S11-B1 duplicate message (legacy DUPREC parity) | existing generator component | colliding retry is the user's re-submit, as on the mainframe | stream | none | n/a |
| S11-B4 | XCTL back | **DECIDED** — PF3 redirects to `/menu` (S01-B3 route contract); no COMMAREA | `ui/` controller `redirect:/menu` | idempotent navigation | stream | none | cutover at wave merge |
| S11-B5 | pre-selection input | **DECIDED** — optional `?accountId=` on `/bill-payment` pre-fills Acct ID and runs ENTER processing immediately | `ui/` controller entry param → service ENTER path | read-only prefill; idempotent | stream | none | n/a |
| S11-B6 | timestamp source | **DECIDED** — injected `java.time.Clock`; orig/proc timestamps = now at second precision with `.000000` micros | `BillingService` `Clock` bean | deterministic in tests via fixed clock | stream | none | n/a |

No stored procedures, no external systems, no lead-time requests.

## 4. Data and persistence
No schema change expected. `accounts` (`acct_id bigint PK`, `acct_curr_bal numeric(19,2)`), `card_xrefs`,
`transactions` (`tran_id varchar(16) PK`, `tran_amount numeric(19,2)`) all exist from V2. Digit `tran_id`s
order identically under varchar lexical and numeric compare, so max+1 is exact. `numeric(19,2)` holds
`S9(09)V99`; the high-order truncation quirk (TRAN-AMT loses the leading digit at |balance| ≥ 1e9) is
replicated in the service, not the column. **Reserved Flyway range V190x (V1900–V1909)** — ALTER only if a
genuine gap appears.

## 5. Phase 0 scaffolding deltas
**None.** Reuses the S-01 shell: `layout.html`/`carddemo.css`, `UiController` aid idiom, server-session
`SecurityConfig`, `MenuService` registry, `CobolMessages`, Flyway/Testcontainers CI.

## 6. Waves (from analysis DAG, leaf-first; one child session per wave, sequential)
| Wave | Programs/seams | Repo area | Consumes FR docs | Boundary seams | Strict edge to next |
|---|---|---|---|---|---|
| 1 | COBIL00C — `BillingService` full parity (edit order, two-step confirm, `N` clear, as-typed key compare, locked update, D1 atomicity, message set, Clock); `BillingController` kept/extended; `ui/` bill-payment controller + `templates/bill-payment.html`; `CobolMessages` additions; `UI_ROUTES["COBIL00C"]="/bill-payment"` flag flip | `spring-boot` (single repo) | `programs/COBIL00C_functional_requirement.md` | S11-B1, B2, B3, B4, B5, B6 | none — leaf stream |

## 7. Per-program FR generation
`programs/COBIL00C_functional_requirement.md` already exists and is updated to Java target refs in this
artifact set — no generation step needed.

## 8. Testing and verification
- Unit: `spring-boot/src/test/java/com/carddemo/service/BillingServiceTest.java` — every FR-S11 row,
  incl. the ≥1e9 TRAN-AMT truncation quirk and the confirm-edit order (Q → `Invalid value...` before any read).
- Integration: `spring-boot/src/test/java/com/carddemo/BillingIntegrationTest.java` — Testcontainers
  Postgres seeded from `app/data/ASCII`; locked read-modify-write, duplicate id → 23505 message, atomic
  rollback on injected write failure (D1 evidence), session guard 401.
- UI: `spring-boot/src/test/java/com/carddemo/BillPaymentUiIntegrationTest.java` — MockMvc over
  `bill-payment.html`; fields, footer, AID handling, green success line, `?accountId=` prefill.
- Stream e2e: menu option 10 → enter acct → balance shown + confirm prompt → `Y` → green success with
  tran id; PF3 back to menu.
- Optional `!mf_online_ui_testing` recorded pass (UI-bearing stream).
- CI: `mvn -q clean verify` green on the wave PR.

## 9. Sign-off gate
`!mf_stream_signoff` over FR-S11-01..20 acceptance criteria + explicit sign-off of deviation D1 (visible in
the FR doc §11); every row covered by a green test; verbatim messages byte-equal; menu flag flipped.

## 10. Risks
- D1 is a deliberate behavioural deviation (source fall-through defect) — must be visible at sign-off; the
  legacy message for the failing step is still surfaced.
- R2 concurrency on TRAN-ID allocation accepted: PK collision yields the legacy duplicate message.
- R3 account key compare kept as-typed (`"123"` does not match `"00000000123"`); on bigint `acct_id` the
  seam compares the typed value against the `%011d` rendering — non-numeric input → `Account ID NOT found...`.
- R4 high-order TRAN-AMT truncation replicated faithfully; flagged for business review.
- Baseline rework: `BillingService.pay()` currently requires a numeric id, has no two-step confirm, and no
  `FOR UPDATE` lock — all superseded by this wave; noted for the wave owner.

## 11. Effort and sequencing
1 wave, one child session, single repo, single PR. No external lead times; sequenced after S-01 (done).

## Validation
Waves match the analysis DAG (depth 1 → single wave; topological order trivially valid). Every FR-S11-01..20
is covered by wave 1. Every boundary has decision, seam, owner, and cutover flag where relevant; no external
lead-time requests required. Scaffolding deltas explicit (none — Phase 0 complete at S-01). Plan matches the
ONLINE surface profile (REST + Thymeleaf + server session) and cites it in §2. Shared programs: none owned by
this stream.
