# COACTVWC parity results — Wave 4 (final), stream S-01 AccountView

| | |
|---|---|
| Program | `COACTVWC` (trancode `CAVW`, map `CACTVWA`/`COACTVW`), ONLINE, UI-bearing |
| Verified revision | PR #98 head `32644a2758215daef4afbc79c15b6abf7df7423f`, branch `devin/1788757216-w4-account-view` |
| Evidence branch | `devin/1788757216-w4-parity` (this document + `parity/wave4/coactvwc/`) |
| Oracle | `app/cbl/COACTVWC.cbl`, `COACTVW.bms`, `COACTVW.CPY`, copybooks `CVACT01Y/03Y`, `CVCUS01Y`, `CVCRD01Y`, `COCOM01Y`, `CSMSG01Y`, raw `app/data/ASCII/*.txt`, `COACTVWC_functional_requirement.md`. Cases were derived (`coactvwc/cases.md`, `derive_expected.py`) before any file under `backend/` or `frontend/` was opened. |
| Customer cases | none supplied (behaviour is source-derived; stream context) |
| Differential COBOL run | not possible: CICS `CO*C` programs do not compile off-host (`07_runbook.md`, missing `DFHBMSCA`/`DFHAID`/translator) |
| Database | PostgreSQL 16 (`postgres:16` container `carddemo-pg`, :5433), wave-1 `import` profile (50/50/50 + 10 USRSEC) + `seed_synthetic.sql`. No H2, no in-memory profile. |

## 1. Verdict

| Unit | Verdict | Confidence | Basis |
|---|---|---|---|
| Backend `GET /api/accounts/{acctId}` (service + endpoint) | **PASS** | high | 31/31 HTTP verdicts, 145/145 API field values vs raw bytes, all four `WHEN OTHER` arms (PG down, ACCTDAT, CUSTDAT) with the program's own E-12 text, DV-01/DV-02/DV-05/DV-06/Q-04/Q-09/Q-14 asserted |
| Angular `/accounts/view` slice | **FAIL** | high | 3 observable mismatches vs COBOL/FR: F-1 initial header shows `mm/dd/yy`/`hh:mm:ss`/blank titles, F-2 blank search does not set `*`/red on the field, F-3 FICO renders `78` not `078`. Everything else (route guard, all 30 output fields, byte-exact E-04/E-05/E-09/E-10/E-11 in red, label order = BMS order, money padding kept, full ZIP/phone, Exit/F3/Esc -> `/menu` with session intact) matches |
| Flag #1 ruling (75-byte truncation) | **target text is correct; FR §5/§7 wording needs a footnote** | high | §3 below |

Counts: 24 case ids derived (43 executions incl. sub-cases + 5×29 field checks), 24 run, **17 PASS, 3 PASS-WITH-RISK, 3 FAIL, 1 UNTESTED**. Every ACV-01..ACV-09 requirement and AC-ACV-01..19 appears once (§2). Failures are handed back to `!mf_program_migration`; nothing was fixed here.

Normalization rule (both sides): trailing blanks stripped; money = remove blanks and commas, force a leading sign, `0` before a leading `.`; dates as `YYYY-MM-DD` text; FICO compared as int at API level and as text on the screen (which is where F-3 surfaces).

## 2. Case table

Evidence files are under `parity/wave4/coactvwc/` (`http_cases.out` + `http/*.body`, `ui/ui-NN.png|json`, `ui/network.jsonl`).

| Case | Req / AC | Input, seed | Expected (cite) | Observed | Verdict |
|---|---|---|---|---|---|
| A-01 | ACV-01 / AC-01 | menu option 1, no search | local render: INFO `Enter or update id of account to display`, empty field, no data, header `CAVW`/`COACTVWC`/date/time/titles (`cbl:436-458`); no backend call | UI `ui-02`: INFO exact, field empty, `maxlength="11"`, `CAVW`, `COACTVWC`, `View Account`, no data, no error, no `/api/accounts` call — **but Date = literal `mm/dd/yy`, Time = literal `hh:mm:ss`, TITLE01/TITLE02 empty** (the BMS `INITIAL` constants; legacy overwrites them on every SEND, `cbl:436-453`). API side: `GET /api/accounts` (empty id) -> 400 E-04 | **FAIL (F-1)** |
| A-02 | ACV-02 / AC-02 | `` , `*`, 11 spaces | 400 `No input received` (`cbl:628-633`, `:640-642`); on the screen `*` in red in the field (`cbl:561-565`, FR §7 "field `*` with error styling") | API a/b/c: 400, message byte-exact. UI `ui-03`: red `No input received`, **field value `""`, normal (blue focus) styling, no `*`** | **FAIL (F-2)** |
| A-03 | ACV-03 / AC-03, Q-02 | `00000000000`, `1234567890A`, `12 34567890`, `1234567890`, `123456789012`, `-0000000001` | 400 `Account Filter must  be a non-zero 11 digit number` (`cbl:666-676`); value echoed red | 6/6 API byte-exact; UI `ui-04/05`: red text exact incl. double space, value echoed | PASS |
| A-04 | ACV-04 / AC-04 | `00000000099` (no xref row) | 404 `Account:00000000099 not found in Cross ref file.  Resp:` … ` Reas:` (`cbl:741-758`), no data | `Account:00000000099 not found in Cross ref file.  Resp:0000000013 Reas:0000` (75 bytes), `account=null customer=null`; UI `ui-06` red, all values blank (labels stay — BMS constant fields) | PASS |
| A-05 | ACV-05 / AC-05 / DV-01 | seed xref `9990000000000001`->acct 901 (no accounts row) | 404 `Account:00000000901 not found in Acct Master file.Resp:` … ` Reas:`; **no account, no customer block** (DV-01; legacy shows customer block + stale area, `cbl:792`, `:704-706`) | message exact (75 bytes), both blocks `null`; UI `ui-07` all values blank | PASS |
| A-06 | ACV-06 / AC-06 | seed xref `9990000000000002` cust 999999902 -> acct 902 (acct row = copy of 1, no customer) | 404 `CustId:999999902 not found in customer master.Resp: ` … ` REAS:`; **account block present** (`cbl:493`, `:471-491`), customer absent | message exact (75 bytes); `account` block present with the 10 acct-1 values; UI `ui-08` account values shown, customer blank | PASS |
| A-07 | ACV-07 / AC-07 | `00000000027` | 200, both blocks = `expected_fixture.json` | 200, 29/29 API fields; UI `ui-09` | PASS |
| A-08 | AC-08 field parity | accounts 001, 010, 027, 033, 050 | every displayed field = raw-byte derivation via COBOL MOVE/edit (`cbl:471-523`) | API: 145/145 (5×29) — `compare_fields.py` in `http_cases.out`. Screen (acct 27): 28/29 — **FICO shows `78`**, legacy `MOVE CUST-FICO-CREDIT-SCORE 9(3) TO ACSTFCOO X(3)` = `078` (`cbl:505-506`, raw `custdata.txt` rec 27 bytes 322-324 = `078`) | **FAIL (F-3)** |
| A-09 | DV-05 / AC-09 | acct 27: ZIP `07923-8822` (10), phone `(935)027-1145  ` (15) | full values, not 5/13 (`bms:290`, `:318`) | API and UI: `07923-8822`, `(935)027-1145`, `(103)537-5007` | PASS |
| A-10 | DV-06 / D-0038 | any fixture account | `Account Group` = `A000000000` (legacy: blank — `cbl:490` reads bytes 113-122 = spaces; the extract carries the value in the ZIP slot 103-112) | `groupId":"A000000000"` for all 5 (+902/903); UI shows `A000000000`; PG `acct_group_id='A000000000'`, `acct_addr_zip=NULL` | PASS-WITH-RISK (approved deviation, pending STOP D default) |
| A-11 | Q-04 / AC-10 | acct 903: xref cards `…0002`->cust 2 inserted first, `…0001`->cust 3 second | customer `000000003` (lowest card, `cbl:727-735`, Q-04) | `customerId":"000000003"` (Larry Cody Homenick) | PASS |
| A-12 | ACV-08 / AC-11 (E-12 CXACAIX) | `docker stop carddemo-pg`, then `GET …/00000000027` | 500 `File Error: READ     on CXACAIX   returned RESP …,RESP2 …` (`cbl:86-105`, `:760-766`; D-0040 class) | `File Error: READ     on CXACAIX   returned RESP 0000000016,RESP2 0000000000` (75 bytes), 500; recovers to 200 after `docker start` | PASS |
| A-13 | ACV-08 (E-12 ACCTDAT) | `ALTER TABLE accounts RENAME` at runtime (xref read succeeds first) | 500 `File Error: READ     on ACCTDAT   …` (`cbl:809-816`) | exact, 500 | PASS |
| A-14 | ACV-08 (E-12 CUSTDAT) | `ALTER TABLE customers RENAME` (xref + account succeed) | 500 `File Error: READ     on CUSTDAT   …` (`cbl:858-865`) | exact, 500; 200 after rename back | PASS |
| A-15 | ACV-09 / AC-12 / B-0011 / DV-02 | Exit button, F3, Esc | `/menu`, session intact (`cbl:324-352`), no account API call | `ui-11/12/13`: `/menu` each time, still signed in; no `/api/accounts` or exit call (the landing menu's own `GET /api/auth/session` + `GET /api/menu` are COMEN01C's, outside this program) | PASS |
| A-16 | DV-03 / AC-15 | F5 with the form focused | nothing happens; no `Invalid key` text (CSMSG01Y is never referenced by `COACTVWC`) | no `Invalid key` text, no `/api/accounts` call; **but physical F5 is the browser reload**: page reloads, form cleared (`ui-10`, `network.jsonl`) — the component-level "F5 does nothing" spec cannot be observed with a real key | PASS-WITH-RISK (see §5 R-1) |
| A-17 | AC-16 | `GET /api/accounts/00000000027`, `GET /api/accounts` without cookie | 401 (FR §7 `CAVW` without COMMAREA) | 401, 401 | PASS |
| A-18 | Q-14 / AC-17 | 30-char id; `abc'def12`; `';drop`; `0000000002%` | 400 E-05 class, never 500 | 30-char and `abc'def12`: 400 E-05 exact; `%3B` and `%25` inputs: **400 with the servlet container's generic body** (`{"error":"Bad Request"}`, rejected before the controller) | PASS-WITH-RISK (status class right, no program text — technical) |
| A-19 | B-0027/B-0028 / DV-02 / AC-14 | `GET /api/auth/session` before/after view, U and A | identical `userId`/`userType` | `{"userId":"USER0001","userType":"U"}` both; ADMIN001 identical before/after | PASS |
| A-20 | AC-18 generic 500 | failure outside the read path | generic `ErrorResponse` 500 | no injection point without modifying code | UNTESTED |
| A-21 | UI vs BMS field map | `/accounts/view` | header (`bms:33-78`), `ACCTSID` len 11, 10 account + 18 customer outputs, INFOMSG, ERRMSG red, `F3=Exit` footer | all present; label text and reading order identical to the BMS `INITIAL` constants row by row (incl. two `Address:` rows); `maxlength=11`; money `white-space: pre` monospace, padding preserved | PASS |
| A-22 | route guard | `/accounts/view` without session | -> `/signon` | redirected (`ui-01`) | PASS |
| A-23 | E-11 UI | `00000000902` in the browser | account block populated, customer blank, red E-11 | as expected (`ui-08`) | PASS |
| A-24 | flag #1 | E-09/E-10/E-11/E-12 lengths | see §3 | 75/75/75/75 bytes, cut exactly where the COBOL cuts | PASS |

## 3. Flag #1 ruling: `WS-RETURN-MSG PIC X(75)` (`cbl:117`)

Facts (cites): messages are built with `STRING … DELIMITED BY SIZE INTO WS-RETURN-MSG` with no `ON OVERFLOW`/`POINTER` (`cbl:744-757`, `:794-806`, `:843-856`) or by `MOVE WS-FILE-ERROR-MESSAGE TO WS-RETURN-MSG` (`:766`, `:816`, `:865`, source is 80 bytes `cbl:86-105`). Both stop at the 75th byte of the receiver. The message then goes `MOVE WS-RETURN-MSG TO ERRMSGO` (`cbl:532`; `COACTVW.CPY:464` `X(78)`, `bms` `ERRMSG LENGTH=78`), i.e. 75 bytes + 3 blanks. **75 bytes is the true observable maximum on the legacy screen; the 78-byte map field never receives more.** `ERROR-RESP`/`ERROR-RESP2` are `X(10)` targets of `MOVE S9(9) COMP` (`cbl:748-749`): 9 digit characters + 1 blank.

| Message | bytes before RESP2 | RESP2 chars visible on legacy | Target observed (`http_cases.out` A-24) |
|---|---|---|---|
| E-09 | 8+11+13+23+10+6 = 71 | 4 of 10 | `…Resp:0000000013 Reas:0000` len 75 — matches |
| E-10 | 71 | 4 of 10 | `…file.Resp:0000000013 Reas:0000` len 75 — matches |
| E-11 | 7+9+10+26+10+6 = 68 | 7 of 10 | `…Resp: 0000000013 REAS:0000000` len 75 — matches (**not in the child's flag, but truncated too**) |
| E-12 | 12+8+4+9+15+10+7 = 65, +10 = 75 | **10 of 10** — only the trailing 5-blank FILLER is cut | `File Error: READ     on CXACAIX   returned RESP 0000000016,RESP2 0000000000` len 75 — matches |

Ruling: the target's truncated E-09/E-10/E-11 texts are **correct** legacy behaviour and must stay; the child's claim that **E-12 is truncated mid-RESP2 is wrong** (it fits exactly). Only the RESP digits differ from a real CICS run (Q-09: not a parity goal; target prints 10 digits into the slot where CICS would print 9 digits + blank — the *visible* RESP2 fragment therefore reads e.g. `0000` instead of `0000000080`'s first 4 chars `0000` — same 4 chars for NOTFND, so byte-identical there). FR fix needed: §5 E-09..E-12 / §7 row "read RESP other than 0/13" list the natural (untruncated) literals and the E-12 literal with single blanks (`READ on <file>`), whereas the observable is `READ     on CXACAIX   ` (8- and 9-byte padded fields) cut at 75 bytes. Recommend a footnote in the FR, not a code change.

## 4. Failures (hand back to `!mf_program_migration`)

| Id | Case | Expected (cite) | Observed | Severity |
|---|---|---|---|---|
| **F-1** | A-01 | Initial screen header carries the current date `mm/dd/yy` and time `hh:mm:ss` from `FUNCTION CURRENT-DATE` and the titles from `COTTL01Y` on every SEND, including the first (`cbl:436-453`; AC-ACV-01 "date/time from the pinned `Clock`") | On local render before the first search the header shows the literal placeholders `mm/dd/yy` / `hh:mm:ss` and empty TITLE01/TITLE02 (`ui/ui-02.png`, `ui-02.json`). After a search the header is filled from the response (`09/07/26`, `10:19:49`, `AWS Mainframe Modernization`, `CardDemo`) | medium (visible on the first screen every user sees) |
| **F-2** | A-02 | Blank search on re-enter: `MOVE '*' TO ACCTSIDO`, `MOVE DFHRED TO ACCTSIDC` (`cbl:561-565`); FR §7 target "field `*` with error styling", AC-ACV-02 | Red `No input received` is shown, but the input stays `""` with normal styling (`ui/ui-03.png`, `ui-03.json`) | low-medium (FR-stated target behaviour missing) |
| **F-3** | A-08 | `MOVE CUST-FICO-CREDIT-SCORE (9(3)) TO ACSTFCOO (X(3))` -> `078` for customer 27 (`cbl:505-506`; raw `custdata.txt` record 27 bytes 322-324 = `078`) | API `"ficoScore":78` (Integer per FR §3.2 `ficoScore: Integer`), screen `FICO Score: 78` (`ui/ui-09.png`) | low; root cause is the FR typing the field as `Integer` — either declare a deviation (DV-08) or render as 3-digit zero-padded text. Not in DV-01..07 → FAIL per the wave rules |

## 5. Target-only observations / register gaps (not failures)

- **R-1 (A-16)** DV-03 says "unbound keys do nothing"; in a real browser F5 is the page reload and clears the form (`ui-10.png`). The Karma spec asserts a synthetic `F5` `keydown` issues no API call — true, but not what a user sees. Suggest the register/FR name a key the SPA actually intercepts (or state that browser-native keys are out of scope).
- **R-2 (A-18)** Encoded `;` and `%` in the path are rejected by Tomcat with a generic 400 before Q-14 validation runs (never 500). Acceptable; note in FR §7 "account >11 chars" row.
- **R-3 (A-15)** Exit is a pure SPA navigation; the `GET /api/auth/session` ×2 + `GET /api/menu` seen after Exit belong to the guard and `COMEN01C`, not to this program.
- **R-4 (A-04/A-05)** With every value blank the constant labels and `Customer Details` heading remain — identical to the BMS map, where labels are constants (`bms` `INITIAL=`).

## 6. FR-doc vs COBOL discrepancies

1. FR §5 E-09..E-12 and §7 quote the natural (untruncated) literals; observable legacy text is cut at 75 bytes (§3). E-12 literal in FR line 129 collapses the 8/9-byte padded `ERROR-OPNAME`/`ERROR-FILE` to single blanks.
2. FR §3.2 types `ficoScore` as `Integer`, which silently drops the leading zero the `X(3)` move keeps (F-3).
3. FR §7 says entry is "local render, no API call" but AC-ACV-01 also requires date/time from `Clock`; the implementation satisfies the first and not the second (F-1). The FR is internally consistent; the implementation is not.
4. `CSMSG01Y`/`CSMSG02Y` were requested as inputs: `COACTVWC` never references `CCDA-MSG-INVALID-KEY`/`CCDA-MSG-THANK-YOU` (PF keys are folded to ENTER, `cbl:306-314`), so DV-03's "no invalid-key message" is trivially true for this program.
5. Child flag #1: E-11 truncation missed, E-12 truncation asserted wrongly (§3).

## 7. PostgreSQL evidence (`coactvwc/pg_excerpts.txt`, `http_cases.out` header)

```
postgres: PostgreSQL 16.15 (Debian 16.15-1.pgdg13+2) on x86_64-pc-linux-gnu ; rows: 50|50|50|10 (+ seed_synthetic.sql: 2 accounts, 4 card_xrefs)
 acct_id | acct_curr_bal | acct_credit_limit | acct_addr_zip | acct_group_id
      27 |        284.00 |           5572.00 |               | A000000000
 cust_id | cust_addr_zip | cust_phone_num_1 | cust_fico_credit_score | cust_ssn
      27 | 07923-8822    | (935)027-1145    |                     78 | 980161210
card_xrefs acct 903: 9993000000000001|3 , 9993000000000002|2   -> service returned customer 000000003 (Q-04)
```

Raw-byte cross-check for acct 27 (`acctdata.txt` rec 27): `ACCT-CURR-BAL` = `00000002840{` -> `+        284.00`; `ACCT-CREDIT-LIMIT` = `00000055720{` -> `+      5,572.00`; bytes 103-112 `A000000000`, 113-122 blanks (D-0038). `custdata.txt` rec 27: ZIP `07923-8822`, phone-1 `(935)027-1145  `, SSN `980161210` -> `980-16-1210`, FICO `078`.

## 8. Suites and conformance (`coactvwc/backend_suite.log`, `frontend_build.log`, `frontend_test.log`, `conformance.out`)

- Backend `mvn clean verify`: **182 tests, 0 failures, 0 errors, BUILD SUCCESS, EXIT 0**, one Testcontainers `postgres:16` instance for the whole JVM (`AccountViewServiceTest` 23, `AccountViewFlowIntegrationTest` 6, `AccountViewRepositoriesIntegrationTest` 6, `AccountControllerWebMvcTest` 10, …).
- Frontend `npm run build` PASS; Karma ChromeHeadless: Executed 79 of 79 SUCCESS.
- `carddemo-target-state-conformance` skill: **all 21 mechanical checks PASS** (CORE 9/9, ONLINE 6/6 + endpoint list, DATA/BOUNDARY 6/6). Endpoints: `/api/accounts/{acctId}`, `/api/menu/header`, `/api/auth/session|signon|signoff`, `/api/menu/select`; README endpoint-table rows: 4 (informational).
- Existing tests were not modified. Note: the delivering child's tests assert `ficoScore == 78` (Integer) — consistent with the FR typing, and the reason F-3 was not caught upstream.

## 9. Reproduction

```bash
docker run -d --name carddemo-pg -e POSTGRES_USER=carddemo -e POSTGRES_PASSWORD=carddemo -e POSTGRES_DB=carddemo -p 5433:5432 postgres:16
cd backend && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 CARDDEMO_DB_USER=carddemo CARDDEMO_DB_PASSWORD=carddemo \
  mvn -q spring-boot:run -Dspring-boot.run.profiles=import \
  -Dspring-boot.run.arguments="--spring.main.web-application-type=none --spring.datasource.url=jdbc:postgresql://localhost:5433/carddemo"
docker exec -i carddemo-pg psql -U carddemo -d carddemo < parity/wave4/coactvwc/seed_synthetic.sql
cd backend && JAVA_HOME=… CARDDEMO_DB_USER=carddemo CARDDEMO_DB_PASSWORD=carddemo mvn -q spring-boot:run \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:postgresql://localhost:5433/carddemo"
python3 parity/wave4/coactvwc/derive_expected.py 00000000001 00000000010 00000000027 00000000033 00000000050 > parity/wave4/coactvwc/expected_fixture.json
parity/wave4/coactvwc/run_http_cases.sh > parity/wave4/coactvwc/http_cases.out     # stops/starts the PG container and renames tables for A-12..A-14
(cd backend && JAVA_HOME=… mvn clean verify > ../parity/wave4/coactvwc/backend_suite.log 2>&1)
(cd frontend && npm ci) && parity/wave4/coactvwc/run_conformance.sh > parity/wave4/coactvwc/conformance.out
cd frontend && npx ng serve   # UI run: parity/wave4/coactvwc/ui/test-plan.md, screenshots ui-01..13, recording coactvwc-ui-evidence.mp4
```

Evidence index: `coactvwc/cases.md`, `derive_expected.py`, `expected_fixture.json`, `seed_synthetic.sql`, `run_http_cases.sh`, `compare_fields.py`, `http_cases.out`, `http/`, `pg_excerpts.txt`, `backend_suite.log`, `frontend_build.log`, `frontend_test.log`, `conformance.out`, `run_conformance.sh`, `ui/` (13 screenshots + DOM JSON, `network.jsonl`, `test-plan.md`, `coactvwc-ui-evidence.mp4`).
