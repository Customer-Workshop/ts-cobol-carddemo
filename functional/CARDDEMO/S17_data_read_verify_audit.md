# S-17 — Data Read / Verify Jobs — Independent Audit

- **Stream**: S-17 — Data read/verify jobs (`data_read_verify`)
- **Audited branch**: `devin/1789516557-carddemo-java-engagement` @ `410e5d0f826ccd3a6a2f4ea04eee673f64c240d8` (checked out locally as `devin/audit-s17-java`)
- **Auditor**: independent auditor (did not perform the migration)
- **Date**: 2026-09-16
- **Scope**: the four READ* verify jobs (READACCT/CBACT01C, READCARD/CBACT02C, READCUST/CBCUS01C, READXREF/CBACT03C) + COBDATFT assembler date-edit, per `S17_functional_requirement.md` (FR-S17-01..12), `S17_data_read_verify_analysis.md`, `S17_data_read_verify_migration_plan.md`, wave PR #114 (+ follow-up #116), and `.migration/04_boundary_register.md` rows S17-B1..B7.

## Verdict: **PASS with findings**

All 12 FRs trace to concrete implementation code and named tests; the scoped test run is green (15/15). No CRITICAL/HIGH findings. Two LOW findings (unported error-path DISPLAY texts; 999 realized as batch `ExitStatus`, not a process exit code) plus informational items.

## 1. Traceability matrix

| FR | Requirement | Implementation | Test(s) | Status |
|---|---|---|---|---|
| S17-FR-01 | Read accounts in `acctId` order; labelled per-field dump + raw record dump per row | `Cbact01JobConfiguration.readacctReader` (spring-boot/src/main/java/com/carddemo/batch/Cbact01JobConfiguration.java:49-57, sort `acctId` ASC) → `readacctProcessor` (:60-80) → `ReadVerifySupport.logLabelledAccount` (ReadVerifySupport.java:42-55) + `accountImage` (:58-72) | `ReadVerifyJobTest.readacctWritesByteFaithfulFilesAndDumps` (ReadVerifyJobTest.java:136) | PASS |
| S17-FR-02 | PSCOMP record per account, field-for-field, zoned numerics + COMP-3 debit, LRECL 107 | `ReadVerifySupport.PscompRecordBuilder.build` (ReadVerifySupport.java:129-154); `BatchFileSupport.pad(.,107)` via `ReadacctWriter.write` (ReadacctWriter.java:38); ISO-8859-1 `FlatFileItemWriter` `shouldDeleteIfExists` (Cbact01JobConfiguration.java:102-110) | same test, byte assertions :152-182 | PASS |
| S17-FR-03 | `ACCT-REISSUE-DATE` reformat `YYYY-MM-DD`→`YYYYMMDD` via `DateEditService` '2'→'2', first 10 bytes | `PscompRecordBuilder.build` :130-134 → `DateEditService.edit` (DateEditService.java:43-77) | `ReadVerifyJobTest` :165; `DateEditServiceTest.typeTwoToTwoStripsDashes` | PASS |
| S17-FR-04 | ARRYPS record: id + 5×(curr-bal, cyc-debit COMP-3), constants 1005.00/1525.00/-1025.00/-2500.00, LRECL 110 | `ReadVerifySupport.arrypsRecord` (ReadVerifySupport.java:162-175); `pad(.,110)` (ReadacctWriter.java:39) | `ReadVerifyJobTest` :184-196 | PASS |
| S17-FR-05 | VBPS variable records: VB1 12 chars, VB2 39 chars, alternating per account | `ReadVerifySupport.vb1Record`/`vb2Record` (:178-193); written as two lines (ReadacctWriter.java:40-41) | `ReadVerifyJobTest` :198-207 | PASS |
| S17-FR-06 | Substitute `2525.00` when `ACCT-CURR-CYC-DEBIT` = 0 | `PscompRecordBuilder` :136-139 (plus stale-carryover quirk state :127 — see §2) | `ReadVerifyJobTest` :168-182 | PASS |
| S17-FR-07 | Any file status ≠ '00'/'10' → abend 999 equivalent | `ReadVerifyStepListener.afterStep` → `ABENDING PROGRAM` + `ExitStatus("999")` (ReadVerifyStepListener.java:26-33); `Abend999JobListener` propagates to job exit (Abend999JobListener.java:11-15); wired on all four jobs/steps (e.g. Cbact01JobConfiguration.java:44,99) | `ReadVerifyJobTest.stepFailureAbendsWith999` (:317-331) | PASS (see findings F1, F2) |
| S17-FR-08 | Read cards in `cardNumber` order; dump `CARD-RECORD` once per row | `Cbact02JobConfiguration.readcardReader` sort `cardNumber` (Cbact02JobConfiguration.java:42-49); single log in `readcardWriter` (:57-60); pass-through processor :52-54 | `ReadVerifyJobTest.readcardDisplaysEachRecordOnce` (:241-260) | PASS |
| S17-FR-09 | Read xrefs in `xrefCardNumber` order; dump `CARD-XREF-RECORD` twice per row | `Cbact03JobConfiguration.readxrefProcessor` logs once (Cbact03JobConfiguration.java:53-58) + `readxrefWriter` logs again (:61-64) | `ReadVerifyJobTest.readxrefDisplaysEachRecordTwice` (:289-305) | PASS |
| S17-FR-10 | Read customers in `custId` order; dump `CUSTOMER-RECORD` twice per row | `Cbcus01JobConfiguration.readcustProcessor` (Cbcus01JobConfiguration.java:52-57) + `readcustWriter` (:60-63) | `ReadVerifyJobTest.readcustDisplaysEachRecordTwice` (:263-286) | PASS |
| S17-FR-11 | Job order `readacctJob → readcardJob → readcustJob → readxrefJob` documented | `BatchJobService.JOB_ORDER` key `"SCHID-030-READ-VERIFY"` (BatchJobService.java:51-53) | `ReadVerifyJobTest.jobOrderDocumentsCa7Schid030Chain` (:308-314) | PASS |
| S17-FR-12 | DateEditService '1'→'1' adds dashes, '2'→'2' strips, else `INVALID INPUT`, normal return | `DateEditService.edit` (DateEditService.java:43-77) | `DateEditServiceTest` — 9 tests covering all in/out combos, bad inType, dash check, padding/truncation | PASS (see finding F5) |

## 2. COBOL parity spot-checks

Message catalogue (every `DISPLAY` literal vs shipped text):

| Legacy literal | COBOL cite | Java cite | Match |
|---|---|---|---|
| `START/END OF EXECUTION OF PROGRAM <pgm>` | CBACT01C.cbl:141,158; CBACT02C.cbl:71,85; CBACT03C.cbl:71,85; CBCUS01C.cbl:71,85 | `ReadVerifySupport.logStart/logEnd` (:33-39) via `ReadVerifyStepListener` :21-31 | YES — byte-identical |
| 11 labelled field lines `ACCT-ID ... ACCT-GROUP-ID` (25-char literals) | CBACT01C.cbl:201-211 | `ReadVerifySupport.logLabelledAccount` :43-53 | YES — extracted literals compared byte-for-byte |
| 49-dash separator | CBACT01C.cbl:212 | `ReadVerifySupport.java:54` (`"-".repeat(49)`) | YES |
| `VBRC-REC1:`/`VBRC-REC2:` + 12/39-byte record | CBACT01C.cbl:283-284 | `Cbact01JobConfiguration.java:71-72` | YES |
| `ABENDING PROGRAM` | CBACT01C.cbl:407; CBACT02C.cbl:155; CBACT03C.cbl:155; CBCUS01C.cbl:155 | `ReadVerifyStepListener.java:28` | YES |
| `ERROR READING/OPENING/CLOSING <file>`, `ACCOUNT FILE WRITE STATUS IS:`, `FILE STATUS IS: NNNN` | CBACT01C.cbl:192,246,268,294,309,328,345,363,381,399,420-425; CBACT02C.cbl:110,129,147,168-173; CBACT03C.cbl:110,129,147,168-173; CBCUS01C.cbl:110,129,147,168-173 | — | NO — not ported (finding F1) |

Edit rules / derivations:

| Item | COBOL cite | Java cite | Match |
|---|---|---|---|
| `OUT-ACCT-REC` PSCOMP layout: 9(11)+X(1)+3×S9(10)V99+3×X(10)+S9(10)V99+S9(10)V99 COMP-3+X(10) = 107 bytes | CBACT01C.cbl:57-69; JCL LRECL=107 READACCT.jcl:39 | `PscompRecordBuilder.build` :140-150 (COMP-3 emitted as 7 raw bytes via `packed`, ZonedDecimalFieldFormatter.java:80-99) | YES — exact 107 (analysis's "106+pad" was wrong; real COMP-3 is 7 bytes) |
| `2525.00` substitute only when debit = 0; **no unconditional MOVE** → non-zero debit keeps prior record's bytes | CBACT01C.cbl:236-238 | `PscompRecordBuilder` instance state `currCycDebit`, init 7 NUL bytes, :127,:136-139 | YES — carryover quirk preserved, incl. program-start binary-zero area (flagged MEDIUM-confidence in code :119-124) |
| `ARR-ARRAY-REC`: `INITIALIZE` per record + occurs 1-3 constants `1005.00/1525.00/-1025.00/-2500.00`, occurs 4-5 stay zeros, ARR-FILLER spaces | CBACT01C.cbl:169,253-260,72-78 | `arrypsRecord` :162-174 (fresh zero/space record per item) | YES — exact 110 |
| `CALL 'COBDATFT'` type '2'→'2'; `MOVE CODATECN-0UT-DATE` (20B) to X(10) field → `YYYYMMDD`+2 pad | CBACT01C.cbl:225-233; CODATECN.cpy:39 | `build` :131-134 + `DateEditResult.outDate` 20-byte field, `substring(0,10)` | YES |
| `VB2-ACCT-REISSUE-YYYY` = first 4 of **source** `YYYY-MM-DD`, not reformatted output | CBACT01C.cbl:224,282,131-137 | `vb2Record` :187-192 | YES |
| VB varying records: `WS-RECD-LEN` 12 then 39 → `VBR-REC(1:n)` writes | CBACT01C.cbl:80-85,288-305; READACCT.jcl:45-47 VB LRECL=84 | two variable-length lines per account (ReadacctWriter.java:40-41) | YES — newline stands in for RDW (documented in PR #114) |
| COBDATFT branch semantics: inType dispatch; '1' path rejects `-` at byte 5 and outType '2' only; '2' path rejects outType '1' only (dash check commented out); error writes `INVALID INPUT` to COERMSG, R15=0, output area untouched | COBDATFT.asm:30-34,36-39,46-54,55-61 | `DateEditService.edit` :46-75; error path returns 20 spaces + `INVALID_INPUT` :79-81; caller ignores error field (silent-garbage parity) | YES — branch-for-branch, incl. unrecognized-outType still producing output |
| Sequential read in key order; EOF at status '10' | CBACT01C.cbl:29-33,166-185; CBACT02C.cbl:93-99; CBACT03C.cbl:93-99; CBCUS01C.cbl:93-99 | `RepositoryItemReader` `findAll` sorted by PK ASC (each config's reader bean) | YES |
| Write-status tolerance '00'/'10' else abend | CBACT01C.cbl:245,266-267,292-293,307-308 | any writer failure throws → step FAILED → 999 | YES (equivalent; '10' unreachable for flat files) |
| PREDEL deletes (IEFBR14) before recreate | READACCT.jcl:22-28 | `shouldDeleteIfExists(true)` on all three writers (Cbact01JobConfiguration.java:108) | YES |
| Record images for raw DISPLAY: 300/150/50/500-byte copybook layouts incl. FILLER sizes | CVACT01Y.cpy:4-17; CVACT02Y.cpy:4-11; CVACT03Y.cpy:4-8; CVCUS01Y.cpy:4-23 | `accountImage`/`cardImage`/`xrefImage`/`customerImage` (ReadVerifySupport.java:58-114) | YES — field order and lengths verified against copybooks |
| Double-DISPLAY quirk: xref/customer display in read paragraph AND main loop; card's in-read DISPLAY commented out | CBACT03C.cbl:96,78; CBCUS01C.cbl:96,78; CBACT02C.cbl:96 (comment),78 | processor+writer split in Cbact03 (:53-64) / Cbcus01 (:52-63); writer-only in Cbact02 (:57-60) | YES |
| CA-7 SCHID=030 chain CLOSEFIL→READACCT→READCARD→READCUST→READXREF→WAITSTEP→OPENFIL | app/scheduler/CardDemo.ca7:340,367,394,421,448,453 | `JOB_ORDER["SCHID-030-READ-VERIFY"]` (BatchJobService.java:51-53) — four S-17 jobs in order; CLOSEFIL/WAITSTEP/OPENFIL owned by other streams | YES (documented, not enforced — per S17-B1) |
| Abend `CEE3ABD ABCODE 999 TIMING 0` | CBACT01C.cbl:406-410 (and :154-158 in the other three) | step `ExitStatus("999")` + job `ExitStatus("999")` → API `exitCode` (BatchAdminController.java:23) | PARTIAL — see F2 |

## 3. Stub/placeholder sweep

Searched the stream's 13 implementation/test files (4 job configs, `ReadVerifySupport`, `ReadacctWriter`, `ReadacctOutputs`, `ReadVerifyStepListener`, `Abend999JobListener`, `BatchFileSupport`, `ZonedDecimalFieldFormatter`, `DateEditService`, `ReadVerifyJobTest`, `DateEditServiceTest`) for: `TODO`, `FIXME`, `XXX`, `not implemented`, `UnsupportedOperationException`, `@Disabled`/`@Ignore`, `assertTrue(true`, commented-out assertions. **No hits** (rg returned no matches). No hardcoded happy-path returns: all output builders compute from the item; the only constants (`2525.00`, `1005.00/1525.00/-1025.00/-2500.00`) are mandated COBOL literals (CBACT01C.cbl:237,256-260). `readcardProcessor` is a pass-through by design — the single DISPLAY is the writer's job for a read/dump program (Cbact02JobConfiguration.java:52-60), matching CBACT02C.cbl:96 where the in-read DISPLAY is commented out.

## 4. Documented deviations vs shipped code

| Deviation | Source | Shipped? | Evidence |
|---|---|---|---|
| S17-B1: job-order config + explicit launches; no scheduler engine | plan §4 / register | YES | `JOB_ORDER` map (BatchJobService.java:51-53); jobs resolve by bean name via `BatchJobLauncherService.launch` (:26-31) + `POST /api/admin/jobs/{jobName}` (BatchAdminController.java:18-25) |
| S17-B2: JPA readers, PK sort, no stored procedures | plan §4 | YES | four `RepositoryItemReader` beans, `findAll` + ASC sort (Cbact01:50-57, Cbact02:42-49, Cbact03:43-50, Cbcus01:42-49) |
| S17-B3: `DateEditService` preserving silent-error contract | plan §4 | YES | DateEditService.java:43-77; error returned in field, out area untouched-spaces |
| S17-B4: `ExitStatus`/exit mapping for 999 | plan §4 | YES (as batch ExitStatus) | ReadVerifyStepListener.java:29; Abend999JobListener.java:13 — see F2 |
| S17-B5: flat files in output dir, recreate per run | plan §4 | YES | `service.output("ACCTDATA.PSCOMP"/"ARRYPS"/"VBPS")` + `shouldDeleteIfExists` (Cbact01JobConfiguration.java:84-88,108); `carddemo.batch.output-dir` (application.properties:13) |
| S17-B6: log seam; keep double-display quirk | plan §4 | YES | `carddemo.sysout` channel (ReadVerifySupport.java:28); processor+writer split in Cbact03/Cbcus01 |
| S17-B7: pad short layouts with spaces to LRECL — INFERRED | plan §4 / register "DECIDED (proposed)"; STOP C adopted it (05_progress.md:35) | YES — and moot | `BatchFileSupport.pad` calls (ReadacctWriter.java:38-39); real layouts are exactly 107/110 (7-byte COMP-3), so pad is a defensive no-op — disclosed in PR #114 |
| PR #114 deviation: COMP-3 counted as 6 bytes in analysis (106/105-byte records); real PIC S9(10)V99 COMP-3 = 7 bytes → exact LRECL | PR #114 | YES | CBACT01C.cbl:67-68,76-77; `packed()` emits 7 bytes (ZonedDecimalFieldFormatter.java:80-99); test byte assertions ReadVerifyJobTest.java:168-182 |
| PR #114 deviation: asm-literal outType handling — `('1',d,'3')`/`('2',d,'3')` produce output, wider than "else INVALID INPUT" shorthand | PR #114 | YES | COBDATFT.asm:38-39,49-50 vs DateEditService.java:49,62; `DateEditServiceTest.unrecognizedOutTypeStillProducesOutput` :67-74 |
| PR #114 deviation: VBPS newline stands in for VB record descriptor | PR #114 | YES | ReadacctWriter.java:40-45 |
| PR #114 flag: GnuCOBOL differential check not run | PR #114 | UNVERIFIED | no toolchain step in environment; byte expectations are derived from source layouts, not a differential run |
| **Undocumented deviation found**: error-path DISPLAY texts not ported (see F1) | audit | NO docs | CBACT01C.cbl:192,246,268,294,309,328,345,363,381,399,420-425 etc. vs no equivalent literals in spring-boot |

## 5. Test evidence

Command (from repo `spring-boot/` dir):

```
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -q test -Dtest='ReadVerifyJobTest,DateEditServiceTest'
```

Observed (target/surefire-reports/*.txt on this checkout):

| Class | Tests run | Failures | Errors | Skipped |
|---|---|---|---|---|
| `com.carddemo.ReadVerifyJobTest` | 6 | 0 | 0 | 0 |
| `com.carddemo.util.DateEditServiceTest` | 9 | 0 | 0 | 0 |
| **Total** | **15** | **0** | **0** | **0** |

Job tests run on a dedicated `jdbc:h2:mem:readverify` datasource with seeding disabled and `carddemo.batch.output-dir=target/test-readverify-output` (ReadVerifyJobTest.java:49-54); expected bytes are computed in-test (e.g. the `packed()` helper :224-237), not copied from the implementation.

## 6. Findings

| # | Severity | Title | Evidence | Recommendation |
|---|---|---|---|---|
| F1 | LOW | Error-path DISPLAY texts not ported: `ERROR READING/OPENING/CLOSING <file>`, `ACCOUNT FILE WRITE STATUS IS:`, `FILE STATUS IS: NNNN` in all four programs; only `ABENDING PROGRAM` survives, and the failing status surfaces as the thrown exception's text, not the `NNNN` status display. Undocumented deviation (not in plan §4, register, or PR #114). FR §5's target is "log status then step FAILED" — satisfied loosely via framework exception logging. | COBOL: CBACT01C.cbl:192,246,268,294,309,328,345,363,381,399,420-425; CBACT02C.cbl:110,129,147,168-173; CBACT03C.cbl:110,129,147,168-173; CBCUS01C.cbl:110,129,147,168-173. Java: `ReadVerifyStepListener.java:26-33` logs only `ABENDING PROGRAM`. | Either port the failure-context displays (log a `FILE STATUS IS:`-style line with the exception/status before returning 999) or record the drop as a deviation in `.migration/04_boundary_register.md` / the plan. |
| F2 | LOW | The `999` abend is a Spring Batch `ExitStatus` string surfaced through the launch API's `exitCode` field — not a process exit code as register row S17-B4 reads ("process exit code 999-equivalent"). | `ReadVerifyStepListener.java:29`; `Abend999JobListener.java:13`; `BatchAdminController.java:22-24`; `.migration/04_boundary_register.md:81` | For a long-running service this is the only sensible mapping; document explicitly that the 999-equivalent is the batch/API exit code, not `System.exit`. |
| F3 | INFO | FR-01 wording is backwards vs source: it says "dump raw record then per-field labelled lines", but COBOL emits labelled lines inside `1000-ACCTFILE-GET-NEXT` (CBACT01C.cbl:170) and the raw `ACCOUNT-RECORD` in the main loop (:151). The implementation correctly matches the COBOL (labelled → VBRC → raw, Cbact01JobConfiguration.java:68-73). | `S17_functional_requirement.md:34` vs `CBACT01C.cbl:151,170`, `Cbact01JobConfiguration.java:68-73` | Fix FR wording on the next doc pass; code needs no change. |
| F4 | INFO | S17-B7's INFERRED padding is moot: `S9(10)V99 COMP-3` is 7 bytes, so PSCOMP is exactly 107 and ARRYPS exactly 110; `BatchFileSupport.pad` is a defensive no-op. Disclosed in PR #114; verified against CBACT01C.cbl:57-78. | `ReadVerifySupport.java:151-153`; `ReadacctWriter.java:35-39`; `ReadVerifyJobTest.java:168-182` | None — already disclosed; close the register's "FACT-pending" flag. |
| F5 | INFO | `DateEditService` is asm-faithful: an unrecognized `outType` (e.g. `('1',d,'3')`) still produces output rather than `INVALID INPUT` — wider than FR-12's "anything else → INVALID INPUT" if read on the outType axis. Matches COBDATFT.asm:38-39,49-50 and is disclosed in PR #114; the FR's four acceptance cases all behave correctly. | `COBDATFT.asm:30-54`; `DateEditService.java:46-75`; `DateEditServiceTest.java:67-74` | None — parity is to the assembler; FR-12 phrasing is imprecise. |
| F6 | INFO | `chunk(1)` commits per record on every step, technically a per-record commit — needed to preserve per-record DISPLAY/write ordering (PR #114); the FR §7 "no commit/checkpoint mid-job" note refers to restart semantics. | `Cbact01JobConfiguration.java:97` (and siblings); `S17_functional_requirement.md:65` | None. |
