# S-10 Reports — Independent Audit

- **Stream**: S-10 Reports (CORPT00C online submit + TRANREPT/CBTRN03C batch chain)
- **Audited branch**: `devin/audit-s10-java` cut from `devin/1789516557-carddemo-java-engagement`
- **HEAD sha**: `410e5d0f826ccd3a6a2f4ea04eee673f64c240d8`
- **Auditor**: independent auditor (did not perform the migration)
- **Date**: 2026-09-16
- **Scope**: FR-S10-01..20 in `S10_functional_requirement.md`; online surface
  `/reports` + `POST /api/reports`; `cbtrn03Job` batch chain; boundary table
  S10-B1..B8 and wave PR #131 deviations. Out of scope: CSUTLDTC internals
  (S-09), menu shell (S-01), downstream print distribution.

## Verdict: **PASS with findings**

All 20 FRs trace to concrete implementation and named tests; every catalogued
message literal, edit order, cursor target, and report layout field matches the
COBOL source verbatim. All documented deviations (S10-B1..B8, D-1, PR #131
notes) are honoured. Findings are LOW/INFO only: one plan-document claim is
inaccurate (2513 cell *can* produce a launchable parameter), plus three
informational parity notes. Scoped tests: 31 run, 31 passed.

## 1. Traceability matrix

| FR | Requirement | Implementation | Named test(s) | Status |
|---|---|---|---|---|
| FR-S10-01 | Monthly → 1st..last day of current month | `ReportService.enter` (`spring-boot/src/main/java/com/carddemo/service/ReportService.java:36-38`) | `ReportServiceTest.monthlyDerivesCurrentMonthRange_frS1001`, `ReportUiIntegrationTest.monthlySubmitLaunchesTheJobAndShowsGreenSuccess_frS1001_frS1014` | PASS |
| FR-S10-02 | Yearly → Jan-01..Dec-31 of current year | `ReportService.enter` (`ReportService.java:40-44`) | `ReportServiceTest.yearlyDerivesFullYearRange_frS1002` | PASS |
| FR-S10-03 | Custom → typed range after edits | `ReportService.enter` (`ReportService.java:45-78`) | `ReportServiceTest.customPassesTheTypedRangeThrough_frS1003`, `ReportUiIntegrationTest.customSubmitRunsTheJobAndWritesTheTypedRange_frS1003_frS1016` | PASS |
| FR-S10-04 | First non-blank type flag wins (Monthly→Yearly→Custom) | `ReportService.enter` evaluation order (`ReportService.java:36-45`) | `ReportServiceTest.firstNonBlankTypeFlagWins_frS1004` | PASS |
| FR-S10-05 | Blank custom field → verbatim "…can NOT be empty…" + cursor, no submit | `ReportService.firstBlank`/`emptyMessage` (`ReportService.java:49-53,111-141`); literals `CobolMessages.java:469-480` | `ReportServiceTest.blankCustomFieldsShowTheFieldMessageInSourceOrder_frS1005`, `ReportUiIntegrationTest.blankStartMonthShowsTheVerbatimError_frS1005` | PASS |
| FR-S10-06 | Month non-numeric or >12 → "Not a valid Month…" | `ReportService.firstInvalid`/`invalidMessage` (`ReportService.java:56-60,122-152`); `CobolMessages.java:481-482,487-488` | `ReportServiceTest.nonNumericOrHighMonthShowsNotAValidMonth_frS1006` | PASS |
| FR-S10-07 | Day non-numeric or >31 → "Not a valid Day…" | `ReportService.java:124,127` + `CobolMessages.java:483-484,489-490` | `ReportServiceTest.nonNumericOrHighDayShowsNotAValidDay_frS1007` | PASS |
| FR-S10-08 | Year non-numeric → "Not a valid Year…" | `ReportService.java:125,128` + `CobolMessages.java:485-486,491-492` | `ReportServiceTest.nonNumericYearShowsNotAValidYear_frS1008` | PASS |
| FR-S10-09 | NUMVAL-C normalization (' 7'→'07') before range edits | `ReportForm.normalized`/`numvalc` (`spring-boot/src/main/java/com/carddemo/api/ReportForm.java:24-41`) | `ReportServiceTest.shortDigitsNormalizeBeforeTheRangeEdits_frS1009` | PASS |
| FR-S10-10 | CSUTLDTC 'YYYY-MM-DD' check; sev 0000 accept, msg 2513 tolerated, else "Not a valid date…" | `ReportService.enter`+`accepted` (`ReportService.java:61-70,154-158`) consuming `DateValidationService` (S-09 port, `DateValidationService.java:65-108`) | `ReportServiceTest.datesRunThroughCsutldtcWithThe2513Tolerance_frS1010` | PASS |
| FR-S10-11 | CONFIRM blank → "Please confirm to print the <name> report…", cursor CONFIRM | `ReportService.submit` (`ReportService.java:89-92`) + `CobolMessages.reportConfirm` (`CobolMessages.java:363-365`) | `ReportServiceTest.blankConfirmPromptsForTheReport_frS1011`, `ReportUiIntegrationTest.blankConfirmPromptsForTheReport_frS1011` | PASS |
| FR-S10-12 | CONFIRM N/n → clear all fields, resend, no submit | `ReportService.submit` → `ReportScreen.blank()` (`ReportService.java:102-104`) | `ReportServiceTest.declinedConfirmClearsTheScreenWithoutSubmitting_frS1012`, `ReportUiIntegrationTest.declinedConfirmClearsTheScreen_frS1012` | PASS |
| FR-S10-13 | CONFIRM other → `"<v>" is not a valid value to confirm…` | `ReportService.submit` (`ReportService.java:105-106`) + `CobolMessages.reportConfirmInvalid` (`CobolMessages.java:499-501`) | `ReportServiceTest.invalidConfirmShowsTheVerbatimMessage_frS1013`, `ReportUiIntegrationTest.invalidConfirmShowsTheVerbatimMessage_frS1013` | PASS |
| FR-S10-14 | CONFIRM Y → job launch (TDQ 'JOBS' equivalent) → green "<name> report submitted for printing …" | `ReportService.submit`/`request` (`ReportService.java:93-100,176-207`) → `BatchJobLauncherService.launch` (`BatchJobLauncherService.java:26-41`); green = `ReportScreen.submitted` style `info` + `CobolMessages.reportSubmitted` (`CobolMessages.java:505-507`) | `ReportServiceTest.confirmedSubmitLaunchesAndShowsGreenSuccess_frS1014`, `ReportUiIntegrationTest.monthlySubmitLaunchesTheJobAndShowsGreenSuccess_frS1001_frS1014`, `ApiIntegrationTest.transactionsBillingReportsAndAdminUsersUsePortedFlows` (POST /api/reports block, `ApiIntegrationTest.java:259-265`) | PASS |
| FR-S10-15 | Submit-seam failure → "Unable to Write TDQ (JOBS)…", no success | `ReportService.submit` catch → `REPORT_TDQ_WRITE_FAILED` (`ReportService.java:96-99`; `CobolMessages.java:493-494`) | `ReportServiceTest.launchFailureShowsTheTdqWriteError_frS1015` | PASS |
| FR-S10-16 | TRANREPT chain semantics: proc-dt in [start,end] filter + card sort | Steps eliminated per decided S10-B8; semantics live in `cbtrn03Reader` — `findByTranProcessTimestampBetween` + `ORDER BY tranCardNumber, tranId` (`Cbtrn03JobConfiguration.java:41-56`, `TransactionRepository.java:14`) | `Cbtrn03ReportParityTest.reportMatchesTheCvtra07yLayout_frS1016_frS1017_frS1018` (out-of-range exclusion asserted), `ReportUiIntegrationTest.customSubmitRunsTheJobAndWritesTheTypedRange_frS1003_frS1016` | PASS |
| FR-S10-17 | Detail rows: only in-range, ordered by card, id + acct (via XREF) + type/cat desc + source + edited amount | `cbtrn03Reader` + `BatchJobService.reportLine` lookups (`BatchJobService.java:177-194`) + `ReportLineAggregator.detail` (`BatchStreamingWriters.java:110-120`) | `Cbtrn03ReportParityTest.reportMatchesTheCvtra07yLayout_frS1016_frS1017_frS1018` (51-line golden file), `rangeWithNoRowsWritesAnEmptyReport_frS1017` | PASS |
| FR-S10-18 | Headers + page break every 20 written records; Page Total per page; Account Total on card break; Grand Total at EOF | `ReportLineAggregator` (`BatchStreamingWriters.java:44-57` break order, `:66-77` footer, `:79-99` header/page/account emit) | `Cbtrn03ReportParityTest.reportMatchesTheCvtra07yLayout_frS1016_frS1017_frS1018` (two card groups, two mid-stream page breaks, single Account Total) | PASS |
| FR-S10-19 | File/lookup IO error → step abends (CEE3ABD 999), no silent truncation | `BatchJobService.reportLine` `.orElseThrow` on missing XREF/TRANTYPE/TRANCATG (`BatchJobService.java:178-192`) → step FAILED | `Cbtrn03ReportParityTest.missingXrefLookupAbendsTheStep_frS1019` | PASS |
| FR-S10-20 | PF3 → caller (COMEN01C); unauthenticated → sign-on; other AID → invalid key | `UiController.submitReportRequest` (`UiController.java:752-760`); auth bounce via security config; `MenuService` option 9 + `UI_ROUTES` (`MenuService.java:46,121`) | `ReportUiIntegrationTest.pf3ReturnsToTheMenu_frS1020`, `unsignedEntryBouncesToSignon_frS1020`, `invalidAidRedisplaysWithTheInvalidKeyMessage_frS1020`, `menuOptionNineRoutesToTheReportScreen` | PASS |

## 2. COBOL parity spot-checks

### Message catalogue (verbatim literal compare — all match)

| Message | COBOL | Java | Match |
|---|---|---|---|
| `Start Date - Month can NOT be empty...` | CORPT00C.cbl:261 | CobolMessages.java:469-470 | ✓ |
| `Start Date - Day can NOT be empty...` | CORPT00C.cbl:268 | CobolMessages.java:471-472 | ✓ |
| `Start Date - Year can NOT be empty...` | CORPT00C.cbl:275 | CobolMessages.java:473-474 | ✓ |
| `End Date - Month can NOT be empty...` | CORPT00C.cbl:282 | CobolMessages.java:475-476 | ✓ |
| `End Date - Day can NOT be empty...` | CORPT00C.cbl:289 | CobolMessages.java:477-478 | ✓ |
| `End Date - Year can NOT be empty...` | CORPT00C.cbl:296 | CobolMessages.java:479-480 | ✓ |
| `Start/End Date - Not a valid Month...` | CORPT00C.cbl:331,357 | CobolMessages.java:481-482,487-488 | ✓ |
| `Start/End Date - Not a valid Day...` | CORPT00C.cbl:340,366 | CobolMessages.java:483-484,489-490 | ✓ |
| `Start/End Date - Not a valid Year...` | CORPT00C.cbl:348,374 | CobolMessages.java:485-486,491-492 | ✓ |
| `Start/End Date - Not a valid date...` | CORPT00C.cbl:400,420 | CobolMessages.java:28-29 | ✓ |
| `Select a report type to print report...` | CORPT00C.cbl:438 | CobolMessages.java:27 | ✓ |
| `Please confirm to print the <name> report...` (name DELIMITED BY SPACE) | CORPT00C.cbl:465-469 | CobolMessages.java:363-365 | ✓ |
| `"<v>" is not a valid value to confirm...` (CONFIRMI DELIMITED BY SPACE) | CORPT00C.cbl:485-489 | CobolMessages.java:499-501 | ✓ |
| `Unable to Write TDQ (JOBS)...` | CORPT00C.cbl:531-532 | CobolMessages.java:493-494 | ✓ |
| `<name> report submitted for printing ...` (WS-REPORT-NAME DELIMITED BY SPACE) | CORPT00C.cbl:449-451 | CobolMessages.java:505-507 | ✓ |
| `Invalid key pressed. Please see below...` | CSMSG01Y.cpy:20-21 (CCDA-MSG-INVALID-KEY) | CobolMessages.java:10 | ✓ |

### Edit rules and flow

| Rule | COBOL | Java | Match |
|---|---|---|---|
| Type flag = any non-blank; first match in Monthly→Yearly→Custom order | CORPT00C.cbl:212-256 (`EVALUATE TRUE`, `NOT = SPACES AND LOW-VALUES`) | ReportService.java:36-45 (`nonBlank` = non-null, non-blank) | ✓ |
| Monthly end = day before next-month-01 (Dec→Jan rollover) | CORPT00C.cbl:223-230 | `YearMonth.atEndOfMonth()` ReportService.java:38 | ✓ |
| Yearly = YYYY-01-01..YYYY-12-31 | CORPT00C.cbl:243-252 | ReportService.java:41-43 | ✓ |
| Blank-field edits in SDTMM→EDTYYYY order, first failure wins, field cursor | CORPT00C.cbl:258-303 | ReportService.java:111-119 (+cursor via `cursorField`) | ✓ |
| NUMVAL-C re-stores zero-padded value between blank and numeric edits | CORPT00C.cbl:305-327 | ReportForm.java:24-41 | ✓ |
| Numeric/>12/>31 edits in same field order | CORPT00C.cbl:329-378 | ReportService.java:122-130 | ✓ |
| CSUTLDTC: sev 0000 accept; non-0000 accepted iff MSG-NUM='2513'; format 'YYYY-MM-DD' | CORPT00C.cbl:388-426 | ReportService.java:61-70,156-158 → DateValidationService.java:65-108 (2513 at :104) | ✓ |
| Confirm gate order: blank-prompt → Y → submit; N → initialize; other → invalid msg | CORPT00C.cbl:464-494 | ReportService.java:88-107 | ✓ |
| Cursor targets: MONTHLY on type-prompt/submitted/TDQ-fail/invalid-AID; CONFIRM on confirm errors; field on field errors | CORPT00C.cbl:441,453,492,533,192 | ReportService.java:46-47,97-98 + ReportScreen.submitted/blank + UiController.java:760 | ✓ |
| AID map: ENTER→process, PF3→COMEN01C, other→invalid-key redisplay; EIBCALEN=0→COSGN00C | CORPT00C.cbl:172-194 | UiController.java:738-762 + auth redirect to `/signon` | ✓ |
| Screen literals: 'Monthly (Current Month)', 'Yearly (Current Year)', 'Custom (Date Range)', 'Start Date :', '  End Date :', '(MM/DD/YYYY)', 'The Report will be submitted for printing. Please confirm:', '(Y/N)', 'ENTER=Continue  F3=Back' | app/bms/CORPT00.bms:93,107,121,126,165,198-205,215,226 | templates/reports.html:20,26,32,35,47,50,62,65,69,71 | ✓ |
| Header: Tran CR00, Prog CORPT00C, 'Transaction Reports' | CORPT00C.cbl:37-38; bms TITLE | reports.html:3,14; layout shell | ✓ |

### File/DB semantics and report layout

| Semantics | COBOL | Java | Match |
|---|---|---|---|
| In-range filter: `TRAN-PROC-TS(1:10)` between start and end | CBTRN03C.cbl:173-174 + TRANREPT.jcl:47-48 (SORT INCLUDE) | `findByTranProcessTimestampBetween` with `startT00:00:00`..`endT23:59:59.999999999` (Cbtrn03JobConfiguration.java:50-52) | ✓ |
| Card ordering: `SORT FIELDS=(TRAN-CARD-NUM,A)` | TRANREPT.jcl:46 | `ORDER BY tranCardNumber, tranId` (Cbtrn03JobConfiguration.java:45-47) | ✓ (see INFO-2 on within-card tiebreak) |
| XREF keyed read on card-number change only | CBTRN03C.cbl:181-188 | `xrefs.findById` per row (BatchJobService.java:178) — same output | ✓ (INFO-3) |
| TRANTYPE / TRANCATG (type-cd+cat-cd) keyed reads for descriptions | CBTRN03C.cbl:189-195 | BatchJobService.java:181-192 | ✓ |
| INVALID KEY → DISPLAY + CEE3ABD 999 abend | CBTRN03C.cbl:486-490, 626-630 | `orElseThrow(IllegalStateException "INVALID CARD NUMBER : <card>")` → step FAILED (BatchJobService.java:178-180) | ✓ |
| Headers emitted inside first in-range row's write (WS-FIRST-TIME) | CBTRN03C.cbl:275-280 | `first` flag → `headers()` on first `aggregate` (BatchStreamingWriters.java:50-53) | ✓ |
| Page break at `MOD(line-counter, 20)=0`; every written record counts (headers/totals/rules/details) | CBTRN03C.cbl:282-285, 299-339, 373 | `lines` incremented in `emit` for every record (BatchStreamingWriters.java:54,101-107) | ✓ |
| Account Total on card break only — never for the last card | CBTRN03C.cbl:181-184 (break branch) + 197-203 (EOF writes no account total) | `accountTotals` only inside break branch (BatchStreamingWriters.java:44-47); footer writes none (:66-77) | ✓ |
| EOF stale-add: last record's TRAN-AMT added once more to page (and dead account) total, then Page+Grand written | CBTRN03C.cbl:197-203 | `footer()` adds `lastAmount` then writes Page+Grand (BatchStreamingWriters.java:66-77) | ✓ |
| Zero in-range rows → no output (NEXT SENTENCE exits the sweep) | CBTRN03C.cbl:176-177 | `footer()` returns `""` when `first` still true (:67-68) | ✓ |
| Detail line: id X(16) sp acct X(11) sp cd'-'desc(15) sp cat9(04)'-'desc(29) sp source(10) 4sp amt `-ZZZ,ZZZ,ZZZ.ZZ` 2sp → 133 | CVTRA07Y.cpy:15-31 | `detail()` (BatchStreamingWriters.java:110-120) | ✓ |
| Totals: 'Page Total'X(11)/'Grand Total'X(11)/'Account Total'X(13) + '.' leaders to col 97 + `+ZZZ,ZZZ,ZZZ.ZZ` | CVTRA07Y.cpy:50-66 | `total()` (BatchStreamingWriters.java:127-130) | ✓ |
| Edited money: fixed sign col, leading-zero+comma suppression, '.' shown | CVTRA07Y.cpy:30,54,60,66 | `edit()` (BatchStreamingWriters.java:135-169) | ✓ |
| Name header: 'DALYREPT'X(38)+'Daily Transaction Report'X(41)+'Date Range: '+start+' to '+end | CVTRA07Y.cpy:4-13 | `nameHeader` (BatchStreamingWriters.java:34-38) | ✓ |
| Header-1 column titles + 133×'-' rule | CVTRA07Y.cpy:33-48 | `headers()` (BatchStreamingWriters.java:79-86) | ✓ |
| XREF-ACCT-ID 9(11) → X(11) digits | CBTRN03C.cbl:364 | `"%011d"` (BatchStreamingWriters.java:124) | ✓ |
| Submit: 14-card JCL → TDQ 'JOBS' (intrdr), SYMNAMES + DATEPARM carrying dates | CORPT00C.cbl:81-127,498-535 | `launcher.launch("cbtrn03Job", {startDate,endDate})` (ReportService.java:203-204) — decided seam S10-B1/B2 | ✓ (by decision) |

### Exit/abend codes

- CEE3ABD abend-999 → step FAILED with non-zero exit: `missingXrefLookupAbendsTheStep_frS1019` observes `BatchStatus.FAILED`; DISPLAY diagnostic ('INVALID CARD NUMBER : ' + card, CBTRN03C.cbl:487) is carried as the exception text (BatchJobService.java:179-180) → application log per S10-B7. ✓

## 3. Stub/placeholder sweep

Searched all S-10 surface files —
`ReportService.java`, `ReportController.java`, `ReportForm.java`,
`ReportScreen.java`, `CobolMessages.java`, `Cbtrn03JobConfiguration.java`,
`BatchStreamingWriters.java`, `BatchJobService.java`, `UiController.java`,
`reports.html`, `ReportServiceTest.java`, `ReportUiIntegrationTest.java`,
`Cbtrn03ReportParityTest.java` — for `TODO`, `FIXME`, `XXX`,
`not implemented`, `UnsupportedOperationException`, `@Disabled`,
`assertTrue(true)`, commented-out assertions, and hardcoded happy-path
returns. **None found.** The three test classes contain no disabled or
trivially-true assertions; the golden-file test compares 51 report lines
byte-for-byte.

## 4. Documented deviations vs shipped code

| Deviation | Source | Compliant? |
|---|---|---|
| S10-B1: POST /api/reports → BatchJobLauncherService → cbtrn03Job; screen uses same service; launch failure → 'Unable to Write TDQ (JOBS)…'; non-idempotent | plan §3 | Yes — ReportController.java:15-18, ReportService.java:93-100,176-207 |
| S10-B2: single startDate/endDate JobParameters; validator rejects missing/invalid before open | plan §3 | Yes — Cbtrn03JobConfiguration.java:58-72; `ApiIntegrationTest.invalidReportJobLaunchIsReturnedAsBadRequest` |
| S10-B3: VSAM→Postgres JPA repositories | plan §3 | Yes — BatchJobService.java:177-193, Cbtrn03JobConfiguration.java:41-56 |
| S10-B4: flat file `cbtrn03-report.txt` under `carddemo.batch.output-dir`; intermediates eliminated; rerun-safe | plan §3 | Yes — Cbtrn03JobConfiguration.java:82-97 (`shouldDeleteIfExists`) |
| S10-B5: consume S-09 CSUTLDTC port, do not re-port; 2513 tolerance in edit logic | plan §3 | Yes — ReportService.java:63-70,156-158 calls `DateValidationService` |
| S10-B6: `/reports` behind auth; PF3 → `/menu`; unauthenticated → `/signon` | plan §3 | Yes — UiController.java:730-761 + UI tests |
| S10-B7: error → exception → step FAILED → non-zero exit; diagnostics to log | plan §3 | Yes — BatchJobService.java:178-192; parity test asserts FAILED |
| S10-B8: REPRO+DFSORT eliminated; repository `BETWEEN` + `ORDER BY card,id` | plan §3 | Yes — Cbtrn03JobConfiguration.java:41-56 |
| D-1: inverted range rejected (`End Date must not be before Start Date...`) though legacy submits silently | FR §11.2, PR #131 | Yes — ReportService.java:71-76,200; `invertedCustomRangeIsRejected_deviationD1` |
| Menu/catalogue tests flipped to expect `/reports` route | PR #131 | Yes — MenuService.java:46,121; `menuOptionNineRoutesToTheReportScreen` |
| `BatchJobIntegrationTest` Account Total → Page Total for single-card report | PR #131 | Yes — BatchJobIntegrationTest.java:122-126 asserts Page+Grand, no account-total claim |
| SQL-filtered sweep: trailing out-of-range record can't suppress EOF totals as it would in COBOL | PR #131 | Yes — reader filters in query (Cbtrn03JobConfiguration.java:50-52); unreachable in legacy anyway since DALY(+1) was pre-filtered by DFSORT |
| REPRO/DFSORT + GDG eliminated | PR #131 | Yes — no job artifact for unload/sort steps |

No undocumented deviations found beyond the INFO items below.

## 5. Test evidence

Command (on the audited branch, `spring-boot/`):

```
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -q test \
  -Dtest='ReportServiceTest,ReportUiIntegrationTest,Cbtrn03ReportParityTest'
```

Observed JUnit counts (surefire reports):

| Class | Tests run | Failures | Errors | Skipped |
|---|---|---|---|---|
| ReportServiceTest | 17 | 0 | 0 | 0 |
| ReportUiIntegrationTest | 11 | 0 | 0 | 0 |
| Cbtrn03ReportParityTest | 3 | 0 | 0 | 0 |
| **Total** | **31** | **0** | **0** | **0** |

Build: BUILD SUCCESS. Also relevant but cross-stream (not in the scoped run):
`ApiIntegrationTest` (POST /api/reports blocks) and
`BatchJobIntegrationTest` (cbtrn03Job assertions).

## 6. Findings

| Severity | Title | Evidence | Recommendation |
|---|---|---|---|
| LOW | Plan documents the 2513 cell as "unreachable-in-target — cannot produce a launchable parameter", but it can: year `0000` passes `numeric()`, is tolerated at `accepted()`, and `LocalDate` supports year 0, so a confirmed custom request launches `cbtrn03Job` with `startDate=0000-06-15` | `S10_functional_requirement.md:169-172` (claim); ReportService.java:63-78 (`accepted` → `LocalDate.parse` → `submit`); DateValidationService.java:100-106 (year 0 → 2513). Behaviour is still parity-conformant — legacy submits the same date and the string-compare filter includes all rows | Correct the assumption text; no code change needed since the shipped behaviour matches legacy |
| INFO | Submit seam is synchronous: `launch()` runs the whole `cbtrn03Job` inline before the screen responds; legacy TDQ write was fire-and-forget | BatchJobLauncherService.java:36 (blocking `launcher.run`); CORPT00C.cbl:517-535. A job *execution* failure still shows the green 'submitted' message (fire-and-forget parity holds) since a FAILED execution is returned, not thrown — verified by `missingXrefLookupAbendsTheStep_frS1019` | Acceptable as designed; note only that the request thread blocks for job duration |
| INFO | Within-card ordering: legacy `SORT FIELDS=(TRAN-CARD-NUM,A)` leaves same-card order to DFSORT stability (input = KSDS tran-id order); target adds an explicit `tranId` tiebreak | TRANREPT.jcl:46 vs Cbtrn03JobConfiguration.java:45-47 | Deterministic and equal in practice; no action |
| INFO | XREF/type/category lookups run per-row in the processor vs legacy xref read only on card-number break | BatchJobService.java:177-193 vs CBTRN03C.cbl:181-188 | Same observable output, extra DB reads only; no action |
| INFO | NUMVAL-C on non-digit input is unspecified in COBOL; the port leaves it as typed and lets the numeric edit fire — documented choice | ReportForm.java:9-10,31-41 | Documented in code; no action |

_No CRITICAL, HIGH or MEDIUM findings. No GAP or PARTIAL trace rows._
