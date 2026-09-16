# S04 Card List — Independent Audit

- **Stream:** S04 — card_list (COCRDLIC, tran CCLI, map CCRDLIA)
- **Audited branch:** `devin/1789516557-carddemo-java-engagement` @ `410e5d0f826ccd3a6a2f4ea04eee673f64c240d8`
- **Auditor:** independent auditor (did not perform the migration)
- **Date:** 2026-09-16
- **Scope:** COCRDLIC browse/select only — `CardService` lines 45–374, `CardListPageState`/related api records, `CardRepository` browse queries, `CardListUiController`, `card-list.html`, the six `CobolMessages` constants, `MenuService.UI_ROUTES` entries for this stream, and the three stream test classes. Wave PR #120 reviewed for documented deviations.

## Verdict: **PASS with findings**

All 23 FRs are implemented with line-faithful COBOL parity and named tests; the scoped suite is 53/53 green. Findings are documentation inaccuracies (Testcontainers claim, PR prose claim), three test-coverage nits on FR sub-clauses, and minor undocumented deltas. No CRITICAL/HIGH.

## 1. Traceability matrix

`svc:` = `CardListServiceTest.java`, `int:` = `CardListIntegrationTest.java`, `ui:` = `CardListUiIntegrationTest.java` (all under `spring-boot/src/test/java/com/carddemo/`). Impl cites are `spring-boot/src/main/java/com/carddemo/service/CardService.java` unless noted.

| FR | Requirement | Implementation | Named test(s) | Status |
|---|---|---|---|---|
| 01 | Fresh entry → page 1, 7 rows, anchors seeded | `browse` !reenter branch :80-84; `CardListPageState.fresh` | svc:`freshEntryListsSevenRowsAndSeedsAnchors_frS0401`:109; int:`freshEntryReturnsFirstPageAndCommarea_frS0401`:104; ui:`entryRendersCobolFieldMapAndHiddenCommarea_frS0401`:138 | PASS |
| 02 | Screen layout (filters, 7 rows, page, info/error, footer) | `card-list.html`:11-79 (maxlength 11/16, crdsel, footer `  F3=Exit F7=Backward  F8=Forward` :74) | ui:`entryRendersCobolFieldMapAndHiddenCommarea_frS0401`:138; ui:`blankFiltersRenderFullBrowseWithOpenSelects_frS0410`:231 | PASS |
| 03 | Invalid account filter → verbatim msg, rows kept, selects protected | `editInputs` :210-221 | svc:`invalidAccountFilterRedisplaysCommareaRows_frS0407`:213; int:`invalidFilterRedisplaysEchoedRowsWithError_frS0407`:178; ui:`invalidFilterEchoesAndProtectsEverySelect_frS0407_frS0411`:199 | PASS |
| 04 | Invalid card filter → verbatim msg; both-invalid → account wins | `editInputs` :222-235; first-message-wins `if (error == null)` :229 | svc:`filterErrorProtectsEverySelectField_frS0411`:292; int:`verbatimErrorMessagesMatchTheCatalogue_frS0423`:317; svc:`verbatimMessagesMatchCobolWorkingStorage_frS0423`:517. No test sends both filters invalid | PARTIAL |
| 05 | Valid filter(s), AND combination, relist from first anchor | `CardRepository.browseForward` :27-34; re-list `readForward(state, state.firstCardNumber,…)` :105 | svc:`validAccountFilterLimitsRowsToAccount_frS0408`:232; svc:`cardFilterReturnsTheExactCardOnly_frS0409`:255; int:`accountFilterLimitsRowsToMatchingAccount_frS0408`:198; ui:`cardFilterReturnsTheExactRow_frS0409`:218; svc:`enterRelistsFromFirstAnchorNotFileStart_frS0405`:182; int:`enterRelistsFromCurrentFirstAnchor_frS0405`:158. No test applies both filters together | PARTIAL |
| 06 | Empty result → `NO RECORDS FOUND…`, info suppressed, no rows | `readForward` :319-321; `page` :136-138 | int:`verbatimErrorMessagesMatchTheCatalogue_frS0423`:317; ui:`infoAndErrorLinesFollowCobolRules_frS0419`:339; svc:`infoLineShowsByDefaultAndSuppressesOnErrors_frS0419`:448 | PASS |
| 07 | PF8 → next anchor via unfiltered look-ahead | `browse` :85-90; `readForward` :298-310; `CardRepository.nextAfter` :44-47 | svc:`pfEightAdvancesFromLookAheadAnchor_frS0403`:143; int:`pfEightThenPfSevenRoundTripsThroughCommarea_frS0403_frS0416`:120; ui:`pfEightAdvancesToSecondPage_frS0403`:173 | PASS |
| 08 | Forward ENDFILE → `NO MORE RECORDS TO SHOW`, next-page off, info on | `readForward` :301-318; `page` :129-131 | svc:`pfEightExhaustionShowsInfoThenNoMorePages_frS0404`:161; int:`pfEightExhaustionShowsInfoThenNoMorePages_frS0404`:141 | PASS |
| 09 | Dead PF8 → `NO MORE PAGES TO DISPLAY`, info blank; non-PF8 resets | `browse` :61-63; `page` :127-131 | svc:`pfEightExhaustionShowsInfoThenNoMorePages_frS0404`:161 (two-step); ui:`infoAndErrorLinesFollowCobolRules_frS0419`:339 | PASS |
| 10 | PF7 → 7 preceding rows, anchors swap, landing-on-1 message | `browse` :91-96; `readBackwards` :327-351; `page` :124-126 | svc:`pfSevenReadsBackwardsFromFirstAnchor_frS0416`:385; int:`pfEightThenPfSevenRoundTripsThroughCommarea_frS0403_frS0416`:120; ui:`pfSevenReturnsToPreviousPage_frS0416`:298 | PASS |
| 11 | PF7 on page 1 → re-list + `NO PREVIOUS PAGES TO DISPLAY` | `browse` :76-79; `page` :125-126 | svc:`pfSevenOnFirstPageKeepsRowsAndShowsNoPreviousPages_frS0402`:127; ui:`pfSevenOnFirstPageShowsNoPreviousPages_frS0402`:163 | PASS |
| 12 | Invalid select code → `INVALID ACTION CODE`, row flagged | `editInputs` :252-260 | svc:`invalidSelectCodeShowsActionErrorAndRowCursor_frS0413`:335; ui:`invalidSelectCodeShowsActionError_frS0413`:263 | PASS |
| 13 | >1 S/U → `PLEASE SELECT ONLY ONE…`, all S/U rows flagged, wins over 12 | `editInputs` :239-251 | svc:`multipleSelectsFlagOnlyOneRecordAndRebrowseFromStart_frS0412`:309; ui:`multipleSelectsShowOnlyOneMessageAndRebrowse_frS0412_s04B2`:244 | PASS |
| 14 | Selection-error redisplay re-reads from file start, page kept (S04-B2) | `browse` :66-74 (`readForward(state, "", …)`), page preserved | svc:`multipleSelectsFlagOnlyOneRecordAndRebrowseFromStart_frS0412`:309; ui:`multipleSelectsShowOnlyOneMessageAndRebrowse_frS0412_s04B2`:244 | PASS |
| 15 | Single S → hand-off to COCRDSLC with row keys (S04-B1 registry) | `browse` :97-100; `navigate` :109-118; `CardListUiController` :85-91; `MenuService.UI_ROUTES` :109-119 | svc:`enterSelectSNavigatesToCardViewWithRowKeys_frS0414`:355; int:`enterSelectSNavigatesWithRowKeys_frS0414`:232; ui:`selectionNavigatesThroughRegistryOrShowsComingSoon_s04B1`:275 | PASS |
| 16 | Single U → hand-off to COCRDUPC with row keys | `browse` :101-104; same navigate path | svc:`enterSelectUNavigatesToCardUpdateWithRowKeys_frS0415`:371; int:`enterSelectUNavigatesWithRowKeys_frS0415`:252; ui:`selectionNavigatesThroughRegistryOrShowsComingSoon_s04B1`:275 | PASS |
| 17 | PF3 → return to menu | `browse` :58-60; `CardListUiController` `redirect:/menu` :82-83 | svc:`pfThreeOnReentryExitsToMenu_frS0422`:502; int:`pfThreeExits_frS0422`:270; ui:`pfThreeReturnsToMenu_frS0422`:379 | PASS |
| 18 | Unmapped AID → ENTER (S04-B3) | `normalizeAid` :182-191 | svc:`unmappedAidIsRemappedToEnter_frS0406`:196; int:`unmappedAidBehavesAsEnter_frS0406`:168; ui:`unmappedAidRelistsThePageLikeEnter_frS0406_s04B3`:187 | PASS |
| 19 | Info line rules (default on; off for filter error, PF7@1, no-more-pages, no-records) | `page` :120-141 | svc:`infoLineShowsByDefaultAndSuppressesOnErrors_frS0419`:448; ui:`infoAndErrorLinesFollowCobolRules_frS0419`:339 | PASS |
| 20 | Filter echo; cursor = first invalid filter / first flagged row 2–7 / acctsid | `screenView` :143-160; `cursorField` :165-178; echo :263-264 | ui:`invalidFilterEchoesAndProtectsEverySelect_frS0407_frS0411`:199; svc:`invalidSelectCodeShowsActionErrorAndRowCursor_frS0413`:335; svc:`commareaRoundTripsRowsAnchorsAndFlags_frS0418`:431 | PASS |
| 21 | Select enterable only on filled rows; all protected on filter error | `screenView` :155; `editInputs` :216/:228 `protect` | svc:`emptyRowSlotsRenderProtectedSelects_frS0420`:467; svc:`filterErrorProtectsEverySelectField_frS0411`:292; int:`blankFiltersLeaveSelectsUnprotected_frS0410`:215 | PASS |
| 22 | Backward exhaustion → bottom-aligned partial rows + verbatim file error (S04-B4) | `readBackwards` :346-350; `CobolMessages.CARD_FILE_ERROR_READ` :157-158 | svc:`backwardBrowseExhaustionRendersFileErrorLayout_s04B4`:481; int:`backwardExhaustionShowsVerbatimFileError_s04B4`:277; ui:`backwardExhaustionRendersFileErrorLayout_frS0421_s04B4`:352 | PASS |
| 23 | Unauthenticated → API 401 / UI redirect to sign-on | `SecurityConfig` `anyRequest().authenticated()` :43-48; entryPoint :60-67 (api → `SC_UNAUTHORIZED`, ui → redirect `/signon`) | ui:`unsignedAccessBouncesToSignon`:387. No unauthenticated `/api/cards` request test exists | PARTIAL |

## 2. COBOL parity spot-checks

Java cites are `spring-boot/src/main/java/com/carddemo/api/CobolMessages.java` unless noted; COBOL cites are `app/cbl/COCRDLIC.cbl`.

### Message catalogue — every entry verified verbatim

| Message | COBOL | Java | Match |
|---|---|---|---|
| `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER` | :1022 | :55 (`CARD_ACCOUNT_FILTER_INVALID`) | byte-equal |
| `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER` | :1058 | :53 (`CARD_FILTER_INVALID`) | byte-equal |
| `PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE` | :124 | :149-150 (`CARD_SELECT_ONE`) | byte-equal |
| `INVALID ACTION CODE` | :126 | :151 (`CARD_INVALID_ACTION`) | byte-equal |
| `NO RECORDS FOUND FOR THIS SEARCH CONDITION.` | :122, :1241-1245 | :152-153 (`CARD_NO_RECORDS_FOUND`) | byte-equal |
| `NO MORE RECORDS TO SHOW` | :1219, :1239 | :40 (`CARD_NO_MORE_RECORDS`) | byte-equal |
| `NO MORE PAGES TO DISPLAY` | :908 | :154 (`CARD_NO_MORE_PAGES`) | byte-equal |
| `NO PREVIOUS PAGES TO DISPLAY` | :903 | :41 (`CARD_NO_PREVIOUS_PAGES`) | byte-equal |
| `File Error: READ     on CARDDAT   returned RESP 000000020 ,RESP2 000000090 ` | :153-171 layout, used :1361-1369 | :157-158 (`CARD_FILE_ERROR_READ`) | byte-equal, 75 chars incl. trailing space; asserted `hasSize(75)` in svc:`backwardBrowseExhaustionRendersFileErrorLayout_s04B4` |
| `TYPE S FOR DETAIL, U TO UPDATE ANY RECORD` | :115-116 | :155-156 (`CARD_INFO_ACTIONS`) | byte-equal |
| `PF03 PRESSED.EXITING` | :120, set :396 | n/a — `exit` outcome carries no message | parity (never displayed in COBOL either) |

### Edit/validation rules

| Rule | COBOL | Java | Result |
|---|---|---|---|
| Filter "supplied" = not LOW-VALUES/SPACES/all-zeros | :1007-1017, :1042-1052 | `supplied` :271-276 | Match |
| NUMERIC class test on X(11)/X(16) | :1017-1025, :1052-1062 | `acctIn.matches("\\d{11}")` :213, `cardIn.matches("\\d{16}")` :225 | Match (digit strings only) |
| Validation order: acct → card → count → per-row; first message wins; selection edits skipped on filter error | :985-997, :1075-1077 | `editInputs` :210-262 (`if (error == null)` :229/:257; `!inputError` guard :238) | Match |
| I-SELECTED = last S/U row | :1099-1102 | `selected = i` inside loop :248 | Match |
| Row-1 select error colours but never takes cursor | :755-761 vs :770-783 | `cursorField` loops `i=1..6` → `crdsel2..7` :172-175 | Match |
| Dead `'*'` marker branch | :757-759 (unreachable) | not ported | Correct — unreachable in COBOL |

### File/DB access semantics

| Semantic | COBOL | Java | Result |
|---|---|---|---|
| STARTBR GTEQ on CARD-NUM | :1140-1158 | `browseForward`: `cardNumber >= :startKey` asc | `CardRepository.java`:27-34 — Match |
| Filtered READNEXT ×7 (`9500-FILTER-RECORDS` ANDs acct + card) | :1159-1190, :1382-1411 | same query, `PageRequest.of(0,7)` :284 | Match |
| Unfiltered look-ahead after row 7 decides next page | :1194-1214 | `nextAfter` unfiltered, limit 1 :299-300 | Match (parity quirk kept: can announce a next page that is empty under a filter — documented, FR doc §11.4) |
| first = row-1 key; next = look-ahead key else row-7 key | :1173-1176, :1211-1214 | :293, :303/:309 | Match |
| READPREV fills bottom-up; partial page bottom-aligned | :1264-1346 | `browseBackward` desc limit 7, placed rows 7→1 :332-341 | Match |
| Backward: next-anchor := old first anchor; next-page on | :1268, :1287 | :328-329 | Match |
| READPREV ENDFILE → first anchor unchanged + file error | :1348-1369 | :343-350 | Match |

### Page number / AID / exits

| Behaviour | COBOL | Java | Result |
|---|---|---|---|
| PIC 9(1) `+1` wraps 9→0 | :492 | `(screenNumber + 1) % 10` :87 | Match |
| PIC 9(1) unsigned `−1`, 0→1 | :508 | `screenNumber == 0 ? 1 : -1` :93 | Match |
| Rescue 0→1 on first-row fill | :1177-1178 | :294-296 | Match |
| PF7 on page 1 re-lists from first anchor + NO PREV PAGES | :439-454 | :76-79, :125-126 | Match |
| Dead PF8 → NO MORE PAGES; non-PF8 clears last-page flag | :410-414, :572-582 | :61-63, :127-131 | Match |
| Unmapped AID → ENTER | :370-380 | `normalizeAid` :182-191 | Match |
| PF3 → XCTL COMEN01C | :384-406 | outcome `exit` → `redirect:/menu` | Match (registry-resolved per S04-B1) |
| S/U → XCTL COCRDSLC/COCRDUPC with acct + card | :517-569 | `navigate` → `uiRouteForProgram` redirect with `accountId`/`cardNumber` | Match |

## 3. Stub/placeholder sweep

Searched `CardService.java`, `CardListUiController.java`, `CardRepository.java`, `card-list.html`, `api/CardList*.java`, `CardController.java`, and all three test classes for: `TODO`, `FIXME`, `not implemented`, `UnsupportedOperationException`, `@Disabled`, `assertTrue(true`, `assertEquals(true, true`, commented-out assertions, hardcoded happy-path returns. **Result: none found.** No `@Disabled`/ignored tests; surefire reports `Skipped: 0` for all three classes.

## 4. Documented deviations vs shipped code

| Deviation | Documented decision | Shipped evidence | Compliant |
|---|---|---|---|
| S04-B1 | S/U resolve through `MenuService` route registry; disabled target → coming-soon idiom | `UI_ROUTES` COCRDLIC/COCRDSLC/COCRDUPC `MenuService.java`:109-119; `CardListUiController` :85-96 (redirect or `optionComingSoon`); ui:`selectionNavigatesThroughRegistryOrShowsComingSoon_s04B1` | yes |
| S04-B2 | Selection-error re-browse from file start (uninitialized key), page number kept | `CardService.java`:66-74 + comment citing :300/:419-438; svc:`multipleSelectsFlagOnlyOneRecordAndRebrowseFromStart_frS0412` | yes |
| S04-B3 | Unmapped AID → ENTER, no invalid-key message | `normalizeAid` :182-191; three tests | yes |
| S04-B4 | Verbatim 75-byte file-error layout, RESP 20/RESP2 90 (docs-derived), bottom-aligned rows, anchor unchanged | `CobolMessages.java`:157-158; `readBackwards` :346-350; three tests incl. 75-char length assert | yes |
| PR #120 claim | "the info line is suppressed whenever an error is displayed" | Contradicted: `page` :129-133 shows `CARD_INFO_ACTIONS` together with `NO MORE RECORDS TO SHOW` on PF8 landing; svc:`pfEightExhaustionShowsInfoThenNoMorePages_frS0404` asserts both | **no — doc error** |
| FR §9 / plan §8 claim | `CardListIntegrationTest` = "Testcontainers PostgreSQL 16 seeded from `app/data/ASCII/carddata.txt`" | H2 in-memory (`jdbc:h2:mem:cardlistit`, `ddl-auto=create-drop`) `CardListIntegrationTest.java`:36-41; `cardRepository.deleteAll()` + 10 hand-seeded synthetic cards :59-66 | **no — doc error** |
| Undocumented | Fresh GET accepts filters | `editInputs` comment :193-194; `CardController` GET `/api/cards` takes filter params — COBOL receives map fields only on re-entry | undocumented delta (harmless widening) |
| Undocumented | COMMAREA-context filter echo dropped | COBOL echoes `CDEMO-ACCT-ID`/`CDEMO-CARD-NUM` into filter fields on non-menu re-entry (:849-866); Java echoes only typed input :263-264 | undocumented delta (UX quirk) |

## 5. Test evidence

Command (from repo root):

```
cd spring-boot && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  mvn -q test -Dtest='CardListServiceTest,CardListIntegrationTest,CardListUiIntegrationTest'
```

Observed (`target/surefire-reports/*.txt`, run 2026-09-16):

| Class | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| `CardListServiceTest` | 23 | 0 | 0 | 0 |
| `CardListIntegrationTest` | 13 | 0 | 0 | 0 |
| `CardListUiIntegrationTest` | 17 | 0 | 0 | 0 |
| **Total** | **53** | **0** | **0** | **0** |

Matches PR #120's claim of 53 new tests. The PR's `mvn clean verify — 122 tests` claim was not re-run (out of scope — separate audit covers the full suite).

## 6. Findings

| Sev | Title | Evidence | Recommendation |
|---|---|---|---|
| MEDIUM | FR doc §9 and plan §8 claim Testcontainers PostgreSQL 16 seeded from `carddata.txt`; shipped stream tests run H2 in-memory with 10 hand-seeded synthetic cards | `functional/CARDDEMO/S04_functional_requirement.md`:112; `S04_card_list_migration_plan.md`:81-82 vs `CardListIntegrationTest.java`:36-41,59-66 and `CardListUiIntegrationTest.java`:36-44 | Correct the doc claims, or switch the tests to the documented Testcontainers/seed setup. Behavioural coverage is unaffected but the docs overstate infra fidelity. |
| LOW | FR-S04-04 precedence clause untested — "when both filters fail the account message wins" has no named test | Impl correct via `if (error == null)` `CardService.java`:229; all filter tests send a single invalid filter | Add a service test posting invalid acct + card and asserting the account message. |
| LOW | FR-S04-05 AND-combination untested — no test applies a valid account filter and card filter together | `CardRepository.browseForward` ANDs both predicates :27-34; tests cover each filter alone only | Add a combined-filter test. |
| LOW | FR-S04-23 API-side 401 untested — no unauthenticated request to `/api/cards` or `POST /api/cards/list` | `SecurityConfig.java`:60-67 returns 401 JSON for `/api/**`; only UI redirect tested (`unsignedAccessBouncesToSignon` `CardListUiIntegrationTest.java`:387) | Add a MockMvc test asserting 401 without a session. |
| LOW | Test-name suffixes `_frS04NN` are ordinals, not FR ids — e.g. `…_frS0402` covers FR-S04-11 (PF7 on page 1), `…_frS0422` covers FR-S04-17 | `CardListServiceTest.java`:127,502 vs FR table numbering | Rename or document the convention; misleading for future audits. |
| LOW | COMMAREA-context filter echo not ported — returning to the list from another program (e.g. COCRDSLC) pre-fills the filter fields with `CDEMO-ACCT-ID`/`CDEMO-CARD-NUM` in COBOL; Java shows blanks | `COCRDLIC.cbl`:849-866 (`WHEN OTHER MOVE CDEMO-ACCT-ID TO ACCTSIDO`) vs `CardService.java`:263-264 (echo = typed input only) | Accept-and-document, or carry the context echo through the UI. |
| LOW | PR #120 description misstates info-line behaviour — "suppressed whenever an error is displayed" is false: the dead-PF8 landing shows info + error together | `CardService.java`:129-133 vs :305/:317; svc:`pfEightExhaustionShowsInfoThenNoMorePages_frS0404`:161-180 | Fix PR/doc prose; shipped behaviour is correct. |
| INFO | Tampered hidden page-state (non-numeric `rowAcct`) throws `NumberFormatException` → HTTP 500 | `CardListUiController.java`:112, :125-127 (`Long.parseLong` on client POST data) | Catch/guard; COMMAREA is client-controlled in the web port. |
| INFO | Select code echoed on empty rows — Java echoes a typed code on a null row; COBOL echoes only for non-LOW-VALUES rows | `CardService.java`:149-150 vs `COCRDLIC.cbl`:683 | Cosmetic edge case; note it. |
| INFO | `WS-CA-LAST/FIRST-CARD-ACCT-ID` anchors write-only in COBOL (reads commented out :448-449 etc.) — correctly dropped in Java | `COCRDLIC.cbl`:242-244; not present in `CardListPageState` | None — parity-safe. |
