# S-08 Transaction View — Migration Plan (`!mf_stream_migration_plan`)

Status: DRAFT for STOP C approval (2026-09-16, Java engagement — supersedes the 2026-08-20
.NET draft). Inputs: `S08_tran_view_analysis.md`, `S08_functional_requirement.md`,
`CardDemo_target_state.md` (CORE + ONLINE + DATA/BOUNDARY, all CONFIRMED at STOP A),
baseline = `spring-boot/` port adopted from the open baseline PR, extended by the merged
S-01 waves (`mvn clean verify` green, 69 tests, engagement HEAD `df05ebc`).

## 1. Goal and scope
Migrate the CardDemo transaction-view screen (COTRN01C; transaction CT01, BMS map
`COTRN1A`) to Java 21 + Spring Boot 3.4.5 + server-rendered Thymeleaf UI + PostgreSQL, in
the single repo (`spring-boot/`). Definition of done: FR-S08-01..17 pass. Hard stop:
every transfer out of COTRN01C — sign-on/menu (S-01, migrated) and transaction list
COTRN00C (S-07, consumed via the route registry). Process type: ONLINE.

**Baseline position (FACT, verified this engagement):** the adopted port already implements
the fetch as a JSON API — `TransactionController` `GET /api/transactions/{transactionId}`
→ `TransactionService.detail` → `TransactionRepository.findById`, returning
`TransactionResponse`; `TRANSACTION_NOT_FOUND` = `Transaction ID NOT found...` is already
verbatim. What it lacks or deviates on vs the source FRs — the deltas this stream closes:

- **No web UI**: no `transaction-view.html`, no `UiController` endpoints, `UI_ROUTES` has
  no COTRN01C entry (option 07 lands on the REST detail route today).
- **Key handling divergence**: `requireTransactionId` demands `\d{1,16}` and zero-pads
  (`%016d`), while the source uses the entered id **verbatim** as the 16-byte key — no
  numeric edit, no upper-casing (FR-S08-12). Effect: a 3-digit entry or `abc...` is a
  400 in the baseline but a verbatim-key NOTFND in the source; `1234567890123456 ` (trailing
  space) and leading-space/lower-case keys behave differently too.
- **Missing verbatim messages**: `Tran ID can NOT be empty...` (blank id currently returns
  `TRANSACTION_ID_INVALID`) and `Unable to lookup Transaction...` (no store-error message)
  don't exist in `CobolMessages`; `TRANSACTION_ID_INVALID` text also drifts (`Tran ID must
  be Numeric...` vs source `Tran ID must be Numeric ...`).
- **AID table**: ENTER/PF3/PF4/PF5 + invalid-key behavior is entirely UI-side — PF4 clears,
  PF5 resolves option 06 through the registry (coming-soon until S-07 lands), PF3 honors
  `returnUrl` else `/menu`.
- **Inbound contract**: `/transactions/view?tranId=<id>` must pre-fill and fetch on render
  (S08-B2 — consumed by S-07's row selection when it lands; reachable today via menu opt 07).
- **Field-derivation formatting**: `+99999999.99` amount edit (9th-digit drop), first-10-char
  `yyyy-MM-dd` dates, 60/30/25 truncations — new formatter on the screen path.

## 2. Target-state mapping
Profiles applied: CORE + ONLINE + DATA/BOUNDARY (`CardDemo_target_state.md`).
- API: REST under `/api` (baseline, keep) — `GET /api/transactions/{transactionId}` stays;
  the verbatim-key behavior belongs to the screen path (the REST edit is a documented
  baseline API simplification — see risk 4).
- Conversational state → server-side HTTP session + Spring Security context (S01-B6,
  built). `CDEMO-CT01-TRN-SELECTED` → `tranId` query param; `CDEMO-FROM-PROGRAM` →
  `returnUrl` query param (internal paths only); the unused paging members of
  `CDEMO-CT01-INFO` are not carried (analysis §4).
- UI: Thymeleaf screen `transaction-view.html` on the S-01 `layout.html` shell — `View
  Transaction` title, header `CT01`/`COTRN01C`/titles/date/time, `Enter Tran ID:` input,
  13 display fields, red ERRMSG, footer `ENTER=Fetch  F3=Back  F4=Clear  F5=Browse Tran.`.
  `UiController` `GET /transactions/view?tranId=&returnUrl=` + `POST /transactions/view`
  (`aid`, `trnIdIn`, `returnUrl` fields).
- Layers: Controller → Service → Repository; records for DTOs; JUnit 5 + AssertJ.
- Persistence: Spring Data JPA + Flyway (Postgres 16, Testcontainers in tests); H2 for
  fast unit tests.

## 3. Boundary decision table (decide mode over S08-B1..B4)
| ID | Class | Decision | Seam in target | Error/idempotency | Owner | Lead-time request | Routing point / cutover |
|---|---|---|---|---|---|---|---|
| S08-B1 | B4 data-access leaf | **`TransactionRepository.findById` with the verbatim key** — trailing spaces stripped (matching `CobolFieldReader` seed import); no numeric edit, no zero-pad, no case fold; empty `Optional` → `Transaction ID NOT found...`; exception → `Unable to lookup Transaction...` | `spring-boot/.../service/TransactionService.java` view path + `repository/TransactionRepository` | store failure → "other RESP" message; reads idempotent, no lock (`READ UPDATE` never rewritten — dropped) | S-08 wave | none | service layer is the strangler point; VSAM TRANSACT read decommissioned at module cutover |
| S08-B2 | B5 inbound pre-selection | **`/transactions/view?tranId=<id>`** renders with the id pre-filled and fetched as if ENTER pressed; absent param → blank screen | `UiController` GET handler | none (pure nav) | S-08 wave (consumed by S-07) | none | S-07's row selection navigates here once its wave merges |
| S08-B3 | B5 outbound return | **`returnUrl` query param (internal path only) → PF3 redirects there, default `/menu`** (S01-B3 idiom) | `UiController` PF3 branch + template hidden field | missing/foreign → `/menu` | S-08 wave | none | — |
| S08-B4 | B5 cross-program switch | **PF5 resolves option 06 (COTRN00C) through `MenuService`/`UI_ROUTES`** — not browsable → coming-soon message, screen retained; browsable → navigate to the list route | `UiController` PF5 branch + `MenuService.uiRoute` | none | S-08 wave | none | S-07's wave adds the `UI_ROUTES` entry; legacy XCTL decommissioned at module cutover |

All seams are inside the confirmed single repo + Postgres; **no external lead-time requests
exist for this stream** (recorded explicitly). No stored-procedure boundaries.

## 4. Data and persistence
- Table consumed (read-only, baselined by Flyway V2): `transactions` — full field mapping
  per analysis §4 (`String`/`BigDecimal`/`LocalDateTime`).
- **No schema change needed** — the reserved Flyway range **V160x stays unused**.
- Seeding: unchanged — `DataSeeder` loads `app/data/ASCII/dailytran.txt` (keys
  trailing-space-stripped by `CobolFieldReader`, matching the verbatim-key decision).

## 5. Phase 0 scaffolding deltas
**NONE.** S-01 built the module's Phase 0: Thymeleaf + `layout.html` shell, `UiController`
idiom, `aid` field convention, Postgres profile + `docker-compose.yml`, Flyway seam, CI
(`mvn -B clean verify` gate), session/security seam. S-08 reuses, never forks.

## 6. Waves (from analysis DAG, leaf-first; one child session per wave, sequential)
| Wave | Programs/seams | Repo area | Consumes FR docs | Boundary seams | Strict edge to next |
|---|---|---|---|---|---|
| 1 | COTRN01C (single program): `transaction-view.html` + `UiController` `/transactions/view` GET/POST; verbatim-key fetch (no numeric/pad edit — the S08-B1 fix); `?tranId=` pre-fill+fetch and `?returnUrl=` contracts; PF4 clear, PF5 registry resolution, invalid-key `aid` handling; 13-field screen-state record with `+99999999.99` / `yyyy-MM-dd` / 60-30-25 formatting; verbatim `CobolMessages` additions/corrections (`Tran ID can NOT be empty...`, `Unable to lookup Transaction...`, `Tran ID must be Numeric ...` spacing); clear-before-read + details-preserved-on-blank behaviors (FR-S08-04/05); FR-S08-01..17 test matrix; `UI_ROUTES` COTRN01C → `/transactions/view` flag flip (menu option 07) | `spring-boot/` | COTRN01C program FR | S08-B1..B4 | — (terminal wave) |

Single-repo topology: one PR into the engagement branch.

## 7. Per-program FR generation
Already produced by the prior engagement and carried over with Java terminology updates
(`programs/COTRN01C_functional_requirement.md`, FR-S08-01..17 = COTRN01C-01..17). Verified
complete against the analysis this session; no regeneration needed.

## 8. Testing and verification
- `!mf_program_parity_test`: JUnit/MockMvc tests mapped to FR-S08-01..17 — the FR §8
  acceptance criteria verbatim (each Given/When/Then row becomes a named test), incl. the
  seeded-row golden result (`0000000000683580` → all 13 fields), the `+99999999.99` amount
  cases (`-919.00` → `-00000919.00`, `123456789.12` → `+23456789.12`, `0` → `+00000000.00`),
  and the verbatim-key cases (lower-case/leading-space id → NOTFND when only a differently
  keyed row exists — the case the baseline's numeric edit breaks).
- Testcontainers Postgres integration: seeded `dailytran.txt` lookup end-to-end.
- Stream E2E: sign-on → menu option 07 → `/transactions/view` → fetch → PF5 (coming-soon
  until S-07 lands, then `/transactions/list`) → PF3 honoring `returnUrl` → PF4 clear.
- CI regression gate: `mvn -B clean verify` green on the wave PR.
- UI-bearing: `!mf_online_ui_testing` recorded pass over the screen (STOP D window).

## 9. Sign-off gate
`!mf_stream_signoff` executes FR §8 acceptance criteria one-by-one against the running app;
every FR row gets pass/fail evidence; then an independent audit by a fresh session; STOP E
for merge authorization.

## 10. Risks
1. Amount edit picture drops the 9th integer digit (|amt| ≥ 100,000,000.00) — reproduced
   exactly; no seed row hits it. LOW (carried from analysis).
2. PF5 target not yet migrated — coming-soon until S-07 lands. LOW (by design).
3. Display truncations (60/30/25) are 3270-real-estate artifacts reproduced for parity —
   flag for the STOP D UX pass. LOW (carried).
4. **Verbatim-key vs baseline REST edit**: the screen path must look up by the entered id,
   but `GET /api/transactions/{id}` keeps `requireTransactionId`'s `\d{1,16}`+`%016d` —
   keep the REST edit as the API contract and give the screen its own verbatim path (the
   wave names it); don't silently tighten the REST route, which would change the
   baseline's published API. LOW-MEDIUM (the one real divergence decision).
5. `optionComingSoon` name-truncation quirk (name emitted `DELIMITED BY SPACE`, see FR §5)
   — shared constant used here for PF5; correction belongs with S-07's wave or a shared
   parity fix, coordinate to avoid two PRs touching it. LOW.
6. `READ UPDATE` lock drop — a mechanic, not observable; recorded in the FR (A1). LOW.

## 11. Effort and sequencing
Program FR already exists; 1 sequential wave child after STOP C. No external lead-time
waits. All Phase-0 dependencies satisfied by S-01. Ordering vs S-07: either order works —
S-07 consumes `/transactions/view?tranId=` (already specified, S08-B2); S-08's PF5 stays
coming-soon until S-07's `UI_ROUTES` entry lands.

## Validation
Waves match analysis DAG (single-program stream, single wave — topological trivially);
FR-S08-01..17 all covered by wave 1; all 4 boundaries decided in Java terms (0 deferrals,
0 undecided); scaffolding deltas explicit (NONE, §5); ONLINE surfaces + UI verification
mode per ONLINE profile; shared seams (session, shell, `TransactionRepository`, CI)
consumed from S-01/baseline, none re-ported; `transactions` table read-only, no ALTER;
hard stop respected (all outbound XCTLs are routes/registry, not implementations).
