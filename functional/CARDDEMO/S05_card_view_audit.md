# S-05 Card View — Independent Audit

- Stream: S-05 `card_view` (COCRDSLC, tran CCDL, map CCRDSLA)
- Branch: `devin/audit-s05-java`, cut from `devin/1789516557-carddemo-java-engagement`
- Audited HEAD: `410e5d0f826ccd3a6a2f4ea04eee673f64c240d8` ("ledger: wave C complete — S-20 (#132); all 20 streams merged (693 tests)")
- Auditor: independent auditor (did not perform the migration)
- Date: 2026-09-16
- Scope: FR-S05-01..16 traceability, COBOL verbatim parity vs `app/cbl/COCRDSLC.cbl` / `app/bms/COCRDSL.bms` / `app/cpy/`, stub sweep, S05-B1..B4 + wave-PR (#123) deviations, scoped JUnit run.

## Verdict: **PASS with findings**

All 16 FRs are implemented and pinned by named, running tests (64/64 green). No stubs or placeholders. One MEDIUM finding (open-redirect guard gap on `returnUrl`) and three LOW/INFO items; nothing CRITICAL/HIGH.

## 1. Traceability matrix

| FR | Requirement | Implementation | Test name(s) | Status |
|---|---|---|---|---|
| FR-S05-01 | Initial display: empty screen, prompt info, cursor on Account, inputs editable | `UiController.cardView` GET `spring-boot/src/main/java/com/carddemo/ui/UiController.java:380-396` → `CardService.initialScreen` `service/CardService.java:951-955` | `CardViewServiceTest.initialScreenIsPromptOnly_frS0501`; `CardViewUiIntegrationTest.initialRenderShowsPromptAndNoData_frS0501` | PASS |
| FR-S05-02 | Account blank/`*`/zero → `Account number not provided`, `*` red, cursor Account, no read | `CardService.editCardViewInputs` `CardService.java:1039-1041` + `unsupplied` `:1081-1086` + `echo` BLANK `CardService.java:1012-1018` | `CardViewServiceTest.blankAccountIsStarRedAndFieldMessage_frS0502` (incl. `findById` never called); `CardViewUiIntegrationTest.blankAccountShowsStarRedAndFieldMessage_frS0502` | PASS |
| FR-S05-03 | Account not exactly 11 numeric → filter message, cleared red | `CardService.java:1042-1044` (`\d{11}`) | `CardViewServiceTest.shortOrAlphaAccountIsClearedRedFilterError_frS0503`; `CardViewUiIntegrationTest.invalidAccountClearedRedWithFilterMessage_frS0503` | PASS |
| FR-S05-04 | Card blank/`*`/zero → `Card number not provided`, `*` red, cursor Card | `CardService.java:1051-1055` | `CardViewServiceTest.blankCardIsStarRedAndFieldMessage_frS0504`; `CardViewUiIntegrationTest.blankCardShowsStarRedAndFieldMessage_frS0504` | PASS |
| FR-S05-05 | Card not 16 numeric → filter message, cleared red | `CardService.java:1056-1060` (`\d{16}`) | `CardViewServiceTest.shortOrAlphaCardIsClearedRedFilterError_frS0505`; `CardViewUiIntegrationTest.invalidCardClearedRedWithFilterMessage_frS0505` | PASS |
| FR-S05-06 | Both blank → `No input received`, both `*` red | `CardService.java:1065-1067` | `CardViewServiceTest.bothBlankIsNoInputReceived_frS0506`; `CardViewUiIntegrationTest.bothBlankIsNoInputReceived_frS0506` | PASS |
| FR-S05-07 | Account message wins; both fields flagged; cursor Account | first-writer-wins slot `CardService.java:1035-1060`; cursor `:1000-1001` | `CardViewServiceTest.accountMessageWinsOverCard_frS0507` | PASS |
| FR-S05-08 | All-zero (any length) = not provided | `unsupplied` `CardService.java:1085` (`chars().allMatch('0')`) | covered inside `blankAccountIsStarRedAndFieldMessage_frS0502` (values `"00000000000"`, `"0"`) and `blankCardIsStarRedAndFieldMessage_frS0504` (`"0000000000000000"`) — see finding F-03 on the `_frS0508` test-name tag | PASS |
| FR-S05-09 | Found: name/expiry MM-YYYY/active + `   Displaying requested details`, echoes, cursor Account | `cardView` `CardService.java:984-998` + `toCardBlock` `:1099-1109` | `CardViewServiceTest.foundCardDisplaysDetailsAndInfoLine_frS0509`; `CardViewUiIntegrationTest.seededCardRendersDetailFields_frS0509` | PASS |
| FR-S05-10 | NOTFND → `Did not find cards for this search condition`, both red, values kept, details blank | `CardService.java:991-996` | `CardViewServiceTest.notFoundFlagsBothAndKeepsEchoes_frS0510`; `CardViewUiIntegrationTest.unknownCardFlagsBothAndKeepsEchoes_frS0510` | PASS |
| FR-S05-11 | Store error → 75-char file-error frame, Account red only | `CardService.java:985-990` + `CobolMessages.fileError` `api/CobolMessages.java:344-349` | `CardViewServiceTest.storeErrorRendersFileErrorFrame_frS0511`; `CardViewUiStoreErrorIntegrationTest.cardStoreErrorRendersFileErrorLayout_frS0511` | PASS |
| FR-S05-12 | Read keyed by card number only; foreign account still displays | `cardRepository.findById(edits.cardNumber())` `CardService.java:984`; REST `detail` `:1117-1128` | `CardViewServiceTest.accountIsNeverCrossCheckedAgainstCard_frS0512`; `CardViewUiIntegrationTest.foreignAccountStillDisplaysCard_frS0512`; `ApiIntegrationTest.cardListAndDetailExposeCobolSelectionSemantics` | PASS |
| FR-S05-13 | Card-list entry: edits skipped, auto-read, inputs read-only | `UiController.cardView` `:385-389` → `CardService.cardListScreen` `:960-964` (`inputsProtected=true` → `th:readonly` `templates/card-view.html:26,37`) | `CardViewServiceTest.cardListContextSkipsEditsAndProtects_frS0513`; `CardViewUiIntegrationTest.cardListContextAutoReadsAndProtects_frS0513` | PASS |
| FR-S05-14 | PF3 → caller (COCRDLIC when it called, else menu) | `submitCardView` `UiController.java:409-411` + `internalRoute` `:426-431` + caller resolution `:390-393` | `CardViewUiIntegrationTest.pf3ReturnsToCallerOrMenu_frS0514` | PASS (but see F-01: same-origin guard is bypassable) |
| FR-S05-15 | Any AID ≠ ENTER/PF3 acts as ENTER, no invalid-key message | `submitCardView` `UiController.java:401-414` (only literal `PF3` branches; all else → `viewScreen`) | `CardViewUiIntegrationTest.unmappedAidSubmitsAsEnterWithNoInvalidKeyText_frS0515` | PASS |
| FR-S05-16 | Screen parity: title, 11/16 inputs, footer, separate info/error areas | `templates/card-view.html:14,22,33,63-64`; header/error areas `templates/layout.html:11-20` | `CardViewUiIntegrationTest.inputLengthsMatchTheMap_frS0516` + `initialRenderShowsPromptAndNoData_frS0501` (title/labels/CCDL/COCRDSLC) | PASS |

## 2. COBOL parity spot-checks

Message catalogue — every reachable entry compared verbatim:

| COBOL source | Java | Match |
|---|---|---|
| `'Please enter Account and Card Number'` `app/cbl/COCRDSLC.cbl:131-132` | `CobolMessages.CARD_VIEW_PROMPT` `CobolMessages.java:290-291` | identical |
| `'   Displaying requested details'` (3 leading spaces) `COCRDSLC.cbl:129-130` | `CARD_VIEW_FOUND` `CobolMessages.java:292-293` | identical |
| `'Account number not provided'` `COCRDSLC.cbl:138-139` | `CARD_ACCOUNT_REQUIRED` `CobolMessages.java:294` | identical |
| `'Card number not provided'` `COCRDSLC.cbl:140-141` | `CARD_NUMBER_REQUIRED` `CobolMessages.java:295` | identical |
| `'No input received'` `COCRDSLC.cbl:142-143` | `NO_INPUT_RECEIVED` `CobolMessages.java:49` | identical |
| `'ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER'` `COCRDSLC.cbl:669-671` | `CARD_ACCOUNT_FILTER_INVALID` `CobolMessages.java:54-55` | identical |
| `'CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER'` `COCRDSLC.cbl:710-712` | `CARD_FILTER_INVALID` `CobolMessages.java:52-53` | identical |
| `'Did not find cards for this search condition'` `COCRDSLC.cbl:153-154` | `CARD_COMBINATION_NOT_FOUND` `CobolMessages.java:58-59` | identical |
| File-error frame `WS-FILE-ERROR-MESSAGE` `COCRDSLC.cbl:102-121`, populated `:767-771` | `CobolMessages.fileError` `CobolMessages.java:344-349` | byte-equal layout: `File Error: ` X(12) + `READ    ` X(8) + ` on ` + `CARDDAT  ` X(9) + ` returned RESP ` + X(10) + `,RESP2 ` + X(10) = 75 chars, matching the X(75) receiver truncation. RESP/RESP2 are the documented fixed pair `000000017 `/`000000120 ` (S05-B4) — CICS RESP has no Java equivalent; deviation is documented |
| Title `View Credit Card Detail` `app/bms/COCRDSL.bms:78` | `card-view.html:14` | identical |
| Labels `Account Number    :` / `Card Number       :` / `Name on card      :` / `Card Active Y/N   : ` / `Expiry Date       : ` `COCRDSL.bms:83,95,106,115,125` | `card-view.html:21,32,43,49,55` | identical incl. spacing |
| Footer `ENTER=Search Cards  F3=Exit` (two spaces) `COCRDSL.bms:152` | `card-view.html:64` | identical |
| Field lengths ACCTSID 11 / CARDSID 16 `COCRDSL.bms:87,99` | `maxlength="11"`/`maxlength="16"` `card-view.html:22,33` | match |
| INFOMSG vs ERRMSG areas `COCRDSL.bms:139-147` | `card-view.html:63` (`screen.infoMessage`) vs `layout.html:20` (`message` = `screen.errorMessage`) | separate areas, as required |

Edit/access semantics:

| COBOL | Java | Match |
|---|---|---|
| `*`/SPACES → LOW-VALUES `COCRDSLC.cbl:615-620,622-627` | `unsupplied` `CardService.java:1081-1086` (`*`-then-blanks, blank) | match; `*` + trailing text is NOT-OK on both sides (verified by `starWithTextIsInputNotBlank_frS0508`) |
| `CC-ACCT-ID-N EQUAL ZEROS` / `CC-CARD-NUM-N EQUAL ZEROS` `COCRDSLC.cbl:653,693` (undefined on non-numeric) | all-`'0'` of any length → BLANK `CardService.java:1085` | match per documented decision (FR §11.3) |
| `IS NOT NUMERIC` on the full-width X(11)/X(16) field `COCRDSLC.cbl:665,706` | `\d{11}` / `\d{16}` `CardService.java:1042,1056` after `mapField` truncation to width `:1073-1075` | equivalent |
| Message slot first-writer-wins `IF WS-RETURN-MSG-OFF` `COCRDSLC.cbl:656-658,668-672,696-698,709-713` | `message == null` guards `CardService.java:1053,1058` | match |
| Both-blank overrides with `No input received` `COCRDSLC.cbl:637-640` | `CardService.java:1065-1067` | match |
| READ keyed `RIDFLD(WS-CARD-RID-CARDNUM)` only — account never compared `COCRDSLC.cbl:739-750` | `findById(edits.cardNumber())` `CardService.java:984`; `cards.card_number` is the PK `model/Card.java:12` | match (source defect kept verbatim, FR §11.1) |
| NOTFND: both flags NOT-OK, values retained `COCRDSLC.cbl:755-761` | `CardService.java:991-996` + `echo` VALID→entered | match |
| RESP OTHER: frame + only acct flag `COCRDSLC.cbl:762-771` | `CardService.java:985-990` | match |
| Echo rules: COMMAREA key = 0 → LOW-VALUES; BLANK re-entry shows `*` red; NOT-OK cleared red `COCRDSLC.cbl:462-471,533-551` | `echo` BLANK→`"*"`, NOT_OK→`""`, VALID→entered `CardService.java:1012-1018` | match |
| Cursor: acct-flag → acctsid, else card-flag → cardsid, else acctsid `COCRDSLC.cbl:515-524` | `CardService.java:1000-1001` | match |
| Card-list entry: edits skipped, numeric-move echo zero-padded `COCRDSLC.cbl:339-348` (`MOVE CDEMO-ACCT-ID TO CC-ACCT-ID-N` `:342-343`) | `cardListScreen` `CardService.java:960-964` + `commareaEcho` `:1091-1097` | match |
| Protected inputs from card list `COCRDSLC.cbl:505-508` | `inputsProtected` → `th:readonly` `card-view.html:26,37` | match on-screen; server-side caveat in F-02 |
| Expiry slices: year bytes 1-4, month bytes 6-7 of `YYYY-MM-DD` `COCRDSLC.cbl:84-90,477-482` | `toCardBlock` `%02d` month / `%04d` year `CardService.java:1103-1108` | match; unparseable→null→blank per FR §6 |
| PF3 XCTL to `CDEMO-FROM-PROGRAM` fallback menu `COCRDSLC.cbl:305-334` | `redirect:` + `internalRoute` `UiController.java:409-411,426-431`; card-list caller resolves `uiRouteForProgram("COCRDLIC")`→`/cards/list` `:392-393`, `MenuService.java:110` | match in intent; see F-01 |
| AID: ENTER/PF3 valid, all else forced to ENTER `COCRDSLC.cbl:291-299` | `UiController.java:401-414` | match |
| Dead path `9150-GETCARD-BYACCT` / CARDAIX `COCRDSLC.cbl:779-810` | not ported (no `findByCardAcctId` call on the view path) | correctly absent |
| Unreachable 88-levels (`PF03 pressed.Exiting`, `Account number must be a non zero…`, `Did not find this account in cards database`, `Error reading Card Data File`, `Looks Good.... so far`) `COCRDSLC.cbl:135-158` | none emitted by the view path (`CARD_ACCOUNT_NOT_FOUND` exists as a constant but is never emitted here) | correctly absent |

## 3. Stub/placeholder sweep

Searched the stream's implementation and tests for `TODO`, `FIXME`, `not implemented`, `UnsupportedOperationException`, `@Disabled`/`disabled`, `assertThat(true)`/`assertTrue(true)`, commented-out assertions:

- `service/CardService.java` (S-05 block `:944-1128`), `service/CardViewScreen.java`, `ui/UiController.java` (`/cards/view` handlers `:380-431`), `api/CardController.java` (`detail` `:36-40`), `api/CobolMessages.java`, `templates/card-view.html`, `CardViewServiceTest`, `CardViewUiIntegrationTest`, `CardViewUiStoreErrorIntegrationTest` — **none found**.
- No hardcoded happy-path returns: the read genuinely hits `CardRepository.findById`; test-mocked failure is the store-error test itself.
- All assertions are concrete value/state checks; no ignored or vacuous tests.

## 4. Documented deviations vs shipped code

| Deviation | Source | Shipped? | Evidence |
|---|---|---|---|
| S05-B1 — `findById(cardNumber)`, no account/xref check; NOTFND→`Did not find cards…`, other→file-error frame | plan §3 | yes | `CardService.java:984-996` |
| S05-B2 — `/cards/view?accountId=&cardNumber=&returnUrl=` auto-read, edits skipped, inputs read-only; partial params → initial display | plan §3 | yes | `UiController.java:380-396`; `CardService.cardListScreen:960-964`; test asserts partial hand-off renders initial display `CardViewUiIntegrationTest.java:256-262`. S-04 emits `accountId`+`cardNumber` `CardListUiController.java:89-90` |
| S05-B3 — PF3 → `returnUrl` else `/menu`, internal paths only | plan §3 | mostly | `UiController.java:409-411,426-431`; same-origin guard incomplete (F-01) |
| S05-B4 — fixed RESP `000000017 `/RESP2 `000000120 ` in the verbatim 75-char frame | plan §3 | yes | `CobolMessages.java:344-349`; test `CardViewUiStoreErrorIntegrationTest.java:70-71` |
| PR #123 dev. 1 — card list emits no `returnUrl`; card-list-context PF3 defaults to `COCRDLIC` route | wave PR | yes | `UiController.java:390-393`; test `CardViewUiIntegrationTest.java:244-246` |
| PR #123 dev. 2 — REST `detail` applies the screen edits verbatim (400 on absent account, `\d{11}`, `No input received`) | wave PR | yes | `CardService.detail:1117-1128`; `ApiIntegrationTest.java:134-136` |
| `NO_CHANGES_DETECTED` removed from both-blank path | plan §1 | yes | `editCardViewInputs` produces `NO_INPUT_RECEIVED`; `NO_CHANGES_DETECTED` remains only on the S-06 update path `CardService.java:1163` |
| Account cross-check + `findByCardAcctId` removed from view read | plan §1 | yes | no such call on the view path |
| PF3 `SET CDEMO-USRTYP-USER` COMMAREA side effect not ported | FR §11.5 | yes (intentional) | Spring Security context is the identity (S01-B6); no code counterpart — documented |
| Undocumented: `cardListContext`/echoes are client-controlled POST fields, so crafted input can mutate "protected" keys | — | shipped | `UiController.java:403-413`; see F-02 |
| Undocumented: `returnUrl` internal-path guard misses `\` | — | shipped | `UiController.java:426-431`; see F-01 |
| Doc drift: plan §8/FR §9 describe `CardViewUiIntegrationTest` as Testcontainers Postgres | plan/FR docs | shipped tests use in-memory H2 | `CardViewUiIntegrationTest.java:36-41` (`jdbc:h2:mem:cardviewuitest`); see F-04 |

## 5. Test evidence

Command (from `spring-boot/`, `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`):

```
mvn -q test -Dtest='CardViewServiceTest,CardViewUiIntegrationTest,CardViewUiStoreErrorIntegrationTest,ApiIntegrationTest,CardListUiIntegrationTest'
```

Observed Surefire counts (`target/surefire-reports/*.txt`):

| Class | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| `com.carddemo.service.CardViewServiceTest` | 13 | 0 | 0 | 0 |
| `com.carddemo.CardViewUiIntegrationTest` | 15 | 0 | 0 | 0 |
| `com.carddemo.CardViewUiStoreErrorIntegrationTest` | 1 | 0 | 0 | 0 |
| `com.carddemo.ApiIntegrationTest` | 18 | 0 | 0 | 0 |
| `com.carddemo.CardListUiIntegrationTest` | 17 | 0 | 0 | 0 |
| **Total** | **64** | **0** | **0** | **0** |

`ApiIntegrationTest` and `CardListUiIntegrationTest` are shared classes included because the trace pointed at them (`detail` contract, 'S'→`/cards/view` navigation); their non-S-05 tests also passed. Full suite intentionally not run — covered by a separate audit.

## 6. Findings

| # | Severity | Finding | Evidence | Recommendation |
|---|---|---|---|---|
| F-01 | MEDIUM | The `internalRoute` same-origin guard rejects `//` but accepts a leading `\`: `returnUrl` `/\evil.example` passes `startsWith("/") && !startsWith("//")`, so PF3 emits `Location: /\evil.example`; browsers normalize `\` to `/` and treat it as protocol-relative, redirecting off-site. The documented S05-B3 intent ("internal paths only") is not fully enforced. Shared with the S-02 account-view exit. | `spring-boot/src/main/java/com/carddemo/ui/UiController.java:426-431` vs plan §3 S05-B3 | Also reject `\` (and ideally any char outside an internal-path allowlist) in `internalRoute`. |
| F-02 | LOW | Card-list "protected" fields are only client-side `readonly` + a hidden `cardListContext` flag. A crafted POST to `/cards/view` with `cardListContext=true` and different `accountId`/`cardNumber` runs the edits and reads a different card while the screen still claims the card-list context. In the source the protected map fields are never RECEIVEd, so the COMMAREA keys are immutable (`COCRDSLC.cbl:505-508`, `339-348`). | `UiController.java:403-413`; `card-view.html:16-17,26,37` | In card-list context, re-derive the keys from the echoed fields or ignore posted key changes server-side. |
| F-03 | INFO | Test-name tags drifted from FR ids: `starWithTextIsInputNotBlank_frS0508` verifies `*`-with-text, not FR-S05-08 zero handling (zero coverage lives inside `_frS0502`/`_frS0504` loops), and `menuOptionFourNavigatesToCardView_frS0513` covers menu routing, not FR-S05-13 entry (covered by `cardListContextAutoReadsAndProtects_frS0513`). All FRs are genuinely covered; only the suffix labels mislead. | `CardViewServiceTest.java:186-196`, `CardViewUiIntegrationTest.java:306-315` | Rename or re-tag so the `_frS05NN` suffix matches the FR asserted. |
| F-04 | INFO | Doc drift: FR §9 and plan §8 describe the UI integration test as Testcontainers Postgres seeded from `app/data/ASCII/carddata.txt`; shipped tests run H2 (`jdbc:h2:mem:cardviewuitest`, `carddemo.seed.data-dir=classpath:seed`). | `CardViewUiIntegrationTest.java:36-41`, `CardViewUiStoreErrorIntegrationTest.java:36-40` | Update the docs or the test profile to match. |
| F-05 | INFO | Literal `aid=F3` in a raw POST is treated as ENTER on this screen (only `"PF3"` exits), while the sibling card-update controller accepts both spellings. The shipped JS emits `PF3` for the F3 key, so browser behaviour is correct; only non-browser clients diverge. | `UiController.java:409` vs `:328` | Optional: accept `"F3"` for symmetry, per the COBOL PFK03 mapping. |

### UNVERIFIED items

- Recorded UI pass (`!mf_online_ui_testing`, plan §8) is out of scope for this audit — no evidence reviewed.
- `EIBCALEN=0`/unsigned bounce parity rests on the S-01 security seam; only asserted here via `CardViewUiIntegrationTest.unsignedRequestsAreRejected` (redirect to `/signon`, 401 on the API), which passed.
