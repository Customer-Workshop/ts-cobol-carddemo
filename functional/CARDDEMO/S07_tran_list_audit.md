# S-07 Transaction List — Independent Audit

- **Stream:** S07 (`tran_list`) — COTRN00C, CICS tran CT00, BMS map COTRN0A.
- **Audited branch/HEAD:** `devin/1789516557-carddemo-java-engagement` @ `410e5d0f826ccd3a6a2f4ea04eee673f64c240d8` (audit branch `devin/audit-s07-java`).
- **Auditor:** independent auditor (did not perform the migration). Wave under audit: PR #117 "S-07 Transaction List: screen + cursor paging parity" (merged, `devin/wave-s07-tran-list`); later touched by S-08 merge `767d0e1` (registry flag flip, expected per S07-B1).
- **Date:** 2026-09-16.
- **Scope:** `spring-boot/src/main/java` implementation of `/transactions/list` (UiController + `TransactionListService` + `transaction-list.html` + `CobolMessages`/`MenuService` additions), the stream's three test classes, `functional/CARDDEMO/S07_*` docs, legacy source `app/cbl/COTRN00C.cbl`, `app/bms/COTRN00.bms`, `app/cpy/*.cpy`.

## Verdict: **PASS with findings**

All 21 FRs are implemented; 28/28 scoped tests pass. Findings are LOW/INFO: one FR (S07-17) has implementation but no named test, test-name `frS07xx` suffixes systematically diverge from FR numbering, and two edge-of-edge semantic divergences exist versus the COBOL browse.

## 1. Traceability matrix

Impl column cites `spring-boot/src/main/java`; test names are verbatim. Note: the tests' `_frS07xx` suffixes use a numbering that matches neither `S07_functional_requirement.md` §4 nor `programs/COTRN00C_functional_requirement.md` §4 (see §6, F-02); the "tests" column below is mapped by actual test behaviour.

| FR | Requirement | Implementation | Test(s) | Status |
|---|---|---|---|---|
| FR-S07-01 | Entry guard: no COMMAREA → sign-on | `SecurityConfig.java:48` (`anyRequest().authenticated()`) + `:65` (redirect `/signon`); `UiController.transactionList` :169-173 | `TransactionListUiIntegrationTest.unsignedNavigationBouncesToSignon_frS0701` | PASS |
| FR-S07-02 | First display: page 1 from file start, blank search, re-enter flag | `TransactionListService.firstDisplay` :127-129 → `enter(Form.blank())` (LOW-VALUES start) | `TransactionListServiceTest.firstEntryBrowsesFromLowValuesAndSetsNextPage_frS0702_frS0704`; `TransactionListUiIntegrationTest.firstEntryRendersFieldMapAndFirstPage_frS0702_frS0704` | PASS |
| FR-S07-03 | ENTER + blank search → restart at lowest id, page 1 | `enter` :151-154 (`field.isBlank()` → key `""` = LOW-VALUES) → `forward` | `TransactionListServiceTest.invalidSelectionIsNonBlockingAndBrowseContinues_frS0708` (blank `trnIdIn`, asserts row 1 = lowest id, page 1). No `enterBlank*` test exists as the FR doc's covering-test column names | PASS (test-name drift, see F-02) |
| FR-S07-04 | ENTER + 16-digit id → GTEQ browse, page 1, field cleared | `enter` :155-156; `forward` :233-235 (TRNIDIN cleared) | `TransactionListServiceTest.enterWithNumericSearchPositionsTheBrowse_frS0705`; `TransactionListUiIntegrationTest.enterWithNumericSearchRepositionsBrowse_frS0705` | PASS |
| FR-S07-05 | Non-numeric 16-char field → `Tran ID must be Numeric ...`, state kept | `enter` :151-164 (`padRight` to 16, `allDigits`, early return with `Page.unchanged`, staged page 0) | `TransactionListServiceTest.shortSearchInputIsNonNumericUnderFullFieldWidth_frS0706`, `nonNumericSearchEchoesScreenAndResetsStatePageOnly_frS0706`; `TransactionListUiIntegrationTest.nonNumericSearchShowsNumericError_frS0706` | PASS |
| FR-S07-06 | Row rendering: id, `mm/dd/yy`, 26-char desc, `+99999999.99`, blanks | `row` :286-294; `formatAmount` :299-307; template :42-45 | `TransactionListServiceTest.rowFieldsFollowTheCobolEdits_frS0703` (incl. high-order-digit drop and negative sign); UI render in `firstEntryRendersFieldMapAndFirstPage` | PASS |
| FR-S07-07 | Full page + successor → page +1, next-page Y, no message | `forward` :227-235 (peek = `remaining.size() > PAGE_SIZE`) | `TransactionListServiceTest.firstEntryBrowsesFromLowValuesAndSetsNextPage_frS0702_frS0704` | PASS |
| FR-S07-08 | ENDFILE on fill/peek → bottom message, next-page N, page +1 iff ≥1 row | `forward` :227-229 (`peek ? pending : TRANSACTION_BOTTOM`; `newPage` +1 only when `remaining` non-empty) | `TransactionListServiceTest.pf8PagesForwardOverTheSkipRead_frS0710`, `rowFieldsFollowTheCobolEdits_frS0703`; `TransactionListUiIntegrationTest.pf8ThenPf7WalksTheFileWithRoundTrippedState_frS0710_frS0711` | PASS |
| FR-S07-09 | STARTBR NOTFND → `You are at the top of the page...`, rows unchanged, page 0/unchanged, next-page N | `forward` :211-218 (ENTER staged page 0 / PF8 unchanged); `backward` :247-252 | `TransactionListServiceTest.forwardBrowseNotFoundKeepsScreenAndShowsTop_frS0719`, `blankFileShowsTopOfPage_frS0719`, `backwardStartbrNotFoundEchoesTop_frS0719` | PASS |
| FR-S07-10 | PF8 + next-page Y → next 10 after last id (skip-read), search cleared | `pf8` :185-192 → `forward(skip=true)` :219 (`subList(1,…)` consumes positioned record) | `TransactionListServiceTest.pf8PagesForwardOverTheSkipRead_frS0710`; `pf8ThenPf7WalksTheFileWithRoundTrippedState_frS0710_frS0711` | PASS |
| FR-S07-11 | PF8 + next-page N → `already at the bottom`, screen preserved | `pf8` :187-188 (`Page.unchanged`) | `TransactionListServiceTest.pf8WithoutNextPageEchoesAlreadyAtBottom_frS0714` | PASS |
| FR-S07-12 | PF7 + page > 1 → previous 10 filled bottom-up; page −1 or 1; next-page Y | `pf7` :174-182 → `backward` :241-281 (`findByTranIdLessThanOrderByTranIdDesc`, `rows.set(PAGE_SIZE-1-i)`) | `TransactionListServiceTest.pf7PagesBackwardWithDescendingFill_frS0711`; `pf8ThenPf7WalksTheFileWithRoundTrippedState_frS0710_frS0711` | PASS |
| FR-S07-13 | PF7 + page ≤ 1 → `already at the top`, screen preserved, next-page forced Y | `pf7` :175-178 (flag forced to `true` before the gate — the `:242` quirk) | `TransactionListServiceTest.pf7AtTopEchoesAndForcesTheNextPageFlag_frS0713`; composed case `pf8AfterForcedTopEchoesForwardAgain` | PASS |
| FR-S07-14 | Backward ENDFILE → `reached the top`, page 1 on full fill, unchanged on partial | `backward` :264-273, :280 (`peek ? null : TRANSACTION_TOP`) | `TransactionListServiceTest.pf7PagesBackwardWithDescendingFill_frS0711`, `pf7WithFewerPredecessorsKeepsPageAndShowsTop`; integration `pf8ThenPf7Walks…` (top message) | PASS |
| FR-S07-15 | `S`/`s` on filled row → COTRN01C with selected id | `enter` :134-150 (`EnterOutcome.select`); `UiController` :208-219 (registry resolve → `redirect:…/transactions/view?tranId=` else coming-soon idiom) | `TransactionListServiceTest.selectWithSOnARowHandsOffItsTranId_frS0707` (S and s, first non-blank wins); `TransactionListUiIntegrationTest.rowSelectionSNavigatesToView_frS0707` (redirect — route registered by S-08, commit `767d0e1`) | PASS |
| FR-S07-16 | Other char on filled row → `Invalid selection. Valid value is S`, non-blocking | `enter` :146-148 (`pending` message; paging may overwrite) | `TransactionListServiceTest.invalidSelectionIsNonBlockingAndBrowseContinues_frS0708` | PASS |
| FR-S07-17 | Selection on a row without an id → ignored | `enter` :137-149 (non-blank sel with blank `tranId` → `break` before any flag evaluation; no message) | **None found** — no `selectBlankRow*` or equivalent test exercises sel-on-blank-row | PARTIAL (F-01) |
| FR-S07-18 | PF3 → main menu | `UiController` :194-196 (`redirect:/menu`) | `TransactionListUiIntegrationTest.pf3TransfersToMenu_frS0712` | PASS |
| FR-S07-19 | Unmapped AID → `Invalid key pressed. Please see below...`, redisplay unchanged | `TransactionListService.invalidAid` :195-197; `UiController` :226 (`default`); template script :74-88 maps other F-keys to raw aid values | `TransactionListServiceTest.invalidAidEchoesScreenWithInvalidKey_frS0718`; `TransactionListUiIntegrationTest.unmappedAidShowsInvalidKeyMessage_frS0718` | PASS |
| FR-S07-20 | RESP other → `Unable to lookup transaction...`, no paging | `forward` :208-210 + `backward` :255-257 (`DataAccessException` → `Page.unchanged`) | `TransactionListServiceTest.storeErrorEchoesUnableToLookup_frS0720`; `TransactionListUiStoreErrorIntegrationTest.storeErrorShowsUnableToLookupMessage_frS0720` (`@MockitoBean` repository throws) | PASS |
| FR-S07-21 | Rows in TRAN-ID byte order | `Sort.by(ASC, tranId)` :207 / `OrderByTranIdDesc` :253-254 over `tran_id` varchar PK | `TransactionListUiIntegrationTest.firstEntryRendersFieldMapAndFirstPage_frS0702_frS0704` (real repository: `>0001<`…`>0010<` present, `>0011<` absent); ordering asserted throughout `TransactionListServiceTest`. No `ordering*` named test as the FR doc promises | PASS (test-name drift, see F-02) |

## 2. COBOL parity spot-checks

Every catalogued user-visible message (FR §5) was compared verbatim, both sides cited.

| Item | COBOL (cite) | Java (cite) | Result |
|---|---|---|---|
| `Invalid selection. Valid value is S` | `COTRN00C.cbl:199` | `CobolMessages.java:143-144` `TRANSACTION_SELECTION_INVALID` | verbatim |
| `Tran ID must be Numeric ...` (note space before `...`) | `COTRN00C.cbl:214` | `CobolMessages.java:135` `TRANSACTION_ID_NOT_NUMERIC` — screen path uses this constant at `TransactionListService.java:163` | verbatim (baseline's space-less `TRANSACTION_ID_INVALID` `:72` remains only on the REST surface — see F-04) |
| `You are at the top of the page...` (STARTBR NOTFND) | `COTRN00C.cbl:608` | `CobolMessages.java:136` `TRANSACTION_AT_TOP` | verbatim |
| `You are already at the top of the page...` (PF7 page ≤1) | `COTRN00C.cbl:248` | `CobolMessages.java:137-138` `TRANSACTION_ALREADY_TOP` | verbatim |
| `You are already at the bottom of the page...` (PF8 flag N) | `COTRN00C.cbl:270` | `CobolMessages.java:139-140` `TRANSACTION_ALREADY_BOTTOM` | verbatim |
| `You have reached the bottom of the page...` (READNEXT ENDFILE) | `COTRN00C.cbl:642` | `CobolMessages.java:36-37` `TRANSACTION_BOTTOM` | verbatim |
| `You have reached the top of the page...` (READPREV ENDFILE) | `COTRN00C.cbl:676` | `CobolMessages.java:38-39` `TRANSACTION_TOP` | verbatim |
| `Unable to lookup transaction...` (RESP other ×3) | `COTRN00C.cbl:615,649,683` | `CobolMessages.java:141-142` `TRANSACTION_LOOKUP_FAILED` | verbatim |
| `Invalid key pressed. Please see below...` | `CSMSG01Y.cpy:21` (`COTRN00C.cbl:132`) | `CobolMessages.java:10` `INVALID_KEY_PRESSED` | verbatim |
| Coming-soon idiom: name emitted `DELIMITED BY SPACE` (first word only) | `COMEN01C.cbl:172-176` | `CobolMessages.optionComingSoon` `:316-322` → `This option Transactionis coming soon ...`; pinned by `MenuServiceTest.java:86` | verbatim (wave-pinned fix, per FR-S07-15 disposition) |
| Search edit: `IS NUMERIC` over full X(16) (BMS space padding rejects short input) | `COTRN00C.cbl:206-219` | `TransactionListService.java:151-156` (`padRight` to 16 + `allDigits`) | equivalent; `"12"` and `"12AB"` both fail as source does |
| Blank search → LOW-VALUES | `COTRN00C.cbl:206-207` | `TransactionListService.java:153-154` (key `""` → `>=` all keys) | equivalent |
| PF8 key: TRNID-LAST or HIGH-VALUES | `COTRN00C.cbl:259-263` | `TransactionListService.java:32,190` (`"￿"×16` sentinel) | equivalent (see F-05 note on collation) |
| Skip-read consuming the positioned record on non-ENTER AID | `COTRN00C.cbl:285-287` | `forward` `skip` flag :202,219 | equivalent (called only from `pf8`) |
| Fill loop 10 READNEXT + peek decides NEXT-PAGE | `COTRN00C.cbl:297-320` | `forward` :220-235 | equivalent — fetch size `skip+PAGE_SIZE+1`, peek boolean = `size>10` |
| Backward fill rows 10→1 + peek page rule (−1 or clamp 1) | `COTRN00C.cbl:349-369` | `backward` :258-273 | equivalent |
| PF7 forces NEXT-PAGE-YES before the page gate | `COTRN00C.cbl:242` | `pf7` :175-176 | equivalent (quirk preserved) |
| Page +1 only when ≥1 row shown on partial fill | `COTRN00C.cbl:314-320` | `forward` :229 (`remaining.isEmpty() ? 0 : 1`) | equivalent |
| TRNID-FIRST = row1 id, TRNID-LAST = row10 id only when filled | `COTRN00C.cbl:392-393,438-439` | `forward` :230-232; `backward` :274-276 | equivalent |
| Date `mm/dd/yy` from TRAN-ORIG-TS yyyy(3:2)/mm/dd | `COTRN00C.cbl:384-388`; `CSDAT01Y.cpy` | `row` :290 (`DateTimeFormatter "MM/dd/yy"`) | equivalent |
| Amount `PIC +99999999.99` (sign, 8 int digits, high-order drop) | `COTRN00C.cbl:56,383` | `formatAmount` :299-307 (`%100_000_000` drop; sign always) | equivalent (verified incl. `123456789.12 → +23456789.12` in test) |
| Description first 26 chars of X(100) | `COTRN00C.cbl:395`, map width | `row` :291-292 (`substring(0,min(26,len))`) | equivalent |
| Page number `9(08)` → X(8) zero-filled | `COTRN00C.cbl:65,324` | `formatPage` :309-311 (`%08d`) | equivalent |
| TRNIDIN cleared on forward path only; kept on numeric error / PF7 | `COTRN00C.cbl:325,227-229`; `backward` sends without clear | `forward` :234 (`""`); `enter` :160-164 echoes; `backward` :278 echoes `form.searchId()` | equivalent |
| AID dispatch ENTER/PF3/PF7/PF8/other | `COTRN00C.cbl:119-134` | `UiController` :194-227 + template keydown script :74-88 | equivalent |
| Selection scan: first non-blank SEL wins; `S`/`s` → XCTL; else non-blocking msg | `COTRN00C.cbl:148-203` | `enter` :134-150 (`charAt(0)` = X(1) truncation; `pending` overwritten by later messages like source's `WS-MESSAGE`) | equivalent |
| Screen literals: `List Transactions`, `Search Tran ID:`, `Sel`, ` Transaction ID `, `  Date  `, desc/amount headings, `Type 'S' to View…`, `ENTER=Continue  F3=Back  F7=Backward  F8=Forward`, `Page:` | `app/bms/COTRN00.bms:79,94,107,112,444-448,454-458,84` | `transaction-list.html` :10-11,15,22-26,55,63 | verbatim |
| Header `Tran:`/`Prog:`/`Date:`/`Time:` + titles | `COTRN00C.cbl:567-586`; `COTTL01Y.cpy` TITLE01 `AWS Mainframe Modernization`, TITLE02 `CardDemo` | `layout.html` :11-15 renders Tran/Prog/title/date/time; TITLE01 not rendered | per FR §11 assumption 4 (header/titles demoted; identifiers static) — compliant |
| Entry guard EIBCALEN=0 → COSGN00C | `COTRN00C.cbl:107-109` | `SecurityConfig.java:48,65` bounce to `/signon` | equivalent (S07-B4) |
| Exit XCTLs: PF3→COMEN01C, S→COTRN01C | `COTRN00C.cbl:122-124,186-195` | `UiController` :195 (`redirect:/menu`), :212-219 (registry) | equivalent (S07-B1/B2) |
| TRANSACT browse-only KSDS | `COTRN00C.cbl:593-696` (STARTBR/READNEXT/READPREV/ENDBR) | `TransactionRepository` :10-12 (no write path used by the service) | equivalent |
| Exit/abend codes | none catalogued for this program beyond RESP protocol | `DataAccessException` → `TRANSACTION_LOOKUP_FAILED` (RESP other) | no exit codes to port |

## 3. Stub/placeholder sweep

Searched `TransactionListService.java`, `UiController.java`, `transaction-list.html`, `CobolMessages.java` (S-07 block), `MenuService.java`, `TransactionService.java`, `TransactionController.java`, and the three stream test classes for: `TODO`, `FIXME`, `XXX`, `not implemented`, `UnsupportedOperationException`, `@Disabled`, `@Ignore`, commented-out assertions, trivially-true assertions, and hardcoded happy-path returns.

**Result: none found.** No TODOs, no disabled/ignored tests, no `UnsupportedOperationException`; every test asserts concrete values/messages; the service is a real state machine (no canned responses). The deliberate "coming soon" fallback in `UiController` :216-219 is the documented S07-B1 seam, not a stub.

## 4. Documented deviations vs shipped code

| ID | Documented decision (plan §3 / wave PR #117) | Shipped | Compliant? |
|---|---|---|---|
| S07-B1 | `S` selection resolves COTRN01C via `MenuService.uiRouteForProgram`; not browsable → coming-soon idiom; browsable → `redirect:/transactions/view?tranId=` | `UiController.java:208-219`; route registered in `MenuService.java:112` (flipped by S-08 `767d0e1`, as the boundary table predicted); coming-soon wording fixed to `DELIMITED BY SPACE` parity (`CobolMessages.java:316-322`) | yes |
| S07-B2 | PF3 → `redirect:/menu` | `UiController.java:194-196` | yes |
| S07-B3 | Reuse `TransactionRepository` browse (STARTBR/READNEXT/READPREV via `>=`/`<` + Sort); store failure → `Unable to lookup transaction...`, state unchanged | `TransactionListService.java:204-209,245-257`; repository methods at `TransactionRepository.java:10-12`; `Page.unchanged` on failure | yes |
| S07-B4 | Unsigned UI navigation → `/signon`; API stays 401 | `SecurityConfig.java:44-48,65` | yes |
| S07-B5 | Client-held paging-state record (first/last id, page num, next-page flag) as hidden fields; tampered/missing → fresh-entry degradation | `transaction-list.html` :58-62; `Form.state()` :52-60 (unparseable → 0/blank/N); `tamperedPagingStateDegradesToFreshValues` test | yes |
| Plan §10 risk 5 | REST `GET /api/transactions` filter edit to tighten `\d{1,16}`+zero-pad → all-16 NUMERIC ("required for FR-S07-05 parity") | **Not shipped** — `TransactionService.list` still accepts `\d{1,16}` and zero-pads (`TransactionService.java:58-66`); PR #117 explicitly says "GET /api/transactions REST contract unchanged"; FR doc §9 also says unchanged | inconsistent narrative — see F-03 (LOW) |
| FR §7 demotions | pseudo-conversational RETURN, intermediate SENDs, header date/time mechanics, ENDBR-after-failed-STARTBR INVREQ — all demoted | no equivalents shipped; final-screen outcomes preserved | yes (documented) |

## 5. Test evidence

Command (repo `spring-boot/`, `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`):

```
mvn -q test -Dtest='TransactionListServiceTest,TransactionListUiIntegrationTest,TransactionListUiStoreErrorIntegrationTest'
```

Observed surefire counts (`target/surefire-reports/*.txt`):

| Class | Run | Failures | Errors | Skipped |
|---|---|---|---|---|
| `com.carddemo.service.TransactionListServiceTest` | 19 | 0 | 0 | 0 |
| `com.carddemo.TransactionListUiIntegrationTest` | 8 | 0 | 0 | 0 |
| `com.carddemo.TransactionListUiStoreErrorIntegrationTest` | 1 | 0 | 0 | 0 |
| **Total** | **28** | **0** | **0** | **0** |

Matches PR #117's claim of 28 stream tests. Integration tests run MockMvc over the real repository on H2 (`tranlistuitest`, `tranlisterrortest` named in-memory DBs).

## 6. Findings

| # | Severity | Finding | Evidence | Recommendation |
|---|---|---|---|---|
| F-01 | LOW | FR-S07-17 (selection on a row without an id is ignored) is implemented but has no named test. The FR doc's covering-test column names `selectBlankRow*`, which does not exist. | Impl: `TransactionListService.java:137-149` (non-blank sel + blank `tranId` → `break`, no message). No matching test in any of the three stream test classes. | Add a small unit test: sel `'X'` on a blank row, ENTER → no selection message, normal browse. |
| F-02 | LOW | Test-name `frS07xx` suffixes systematically diverge from FR-S07-xx numbering in both FR docs, impeding traceability. E.g. `rowSelectionSNavigatesToView_frS0707` covers FR-S07-15; `pf3TransfersToMenu_frS0712` covers FR-S07-18; `invalidAidEchoesScreenWithInvalidKey_frS0718` covers FR-S07-19; the three NOTFND tests carry `_frS0719` for FR-S07-09; `pf8WithoutNextPageEchoesAlreadyAtBottom_frS0714` covers FR-S07-11. Separately, the FR doc §4 covering-test column names (`enterBlank*`, `rowMapping*`, `enterNumericKey*`, `enterNonNumeric*`, `enterFullPage*`, `ordering*`) do not exist under those names. | `TransactionListServiceTest.java` / `TransactionListUiIntegrationTest.java` method names vs `S07_functional_requirement.md` §4/§9 and `programs/COTRN00C_functional_requirement.md` §4 mapping table. | Rename suffixes to the real FR-S07 ids, or document the numbering the suffixes follow; update the FR doc covering-test column to the actual test names. |
| F-03 | LOW | Plan §10 risk 5 calls the REST filter tightening to all-16 NUMERIC "required for FR-S07-05 parity"; shipped REST still accepts `\d{1,16}` with zero-padding (`Tran ID must be Numeric...` — also still the space-less literal). FR doc §9 and PR #117 describe the REST contract as unchanged, so the plan is internally inconsistent, and the screen path is correct regardless. | `TransactionService.java:58-66` (`filter.matches("\\d{1,16}")` + `"%016d"` zero-pad; `TRANSACTION_ID_INVALID` at `CobolMessages.java:72`); plan `S07_tran_list_migration_plan.md` §10.5 vs FR §9. | Amend the plan's risk wording to say the tightening applies to the screen edit only, or ship the REST tightening deliberately with a deviation note. |
| F-04 | LOW | Backward browse with a positioned record but zero predecessors blanks all 10 rows; the source would echo the previous rows (skip-read READPREV hits ENDFILE before INITIALIZE, so rows are never cleared — `COTRN00C.cbl:339-347`). Reachable only with inconsistent client-held state or concurrent deletion of all lower keys (plan risk 6 accepts cursor shifts, not row blanking). | `TransactionListService.java:253-263` — `predecessors` empty → `fill=0` → all-`BLANK` `rows` returned, vs COBOL echo. | Return `Page.unchanged`-style row echo when `fill == 0`, or record the divergence as a documented demotion. |
| F-05 | INFO | On the numeric-error path the Java code returns before calling the repository at all; the source still executes STARTBR (`COTRN00C.cbl:281`) and a store failure there would surface `Unable to lookup transaction...` over the numeric message. Compound edge only; FR §7's claim "final message is the numeric error" holds only when STARTBR succeeds. | `TransactionListService.java:157-164` (early return, no repository call) vs `COTRN00C.cbl:281-283,612-618`. | No action needed unless store-failure-during-edit parity is desired; optionally note it in FR §7. |
| F-06 | INFO | `HIGH_VALUES` sentinel `"￿"×16` (`TransactionListService.java:32`) relies on DB collation sorting U+FFFF above digits — true under byte/C collation (H2 tests, Postgres `C`/`C.UTF-8`); under a non-C collation the PF8-from-blank-last-id edge could behave differently. Only reachable via tampered state (nextPage=Y implies a full page was shown, which always sets lastId). | `TransactionListService.java:31-32,190`. | None required; note it if the Postgres profile ever adopts a non-C collation. |
