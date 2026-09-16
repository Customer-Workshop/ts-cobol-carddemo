# S20 — auth_processing — Independent Audit

- **Stream:** S20 (auth_processing) — COPAUA0C online MQ auth processor (CP00), CBPAUP0C/CBPAUP0J pending-auth purge, PAUDBLOD load, PAUDBUNL/DBUNLDGS unload; DBPAUTP0 (ULU) and DFSURGU0 (IBM utility) not ported by decision.
- **Audited branch:** `devin/audit-s20-java` at HEAD `410e5d0f826ccd3a6a2f4ea04eee673f64c240d8` (engagement branch `devin/1789516557-carddemo-java-engagement`).
- **Auditor:** independent auditor (did not perform the migration).
- **Date:** 2026-09-16.
- **Scope:** every FR-S20-xx row traced to Java implementation + named test; verbatim parity spot-checks vs COBOL under `app/app-authorization-ims-db2-mq/` (`cbl/`, `cpy/`); stub sweep; documented deviations vs shipped code; scoped test run of the stream's classes only.

COBOL cites below are relative to `app/app-authorization-ims-db2-mq/`. Java cites are relative to `spring-boot/src/main/java/com/carddemo/` and `spring-boot/src/test/java/com/carddemo/`.

## Verdict: **PASS with findings**

## 1. Traceability matrix

| FR | Requirement | Implementation | Named test(s) | Status |
|---|---|---|---|---|
| FR-S20-01 | Consume `carddemo.pauth.request`, parse 18-field `CCPAURQY` CSV | `AuthProcessingService.processRun`/`pollRequest`/`parseRequest` (service/AuthProcessingService.java:118-146, 225-241); `InProcessMqService.registerConsumer`/`drain` (queue/InProcessMqService.java:127-160); queue name at queue/MqQueues.java:26 | `AuthProcessingParityTest.approvedRequestRepliesAndPersists_frS20_01_03_04_05_06`, `malformedCsvLogsAndSkipsWithoutReply_frS20_01`, `signedAmountTokenParsesViaNumval_frS20_01`, `AuthProcessingQueueIT.requestTriggersConsumerRunAndReply_frS20_01_04` | PASS |
| FR-S20-02 | Resolve card→acct→cust via XREF/ACCT/CUST; any miss → decline path | `processMessage` resolution chain service/AuthProcessingService.java:164-212 | `unknownCardDeclines3100AndPersistsNothing_frS20_02_03`, `missingAcctDeclines3100ButStillPersists_frS20_02_03`, `missingCustStillDecidesByLimits_frS20_02_03` | PASS |
| FR-S20-03 | Decide approve '00'/amt=txn or decline '05'/amt=0; reason per cause map | `decide` service/AuthProcessingService.java:249-287 (summary limits win, then account limits, then decline; reasons 3100/4100/9000) | `declineAgainstSummaryLimits_frS20_03_05`, `declineAgainstAccountLimitsWhenNoSummary_frS20_03`, `approvedRequestRepliesAndPersists_frS20_01_03_04_05_06` | PASS |
| FR-S20-04 | 6-field `CCPAURLY` CSV reply on request's replyTo, correlId echoed | `sendReply` service/AuthProcessingService.java:291-306 | `requestWithoutReplyQueueLogsM004AndTerminates_frS20_04_07`, `AuthProcessingQueueIT.requestTriggersConsumerRunAndReply_frS20_01_04` | PASS |
| FR-S20-05 | PAUTSUM0 summary upsert (REPL-or-ISRT) | `persist` summary branch service/AuthProcessingService.java:313-357 | `approvedRequestRepliesAndPersists_frS20_01_03_04_05_06`, `missingAcctDeclines3100ButStillPersists_frS20_02_03` | PASS |
| FR-S20-06 | PAUTDTL1 detail insert, complement key, match status P/D | `newDetail` service/AuthProcessingService.java:373-406 | `approvedRequestRepliesAndPersists_frS20_01_03_04_05_06` | PASS |
| FR-S20-07 | CSSL-format structured error log w/ event key | `errorLog` service/AuthProcessingService.java:414-435; CSSL constants :63-64 | `unknownCardWarningIsStructuredLog_frS20_07`, `requestWithoutReplyQueueLogsM004AndTerminates_frS20_04_07` | PASS |
| FR-S20-08 | Bounded run: ≤500 msgs then exit; per-msg SYNCPOINT | `PROCESS_LIMIT` + loop bound service/AuthProcessingService.java:61,118-135; per-message tx at :219 `tx.executeWithoutResult` | `runBoundedAtFiveHundredMessages_frS20_08`, `nonCriticalMidRunContinuesToNextMessage_frS20_08` | PASS |
| FR-S20-09 | Purge expired details; emptied summaries deleted; counters logged; RC16 on failure | `PendingAuthPurgeService.purge`/inner loop batch/PendingAuthPurgeService.java:69-125; `PendingAuthPurgeTasklet.execute` batch/PendingAuthPurgeTasklet.java:50-101 | `PendingAuthPurgeJobIT.expiredDetailDeletedAndEmptySummaryDropped_frS20_09`, `survivingSummaryKeepsStoredCounters_frS20_09`, `doubleApprovedGuardIgnoresDeclinedCount_frS20_09`, `expiryBoundaryIsInclusive_frS20_09`, `blankAndNonNumericParmsFallBackToDefaults_frS20_09`, `deleteFailureEndsJobAsRc16_frS20_09` | PASS |
| FR-S20-10 | Checkpoint every chkpFreq summaries + periodic display | `PendingAuthPurgeTasklet` batching + ExecutionContext `RMAD%04d` batch/PendingAuthPurgeTasklet.java:65-97 | `PendingAuthPurgeJobIT.checkpointIdAndDisplayCadence_frS20_10`, `debugFlagLogsAndRejectNonY_frS20_10` | PASS |
| FR-S20-11 | Load summary+detail; 'II' dup tolerated; non-numeric key skipped; other status → RC16 | `PendingAuthImportTasklet` + `PendingAuthCsv` (batch/PendingAuthImportTasklet.java; batch/PendingAuthCsv.java) | `PendingAuthImportJobIT.rootsAndChildrenInsert_frS20_11`, `duplicateRootIsTolerated_frS20_11`, `duplicateChildIsTolerated_frS20_11`, `nonNumericRootKeyIsSkipped_frS20_11`, `childWithoutParentFailsRc16_frS20_11`, `missingInputFileFailsLikeMissingDd_frS20_11`, `layoutIsRoundTripStable_frS20_11` | PASS |
| FR-S20-12 | Unload roots + `key|child` records (PAUDBUNL QSAM ≡ DBUNLDGS GSAM) | `PendingAuthExportTasklet` (batch/PendingAuthExportTasklet.java) — one impl covers both programs per comment :25-32 | `PendingAuthExportJobIT.exportsRootsInGnOrderAndChildrenUnderEachParent_frS20_12`, `exportedFilesReloadThroughImportLayout_frS20_11_12`, `defaultOutputTargetsPautdbDatasets_frS20_12`, `emptyDatabaseWritesEmptyFiles_frS20_12`, `unwritableOutputFails_frS20_12` | PASS |

All 12 FRs trace to concrete implementation and named tests. No GAP or PARTIAL rows.

## 2. COBOL parity spot-checks

| Source fact (COBOL) | Java equivalent | Result |
|---|---|---|
| 18-field request `CCPAURQY` field order (cpy/CCPAURQY.cpy) | `AuthRequest` record + `parseRequest` fields[0..17] (service/AuthProcessingService.java:225-241, 467-473) | Match — all 18 fields in order; `fields.length < 18 → null` guard at :226 (COBOL has no failure path on short UNSTRING — see Finding F2) |
| `WS-REQSTS-PROCESS-LIMIT = 500` (cbl/COPAUA0C.cbl:40); `>` check post-increment processes a 501st (:334-342) | `PROCESS_LIMIT = 500`, `processed < PROCESS_LIMIT` (:61, :129-132) — exactly 500 per FR | Documented deviation (PR #132, code comment :51-54); compliant |
| Request read: MQGET 5s wait (:354-372, MQGMO-WAITINTERVAL) | `DEFAULT_GET_WAIT_MILLIS = 5000` (queue/InProcessMqService.java:55) | Match |
| Reply `STRING` layout: card(16),tranid(15),authid(6),resp(2),reason(4),amt display,trailing comma (:722-731) | `sendReply` pads 16/15/6/2/4 + `editedAmount` + trailing `,` (:292-297) | Match (see Finding F6 for negative-amount edge) |
| `PA-RL-AUTH-ID-CODE` ← request auth *time*, not id-code (:662) | `new Decision(..., req.authTime())` (:287) | Quirk preserved |
| Decision: summary exists → `PA-CREDIT-LIMIT − PA-CREDIT-BALANCE`; else acct limits; else decline (:666-674); decline → '05'/amt 0 (:688-716); reasons 3100 > 4100 > 9000; 4200/4300/5100/5200 unreachable via 5600 stub (:647-650) | `decide` (:249-287) identical ordering and unreachable-code comment at :278 | Match |
| Reply MQMD: correlId echoed, MSGID=NONE, REPLYTOQ spaces, NOT-PERSISTENT, EXPIRY 50, FORMAT string (:744-760) | `new Message(null, request.correlId(), null, FORMAT_STRING, csv)` (:299); seam envelope has no persistence/expiry fields (S-22 seam scope) | Functional fields match; MQMD persistence/expiry not modeled — seam decision, INFO noted |
| Summary init on miss + unconditional ACCT-limit MOVEs even on read failure (:801-811) | `INITIALIZE` → zeroed summary; acct-miss leaves limits as stored, guarded `if (account != null)` (:334-338) | Documented deviation (PR #132); compliant |
| Approved: cnt+1, amt+=approved, credit_balance+=amt, cash=0; declined: cnt+1, `ADD PA-TRANSACTION-AMT` (stale detail buffer — filled only in 8500) (:813-821) | Same counters; decline add uses `req.transactionAmt()` (:351) | Documented deviation (PR #132, FR-intent); compliant |
| Detail key: `99999 − YYDDD`, `999999999 − HHMMSSmmm` (:868-875); match 'P'/'D' (:902-905); fraud fields spaces (:908-909) | `newDetail` complement math :373-380; `"P"`/`"D"` :403; `" "`/`"        "` :404-405 | Byte-for-byte match |
| CSSL record layout `CCPAUERY` (YYMMDD+HHMMSS, CP00, COPAUA0C, location, level, subsystem, code1, code2, msg, event key) (:983-1004; cpy/CCPAUERY.cpy) | `errorLog` builds identical-width record; C→error, W→warn, I→info (:414-435) | Match (verified field-by-field vs copybook widths) |
| Error codes emitted: M003 (:419-427), M004 (:768-775), I002 (:633-637), I003 (:840-845), I004 (:925-929), A001/A002/A003 (:494-497,:541-545,:589-592), C001/C002/C003 | M003 :142-146, M004 :303-305, I002 :209-212, I003/I004 :355-367, A001 :181, A002 :193, A003 :205, C001 :169-171, C002 :187-189, C003 :199-201 | Codes/messages match; **continuation semantics diverge** — Finding F1. M001 (:273-281), M005 (:966-973), I001 (:311-315) unreachable in-process — Finding F3 |
| Purge: `IF PA-APPROVED-AUTH-CNT <= 0 AND PA-APPROVED-AUTH-CNT <= 0` double-approved (cbl/CBPAUP0C.cbl:156) | `if (approvedCnt <= 0 && approvedCnt <= 0)` (batch/PendingAuthPurgeService.java:114) with comment | Quirk verbatim |
| Purge expiry: `99999 − date9c`, `CURRENT-YYDDD − authYYDDD >= expiry` (:280-284) | `99999 - authDate9c`, `currentYYDDD - authDate >= expiryDays` (:91-93) | Match (year-wrap quirk preserved) |
| Purge counters: resp '00' → dec approved cnt + PA-APPROVED-AMT else dec declined cnt + PA-TRANSACTION-AMT; decrements never REPL'd (:287-292) | locals only, never saved (:94-99, documented :27-34) | Match — tested by `survivingSummaryKeepsStoredCounters` |
| Purge parms: non-numeric expiry→5, blank/0/LOW-VALUES freqs→5/10, debug≠'Y'→'N' (:196-205) | `parseNumericOr`/`parseFreqOr` (batch/PendingAuthPurgeTasklet.java:51-53,120-137) | Match; Java also defaults *non-numeric* freqs (COBOL only checks blank/0) — minor widening, Finding F7 |
| CHKP per `procCnt > P-CHKP-FREQ` → freq+1 roots; `RMAD`+ctr; display per CHKP-DIS-FREQ (:160,:355-370); RC16 (:380-383) | batches of `chkpFreq+1` (:65-66); `s20.chkpId="RMAD%04d"` + display cadence (:84-97); `JobExecutionException("RC=16 …")` (:99-101) | Match |
| Load: 'II' tolerated+counted, other status→9999-ABEND RC16; non-numeric root key skipped; child gated on GU parent (cbl/PAUDBLOD.CBL:253-261,275,305-335) | `existsById` skip+count; non-numeric → `parseDetail` null → skip; missing parent → `JobExecutionException("ROOT GU CALL FAIL:GE")`; any failure → FAILED | Match (CSV stands in per S20-B8) |
| Unload: GN roots ascending, GNP children under each parent, `PA-ACCT-ID IS NUMERIC` guard, RC16 (cbl/PAUDBUNL.CBL:232 ff.; DBUNLDGS.CBL same walk) | `findAllById` sorted + `findAllByIdAcctIdOrderByIdAuthDate9cAscIdAuthTime9cAsc`; one impl for both QSAM/GSAM | Match — GN/GNP ordering asserted by test |

## 3. Stub/placeholder sweep

Searched `service/AuthProcessingService.java`, `batch/*PendingAuth*`, `queue/*`, `model/PendingAuth*.java` and all six test classes for `TODO`, `FIXME`, `not implemented`, `UnsupportedOperationException`, `@Disabled`, commented-out or trivially-true assertions, and hardcoded happy-path returns. **Found: none.** The only deliberate stubs are source-faithful ones already documented: COBOL's 5600 profile-load stub leaves reasons 4200/4300/5100/5200 unreachable, reproduced with comment at AuthProcessingService.java:278.

## 4. Documented deviations vs shipped code

| Deviation (plan S20-Bx / PR #132) | Shipped | Compliant |
|---|---|---|
| `> 500` post-increment → 501st processed in source; FR pins exactly 500 | `processed < 500` (:129-132); comment :51-54 | Yes |
| Decline add uses current request amount, not stale `PA-TRANSACTION-AMT` (:821) | `req.transactionAmt()` (:351) | Yes |
| Acct-miss leaves summary limits as-is (unread ACCTDAT MOVE skipped) | guarded `if (account != null)` (:334-338) | Yes |
| Cust miss alone does not decline an in-limit request (flag only feeds reason map) | `missingCustStillDecidesByLimits` test asserts '00' | Yes |
| Reasons 4200/4300/5100/5200 unreachable (5600 stub) | only 3100/4100/9000 emitted (:273-280) | Yes |
| Per-message flag staleness (summary/cust flags persist across msgs) not replicated | fresh locals per `processMessage` | Yes |
| S20-B1/B2: queue names, 18-field CSV, replyTo+correlId, 5s wait | `carddemo.pauth.request`/`.reply` (MqQueues.java:26-27), 5000ms, envelope echo | Yes |
| S20-B3: bounded drain not daemon; S20-B4: per-msg tx | `processRun` loop + drain thread; `tx.executeWithoutResult` per message | Yes |
| S20-B6: CSSL → SLF4J w/ preserved fields | `errorLog` (:414-435) | Yes |
| S20-B7: `pendingAuthPurgeJob`, parms, CHKP→batched commit, RC16→FAILED | Job bean + tasklet/service as cited; launchable via `BatchAdminController` `/api/admin/jobs/{jobName}` | Yes |
| S20-B8: CSV import/export replaces 100B/206B FB; ULU = pg_dump, not ported | `PendingAuthCsv` layout; no DBPAUTP0/DFSURGU0 port present | Yes |
| PR #132: "M001/M003/M004 and I003/I004 criticals terminate the run" | M003/M004/I002/I003/I004 terminate (throw); **M001 is never emitted** (open can't fail in-process) — see Findings F1/F3 | Mostly — M001 claim overstated |

No documented deviation is missing from shipped code; one undocumented behavioral consequence (post-critical persistence on M004) is Finding F1.

## 5. Test evidence

Command: `cd spring-boot && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -q test -Dtest='AuthProcessingParityTest,AuthProcessingQueueIT,PendingAuthPurgeJobIT,PendingAuthImportJobIT,PendingAuthExportJobIT,InProcessMqServiceTest'`

Surefire results (target/surefire-reports):

| Class | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| AuthProcessingParityTest | 12 | 0 | 0 | 0 |
| AuthProcessingQueueIT | 1 | 0 | 0 | 0 |
| PendingAuthPurgeJobIT | 8 | 0 | 0 | 0 |
| PendingAuthImportJobIT | 7 | 0 | 0 | 0 |
| PendingAuthExportJobIT | 5 | 0 | 0 | 0 |
| queue.InProcessMqServiceTest | 8 | 0 | 0 | 0 |
| **Total** | **41** | **0** | **0** | **0** |

(Stack traces appear in the log for `childWithoutParentFailsRc16`/`missingInputFileFailsLikeMissingDd` — expected `JobExecutionException` output from asserting FAILED status; all tests passed.)

## 6. Findings

| # | Severity | Finding | Evidence | Recommendation |
|---|---|---|---|---|
| F1 | LOW | Critical-error continuation diverges: COBOL logs criticals and **continues** (on M004 it still persists summary+detail at 8000; on C00x/I002/M003 it loops on). Java throws `MqProcessingException`, terminating the message/run — so a reply-put failure persists nothing where COBOL persists. Documented at wave-PR level ("criticals terminate the run") but absent from FR §5 and the analysis doc; the durable-state consequence on M004 is undocumented. | COBOL: cbl/COPAUA0C.cbl:768-779 (log → fallthrough to :463-465 persist); :419-435, :501-513, :549-560, :597-608, :632-639 (log-and-continue). Java: service/AuthProcessingService.java:303-305 throw before persist :218-220; :169-171,187-189,199-201,209-212,142-146. Test asserts empty persistence: AuthProcessingParityTest.java:243-260 | Record the continue-vs-abort decision in the FR/analysis docs; confirm the M004 no-persist behavior is intended (COBOL writes rows whose reply never arrives). |
| F2 | LOW | `A004 'REQUEST CSV PARSE FAILED'` is a Java-added CSSL location code not in the source catalogue — COBOL UNSTRING has no failure path and would process the garbage record. FR §11.3 documents the no-reply decision but not the new code. | service/AuthProcessingService.java:160-161; catalogue cbl/COPAUA0C.cbl:270-320 has no A004 | Add A004 to the error catalogue doc for completeness. |
| F3 | INFO | M001 'REQ MQ OPEN ERROR', M005 close warning, I001 'IMS SCHD FAILED' are unreachable in-process (open can't fail, close is no-op, no PSB SCHD). PR #132 text lists M001 among run-terminating criticals but no code emits it. | cbl/COPAUA0C.cbl:273-281,311-315,966-973; M001 appears only in javadoc service/AuthProcessingService.java:92; `openIsIdempotentAndCloseIsANoOp` test queue/InProcessMqServiceTest.java:53 | None needed; correct the PR description wording if accuracy of the catalogue matters. |
| F4 | INFO | FR doc §5 row 1 mislabels the open-failure code: 'REQ MQ OPEN ERROR' is M001 in source (:278-280), not M003 (:420-426 is the read failure). Doc-only nit; shipped codes are right. | functional/CARDDEMO/S20_functional_requirement.md §5 vs cbl/COPAUA0C.cbl:278,420 | Fix the doc row. |
| F5 | INFO | FR doc §3 says "1000-byte buffer"; source `W01-GET-BUFFER` is `PIC X(500)` (:103). Doc-only nit; Java has no buffer bound so behavior unaffected (COBOL >500B msgs would MQGET-truncate). | functional/CARDDEMO/S20_functional_requirement.md §3 vs cbl/COPAUA0C.cbl:103 | Fix the doc. |
| F6 | INFO | Reply amount edit: `WS-APPROVED-AMT-DIS PIC -zzzzzzzzz9.99` (floating sign) vs `String.format("%14.2f")`. Identical for non-negative values; a negative approved amount (reachable only via a negative request amount, itself abnormal) renders with the sign in a different column position. | cbl/COPAUA0C.cbl:66,:720-727; service/AuthProcessingService.java:437-441 | None — edge is effectively unreachable; note only. |
| F7 | INFO | Java defaults non-numeric purge freq parms to 5/10; source only treats blank/'0'/LOW-VALUES as default (a non-numeric X(5) like 'ABCDE' would pass its literal check and compare as garbage). Slightly wider fallback than source — defensible hardening. | cbl/CBPAUP0C.cbl:196-205; batch/PendingAuthPurgeTasklet.java:132-137 | Note in the FR doc's derivation section if strictness matters. |

---
*Audit produced read-only: no code, test, COBOL, or doc files were modified. Scoped suite only; full-suite audit is covered by a separate run.*
