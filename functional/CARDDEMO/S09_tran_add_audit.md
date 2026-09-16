# S09 — tran_add — Independent Audit

- **Stream:** S09 (tran_add) — COTRN02C (`app/cbl/COTRN02C.cbl`, CICS tran CT02, map COTRN2A, menu option 8) + shared utility CSUTLDTC (`app/cbl/CSUTLDTC.cbl`)
- **Audited branch:** `devin/1789516557-carddemo-java-engagement` on audit branch `devin/audit-s09-java`
- **HEAD sha:** `410e5d0f826ccd3a6a2f4ea04eee673f64c240d8`
- **Auditor:** independent auditor (did not perform the migration)
- **Date:** 2026-09-16
- **Scope:** `spring-boot/src/main/java/com/carddemo/` classes implementing CT02 (`service/TransactionService.java`, `service/DateValidationService.java`, `service/DateValidationResult.java`, `service/TransactionIdGenerator.java`, `api/TransactionController.java`, `api/TransactionAddScreen.java`, `api/TransactionCreateRequest.java`, `api/CobolMessages.java`, `ui/UiController.java` tran-add handlers, `templates/transaction-add.html`, plus touch-points in `security/SecurityConfig.java` and `service/MenuService.java`) and the stream's tests (`service/TransactionAddServiceTest.java`, `service/DateValidationServiceTest.java`, `TransactionAddIntegrationTest.java`, `TranAddUiIntegrationTest.java`), audited against `functional/CARDDEMO/S09_functional_requirement.md` (FR-S09-01..32 + §5 message catalogue), `S09_tran_add_migration_plan.md` (boundary table S09-B1..B6, deviations D-1..D-3), `app/cbl/`, `app/cpy/`, `app/cpy-bms/`, `app/bms/`. Wave PR: #118 (merged).

## Verdict: **PASS with findings**

Every FR-S09 row is implemented and traced to at least one named test; all 35 catalogue messages are verbatim; all 63 scoped tests pass. Two PARTIAL trace rows (FR-S09-24, FR-S09-28) plus several LOW/INFO items are listed in §6. No CRITICAL or HIGH findings.

## 1. Traceability matrix

Java references are in `spring-boot/src/main/java/com/carddemo/` unless noted. Tests live in `spring-boot/src/test/java/com/carddemo/` (unit tests under `service/`).

| FR | Requirement (short) | Implementation | Named test(s) | Status |
|----|--------------------|----------------|---------------|--------|
| FR-S09-01 | First entry: blank 14-field screen, cursor Acct #, auth bounce | `UiController` GET `/transactions/add` (:612-620); `TransactionService.blank()`-backed `TransactionAddScreen.blank()` (cursor `accountId`); `SecurityConfig` `anyRequest().authenticated()` | `TranAddUiIntegrationTest.addScreenRendersTheCotrn2aInputMap_frS0901`, `.unsignedEntryBouncesToSignon_frS0901`, `.menuOptionEightRoutesToTheAddScreen_frS0901` | PASS |
| FR-S09-02 | Both keys blank → `Account or Card Number must be entered...`, cursor Acct | `TransactionService.validateKeyFields` :210-213 (`TRANSACTION_ACCOUNT_OR_CARD_REQUIRED`, CobolMessages.java:77) | `TransactionAddServiceTest.bothKeysBlankYieldsKeyRequired_frS0902` | PASS |
| FR-S09-03 | Acct present, not 11 digits → `Account ID must be Numeric...` | `validateKeyFields` :214-216 (`allDigits`, `TRANSACTION_ACCOUNT_NUMERIC` CobolMessages.java:103) | `TransactionAddServiceTest.shortAccountYieldsAccountNumeric_frS0903`; `TransactionAddIntegrationTest.businessRejectionStaysOnTheScreenWithTheCobolMessage_frS0903` | PASS |
| FR-S09-04 | Acct found in CXACAIX → Card # filled; account path wins | `validateKeyFields` :217-226 `cardXrefRepository.findByXrefAcctId` (ordered lowest-card-first, CardXrefRepository.java `@Query ... ORDER BY x.xrefCardNumber`) | `TransactionAddServiceTest.accountResolutionFillsCard_frS0904`, `.accountPathWinsOverTypedCard_frS0904`; `TransactionAddIntegrationTest.accountResolutionEchoesTheXrefCard_frS0904` | PASS |
| FR-S09-05 | Acct not in xref → `Account ID NOT found...` | `validateKeyFields` :220-222 (`TRANSACTION_ACCOUNT_NOT_FOUND` CobolMessages.java:79) | `TransactionAddServiceTest.unknownAccountYieldsNotFound_frS0905` | PASS |
| FR-S09-06 | Acct lookup store error → `Unable to lookup Acct in XREF AIX file...` | `validateKeyFields` catch `DataAccessException` → `TRANSACTION_ACCOUNT_LOOKUP_FAILED` (CobolMessages.java:104-105) | `TransactionAddServiceTest.accountLookupErrorYieldsUnableToLookup_frS0906` | PASS |
| FR-S09-07 | Card present, not 16 digits → `Card Number must be Numeric...` | `validateKeyFields` :228-230 (`TRANSACTION_CARD_INVALID` CobolMessages.java:81) | `TransactionAddServiceTest.nonNumericCardYieldsCardNumeric_frS0907` | PASS |
| FR-S09-08 | Card found → Acct # filled, continue | `validateKeyFields` `cardXrefRepository.findById(card)` :231-240 | `TransactionAddServiceTest.cardResolutionFillsAccount_frS0908` | PASS |
| FR-S09-09 | Card not found → `Card Number NOT found...` | `validateKeyFields` :234-236 (`TRANSACTION_CARD_NOT_FOUND` CobolMessages.java:82) | `TransactionAddServiceTest.unknownCardYieldsNotFound_frS0909` | PASS |
| FR-S09-10 | Card lookup store error → `Unable to lookup Card # in XREF file...` | `validateKeyFields` catch → `TRANSACTION_CARD_LOOKUP_FAILED` (CobolMessages.java:106-107) | `TransactionAddServiceTest.cardLookupErrorYieldsUnableToLookup_frS0910` | PASS |
| FR-S09-11 | Mandatory fields in screen order, first blank wins | `TransactionService.validateDataFields` :252-286 (Type→Cat→Source→Desc→Amount→Orig→Proc→MerchID→Name→City→Zip; CobolMessages.java:83-93) | `TransactionAddServiceTest.blankDataFieldsFailInScreenOrder_frS0911`; `TranAddUiIntegrationTest.enterWithBlankFieldRedisplaysVerbatimError_frS0911` | PASS |
| FR-S09-12 | Type/CD non-digit → `... must be Numeric...` | `validateDataFields` :287-293 (`allDigits`) | `TransactionAddServiceTest.nonDigitTypeYieldsTypeNumeric_frS0912`, `.shortCategoryYieldsCategoryNumeric_frS0912` | PASS |
| FR-S09-13 | Amount not `[+-]dddddddd.dd` → `Amount should be in format -99999999.99` | `amountLayout` :294-295, :481-487; over-width rejected at API edge `padAtApi` :443-452 | `TransactionAddServiceTest.malformedAmountYieldsFormatMessage_frS0913`, `.overWidthAmountIsRejectedAtTheApiEdge_frS0913`; `TransactionAddIntegrationTest.overWidthFieldIsRejectedAtTheEdge_frS0913` | PASS |
| FR-S09-14 | Orig/Proc not `dddd-dd-dd` (orig first) | `dateLayout` :297-302, :489-497 | `TransactionAddServiceTest.malformedDatesYieldLayoutMessage_frS0914` | PASS |
| FR-S09-15 | Amount echoed `+99999999.99` | `fields.amount = echoAmount(...)` :305; `echoAmount` `"%+012.2f"` :477-479 | `TransactionAddServiceTest.validAmountEchoesInPaddedNumericForm_frS0915` | PASS |
| FR-S09-16 | CSUTLDTC severity≠0000 & msg≠2513 → `... - Not a valid date...` | `validateDate` :326-339 calling `DateValidationService.validate` | `TransactionAddServiceTest.csutldtcRejectsStructurallyBadDates_frS0916` | PASS |
| FR-S09-17 | Merchant ID non-digit → `Merchant ID must be Numeric...` | `validateDataFields` :316-318 | `TransactionAddServiceTest.nonDigitMerchantIdYieldsMerchantNumeric_frS0917` | PASS |
| FR-S09-18 | Confirm blank/N/n → `Confirm to add this transaction...`, values kept | `processEnter` :196-203 (`equalsIgnoreCase("N")`/empty) | `TransactionAddServiceTest.blankOrNConfirmPromptsAndKeepsValues_frS0918` | PASS |
| FR-S09-19 | Confirm other → `Invalid value. Valid values are (Y/N)...` | `processEnter` :204-206 | `TransactionAddServiceTest.otherConfirmValueYieldsInvalidValue_frS0919` | PASS |
| FR-S09-20 | Confirm Y/y → next sequential TRAN-ID, 16-digit | `processEnter` :198 `writeTransaction` :373-409; `TransactionIdGenerator.nextId()` (`findTopByOrderByTranIdDesc`, `"%016d"`, empty→`0000000000000001`) | `TransactionAddServiceTest.confirmedAddWritesNextSequentialId_frS0920`, `.confirmedAddOnEmptyFileWritesIdOne_frS0920`; `TransactionAddIntegrationTest.enterWritesTheRowAndReturnsTheScreenState_frS0920_21` | PASS |
| FR-S09-21 | Write OK → all fields cleared, green `Transaction added successfully.  Your Tran ID is <id>.` | `writeTransaction` success path :390-408 (blank screen + `CobolMessages.transactionAdded` :371-374 + `messageStyle "info"` → `.message-line.info` green, carddemo.css:140) | `TransactionAddServiceTest.successfulWriteClearsFieldsAndShowsGreenMessage_frS0921`; `TranAddUiIntegrationTest.enterWithValidFieldsWritesAndShowsGreenMessage_frS0921` | PASS |
| FR-S09-22 | DUPKEY/DUPREC → `Tran ID already exist...`, cursor Acct | `writeTransaction` catch `DataIntegrityViolationException` → `TRANSACTION_DUPLICATE` :382-386 | `TransactionAddServiceTest.duplicateKeyYieldsAlreadyExists_frS0922` | PASS (integration-level gap noted in §6 F-5) |
| FR-S09-23 | Other write failure → `Unable to Add Transaction...` | `writeTransaction` catch `DataAccessException` → `TRANSACTION_ADD_FAILED` :387-389 | `TransactionAddServiceTest.otherWriteErrorYieldsUnableToAdd_frS0923` | PASS |
| FR-S09-24 | Highest-id browse fails: NOTFND → `Transaction ID NOT found...`; other → `Unable to lookup Transaction...` | `copyLastInto` :344-350 — `DataAccessException` → `TRANSACTION_ADD_LOOKUP_FAILED` only. **No trigger emits `Transaction ID NOT found...`** (constant exists at CobolMessages.java:73, used only by the view flow); `findTopByOrderByTranIdDesc()` null is treated as "empty file", not NOTFND | `TransactionAddServiceTest.browseLastErrorYieldsUnableToLookup_frS0924` (covers the second half only) | **PARTIAL** → F-2 |
| FR-S09-25 | PF3 → return to caller (menu) | `UiController` POST `/transactions/add` aid map PF3 → `redirect:/menu` (:644-646) | `TranAddUiIntegrationTest.pf3ReturnsToTheMenu_frS0925` | PASS |
| FR-S09-26 | PF4 → clear all fields + message | UiController PF4 → `TransactionAddScreen.blank()` | `TranAddUiIntegrationTest.pf4ClearsFieldsAndMessage_frS0926` | PASS |
| FR-S09-27 | PF5 + valid key → data fields from highest-id row, keys/confirm kept | `TransactionController` POST `/api/transactions/copy-last` → `copyLast`; `copyLastInto` :351-366 (truncates via `first()`/`%04d`/`%09d`/`echoAmount`/`dateText`); UiController PF5 | `TransactionAddServiceTest.copyLastFillsDataFieldsFromHighestId_frS0927`, `.copyLastTruncatesToScreenWidths_frS0927`, `.copyLastWithConfirmYWritesImmediately_frS0927`; `TranAddUiIntegrationTest.pf5CopiesTheLastTransaction_frS0927`; `TransactionAddIntegrationTest.copyLastReturnsTheHighestIdFields_frS0927` | PASS |
| FR-S09-28 | PF5 on empty file → copied fields blank → `Type CD can NOT be empty...` | `copyLastInto` :351 `if (last != null)` — **no else**: when no row exists, already-typed data fields are left intact; COBOL overwrites all data fields with the (blank) record whenever `NOT ERR-FLG-ON` (COTRN02C.cbl:480-493) | `TransactionAddServiceTest.copyLastOnEmptyFileYieldsTypeRequired_frS0928` (all-null fields only — does not exercise the divergence) | **PARTIAL** → F-1 |
| FR-S09-29 | Any other AID → `Invalid key pressed. Please see below...`, state preserved | UiController default branch → `preserved(request, INVALID_KEY_PRESSED)` (CobolMessages.java:10; literal from CSMSG01Y.cpy:20-21) | `TranAddUiIntegrationTest.unmappedFunctionKeyPreservesState_frS0929` | PASS |
| FR-S09-30 | `CDEMO-CT02-TRN-SELECTED` → pre-fill Card #, run ENTER immediately | UiController GET `?cardNumber=` → `transactionService.enter` at once (:612-620) | `TranAddUiIntegrationTest.preSelectedCardRunsEnterImmediately_frS0930` | PASS |
| FR-S09-31 | CSUTLDTC → 80-byte result, severity/mask/verdict | `DateValidationService.validate` + `resultText` :189-196 (exact 80-byte `WS-MESSAGE` layout), `DateValidationResult` | `DateValidationServiceTest`: `validDatesReturnSuccess_frS0931`, `dayFaultReturns2508_frS0931`, `monthFaultReturns2517_frS0931`, `nonDigitByteReturns2520_frS0931`, `shortDateReturns2507_frS0931`, `badMaskReturns2518_frS0931`, `maskTokensReordered_frS0931`, `resultTextIsEightyBytes_frS0931` | PASS |
| FR-S09-32 | Pre-1582-10-15 → 2513; accepted when parsed (year 0000 → null → rejected, D-2) | `DateValidationService` LILLIAN_MIN=1582-10-15 :28; `parsed==null` iff year==0; `validateDate` :329-330 accepts `2513 && parsed!=null` | `DateValidationServiceTest.preLillianDateReturns2513WithParsedDate_frS0932`, `.lillianBoundaryIsInclusive_frS0932`, `.yearZeroReturns2513WithNoParsedDate_frS0932`; `TransactionAddServiceTest.preLillianDateIsAccepted_frS0932`, `.yearZeroDateIsRejected_frS0932` | PASS |

## 2. COBOL parity spot-checks

All 35 §5-catalogue messages verified verbatim, Java literal vs `app/cbl/COTRN02C.cbl` source line:

| Message | COBOL | Java |
|---|---|---|
| `Account or Card Number must be entered...` | COTRN02C.cbl:201 | CobolMessages.java:77-78 |
| `Account ID must be Numeric...` | :199 | :102-103 |
| `Card Number must be Numeric...` | :213 | :81 |
| `Account ID NOT found...` | :593 | :79-80 |
| `Unable to lookup Acct in XREF AIX file...` | :600 | :104-105 |
| `Card Number NOT found...` | :626 | :82 |
| `Unable to lookup Card # in XREF file...` | :633 | :106-107 |
| `Type CD can NOT be empty...` | :254 | :83 |
| `Category CD can NOT be empty...` | :260 | :84 |
| `Source can NOT be empty...` | :266 | :85 |
| `Description can NOT be empty...` | :272 | :86 |
| `Amount can NOT be empty...` | :278 | :87 |
| `Orig Date can NOT be empty...` | :284 | :88 |
| `Proc Date can NOT be empty...` | :290 | :89 |
| `Merchant ID can NOT be empty...` | :296 | :90 |
| `Merchant Name can NOT be empty...` | :302 | :91 |
| `Merchant City can NOT be empty...` | :308 | :92 |
| `Merchant Zip can NOT be empty...` | :314 | :93 |
| `Type CD must be Numeric...` | :325 | :95 |
| `Category CD must be Numeric...` | :331 | :97 |
| `Amount should be in format -99999999.99` | :345 | :108-109 |
| `Orig Date should be in format YYYY-MM-DD` | :360 | :98-99 |
| `Proc Date should be in format YYYY-MM-DD` | :375 | :100-101 |
| `Orig Date - Not a valid date...` | :401 | :110-111 |
| `Proc Date - Not a valid date...` | :421 | :112-113 |
| `Merchant ID must be Numeric...` | :432 | :114-115 |
| `Confirm to add this transaction...` | :178 | :94 |
| `Invalid value. Valid values are (Y/N)...` | :184 | :116-117 |
| `Transaction added successfully.  Your Tran ID is <id>.` (two spaces before "Your") | :727-732 (STRING `'...successfully. '` + `' Your Tran ID is '`) | `transactionAdded` :371-374 |
| `Transaction ID NOT found...` | :657 | CobolMessages.java:73 — **unreachable from add flow** (F-2) |
| `Unable to lookup Transaction...` | :664, :693 | :118-119 |
| `Tran ID already exist...` | :738 | :120 |
| `Unable to Add Transaction...` | :745 | :121 |
| `Invalid key pressed. Please see below...` | app/cpy/CSMSG01Y.cpy:20-21 | CobolMessages.java:10 |

Edit/validation rules:

- **Edit order** identical: key fields (acct first — account path wins when both typed, :216 vs COTRN02C.cbl:196-230 — "entered both" is unreachable in COBOL because the EVALUATE acct-branch consumes control first) then data fields in screen order with first-failure-wins (validateDataFields :252-319 vs :251-436).
- **Padded-field semantics**: API inputs padded/truncated to 3270 widths via `padAtApi` :443-452 (over-width → HTTP 400 = deviation D-3); class tests run against the padded value, matching COBOL's PIC X field tests.
- **Amount layout**: `amountLayout` :481-487 enforces `[+-]dddddddd.dd`; echo via `"%+012.2f"` :477-479 reproduces `NUMVAL-C` → `PIC +99999999.99` (:383-386, WS-EDIT-AMT :329-335).
- **Date layout**: `dddd-dd-dd` positional check :489-497 vs :353-381; then CSUTLDTC port (`DateValidationService`) — CEEDAYS emulation verified: 80-byte result text byte-exact against CSUTLDTC.cbl:42-57 WS-MESSAGE layout (`resultText` :189-196), verdict literals X15-padded matching :128-148, severity as return code (:98 → `Integer.parseInt(severity)`), classification order non-digit-byte→2520, month→2517, day→2508 (leap-aware), range→2513, mask→2518, short→2507 (plan order, S09-B4).
- **2513 exemption**: `validateDate` :329-330 accepts severity 0000 or message 2513 with parsed date — matches COTRN02C.cbl:389-427 (`IF WS-SEVERITY = '0000' OR WS-MESSAGE-NO = '2513'`); year-0000 → 2513 + parsed=null → rejected (D-2, implemented + tested).
- **Confirm machine**: Y/y write; N/n/blank/empty → confirm prompt; else `Invalid value` — `processEnter` :196-206 vs COTRN02C.cbl:169-188 (LOW-VALUES ≈ null/empty).
- **Write path**: next-id = highest+1 zero-padded, `DataIntegrityViolationException`→DUPREC message, other `DataAccessException`→`Unable to Add...` — :373-409 vs :711-749. Success clears all fields + green message + cursor Acct #, matching INITIALIZE-ALL-FIELDS + NORMAL branch (:723-739).
- **File/DB access**: CXACAIX keyed read → `findByXrefAcctId` ordered lowest-card-first (AIX dup-key order, CardXrefRepository.java `@Query`); CCXREF → `findById`; STARTBR/READPREV highest-key → `findTopByOrderByTranIdDesc`; WRITE → `saveAndFlush`.
- **AID table**: ENTER/PF3/PF4/PF5/other — UiController :644-660 vs :133-152. PF3 also fires post-success (:497-511 XCTL) — Java redirects same way.
- **BMS surface**: 14 inputs with exact BMS maxlengths (copybook `app/cpy-bms/COTRN02.CPY`: 11,16,2,4,10,60,12,10,10,9,30,25,10,1); title `'Add Transaction'` (bms:79), `'Enter Acct #:'` (:84), `'(or)'` (:98), `'Card #:'` (:103), hints `(-99999999.99)` (:212), `(YYYY-MM-DD)` (:217/:222), `(Y/N)` (:292), legend `ENTER=Continue  F3=Back  F4=Clear  F5=Copy Last-Tran.` (:301-303) — all verbatim. Label spacing deltas noted in §6 F-4.
- **Exit/abend**: no ABEND in this program's normal paths; PF3 XCTL→COSGN00C/`CDEMO-FROM-PROGRAM` mapped to redirect `/menu` (S09-B1). No exit code surface unported.
- **Derivations sampled**: TRAN-ID `%016d` = highest+1 (TransactionIdGenerator); `TRAN-CAT-CD` 9(4)→`%04d`; `TRAN-MERCHANT-ID` 9(9)→`%09d`; timestamps stored at midnight (`LocalDateTime.of(date, LocalTime.MIDNIGHT)`) per S09-B6; copy-last truncations `first(..., n)` match MOVE-to-smaller-field semantics.

## 3. Stub/placeholder sweep

Searched `spring-boot/src/main/java` (stream classes) and `spring-boot/src/test/java` (stream tests) for `TODO`, `FIXME`, `not implemented`, `UnsupportedOperationException`, `@Disabled`, `assertTrue(true)`, commented-out assertions, hardcoded happy-path returns:

- `grep -rni "TODO\|FIXME\|not implemented\|UnsupportedOperationException\|@Disabled"` over the stream's impl+test classes: **no matches**.
- All 4 test classes: every `@Test` has real assertions (Mockito/AssertJ/MockMvc `andExpect` chains); no ignored or trivially-true assertions found.
- `TransactionService`, `DateValidationService`, `TransactionIdGenerator`: no constant-return or stubbed branches; every COBOL branch has a real implementation.
- `TransactionAddScreen.preserved()` (invalid-AID state retention) is real logic, not a stub.

**Result: clean.**

## 4. Documented deviations vs shipped code

| Deviation | Documented | Shipped? | Evidence |
|---|---|---|---|
| S09-B1: UI route + session guard; bounce→`/signon`; `?cardNumber=` pre-fill; menu flag flip `COTRN02C`→`/transactions/add` | yes | **yes** | `SecurityConfig` `anyRequest().authenticated()`; `unsignedEntryBouncesToSignon_frS0901` shows redirect; `MenuService.UI_ROUTES` entry `"COTRN02C"→"/transactions/add"` (:113); menu row option(8,...) (:45); `?cardNumber=` path tested |
| S09-B2: baseline xref reads; empty→NOT-FOUND msgs; `DataAccessException`→lookup msgs | yes | **yes** | `CardXrefRepository` `findByXrefAcctId` + `findById`; messages verbatim |
| S09-B3: browse→`findTopByOrderByTranIdDesc`; 23505→`Tran ID already exist...`; other→`Unable to Add...`; not idempotent, concurrent adders collide on PK | yes | **mostly** | `TransactionService` :344-409 implements; but "browse errors → `Transaction ID NOT found...`" half has no trigger (F-2); promised Testcontainers dup-PK/concurrency tests not shipped (F-5) |
| S09-B4: port CSUTLDTC as `DateValidationService`; replaces `TransactionService.parseDate`; S-10 adopts later | yes | **yes** | `service/DateValidationService.java` (217 lines) + `DateValidationResult`; 15 tests; no leftover `parseDate` on TransactionService |
| S09-B5: no schema change; V170x unused | yes | **yes** | no new migration files in wave |
| S09-B6: timestamps stored at midnight `00:00:00`; year-0000 → rejected via 2513+null | yes | **yes** | `writeTransaction` maps `LocalDate`→`LocalDateTime.of(..., MIDNIGHT)`; `yearZeroDateIsRejected_frS0932` |
| D-1: language English-only (CSMSG01Y translation dropped) | yes | **yes** | `CobolMessages` is English literals only |
| D-2: year `0000` (2513-exempted) → parsed=null → rejected with `... - Not a valid date...` | yes | **yes** | `DateValidationService` `parsed==null` iff year 0; `validateDate` :329-330; test `yearZeroDateIsRejected_frS0932` |
| D-3: over-width 3270 input → HTTP 400 (can't happen on a 3270) | yes | **yes** | `padAtApi` :443-452 → `CobolApiException` 400; tests at service + API level |
| PR #118 note: "PF5 on the untouched seed yields `Proc Date can NOT be empty...`" | yes (PR text) | **inaccurate** | Actual outcome is `Orig Date can NOT be empty...` — both seeded timestamps are null (test fixture `src/test/resources/seed/ASCII/dailytran.txt` is 299 bytes; `timestamp(line,278,26)` DataSeeder.java:294 gets `'06-10 19:27:53.000000'` → parse fails → null) and Orig is checked before Proc (`validateDataFields` :269-270 before :272-273). Behavior is COBOL-faithful; the PR description names the wrong field (F-3) |

## 5. Test evidence

Command (S09 classes only — full suite intentionally not run):

```
cd spring-boot
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -q test \
  -Dtest='TransactionAddServiceTest,DateValidationServiceTest,TransactionAddIntegrationTest,TranAddUiIntegrationTest'
```

Observed (surefire `.txt` reports under `spring-boot/target/surefire-reports/`):

| Class | Tests run | Failures | Errors | Skipped |
|---|---|---|---|---|
| `com.carddemo.service.TransactionAddServiceTest` | 33 | 0 | 0 | 0 |
| `com.carddemo.service.DateValidationServiceTest` | 15 | 0 | 0 | 0 |
| `com.carddemo.TransactionAddIntegrationTest` | 5 | 0 | 0 | 0 |
| `com.carddemo.TranAddUiIntegrationTest` | 10 | 0 | 0 | 0 |
| **Total** | **63** | **0** | **0** | **0** |

Matches the wave PR's "63 new tests" claim exactly. Maven exit code 0.

Note: both integration classes run on H2 (`jdbc:h2:mem:tranaddtest` / `tranadduitest`, `ddl-auto=create-drop`), not the plan's Testcontainers/Postgres-16 stack — see F-5.

## 6. Findings

| # | Severity | Title | Evidence | Recommendation |
|---|---|---|---|---|
| F-1 | **MEDIUM** | FR-S09-28 PARTIAL — PF5 on an empty file preserves typed data fields; COBOL wipes them | `TransactionService.copyLastInto` :351 `if (last != null)` — no else branch, so typed values survive when `findTopByOrderByTranIdDesc()` returns null. COBOL `COPY-LAST-TRAN-DATA` unconditionally `MOVE`s TRAN-RECORD over every data field when `NOT ERR-FLG-ON` (COTRN02C.cbl:480-493); on an empty file the record buffer is untouched (blank), so ENTER yields `Type CD can NOT be empty...` regardless of prior input. Java: fully typed form + Confirm `Y` + empty table + PF5 → writes a row COBOL would reject. Undocumented; `copyLastOnEmptyFileYieldsTypeRequired_frS0928` (:525-533) passes all-null fields so the divergent path is untested. | In `copyLastInto`, explicitly blank all 11 data fields when `last == null` (or blank-then-fill unconditionally). Add a test with typed fields + empty table. Update the FR/plan docs if the team instead accepts the divergence deliberately. |
| F-2 | **LOW** | FR-S09-24 PARTIAL — `Transaction ID NOT found...` (STARTBR NOTFND, COTRN02C.cbl:655-660) has no Java trigger | `copyLastInto` maps only `DataAccessException`→`Unable to lookup Transaction...` (:348-349). A null top row is treated as "empty file" not NOTFND; `TRANSACTION_NOT_FOUND` (CobolMessages.java:73) is reachable only from the transaction-view flow. A relational `findTop...` can't distinguish "browse failed to start" from "no rows"; under real CICS an empty KSDS STARTBR raises NOTFND — which would make this the actual empty-file behavior (see UNVERIFIED note below). | Either document the mapping explicitly (empty-file = ENDFILE/zeros path per FR-S09-28) or emit `Transaction ID NOT found...` on the null branch, matching real CICS. The two interpretations conflict — the FR doc itself should pick one (F-1 and this finding interact). |
| F-3 | **LOW** | PR #118 documents the seeded-PF5 deviation with the wrong message field | PR text: "PF5 copy on the untouched seed yields `Proc Date can NOT be empty...`". Actual: `Orig Date can NOT be empty...` — both timestamps null on the 299-byte fixture; Orig checked first (`TransactionService.java` :269-270 vs :272-273; `DataSeeder.java` :294-295). | Correct the PR description / plan note. No code change needed. |
| F-4 | **LOW** | Screen label literals diverge from BMS | Template `transaction-add.html` renders `Type CD :`, `Category CD :`, `Source :`, `Description :`, `Amount :`, `Orig Date :`, `Proc Date :`, `Merchant ID :`, `Merchant Name :`, `Merchant City :`, `Merchant Zip :` — space before colon vs BMS literals `Type CD:` etc. (COTRN02.bms:121,134,147,160,173,186,199,227,240,253,266). The long prompt `You are about to add this transaction. Please confirm :` (bms:278-280) is replaced by short `Confirmation :` (:84). All other literals verbatim. | Cosmetic; either fix template strings or record as accepted cosmetic deviation in the plan. |
| F-5 | **LOW** | Plan promised Testcontainers/Postgres integration tests (dup-PK, concurrent adders); shipped tests run on H2 and don't cover those cases | `S09_tran_add_migration_plan.md` :89-90, :107, :121 — "Testcontainers — happy-path write, next-id, duplicate-PK, concurrent adders", "H2 only for the fast service-level tests". Shipped `TransactionAddIntegrationTest` uses `jdbc:h2:mem:tranaddtest` + `create-drop` (:27-32) and has no duplicate-PK/concurrent test; `pom.xml` has no testcontainers dependency. DUPREC mapping is covered at service level by `duplicateKeyYieldsAlreadyExists_frS0922` (mocked exception). | Either add a Testcontainers-backed integration test for the PK-collision path or amend the plan to accept service-level coverage. |
| F-6 | **INFO** | Test-seed `dailytran.txt` misalignment is broader than documented | Record is 299 bytes with unpadded zip: zip seeds as `'0210811112'`, card as `'222333344442022-'` (fields shifted 5 bytes early). Harmless — only visible if PF5 copies the seeded row (zip copied; card is a key field, not copied) — but the PR documents only the timestamp effect. | Regenerate the fixture at 350 bytes to make seeded-row copies realistic. |
| F-7 | **INFO** | CSUTLDTC codes 2509/2521 (era) and the `OTHER` verdict `Date is invalid` not ported | CSUTLDTC.cbl:62-70 88-levels and :128-148 verdict literals vs `DateValidationService` (implements 2507/2508/2513/2517/2518/2520 + `Date is valid`). Unreachable under the YYYY-MM-DD/YMD masks actually used; plan A-1 only required recognised codes. | None — record as understood dead code. |

**UNVERIFIED note on real CICS STARTBR semantics:** the FR doc asserts empty-file PF5 → `Type CD can NOT be empty...` (FR-S09-28), which requires STARTBR to succeed on an empty KSDS and READPREV to return ENDFILE. Reference material indicates real CICS raises STARTBR NOTFND on an empty file (→ `Transaction ID NOT found...`, the FR-S09-24 path). If so, the FR doc's expected outcome is itself wrong and F-1/F-2 should be resolved toward `Transaction ID NOT found...` on an empty table. Cannot be verified against a live CICS region from this checkout; flagged for the FR owners rather than guessed.
