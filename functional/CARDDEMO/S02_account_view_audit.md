# S-02 Account View — Independent Audit (Java / Spring Boot)

Audited branch: `devin/1789516557-carddemo-java-engagement`, checked out on audit
branch `devin/audit-s02-java`, HEAD `410e5d0f826ccd3a6a2f4ea04eee673f64c240d8`.
Auditor: independent auditor (did not perform the migration). Date: 2026-09-16.
Scope: traceability of FR-S02-01..15, verbatim COBOL parity (messages, edits,
file/DB semantics, exit/abend paths), stub/placeholder sweep, documented
deviations vs shipped code (plan boundary table + wave PR #119 "S-02 Account
View: screen + parity", merged), and a scoped test run of the stream's classes.
Sources of truth: `app/cbl/COACTVWC.cbl`, `app/bms/COACTVW.bms`,
`app/csd/CARDDEMO.CSD`, `functional/CARDDEMO/S02_functional_requirement.md`,
`functional/CARDDEMO/S02_account_view_analysis.md`,
`functional/CARDDEMO/S02_account_view_migration_plan.md`.
Abbreviations below: `svc` = `spring-boot/src/main/java/com/carddemo/service/AccountViewService.java`,
`ui` = `spring-boot/src/main/java/com/carddemo/ui/UiController.java`,
`msg` = `spring-boot/src/main/java/com/carddemo/api/CobolMessages.java`,
`fmt` = `spring-boot/src/main/java/com/carddemo/service/CobolFormat.java`,
`tpl` = `spring-boot/src/main/resources/templates/account-view.html`,
`svcTest` = `AccountViewServiceTest`, `uiTest` = `AccountViewUiIntegrationTest`,
`errTest` = `AccountViewUiStoreErrorIntegrationTest`,
`apiTest` = `ApiIntegrationTest`.

## Verdict: **PASS with findings**

All 15 stream FRs trace to a concrete implementation and at least one named
test; no GAP or PARTIAL rows. Every catalogued message literal (including the
doubled space in `Account Filter must  be...` and the 75-char not-found/file-error
templates), the input edits, the keyed-read order (CXACAIX → ACCTDAT → CUSTDAT),
the '*' + red echo semantics, the PICOUT signed-amount editor, and all customer-block
derivations match `app/cbl/COACTVWC.cbl` / `app/bms/COACTVW.bms`. The stub sweep
is clean, and every documented deviation (B-009, B-012, S01-B6, S02-B1, S02-B2
plus the PR-documented cbl:704 dead-flag quirk) is present in shipped code. No
undocumented deviation changes observable behavior.

Scoped run: **43 tests, 0 failures, 0 errors, 0 skipped** across the four S02
classes. Findings are LOW/INFO only; none blocks sign-off.

## 1. Traceability matrix

| FR | Requirement | Implementation | Test name(s) | Status |
|---|---|---|---|---|
| FR-S02-01 | Initial screen: prompt only, no data, no error | `svc.initialScreen()` `:50-53`; GET `/accounts/view` `ui.java:346-351` → `tpl` render | `svcTest.initialScreenIsPromptOnly_frS0201`, `uiTest.initialRenderShowsPromptAndNoData_frS0201` | PASS |
| FR-S02-02 | Blank / `*` input → "No input received", field shown as `*` in red | `svc.resolve()` `:126-130` (BLANK), `viewScreen` `:58-59` (echo `"*"`, red) | `svcTest.blankAndStarInputsAreNoInputReceived_frS0202`, `uiTest.blankSubmitShowsStarRedAndNoInput_frS0202` | PASS |
| FR-S02-03 | Non-11-digit/non-zero filter → verbatim filter msg + red echo | `svc.resolve()` `:131-134` (`\d{11}` + `00000000000`), `viewScreen` `:60-61`; REST 400 `svc.view` `:82-83` | `svcTest.nonElevenDigitInputsAreFilterErrors_frS0203`, `uiTest.invalidFilterEchoesRedWithFilterMessage_frS0203`, `apiTest.menuSelectionAndAccountViewUseCobolTargetsAndValues` (`/api/accounts/1` → 400) | PASS |
| FR-S02-04 | Xref (CXACAIX) miss → verbatim 75-char not-found msg | `svc.resolve()` `:144`, `msg.xrefNotFound` `:328-330`; REST 404 `svc.view` `:84-85` | `svcTest.xrefNotFoundRendersVerbatimMessage_frS0204`, `uiTest.xrefNotFoundRendersVerbatimMessage_frS0204` | PASS |
| FR-S02-05 | ACCTDAT miss → verbatim 75-char not-found msg | `svc.resolve()` `:154`, `msg.accountNotFound` `:332-334`; REST 404 `svc.view` `:86-87` | `svcTest.acctNotFoundRendersVerbatimMessage_frS0205`, `uiTest.acctNotFoundRendersVerbatimMessage_frS0205` | PASS |
| FR-S02-06 | CUSTDAT miss → cust-not-found msg, account block still shown | `svc.resolve()` `:160-164`, `viewScreen` `:68-69` (account block passed), `msg.customerNotFound` `:336-338`; REST 404 `svc.view` `:88-89` | `svcTest.custNotFoundKeepsAccountBlockWithoutRed_frS0206`, `uiTest.custNotFoundStillShowsAccountBlock_frS0206` | PASS |
| FR-S02-07 | Full hit → both blocks, all catalogued fields, info line = prompt | `svc.toAccountBlock` `:171-189`, `toCustomerBlock` `:189-215`, `viewScreen` `:71-72`; `tpl` field wiring; info line constant `tpl:166-167` | `svcTest.fullHitRendersBothBlocksAndRestView_frS0207`, `uiTest.seededAccountRendersEveryField_frS0207` | PASS |
| FR-S02-08 | Amounts edited per PICOUT `+ZZZ,ZZZ,ZZZ.99` | `fmt.editSignedAmount` `:18-44` (sign, zero-suppress, blank commas, 15 chars) | `svcTest.signedAmountEditorMatchesCobolPicout_frS0208` (9-value table incl. 0, negatives, max) | PASS |
| FR-S02-09 | Customer derivations: SSN `nnn-nn-nnnn`, FICO 3 digits, cust id 9 digits, zip first 5, phones first 13, dates `yyyy-mm-dd` | `svc.toCustomerBlock` `:189-215` (`fmt.ssn` `:69-75`, `%09d`, `%03d`, `truncate`, `date`) | `svcTest.customerBlockDerivations_frS0209` | PASS |
| FR-S02-10 | Multiple xref rows → customer of lowest card number (AIX order) | `CardXrefRepository.java:9` `ORDER BY x.xrefCardNumber`; `svc.resolve()` first row `:144-147` | `uiTest.multipleXrefsUseLowestCardNumberCustomer_frS0210` | PASS |
| FR-S02-11 | PF3/Exit → caller (menu when caller blank or menu) | `ui` POST `/accounts/view` `:363-364` (`redirect:` + `internalRoute`), `internalRoute` `:426-431` (internal `/` else `/menu`) | `uiTest.pf3ReturnsToCallerOrMenu_frS0211` | PASS |
| FR-S02-12 | Other AID keys behave as ENTER; no invalid-key text | `ui` POST `:357-368` — only `PF3` branches, everything else submits; no `INVALID_KEY_PRESSED` path on this screen | `uiTest.otherAidSubmitsAsEnterWithNoInvalidKeyText_frS0212` (F7) | PASS |
| FR-S02-13 | Store failure on any read → verbatim file-error layout (RESP/RESP2 per S02-B2) | `svc.resolve()` storeError `:139-141/:149-151/:160-162`, `viewScreen` `:66-67` (cust failure keeps account block), `msg.fileError` `:344-349` | `svcTest.storeFailuresRenderFileErrorPerFile_frS0213`, `errTest.xrefStoreErrorRendersFileErrorLayout_frS0213`, `errTest.acctStoreErrorRendersFileErrorLayout_frS0213`, `errTest.custStoreErrorKeepsAccountBlock_frS0213` | PASS |
| FR-S02-14 | Account filter input max 11 chars | `tpl:18` `maxlength="11"`; service truncates to 11 `svc.resolve()` `:126-127` (BMS `LENGTH=11` receive parity, `bms:84-90`) | `uiTest.accountInputLengthIsEleven_frS0214` | PASS |
| FR-S02-15 | Any user type may use it; unsigned requests rejected | `SecurityConfig.java:48` `.anyRequest().authenticated()`; unsigned UI → redirect `/signon` `:65`, API → 401 `:62-63` | `uiTest.unsignedRequestsAreRejected_frS0215` | PASS |

Seam notes: menu option 1 routes to this screen — `MenuService.java:38` marks
COACTVWC implemented and `UI_ROUTES` `COACTVWC→/accounts/view` `:124`;
`apiTest.menuSelectionAndAccountViewUseCobolTargetsAndValues` (`:86-114`) proves
menu option 1 → COACTVWC and the REST happy path end to end.

## 2. COBOL parity spot-checks

All comparisons verified byte-for-byte against `app/cbl/COACTVWC.cbl` (941 lines,
read in full) and `app/bms/COACTVW.bms`.

### Message catalogue (every entry)

| Item | COBOL | Java | Result |
|---|---|---|---|
| MSG-NO-INPUT `No input received` | `cbl:123-124` (WS-RETURN-MSG STRING) | `msg:49` | verbatim |
| INFO-PROMPT `Enter or update id of account to display` | `cbl:113-114` | `msg:50-51`; always rendered `tpl:166-167` (COBOL re-sets it every send `cbl:528-530`, `:534`) | verbatim |
| MSG-ACCT-FILTER `Account Filter must  be a non-zero 11 digit number` (two spaces after "must") | `cbl:672` | `msg:45-46` | verbatim incl. doubled space |
| MSG-XREF-NOTFND `Account <id> NOT found in Cross reference file.` | STRING `cbl:747-757` | `msg.xrefNotFound` `:328-330` | verbatim; `<id>` = 11-char echo both sides |
| MSG-ACCT-NOTFND `Account <id> NOT found in Account Master file.` | STRING `cbl:796-806` | `msg.accountNotFound` `:332-334` | verbatim |
| MSG-CUST-NOTFND `Customer <id> NOT found in Customer Master file.` | STRING `cbl:846-856` | `msg.customerNotFound` `:336-338` | verbatim; `<id>` = 9-digit zero-padded cust id both sides |
| MSG-FILE-ERROR `File Error: <file9>Resp: <resp>Resp2:<resp2><pgm8>` (75 chars) | layout `cbl:86-105` | `msg.fileError` `:344-349` (file padded to 9, pgm `COACTVWC` to 8, total 75) | verbatim shape; RESP/RESP2 fixed per S02-B2 (see §4) |

### Edits / validation order

| Rule | COBOL | Java | Result |
|---|---|---|---|
| Blank/`SPACES`/`LOW-VALUES` input → no-input path, echo `*` | `cbl:622-631`, `*` moved `cbl:561-565` | `svc.resolve()` `:126-130`; `viewScreen` `:58` | parity — `isBlank()` plus `*`+blanks; `*abc` correctly falls to the filter path both sides |
| Field must be 11 digits and non-zero | `cbl:666-680` (CC-ACCT-ID numeric test + `= ZERO`) | `svc.resolve()` `:131-134` (`\d{11}` + literal zeros) | parity |
| Field length 11 (longer input impossible/truncated) | BMS `ACCTSID LENGTH=11` `bms:84-90` | `tpl:18` `maxlength="11"` + `svc.resolve()` `:126-127` truncate | parity |
| Error → red field on redisplay | `cbl:557-559` (RED attr on NOT-OK) | `viewScreen` echo flag `:58-66` → `tpl:21` `classappend="field-red"` → `.account-view input.field-red` `#f00` | parity |
| Echo of entered value on error | ACCTSIDI→ACCTSIDO `cbl` screen setup | `echo` `svc.resolve()` `:131` | parity (see INFO-4 §6) |

### File/DB access semantics

| Semantic | COBOL | Java | Result |
|---|---|---|---|
| Ordered keyed reads CXACAIX → ACCTDAT → CUSTDAT, stop at first miss | `cbl:723-870` | `svc.resolve()` `:134-166` | parity |
| CXACAIX is the card-xref AIX keyed on account | `app/csd/CARDDEMO.CSD:63-64` | `CardXrefRepository.findByXrefAcctId` `:9` | parity |
| AIX multi-row order = alternate (card) key order | `cbl` FIRST-of-set read | `ORDER BY x.xrefCardNumber` `CardXrefRepository.java:9` | parity — X(16) card keys order identically lexicographically |
| NOTFND vs other RESP separation | `cbl` `WHEN DFHRESP(NOTFND)` / `OTHER` per read | `Optional` empty → not-found kinds; `RuntimeException` → storeError `svc.resolve()` `:139-162` | parity |
| CUSTDAT failure keeps account block | `FOUND-ACCT-IN-ACCTDAT` survives `cbl:471-472` | `STORE_ERROR` carries account `svc.resolve()` `:160-162`, rendered `:66-67` | parity |
| File names in error text | `LIT-CARDXREFNAME-ACCT-PATH 'CXACAIX '`, `'ACCTDAT '`, `'CUSTDAT '` `cbl` WS literals | `svc` `:34-36`; `msg.fileError` pads to 9 | verbatim |

### Derivations (sampled)

| Derivation | COBOL | Java | Result |
|---|---|---|---|
| SSN `nnn-nn-nnnn` | STRING `CUST-SSN(1:3)'-'(4:2)'-'(6:4)` `cbl:496-503` | `fmt.ssn` `:69-75` (`%09d` then 3-2-4) | parity |
| Customer block field-for-field (city ← `CUST-ADDR-LINE-3` `cbl:513`, zip `cbl:515`, phones `cbl:517-518`, FICO/DOB/EFT/primary-flag) | `cbl:493-523` | `svc.toCustomerBlock` `:189-215` | parity — incl. zip/phone truncation done by `truncate` where CICS truncates on map send |
| Account block (status/opened/reissue/group + amounts) | `cbl:471-491` | `svc.toAccountBlock` `:171-189` | parity |
| Credit-limit PICOUT `+ZZZ,ZZZ,ZZZ.99` | `bms:115-119`; editor semantics in `cbl` | `fmt.editSignedAmount` `:18-44` (incl. `+           .00` zero case) | parity |
| PF3 → XCTL to caller (menu default) | `cbl:324-352` | `ui:363-364` + `internalRoute` `:426-431` | parity per B-012 (USRTYP commarea reset demoted — see §4) |
| Non-ENTER/non-PF3 AID → ENTER | `cbl:306-314` | `ui:357-368` (only PF3 branches) | parity per S02-B1 |
| Screen furniture: title, tran/prog in header, `F3=Exit` footer, red ERRMSG | `bms` titles/footer `:369-373`, ERRMSG RED BRT `:362-368`, INFOMSG `:356-361` | `tpl` layout call `:3`, footer `:168,171`, `.message-line` `#f00` in `carddemo.css` | parity (Thymeleaf re-skin documented) |

### Exit/abend codes

COACTVWC has no ABEND path on this screen: exits are PF3 XCTL to caller
(`cbl:324-352`) and RETURN after each pseudo-conversational turn. Shipped code
matches: PF3 → redirect (`ui:363-364`), every other path re-renders the same
template (`ui:367-368`). REST surface adds 400/404/500 mappings
(`svc.view` `:77-91`, `GlobalExceptionHandler.java:19-22,41-44`) per the plan's
API contract.

## 3. Stub/placeholder sweep

Searched `spring-boot/src` for the stream's files (`AccountViewService`,
`AccountViewScreen`, `UiController` account-view mapping, `account-view.html`,
`CobolMessages` account-view literals, `CobolFormat`, `CardXrefRepository`) and
the four test classes for: `TODO`, `FIXME`, `XXX`, `not implemented`,
`UnsupportedOperationException`, `@Disabled`, `@Ignore`,
`assertTrue(true)`-style trivially-true assertions, and commented-out asserts.

**Found: none.** No disabled or skipped tests in the stream's classes (surefire
reports `Skipped: 0` on all four). All message bodies are exercised by verbatim
string assertions in named `_frS02xx` tests — none hard-coded in the happy path
only. The store-error path is covered by `@MockitoBean` failure tests per file.

## 4. Documented deviations vs shipped code

| Deviation | Documented in | Shipped as | Compliant |
|---|---|---|---|
| B-009: reuse shared JPA repositories; NOTFND ↔ empty `Optional`; any other RESP ↔ `RuntimeException` → store-error path | plan boundary table | `svc.resolve()` `:139-166` wraps each repo call in try/catch to storeError; empty Optional → NOTFND kinds | yes |
| B-012: PF3 → caller/`/menu` via internal-path check; `CDEMO-USRTYP-USER` reset demoted (S-01 owns commarea user type) | plan boundary table | `ui:363-364`, `internalRoute` `:426-431` (internal `/` path only, no `//`, else `/menu`); no USRTYP write here | yes |
| S01-B6: session + `SecurityContext`, `authenticated()` matcher, unsigned → `/signon` (UI) / 401 JSON (API) | plan boundary table | `SecurityConfig.java:44-48`, `:60-68` | yes |
| S02-B1: any non-ENTER/non-PF3 AID coerced to ENTER; no invalid-key message on this screen | plan boundary table | `ui:357-368` + comment `:353-356` citing `cbl:306-352`; `tpl` JS `:178-189` sends raw F-key aid values; test proves F7 submits with no invalid-key text | yes |
| S02-B2: file-error renders fixed RESP `000000017 `/RESP2 `000000120 ` | plan boundary table | `msg.fileError` `:344-349` (hard-coded codes, verbatim 75-char layout) | yes |
| cbl:704 dead-flag quirk: source's `DID-NOT-FIND-ACCT-IN-ACCTDAT` check uses an 88-level whose only SET is commented out (`cbl:792`), so the source falls through to the CUSTDAT read on a real ACCTDAT miss; port exits early per the FR contract | service Javadoc `svc:27-30` + PR #119 | early `ACCT_NOT_FOUND` exit `svc.resolve()` `:154` | yes (documented; see INFO-2 §6) |
| Truncate input to 11 before validation (BMS `LENGTH=11` receive parity) | PR #119 | `svc.resolve()` `:126-127` | yes |
| Multi-xref ordering via `ORDER BY xrefCardNumber` | PR #119 | `CardXrefRepository.java:9` | yes |
| Thymeleaf re-skin (3270 colours/attrs approximated, not pixel-faithful) | plan + PR #119 | `tpl` + `carddemo.css` (`.message-line` red, `.field-red` red, `.info-line`) | yes |

No documented deviation is missing, and no undocumented behavioral deviation
was found.

## 5. Test evidence

Command (scoped to this stream only, per audit brief — full suite covered
separately):

```
cd spring-boot && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  mvn -q test -Dtest='AccountViewServiceTest,AccountViewUiIntegrationTest,AccountViewUiStoreErrorIntegrationTest,ApiIntegrationTest'
```

Surefire results (from `spring-boot/target/surefire-reports/*.txt`):

| Class | Tests run | Failures | Errors | Skipped |
|---|---|---|---|---|
| `com.carddemo.service.AccountViewServiceTest` | 10 | 0 | 0 | 0 |
| `com.carddemo.AccountViewUiIntegrationTest` | 12 | 0 | 0 | 0 |
| `com.carddemo.AccountViewUiStoreErrorIntegrationTest` | 3 | 0 | 0 | 0 |
| `com.carddemo.ApiIntegrationTest` | 18 | 0 | 0 | 0 |
| **Total** | **43** | **0** | **0** | **0** |

`ApiIntegrationTest` is the shared API-seam suite; the S02-relevant method is
`menuSelectionAndAccountViewUseCobolTargetsAndValues` (`ApiIntegrationTest.java:86-114`).
Exit code 0.

## 6. Findings

| Severity | Finding | Evidence | Recommendation |
|---|---|---|---|
| LOW | FR doc COBOL cite coordinates drift from actual lines — several `cbl:` references in `S02_functional_requirement.md` land ~4–40 lines away from the literals they name (e.g. doc `cbl:98-99` for "No input received" vs actual `app/cbl/COACTVWC.cbl:123-124`; doc `cbl:107-108` prompt vs `:113-114`; doc `cbl:121-127` file-error layout vs `:86-105`; doc `cbl:746-756` vs `:747-757`). The catalogued literals themselves are verbatim-correct. | `functional/CARDDEMO/S02_functional_requirement.md` message catalogue vs `app/cbl/COACTVWC.cbl:86-105,113-114,123-124,747-757` | Correct the line cites in the FR doc; literals need no change. |
| INFO | Source quirk vs port: `app/cbl/COACTVWC.cbl:704` tests `DID-NOT-FIND-ACCT-IN-ACCTDAT`, an 88-level on `WS-RETURN-MSG` whose only `SET` is commented out (`cbl:792`) — so in the source, a real ACCTDAT NOTFND (or OTHER) falls through to the CUSTDAT read with blank account fields, while the port exits early at `svc.resolve()` `:154`. Behaviorally unreachable in normal data (xref row implies account), compliant with FR-S02-05's "no data" contract, and already documented in the service Javadoc (`svc:27-30`) and PR #119. | `app/cbl/COACTVWC.cbl:704,792` vs `svc.resolve()` `:154`, Javadoc `svc:27-30` | None — recorded here for completeness; keep the Javadoc note. |
| INFO | REST store-error surface returns the generic 500 body ("Unable to process request") rather than the verbatim MSG-FILE-ERROR text the UI renders. Consistent with B-009 (store failure → `RuntimeException`) and the plan's unchanged REST contract; the UI path is verbatim. | `svc.view` `:90` (`throw r.cause()`), `GlobalExceptionHandler.java:41-44` vs `msg.fileError` `:344-349` | None required; flag only if API consumers are expected to see the verbatim text. |
| INFO | Field echo strips trailing whitespace (`svc.resolve()` `:131` `replaceAll("\\s+$","")`) where the COBOL echoes `ACCTSIDI` verbatim. Difference is invisible on screen and unreachable on the not-found paths (those echoes are always exactly 11 digits). | `svc.resolve()` `:131` vs `cbl` ACCTSIDI→ACCTSIDO echo in screen send `cbl:460-571` | None required. |
