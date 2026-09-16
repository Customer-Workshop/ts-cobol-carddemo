# S-05 Card View — Migration Plan (`!mf_stream_migration_plan`)

Status: DRAFT for STOP C approval (2026-09-16, Java engagement — supersedes the 2026-08-20
.NET draft). Inputs: `S05_card_view_analysis.md`, `S05_functional_requirement.md`,
`CardDemo_target_state.md` (CORE + ONLINE + DATA/BOUNDARY, all CONFIRMED at STOP A),
baseline = `spring-boot/` port adopted from the open baseline PR, extended by the merged
S-01 waves (`mvn clean verify` green, 69 tests, engagement HEAD `df05ebc`).

## 1. Goal and scope
Migrate the CardDemo card-detail inquiry (COCRDSLC; transaction CCDL, BMS map `CCRDSLA`) to
Java 21 + Spring Boot 3.4.5 + server-rendered Thymeleaf UI + PostgreSQL, in the single repo
(`spring-boot/`). Definition of done: FR-S05-01..16 pass. Hard stop: PF3 return to the
caller route (`returnUrl` else `/menu`) — COCRDLIC (S-04) is the only other caller and is
not this stream's scope. Process type: ONLINE.

**Baseline position (FACT, verified this engagement):** the adopted port already implements
the read path as a JSON API — `CardController` `GET /api/cards/{cardNumber}?accountId=` →
`CardService.detail` → `CardRepository.findById`, returning `CardResponse` (embossed name,
expiry `LocalDate`, active status, CVV). NOTFND → `CARD_COMBINATION_NOT_FOUND` =
`Did not find cards for this search condition` (already verbatim). What it lacks or
deviates on vs the source FRs — the deltas this stream closes:

- **No web UI**: no `card-view.html`, no `UiController` endpoints, `UI_ROUTES` has no
  COCRDSLC entry (menu option 04 lands on the REST list route today).
- **Cross-check divergence**: `CardService.detail` compares the supplied account against
  `card.cardAcctId` and returns NOT_FOUND on mismatch. The source reads **by card number
  only** and never compares (FR-S05-12 — a documented source defect kept verbatim). The
  card-view read must not cross-check; the update path (`update`, S-06's program) keeps
  its own semantics.
- **Edit shape**: the service collapses blank inputs to `null` and throws
  `NO_CHANGES_DETECTED` ("No change detected with respect to values fetched.") when both
  are missing — the source emits `No input received`, plus per-field `… not provided`
  messages and per-field flag/cursor state the exception path cannot express. Also
  `\d{1,11}` accepts a short account where the source requires exactly 11 digits.
- **Verbatim messages**: `Account number not provided`, `Card number not provided`,
  `No input received`, the info strings (`Please enter Account and Card Number` /
  `   Displaying requested details`), and the MSG-FILE-ERROR frame do not exist in
  `CobolMessages` yet.
- **Inbound card-list contract** (S05-B2): `/cards/view?accountId=&cardNumber=&returnUrl=`
  with edits skipped and inputs read-only — new surface for the UI.

## 2. Target-state mapping
Profiles applied: CORE + ONLINE + DATA/BOUNDARY (`CardDemo_target_state.md`).
- API: REST under `/api` (baseline, keep) — `GET /api/cards/{cardNumber}?accountId=` keeps
  its contract; its message bodies and the view path's key semantics tighten to the source
  (no cross-check on the view read; `update` unchanged).
- Conversational state (COMMAREA) → server-side HTTP session + Spring Security context
  (S01-B6, built). Per-screen state (field values, `*`/red flags, cursor target,
  card-list-context flag) rides as form fields/model attributes; `returnUrl` replaces
  `CDEMO-FROM-PROGRAM`/`LAST-MAPSET` plumbing; presence of both `accountId`+`cardNumber`
  replaces `FROM-PROGRAM = COCRDLIC` detection.
- UI: Thymeleaf screen `card-view.html` on the S-01 `layout.html` shell (header
  `CCDL`/`COCRDSLC`/titles/date/time, separate `INFOMSG`/`ERRMSG` lines, footer
  `ENTER=Search Cards  F3=Exit`). Thin `UiController` `GET /cards/view` (initial display or
  card-list auto-read) + `POST /cards/view` (submit with `accountId` + `cardNumber` + `aid`
  fields) over the same service path as the REST surface.
- Layers: Controller → Service → Repository; records for DTOs; JUnit 5 + AssertJ.
- Persistence: Spring Data JPA + Flyway (Postgres 16, Testcontainers in tests); H2 remains
  the fast unit-test profile.

## 3. Boundary decision table (decide mode over S05-B1..B4)
| ID | Class | Decision | Seam in target | Error/idempotency | Owner | Lead-time request | Routing point / cutover |
|---|---|---|---|---|---|---|---|
| S05-B1 | B4 data-access leaf | **Reuse `CardRepository.findById(cardNumber)`** — keyed by card number only, no account/xref check (source parity; cross-check deliberately not added). NOTFND ↔ empty `Optional` → `Did not find cards for this search condition`; other ↔ `RuntimeException` → file-error frame | `spring-boot/.../repository/CardRepository` + card-view service path | store failure → MSG-FILE-ERROR (S05-B4); reads idempotent | S-05 wave | none (Postgres is ours) | service layer is the strangler point; VSAM CARDDAT read path decommissioned at module cutover |
| S05-B2 | B5 inbound switch | **Route contract**: `/cards/view?accountId=&cardNumber=&returnUrl=` — when the caller supplies both keys the screen auto-reads with edits skipped and both inputs render read-only; S-04 emits this URL when it lands | `UiController` `/cards/view` GET + screen-state flag | missing/partial params → initial display (no error) | S-05 wave | none | contract published in this plan; S-04 consumes at its wave |
| S05-B3 | B5 outbound return | **Exit → `returnUrl` else `/menu`** (internal paths only — reject external `returnUrl` to keep the surface same-origin) | `UiController` PF3 branch | none needed (pure nav) | S-05 wave | none | — |
| S05-B4 | Runtime diagnostic | **Fixed technical-path rendering**: file-error frame verbatim (`File Error: READ     on CARDDAT   returned RESP …,RESP2 …`, X(75)) with RESP `000000017 ` / RESP2 `000000120 ` — the same IOERR convention S02-B2 fixed; Java has no per-exception RESP, so the prior "unsigned HResult" formulation is re-decided to the fixed pair | `CobolMessages` template + `UiController` `RuntimeException` catch | technical path only | S-05 wave | none | — |

All seams are inside the confirmed single repo + Postgres; **no external lead-time requests
exist for this stream** (recorded explicitly). No stored-procedure boundaries.

## 4. Data and persistence
- Table consumed (read-only, baselined by Flyway V2): `cards` (PK `card_num`, plus
  `card_acct_id`, `card_embossed_name`, `card_expiration_date`, `card_active_status`,
  `card_cvv_cd`). Field mapping per analysis §4: `X(n)` → `String`, `9(n)` →
  `Long`/`Integer`, `YYYY-MM-DD` → `LocalDate`.
- **No schema change needed** — every field the screen reads already exists; the reserved
  Flyway range **V130x stays unused**.
- Seeding: unchanged — `DataSeeder` loads `app/data/ASCII/carddata.txt` (e.g. card
  `0500024453765740` "Aniya Von", expiry `2023-03`, per FR-S05-09).

## 5. Phase 0 scaffolding deltas
**NONE.** S-01 built the module's Phase 0: Thymeleaf + `layout.html` shell, `UiController`
idiom, `aid` field convention, Postgres profile + `docker-compose.yml`, Flyway seam, CI
(`mvn -B clean verify` gate), session/security seam. S-05 reuses, never forks.

## 6. Waves (from analysis DAG, leaf-first; one child session per wave, sequential)
| Wave | Programs/seams | Repo area | Consumes FR docs | Boundary seams | Strict edge to next |
|---|---|---|---|---|---|
| 1 | COCRDSLC (single program): `card-view.html` + `UiController` `/cards/view` GET (initial or card-list auto-read) / POST (edits → read → redisplay); screen-state service path covering all outcomes (initial, per-field blank/invalid with `*`-red/echo/cursor rules, both-blank `No input received`, account-message-wins precedence, found, not-found, file-error, card-list read-only); verbatim `CobolMessages` additions/corrections; source-parity fix to the view read (card-number only, no account cross-check; `\d{11}` account edit); FR-S05-01..16 test matrix; `UI_ROUTES` COCRDSLC → `/cards/view` flag flip (menu option 04) | `spring-boot/` | COCRDSLC program FR | S05-B1, B2, B3, B4 | — (terminal wave) |

Single-repo topology: one PR into the engagement branch. The service changes are
source-parity corrections to the view path; the REST contract is unchanged and the card
update path (`update`, S-06) is untouched.

## 7. Per-program FR generation
Already produced by the prior engagement and carried over with Java terminology updates
(`programs/COCRDSLC_functional_requirement.md`, FR-S05-01..16 = COCRDSLC-01..16). Verified
complete against the analysis this session; no regeneration needed.

## 8. Testing and verification
- `!mf_program_parity_test`: JUnit/MockMvc tests mapped to FR-S05-01..16 — per-field
  blank/`*`/zero edits, exact-width numeric edits, both-blank override, message precedence,
  found/not-found, store-error frame, card-list entry (edits skipped, inputs read-only),
  foreign-account display (FR-S05-12), PF3 return incl. `returnUrl`, unmapped-AID-as-ENTER,
  layout. Service unit tests + `CardViewUiIntegrationTest`.
- Testcontainers Postgres integration: seeded `carddata.txt` cases (found card `0500024453765740`,
  unknown card `9999999999999999`, foreign-account combination).
- Stream E2E: sign-on → menu option 04 → `/cards/view` → search → PF3 back to `/menu`.
- CI regression gate: `mvn -B clean verify` green on the wave PR.
- UI-bearing: `!mf_online_ui_testing` recorded pass over the screen (STOP D window).

## 9. Sign-off gate
`!mf_stream_signoff` executes FR §8 acceptance criteria one-by-one against the running app;
every FR row gets pass/fail evidence; then an independent audit by a fresh session; STOP E
for merge authorization.

## 10. Risks
1. Baseline cross-check divergence (FR-S05-12): correcting `detail` to card-number-only is
   a deliberate parity fix — the account mismatch NOT_FOUND branch disappears from the view
   path. MEDIUM (touches a shared service; `update` unaffected but regression tests must
   pin both).
2. `NO_CHANGES_DETECTED` currently fires for the both-blank case — must become
   `No input received` on the view path; check no other stream's caller depends on the
   current text. LOW-MEDIUM.
3. Per-field flag/cursor/`*/clear` state matrix (analysis §3 "Screen state after
   processing") is the fiddliest part — eight interaction rules pinned by the FR matrix.
   MEDIUM.
4. Dead code / unreachable 88-levels listed in analysis §7.5 are not ported; audit must not
   flag their absence. LOW.
5. `returnUrl` is a navigation parameter — restrict to internal paths (open-redirect
   hygiene). LOW.

## 11. Effort and sequencing
Program FR already exists; 1 sequential wave child after STOP C. No external lead-time
waits. All Phase-0 dependencies satisfied by S-01. The card-list caller (S-04) consumes the
published `/cards/view` contract when its wave lands — no dependency on S-04.

## Validation
Waves match analysis DAG (single-program stream, single wave — topological trivially);
FR-S05-01..16 all covered by wave 1; all 4 boundaries decided in Java terms (0 deferrals,
0 undecided; S05-B4 re-decided from "unsigned HResult" to the fixed IOERR pair);
scaffolding deltas explicit (NONE, §5); ONLINE surfaces + UI verification mode per ONLINE
profile; shared seams (session, shell, repositories, CI) consumed from S-01, none
re-ported; `cards` table read-only, no ALTER.
