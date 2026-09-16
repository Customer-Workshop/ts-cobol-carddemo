# S-12 User Admin — Independent Audit Notes

- Stream: **S-12 user_admin** (COUSR00C list/browse, COUSR01C add, COUSR02C update, COUSR03C delete)
- Audited branch: `devin/audit-s12-java` checked out from `devin/1789516557-carddemo-java-engagement` FETCH_HEAD
- HEAD sha: `410e5d0f826ccd3a6a2f4ea04eee673f64c240d8`
- Auditor: independent auditor (did not perform the migration)
- Date: 2026-09-16
- Scope: every FR-S12-01..40 traced to implementation + named tests; verbatim parity spot-checks of the message catalogue, edit rules, file/DB semantics, and exit/AID behaviour against `app/cbl/COUSR0*C.cbl`, `app/cpy/CSMSG01Y.cpy`, `app/bms/COUSR0*.bms`; stub/placeholder sweep; documented deviations (S12-B1..B5, S01-B5 closure) vs shipped code; scoped JUnit run of the stream's test classes. Wave PR audited: #124 (merged).
- Implementation under audit: `spring-boot/src/main/java/com/carddemo/service/AdminUserService.java`, `ui/UserAdminUiController.java`, `api/AdminUserController.java` (+ `AdminUserRequest`/`AdminUserResponse`/`AdminUserListResponse`), `api/CobolMessages.java`, `repository/SecurityUserRepository.java`, `model/SecurityUser.java`, templates `user-list/add/update/delete.html`, `MenuService` UI_ROUTES, `SecurityConfig` admin gate.

## Verdict: **PASS with findings**

All 40 FRs trace to concrete implementation and named tests; every catalogued message, edit rule, file/DB semantic, and AID outcome spot-checked matches the COBOL source; all documented deviations shipped as written; the scoped suite is green (84 tests, 0 failures). Findings are LOW/INFO edge cases only — no CRITICAL/HIGH and no wholly-unimplemented FR.

## 1. Traceability matrix

| FR | Requirement | Implementation | Named test(s) | Status |
|---|---|---|---|---|
| FR-S12-01 | First entry: 10 rows from low-values, page 1, look-ahead NEXT-PAGE | `AdminUserService.firstDisplay`/`forward` (AdminUserService.java:130-131, 192-225) | `firstEntryBrowsesFromLowValuesAndSetsNextPage_frS1201`, `firstEntryRendersMapAndFirstPage_frS1201`, `browseCyclesBothWays_frS1201_...` | PASS |
| FR-S12-02 | ENTER + USRIDIN restarts browse at first key ≥ input, page 1 | `enter` → `forward(key)` (AdminUserService.java:155-161) | `enterWithSearchKeyRestartsBrowse_frS1202`, `enterWithSearchKeyRestartsAtThatKey_frS1202`, `enterRepositionsOnSearchKey_frS1202` | PASS |
| FR-S12-03 | `U`/`u` row → update screen with user id, FROM-PROGRAM=list | `enter` select outcome (AdminUserService.java:137-154); `submitUserList` redirect (UserAdminUiController.java:82-91) | `enterWithUpdateSelectionHandsOffToCousr02c_frS1203`, `enterWithUpdateSelectionOpensUpdateWithUserIdAndFrom_frS1203` | PASS |
| FR-S12-04 | `D`/`d` row → delete screen, same mechanics | same (UserAdminUiController.java:86-90) | `enterWithDeleteSelectionHandsOffToCousr03c_frS1204`, `enterWithDeleteSelectionOpensDeleteWithUserIdAndFrom_frS1204` | PASS |
| FR-S12-05 | Other SEL char → `Invalid selection. Valid values are U and D`, refresh from search key | `enter` pending message + forward browse (AdminUserService.java:151, 216-217) | `enterWithInvalidSelectionRefreshesAndReports_frS1205` (unit + UI) | PASS |
| FR-S12-06 | Only first non-blank SEL honoured | `enter` loop + `break` at first non-blank (AdminUserService.java:139-154) | `enterHonoursFirstNonBlankSelectionOnly_frS1206`, `enterHonoursOnlyTheFirstNonBlankSelection_frS1206` | PASS |
| FR-S12-07 | PF8 next page after USRID-LAST, page+1, re-derive flag | `pf8`/`forward` skip+peek (AdminUserService.java:176-182, 208-224) | `pf8SkipsPositionedRowAndAdvancesPage_frS1207`, `pf8AndPf7PageBothDirections_frS1207_frS1210` | PASS |
| FR-S12-08 | PF8 with NEXT-PAGE-NO → `already at the bottom` | `pf8` gate (AdminUserService.java:178-180) | `pf8WithoutNextPageReportsBottom_frS1208` | PASS |
| FR-S12-09 | Forward ENDFILE → `reached the bottom`, NEXT-PAGE-NO, page+1 only if ≥1 row read | `forward` (AdminUserService.java:216-221) | `firstEntryOnShortFileReachesBottom_frS1209`, `forwardShortPageReportsBottomAndCounts_frS1209`, `browseCyclesBothWays_..._frS1209_...` | PASS |
| FR-S12-10 | PF7 page>1 → previous 10, page−1, NEXT-PAGE-YES | `pf7`/`backward` (AdminUserService.java:165-173, 230-270) | `pf7ReturnsToPreviousPage_frS1210`, `pf8AndPf7PageBothDirections_frS1207_frS1210`, `browseCyclesBothWays_...` | PASS |
| FR-S12-11 | PF7 page≤1 → `already at the top`, page unchanged | `pf7` gate (AdminUserService.java:168-170) | `pf7OnFirstPageReportsTop_frS1211` | PASS |
| FR-S12-12 | Backward ENDFILE → `reached the top`; page 1 when look-behind hits SOF | `backward` (AdminUserService.java:253-262, 267-269) | `pf7ShortFillReportsTopAndKeepsPage_frS1212`, `browseCyclesBothWays_..._frS1212` | PASS |
| FR-S12-13 | STARTBR NOTFND → `at the top of the page`, no rows, page 0 | `forward` empty window (AdminUserService.java:200-207) | `enterWithKeyBeyondFileShowsTop_frS1213`, `enterBeyondEveryKeyShowsTop_frS1213` | PASS |
| FR-S12-14 | Other STARTBR/READNEXT/READPREV error → `Unable to lookup User...` | `forward`/`backward` DataAccessException catch (AdminUserService.java:197-199, 244-246) | `browseStoreErrorShowsLookupFailed_frS1214` | PASS |
| FR-S12-15 | PF3 → admin menu | `submitUserList` PF3 (UserAdminUiController.java:70-72) | `pf3ReturnsToAdminMenu_frS1215` | PASS |
| FR-S12-16 | Other AID → `Invalid key pressed. Please see below...` | `invalidAid` + default case (AdminUserService.java:185-187; UserAdminUiController.java:96) | `invalidAidShowsInvalidKey_frS1216`, `unmappedAidShowsInvalidKey_frS1216` | PASS |
| FR-S12-17 | Add validation order FNAME→LNAME→USERID→PASSWD→USRTYPE, cursor on field | `addEnter` (AdminUserService.java:304-324) | `addEnterChecksFieldsInCobolOrder_frS1217`, `entryRendersAddMap_frS1217`, `enterChecksFieldsInCobolOrder_frS1217` | PASS |
| FR-S12-18 | New key → write, clear, green `User <id> has been added ...` | `addWrite` (AdminUserService.java:326-341); `CobolMessages.userAdded` (CobolMessages.java:441-443) | `addEnterWritesClearsAndConfirms_frS1218`, `enterWritesClearsAndConfirms_frS1218`, `addPersistsAndDuplicateKeepsFields_frS1218_frS1219` | PASS |
| FR-S12-19 | DUPKEY/DUPREC → `User ID already exist...`, fields retained | `addWrite` existsById (AdminUserService.java:329-331, 343-345) | `addEnterDuplicateKeepsFieldsAndFails_frS1219`, `enterDuplicateKeepsFieldsAndReports_frS1219`, `addPersistsAndDuplicateKeepsFields_...` | PASS |
| FR-S12-20 | Other WRITE error → `Unable to Add User...` | `addWrite` catch (AdminUserService.java:346-349) | `addEnterWriteErrorFailsWithUnable_frS1220` | PASS |
| FR-S12-21 | Add PF3 → admin menu | `submitUserAdd` PF3 (UserAdminUiController.java:126-128) | `pf3ReturnsToAdminMenu_frS1221` | PASS |
| FR-S12-22 | Add PF4 → fields+message cleared, cursor FNAME | `addClear` (AdminUserService.java:352-355; UserAdminUiController.java:129-131) | `pf4ClearsFieldsAndMessage_frS1222` | PASS |
| FR-S12-23 | Add other AID (incl. PF12 despite footer) → invalid key | `addInvalidAid` (AdminUserService.java:357-360; UserAdminUiController.java:134-137) | `addInvalidAidKeepsFieldsWithCursorOnFname_frS1223`, `pf12IsInvalidDespiteTheFooter_frS1223` | PASS |
| FR-S12-24 | Update entry with selected id → immediate fetch | `updateEntry` (AdminUserService.java:388-393; UserAdminUiController.java:153-159) | `updateEntryPrefetchesSelectedId_frS1224`, `entryWithUserIdPrefetches_frS1224_frS1226` | PASS |
| FR-S12-25 | Update ENTER blank USRIDIN → `User ID can NOT be empty...` | `updateFetch` (AdminUserService.java:399-402) | `updateFetchRejectsBlankId_frS1225`, `enterWithBlankIdReports_frS1225` | PASS |
| FR-S12-26 | Update ENTER found → fields + stored password echoed to dark field + `Press PF5 key to save your updates ...` | `updateFetch` (AdminUserService.java:403-417); `type=password` echo (user-update.html:35-37) | `updateFetchEchoesPasswordAndPrompts_frS1226`, `entryWithUserIdPrefetches_..._frS1226`, `updateFetchAndSavePersist_frS1226_...` | PASS |
| FR-S12-27 | Update ENTER not found → `User ID NOT found...` | `updateFetch` (AdminUserService.java:410-413) | `updateFetchNotFoundReportsId_frS1227`, `enterWithUnknownIdReportsNotFound_frS1227` | PASS |
| FR-S12-28 | Update READ other error → `Unable to lookup User...` | `updateFetch` catch (AdminUserService.java:406-409) | `updateFetchStoreErrorShowsLookupFailed_frS1228` | PASS |
| FR-S12-29 | Save validation order USRIDIN→FNAME→LNAME→PASSWD→USRTYPE | `updateSave` (AdminUserService.java:423-441) | `updateSaveChecksFieldsInCobolOrder_frS1229`, `pf5ValidatesThenSavesAndStays_frS1229_...` | PASS |
| FR-S12-30 | Save no-change → `Please modify to update ...`, no write | `updateSave` byte-compare (AdminUserService.java:452-458) | `updateSaveUnchangedFieldsPromptsModify_frS1230`, `pf5WithoutChangesPromptsModify_frS1230` | PASS |
| FR-S12-31 | Save changed → rewrite, green `User <id> has been updated ...` | `updateSave` write (AdminUserService.java:459-465); `CobolMessages.userUpdated` (CobolMessages.java:445-447) | `updateSaveChangedFieldRewritesAndConfirms_frS1231`, `updateFetchAndSavePersist_..._frS1231_...` | PASS |
| FR-S12-32 | Save NOTFND / REWRITE other → `User ID NOT found...` / `Unable to Update User...` | `updateSave` (AdminUserService.java:449-451, 466-474) | `updateSaveNotFoundAndStoreErrors_frS1232`, `userTypeOutsideDomainFailsLikeOtherError_frS1232` | PASS |
| FR-S12-33 | Update PF3 → save then return to FROM-PROGRAM/COADM01C regardless | `submitUserUpdate` PF3 (UserAdminUiController.java:187-190) | `pf3SavesThenReturnsToCaller_frS1233` | PASS |
| FR-S12-34 | Update PF5 → save, stay | `submitUserUpdate` PF5 (UserAdminUiController.java:186) | `pf5ValidatesThenSavesAndStays_..._frS1234` | PASS |
| FR-S12-35 | Update PF12 → COADM01C without saving | `submitUserUpdate` PF12 (UserAdminUiController.java:174-176) | `pf12CancelsToMenuWithoutSaving_frS1235` | PASS |
| FR-S12-36 | Update PF4 clears / other AID invalid | `updateClear`/`updateInvalidAid` (AdminUserService.java:478-485; UserAdminUiController.java:177-180, 191) | `pf4ClearsAndOtherAidIsInvalid_frS1236` | PASS |
| FR-S12-37 | Delete entry with selected id → immediate fetch | `deleteEntry` (AdminUserService.java:511-516; UserAdminUiController.java:207-213) | `deleteEntryPrefetchesSelectedId_frS1237`, `entryWithUserIdPrefetches_frS1237_frS1238` | PASS |
| FR-S12-38 | Delete ENTER: blank → required msg; found → fields + `Press PF5 key to delete this user ...`; not found → NOT found; other → lookup fail | `deleteFetch` (AdminUserService.java:521-539) | `deleteFetchOutcomes_frS1238`, `enterWithBlankAndUnknownIdsReport_frS1238`, `deleteRoundTripPersists_frS1238_frS1239` | PASS |
| FR-S12-39 | Delete PF5: blank → required; deleted → clear + green `User <id> has been deleted ...`; not found; other → verbatim `Unable to Update User...` | `deleteDelete` (AdminUserService.java:545-568); `CobolMessages.userDeleted` (CobolMessages.java:449-451) | `deleteDeleteRemovesClearsAndConfirms_frS1239`, `deleteDeleteNotFoundAndQuirkError_frS1239`, `pf5DeletesClearsAndConfirms_frS1239`, `pf5OnMissingRowReportsNotFound_frS1239`, `deleteRoundTripPersists_...` | PASS |
| FR-S12-40 | Delete PF3 → caller; PF12 → COADM01C; PF4 clear; other → invalid key | `submitUserDelete` (UserAdminUiController.java:227-242); `fromRoute` (257-259) | `pf3ReturnsToCallerWithoutDeleting_frS1240`, `pf12Pf4AndOtherAids_frS1240` | PASS |

Gate/preconditions: session guard + `ROLE_ADMIN` on `/admin/**`, `/api/admin/**` (`SecurityConfig.java:47`; `SecurityUserDetailsService.java:27` maps `user_type 'A'`→`ROLE_ADMIN`; `unsignedBouncesToSignonAndRegularUserIsForbidden_frS12b5` + per-screen `regularUserIsForbidden_frS12b5` tests, `adminEndpointsRequireAuthAndAdminRole`). `UI_ROUTES` flipped for all four programs (`MenuService.java:114-117`). USRSEC stays seed-only; `UsersSeedParityIntegrationTest` guards the fixture.

## 2. COBOL parity spot-checks

### Message catalogue (verbatim literals)

| Message | COBOL | Java | Match |
|---|---|---|---|
| `Invalid selection. Valid values are U and D` | COUSR00C.cbl:211-213 | CobolMessages.java:272-273 | yes |
| `You are already at the top of the page...` | COUSR00C.cbl:251 | CobolMessages.java:274-275 | yes |
| `You are already at the bottom of the page...` | COUSR00C.cbl:273 | CobolMessages.java:276-277 | yes |
| `You are at the top of the page...` (STARTBR NOTFND) | COUSR00C.cbl:603 | CobolMessages.java:278 | yes |
| `You have reached the bottom of the page...` (READNEXT ENDFILE) | COUSR00C.cbl:637 | CobolMessages.java:279-280 | yes |
| `You have reached the top of the page...` (READPREV ENDFILE) | COUSR00C.cbl:671 | CobolMessages.java:281-282 | yes |
| `Unable to lookup User...` | COUSR00C.cbl:610/644/678, COUSR02C.cbl:349, COUSR03C.cbl:296 | CobolMessages.java:283 | yes |
| `First/Last Name can NOT be empty...` | COUSR01C.cbl:120/126, COUSR02C.cbl:188/194 | CobolMessages.java:13-14 | yes |
| `User ID can NOT be empty...` | COUSR01C.cbl:132, COUSR02C.cbl:182, COUSR03C.cbl:147/179 | CobolMessages.java:15 | yes |
| `Password can NOT be empty...` | COUSR01C.cbl:138, COUSR02C.cbl:200 | CobolMessages.java:16 | yes |
| `User Type can NOT be empty...` | COUSR01C.cbl:144, COUSR02C.cbl:206 | CobolMessages.java:17 | yes |
| `User <id> has been added/updated/deleted ...` (DELIMITED BY SPACE) | COUSR01C.cbl:255-258, COUSR02C.cbl:372-375, COUSR03C.cbl:318-321 | CobolMessages.java:441-451 + `delimitedBySpace` :453 | yes |
| `User ID already exist...` | COUSR01C.cbl:263 | CobolMessages.java:11 | yes |
| `Unable to Add User...` | COUSR01C.cbl:270 | CobolMessages.java:23 | yes |
| `Press PF5 key to save your updates ...` | COUSR02C.cbl:336 | CobolMessages.java:284-285 | yes |
| `User ID NOT found...` | COUSR02C.cbl:342/379, COUSR03C.cbl:289/325 | CobolMessages.java:18 | yes |
| `Please modify to update ...` | COUSR02C.cbl:239 | CobolMessages.java:286 | yes |
| `Unable to Update User...` (incl. COUSR03C DELETE quirk) | COUSR02C.cbl:386, COUSR03C.cbl:332 | CobolMessages.java:24 (used at AdminUserService.java:566 for delete errors — quirk preserved) | yes |
| `Press PF5 key to delete this user ...` | COUSR03C.cbl:283 | CobolMessages.java:25-26 | yes |
| `Invalid key pressed. Please see below...` | CSMSG01Y.cpy:20-21 | CobolMessages.java:10 | yes |

### Edit rules, screen text, derivations

- Validation order add: FNAME→LNAME→USERID→PASSWD→USRTYPE (COUSR01C.cbl:117-151) = `addEnter` (AdminUserService.java:308-322). Update save order: USRIDIN→FNAME→LNAME→PASSWD→USRTYPE (COUSR02C.cbl:179-213) = `updateSave` (:427-440). First failure wins, cursor moved to failing field via `-1`→field-length idiom — mapped to `cursorField` autofocus (see finding F-1 for a naming slip).
- Footers verbatim: `ENTER=Continue  F3=Back  F7=Backward  F8=Forward` (COUSR00.bms:457) = user-list.html:68; `ENTER=Add User  F3=Back  F4=Clear  F12=Exit` (COUSR01.bms:159) = user-add.html:44; `ENTER=Fetch  F3=Save&&Exit  F4=Clear  F5=Save  F12=Cancel` (COUSR02.bms:163-164) = user-update.html:45 (`Save&amp;Exit`); `ENTER=Fetch  F3=Back  F4=Clear  F5=Delete` (COUSR03.bms:148) = user-delete.html:36. Instruction `Type 'U' to Update or 'D' to Delete a User from the list` (COUSR00.bms:447-448) = user-list.html:60. Labels `Search User ID:`/`Enter User ID:`/titles/asterisk separator/`(A=Admin, U=User)` all match (COUSR00.bms:94; COUSR02.bms:79/84/96/154; COUSR03.bms:79/84/96/139; COUSR01.bms:79/150).
- Field widths: `field(v,8)/field(v,20)/field(v,1)` truncation (AdminUserService.java:688-693) = BMS X(8)/X(20)/X(1) + `maxlength` on the inputs. No upper-casing anywhere — store-as-typed parity (CSUSR01Y.cpy; `fill`/setters trim only, AdminUserService.java:333-337, 459-462).
- Page mechanics: page 0 pre-browse, +1 per forward page only when ≥1 row read (COUSR00C.cbl:308-323 = AdminUserService.java:218); PF7 forces NEXT-PAGE-YES before the top gate (COUSR00C.cbl:245 = AdminUserService.java:166-167); backward full-fill+peek decrement vs clamp-to-1 (COUSR00C.cbl:362-372 = AdminUserService.java:253-262); bottom-up fill WS-IDX 10→1 (COUSR00C.cbl:352-360 = :250-252); USRID-FIRST/LAST = row1/row10 (COUSR00C.cbl:388-389, 434-435 = :219-221, 263-265); PAGENUM rendered as 8 digits (`formatPage` :711-713).
- File/DB semantics: STARTBR GTEQ + positioned-record skip-read on non-ENTER aids (COUSR00C.cbl:288-290) = `forward` `skip` slice (AdminUserService.java:195-196, 208) — including the quirk of consuming a valid record when the anchor was deleted. Look-ahead/look-behind via `PageRequest` size 11 (:195, 243). WRITE/DUPKEY→existsById+insert (:329-338); READ UPDATE→findById (:405, 445, 528, 552); REWRITE→saveAndFlush (:464); DELETE→repository.delete (:562). USRSEC is seed-only (S01-B5 closure): `DataSeeder` imports `app/data/ASCII/usrsec.txt` (`users=10` observed in seed log); `UsersSeedParityIntegrationTest` green.
- Exit/AID behaviour: PF3/PF12→COADM01C = `redirect:/admin/menu`; list PF3/…→caller = `from=list`→`/admin/users`; update PF3 saves then exits regardless of outcome (UserAdminUiController.java:187-190 = COUSR02C.cbl:111-119); delete PF3 exits without deleting (:230-232 = COUSR03C.cbl:111-118); PF12 on add is invalid despite the footer (UserAdminUiController.java:136 + `pf12IsInvalidDespiteTheFooter_frS1223` = COUSR01C.cbl:98-102); all other AIDs → invalid-key redisplay.
- Fetch-then-act update/delete: `userId` param prefetch (`updateEntry`/`deleteEntry`) = CDEMO-CU0n-USR-SELECTED ENTER path (COUSR02C.cbl:99-104, COUSR03C.cbl:99-104).
- No abend codes in this stream; the only "exit" semantics are the XCTL returns above.

## 3. Stub/placeholder sweep

Searched `AdminUserService.java`, `AdminUser*.java`, `UserAdminUiController.java`, `SecurityUserRepository.java`, `user-*.html`, and all seven stream test classes + `UsersSeedParityIntegrationTest` for `TODO|FIXME|not implemented|UnsupportedOperation|@Disabled|@Ignore|stub|placeholder`: **zero hits**. No commented-out assertions; tests assert persisted state (e.g. `pf3SavesThenReturnsToCaller_frS1233` reads the row back via `userRepository.findById`, UserUpdateUiIntegrationTest.java:161-164), rendered content, model attributes, and redirect targets — no trivially-true checks found on inspection.

## 4. Documented deviations vs shipped code

| ID | Decision | Shipped | Compliant |
|---|---|---|---|
| S12-B1 | Keep `user_type` CHECK 'A'/'U'; violation → OTHER-path message, no new validation message | `SecurityUser` `@Check` (SecurityUser.java:14); `DataIntegrityViolationException`→`USER_ADD_FAILED`/`USER_UPDATE_FAILED` (AdminUserService.java:341-349, 466-468); tested by `userTypeOutsideDomainFailsLikeOtherError_frS1232`. `USER_TYPE_INVALID` constant still exists in CobolMessages.java:12 but is no longer referenced by this stream | yes |
| S12-B2 | Plaintext storage; fetch echoes stored password into `type=password`; byte-compare "modified" | `UsrsecPlaintextPasswordEncoder` bean (SecurityConfig.java:26-28); `updateFetch` returns stored password (AdminUserService.java:414-416) echoed into `type=password` (user-update.html:35-37); byte-compare `updateSave` (:452-455 = COUSR02C.cbl:219-234) | yes |
| S12-B3 | Render exactly the rows returned; no stale-row overlay | `forward` empty/short renders returned rows (AdminUserService.java:200-224); `backward` STARTBR-NOTFND keeps the submitted rows (:238-241) — matches the COBOL artifact where the source keeps stale rows; documented direction followed | yes |
| S12-B4 | `from`/`userId` route params carry caller and selection | list→`?userId=..&from=list` (UserAdminUiController.java:88-90); `fromRoute` (:257-259); prefetch entries (:153-159, :207-213) | yes |
| S12-B5 | `/admin/**`,`/api/admin/**` → `ROLE_ADMIN` | SecurityConfig.java:47; role map SecurityUserDetailsService.java:27; `frS12b5` tests green | yes |
| S01-B5 | Postgres `users` single writer; USRSEC seed-only | all CRUD via `SecurityUserRepository`; DataSeeder imports usrsec.txt; seed-parity test green | yes |
| PR #124 claims | verbatim paging messages; PF12-invalid-on-add; `Unable to Update User...` delete quirk; REST keeps offset paging + verbatim errors; baseline test renamed for store-as-typed | all confirmed in code and tests above (`list()` offset paging AdminUserService.java:585-596; `adminCreatedUsersAreStoredAsTyped` present in the suite) | yes |

## 5. Test evidence

Command (from `spring-boot/`, `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`):
`mvn -q test -Dtest='UserAdminServiceTest,UserAdminIntegrationTest,UserAdminApiIntegrationTest,UserListUiIntegrationTest,UserAddUiIntegrationTest,UserUpdateUiIntegrationTest,UserDeleteUiIntegrationTest,UsersSeedParityIntegrationTest'`

Surefire results (target/surefire-reports/*.txt): **Tests run: 84, Failures: 0, Errors: 0, Skipped: 0.**

| Class | run | fail |
|---|---|---|
| com.carddemo.service.UserAdminServiceTest | 34 | 0 |
| com.carddemo.UserListUiIntegrationTest | 10 | 0 |
| com.carddemo.UserUpdateUiIntegrationTest | 10 | 0 |
| com.carddemo.UserAddUiIntegrationTest | 8 | 0 |
| com.carddemo.UserDeleteUiIntegrationTest | 8 | 0 |
| com.carddemo.UserAdminIntegrationTest | 7 | 0 |
| com.carddemo.UserAdminApiIntegrationTest | 6 | 0 |
| com.carddemo.UsersSeedParityIntegrationTest | 1 | 0 |

(84 vs the PR's "83 named _frS12xx" — the extra is the seed-parity guard.) Full-suite run intentionally not repeated; a separate audit run covers it. Tests run on the H2 profile; `@Check` emits the `user_type` constraint there so the S12-B1 violation path is exercised.

## 6. Findings

| # | Severity | Title | Evidence | Recommendation |
|---|---|---|---|---|
| F-1 | LOW | Cursor autofocus never fires on three add/update fields: service returns `cursorField` values `"userId"`/`"usrtype"` but templates test `'userid'`/`'usertype'` | AdminUserService.java:315,321,330,344 (`"userId"`/`"usrtype"`) vs user-add.html:29,41 (`'userid'`/`'usertype'`); AdminUserService.java:440 (`"usrtype"`) vs user-update.html:42 (`'usertype'`). COBOL equivalent: `MOVE -1 TO <field>L` (COUSR01C.cbl:134,146; COUSR02C.cbl:208) | Rename the service constants to the template spellings (or vice versa) so `-1`-cursor parity actually lands |
| F-2 | LOW | Delete error-path divergence on store failure/missing-row race: Java skips the DELETE after a failed read and shows `Unable to lookup User...`; COBOL performs DELETE-USER-SEC-FILE unconditionally, whose failure surfaces `Unable to Update User...` (or `User ID NOT found...`). A row deleted between read and delete returns the green success message vs COBOL's NOTFND | AdminUserService.java:552-560, 561-567 vs COUSR03C.cbl:188-191 (`PERFORM READ … PERFORM DELETE` unconditional), :323-335 | Acceptable edge case; if exact error-path parity is wanted, attempt the delete after a failed read and map its outcome, and treat a no-op delete as NOTFND |
| F-3 | LOW | PF8 with blank `lastId` + `nextPage=Y`: COBOL uses HIGH-VALUES → STARTBR NOTFND → `You are at the top of the page...`; Java treats `""` as low-values and re-renders page 1. Only reachable via tampered client-held hidden fields (COMMAREA was server-side in the source) | AdminUserService.java:176-182 + 195-196 vs COUSR00C.cbl:262-266, 600-606 | Optional: treat blank `lastId` on PF8 as the NOTFND path, or bound-check client paging state |
| F-4 | INFO | Update fetch success renders no autofocus target (`cursorField=null`); COBOL leaves the cursor on USRIDIN | AdminUserService.java:414-416 vs COUSR02C.cbl:153 (`MOVE -1 TO USRIDINL` before READ) | Cosmetic; set cursorField `usridin` on the fetch-success screen if desired |
| F-5 | INFO | On a store error during update-save, `existsById` inside the catch can itself throw, surfacing a 500 instead of the OTHER-path message | AdminUserService.java:469-474 | Optional: guard the re-check |

No GAP or PARTIAL trace rows. All findings are edge-case or cosmetic; none blocks sign-off.
