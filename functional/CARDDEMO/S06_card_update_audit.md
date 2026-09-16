# S06 — Card Update (COCRDUPC) — Independent Audit

- **Stream:** S06 — card_update (COCRDUPC, tran CCUP, map CCRDUPA / mapset COCRDUP)
- **Audited branch:** `devin/audit-s06-java` (created from `devin/1789516557-carddemo-java-engagement`)
- **Audited HEAD:** `410e5d0f826ccd3a6a2f4ea04eee673f64c240d8`
- **Auditor:** independent auditor (did not perform the migration)
- **Date:** 2026-09-16
- **Scope:** FR-S06-01..29 per `functional/CARDDEMO/S06_functional_requirement.md`; implementation under `spring-boot/` (`CardService` S-06 block, `UiController` `/cards/update`, `CardController`, `CobolMessages`, `CardRepository`, `card-update.html`, `MenuService`); parity vs `app/cbl/COCRDUPC.cbl`, `app/cpy/CVACT02Y.cpy`, `app/bms/COCRDUP.bms`.

## Verdict: **PASS with findings**

All 29 FRs trace to concrete implementation code and named tests; every catalogued message is verbatim; all documented deviations (D1/D2/D3, S06-B1..B4) shipped as registered. Scoped suite: 45 tests, 45 passed. Findings are one LOW (REST save surface accepts a shorter account id than the source's 11-digit edit and uses a dead-code message for blank) and two INFO (doc-wording nits). No CRITICAL/HIGH; no FR unimplemented.

## 1. Traceability matrix

Convention: `CS` = `spring-boot/src/main/java/com/carddemo/service/CardService.java`; `Ui` = `.../ui/UiController.java`; `Svc`/`IT`/`UiIT` = `CardUpdateServiceTest` / `CardUpdateIntegrationTest` / `CardUpdateUiIntegrationTest`.

| FR | Requirement | Implementation | Test(s) | Status |
|---|---|---|---|---|
| FR-S06-01 | First entry: empty screen, search editable, `Please enter Account and Card Number` | `CS.initialCardUpdate()` CS:421; GET `Ui.cardUpdate` Ui:288-303; menu opt 5 → `UI_ROUTES["COCRDUPC"]` MenuService:119 | `freshEntryShowsSearchPromptWithEditableKeys_frS0601` (Svc); `freshGetShowsSearchScreenWithoutRunningEdits_frS0601`, `unauthenticatedAccessBouncesToSignon_frS0601`, `menuOptionFiveDispatchesToCardUpdate_frS0601` (UiIT) | PASS |
| FR-S06-02 | Unknown AID → ENTER, no invalid-key message | `CS.normalizeUpdateAid` CS:462-474 (ENTER/PF3 always, PF5 only N, PF12 only fetched, else ENTER) | `unmappedAidRemapsToEnterWithoutInvalidKeyMessage_frS0602` (Svc); `unmappedAidActsAsEnterWithNoInvalidKeyMessage_frS0602` (UiIT) | PASS |
| FR-S06-03 | PF3 in any state → `/menu` | `Ui.submitCardUpdate` Ui:328-330 (`PF3`/`F3` → `redirect:/menu`) | `pfThreeTransfersToTheMenu_frS0603` (UiIT) | PASS |
| FR-S06-04 | Blank/zero/`*` account → `Account number not provided`, `*` echo, cursor acct | `CS.editUpdateAccount` CS:515-529 (`null` or `0`.repeat(11) → blank); `'*'` echo CS:698-701 | `blankAccountShowsNotProvidedWithStarEcho_frS0604` (Svc) | PASS |
| FR-S06-05 | Account not 11 digits → `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER` | `CS.editUpdateAccount` CS:524-528 (`\d{11}`) | `nonElevenDigitAccountShowsFilterError_frS0605` (Svc) | PASS |
| FR-S06-06 | Blank card → `Card number not provided` (only if no earlier msg), flagged | `CS.editUpdateCard` CS:532-546 + first-message-wins `setMessage` | `blankCardShowsNotProvidedWithStarEcho_frS0606` (Svc) | PASS |
| FR-S06-07 | Card not 16 digits → `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER` | `CS.editUpdateCard` CS:532-546 (`\d{16}`) | `nonSixteenDigitCardShowsFilterError_frS0607` (Svc) | PASS |
| FR-S06-08 | Both keys blank → `No input received` overrides | `CS.editUpdateInputs` CS:485-511 (accountBlank && cardBlank → NO_INPUT_RECEIVED) | `bothKeysBlankShowNoInputReceived_frS0608` (Svc) | PASS |
| FR-S06-09 | Both keys fail → first (account) message shown; both flagged; cursor acct | `UpdateEdits.setMessage` first-wins; cursor order acct→card CS:687-784 | `doubleKeyErrorShowsAccountMessageFirst_frS0609` (Svc) | PASS |
| FR-S06-10 | Valid keys → read by card number only; OLD image; state S; `Details of selected card shown above`; cursor name | `CS.readCardForUpdate` CS:624-648 (`findById(cardNumber)` only); `UpdateState.setOldImage` CS:868+ (upper-cases name, splits date); INFOMSG CS:687-784 | `validKeysFetchOldImageAndShowDetails_frS0610` (Svc); `lookupFetchesByCardNumberOnlyAndShowsOldImage_frS0610` (IT, acct `00000000002` vs stored acct 1); `searchOnUiFetchesAndShowsOldImage_frS0610` (UiIT) | PASS |
| FR-S06-11 | Card not found → `Did not find cards for this search condition`; both keys flagged; stays not fetched | `CS.readCardForUpdate` CS:639-645 (null → both flags + message, no state S) | `missedReadFlagsBothKeysAndShowsNotFound_frS0611` (Svc); `lookupMissShowsNotFoundAndFlagsBothKeys_frS0611` (IT) | PASS |
| FR-S06-12 | Other read failure → 75-char `File Error: READ …RESP 000000017 ,RESP2 000000000 ` template | `CS.readCardForUpdate` CS:629-637; `CobolMessages.CARD_UPDATE_FILE_ERROR_READ` :255-256 | `readErrorShowsRespFileErrorTemplate_frS0612` (Svc, asserts `.hasSize(75)`) | PASS |
| FR-S06-13 | No field differs from OLD (5-field, case-insensitive) → `No change detected with respect to values fetched.`; stays S | `UpdateState.sameData` CS:~900-940 (name/status case-insensitive, year/month/day equal — no CVV, deviation D2); `editUpdateInputs` CS:485-487 → NO_CHANGES | `unchangedInputShowsNoChangeDetectedAndStaysShown_frS0613` (Svc) | PASS |
| FR-S06-14 | Name blank → `Card name not provided`, `*` | `CS.editUpdateName` CS:550-564 | `blankNameShowsRequiredWithStarEcho_frS0614` (Svc) | PASS |
| FR-S06-15 | Name non-alpha → `Card name can only contain alphabets and spaces` | `CS.editUpdateName` CS:556-562 (`[A-Za-z ]+`) | `nonAlphaNameShowsAlphabetError_frS0615` (Svc) | PASS |
| FR-S06-16 | Status blank or not literal `Y`/`N` → `Card Active Status must be Y or N` | `CS.editUpdateStatus` CS:567-582 (first char, uppercase literals only) | `nonYnStatusShowsStatusError_frS0616` (Svc — incl. lowercase `y`/`n` rejected) | PASS |
| FR-S06-17 | Month blank/non-numeric/not 01..12 (JUSTIFY-RIGHT zero-fill) → `Card expiry month must be between 1 and 12` | `CS.editUpdateMonth` CS:585-600; `NewInput.receive` zero-fill CS:811-820 | `monthOutOfRangeShowsMonthError_frS0617` (Svc — `0`→`00`→blank→`*`, `13` invalid, `5`→`05` valid) | PASS |
| FR-S06-18 | Year blank/non-numeric/not 1950..2099 (zero-filled `25`→`0025` fails) → `Invalid card expiry year` | `CS.editUpdateYear` CS:603-618 | `yearOutOfRangeShowsYearError_frS0618` (Svc) | PASS |
| FR-S06-19 | Multiple invalid → all 4 edits run, first message (name→status→month→year), all flagged, cursor first failing, state E, `Update card details presented above.` | `CS.editUpdateInputs` CS:493-511 (ladder always runs all four, first-wins msg); cursor + reds CS:720-784 | `multipleDetailFailuresFlagAllAndShowFirstMessage_frS0619` (Svc) | PASS |
| FR-S06-20 | All edits pass → state N, protected, `Changes validated.Press F5 to save`, bright `F5=Save F12=Cancel` | `CS.editUpdateInputs` CS:~508-511 → N; `confirmPending` CardUpdateScreen:43-45; legend `card-update.html`:75-83 (`th:if=confirmPending`) | `allEditsPassShowValidatedAwaitingF5_frS0620` (Svc); `validateTurnRunsTheEditLadderAndPromotesToN_frS0620` (IT); `validatedEditShowsConfirmLegendAndPromotesToN_frS0620` (UiIT — asserts literal `F5=Save F12=Cancel`) | PASS |
| FR-S06-21 | ENTER in N → redisplayed unchanged in N | `CS.cardUpdate` CS:449-455 — ENTER in N runs edits (skipped: N/C skip → stays N) | `enterInValidatedStateStaysAwaitingF5_frS0621` (Svc) | PASS |
| FR-S06-22 | PF5 in N, unchanged → rewrite name/status/`year-month-<old day>`; state C; `Changes committed to database` | `CS.cardUpdate` CS:453-455 → `writeCardForUpdate` CS:652-682 → `saveLocked` CS:1176-1230 (`findForUpdate` @Lock, compare, `LocalDate.of(newYear,newMonth,oldDay)` :1214-1216, save :1220) | `pfFiveOnUnchangedRecordCommitsAndPreservesCvv_frS0622` (Svc — CVV 123 & acct 1 preserved, D1); `pfFiveCommitsAndPreservesCvvAndAccount_frS0622` (IT) | PASS |
| FR-S06-23 | PF5 in N, record differs → `Record changed by some one else. Please review`; OLD refreshed; state S | `saveLocked` CS:1193-1199 (5-field compare → CHANGED) → `writeCardForUpdate` CHANGED → S + `setOldImage` refresh | `pfFiveOnChangedRecordShowsReviewAndRefreshesOld_frS0623` (Svc); `pfFiveOnRecordChangedByOtherShowsReview_frS0623` (IT) | PASS |
| FR-S06-24 | READ UPDATE fails (record gone/store error) → `Could not lock record for update`; state L; `Changes unsuccessful. Please try again` | `saveLocked` CS:1183-1188 (exception or absent → LOCK_ERROR) → `writeCardForUpdate` → L | `pfFiveOnDeletedRecordShowsLockError_frS0624` (Svc); `pfFiveOnMissingRecordShowsLockError_frS0624`, `putOnDeletedCardShowsLockError_frS0624` (IT, PUT → 409) | PASS |
| FR-S06-25 | REWRITE fails → `Update of record failed`; state F; `Changes unsuccessful. Please try again` | `saveLocked` CS:1210-1229 (exception → restore image → UPDATE_FAILED) → `writeCardForUpdate` → F | `rewriteFailureShowsUpdateFailed_frS0625`, `nonCalendarExpiryFailsAsRewriteError_frS0625d3` (Svc); `nonCalendarExpiryOnPutFailsTheUpdate_frS0625d3` (IT, PUT → 500) | PASS |
| FR-S06-26 | ENTER after C/L/F → fresh search screen | `CS.cardUpdate` CS:440-442 (`state.done()` → `initialCardUpdate`) before any input processing | `anyAidAfterDoneResetsToFreshSearch_frS0626` (Svc — loops C, L, F); `enterAfterCommittedResetsToFreshSearch_frS0626` (UiIT) | PASS |
| FR-S06-27 | PF12 with details fetched → record re-read, OLD image in S, edit-pass message kept | `CS.cardUpdate` CS:449-452 (fetched + PF12 → `readCardForUpdate`, no message overwrite); `readCardForUpdate` preserves `edits.message` on success | `pfTwelveReReadsAndRestoresOldKeepingTheMessage_frS0627` (Svc — asserts prior `Card name not provided` survives re-read) | PASS |
| FR-S06-28 | Called with acct+card known → immediate fetch, state S (`?acctId=&cardNum=`) | `Ui.cardUpdate` GET Ui:288-300 (both spellings `acctId/cardNum` and `accountId/cardNumber`; blank → fresh search) | `getWithKeysPreFetchesTheRecord_frS0628` (UiIT — covers both param spellings) | PASS |
| FR-S06-29 | Field lengths 11/16/50/1/2/4; day read-only | `card-update.html` maxlengths on acctsid/cardsid/crdname/crdstcd/expmon/expyear; `expday` `readonly` :70 | `fieldsCarryBmsLengthsAndProtectedDay_frS0629` (UiIT — asserts every maxlength + legend dark outside N) | PASS |

## 2. COBOL parity spot-checks

### 2a. Message catalogue — every §5 entry verbatim

`CobolMessages` vs `COCRDUPC.cbl` WS-RETURN-MSG 88-values (:146-214):

| Message | Java | COBOL | Verdict |
|---|---|---|---|
| `Please enter Account and Card Number` | CobolMessages:257-258 | cbl:162-164 (WS-INFO-MESSAGE init) | verbatim |
| `Details of selected card shown above` | CobolMessages:259-260 | cbl:166-168 (INFO-OK) | verbatim |
| `Update card details presented above.` | CobolMessages:261-262 | cbl:169-171 | verbatim |
| `Changes validated.Press F5 to save` (no space after period) | CobolMessages:263-264 | cbl:172-174 | verbatim |
| `Changes committed to database` | CobolMessages:265-266 | cbl:175-176 | verbatim |
| `Changes unsuccessful. Please try again` | CobolMessages:267-268 | cbl:177-179 area (INFO-FAILED) | verbatim |
| `Account number not provided` | CobolMessages:249-250 | cbl:178 (`WS-PROMPT-FOR-ACCT`) | verbatim |
| `Card number not provided` | CobolMessages:251-252 | cbl:180 (`WS-PROMPT-FOR-CARD`) | verbatim |
| `Card name not provided` | CobolMessages:60 | cbl:182 (`WS-PROMPT-FOR-NAME`) | verbatim |
| `Card name can only contain alphabets and spaces` | CobolMessages:61 | cbl:184 | verbatim |
| `No input received` | CobolMessages:49 | cbl:186 (NO-SEARCH-CRITERIA-RECEIVED) | verbatim |
| `No change detected with respect to values fetched.` | CobolMessages:66-67 | cbl:188 | verbatim |
| `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER` | CobolMessages:54-55 | cbl:194 literal at :745 | verbatim |
| `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER` | CobolMessages:52-53 | cbl:789 | verbatim |
| `Card Active Status must be Y or N` | CobolMessages:62 | cbl:196 | verbatim |
| `Card expiry month must be between 1 and 12` | CobolMessages:63-64 | cbl:198 | verbatim |
| `Invalid card expiry year` | CobolMessages:65 | cbl:200 | verbatim |
| `Did not find cards for this search condition` | CobolMessages:58-59 | cbl:204 | verbatim |
| `Could not lock record for update` | CobolMessages:253-254 | cbl:206 | verbatim |
| `Record changed by some one else. Please review` | CobolMessages:68-69 | cbl:208 (incl. "some one") | verbatim |
| `Update of record failed` | CobolMessages:122 | cbl:210 | verbatim |
| `File Error: READ     on CARDDAT   returned RESP 000000017 ,RESP2 000000000 ` | CobolMessages:255-256 | cbl:146-158 + :133-152 layout | verbatim; exactly 75 chars — the source's X(75) `WS-RETURN-MSG` truncates the 80-byte layout (5-byte FILLER → 1 trailing space); test asserts `.hasSize(75)`. RESP `000000017`/RESP2 `000000000` per FR §11 assumption |

### 2b. Edit rules

- **Search edits run only while not-fetched** (cbl:645-663 vs CS:482-489): same guard; both-blank → `No input received` override (cbl:656-661 vs CS:487-491).
- **Blank = LOW-VALUES/SPACES/ZEROS** (cbl:725-728 acct, :762-766 card): Java treats `null` (from `'*'`/blank receive, `NewInput.receive` CS:811-820) and `"0".repeat(11/16)` as blank — CS:519-521, :536-538. Parity incl. `*` → LOW-VALUES (cbl:589-635 RECEIVE logic vs `lowValuesOnStar`).
- **Not-numeric vs not-provided message split**: cbl:742-753 / :784-792 vs CS:524-528 / :541-544. Parity.
- **First message wins** (`IF WS-RETURN-MSG-OFF` guards, cbl:731, :776, :817, etc.) vs `UpdateEdits.setMessage` — parity.
- **Same-data check before the detail ladder** (cbl:680-693): `UPPER-CASE(NEW) = UPPER-CASE(OLD)` over name/year/month/day/status — `UpdateState.sameData` does the same five-field case-insensitive compare (CVV absent by design, D2). Skips the ladder when unchanged/N/C — CS:485-493.
- **Detail ladder order name→status→month→year, all run** (cbl:698-712) vs CS:493-507. Parity; first-wins on the *message* only, all flags still set.
- **Status literal `Y`/`N`** (cbl:845-872, `FLG-YES-NO-VALID` :91): CS:567-582 compares uppercase literals — lowercase `y`/`n` rejected, test-proven.
- **Month/year ranges** (cbl:95 `1 THRU 12`, :99 `1950 THRU 2099`): CS:585-600 / :603-618. BMS JUSTIFY=RIGHT zero-fill (`bms:129`, :137) reproduced in `NewInput.receive` (`5`→`05`, `25`→`0025`).
- **Name alphabetic+space**: cbl:823-836 INSPECT vs regex `[A-Za-z ]+` CS:556-562.
- **Name stored as typed on save; OLD image upper-cased for compare** — `setOldImage` uppercases/trim name (CS:868+); `saveLocked` stores `newName.trim()` (:1217) while compare is `sameIgnoreCase` (:1193).

### 2c. File/DB access semantics

- **Keyed read by card number only; account never matched** — cbl:1343-1412 reads `CARDDAT` on `CARD-NUM`; the account match is commented out at :1379-1380 (verified: lines are comments). Java `findById(input.cardNumber)` CS:628 — no account predicate. IT `lookupFetchesByCardNumberOnlyAndShowsOldImage_frS0610` proves a different valid account still fetches (and the save test uses acct `00000000009` vs stored 1).
- **Locked read + compare + write in one unit** (READ UPDATE/REWRITE, cbl:1420-1495): `CardRepository.findForUpdate` `@Lock(PESSIMISTIC_WRITE)` (:49-53) inside `cardUpdate`'s `@Transactional` (CS:433); absent or exception → `Could not lock record for update` (CS:1183-1188 = cbl:1436-1447). Lock-acquisition/timeout exception is mapped to the same COULD-NOT-LOCK outcome — reasonable analogue of a failed READ UPDATE.
- **9300 compare then REWRITE** (cbl:1453-1517): five displayed fields compared (CVV omitted, D2); mismatch → `Record changed by some one else. Please review` + OLD image refreshed from the row (CS:1198-1199 returns the card for `setOldImage`). Match → set name/status/date + `save` (CS:1208-1220). Failure → `Update of record failed` and the managed entity's fields are restored (CS:1222-1228), matching the no-persist contract.
- **Date assembly `year-month-<old day>`** — COBOL `STRING CCUP-NEW-EXPYEAR '-' EXPMON '-' CCUP-OLD-EXPDAY` (cbl ~:1470-1483) vs `LocalDate.of(newYear,newMonth,oldDay)` CS:1214-1216 (D3: no clamp → non-calendar date throws → UPDATE_FAILED).

### 2d. Exit/abend & navigation

- **PF3 → calling program** (cbl:435-478 XCTL to caller): `redirect:/menu` (Ui:328-330), S06-B3.
- **ABEND codes** `'0001'` (unexpected action, cbl:1019-1027) and `'9999'` (ABEND-ROUTINE, cbl:1531-1556): unreachable-equivalents — `normalizeUpdateAid` makes "unexpected" impossible and there is no analog of a forced abend in the target surface; the failed-write path (`F` state / REST 500) is the mapped counterpart. Documented in plan §3-4; accepted.
- **Six-state machine** `""/S/E/N/C/L/F` (cbl:274-313 CCUP-CHANGE-ACTION) round-tripped via `CardUpdateCommarea` hidden fields (template :13-20) — parity incl. done-reset ordering before input processing (CS:440-442 = cbl:517-528).

## 3. Stub/placeholder sweep

Searched `CardService.java`, `Card*.java`, `CobolMessages.java`, `UiController.java`, `card-update.html`, `CardUpdateServiceTest.java`, `CardUpdateIntegrationTest.java`, `CardUpdateUiIntegrationTest.java` for `TODO`, `FIXME`, `XXX`, `HACK`, `UnsupportedOperationException`, `not implemented`, `@Disabled`, `@Ignore`, `assertTrue(true)`, `assertEquals(1, 1)`, `// stub`, `placeholder`:

- **None found** — zero matches.
- Test assertions reviewed (all 45): real AssertJ/MockMvc checks on exact message strings, flags, state, cursor, echoes, and DB content (e.g. CVV/account preservation in `pfFiveOnUnchangedRecordCommitsAndPreservesCvv_frS0622`). No commented-out assertions; the `.hasSize(75)` check on the file-error template is a *stronger* assertion than typical.
- No hardcoded happy-path returns in implementation (every outcome routes through the real repository/`@Lock` path).

## 4. Documented deviations vs shipped code

| Deviation (plan §3-4 / FR §11 / PR #126 register) | Shipped? | Evidence |
|---|---|---|
| **D1** — REWRITE preserves stored CVV + account id (source defect wrote CVV `000` + typed acct, cbl:1461-1465) | Yes | `saveLocked` never sets CVV/acct (CS:1208-1220 — only name, status, expiry); tests assert `getCardCvvCode()==123`, `getCardAcctId()==1L` (Svc `pfFiveOnUnchangedRecordCommitsAndPreservesCvv_frS0622`, IT `pfFiveCommitsAndPreservesCvvAndAccount_frS0622`) |
| **D2** — CVV omitted from the concurrency compare (never leaves server; 5 displayed fields only) | Yes | `CardUpdateCommarea` has no cvv field (`CardUpdateCommarea.java`:11-19, doc comment cites D2); `saveLocked` compare is 5 fields (CS:1193-1197) vs source's 6-field incl. CVV (cbl:1503-1508) |
| **D3** — `LocalDate.of(year, month, oldDay)` no day-clamp; non-calendar date → `Update of record failed` | Yes | CS:1211-1216 comment + code; Svc `nonCalendarExpiryFailsAsRewriteError_frS0625d3` (Feb-30 → F, row unchanged), IT `nonCalendarExpiryOnPutFailsTheUpdate_frS0625d3` (500) |
| **S06-B1** — `findById` read + `findForUpdate` `@Lock(PESSIMISTIC_WRITE)` + compare + `save` in one `@Transactional`; NOTFND/IOERR messages per catalogue | Yes | CardRepository:49-53; CS:433 (`@Transactional`), 1176-1230; messages verified §2a |
| **S06-B2** — `GET /cards/update?acctId=&cardNum=` prefetch; return-to-list unmigrated; bad/missing params → fresh search | Yes | Ui:288-300 (both param spellings — covers COCRDLIC U-select `accountId/cardNumber` too); blank/blank → `initialCardUpdate` |
| **S06-B3** — PF3 → `/menu` | Yes | Ui:328-330; UiIT `pfThreeTransfersToTheMenu_frS0603` |
| **S06-B4** — shared `cards` table; this stream is the only online writer | Yes | Only `cardRepository.save` in `main` outside seeders is CS:1220 (grep verified) |
| Flyway range V140x reserved-unused | Consistent | `spring-boot/src/main/resources/db/migration/` has no V14xx scripts (V1,V2,V3,V2201,V2601,V2801 only) |

No undocumented deviations found in the shipped code beyond the LOW item in §6 (a baseline-REST-surface looseness, not an S06-introduced drift).

## 5. Test evidence

Command (scoped run only, per brief):

```
cd spring-boot && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  mvn -q test -Dtest='CardUpdateServiceTest,CardUpdateIntegrationTest,CardUpdateUiIntegrationTest'
```

Observed (surefire txt reports):

| Class | Tests run | Failures | Errors | Skipped |
|---|---|---|---|---|
| `com.carddemo.service.CardUpdateServiceTest` | 27 | 0 | 0 | 0 |
| `com.carddemo.CardUpdateIntegrationTest` | 8 | 0 | 0 | 0 |
| `com.carddemo.CardUpdateUiIntegrationTest` | 10 | 0 | 0 | 0 |
| **Total** | **45** | **0** | **0** | **0** |

Matches the wave PR's stated test plan (45 new tests). Integration tests run on H2 (`@TestPropertySource`), not Testcontainers — no Docker dependency. Full suite was not run (out of scope per brief).

## 6. Findings

| Sev | Finding | Evidence | Recommendation |
|---|---|---|---|
| LOW | REST `PUT /api/cards/{cardNumber}` pre-save edits are looser than the source's 1210/1220 ladder: `validateAccount` accepts `\d{1,11}` (any 1-11 digits) where the source's `X(11) IS NUMERIC` rejects anything not exactly 11 digits; a blank/absent account emits `Account number must be a non zero 11 digit number` — a string that exists in the source (`SEARCHED-ACCT-ZEROES`, cbl:189-190) but is **dead code there** (declared, never SET) — instead of `Account number not provided` (cbl:178, set at :731). Blank card on PUT collapses onto `CARD ID FILTER…` instead of `Card number not provided`. Screen path is verbatim-correct; the PUT surface (baseline contract, widened per plan §9 FR-22..25 mapping) is the only gap. | `CardService.validateAccount` CS:1250-1261 (`\d{1,11}` at :1256, `ACCOUNT_NUMBER_INVALID` at :1258-1259); `CardService.update` CS:1139-1169; COBOL cbl:724-738 (blank→PROMPT-FOR-ACCT), :742-753 (NOT NUMERIC→filter), :189-192 (88 declared only). | Tighten PUT account edit to `\d{11}`; emit `Account number not provided` for blank and `Card number not provided` for blank card, mirroring 1210/1220. |
| INFO | FR-S06-12 row text says "both search fields flagged" for other-read-failure, but the source flags only the account field and only when no message is already set (cbl:1403-1407 `IF WS-RETURN-MSG-OFF → SET FLG-ACCTFILTER-NOT-OK`). The implementation matches the source exactly (CS:631-635) — the FR doc overstates. | COCRDUPC.cbl:1402-1411 vs CardService.java:629-637; FR doc line 50. | Amend the FR row to "account field flagged (conditional on message-off)". |
| INFO | Doc premise now stale: analysis §5 / plan §3 justify "return-to-list stays unmigrated" because "S-04 has no UI_ROUTES entry" — S-04 has since merged and `COCRDLIC → /cards/list` exists (MenuService.java:110). Shipped behavior still matches the *documented decision* (PF3→/menu, post-done reset, no CD02 return-to-list), so no code finding; but the rationale in `S06_card_update_analysis.md`/`_migration_plan.md` and FR §11's "unreachable until S-04 migrates" no longer describes the current codebase. | MenuService.java:110 (`Map.entry("COCRDLIC", "/cards/list")`); S06 docs; PR #126 notes S-04's U-select is live. | Update the stream docs to note COCRDLIC now exists and return-to-list remains a decided non-goal (or file follow-up to wire caller=list → `/cards/list`). |

**UNVERIFIED items:** none — every catalogued claim was checked against both sides. Row-lock contention behavior (concurrent PF5 storms) is verified at the contract level (`@Lock` + compare tests); no load-level contention test exists, which is normal for this suite.
