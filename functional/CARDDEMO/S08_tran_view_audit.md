# S-08 Transaction View — Independent Audit

- **Stream:** S-08 `tran_view` (COTRN01C, tran CT01, map COTRN1A)
- **Branch audited:** `devin/audit-s08-java` (= `origin/devin/1789516557-carddemo-java-engagement`), HEAD `410e5d0f826ccd3a6a2f4ea04eee673f64c240d8`
- **Auditor:** independent auditor (did not perform the migration)
- **Date:** 2026-09-16
- **Scope:** `functional/CARDDEMO/S08_functional_requirement.md` FR-S08-01..17; `spring-boot/` implementation + tests; verbatim parity vs `app/cbl/COTRN01C.cbl`, `app/cpy/`, `app/bms/COTRN01.bms`; wave PR #121 (`devin/wave-s08-tran-view`, merged 2026-09-16).

## Verdict: **PASS with findings**

All 17 FRs trace to concrete implementation and named tests; all messages, edits, and boundary decisions verify verbatim against COBOL. Scoped test run green (28/28). Findings are LOW/INFO only — no CRITICAL/HIGH, no unimplemented FR.

## 1. Traceability matrix

| FR | Requirement | Implementation | Test(s) | Status |
|---|---|---|---|---|
| FR-S08-01 | No session → sign-on; API → 401 | `security/SecurityConfig.java:43-48` (`anyRequest().authenticated()`), entry point `:60-68` (UI→`/signon`, `/api/`→401) | `TransactionViewUiIntegrationTest.unsignedBouncesToSignon_frS0801` | PASS |
| FR-S08-02 | First entry blank, cursor on Tran ID, no lookup | `service/TransactionService.openView` `:95-98` → `TransactionViewScreen.blank` `:64-66`; `autofocus` `templates/transaction-view.html:19` | `TransactionViewServiceTest.openViewBlankShowsEmptyScreen_frS0802` (incl. `verifyNoInteractions`), `...UiIntegrationTest.firstEntryBlankScreen_frS0802` | PASS |
| FR-S08-03 | Pre-selected id pre-fills + fetches | `openView` `:95-98` → `enterView`; `UiController.transactionView` `:776-783` | `...ServiceTest.openViewPreselectedRunsEnter_frS0803`, `...UiIntegrationTest.preselectedFetchesImmediately_frS0803` | PASS |
| FR-S08-04 | Blank id → `Tran ID can NOT be empty...`, details kept | `enterView` `:109-112` → `retained(..., TRANSACTION_ID_REQUIRED, null, displayed)` | `...ServiceTest.blankIdRejectsAndKeepsDetails_frS0804`, `...UiIntegrationTest.blankEnterKeepsDetails_frS0804` | PASS |
| FR-S08-05 | Non-blank id → details cleared before lookup | `enterView` `:113-131` — every post-read outcome passes `null` details | `...ServiceTest.nonBlankIdClearsDetailsBeforeRead_frS0805`, `...UiIntegrationTest.failedLookupClearsDetails_frS0805` | PASS |
| FR-S08-06 | NOTFND → `Transaction ID NOT found...`, id retained | `enterView` `:127-130` | `...ServiceTest.unknownIdNotFoundRetainsInput_frS0806`, `...UiIntegrationTest.notFoundRetainsTypedId_frS0806` | PASS |
| FR-S08-07 | Other RESP → `Unable to lookup Transaction...`, logged | `enterView` `:114-126` — `catch (RuntimeException)` → `log.warn` + message | `...ServiceTest.storeFailureShowsUnableToLookup_frS0807` | PASS (service-level only — see F-3) |
| FR-S08-08 | Found → all 13 fields, blank message | `enterView` `:131` → `toViewDetails` `:138-155` | `...ServiceTest.goldenRowPopulatesEveryField_frS0808`, `...UiIntegrationTest.seededDebitRendersEveryField_frS0808` | PASS |
| FR-S08-09 | `+99999999.99` amount edit | `TransactionListService.formatAmount` `:299-307` (`%08d`, `%100_000_000` drops the 9th integer digit) | `...ServiceTest.amountRunsThroughCobolEdit_frS0809` (incl. `123456789.12`→`+23456789.12`), `...UiIntegrationTest.seededCreditShowsSignedAmount_frS0809` | PASS |
| FR-S08-10 | First-10 `yyyy-MM-dd`, blank when absent | `TransactionService.dateText` `:468-470` | `...ServiceTest.timestampsKeepFirstTenCharacters_frS0810`, UI golden row `:194` | PASS |
| FR-S08-11 | 60/30/25 truncations, rest full | `toViewDetails` `:140-154` via `CobolFormat.truncate` `:47-52` | `...ServiceTest.longTextTruncatesToMapWidths_frS0811` | PASS |
| FR-S08-12 | Verbatim X(16) key; trailing blanks = padding | `TransactionViewScreen.tranIdField` `:80-83` (16-char truncate, low-values→spaces), `findById(field.stripTrailing())` `TransactionService.java:119`, `maxlength="16"` `transaction-view.html:18`; seed import strips trailing spaces `data/CobolFieldReader.java:18` | `...ServiceTest.keyIsVerbatimSixteenWithTrailingBlankTrim_frS0812`, `...ServiceTest.leadingBlanksAndCaseAreSignificant_frS0812`, `...UiIntegrationTest.caseAndLeadingSpaceAreVerbatim_frS0812`, `...UiIntegrationTest.tranIdInputLengthIsSixteen_frS0812` | PASS |
| FR-S08-13 | PF3 → caller or menu | `UiController.java:811-813` + `internalRoute` `:426-431` (internal paths only, else `/menu`) | `...UiIntegrationTest.pf3ReturnsToCallerOrMenu_frS0813` (incl. external-URL rejection) | PASS |
| FR-S08-14 | PF4 → all cleared, cursor on input, no lookup | `UiController.java:814-817` → `TransactionViewScreen.blank` | `...UiIntegrationTest.pf4ClearsTheMap_frS0814` | PASS |
| FR-S08-15 | PF5 → COTRN00C; coming-soon while not browsable | `UiController.java:824-836` — `uiRouteForProgram("COTRN00C")` → `MenuService.java:111` returns `/transactions/list`; coming-soon fallback `:832-835` | `...UiIntegrationTest.pf5BrowsesTheListRoute_frS0815` | PASS (S-07 has landed, so the browsable branch ships — matches the FR's "once browsable" clause; see F-2) |
| FR-S08-16 | Other AID → `Invalid key pressed. Please see below...`, contents retained | `UiController.java:837-838` → `retained(trnIdIn, INVALID_KEY_PRESSED, null, displayed)` | `...UiIntegrationTest.invalidAidKeepsDetails_frS0816` | PASS |
| FR-S08-17 | Title/header/footer verbatim | `transaction-view.html:3` (CT01/COTRN01C via layout), `:13` title, `:100` footer; `layout.html:11-12` `Tran:`/`Prog:`; header date/time `UiController.java:92-97` | `...UiIntegrationTest.headerAndFooterAreVerbatim_frS0817`, `menuOptionSevenRoutesToView` | PASS |

## 2. COBOL parity spot-checks

**Message catalogue (every entry the FR doc catalogues):**

| COBOL source | Java | Match |
|---|---|---|
| `'Tran ID can NOT be empty...'` `COTRN01C.cbl:149` | `CobolMessages.java:74` `TRANSACTION_ID_REQUIRED` | verbatim |
| `'Transaction ID NOT found...'` `COTRN01C.cbl:285` | `CobolMessages.java:73` `TRANSACTION_NOT_FOUND` | verbatim |
| `'Unable to lookup Transaction...'` `COTRN01C.cbl:292` | `CobolMessages.java:75-76` `TRANSACTION_VIEW_LOOKUP_FAILED` | verbatim |
| `CCDA-MSG-INVALID-KEY` `COTRN01C.cbl:129-130` = `'Invalid key pressed. Please see below...'` `app/cpy/CSMSG01Y.cpy:20-21` | `CobolMessages.java:10` `INVALID_KEY_PRESSED` | verbatim |
| Coming-soon `This option `+name `DELIMITED BY SPACE`+`is coming soon ...` `COMEN01C.cbl:172-176` | `CobolMessages.optionComingSoon` `CobolMessages.java:316-322` → `"This option Transactionis coming soon ..."` | verbatim incl. the first-word quirk |

**Edit/validation rules and order** (`:146-192` vs `TransactionService.enterView` `:106-132`): (1) blank check → message, details kept `:109-112` (cbl `:147-152`); (2) detail fields cleared before the keyed read (cbl `:158-173`) — the record's `details` slot is `null` on every read outcome; (3) `READ ... RIDFLD(TRAN-ID) KEYLENGTH 16 UPDATE` `:269-278` → `transactionRepository.findById(field.stripTrailing())` `:119` — `UPDATE` lock dropped (documented A1: never rewritten, released at task end); (4) RESP NORMAL/NOTFND/other `:280-296` → `Optional.empty` / `catch (RuntimeException)` `:119-130`; `DISPLAY 'RESP:' ... 'REAS:'` `:290` → `log.warn(...exception)` `:123`. Order and cursor-to-TRNIDIN semantics (autofocus on the input) preserved.

**Field derivations:** amount `MOVE TRAN-AMT TO WS-TRAN-AMT PIC +99999999.99` `:49,:177,:183` → `formatAmount` `TransactionListService.java:299-307` (explicit sign, 8 zero-padded digits, 9th dropped via `% 100_000_000`); TCATCD 9(04) `:181` → `%04d` `:144`; MID 9(09) `:187` → `%09d` `:151`; TDESC 100→60 `:184`, MNAME 50→30 `:188`, MCITY 50→25 `:189` → `CobolFormat.truncate` on `:146,:152,:153`; dates first-10 `:185-186` → `dateText` `:468-470`; verbatim fields `:178-182,:190` → `:140-145,:154`.

**Key handling:** `MOVE TRNIDINI TO TRAN-ID` `:172` (no edits) → `tranIdField` `:80-83` + `stripTrailing` `:119`; stored keys are trailing-space-stripped on import (`CobolFieldReader.java:18`) — equivalent to the 16-byte padded VSAM compare for every key.

**Surface literals:** title `View Transaction` `bms:75-79` → `transaction-view.html:13`; `Enter Tran ID:` `bms:80-84` → `:17`; 13 output labels `bms:100-251` → `:24-83` (all verbatim); ERRMSG X(78) red `bms:259-262` → `message-line` `layout.html:20` (default style = error, `"info"` for coming-soon, matching DFHGREEN `COMEN01C.cbl`); footer `ENTER=Fetch  F3=Back  F4=Clear  F5=Browse Tran.` `bms:263-268` → `transaction-view.html:100` (byte-identical); header `Tran: CT01`/`Prog: COTRN01C`/date `mm/dd/yy`/time `hh:mm:ss` `:243-262` → `transaction-view.html:3` + `layout.html:11-12` + `UiController.java:92-97`.

**Exit/abend codes:** none in this program (no ABEND path); `RETURN TRANSID CT01` `:136-139` demoted to stateless HTTP — documented mechanics. Entry/transfer points verified: `CT01→COTRN01C` `app/csd/CARDDEMO.CSD:429-430`; inbound `CDEMO-CT01-TRN-SELECTED` XCTL `COTRN00C.cbl:186-195`; PF3 `CDEMO-FROM-PROGRAM`/COMEN01C `:115-122`; PF5 `MOVE 'COTRN00C' ... XCTL` `:125-127`.

## 3. Stub/placeholder sweep

Searched `TransactionService.java`, `TransactionViewScreen.java`, `TransactionListService.java`, `CobolMessages.java`, `transaction-view.html`, `UiController.java` (S-08 handlers), `TransactionViewServiceTest.java`, `TransactionViewUiIntegrationTest.java` for `TODO`, `FIXME`, "not implemented", `UnsupportedOperationException`, `@Disabled`/`@Ignored`, hardcoded happy-path returns, `assertTrue(true)` / trivially-true and commented-out assertions: **none found** (grep over the stream's files returned zero matches). No skipped tests in the scoped run (Skipped: 0 in both classes).

## 4. Documented deviations vs shipped code

| Deviation | Source | Shipped? | Evidence |
|---|---|---|---|
| S08-B1 verbatim key on screen path (no numeric edit/pad/case fold); REST `GET /api/transactions/{id}` keeps `requireTransactionId` `\d{1,16}`+`%016d` | plan §3 + risk 4; PR #121 | yes | `TransactionService.java:115-119` (view path) vs `:533-536` (REST edit retained); comment at `:115-118` states the deliberate split |
| S08-B2 `?tranId=` pre-fill + fetch | plan §3; PR #121 | yes | `UiController.java:776-783`, `TransactionService.java:95-98`; consumed live by S-07 `UiController.java:212-214` (`redirect:...?tranId=`) |
| S08-B3 `returnUrl` internal-only → `/menu` | plan §3 | yes | `UiController.java:426-431` (rejects `//` and non-`/` prefixes), `:811-813` |
| S08-B4 PF5 via `UI_ROUTES`; coming-soon when absent | plan §3 | yes | `UiController.java:824-836`; `MenuService.java:111` now maps `COTRN00C→/transactions/list`, so the navigate branch ships (coming-soon retained if the entry is removed) |
| `CobolMessages` additions `TRANSACTION_ID_REQUIRED`, `TRANSACTION_VIEW_LOOKUP_FAILED`; `Tran ID must be Numeric ...` spacing correction | plan §6 wave 1 | yes, as a new constant | `CobolMessages.java:74-76` (new); `:135` `TRANSACTION_ID_NOT_NUMERIC` carries the verbatim spaced text, used by S-07's screen `TransactionListService.java:163`; see F-1 for the residual baseline drift |
| `UI_ROUTES` flip `COTRN01C → /transactions/view` | plan §6 | yes | `MenuService.java:112`; menu option 07 routes there (`menuOptionSevenRoutesToView`) |
| `READ UPDATE` lock dropped | plan risk 6 / FR A1 | yes | `findById` only, `TransactionService.java:119` |
| 9th-integer-digit drop `+99999999.99` | plan risk 1 | yes | `TransactionListService.java:303-304`; asserted `123456789.12→+23456789.12` |
| Coming-soon name quirk (`DELIMITED BY SPACE`) reproduced | plan risk 5 | yes | `CobolMessages.java:316-322` vs `COMEN01C.cbl:172-176` |
| `mvn clean verify` green, 28 new tests | PR #121 | yes | PR CI passed; scoped rerun 28/28 green (§5) |

No undocumented deviation found.

## 5. Test evidence

```
cd spring-boot && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  mvn -q test -Dtest='TransactionViewServiceTest,TransactionViewUiIntegrationTest'
```

- `com.carddemo.service.TransactionViewServiceTest`: **Tests run: 12, Failures: 0, Errors: 0, Skipped: 0**
- `com.carddemo.TransactionViewUiIntegrationTest`: **Tests run: 16, Failures: 0, Errors: 0, Skipped: 0**
- **Total: 28 run / 28 passed** (matches PR #121's "28 new" claim; PR CI `mvn clean verify` passed at merge). Integration test runs on a dedicated H2 mem schema (`tranviewuitest`, `TransactionViewUiIntegrationTest.java:38-45`), seeding via `classpath:seed`.

## 6. Findings

| # | Severity | Finding | Evidence | Recommendation |
|---|---|---|---|---|
| F-1 | LOW | Residual verbatim drift on the REST/list API paths: `TRANSACTION_ID_INVALID` is `"Tran ID must be Numeric..."` but the source text is `'Tran ID must be Numeric ...'` (space before ellipsis). The wave added the verbatim form as a separate constant (`TRANSACTION_ID_NOT_NUMERIC`) used by the S-07 screen, while the drifted constant still serves `requireTransactionId` and the list filter. Reachable via `GET /api/transactions/<non-numeric>` → 400. | `CobolMessages.java:72` vs `app/cbl/COTRN00C.cbl:214`; uses at `TransactionService.java:60,:534`; verbatim twin at `CobolMessages.java:135` / `TransactionListService.java:163` | Either accept as baseline-API drift (plan risk 4) and record it, or align the literal — the constant feeds two live paths |
| F-2 | LOW | PF5 coming-soon fallback is now dead code: S-07 merged and `UI_ROUTES` contains `COTRN00C→/transactions/list`, so `UiController.java:832-835` can no longer be reached or regression-tested; `pf5BrowsesTheListRoute_frS0815` pins only the navigate branch. | `MenuService.java:111`; `UiController.java:828-835` | Keep as registry-fallback (matches plan) — flag only because the coming-soon branch is unverifiable while the route exists |
| F-3 | INFO | FR-S08-07 has service-level coverage only; sibling streams carry a MockMvc store-error class (`AccountViewUiStoreErrorIntegrationTest`, `CardViewUiStoreErrorIntegrationTest`) but S-08 has no `TransactionViewUiStoreErrorIntegrationTest`. The FR §4 covering-test column is satisfied (`TransactionViewServiceTest.storeFailure_*`), so this is a consistency gap, not a trace gap. | `TransactionViewServiceTest.java:92-99`; `ls src/test/java/com/carddemo` shows no store-error class for the view stream | Optional: add a one-test UI class asserting `Unable to lookup Transaction...` redisplay on repository failure |
| F-4 | INFO | Coming-soon message reproduces the source's `DELIMITED BY SPACE` quirk verbatim (`This option Transactionis coming soon ...` — missing space after the truncated name). Faithful to `COMEN01C.cbl:172-176`; the plan flags it for a shared parity fix / STOP D UX pass, not this stream. | `CobolMessages.java:316-322`; FR §5 row | No action for S-08; tracked by plan risk 5 |

UNVERIFIED items: none.
