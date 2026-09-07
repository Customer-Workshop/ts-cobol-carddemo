# Wave 3 parity results — `COMEN01C` (main menu, `CM00`, map `COMEN1A`)

Independent verification of PR #97 (branch `devin/1788757216-w3-menu`, head `7ca7906`, base
`devin/1788757216-cardemo-account-view-stream`). Stream S-01 AccountView, wave 3. Playbook: `[COBOL v2] Program Parity Test`.
Parity branch `devin/1788757216-w3-parity` (this document + `comen01c/` evidence only).

Expectations were derived FIRST from `app/cbl/COMEN01C.cbl`, `app/cpy/COMEN02Y.cpy`, `app/bms/COMEN01.bms`,
`app/cpy-bms/COMEN01.CPY`, `CSMSG01Y.cpy`, `COCOM01Y.cpy`, `COTTL01Y.cpy`, `CSDAT01Y.cpy` and
`functional/CardDemo/programs/COMEN01C_functional_requirement.md` (derivation: `comen01c/cases.md`), then run against the
Java/Angular. **No implementation, test, `app/`, `functional/` or `.migration/` file was modified.** No customer-supplied test
cases exist for this program (none provided by the orchestrator; none in the repo) — the customer-case disagreement section is
empty. GnuCOBOL differential run: the CICS program itself cannot compile off-host (`07_runbook.md`); the pure normalisation
block `:117-133` was replayed under GnuCOBOL (`comen01c/norm_oracle.cbl`, `.out`) — see §7 (dialect divergence found and ruled).

Cites: `cbl:` = `app/cbl/COMEN01C.cbl`, `tbl:` = `app/cpy/COMEN02Y.cpy`, `bms:` = `app/bms/COMEN01.bms`.

## Verdict

| Unit | Verdict | Confidence | Why |
|---|---|---|---|
| `PROCESS-ENTER-KEY` normalisation + validation (blank, zero, non-numeric, >11, leading/trailing blank, leading zero) — HTTP | **PASS** | HIGH (numeric), MEDIUM (non-numeric echoes, §7) | 16/16 option inputs return E-08 or dispatch exactly as derived; echo (`OPTIONO`, `cbl:125`) byte-exact incl. `0A`, `1A`, `A1`, `0?`. |
| Dispatch option 1 -> `COACTVWC` / `/accounts/view` (hard stop at the XCTL) | **PASS** | HIGH | `{program:COACTVWC, endpoint:/api/accounts/{acctId}, route:/accounts/view, implemented:true}` for `1`, ` 1`, `01`, `1 ` (U and A sessions). |
| Options 2..11 -> Q-12 facade `Option not available in this release` | **PASS** | HIGH | 13/13; `program` = table PGMNAME of the row; no `is not installed` / `coming soon` / `No access` text anywhere. |
| Route table (`COMEN02Y` 11 rows: number, name, program, user type, order; no row 12) — API and rendered menu | **PASS** | HIGH | 11/11 rows identical; rendered labels `01. Account View` .. `11. Pending Authorization View`. |
| Item 3 — options 12..99 (out-of-range subscript at `cbl:136-137`) | **PASS (observably identical) / UNDECLARED deviation in the DV register** | HIGH | Ruling in §5: same first screen (E-08 + echo); the legacy's second, storage-dependent outcome is not reproduced; declared only as V-3/R-07 in the FR, **not** in DV-01..05. |
| Admin-only guard (`cbl:136-143`) / admin landing (Q-01) | **PASS** | HIGH | Dead code (all 11 `USRTYPE='U'`, `tbl:29..89`); ADMIN001 gets identical menu, banner `Administration is not available in this release`, option 1 works. |
| PF3 / Exit -> `COSGN00C` without COMMAREA, session dropped | **PASS** | HIGH | Esc, F3, Exit -> `/signon`; `GET /api/menu` afterwards 401. **No thank-you text is correct** (§8 D-1: the brief's expectation contradicts `cbl:196-203` + `COSGN00C.cbl:80-83`). |
| DV-03 invalid key (legacy E-03 `cbl:99-102`) | **PASS (declared deviation)** | HIGH | F1/F5/F7/F12/PageDown/Tab: no message, no `Invalid key` text in DOM/API (browser-native effects only). |
| HTTP surface: JSON 401 unauthenticated, session = COMMAREA (B-0027/B-0028), `CM00` only in header (D-0016) | **PASS** | HIGH | M-12, M-13, M-14. |
| D-0040 class / PG-down on read paths | **PASS (informational)** | HIGH | Program has no data access (`cbl` FR §6); `GET /api/menu`, `POST /select`, `GET /api/auth/session` keep answering with PG stopped; sign-on (COSGN00C) returns the mapped E-13 500, not a stack trace. |
| Target-only payload edits (Q-14) | **PASS-WITH-RISK** | HIGH | `'123'` -> 400 `option: must be at most 2 characters` (good). **Malformed / empty JSON body -> 500 `An unexpected error occurred`** (should be 400); **Unicode digits `"٠١"` are accepted as option 1 and echoed unnormalised** (`Character.isDigit` + `Integer.parseInt`). Legacy-impossible inputs; not parity FAILs, handed back as target defects. |
| UI `/menu` vs BMS `COMEN1A` field map, edits, clicks, guard | **PASS** | HIGH | §6; recording `comen01c/ui/recording.mp4`. |
| Conformance skill + whole backend suite on real PG in one JVM (D-0040 regression) | **PASS** | HIGH | 24/24 skill lines PASS; `mvn clean verify` 136 tests, 0 failures, exactly **one** `postgres:16` container created (`backend_suite.log:96-98`); Karma 55/55. |

**Overall: PASS for the COMEN01C menu function (0 parity FAILs). One register gap (item 3 must be declared as DV-06 or accepted as
V-3/R-07) and two target-only robustness defects (500 on malformed JSON; Unicode-digit acceptance) handed back to
`!mf_program_migration`.**

Counts: **19 cases derived** (M-01..M-19, 78 sub-assertions), **19 run**, **19 PASS** (1 PASS-WITH-RISK: M-15), **0 FAIL**,
**0 UNTESTED cases** (two sub-assertions untested, §9). FR coverage: MEN-01..MEN-06, FR-09, FR-10, FR-11(MEN), FR-12,
FR-05(MEN), FR-25(MEN), V-1..V-7, Q-01, Q-12, Q-14, DV-03, B-0027/B-0028/B-0029/B-0032/B-0033, D-0016, D-0040 and
AC-MEN-01..13 — each appears exactly once in the table below.

## 1. Normalisation rule

Messages compared after stripping trailing blanks (`WS-MESSAGE PIC X(80)`, `CSMSG01Y` literals `PIC X(50)`); menu labels compared
after stripping the `PIC X(35)` padding; everything else byte-exact. Option echo compared byte-exact against `OPTIONO PIC X(2)`
(`COMEN01.CPY:254`). Applied to both sides (`run_http_cases.sh` `sel()`).

## 2. Case table with outcomes

Evidence: `comen01c/http_cases.out` (every request body + status + response; per-response bodies in `comen01c/http/*.body`),
`comen01c/pg/pg_down.out`, `comen01c/ui/ui_report.md` + `comen01c/ui/*.png` + `comen01c/ui/recording.mp4`.
Runtime: Spring Boot default profile on PostgreSQL 16.15 (docker `carddemo-pg`, :5433), fixture loaded by the `import` profile
(`comen01c/pg/import_run.log`, 10 USRSEC users; `users_before.txt`).

| Case | Req / AC | Input | Expected (source) | Observed | Verdict |
|---|---|---|---|---|---|
| M-01 | MEN-01 / FR-09, FR-05(MEN) / AC-MEN-01 | `GET /api/menu` as USER0001 | header `CM00`/`COMEN01C` (`cbl:36-37`), titles `COTTL01Y`, `mm/dd/yy` (`cbl:247-251`), `hh:mm:ss` (`cbl:253-257`); 11 labels (`cbl:262-303`) | `{"tranId":"CM00","programName":"COMEN01C","title01":"AWS Mainframe Modernization","title02":"CardDemo","currentDate":"09/07/26","currentTime":"09:09:41"}`; 11 rows | PASS |
| M-02 | B-0033 / AC-MEN-02 | rows of M-01 | 11 × (num, name, pgm, `U`) = `tbl:25-90`; only row 1 implemented | 11/11 ROW PASS, COUNT 11, ORDER 1..11, implemented `[1]` | PASS |
| M-03 | MEN-02 / FR-10 / AC-MEN-03 | `''`, `'  '`, `{}`, `null` | 400 E-08, echo `00` (`cbl:117-133`) | 4/4: 400 `Please enter a valid option number...` option `00` | PASS |
| M-04 | MEN-02 / FR-10 / AC-MEN-04 | `0`,`00`,`A`,`1A`,`A1`,`?`,` A`,` 0`,`a` | 400 E-08; echo `00`,`00`,`0A`,`1A`,`A1`,`0?`,`0A`,`00`,`0a` | 9/9 exactly as expected | PASS (MEDIUM, §7) |
| M-05 | V-3 / R-07 / AC-MEN-05 | `12`,`13`,`99` | 400 E-08 echo as typed, never 500 (§5) | 3/3: 400, echo `12`/`13`/`99` | PASS (see §5 ruling) |
| M-06 | MEN-03 / FR-11(MEN) / AC-MEN-06 | `1`,` 1`,`01`,`1 ` | 200 `COACTVWC`, `/api/accounts/{acctId}`, implemented, echo `01` (`cbl:117-125,177-187`, `tbl:25-29`) | 4/4 + DISPATCH PASS | PASS |
| M-07 | MEN-06 / Q-12, B-0032 / AC-MEN-07 | `2`..`11`, `02`, ` 2`, `11` | 200 `implemented:false`, `Option not available in this release`, program = row PGMNAME | 13/13 + FACADE PASS (COACTUPC..COPAUS0C) | PASS |
| M-08 | V-4 (dead admin guard) | all option responses | no `No access`, `is not installed`, `coming soon`, `Invalid key` | 0 files each | PASS |
| M-09 | MEN-04 / FR-12 / AC-MEN-08 | Exit / F3 / Esc; `POST /api/auth/signoff` | session invalid, `/signon` shown blank (`cbl:196-203`, `COSGN00C.cbl:80-83`) | signoff 200; `GET /api/menu` 401; `POST /select` 401; UI: all three exits -> `/signon`, direct `/menu` -> `/signon` | PASS |
| M-10 | MEN-05 / FR-25(MEN) / DV-03 / AC-MEN-09 | F1, F5, F7, F12, PageDown, Tab | no message, no `Invalid key` text (legacy E-03 `cbl:99-102`, `CSMSG01Y:20-21`) | browser-native effects only (Help, reload, caret, DevTools); menu unchanged; no text | PASS (browser caveat) |
| M-11 | Q-01 / AC-MEN-10 | ADMIN001: `GET /api/menu`, `1`, `5`, `/menu` | same 11 rows; option 1 works; banner NEW TEXT | OPTIONS identical U vs A; `1` -> COACTVWC; `5` -> facade; banner `Administration is not available in this release` exact | PASS |
| M-12 | V-7 / B-0027 / AC-MEN-11 | no cookie: `GET /api/menu`, `POST /select`, `Accept: text/html` | JSON 401 (`cbl:82-84`); SPA guard -> `/signon` | 3 × `401 {"message":"Authentication required"}`; UI `/menu` -> `/signon` | PASS |
| M-13 | B-0027 / DV-02 / AC-MEN-12 | session -> `1` -> `GET /api/accounts/00000000001` -> menu -> session | identity intact (`cbl:181-182` commented) | `USER0001/U` before and after; accounts endpoint 404 `Resource not found` (COACTVWC not yet ported — outside boundary) | PASS |
| M-14 | D-0016 / AC-MEN-13 | all bodies | `CM00` only as `header.tranId` | `CM00 outside header.tranId: none` | PASS |
| M-15 | Q-14 (target-only) | `'123'`, `1` (number), `{"option":` , `"٠١"`, extra field, empty body | deterministic 400, never 500; legacy X(2) cannot hold these | `123` -> 400 `option: must be at most 2 characters`; `1` -> 200 option 1; **malformed -> 500**; **`"٠١"` -> 200 `{"option":"٠١","program":"COACTVWC",...}`**; extra field ignored 200; **empty body -> 500** | PASS-WITH-RISK (§4) |
| M-16 | D-0040 class | `docker stop carddemo-pg`, then menu/select/session/signon | menu has no I/O -> keeps working; any 5xx mapped | menu 200, session 200, select `1` 200, `12` 400; signon 500 `Unable to verify the User ...`; all recover after `docker start` | PASS |
| M-17 | B-0029 field map | `/menu` DOM vs `bms:29-162` | §6 | §6 all rows match | PASS |
| M-18 | Q-12 UI click | click row 1, rows 2..11 | row 1 -> `/accounts/view`; 2..11 `disabled`/`aria-disabled` inert | as expected; back -> still signed in | PASS |
| M-19 | V-1 UI edits | `maxlength=2`; echoes `00`,`00`,`0A`,`12`,`99`,`02`,`11`; `123` | value after ENTER = normalised echo | all exact; `123` leaves `12` | PASS |

## 3. Failure diffs

No parity FAIL. Non-parity (target-only) defects, see §4.

## 4. Target-only defects handed back (not COBOL parity failures)

| # | Observed | Expected (FR Q-14 "request payload validated explicitly") | Evidence |
|---|---|---|---|
| T-1 | `POST /api/menu/select` with body `{"option":` or empty body -> **500** `An unexpected error occurred` | 400 with a validation message (HttpMessageNotReadableException is caught by the generic `Exception` handler) | `http_cases.out` M-15c, M-15f |
| T-2 | `{"option":"٠١"}` (Arabic-Indic digits) -> **200, dispatch to COACTVWC**, echo `٠١` (not `01`) | 400 E-08 (only ASCII `0-9` exist in `PIC 9(02)` / EBCDIC `PIC X(2)`); `Character.isDigit`/`Integer.parseInt` accept any Unicode `Nd` | `http_cases.out` M-15d |
| T-3 | Register gap: item 3 (§5) documented as V-3/R-07 in the FR but absent from DV-01..05 | add DV-06 (or an explicit decision row) | FR §5 V-3, §7 row `option 12..99`; plan §9 |

## 5. Ruling on item 3 — options 12..99 and the out-of-range subscript

Legacy sequence (`cbl:127-143`), option `12`..`99`, `U` user:
1. `:127-129` `WS-OPTION > CDEMO-MENU-OPT-COUNT` TRUE -> `:130` `WS-ERR-FLG = 'Y'`, `:131-132` E-08, `:133` `SEND-MENU-SCREEN`
   (first SEND: E-08 in `ERRMSGO`, `OPTIONO` = typed value from `:125`). **Deterministic, always happens.**
2. `:136-137` `IF CDEMO-USRTYP-USER AND CDEMO-MENU-OPT-USRTYPE(WS-OPTION) = 'A'` — evaluated unconditionally. Subscript 12 reads
   the 12th `OCCURS` slot (`tbl:94`), whose bytes are **not covered by any VALUE clause** (`tbl:25-90` define rows 1..11 only):
   contents = whatever follows in WORKING-STORAGE (start of `COMEN1AI`, `COPY COMEN01` at `cbl:53`) — arbitrary. Subscripts 13..99
   read up to 88 × 46 = 4048 bytes beyond the table. Without `SSRANGE` (CICS production default) this is a silent read; with
   `SSRANGE` it is an out-of-range abend (IGZ0006S) **after** the E-08 map was already sent.
3. If the read byte is `'A'` -> `:138-142` second `SEND MAP ... ERASE` with `No access - Admin Only option... ` overwrites E-08.
   Otherwise nothing; `:145` `IF NOT ERR-FLG-ON` is FALSE, `:191` returns with E-08 on the screen.

Legacy observable result: **E-08 with the typed echo** in every deterministic reading; a **storage-dependent** replacement by the
admin-only text or an SSRANGE abend is possible but not reproducible from source. The target (`MenuService.select`, 400 E-08 before
any table access; verified M-05: `12`/`13`/`99` -> 400 E-08, never 500) is **observably identical to the legacy's deterministic
result** and removes the non-deterministic/abend tail.

Ruling: **the target's behaviour is a deviation from the source code path, correctly described in the FR as V-3 / R-07 ("legacy
defect, not reproduced") — but it is NOT declared in DV-01..DV-05, which is the register the acceptance criteria point to. It is
therefore an UNDECLARED deviation in the DV register sense (T-3), even though no observable difference could be demonstrated for
a `U` user.** Not softened: if a customer ever reported "No access - Admin Only option" for option 12+, the target will never
reproduce it. Same applies to non-numeric subscripts (`0A` etc.): IBM `PACK`/`CVB` of non-digit zones yields an arbitrary small
subscript or a S0C7 for low nibbles > 9 — also removed by the target (§7, `cases.md` "Legacy subscript" section).

## 6. Field map — Angular `/menu` vs BMS `COMEN1A` (`bms:29-162`, `COMEN01.CPY`)

| BMS field (pos, len) | Legacy value (cbl) | Target element | Observed | Result |
|---|---|---|---|---|
| `TRNNAME` (1,7) 4 | `CM00` (`:37`, `:244`) | `[data-field=TRNNAME]` | `CM00` | match |
| `TITLE01` (1,21) 40 | `CCDA-TITLE01` | `[data-field=TITLE01]` | `AWS Mainframe Modernization` | match |
| `CURDATE` (1,71) 8 | `mm/dd/yy` (`:247-251`) | `[data-field=CURDATE]` | `09/07/26` | match |
| `PGMNAME` (2,7) 8 | `COMEN01C` (`:36`, `:245`) | `[data-field=PGMNAME]` | `COMEN01C` | match |
| `TITLE02` (2,21) 40 | `CCDA-TITLE02` | `[data-field=TITLE02]` | `CardDemo` | match |
| `CURTIME` (2,71) 8 | `hh:mm:ss` (`:253-257`) | `[data-field=CURTIME]` | `09:13:37` | match |
| title (4,x) | `Main Menu` (`bms:79`) | `.screen-title` | `Main Menu` | match |
| `OPTN001..011` 40 | `nn. name` (`:262-303`) | `[data-field=OPTN001..011]` buttons | 11 exact labels, order 1..11 | match |
| `OPTN012` 40 | never populated (`tbl:21` count 11) | absent | absent | match |
| prompt (20,x) | `Please select an option :` (`bms:144`) | `label.prompt` | exact | match |
| `OPTION` (20,41) 2, NUM UNPROT IC, JUSTIFY RIGHT ZERO | echo `OPTIONO` (`:125`) | `input[data-field=OPTION] maxlength=2 inputmode=numeric autofocus` | `maxlength="2"`, echoes `00`/`0A`/`12`/`02` after submit | match (NUM attribute = `inputmode`, not enforced — legacy 3270 blocks non-digits; target lets `A` through and returns E-08, which is the COBOL result for a non-digit) |
| `ERRMSG` (23,1) 78 BRT RED | `WS-MESSAGE` (`:215`) | `[data-field=ERRMSG][role=alert]` | empty initially; E-08 / facade text verbatim | match |
| footer (24,x) | `ENTER=Continue  F3=Exit` (`bms:162`) | `.keys` | exact (two spaces) | match |
| — | — | `ADMINBANNER` (target-only, Q-01) | only for `A` session | declared addition |
| AID | ENTER, PF3, other=E-03 | Enter/Continue, F3/Esc/Exit, others ignored (DV-03) | as designed | declared deviation |

Screenshots: `comen01c/ui/user_menu.png`, `admin_menu.png`, `invalid_*.png`, `facade_02.png`, `facade_11.png`,
`maxlength_123.png`, `escape_signon.png`, `f3_signon.png`, `exit_signon.png`, `post_exit_guard.png`, `account_view.png`.

## 7. FR-vs-COBOL and oracle discrepancies

| # | Where | Finding |
|---|---|---|
| D-1 | Orchestrator brief item 1/5 "PF3 exit -> sign-off text" vs `cbl:196-203` | The menu's PF3 is `XCTL COSGN00C` **without COMMAREA**; `COSGN00C.cbl:80-83` (`EIBCALEN = 0`) sends a blank sign-on map. The thank-you text exists only on COSGN00C's own PF3 (`COSGN00C.cbl:88-89`). The target UI (no thank-you after menu Exit) is **correct**; the brief's expectation is wrong. `ui_report.md` rows "6 * message" re-ruled PASS (addendum in that file). |
| D-2 | GnuCOBOL vs IBM for `MOVE WS-OPTION-X TO WS-OPTION` (`cbl:124`) with non-digits | GnuCOBOL (all `-std`) converts digit-wise: `'A '`->`00`, `'1A'`->`01` **and dispatches option 1**. IBM Enterprise COBOL moves the bytes unchanged, `IS NUMERIC` FALSE -> E-08 with echo `0A`/`1A`. Derivation (IBM) kept; GnuCOBOL output recorded as a disagreeing second oracle (`norm_oracle.out`). Target follows IBM. Confidence MEDIUM. |
| D-3 | FR §5 V-3 / §7 "option 12..99" vs plan DV-01..05 | Deviation described in the FR but absent from the DV register (§5, T-3). |
| D-4 | FR §7 "RECEIVE MAP failure -> payload validation (Q-14)" vs target | Malformed/empty JSON -> 500, not the "explicit validation" promised (T-1). |
| D-5 | `cbl:159-173` option 11 `INQUIRE PROGRAM` guard text `This option Pending Authorization View is not installed...` | Not reproduced; folded into the Q-12 facade text as planned (B-0032). Declared in FR; noted for completeness — option 11 answers `Option not available in this release` like 2..10. |

Customer-supplied cases that disagreed with the derivation: **none (no customer cases exist).**

## 8. PostgreSQL 16 evidence

`comen01c/pg/users_before.txt` (after import, before any sign-on) / `users_after.txt`:
```
 sec_usr_id | sec_usr_type | has_hash | has_legacy        before -> after
 ADMIN001   | A            | f -> t   | t -> f            (BCrypt upgrade-on-login by COSGN00C, wave 2 behaviour)
 USER0001   | U            | f -> t   | t -> f
 USER0002   | U            | f -> t   | t -> f            (signed on once after PG restart, M-16)
 others (ADMIN002..005, USER0003..005)  unchanged f / t
```
Tables: `accounts card_xrefs customers flyway_schema_history users`. The menu program itself issues **no SQL** (no `Hibernate:`
statements attributable to `/api/menu` in `backend_run.log`; menu kept answering with PG stopped, `pg/pg_down.out`).
Backend log: `Database: jdbc:postgresql://localhost:5433/carddemo (PostgreSQL 16.15)` (`backend_run.log`).

## 9. Untested

| Item | Reason |
|---|---|
| Legacy second-SEND / SSRANGE tail for options 12..99 and non-numeric subscripts (§5 step 2-3) | Requires the CICS runtime and actual WORKING-STORAGE layout; cannot be executed off-host (`07_runbook.md`). Ruled from source. |
| Whether Angular receives F1/F5/F7/F12 keydown before the browser consumes them | Browser-native handlers fire; no application effect observed either way (DV-03 satisfied by absence). |

## 10. Conformance skill and suites

`comen01c/conformance.out` (`run_conformance.sh` = SKILL.md lines, read-only): **24/24 PASS** (CORE 9, ONLINE 6, DATA/BOUNDARY 6,
plus build/karma). Note: the skill's endpoint grep only lists mappings with a path literal, so `@GetMapping` on `/api/menu` is not
printed — informational. Backend `mvn clean verify` (`backend_suite.log`): `Tests run: 136, Failures: 0, Errors: 0, Skipped: 0`,
BUILD SUCCESS, one `postgres:16` Testcontainer for the whole JVM (lines 96-98) — D-0040 singleton-container fix holds. Frontend
`npm run build` OK, Karma `55 of 55 SUCCESS` (`frontend_test.log`).

## 11. Evidence index and reproduction

- `comen01c/cases.md` — source-derived case list (written before reading target code) + legacy subscript analysis + GnuCOBOL note
- `comen01c/norm_oracle.cbl`, `norm_oracle.out` — GnuCOBOL replay of `cbl:117-133`
- `comen01c/run_http_cases.sh` -> `http_cases.out`, `http/*.body` — HTTP cases (36/36 `VERDICT PASS`)
- `comen01c/pg/import_run.log`, `users_before.txt`, `users_after.txt`, `pg_down.out`
- `comen01c/backend_run.log`, `backend_suite.log`, `frontend_build.log`, `frontend_test.log`
- `comen01c/run_conformance.sh` -> `conformance.out`
- `comen01c/ui/ui_report.md`, `ui/test_plan.md`, `ui/*.png`, `ui/recording.mp4`, `ui/frontend_run.log`

Reproduce: `docker run -d --name carddemo-pg -e POSTGRES_DB=carddemo -e POSTGRES_USER=carddemo -e POSTGRES_PASSWORD=carddemo -p 5433:5432 postgres:16`;
`cd backend && CARDDEMO_DB_USER=carddemo CARDDEMO_DB_PASSWORD=carddemo mvn spring-boot:run -Dspring-boot.run.profiles=import -Dspring-boot.run.arguments=--spring.main.web-application-type=none`;
`mvn spring-boot:run` (same env); `parity/wave3/comen01c/run_http_cases.sh > http_cases.out`; `cd frontend && npx ng serve` for the UI cases;
`parity/wave3/comen01c/run_conformance.sh`.
