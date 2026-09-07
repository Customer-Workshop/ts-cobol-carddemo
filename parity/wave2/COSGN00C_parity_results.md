# Wave 2 parity results — `COSGN00C` (sign-on, `CC00`, map `COSGN0A`)

Independent verification of PR #96 (branch `devin/1788757216-w2-signon`, head `01a7138210c1b9114961b8992a517c058f5f4ed2`,
base `devin/1788757216-cardemo-account-view-stream`). Stream S-01 AccountView, wave 2. Playbook: `[COBOL v2] Program Parity Test`.

Expectations were derived FIRST from `app/cbl/COSGN00C.cbl`, `app/bms/COSGN00.bms`, `app/cpy-bms/COSGN00.CPY`,
`app/cpy/CSUSR01Y.cpy`, `CSMSG01Y.cpy`, `COCOM01Y.cpy`, `COTTL01Y.cpy`, `CSDAT01Y.cpy` and
`functional/CardDemo/programs/COSGN00C_functional_requirement.md` (derivation: `cosgn00c/cases.md`), then run against the
Java/Angular. **No implementation, test, `app/`, `functional/` or `.migration/` file was modified.** No customer-supplied
test cases exist for this program (none provided by the orchestrator; none in the repo). GnuCOBOL differential run: not
possible (CICS program, `.migration/07_runbook.md`).

## Verdict

| Unit | Verdict | Confidence | Why |
|---|---|---|---|
| Sign-on program logic (blank checks, upper-casing, NOTFND, mismatch, match U/A, PF3 text, session identity) — HTTP | **PASS** | HIGH | 17/17 HTTP-level cases match the COBOL byte-exact (trailing blanks normalised). |
| Datastore-failure branch (`WHEN OTHER`, E-13) | **FAIL (partial)** | HIGH | SQL-level failure → E-13 as expected; **connection-unavailable** failure → generic `An unexpected error occurred` (500), not E-13. |
| UI slice `/signon` vs BMS `COSGN0A` (field map, edits, DV-03, Exit/F3/Esc) | **FAIL (one cursor rule)** | HIGH | Field map, texts, key bindings all match; **wrong-password (E-06) focuses User ID, COBOL puts the cursor on Password** (`cbl:244`). |
| BCrypt upgrade-on-login on real PostgreSQL 16 | **PASS** | HIGH | before/after rows captured (§4). |
| HTTP surface (JSON 401, cookie session, signoff invalidation, session replacement) | **PASS** | HIGH | §3 P-15. SPA redirect-to-`/signon` on 401 (AC-SGN-15 second half) UNTESTED. |
| Operational: wave-1 `import` command from `backend/README.md` / wave-1 results | **FAIL (regression)** | HIGH | `--spring.main.web-application-type=none` now fails to start: `PasswordEncoder` bean is `@ConditionalOnWebApplication`. |
| Wave-1 `PostgresIntegrationTest` static-container lifecycle (child flag) | **CONFIRMED latent defect** | HIGH | A second subclass makes the suite fail (6 errors, `Connection ... refused`); today's suite passes only because exactly one subclass exists. |
| Conformance skill `.agents/skills/carddemo-target-state-conformance` | **PASS** | HIGH | 0 failing lines (§6). |

**Overall: PASS-WITH-RISK for the sign-on function; 2 parity FAILs (E-06 cursor; E-13 on connection loss) and 2 non-parity defects (import command regression; latent test-lifecycle defect) handed back to `!mf_program_migration`.**

Counts: **20 cases derived** (P-01..P-20, plus sub-variants), **20 run**, **17 PASS**, **2 FAIL**, **1 PASS-WITH-RISK**, **0 UNTESTED cases**
(one sub-assertion untested: SPA redirect on 401). FR coverage: FR-01, 02, 03, 04, 05(SGN), 06, 07, 08, 22, 25(SGN) and
AC-SGN-01..16 — each appears exactly once in the table below.

## 1. Normalisation rule

Messages are compared after stripping trailing blanks (COBOL `WS-MESSAGE PIC X(80)`, `CSMSG01Y` literals `PIC X(50)`);
everything else byte-exact. Applied to both sides.

## 2. Case table with outcomes

Evidence: `cosgn00c/http_cases.out` (HTTP; every request body + status + response), `cosgn00c/http_case_p10c.out`,
`cosgn00c/ui/ui_report.md` (browser; DOM-copied strings), `cosgn00c/ui/*.png`, `cosgn00c/ui/cosgn00c_parity.webp`.

| Case | Req / AC | Input | Seed | Expected (source) | Observed | Outcome |
|---|---|---|---|---|---|---|
| P-01 | FR-01 / AC-SGN-01 | fresh `/signon`, `GET /api/auth/header` | fixture | `CC00`, `COSGN00C`, titles `AWS Mainframe Modernization` / `CardDemo`, date `mm/dd/yy`, time `hh:mm:ss`, APPLID, SYSID, User ID focused, password masked, ERRMSG blank, footer `ENTER=Sign-on  F3=Exit` (`cbl:80-84,145-157,177-204`; `bms:29-205`; `COTTL01Y`) | header JSON `{"tranId":"CC00","programName":"COSGN00C","title01":"AWS Mainframe Modernization","title02":"CardDemo","currentDate":"09/07/26","currentTime":"07:44:06","applId":"CARDDEMO","sysId":"CICS"}`; DOM: all 8 display fields are `<output>`, `userId` autofocus + activeElement, `password` `type=password`, ERRMSG `""`, footer exact with two spaces | PASS |
| P-02 | FR-02 / AC-SGN-02 | `{"userId":"","password":"PASSWORD"}`; also spaces-only and null | — | 400 `Please enter User ID ...` (`cbl:118-122`); UI focus User ID | 400 `Please enter User ID ...` ×3; UI: same text, activeElement `userId` | PASS |
| P-03 | FR-03 / AC-SGN-03 | `{"userId":"USER0001","password":""}`; also null | fixture | 400 `Please enter Password ...` (`cbl:123-127`); focus Password | 400 `Please enter Password ...` ×2; UI: activeElement `password` | PASS |
| P-04 | AC-SGN-04 | both blank / `{}` | — | only E-01 (`EVALUATE TRUE` first `WHEN`, `cbl:117-130`) | 400 `Please enter User ID ...` ×2; UI same | PASS |
| P-05 | FR-04 / AC-SGN-05 (Q-13) | `user0001` / `password` (lower-case) | fixture | success identical to upper-case (`cbl:132-136`) | 200 `{"userId":"USER0001","userType":"U","landingTarget":"/menu"}`; UI `user0002`/`password` → `/menu` | PASS |
| P-06 | FR-05 / AC-SGN-06 | `USER0003` / `PASSWORD` | fixture | success; COMMAREA `CDEMO-USER-ID`, `CDEMO-USER-TYPE='U'` (`cbl:221-229`); XCTL `COMEN01C` ≙ `landingTarget=/menu`; session cookie | 200 `{"userId":"USER0003","userType":"U","landingTarget":"/menu"}`; `Set-Cookie` `JSESSIONID` HttpOnly path `/`; `GET /api/auth/session` → `{"userId":"USER0003","userType":"U"}`; browser lands on `http://localhost:4200/menu` | PASS |
| P-07 | FR-06 / AC-SGN-07 | `USER0001` / `BADPASS1` | fixture | 401 `Wrong Password. Try again ...`, **cursor Password** (`cbl:241-246`, `MOVE -1 TO PASSWDL` at `:244`), no session | 401 `Wrong Password. Try again ...`, no cookie; **UI activeElement = `userId`** (screenshot `ui/06_wrong_password_focus_failure.png`) | **FAIL** (text PASS, cursor FAIL) |
| P-08 | FR-07 / AC-SGN-08 | `NOBODY01` / `PASSWORD` | id absent | 401 `User not found. Try again ...`, cursor User ID (`cbl:247-251`) | 401 `User not found. Try again ...`; UI activeElement `userId` | PASS |
| P-09 | FR-08 / AC-SGN-10 | `POST /api/auth/signoff` with session; Exit button; F3; Esc | any | `Thank you for using CardDemo application...` (`CSMSG01Y:18-19`, `cbl:88-90,162-172`); conversation ends → session invalid | 200 `{"message":"Thank you for using CardDemo application...      "}` (X(50) padding intact); session cookie afterwards → 401; UI: Exit, F3, Esc each show exact text, inputs removed | PASS |
| P-10 | FR-22 / AC-SGN-09 | (a) `users` table renamed during request; (b) PostgreSQL stopped | fixture | `Unable to verify the User ...`, cursor User ID (`cbl:252-256`, `WHEN OTHER`) | (a) 500 `Unable to verify the User ...`; (b) **500 `An unexpected error occurred`** — `CannotCreateTransactionException` is not a `DataAccessException`, so `AuthService` never maps it to E-13 (`backend_run.log:63-68`) | **FAIL (partial)** |
| P-11 | FR-25 / AC-SGN-11, DV-03 | F1, F7, F12, PageDown on `/signon` (F5 = browser reload, skipped) | — | legacy E-03 `Invalid key pressed. Please see below...` (`cbl:91-94`); target DV-03: no message, no request; literal absent | ERRMSG stays `""`, zero app requests, `Invalid key pressed` absent from DOM | PASS (DV-03, approved deviation) |
| P-12 | Q-01 / AC-SGN-12 | `ADMIN001` / `PASSWORD`; `admin001`/`password` | fixture | authenticates, `CDEMO-USER-TYPE='A'` (`cbl:223-229`); legacy XCTL `COADM01C` (`:230-234`, excluded) → target `/menu`; no `/admin` route | 200 `{"userId":"ADMIN001","userType":"A","landingTarget":"/menu"}` ×2; session `{"userId":"ADMIN001","userType":"A"}`; browser `ADMIN002` → `/menu` | PASS-WITH-RISK (approved Q-01 divergence from COBOL; documented) |
| P-13 | Q-13 / AC-SGN-13 | `USER0004` / `PASSWORD` twice | legacy row | 1st: success + `sec_usr_pwd_hash` BCrypt, `sec_usr_pwd_legacy NULL`; 2nd: success via hash | before `USER0004\|U\|<null>\|<null>\|8`; after 1st `USER0004\|U\|{bcrypt}$2a$\|68\|<null>`; 2nd 200, row unchanged | PASS |
| P-13b | Q-13 negative | `USER0002` / `WRONGPWD` on legacy row | legacy row | E-06, row unchanged | 401 E-06; row before = after `USER0002\|U\|<null>\|<null>\|8` | PASS |
| P-13c | Q-13 post-upgrade | `USER0004` / `BADPASS1` after upgrade | upgraded row | same E-06 text | 401 `Wrong Password. Try again ...`; row unchanged | PASS |
| P-14 | V-7 / AC-SGN-14 | 10× wrong then right (`USER0005`) | fixture | 11th succeeds, no lockout (absence in `cbl:207-257`) | 10× 401, then 200 | PASS |
| P-15 | B-0027/B-0031 / AC-SGN-15 | `GET /api/accounts/…` no cookie; with cookie; `/api/auth/session` no cookie; after signoff; second sign-on on an existing session | — | JSON 401 (no HTML/redirect); signoff kills session; new sign-on replaces old session | 401 `{"message":"Authentication required",...}` JSON; with cookie 404 (endpoint not in this wave); after signoff 401; old cookie after re-sign-on 401, new cookie valid | PASS (API). SPA redirect to `/signon` on 401: UNTESTED (no protected page exists in the wave-2 UI to trigger it) |
| P-16 | D-0016 / AC-SGN-16 | grep all responses/URLs | — | `CC00` only as header text | only `P-01.body` (header) contains `CC00`; routes `/signon`, `/menu`, endpoints `/api/auth/*` | PASS |
| P-17 | V-8 / A-SGN-1 (target-only) | 9-char userId / password via HTTP; typing 9 chars in UI | — | legacy impossible (8-byte map field); target: 400 with NEW text | 400 `userId: must be at most 8 characters` / `password: must be at most 8 characters`; UI caps input at 8 chars silently | PASS (target-only new text, as FR §7 documents) |
| P-18 | V-5 padding (informational) | `USER0001 ` / ` USER0001` / `PASSWORD ` | fixture | legacy cannot carry a 9th byte; target behaviour documented | 400 `... must be at most 8 characters` (the `@Size` check runs before trimming) | PASS-WITH-RISK (informational; a JSON client sending a padded 8+1 value is rejected rather than trimmed) |
| P-19 | AC-SGN-01 field map | Angular DOM vs BMS `COSGN0A` | — | fields/lengths/labels/hints/footer/ERRMSG per `bms:29-205` | see §5 | PASS |
| P-20 | FR §3.4 session content | `GET /api/auth/session` after P-05 | fixture | only `userId`, `userType` (`cbl:226-227`); no password/legacy value anywhere | `{"userId":"USER0001","userType":"U"}`; no response body contains a password or hash | PASS |

## 3. Failure diffs (actual vs expected, with COBOL cite)

### F-1 — E-06 cursor placement (UI)  `COSGN00C.cbl:241-246`
```
COBOL  : MOVE 'Wrong Password. Try again ...' TO WS-MESSAGE
         MOVE -1       TO PASSWDL OF COSGN0AI          <- cursor on PASSWORD   (cbl:244)
Target : errorMessage = 'Wrong Password. Try again ...'  (text OK)
         document.activeElement.id = "userId"          <- cursor on USER ID
```
Cause (observed, not fixed): `frontend/src/app/features/signon/signon.component.ts:160-166` `focusAfterError()` focuses
Password only for `'Please enter Password ...'`; every other error, including E-06, falls to User ID. FR AC-SGN-07 itself
says "focus Password", so the delivered code also misses its own FR. The wave-2 Karma spec did not catch it.
Evidence: `cosgn00c/ui/06_wrong_password_focus_failure.png`, `cosgn00c/ui/ui_report.md` check 6.

### F-2 — `WHEN OTHER` on connection loss (HTTP)  `COSGN00C.cbl:252-256`
```
COBOL  : WHEN OTHER  MOVE 'Unable to verify the User ...' TO WS-MESSAGE   (any RESP not 0/13)
Target : PostgreSQL down -> HTTP 500 {"message":"An unexpected error occurred"}
         PostgreSQL up but SQL fails -> HTTP 500 {"message":"Unable to verify the User ..."}   (OK)
```
Observed cause: `org.springframework.transaction.CannotCreateTransactionException` (not a `DataAccessException`) raised
when the transaction opens (`cosgn00c/backend_run.log:63-68`); `AuthService` only maps `DataAccessException` to E-13, the
`GlobalExceptionHandler` fallback text is emitted instead. Cite: `cosgn00c/http_cases.out` P-10 vs `http_case_p10c.out`.

### D-3 — `import` profile command regression (operational, not a program branch)
`backend/README.md:24-26` and `parity/wave1/Wave1_parity_results.md:159` run the importer with
`--spring.main.web-application-type=none`. On head `01a7138` this fails:
`Parameter 1 of constructor in com.carddemo.service.AuthService required a bean of type PasswordEncoder that could not be found`
because `SecurityConfig` (which defines the encoder) is `@ConditionalOnWebApplication(type = SERVLET)`
(`backend/src/main/java/com/carddemo/security/SecurityConfig.java:27-34`). Log: `cosgn00c/pg/import_run.log`.
Workaround used for this evidence: run the importer with the web server enabled (`cosgn00c/pg/import_run_workaround.log`,
`Import complete: accounts=50 customers=50 card_xrefs=50 users=10`). The documented runbook step is broken.

### D-4 — wave-1 `PostgresIntegrationTest` static-container lifecycle: CONFIRMED
Probe: a second subclass of `PostgresIntegrationTest` (file kept as `cosgn00c/ProbeSecondSubclassIT.java.txt`, never
committed into `backend/`) run together with `AccountViewRepositoriesIntegrationTest`:
```
[ERROR] Tests run: 7, Failures: 1, Errors: 6
HikariPool-1 - Failed to validate connection ... (This connection has been closed.)   x10
Connection to localhost:32772 refused ... (6 x 30 s timeouts in AccountViewRepositoriesIntegrationTest)
```
(the single "Failure" is the probe's own deliberately naive row-count assertion, irrelevant). The `@Container` is started
and stopped per subclass while the Spring context — and its Hikari pool bound to the first container's port — is cached and
reused by the next subclass. Latent, real, will bite the first wave that adds a second subclass. The delivering child's
workaround (own container in `AuthUpgradeOnLoginIntegrationTest`) avoids it but doubles container start-ups.
Log: `cosgn00c/probe_static_container.log`.

## 4. Real PostgreSQL evidence (docker `carddemo-pg`, `PostgreSQL 16.15`, port 5433; fixture = real USRSEC extract, 10 rows)

Before any sign-on (`cosgn00c/pg/users_before.txt`):
```
 sec_usr_id | sec_usr_type | sec_usr_pwd_hash | has_legacy | legacy_len
 ADMIN001   | A            |                  | t          |          8
 ...        (10 rows, all hash NULL, legacy set)
 USER0004   | U            |                  | t          |          8
```
Per-case rows (`cosgn00c/http_cases.out`), format `id|type|hash_prefix|hash_len|legacy_len`:
```
P-13b USER0002 before: USER0002|U|<null>|<null>|8   wrong pwd -> 401   after: USER0002|U|<null>|<null>|8      (unchanged)
P-13  USER0004 before: USER0004|U|<null>|<null>|8   1st ok  -> 200     after: USER0004|U|{bcrypt}$2a$|68|<null>
                                                    2nd ok  -> 200     after: USER0004|U|{bcrypt}$2a$|68|<null>
P-13c USER0004 wrong pwd after upgrade -> 401 'Wrong Password. Try again ...'  row unchanged
```
Final table:
```
 sec_usr_id | sec_usr_type | hash_prefix  | hash_len | has_legacy
 ADMIN001   | A            | {bcrypt}$2a$ |       68 | f
 ADMIN002..ADMIN005 | A    |              |          | t
 USER0001   | U            | {bcrypt}$2a$ |       68 | f
 USER0002   | U            |              |          | t
 USER0003/4/5 | U          | {bcrypt}$2a$ |       68 | f
```
Full backend suite on real PostgreSQL 16 (Testcontainers): `mvn -B clean verify` → **79 tests, 0 failures, 0 errors**
(`conformance/backend_mvn_verify.log`). H2 was used by no parity evidence here.

## 5. Field map — Angular `/signon` vs BMS `COSGN0A` (`app/bms/COSGN00.bms`)

| BMS field (len, attrs) | Angular observed | Result |
|---|---|---|
| TRNNAME (4) `CC00` | `<output>` `CC00` | PASS |
| TITLE01 (40) | `AWS Mainframe Modernization` (COBOL literal trimmed) | PASS |
| CURDATE (8) `mm/dd/yy` | `09/07/26` | PASS |
| PGMNAME (8) | `COSGN00C` | PASS |
| TITLE02 (40) | `CardDemo` | PASS |
| CURTIME (9 in BMS, 8 sent by COBOL `hh:mm:ss`) | `07:51:58` | PASS (matches the sent value; see discrepancy 3) |
| APPLID (8) / SYSID (8) | `CARDDEMO` / `CICS` | PASS |
| USERID (8, UNPROT, IC) | `<input id=userId maxlength=8 autofocus>`; activeElement on load | PASS |
| PASSWD (8, UNPROT, DRK) | `<input id=password type=password maxlength=8>` | PASS |
| Labels / hints | `User ID     :` `(8 Char)`; `Password    :` `(8 Char)` | PASS |
| ERRMSG (78, ASKIP BRT) | error line, `""` initially, receives E-01/E-02/E-06/E-07 texts | PASS |
| Footer `ENTER=Sign-on  F3=Exit` | exact, two spaces | PASS |
| Display-only fields not editable | all 8 are `<output>`, no contenteditable | PASS |
| DV-03: no `Invalid key pressed` | absent from DOM; F1/F7/F12/PageDown produce no message/request | PASS |
| Exit button + F3 + Esc → thank-you | all three: `Thank you for using CardDemo application...`, inputs removed | PASS |
| Cursor after E-06 | User ID (expected Password) | **FAIL (F-1)** |

Post-exit the target shows a `Sign on again` button (re-entry ≙ `EIBCALEN = 0`); the CICS conversation simply ends —
informational, not a parity defect.

## 6. Conformance skill result — 0 failing lines

`conformance/skill_checks.txt`: all 18 mechanical lines PASS (Java 21, Boot 3.x, no Lombok/mapstruct, no float/double, no
field injection, DTOs are records, standalone components, no NgRx, Material, `withCredentials`, `ddl-auto=validate`, no H2
in main properties, `jdbc:postgresql`, BigDecimal precision, `CommandLineRunner` only under `import`, no `@Transactional`
on controllers). Legacy diff `app/` vs `origin/main` and vs stream base: 0 files. Backend `mvn clean verify`: 79/79.
Frontend `npm ci && npm run build && ng test --watch=false --browsers=ChromeHeadless`: 29/29 (`conformance/frontend_build_test.log`).
Endpoints: `/api/auth/header`, `/signon`, `/session`, `/signoff` — all listed in `backend/README.md` (4 table rows).

## 7. FR doc vs COBOL discrepancies

1. `COTTL01Y.cpy` contains an unused `CCDA-THANK-YOU` literal; `COSGN00C` uses `CSMSG01Y` `CCDA-MSG-THANK-YOU`. FR and target use the right one. No action.
2. BMS `PASSWD` carries an invisible DRK placeholder (`________`); not observable, target omits it. No action.
3. BMS `CURTIME` is `LENGTH=9` (`Ahh:mm:ss`) while the COBOL moves an 8-char `hh:mm:ss`; the FR and target use the sent value. No action.
4. FR routes type `A` to `/menu` although the COBOL XCTLs to `COADM01C` (`cbl:230-234`): approved Q-01 exclusion (D-0033); recorded as PASS-WITH-RISK in P-12, banner deferred to wave 3.
5. FR AC-SGN-07 correctly says "focus Password" — the delivered UI does not (F-1). The FR is right; the code is wrong.
6. FR AC-SGN-09 says a repository exception yields E-13; the COBOL `WHEN OTHER` is broader (any non-0/13 RESP). The FR under-specifies the connection-loss case (F-2).

## 8. Child flags reviewed

- `landingTarget` vs brief's `nextRoute`: FR §4 / boundary register use `landingTarget`; delivered field matches the FR. No parity impact.
- Admin routing to `/menu`: verified (P-12); approved deviation, PASS-WITH-RISK.
- `PostgresIntegrationTest` lifecycle: **confirmed real latent defect** (D-4). Recommend a singleton container (manual start, no `@Container`) in wave 3 housekeeping.
- Not flagged by the child but found here: import-command regression (D-3), E-06 cursor (F-1), E-13 on connection loss (F-2).

## 9. Untested

- AC-SGN-15 second half (SPA redirects to `/signon` on a 401 from a protected page): no protected page exists in the wave-2 UI. API-side 401 verified.
- F5 as "unbound key" (DV-03): browser reload; asserted with F1/F7/F12/PageDown instead.
- Legacy E-03 `Invalid key pressed` text: intentionally absent (DV-03), asserted as absence.

## 10. Evidence index

```
parity/wave2/COSGN00C_parity_results.md          this document
parity/wave2/cosgn00c/cases.md                    source-derived case table (written before reading Java/Angular)
parity/wave2/cosgn00c/REPRODUCE.md                reproduction steps + fixture ids
parity/wave2/cosgn00c/run_http_cases.sh           HTTP harness script (curl + psql)
parity/wave2/cosgn00c/http_cases.out              captured HTTP run (P-01..P-20 except UI-only)
parity/wave2/cosgn00c/http_case_p10c.out          E-13 SQL-failure variant
parity/wave2/cosgn00c/http/*.body, jar_*.txt      raw response bodies and cookie jars
parity/wave2/cosgn00c/pg/users_before.txt, users_schema.txt, import_run.log, import_run_workaround.log
parity/wave2/cosgn00c/backend_run.log             backend log incl. F-2 stack trace
parity/wave2/cosgn00c/probe_static_container.log  D-4 probe run; ProbeSecondSubclassIT.java.txt = probe source
parity/wave2/cosgn00c/ui/ui_report.md, *.png, cosgn00c_parity.webp   browser pass + recording
parity/wave2/conformance/skill_checks.txt, backend_mvn_verify.log, frontend_build_test.log, frontend_npm_ci.log
```
Branch `devin/1788757216-w2-parity` (parity artifacts only) on top of `01a7138210c1b9114961b8992a517c058f5f4ed2`.
