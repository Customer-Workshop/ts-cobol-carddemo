# S-21 Tran-Type Maintenance — Independent Audit (Java / Spring Boot)

Audited branch: `devin/1789516557-carddemo-java-engagement`, HEAD
`410e5d0f826ccd3a6a2f4ea04eee673f64c240d8` (checked out via `devin/audit-s21-java`).
Auditor: independent (did not perform the migration). Date: 2026-09-16.
Scope: COTRTLIC (admin list screen, flag-D/U + F10 confirm, tran CTLI), COTRTUPC
(single-record maintenance state machine, tran CTTU), MNTTRDB2/COBTUPDT (batch
A/U/D apply), TRANEXTR (DSNTIAUL 60-char extracts), plus the COADM01C menu
options 5/6 entry points. Docs audited: `S21_functional_requirement.md`,
`S21_tran_type_maintenance_analysis.md`,
`S21_tran_type_maintenance_migration_plan.md`. Wave PR audited: #127
`devin/wave-s21-tran-type` (merged).

## Verdict: **PASS with findings**

All 14 FRs trace to concrete implementation classes and named tests; the scoped
stream suite runs 25 tests, 0 failures, 0 errors, 0 skipped. Message literals,
edit/validation rules, the 14-state machine, keyset cursor semantics, SQLCODE
mapping (-532/-911/-803/+100), the 53-byte batch record, and the 60-char extract
layouts are verbatim-faithful to `app/app-transaction-type-db2/cbl/*.cbl`,
`cpy/`, `bms/`, `jcl/`. All boundary decisions S21-B1..B7 and the wave PR's three
documented deviations shipped as recorded. Two MEDIUM findings are
implementation-vs-shipped-COBOL divergences that are undocumented (a posted-flag
silently dropped on filter-change turns, and a narrower PF5 AID gate than the
COBOL one); the rest are LOW/INFO.

## 1. Traceability matrix (FR → implementation → named test)

| FR | Requirement | Implementation (file:line) | Test name(s) | Status |
|---|---|---|---|---|
| FR-S21-01 | admin menu opts 5/6 → list/maint | `MenuService.java:55-56` (implemented=true), `:125-126` (UI_ROUTES COTRTLIC→`/ui/tran-types`, COTRTUPC→`/ui/tran-types/maint`) | `TranTypeListIntegrationTest.adminMenuOptionsRouteToBothScreens_frS2101` | PASS |
| FR-S21-02 | entry → ≤7 rows TR_TYPE order | `TranTypeService.browse` `TranTypeService.java:60-177`; `forward` `:348-387` (keyset `tranType >= :start`, PAGE_SIZE+1 peek) | `...freshEntryShowsFirstSevenRowsOrderedByCode_frS2102` | PASS |
| FR-S21-03 | type/desc filter + 'No Records found' | `ListTurn.editInputs` `:461-508` (`%02d` pad, `\\d{1,2}` gate, `%desc%` like); JPQL filters `TransactionTypeRepository` | `...filtersRejectBadCodeAndReportNoMatch_frS2103` | PASS |
| FR-S21-04 | F7/F8 paging + edge messages | `forward` `:348-387`, `backward` `:390-411` ('Error on fetch Cursor C-TR-TYPE-BACKWARD'); edge msgs `:220-235` | `...pageEdgesShowVerbatimMessages_frS2104` | PASS |
| FR-S21-05 | flag D/U + F10 confirm write/delete | `editArray` `:512-555`, `deleteFlagged` `:281-309` (-532 probe), `updateFlagged` `:312-345` (-911); F10 remap/degrade `:70-91` | `...deleteFlagThenF10DeletesAfterConfirm_frS2105`, `...updateFlagThenF10WritesNewDescription_frS2105` | PASS |
| FR-S21-06 | >1 action / bad flag errors | `editArray` `:512-555` — bad → `TRTYPE_ACTION_INVALID` `:545`, multi → `TRTYPE_SELECT_ONLY_ONE` `:553` | `...multipleOrBadFlagsRaiseTheVerbatimErrors_frS2106` | PASS |
| FR-S21-07 | F2 add → maint in create state | dispatch PF2 `TranTypeService.java:126-128` → `TranTypeNavigation("COTRTUPC","COTRTLIC")`; maint `R` state | `...pfTwoNavigatesToMaintAndPfThreeExits_frS2107_frS2112` | PASS |
| FR-S21-08 | maint search edits + no-change | `maintain` `:629-750`, `editSearchKey` `:958-980` (`%02d`, required/numeric/non-zero), `editDescription` `:983-998`, no-change compare `:896-905` | `...blankBadAndZeroCodesFailTheSearchEdits_frS2108`, `...searchHitShowsRecordAndMissPromptsCreate_frS2108`, `...unchangedSubmitShortCircuitsWithNoChangeMessage_frS2108` | PASS |
| FR-S21-09 | F5 save INSERT/UPDATE + -911 | `maintWrite` `:811-847` (update→+100→insert fallback; -911→`LOCK_ERROR`+`TTUP_LOCK_FAILED`) | `...f5SaveCommitsUpdateThroughNAndC_frS2109`, `...f5FromNotFoundCreatesTheRecord_frS2109` | PASS (see F-5: -911 path untested) |
| FR-S21-10 | F4 confirm-delete, -532 child guard | `maintDelete` `:777-805` (child-count probe → -532 keeps '8'; other → '6') + `V2801__tran_type_fk.sql` FK RESTRICT | `...f4DeletesAfterConfirm_frS2110`, `...f4DeleteWithChildrenStaysWithMinus532_frS2110` | PASS |
| FR-S21-11 | F12 cancel → originals + msgs | PF12 leg `:706-721` → `BACKED_OUT` + `TTUP_UPDATE_CANCELLED`/`TTUP_DELETE_CANCELLED` | `...f12CancelsUpdateAndDeleteWithVerbatimMessages_frS2111` | PASS |
| FR-S21-12 | F3 exit to caller | PF3 leg `:667-671` → `TranTypeNavigation(fromProgram ‖ COADM01C, "COTRTUPC")` | `...pfThreeReturnsToTheCallingProgram_frS2112` | PASS |
| FR-S21-13 | batch A/U/D/'*', bad→RC4 | `TranTypeMaintTasklet` + `BatchJobService.applyTranTypeRecord` `BatchJobService.java:597-637` (`REQUIRES_NEW` per record; 'ERROR: TYPE NOT VALID'/'No records found.'/'Error accessing:' → collected → FAILED) | `TranTypeMaintJobIT.maintJobAppliesAddUpdateDeleteAndSkipsComments_frS2113`, `...maintJobFailsWithRc4OnBadRecord_frS2113`, `...maintJobFailsOnUpdateMiss_frS2113` | PASS |
| FR-S21-14 | extract 60-char TRANTYPE.PS/TRANCATG.PS | `TranTypeExtractTasklet` + `tranTypeExtractLine`/`tranCatgExtractLine` `BatchJobService.java:640-651` (type(2)+desc(50)+'0'×8; type(2)+cat(%04d)+data(50)+'0'×4) | `TranTypeMaintJobIT.extractEmitsGoldenSixtyCharLayouts_frS2114` | PASS |

Auxiliary coverage: `...childGuardAndMissingRowsProduceVerbatimErrors_s21B4`
(list -532), `...restCrudRoundTripWithVerbatimErrors_s21B3` (REST CRUD +
-803/-532/404), `...nonAdminIsRejectedWithAdminOnly_s21Gate`
(`requireAdmin` `:1217-1225`), `...enterWhileConfirmDeleteIsInvalidKey_s21AidGate`.

## 2. COBOL parity spot-checks

All app/cbl references are under `app/app-transaction-type-db2/cbl/`.

**Message literals** — every constant in `CobolMessages.java:180-243`
(TRTYPE_* :182-209, TTUP_* :212-243) compared verbatim against the COBOL
WS literals: COTRTLIC.cbl:236-262 (filter-invalid, select-only-1, action-invalid,
no-records, page edges, confirm-delete/update, update/delete done, deadlock,
record-gone, child-records, delete-failed prefix) and COTRTUPC.cbl:140-196
(search-keys, code required/numeric/zero, desc required/alnum, no-record-found,
no-input, invalid-key, no-change, changes-committed/unsuccessful, lock-failed,
update/insert/delete-failed prefixes, update/delete cancelled). All match
byte-for-byte including the trailing space in `'Record not found. Deleted by
others ? '` (COTRTLIC.cbl:1846-1893 vs `CobolMessages.java:194-195`).

**Edit rules**
- Type filter: COBOL `IS ALL NUMERIC` + 2-digit (COTRTLIC.cbl:1111-1118) → Java
  `\\d{1,2}` + `%02d` zero-pad (`TranTypeService.java:470-480`). Shipped BMS
  TRTYPE is PIC X(2) so the 1-digit accept is lenient-but-compatible (INFO-9).
- Desc filter STRING `'%' TRIM '%'` (COTRTLIC.cbl:1155-1162) → `"%"+trim+"%"`
  like (`:568-575`).
- Maint search key: blank→'supplied', non-numeric→'numeric', zero→'not zero'
  (COTRTUPC.cbl:826-968) → `editSearchKey` `:958-980` identical order/wording.
- Desc: blank→'supplied', `INSPECT CONVERTING` alnum-only (COTRTUPC.cbl:983-1050
  region) → `Character.isLetterOrDigit(c) || c==' '` `:983-998,1181`.
- Blank-desc flag `*` echo (COTRTLIC.cbl:1414-1415, COTRTUPC.cbl:1336-1340) →
  Java `'*'` render `:209` and `'*'`→blank receive `:877-879`.

**File/DB semantics**
- Cursor browse → keyset `>= :start` / `< :end` order-by with PAGE_SIZE+1 peek
  (`forward` `:348-387`, `backward` `:390-411`); partial backward page →
  'Error on fetch Cursor C-TR-TYPE-BACKWARD' (COTRTLIC.cbl:1727-1791 vs `:405`).
- UPDATE +100 → 'Record not found...' + re-browse (COTRTLIC.cbl:1865-1893 vs
  `updateFlagged` `:312-345`); -911 → INPUT-ERROR + 'Deadlock...' (`:317-322`
  vs `:1865-1893`).
- DELETE -532 → child-records message; Java probes `transaction_categories`
  count then also catches `DataIntegrityViolationException` → sqlcode -532
  (`:281-309`, `:783-799`); FK added by `db/migration/V2801__tran_type_fk.sql`
  (ddl/TRNTYCAT.ddl RESTRICT parity).
- Maint UPDATE miss +100 → INSERT (COTRTUPC.cbl:1568-1603 vs `maintWrite`
  `:819-826`); INSERT -803 → 'Error inserting...' prefix (`:826-833`).
- Batch record: 53 bytes action X(1)+code X(2)+desc X(50) (COBTUPDT.cbl:39-129);
  `'*'` comment-skip, 'ERROR: TYPE NOT VALID', 'No records found.' →
  `BatchJobService.java:597-637`; REQUIRES_NEW per record reproduces
  RC4-and-continue (COBTUPDT.cbl:230-233) then job FAILED.
- Extract layouts: `TRANEXTR.jcl` STEP40/50 60-char SELECTs → `:640-651`
  verified (2+50+8 zeros; 2+4+50+4 zeros); `pad()` truncates/pads
  (`BatchFileSupport.pad`).

**Exit/abend codes** — 9999-ABEND '0001' paths (RC4 batch, DECIDE-ACTION OTHER)
map to job FAILED / state-unchanged screen (see §4); AID remap table (COTRTLIC
PFK validity :575-587, F10 remap :666-678) → `:70-91` verified, incl. the
"changed criteria under pending action" F10→ENTER degrade (`:86-91` vs
`:667-671` flag conditions).

**Screen state machine** — COTRTUPC 14 live states (' ',K,X,S,R,9,8,7,6,E,N,L,
F,C,B) → `TranTypeMaintState`; 3200/3250/3300 screen-var/cursor/attr logic →
`maintScreen`/`codeEditableFor` `:1046-1089` (editable fields, cursor field,
original-vs-new display groups verified vs cbl:1140-1250,1300-1350). COBOL 'V'
is declared never-set (cbl:306) — Java omits it (INFO).

## 3. Stub/placeholder sweep

Searched `TranTypeService`, `TranTypeController`, all `api/TranType*` DTOs,
`TranTypeUiController`, `batch/TranType{Maint,Extract}{Tasklet,JobConfiguration}`,
`BatchJobService`, `MenuService`, `Db2ErrorFormatter`, `templates/tran-type*.html`
and the three stream test classes for: TODO, FIXME, XXX, HACK, "not
implemented", UnsupportedOperationException, hardcoded happy-path returns,
`@Disabled`/ignored tests, `assertTrue(true)` / commented-out assertions.
**Found: none.** The REST surface (`GET/POST /api/tran-types`, `GET/PUT/DELETE
/api/tran-types/{code}`, `POST /api/tran-types/list`, `POST
/api/tran-types/maint`) is fully wired through the same service — no dead
endpoints. Note the COBOL-side placeholders ('No input received' shadowed by
the earlier supplied-check; 'V' state; `WS-INVALID-KEY` commented out
cbl:611-616) are preserved 1:1 rather than stubbed (INFO, not a gap).

## 4. Documented deviations vs shipped code

| # | Source | Deviation | Shipped? | Compliant |
|---|---|---|---|---|
| 1 | PR #127 | Empty `WHEN TTUP-DETAILS-NOT-FETCHED` leg of DECIDE-ACTION (cbl:984-988, merged-WHEN shares PF12 body) implemented as ' '+valid key→read→S/X | Yes — `maintain` else-branch `processMaintInputs`→read (`:725-733`,`896-905`) | YES |
| 2 | PR #127 | `UNEXPECTED DATA SCENARIO` abend (cbl:1073-1080) → "state-unchanged (no abend semantics in REST)" | Partly — unreachable for PF5@'9' (gate rejects first, F-2); other OTHER legs do keep state + show 'Invalid key pressed'. State-unchanged claim holds; the surviving paths differ from COBOL's abort | YES (see F-2) |
| 3 | PR #127 / S21-B5 | COBOL RC4 → job FAILED after all records applied | Yes — `applyTranTypeRecord` `REQUIRES_NEW` `BatchJobService.java:597-637`; test `maintJobFailsWithRc4OnBadRecord_frS2113` asserts applied rows + FAILED | YES |
| B1 | analysis:72 | Menu opts 5/6 implemented + UI_ROUTES | `MenuService.java:55-56,125-126` | YES |
| B2 | analysis:73 | F2→maint, PF3→caller routes | `:126-128`, `:667-671` | YES |
| B3 | analysis:74 | reuse entity/repo; keyset paging | `forward`/`backward` `:348-411` | YES |
| B4 | analysis:75 | V2801 FK RESTRICT + -532 friendly msg | `V2801__tran_type_fk.sql`; `:783-799` | YES |
| B5 | analysis:76 | `tranTypeMaintJob`; INPFILE→param Resource; RC4→FAILED | `TranTypeMaintJobConfiguration.java:30-40` | YES (default file missing — F-3) |
| B6 | analysis:77 | `tranTypeExtractJob` 60-char layouts; GDG→"versioned export files (optional, flagged)" | `TranTypeExtractTasklet`:52-53 writes `TRANTYPE.PS`/`TRANCATG.PS` via `output()` — single file, no versioning | YES (optional item not implemented, as flagged) |
| B7 | analysis:78 | DSNTIAC → `Db2ErrorFormatter`; priming → connectivity check on entry | `Db2ErrorFormatter.java`; `types.count()` `:95` | YES (fresh-entry skips check — F-8) |

## 5. Test evidence

Command (scoped, stream only):

```
cd spring-boot && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  mvn -q test -Dtest='TranTypeListIntegrationTest,TranTypeMaintIntegrationTest,TranTypeMaintJobIT'
```

Observed (surefire reports):

| Class | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| TranTypeListIntegrationTest | 11 | 0 | 0 | 0 |
| TranTypeMaintIntegrationTest | 10 | 0 | 0 | 0 |
| TranTypeMaintJobIT | 4 | 0 | 0 | 0 |
| **Total** | **25** | **0** | **0** | **0** |

(Full suite deliberately not run per brief; the wave PR claims 570/0 on
`mvn clean verify` — taken as documentation, not re-verified.)

## 6. Findings

| Sev | ID | Finding | Evidence | Recommendation |
|---|---|---|---|---|
| MEDIUM | F-1 | **Filter-change turn silently discards posted row flags.** On a submit where the type or desc filter changed, Java replaces `selects` with a blank array and skips `editArray` entirely — so a posted 'D'/'U'/'X' flag is never counted or validated (no 'Action code selected is invalid', no 'Please select only 1 action', no confirm). Shipped COBOL intends the same skip at COTRTLIC.cbl:991-994, but it is dead code: `WS-TYPE/DESCFILTER-CHANGED` live in per-invocation working storage (cbl:111-116, inside WS-MISC-STORAGE, not the COMMAREA) and are only SET inside 1230/1220-EXIT (cbl:1174,:1136) which run *after* 1210-EDIT-ARRAY (PERFORM order cbl:965-972). So shipped COBOL always runs the array edit; Java diverges from shipped behavior and undocumented. | `TranTypeService.java:484-488` vs `COTRTLIC.cbl:991-994,111-116,965-972,1132-1174` | Either run the array edit before the filter-change check (match shipped COBOL) or keep the intent-faithful skip and record it as a documented deviation. |
| MEDIUM | F-2 | **PF5 AID gate omits CONFIRM_DELETE ('9').** `validAid` allows PF5 only for N/X/8/7/6; COBOL `0001-CHECK-PFKEYS` accepts PF5 for `TTUP-DELETE-IN-PROGRESS` = '9','8','7','6' — including '9'. In COBOL, PF5@'9' passes the gate and falls to the DECIDE-ACTION OTHER leg → ABEND '0001' 'UNEXPECTED DATA SCENARIO'. Java instead emits 'Invalid key pressed' with the state kept at '9'. PR #127's deviation ("abend → state-unchanged") loosely covers the outcome, but the gate itself is narrower than shipped COBOL and this narrower mapping is undocumented. | `TranTypeService.java:759-763` vs `COTRTUPC.cbl:588-593,307-309,1073-1080` | Acceptable under the abend deviation, but document the '9' gate difference; if byte-parity is wanted, admit '9' at the gate and let the OTHER leg handle it. |
| LOW | F-3 | **`tranTypeMaintJob` default input file does not exist.** Default is `Path.of(dataDirectory,"ASCII","trantype-update.txt")`; `app/data/ASCII/` has no such file — a run without the `inputFile` parameter fails at open. (Legacy parity is arguable: MNTTRDB2.jcl ships literal `DSN=INPFILE`, also unresolved.) | `TranTypeMaintJobConfiguration.java:36-38`; `ls app/data/ASCII/` (trantype.txt, trancatg.txt, …) | Ship a sample `trantype-update.txt` fixture or make `inputFile` a required parameter with a clear failure. |
| LOW | F-4 | **'Invalid key pressed' is lost on post-reset turns.** Java resets terminal states E/N/L/F/C/B(→' ') *before* the AID gate (`:647-662` then `:666`), so an AID judged invalid in COBOL (gate at cbl:400-401 runs before reset :405-419) is evaluated against ' ' instead — e.g. PF4 arriving while state='C' is valid-after-reset in Java (silent fresh map) but 'Invalid key pressed' in COBOL. | `TranTypeService.java:647-666` vs `COTRTUPC.cbl:400-419` | Gate on the pre-reset action to match COBOL ordering. |
| LOW | F-5 | **Untested error-code mappings.** `-911→LOCK_ERROR+'Could not lock record for update'` (`maintWrite` `:836-841`) and the list-update `+100→'Record not found. Deleted by others ? '` (`updateFlagged` `:312-345`) have no asserting test; H2 cannot naturally produce -911. `-532` maint path and `-803` REST path are covered. | `TranTypeService.java:836-841,312-345`; tests cover only -532/-803/+100-delete paths | Add unit tests that stub the repository to throw the mapped `DataAccessException`s. |
| INFO | F-6 | **Stored descriptions are trimmed** (`newDesc.trim()` `:813`; list update `:331`; batch `:601`) while COBOL writes the full 50-byte VARCHAR with trailing spaces (`COTRTUPC.cbl:1540-1542`, `COTRTLIC.cbl:1845-1848`, `COBTUPDT.cbl:145-146`). Read-side equivalent. | as cited | Acceptable; note if a byte-level recon ever diffs stored rows. |
| INFO | F-7 | **FR doc §5 catalogue paraphrases don't match shipped literals** — 'Transaction Type: Enter a valid 2 digit code' / 'Description is required' are not real messages; shipped (and COBOL-correct) literals are 'Tran Type code must be supplied./must be numeric./must not be zero.' and 'Transaction Desc must be supplied./must be alphanumeric.' Doc inaccuracy only; implementation matches COBOL. | `S21_functional_requirement.md` §5 vs `COTRTUPC.cbl:826,865-968` / `CobolMessages.java:212-243` | Correct the FR doc catalogue to the real literals. |
| INFO | F-8 | **Priming connectivity check skipped on fresh entry.** COBOL `PERFORM 9998-PRIMING-QUERY` runs every invocation (COTRTLIC.cbl:684); Java runs `types.count()` only inside `if (received)` — a fresh GET skips the health probe. | `TranTypeService.java:81-101` vs `COTRTLIC.cbl:684-691` | Run the check unconditionally if DB-failure-on-entry parity matters. |
| INFO | F-9 | **Posted `'*'` sentinel maps to blank.** Java treats a submitted `'*'` code as empty → 'Tran Type code must be supplied.' (`:877-879`); COBOL `IS ALL NUMERIC` fails on `'*'` → 'must be numeric.' Also the 1-digit type-filter accept (`\\d{1,2}` `:470`) is lenient vs the X(2) BMS field — both leniencies are input-side only. | `TranTypeService.java:877-879,470` vs `COTRTUPC.cbl:865-968`, `COTRTLI.bms` | Optional: treat posted '*' as non-numeric for exact message parity. |
| INFO | F-10 | **PR text overclaims the state set.** PR #127 lists 'V' (REVIEW-NEW-RECORD) in the ported states; `TranTypeMaintState` has no 'V'. COBOL declares it at cbl:306 but never SETs it — omission is correct; the PR description is slightly inaccurate. Also 'No input received' (TTUP_NO_INPUT) is shadowed by the supplied-check on both sides — preserved dead path, consistent. | PR #127 description; `COTRTUPC.cbl:306`; `TranTypeService.java:903-905` | Doc-level; no code change needed. |
| INFO | F-11 | **Fabricated delete-miss message ends in a bare ':'.** `maintDelete` `!existsById` returns `…+sqlcodeDisplay(100)+":"` with no SQLERRM tail (COBOL appends ':' + SQLERRM text, cbl:1638-1660); REST DELETE-miss test asserts only via `containsString` so the bare colon is invisible. Same for the list-delete fabricated +100 (`:289,305`). | `TranTypeService.java:780-782,289,305` vs `COTRTUPC.cbl:1638-1660` | Append a generic reason string or drop the trailing colon. |
| UNVERIFIED | F-12 | **`app/data/ASCII/trantype.txt` seed layout.** The file is in the 60-char extract layout (`01Purchase…00000000`) and `DataSeeder` reads it at width 60 (`DataSeeder.java:128`) — the seeder path was not fully traced in this audit; the test seed `src/test/resources/seed/ASCII/trantype.txt` is a plain 11-byte `01Purchase`. If the seeder slices by the extract layout the real seed works; flagged UNVERIFIED rather than guessed. | `app/data/ASCII/trantype.txt` vs `DataSeeder.java:128,137` | Verify `parseTypes` slices code/desc at the extract offsets (2/50) for the 60-char rows. |
