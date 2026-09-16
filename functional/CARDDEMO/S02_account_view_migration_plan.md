# S-02 Account View — Migration Plan (`!mf_stream_migration_plan`)

Status: DRAFT for STOP C approval (2026-09-16, Java engagement — supersedes the 2026-08-20
.NET draft). Inputs: `S02_account_view_analysis.md`, `S02_functional_requirement.md`,
`CardDemo_target_state.md` (CORE + ONLINE + DATA/BOUNDARY, all CONFIRMED at STOP A),
baseline = `spring-boot/` port adopted from the open baseline PR, extended by the merged
S-01 waves (`mvn clean verify` green, 69 tests, engagement HEAD `df05ebc`).

## 1. Goal and scope
Migrate the CardDemo account-view inquiry (COACTVWC; transaction CAVW, BMS map `CACTVWA`)
to Java 21 + Spring Boot 3.4.5 + server-rendered Thymeleaf UI + PostgreSQL, in the single
repo (`spring-boot/`). Definition of done: FR-S02-01..15 pass. Hard stop: PF3 return
navigates to the caller route (`/menu`); the program has no other outbound XCTL. Process
type: ONLINE.

**Baseline position (FACT, verified this engagement):** the adopted port already implements
the stream's read path as a JSON API — `AccountController` `GET /api/accounts/{acctId}` →
`AccountViewService.view` → `CardXrefRepository.findByXrefAcctId` (first result = AIX
keyed-read parity) → `AccountRepository.findById` → `CustomerRepository.findById`, returning
`AccountViewResponse` (record) with SSN formatted `nnn-nn-nnnn`. What it lacks or deviates
on vs the source FRs — the deltas this stream closes:

- **No web UI**: no Thymeleaf `account-view.html`, no `UiController` endpoints, no menu
  route flag (`MenuService` option 01 exists; `UI_ROUTES` has no COACTVWC entry yet).
- **Verbatim messages**: `CobolMessages.xrefNotFound`/`accountNotFound`/`customerNotFound`
  emit the short `Resp:13 Reas:0`/`REAS:0` forms; the source (and FR §2 catalogue) require
  the 75-char STRING-truncated forms `Resp:000000013  Reas:0000`, `Resp: 000000013  REAS:0000000`
  with their doubled spaces. MSG-FILE-ERROR (`File Error: READ ...`) has no REST equivalent yet.
- **Outcome shape**: the service throws on every failure, so it cannot express FR-S02-06
  (customer missing → account block still displayed) or FR-S02-02 (blank input →
  `No input received`, distinct from the filter message). Blank/`*` currently falls into the
  filter message, and `\d{1,11}` accepts a short entry where the source requires exactly 11
  numeric digits (§4 edit #2).
- **Echo/red-field state** (account echo, `*` red on blank, red on filter fail) is UI-side
  state the REST surface does not carry.

## 2. Target-state mapping
Profiles applied: CORE + ONLINE + DATA/BOUNDARY (`CardDemo_target_state.md`).
- API: REST under `/api` (baseline, keep) — `GET /api/accounts/{acctId}` unchanged in
  contract; message bodies corrected to verbatim (shared `CobolMessages`).
- Conversational state (COMMAREA) → server-side HTTP session + Spring Security context
  (S01-B6, built). The screen's per-request state (entered account, echo, red flag,
  `CDEMO-PGM-ENTER` first-display vs re-enter) is carried as form fields/model attributes —
  `returnUrl` replaces `CDEMO-FROM-PROGRAM` plumbing.
- UI: Thymeleaf screen `account-view.html` on the S-01 `layout.html` shell (header
  `CAVW`/`COACTVWC`/titles/date/time, red `ERRMSG` line, `F3=Exit` footer). A thin
  `UiController` `GET /accounts/view` (first display) + `POST /accounts/view` (submit with
  `acctId` + `aid` fields) calls the same service the REST API uses — one logic path, two
  surfaces.
- Layers: Controller → Service → Repository; records for DTOs; JUnit 5 + AssertJ.
- Persistence: Spring Data JPA + Flyway (Postgres 16, Testcontainers in tests); H2 remains
  the fast unit-test profile.

## 3. Boundary decision table (decide mode over B-009, B-012, S01-B6, S02-B1, S02-B2)
| ID | Class | Decision | Seam in target | Error/idempotency | Owner | Lead-time request | Routing point / cutover |
|---|---|---|---|---|---|---|---|
| B-009 | B4 persistence | **Reuse shared Spring Data JPA repositories** `AccountRepository`, `CustomerRepository`, `CardXrefRepository` (read-only stream). NOTFND ↔ empty `Optional`; other RESP ↔ `RuntimeException` → store-error path | `spring-boot/.../repository/*` + `AccountViewService` | store failure → MSG-FILE-ERROR (S02-B2); reads idempotent | S-02 wave | none (Postgres is ours) | service layer is the strangler point; VSAM read path decommissioned at module cutover |
| B-012 | B5 dynamic routing | **Web route return.** PF3/Exit navigates to `returnUrl` (internal paths only) else `/menu`; the only registry caller is the main menu | `UiController` PF3 branch | none needed (pure nav) | S-02 wave | none | route consumed by menu option 01 flag flip; CDEMO-USRTYP-USER reset demoted (no observable effect) |
| S01-B6 | B10 session | **Reuse**: server session + `SecurityContext` carrying user id/type; route under `SecurityConfig` `authenticated()` matcher; unsigned UI nav bounces to `/signon` (the CICS re-sign-on idiom); API keeps its 401 `ErrorResponse` | `security/SecurityConfig` entry-point split (built S-01) | session expiry → bounce to `/signon` | S-01 (built), consumed here | none | all online streams consume |
| S02-B1 | B5 AID parity | **Source wins**: every AID other than ENTER/PF3 is treated as ENTER (F-key re-submits, no invalid-key message — differs from S-01's shared invalid-key convention) | `aid` form field on the screen; `UiController` maps non-ENTER/non-PF3 to the submit path | none | S-02 wave | none | — |
| S02-B2 | B4 error-code fidelity | **Fixed technical-path rendering**: any store failure renders MSG-FILE-ERROR with RESP `000000017 ` / RESP2 `000000120 ` (DFHRESP IOERR / VSAM I/O) in the exact X(75) layout; `CobolMessages` gains the verbatim template | `CobolMessages` + `UiController` catch of `RuntimeException` | technical path only; not a user-retryable state | S-02 wave | none | — |

All seams are inside the confirmed single repo + Postgres; **no external lead-time requests
exist for this stream** (recorded explicitly). No stored-procedure boundaries.

## 4. Data and persistence
- Tables consumed (all read-only, already baselined by Flyway `V2__baseline_domain_tables.sql`):
  `accounts` (PK `acct_id`), `customers` (PK `cust_id`), `card_xrefs`
  (`xref_acct_id`/`xref_cust_id`/`xref_card_num`). Field mapping per analysis §7: `9(n)` →
  `String`/`bigint`, `S9(10)V99` → `BigDecimal`, `X(n)` → `String`, dates → `LocalDate`.
- **No schema change needed** — every field the screen reads already exists; S-02 does not
  own these tables (shared/stream-neutral baseline), so no ALTER either. The reserved
  Flyway range **V100x stays unused** (recorded so later changes don't steal it silently).
- Seeding: unchanged — `DataSeeder` loads `app/data/ASCII/{acctdata,custdata,cardxref}.txt`;
  integration tests use Testcontainers Postgres with seeded fixture rows
  (e.g. `acctId=00000000001` per the FR matrix).

## 5. Phase 0 scaffolding deltas
**NONE.** S-01 built the module's Phase 0: Thymeleaf + `layout.html` shell, `UiController`
idiom, `aid` field convention, Postgres profile + `docker-compose.yml`, Flyway seam, CI
(`mvn -B clean verify` gate), session/security seam. S-02 reuses, never forks.

## 6. Waves (from analysis DAG, leaf-first; one child session per wave, sequential)
| Wave | Programs/seams | Repo area | Consumes FR docs | Boundary seams | Strict edge to next |
|---|---|---|---|---|---|
| 1 | COACTVWC (single program): `account-view.html` + `UiController` `/accounts/view` GET/POST + `aid` handling; screen-state service path covering all five outcomes (initial, no-input, invalid-filter, xref/acct not-found, customer-not-found-with-account) + echo/`*/red` field state; verbatim `CobolMessages` corrections (MSG-XREF-NOTFND/ACCT-NOTFND/CUST-NOTFND doubled spaces + 9-digit RESP/4-or-7-digit REAS truncation, new MSG-FILE-ERROR template); `\d{11}` exact-width fix + blank→`No input received` path in `AccountViewService`; FR-S02-01..15 test matrix; `UI_ROUTES` COACTVWC → `/accounts/view` flag flip (menu option 01) | `spring-boot/` | COACTVWC program FR | B-009, B-012, S01-B6, S02-B1, S02-B2 | — (terminal wave) |

Single-repo topology: one PR into the engagement branch. The service changes stay
source-parity only — the REST contract (`GET /api/accounts/{acctId}`) is unchanged; message
texts tighten to the verbatim forms (both surfaces share `CobolMessages`).

## 7. Per-program FR generation
Already produced by the prior engagement and carried over with Java terminology updates
(`programs/COACTVWC_functional_requirement.md`, FR-S02-01..15 = COACTVWC-01..15). Verified
complete against the analysis this session; no regeneration needed.

## 8. Testing and verification
- `!mf_program_parity_test`: JUnit/MockMvc tests mapped to FR-S02-01..15 — the §4 test
  matrix (initial render, blank/`*`, non-11-digit/all-zero, three NOTFND variants, customer-
  missing-with-account, amount formatter table, customer derivations, first-xref ordering,
  PF3 return, other-AID-as-ENTER, store-error MSG-FILE-ERROR per file, maxlength, unsigned
  bounce). Service unit tests + `AccountViewUiIntegrationTest`; store-error cases via mocked
  repository failure.
- Testcontainers Postgres integration: seeded ASCII data (`acctdata/custdata/cardxref`),
  happy-path full render + first-xref-customer ordering case.
- Stream E2E: sign-on → menu option 01 → `/accounts/view` → lookup → PF3 back to `/menu`,
  exercised on the UI surface (REST surface covered by `ApiIntegrationTest`).
- CI regression gate: `mvn -B clean verify` green on the wave PR.
- UI-bearing: `!mf_online_ui_testing` recorded pass over the screen (STOP D window).

## 9. Sign-off gate
`!mf_stream_signoff` executes FR §4 acceptance criteria one-by-one against the running app;
every FR row gets pass/fail evidence; then an independent audit by a fresh session; STOP E
for merge authorization.

## 10. Risks
1. Baseline message drift (short `Resp:13` forms, missing MSG-FILE-ERROR) must be corrected
   to the verbatim X(75) strings — tests pin exact text incl. doubled spaces. MEDIUM
   (mechanical, pinned by the FR matrix).
2. FR-S02-06 needs a partial-outcome (account block + customer error) the current
   exception-throwing service can't express — wave introduces the screen-state result
   without changing the REST contract. LOW-MEDIUM.
3. Blank vs invalid-input ordering: `No input received` must outrank the filter message
   (source order `:653-661` then `:628-633`); easy to regress. LOW.
4. Amount PICOUT `+ZZZ,ZZZ,ZZZ.99` drops the 10th integer digit; formatter must reproduce
   the truncation, not just format. LOW (fixture data never exceeds 9 digits).
5. `\d{1,11}` → `\d{11}` tightening changes REST behavior for short ids (currently
   zero-padded and looked up); required for FR-S02-03 parity. LOW (a divergence fix, not a
   contract break — response was a look-up outcome, becomes the verbatim filter message).

## 11. Effort and sequencing
Program FR already exists; 1 sequential wave child after STOP C. No external lead-time
waits. All Phase-0 dependencies satisfied by S-01.

## Validation
Waves match analysis DAG (single-program stream, single wave — topological trivially);
FR-S02-01..15 all covered by wave 1; all 5 boundaries decided in Java terms (0 deferrals,
0 undecided); scaffolding deltas explicit (NONE, §5); ONLINE surfaces + UI verification
mode per ONLINE profile; shared seams (session, shell, repositories, CI) consumed from
S-01, none re-ported; table ownership respected (no ALTER on tables S-02 doesn't own).
