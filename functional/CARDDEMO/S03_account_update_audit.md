# S03 — Account Update (COACTUPC / CAUP / CACTUPA) — Independent Audit

- **Stream:** S03 — account_update
- **Audited branch:** `devin/audit-s03-java` (cut from `devin/1789516557-carddemo-java-engagement`)
- **HEAD sha:** `410e5d0f826ccd3a6a2f4ea04eee673f64c240d8`
- **Auditor:** independent auditor (did not perform the migration)
- **Date:** 2026-09-16
- **Scope:** COBOL source `app/cbl/COACTUPC.cbl`, copybooks `app/cpy/{CSUTLDPY,CSLKPCDY,CSSETATY,CVACT01Y,CVCUS01Y,CVACT03Y}.cpy`, map `app/bms/COACTUP.bms`; Java implementation under `spring-boot/src/main/java/com/carddemo/` (`api/AccountUpdateController.java`, `api/CobolMessages.java`, `service/AccountUpdateService.java`, `service/AccountUpdateEditRules.java`, `service/AccountUpdateScreen.java`, `service/AccountUpdateSnapshot.java`, `service/AccountUpdateForm.java`, `ui/UiController.java` `:437-607`, `templates/account-update.html`); stream tests `AccountUpdateEditRulesTest`, `AccountUpdateServiceTest`, `AccountUpdateIntegrationTest`, `AccountUpdateUiIntegrationTest`.

## Verdict: **PASS with findings**

34 FR rows traced: 33 PASS, 1 PARTIAL (FR-S03-21). All scoped tests pass (47/47). One MEDIUM parity gap in the phone "all-blank" test, plus three LOW/INFO items. No CRITICAL/HIGH findings, no wholly unimplemented FR.

## 1. Traceability matrix

| FR | Requirement | Implementation | Test(s) | Status |
|----|-------------|----------------|---------|--------|
| FR-S03-01 | First entry → Search state, blank details, info prompt, id editable + cursor | `AccountUpdateService.initialScreen()`; `UiController.java:437-444`; `AccountUpdateScreen.java` (State.SEARCH, `accountIdEditable()`) | `AccountUpdateUiIntegrationTest.firstEntryShowsSearchPrompt_frS0301` | PASS (FR doc §9 names test `initialState_frS0301`; actual name differs — see F-05) |
| FR-S03-02 | Blank/`*` id → `No input received` | `AccountUpdateEditRules.blank()` `AccountUpdateEditRules.java:82-87`; `AccountUpdateService.lookup()` `AccountUpdateService.java:61-70` | `AccountUpdateUiIntegrationTest.blankOrStarSearchIsNoInputReceived_frS0302`; `AccountUpdateServiceTest.searchEditRejectsBlankStarAndNonElevenDigitIds_frS0302_frS0303`; `AccountUpdateIntegrationTest.searchEditRejectsBlankAndBadIds_frS0302_frS0303` | PASS |
| FR-S03-03 | Non-11-digit/all-zero id → `Account Number if supplied must be a 11 digit Non-Zero Number` | `AccountUpdateService.lookup()` `AccountUpdateService.java:63-69`; PUT path `update()` `AccountUpdateService.java` (regex `\d{1,11}` nonzero) | `AccountUpdateServiceTest.searchEditRejectsBlankStarAndNonElevenDigitIds_frS0302_frS0303`; `AccountUpdateIntegrationTest.searchEditRejectsBlankAndBadIds_frS0302_frS0303`; `AccountUpdateServiceTest.putPathEditsAndSnapshotGate_frS0303` | PASS |
| FR-S03-04 | No xref row → 75-char `Account:<id> not found in Cross ref file.  Resp:000000013  Reas:0000` | `AccountUpdateService.lookup()` `AccountUpdateService.java:71-76`; `CobolMessages.xrefNotFound` `CobolMessages.java:328-330` | `AccountUpdateIntegrationTest.lookupFailsInXrefAccountCustomerOrder_frS0304_frS0305_frS0306`; `AccountUpdateServiceTest.lookupStopsAtTheFirstMissingRecord_frS0304_frS0305_frS0306` | PASS |
| FR-S03-05 | Xref hit, account miss → `Account:<id> not found in Acct Master file.Resp:000000013  Reas:0000` (D2) | `AccountUpdateService.lookup()` `AccountUpdateService.java:77-84`; `CobolMessages.accountNotFound` `CobolMessages.java:331-333` | same pair as FR-S03-04 | PASS |
| FR-S03-06 | Account hit, customer miss → `CustId:<custid> not found in customer master.Resp: 000000013  REAS:0000000` | `AccountUpdateService.lookup()` `AccountUpdateService.java:85-102`; `CobolMessages.customerNotFound` `CobolMessages.java:334-338` | same pair as FR-S03-04 | PASS |
| FR-S03-07 | All three found → Details state, derivations (money `+ZZZ,ZZZ,ZZZ.99`, dates y/m/d, SSN 3/2/4, phone 3/3/4, zip first 5), info `Update account details presented above.` | `AccountUpdateService.lookup()` `AccountUpdateService.java:88-102`; `AccountUpdateSnapshot.of()/displayForm()/phoneParts()` `AccountUpdateSnapshot.java`; `CobolFormat.editSignedAmount` `CobolFormat.java` | `AccountUpdateIntegrationTest.lookupShowsDerivedOriginals_frS0307`; `AccountUpdateUiIntegrationTest.validLookupRendersDetailsWithProtection_frS0307`; `AccountUpdateServiceTest.lookupReadsXrefThenAccountThenCustomer_frS0307` | PASS |
| FR-S03-08 | No-change detection (text trimmed+case-insensitive, money numeric, parts exact) → `No change detected with respect to values fetched.` | `AccountUpdateEditRules.changed()` `AccountUpdateEditRules.java:159-204`; `AccountUpdateService.validate()` `AccountUpdateService.java:105-116` | `AccountUpdateEditRulesTest.unchangedOrCaseAndSpaceOnlyDifferencesAreNotChanges_frS0308`; `AccountUpdateServiceTest.validateNoChangeKeepsDetails_frS0308`; `AccountUpdateUiIntegrationTest.unchangedEnterReportsNoChangeDetected_frS0308`; `AccountUpdateIntegrationTest.validateUnchangedAndBadEdits_frS0308_frS0324` | PASS |
| FR-S03-09 | Edit ladder order, first failing edit supplies message, later edits still flag | `AccountUpdateEditRules.validate()` `AccountUpdateEditRules.java:228-267` | `AccountUpdateEditRulesTest.firstMessageComesFromEarliestFailingEdit_frS0309`; `validFormPassesEveryEdit_frS0309`; `AccountUpdateServiceTest.validateRunsLadderToConfirmOrEditError_frS0309_frS0323_frS0324` | PASS |
| FR-S03-10 | Y/N edits; `*`/spaces/zeros → blank | `AccountUpdateEditRules.editYesNo` | `AccountUpdateEditRulesTest.yesNoEditsTreatBlankStarSpacesAndZerosAsMissing_frS0310` | PASS |
| FR-S03-11 | Date part + combination edits (CSUTLDPY messages) | `AccountUpdateEditRules.editDate` | `AccountUpdateEditRulesTest.datePartEditsFollowCsutldpy_frS0311` | PASS |
| FR-S03-12 | DOB must be strictly in the past → `Date of Birth:cannot be in the future ` | `AccountUpdateEditRules.editDob` (`isBefore(now)`) | `AccountUpdateEditRulesTest.dobNotStrictlyInThePastIsRejected_frS0312` | PASS |
| FR-S03-13 | Money via NUMVAL-C forms ($ , . +/- CR/DB) | `AccountUpdateEditRules.numvalC` `AccountUpdateEditRules.java:129-156`, `editMoney` | `AccountUpdateEditRulesTest.moneyEditsUseTestNumvalCForms_frS0313` | PASS |
| FR-S03-14 | SSN parts numeric-required + part1 not 666/900-999 | `AccountUpdateEditRules.editSsn` | `AccountUpdateEditRulesTest.ssnPartsAreNumericRequiredPlusPartOneRanges_frS0314` | PASS |
| FR-S03-15 | FICO numeric-required + range 300-850 | `AccountUpdateEditRules.editFico` | `AccountUpdateEditRulesTest.ficoIsNumericRequiredThenRanged_frS0315` | PASS |
| FR-S03-16 | Alphabetic required/optional (Middle Name optional) | `AccountUpdateEditRules.editAlphaRequired/editAlphaOptional` | `AccountUpdateEditRulesTest.alphaEditsAllowLettersAndSpacesOnly_frS0316` | PASS |
| FR-S03-17 | Address Line 1 mandatory; Line 2 unedited | `AccountUpdateEditRules.editMandatory`; ladder has no line-2 edit | `AccountUpdateEditRulesTest.addressLine1IsMandatoryAndLine2IsUnedited_frS0317` | PASS |
| FR-S03-18 | State: alphabetic then 56-code US list | `AccountUpdateEditRules.editUsState`; `cslkpcdy/us-state-codes.txt` | `AccountUpdateEditRulesTest.stateMustBeInUsListAfterAlphaCheck_frS0318` | PASS |
| FR-S03-19 | Zip: 5-digit numeric-required | `AccountUpdateEditRules.editNumRequired` | `AccountUpdateEditRulesTest.zipIsFiveDigitNumericRequired_frS0319` | PASS |
| FR-S03-20 | State+zip2 combo in 240-row list, both flagged | `AccountUpdateEditRules.editStateZip`; `cslkpcdy/us-state-zip-combos.txt` | `AccountUpdateEditRulesTest.stateAndZipCombinationMustPair_frS0320` | PASS |
| FR-S03-21 | Phones 3/3/4: all-blank → accept, else per-part area/prefix/line edits | `AccountUpdateEditRules.editPhone` `AccountUpdateEditRules.java:460-500`; `cslkpcdy/us-area-codes.txt` | `AccountUpdateEditRulesTest.phoneEditsWalkAreaThenPrefixThenLine_frS0321` | **PARTIAL — see F-01** |
| FR-S03-22 | EFT account id: 10-digit numeric-required | `AccountUpdateEditRules.editNumRequired` | `AccountUpdateEditRulesTest.eftAccountIdIsTenDigitNumericRequired_frS0322` | PASS |
| FR-S03-23 | All edits pass → Confirm state, `Changes validated.Press F5 to save`, F5/F12 lit, ENTER redisplays | `AccountUpdateEditRules.validate()` → `State.CONFIRM`; `AccountUpdateScreen.f5Lit()/f12Lit()`; `UiController.java` ENTER dispatch | `AccountUpdateIntegrationTest.validateValidChangesReachConfirm_frS0323`; `AccountUpdateUiIntegrationTest.validEditsReachConfirmWithF5Lit_frS0323`; `AccountUpdateServiceTest.validateRunsLadderToConfirmOrEditError_frS0309_frS0323_frS0324` | PASS |
| FR-S03-24 | Edit failed → Edit-error state: first message, invalid fields highlighted, blank-invalid shown as `*`, F12 lit | `AccountUpdateScreen.flagged()/value()`; `templates/account-update.html` (`field-red`, `th:readonly`) | `AccountUpdateUiIntegrationTest.failedEditsRenderRedAndStarBlankedFields_frS0324`; `AccountUpdateIntegrationTest.validateUnchangedAndBadEdits_frS0308_frS0324` | PASS |
| FR-S03-25 | F5 in Confirm → lock acct then cust, snapshot compare, REWRITE both, `Changes committed to database` | `AccountUpdateService.save()` `AccountUpdateService.java:142-207` (`@Transactional`) | `AccountUpdateUiIntegrationTest.pf5InConfirmCommitsAndDoneEnterRefetches_frS0325_frS0330`; `AccountUpdateIntegrationTest.saveWritesBothRowsAndReportsDone_frS0325_frS0334`; `AccountUpdateServiceTest.saveCommitsAndAppliesTheWriteMapping_frS0325_frS0334` | PASS |
| FR-S03-26 | Account lock fails → `Could not lock account record for update` + `Changes unsuccessful. Please try again` | `AccountUpdateService.save()` lock branch; `lockProbeOk()` `AccountUpdateService.java:249-265`; `AccountRepository.findByIdForUpdate` `AccountRepository.java:15-19` (`PESSIMISTIC_WRITE`, `lock.timeout=0`) | `AccountUpdateIntegrationTest.accountLockContentionReportsFailure_frS0326` (real second-connection contention); `AccountUpdateServiceTest.saveLockFailuresReportAndKeepValues_frS0326_frS0327` | PASS |
| FR-S03-27 | Customer lock fails → `Could not lock customer record for update` + failure info (D1: Java goes to FAILED, source falls through to Done) | `AccountUpdateService.save()` customer branch; `CustomerRepository.findByIdForUpdate` `CustomerRepository.java:12-16` | `AccountUpdateIntegrationTest.customerLockContentionReportsFailure_frS0327`; `AccountUpdateServiceTest.saveLockFailuresReportAndKeepValues_frS0326_frS0327` | PASS |
| FR-S03-28 | Stored rows differ from fetched snapshot → `Record changed by some one else. Please review`, back to Details, nothing written | `AccountUpdateService.matchesAccount()/matchesCustomer()` `AccountUpdateService.java:323-356`; `ScreenRollbackException` carrier | `AccountUpdateIntegrationTest.concurrentStoreChangeRollsBackToDetails_frS0328`; `AccountUpdateServiceTest.saveSnapshotMismatchRollsBackToDetails_frS0328` | PASS |
| FR-S03-29 | REWRITE fails → `Update of record failed` + failure info; whole unit rolled back | `AccountUpdateService.save()` catch → `ScreenRollbackException` → `State.FAILED` | `AccountUpdateServiceTest.saveWriteFailureRollsBackAndReports_frS0329` | PASS (mocked failure only — see F-04) |
| FR-S03-30 | ENTER in Done → re-fetch same account into Details (D3) | `UiController.java` dispatch `case DONE -> cancel` | `AccountUpdateUiIntegrationTest.pf5InConfirmCommitsAndDoneEnterRefetches_frS0325_frS0330` | PASS |
| FR-S03-31 | F12 → re-read acct+cust, discard edits, Details, message cleared | `AccountUpdateService.cancel()`; `UiController.java` F12 dispatch | `AccountUpdateUiIntegrationTest.f12RereadsAndClearsEdits_frS0331`; `AccountUpdateServiceTest.cancelRereadsTheRecord_frS0331`; `AccountUpdateIntegrationTest.repeatLookupRereadsCurrentRows_frS0331` | PASS |
| FR-S03-32 | F3 in any state → `/menu`, no write | `UiController.java` PF3 → `redirect:/menu`; `MenuService.java:123` route | `AccountUpdateUiIntegrationTest.f3ExitsToMenuFromAnyState_frS0332` | PASS |
| FR-S03-33 | Other AIDs → `Invalid key pressed. Please see below...`, screen unchanged (source treats as ENTER) | `UiController.java` default dispatch → `invalidAid()`; `AccountUpdateService.invalidAid()` per-state info | `AccountUpdateUiIntegrationTest.otherAidsShowInvalidKeyAndKeepTheScreen_frS0333` | PASS |
| FR-S03-34 | Write mapping: acct status/5 amounts/3 dates/group; cust names/addr/state/zip(5)/phones re-stringed/SSN/DOB/EFT/ind/FICO | `AccountUpdateService.apply()` `AccountUpdateService.java:392-429` | `AccountUpdateIntegrationTest.saveWritesBothRowsAndReportsDone_frS0325_frS0334`; `AccountUpdateServiceTest.saveCommitsAndAppliesTheWriteMapping_frS0325_frS0334` | PASS |

## 2. COBOL parity spot-checks

**Message catalogue (§5 of FR doc — every entry checked):**

| Message | COBOL | Java | Match |
|---------|-------|------|-------|
| `Enter or update id of account to update` | `COACTUPC.cbl:466` (WS-INFO-MSG 88) | `CobolMessages.java:161` `ACCOUNT_UPDATE_PROMPT` | verbatim |
| `Update account details presented above.` | `:467` | `:162` `ACCOUNT_UPDATE_DETAILS` | verbatim |
| `Changes validated.Press F5 to save` | `:480` area (WS-RETURN-MSG 88) | `:163` `ACCOUNT_UPDATE_CONFIRM` | verbatim |
| `Changes committed to database` | `:480-528` | `:164` `ACCOUNT_UPDATE_COMMITTED` | verbatim |
| `Changes unsuccessful. Please try again` | `:480-528` | `:165` `ACCOUNT_UPDATE_UNSUCCESSFUL` | verbatim |
| `Account Number if supplied must be a 11 digit Non-Zero Number` | `:480-528` | `:166` `ACCOUNT_UPDATE_ID_INVALID` | verbatim |
| `Could not lock account record for update` | `:3903-3912` | `:167` `COULD_NOT_LOCK_ACCOUNT` | verbatim |
| `Could not lock customer record for update` | `:3924-3933` | `:168` `COULD_NOT_LOCK_CUSTOMER` | verbatim |
| `Account:<id> not found in Cross ref file.  Resp:000000013  Reas:0000` (75-char STRING cut) | `:3650-3698` | `:328-330` `xrefNotFound` | verbatim (X(75) cut recomputed by hand — identical incl. 2-space `Resp`/`Reas`) |
| `Account:<id> not found in Acct Master file.Resp:000000013  Reas:0000` | `:3701-3748` | `:331-333` `accountNotFound` | verbatim |
| `CustId:<custid> not found in customer master.Resp: 000000013  REAS:0000000` | `:3752-3797` | `:334-338` `customerNotFound` | verbatim (7-digit `REAS` cut confirmed) |
| WS-FILE-ERROR-MESSAGE layout (`File Error ... Resp:xx Resp2:xx`) | `:389-408` | `fileError()` | verbatim; RESP 17 / RESP2 120 convention preserved |
| No input received / No change detected / Record changed by some one else / Update of record failed / Invalid key pressed | `:464-528` message pool | `CobolMessages.java` constants | verbatim |

**Edit rules:** all CSUTLDPY date messages verified verbatim against `CSUTLDPY.cpy:19-283` incl. `':day must be a number between 1 and 31.'` (lowercase `d`), `':Not a leap year.Cannot have 29 days in this month.'`, `':cannot be in the future '` (trailing space). Leap rule `yy=0 → ÷400 else ÷4` = Java `isLeapYear` equivalent. Ladder order `AccountUpdateEditRules.java:228-267` vs `COACTUPC.cbl:1469-1677` — identical. `blank()` mirrors `'*'`-then-spaces/LOW-VALUES (CSSETATY FLG-BLANK + `1100-RECEIVE-MAP` `'*'`/SPACES→LOW-VALUES `:1050-1100`). `numvalC` covers the documented TEST-NUMVAL-C forms `:2180-2221`.

**Lookup-table data:** programmatic diff of all three CSLKPCDY 88-lists vs `spring-boot/src/main/resources/cslkpcdy/*.txt` — zero diffs: `VALID-GENERAL-PURP-CODE` (410 rows) = `us-area-codes.txt`; `VALID-US-STATE-CODE` (56) = `us-state-codes.txt`; `VALID-US-STATE-ZIP-CD2-COMBO` (240) = `us-state-zip-combos.txt`.

**Derivations (§6):** money `+ZZZ,ZZZ,ZZZ.99` (`COACTUPC.cbl:371` PIC + `:2787-2867` derivations) = `CobolFormat.editSignedAmount`; SSN split (1:3)/(4:2)/(6:4) and phone (2:3)/(6:3)/(10:4) = `AccountUpdateSnapshot.phoneParts()` slices; zip first 5; custId `%09d`; accountId `%011d`. 9700 compare field list (`:4109-4200`) = `matchesAccount`/`matchesCustomer` (`AccountUpdateService.java:323-356`): group id case-insensitive (LOWER), names case-insensitive, zip/phone/SSN/DOB/EFT/ind/FICO exact — DOB part offsets verified non-buggy (ACUP-OLD-CUST-DOB is X(08) `YYYYMMDD`, `COACTUPC.cbl:746-751`, so `CUST-DOB(6:2)=MM` vs `ACUP-OLD(5:2)=MM` is correct, not an off-by-one).

**File/DB access semantics:** READ UPDATE acct→cust then REWRITE acct→cust (`:3888-4105`) = `@Transactional save()` ordering `AccountUpdateService.java:142-207`; FOR UPDATE NOWAIT = `findByIdForUpdate` (`AccountRepository.java:15-19`, `CustomerRepository.java:12-16`, `PESSIMISTIC_WRITE` + `jakarta.persistence.lock.timeout=0`); rollback on 2nd-REWRITE failure = `ScreenRollbackException` thrown past the transaction boundary.

**Exit/abend codes:** PF3 XCTL to `COMEN01C` (`:927-960`) = redirect `/menu` (S03-B1); abend path `ABEND-CODE 0001` 'UNEXPECTED DATA SCENARIO' (`:2635-2642`) intentionally not ported (D4); `ABCODE 9999` abend routine (`:4195-4222`) not ported — framework exception handling, consistent with plan.

## 3. Stub/placeholder sweep

Searched `AccountUpdate*.java`, `AccountUpdateController.java`, `CobolMessages.java`, `UiController.java`, and all four stream test classes for `TODO`, `FIXME`, `not implemented`, `UnsupportedOperationException`, `@Disabled`, `@Ignore`, `assertTrue(true)`, and commented-out assertions — **none found**.

One dead helper found: `CobolMessages.phoneInvalid(String, int)` (`CobolMessages.java:359-361`) is never called, ignores its `part` parameter, and hardcodes the area-code message — stub-like leftover, no behavioral impact (F-03).

## 4. Documented deviations vs shipped code

| Deviation | Source | Compliant? |
|-----------|--------|------------|
| S03-B1: PF3 → `/menu` | `UiController.java` PF3 → `redirect:/menu`; `MenuService.java:123` | yes |
| S03-B2: one `@Transactional` save, FOR UPDATE NOWAIT locks acct→cust | `AccountUpdateService.java:142-207`; repos `lock.timeout=0` | yes |
| S03-B3: stateless COMMAREA via `orig.*`/`screenState` form fields | `templates/account-update.html:15-45`; `UiController.java:437-607` | yes |
| S03-B4: COCRDUPC/COCRDLIC/COCRDSLC off-stream | no cross-program references in stream code | yes |
| S03-B5: CSUTLDTC not ported (S-09 owns it) | no CSUTLDTC usage found | yes |
| D1: customer-lock failure → DONE in source (`WHEN OTHER` fall-through `:2600-2612`), Java → FAILED | `AccountUpdateService.save()` customer branch → `State.FAILED` | yes (as documented) |
| D2: source continues after account-miss (`SET DID-NOT-FIND-ACCT-IN-ACCTDAT` commented out `:3720`); Java stops | `AccountUpdateService.java:77-84` stops | yes (as documented) |
| D3: Done+ENTER re-fetches instead of stale snapshot | `case DONE -> cancel` in `UiController.java` | yes |
| D4: Failed+no-change ENTER → validate instead of abend 0001 | dispatch falls to `validate` | yes |
| D5: blank/`*`/spaces normalized uniformly | `norm`/`blank` in `AccountUpdateEditRules.java` | yes |
| Wave PR #128: ScreenRollbackException carrier, `screenState` param name, lock probe, H2 instead of Testcontainers, `*` padded-compare quirk | all present as described | yes |
| **Undocumented:** phone all-blank clause divergence | see F-01 | **no — finding** |

## 5. Test evidence

Command (run on `devin/audit-s03-java` @ `410e5d0`):

```
cd spring-boot && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  mvn -q test -Dtest='AccountUpdateEditRulesTest,AccountUpdateServiceTest,AccountUpdateIntegrationTest,AccountUpdateUiIntegrationTest'
```

Results (surefire reports):

| Class | Run | Failures | Errors | Skipped |
|-------|-----|----------|--------|---------|
| AccountUpdateEditRulesTest | 16 | 0 | 0 | 0 |
| AccountUpdateServiceTest | 11 | 0 | 0 | 0 |
| AccountUpdateIntegrationTest | 10 | 0 | 0 | 0 |
| AccountUpdateUiIntegrationTest | 10 | 0 | 0 | 0 |
| **Total** | **47** | **0** | **0** | **0** |

Matches the wave PR's claimed 47 tests. Lock-contention tests use a real second JDBC connection holding `FOR UPDATE` — verified in log output (H2 lock-conflict stack trace is the expected contention signal inside the test).

## 6. Findings

| ID | Severity | Title | Evidence | Recommendation |
|----|----------|-------|----------|----------------|
| F-01 | MEDIUM | Phone "all-blank" test misreads the third COBOL clause — Java accepts a phone with blank area+prefix and a typed line part that COBOL rejects with `…: Area code must be supplied.` | COBOL `COACTUPC.cbl:2236-2239`: third clause is `(WS-EDIT-US-PHONE-NUMA EQUAL SPACES OR WS-EDIT-US-PHONE-NUMC EQUAL LOW-VALUES)`. When blanked input arrives as LOW-VALUES (`1100-RECEIVE-MAP`, `:1050-1100`), NUMA=LOW-VALUES → `NUMA=SPACES` is false and `NUMC=LOW-VALUES` is false for a typed line → edits run. Java `AccountUpdateEditRules.java:464-465`: `if (blank(a) && blank(b) && (blank(a) || blank(c))) return;` reduces to `blank(a) && blank(b)`, always accepting. Test `phoneEditsWalkAreaThenPrefixThenLine_frS0321` codifies the Java reading. Undocumented deviation (not in D1–D5). FR-S03-21 → PARTIAL. | Change clause 3 to `a.equals-spaces || c.equals-low-values` semantics — e.g. `(isLiteralSpaces(a) || isLowValues(c))` — or document the acceptance as a deviation. Note: typed digits are never persisted either way (apply() rebuilds phone from stored parts), so impact is a missing error message, not data corruption. |
| F-02 | LOW | `lockProbeOk` uses plain `SELECT … FOR UPDATE` without NOWAIT on a pooled auto-released connection | `AccountUpdateService.java:159-166, 249-265`. On a blocking lock manager (e.g. Postgres) the probe can wait instead of failing fast; the authoritative check is the JPA `findByIdForUpdate` with `lock.timeout=0` (`AccountRepository.java:15-19`, `CustomerRepository.java:12-16`). Documented in wave PR #128 as an H2 fast path. | Add `NOWAIT`/`FOR UPDATE NOWAIT` to the probe SQL when the dialect supports it, or drop the probe and rely on the NOWAIT JPA reads. |
| F-03 | LOW | Dead helper `CobolMessages.phoneInvalid(String, int)` — never called, ignores `part`, returns a fixed area-code message | `CobolMessages.java:359-361`; no call sites in `src/main` or `src/test` | Remove it or wire it into the phone edit path (currently per-part messages are inline literals). |
| F-04 | INFO | FR-S03-29 write-failure path is exercised only via mocked `saveAndFlush` throwing; no real-DB test forces a failed REWRITE | `AccountUpdateServiceTest.saveWriteFailureRollsBackAndReports_frS0329` (Mockito); contrast with real second-connection coverage for FR-S03-26/27/28 in `AccountUpdateIntegrationTest` | Add one integration test that violates a constraint (or forces a flush failure) against H2 to prove both rows stay unchanged. |
| F-05 | INFO | FR doc §9 cites `AccountUpdateUiIntegrationTest.initialState_frS0301`; the actual test is `firstEntryShowsSearchPrompt_frS0301` | `S03_functional_requirement.md:31` vs test class | Cosmetic doc drift — update the FR doc test name on the next doc pass. |
