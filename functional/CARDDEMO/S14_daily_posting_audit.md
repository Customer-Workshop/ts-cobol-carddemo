# S-14 Daily Posting — Independent Audit

- **Stream:** S14 (daily_posting) — POSTTRAN/CBTRN02C posting job, WAITSTEP/COBSWAIT→MVSWAIT wait step, CBTRN01C orphan validation utility.
- **Audited branch:** `devin/1789516557-carddemo-java-engagement` @ HEAD `410e5d0f826ccd3a6a2f4ea04eee673f64c240d8` (audit run on `devin/audit-s14-java`).
- **Auditor:** independent auditor (did not perform the migration).
- **Date:** 2026-09-16.
- **Scope:** every FR-S14-xx row in `S14_functional_requirement.md`; parity vs `app/cbl/CBTRN02C.cbl`, `app/cbl/COBSWAIT.cbl`, `app/cbl/CBTRN01C.cbl`, `app/cpy/CVTRA06Y.cpy`, `app/jcl/POSTTRAN.jcl`, `app/jcl/WAITSTEP.jcl`; stub sweep over the stream's Java code and tests; documented deviations in `S14_daily_posting_migration_plan.md` §3 and wave PR #115 vs shipped code; scoped JUnit run of stream tests only.

## Verdict: **PASS with findings**

All 18 FRs are implemented and the scoped tests pass (23 run, 0 failures). Findings are documentation-level or test-coverage gaps — the most material is a spec defect in the FR document itself (FR-S14-09), which the wave PR already disclosed and which the code correctly does not follow.

## 1. Traceability matrix

| FR | Requirement | Implementation | Named test(s) | Status |
|---|---|---|---|---|
| FR-S14-01 | card not in XREFFILE → reject 0100 | `BatchJobService.validate` (`spring-boot/src/main/java/com/carddemo/batch/BatchJobService.java:670-671`), reject emit `:136-141` | `DailyPostingJobIntegrationTest.rejectsCardNotInXrefWithReason100AndSurfacesRejectsCount` | PASS |
| FR-S14-02 | acct not in ACCTFILE → reject 0101 | `BatchJobService.java:672-673` | `DailyPostingJobIntegrationTest.rejectsWhenXrefPointsAtMissingAccountWithReason101` | PASS |
| FR-S14-03 | projected cyc net > credit limit → reject 0102 | `BatchJobService.java:674-681` | Branch executes in `rejectsOverlimitAndExpiredTransactionWithReason103LastWriteWins` and `BatchJobIntegrationTest.expirationFailureOverwritesEarlierOverlimitFailure`, but **no test asserts an 0102 reject outcome**; `PostDailyParityTest.overlimitBoundaryAtExactCreditLimitPosts` covers only the passing boundary | PARTIAL |
| FR-S14-04 | orig-date > expiry → reject 0103 | `BatchJobService.java:682-688` | `DailyPostingJobIntegrationTest.rejectsOverlimitAndExpiredTransactionWithReason103LastWriteWins`; `PostDailyParityTest.blankAccountExpiryRejects103LikeTheCobolStringCompare` | PASS |
| FR-S14-05 | TRAN-RECORD fields verbatim + PROC-TS=now | `BatchJobService.toTransaction` `:692-699`, save `:170` | `postsValidDailyTransactionUpdatingBalancesAndWritingTranRecord` | PASS |
| FR-S14-06 | existing TCATBAL → ADD + REWRITE | `BatchJobService.java:148-155` (existing row path) | `postsValidDailyTransactionUpdatingBalancesAndWritingTranRecord` (tcatBefore+amt assert) | PASS |
| FR-S14-07 | absent TCATBAL → build + WRITE | `BatchJobService.java:148-153` (`orElseGet` new row) | `createsMissingCategoryBalanceRowBeforePosting` | PASS |
| FR-S14-08 | acct bal += amt; sign-aware cyc credit/debit; REWRITE | `BatchJobService.java:161-169` | `postsValid…` + `negativeAmountFlowsToCycleDebitOnly` | PASS |
| FR-S14-09 | ACCTFILE REWRITE invalid key → "reject 0109 'UNABLE TO UPDATE ACCOUNT'" | `BatchJobService.java:156-169` — dead end preserved: no reject emitted, account update skipped, transaction still posts | `PostDailyParityTest.accountRewriteDeadEndStillPostsWithoutEmittingAReject` | PARTIAL — FR text is wrong vs source (see Findings F-1); implementation is COBOL-faithful, not FR-faithful |
| FR-S14-10 | rejects>0 → DISPLAY + RC 4 | `Cbtrn02JobConfiguration.cbtrn02ExitListener` `:92-103` → `COMPLETED` + `REJECTS:<n>` | `rejectsCardNotInXrefWithReason100AndSurfacesRejectsCount`, `countsEveryRejectInExitDescription`, `launchesTheDocumentedChainInOrderThroughTheAdminEndpoint` (API `exitDescription` assert) | PASS (RC4→exit-description is the documented S14-B5 mapping) |
| FR-S14-11 | zero rejects → RC 0 | `Cbtrn02JobConfiguration.java:100-102` | `postsValid…` asserts empty exit description | PASS |
| FR-S14-12 | file status ≠00 → DISPLAY + CEE3ABD abend | Exception propagation: reader open/parse failure or `DataAccessException` in `postDaily` → chunk rollback → step `FAILED` | None found for cbtrn02Job | PARTIAL — structurally present, untested; also no `Abend999JobListener` on this job (see F-3) |
| FR-S14-13 | reject rec = 350B + 9(04)+X(76) trailer | `BatchJobService.java:138-140` (`pad(raw,350)` + `pad("%04d%s",80)`); `BatchFileSupport.pad` truncates/pads to width | `assertRejectLine` helper asserts 430 length + reason/desc offsets, used by all reject tests | PASS |
| FR-S14-14 | sleep SYSIN centiseconds (3600 cs = 36 s) then RC 0 | `WaitStepTasklet` `:23,:52-59`; `WaitStepJobConfiguration` `:31-37` | `WaitStepTaskletTest` (5 tests) + `waitStepJobHonorsTheCentisecondParm` | PASS |
| FR-S14-15 | chain order POSTTRAN→WAITSTEP documented | `BatchJobService.DAILY_POSTING_CHAIN` `:65`; `docs/runbook/daily-posting-chain.md` | `launchesTheDocumentedChainInOrderThroughTheAdminEndpoint` | PASS |
| FR-S14-16 | cbtrn01-style validate-only sweep, diagnostics, no updates | `Cbtrn01JobConfiguration` (processor → `service::validateDaily` `:71-74`); `BatchJobService.validateDaily` `:123-133` | `orphanCbtrn01JobDiagnosesWithoutPosting` — card path only; **acct-not-found diagnostic branch (`:129-131`) untested** | PARTIAL |
| FR-S14-17 | rerun re-applies (non-idempotent) | additive `postDaily`; JPA `save` upserts the tran row | `rerunDoublePostsBalancesAndOverwritesTheTranRow` | PASS (dup-write abend→upsert is a documented deviation) |
| FR-S14-18 | empty/missing DALYTRAN → no writes, RC 0 | `Cbtrn02JobConfiguration.java:46-55` (`ByteArrayResource` empty feed when file absent) | `missingDailyTranFileIsAnEmptyFeedNotAnError`, `emptyFeedCompletesWithZeroWork` | PASS |

## 2. COBOL parity spot-checks

| Item | COBOL source | Java target | Match |
|---|---|---|---|
| 0100 `'INVALID CARD NUMBER FOUND'` | CBTRN02C.cbl:385-388 | BatchJobService.java:671 | exact literal ✓ |
| 0101 `'ACCOUNT RECORD NOT FOUND'` | CBTRN02C.cbl:397-399 | :673 | exact literal ✓ |
| 0102 `'OVERLIMIT TRANSACTION'` | CBTRN02C.cbl:410-412 | :680 | exact literal ✓ |
| 0103 `'TRANSACTION RECEIVED AFTER ACCT EXPIRATION'` | CBTRN02C.cbl:417-419 | :687 | exact literal ✓ |
| 109 literal is `'ACCOUNT RECORD NOT FOUND'` (NOT 'UNABLE TO UPDATE ACCOUNT') | CBTRN02C.cbl:556-558 | not emitted — dead end preserved (:156-169) | faithful to source, contradicts FR text (F-1) |
| Overlimit formula `CYC-CREDIT − CYC-DEBIT + AMT` vs limit, `>=` continues | CBTRN02C.cbl:403-412 | :674-678 (`creditLimit.compareTo(current) < 0` → reject) | ✓, boundary equality posts both sides (test: `overlimitBoundaryAtExactCreditLimitPosts`) |
| Expiry is an X(10) **string** compare; blank expiry < any date → rejects | CBTRN02C.cbl:414-419 | :684-686 (null expiry → reject; comment cites string-compare) | ✓ (test: `blankAccountExpiryRejects103…`) |
| Reason precedence = last-write-wins (102 then 103, no ELSE chain) | CBTRN02C.cbl:410-419 | :678-688 (sequential assignment, same order) | ✓ (test asserts 103 wins) |
| Reject record = 350B original + trailer `9(04)` + `X(76)` | CBTRN02C.cbl:177-182, :446-448; POSTTRAN.jcl LRECL=430 | :138-140 (`pad(raw,350)+pad("%04d%s",80)`), `pad` truncates | ✓ 430B per line asserted |
| `'TRANSACTIONS PROCESSED :'`/`'TRANSACTIONS REJECTED  :'` DISPLAY | CBTRN02C.cbl:227-228 | Cbtrn02JobConfiguration.java:97-98 (same literals incl. spacing) | ✓ |
| RC=4 when `WS-REJECT-COUNT > 0`, else 0 | CBTRN02C.cbl:229-231 | :99-102 → `REJECTS:<n>` exit description, job COMPLETED | ✓ documented mapping (S14-B5) |
| File-status ≠00 → DISPLAY IO-STATUS + CEE3ABD abend 999 | CBTRN02C.cbl:250,268,287,…, 9999-ABEND :707-711 | exceptions → step FAILED (B-004 contract) | ✓ semantics, untested (F-3); exit code is `FAILED`, not `999` (no `Abend999JobListener` on cbtrn02Job — other jobs register it, e.g. Cbact01JobConfiguration.java:44) |
| `ADD DALYTRAN-AMT TO ACCT-CURR-BAL`; sign-aware cyc credit (≥0)/debit (<0); REWRITE | CBTRN02C.cbl:547-553 | :162-168 | ✓ incl. `amt>=0` boundary |
| TCATBAL NOTFND → INITIALIZE + ADD + WRITE; else ADD + REWRITE | CBTRN02C.cbl:467-528 | :144-155 | ✓ |
| Post order TCATBAL → ACCT → TRANFILE | CBTRN02C.cbl:440-442 | :155,161-169,170 same order | ✓ (PR notes program-FR doc had a different textual order; code wins) |
| TRAN fields verbatim; PROC-TS = current DB2-format timestamp | CBTRN02C.cbl:425-438, Z-GET-DB2-FORMAT-TIMESTAMP :692-705 | `toTransaction` :692-699, `LocalDateTime.now()` | ✓ (target column is a real timestamp; the `YYYY-MM-DD-HH.MM.SS.SSSSSS` text format N/A on Postgres — INFO) |
| DALYTRAN layout 350B (CVTRA06Y offsets) | app/cpy/CVTRA06Y.cpy:4-18 | `DailyTransactionRecord.parse` offsets 0/16/18/22/32/132/143/152/202/252/262/278/304 — field-by-field match verified | ✓ |
| COBSWAIT `ACCEPT SYSIN → MOVE to 9(8) COMP → CALL 'MVSWAIT'`; SYSIN `00003600` = 36.00 s | COBSWAIT.cbl:36-38; WAITSTEP.jcl:7-8 | `WaitStepTasklet.centiseconds` (:36-47, first-8-bytes/leading-digits/blank→0) + `Thread.sleep(cs×10ms)` (:55), default 3600 (:23) | ✓ incl. garbage-card semantics |
| CBTRN01C diagnostics: `'CARD NUMBER <n> COULD NOT BE VERIFIED. SKIPPING TRANSACTION ID-<id>'`, `'ACCOUNT <id> NOT FOUND'` | CBTRN01C.cbl:174-178 (acct), :180-184 region (card — `'CARD NUMBER '…' COULD NOT BE VERIFIED. SKIPPING TRANSACTION ID-'`) | `validateDaily` :125-131 (same literals) | ✓ literals; success-path DISPLAYs not ported (F-4) |
| CBTRN01C writes nothing | CBTRN01C.cbl (no WRITE verbs; opens TRANFILE but never writes) | processor returns diagnostic strings only | ✓ |

## 3. Stub/placeholder sweep

Searched the stream's implementation and test files (`Cbtrn01JobConfiguration`, `Cbtrn02JobConfiguration`, `WaitStepTasklet`, `WaitStepJobConfiguration`, `BatchJobService`, `BatchStreamingReaders`, `DailyTransactionRecord`, `BatchFileSupport`, `DailyPostingJobIntegrationTest`, `PostDailyParityTest`, `WaitStepTaskletTest`, `BatchJobIntegrationTest`) for: `TODO`, `FIXME`, `not implemented`, `UnsupportedOperationException`, `@Disabled`, trivially-true assertions (`assertTrue(true)`), commented-out asserts, hardcoded happy-path returns.

**Result: none found.** No disabled tests; all assertions observed are value-bearing (reject line byte offsets, balance deltas, exit descriptions).

## 4. Documented deviations vs shipped code

| Deviation (source) | Claimed | Shipped | Compliant |
|---|---|---|---|
| S14-B1 (plan §3) | documented job order `cbtrn02Job → waitStepJob`, exit codes drive ops | `BatchJobService.DAILY_POSTING_CHAIN` :65 + `docs/runbook/daily-posting-chain.md` | yes |
| S14-B2 | JPA repositories for TRANFILE/XREF/ACCT/TCATBAL | `postDaily` uses the four repositories | yes |
| S14-B3 | `dailyFile` param in; `cbtrn02-rejects.txt` 430B out, rerun-safe | Cbtrn02JobConfiguration :44-55, :68-75 (`shouldDeleteIfExists`) | yes |
| S14-B4 | `WaitStepTasklet` = `Thread.sleep(cs×10ms)`, interruption → failure | WaitStepTasklet :52-59 | yes |
| S14-B5 | exceptions → FAILED; reject count → exit description, not failure; per-chunk commits | listener :92-103; `.chunk(20, …)` :114 | yes |
| S14-B6 | close/open bracket dropped, runbook documents order | runbook table rows 1/6 | yes |
| S14-B7 | absent file → empty run RC 0 | `ByteArrayResource` fallback :50-51 + test | yes |
| S14-B8 | `cbtrn01Job` standalone orphan, not in chain | job exists; excluded from `DAILY_POSTING_CHAIN`; orphan label in class javadoc | yes |
| S14-B9 | TRANBKP eliminated | absent from code; runbook row 4 documents elimination | yes |
| PR #115: reason-109 dead end (vs FR-S14-09 text) | no reject, post continues | `BatchJobService.java:156-169` + `accountRewriteDeadEnd…` test | yes — and the PR correctly identifies the FR doc defect (F-1) |
| PR #115: dup TRAN-ID on rerun — legacy abends (VSAM 22 → CEE3ABD), target upserts | re-apply balances, overwrite tran row | `transactions.save` upsert; test `rerunDoublePostsBalancesAndOverwritesTheTranRow` | yes — disclosed in PR and runbook §"Rerun / restart semantics" |
| PR #115: post order follows code (TCATBAL→ACCT→TRANFILE), not program-FR text | code order | `postDaily` order matches CBTRN02C.cbl:440-442 | yes |
| PR #115: CBTRN01C EOF quirk (re-lookup of last record after EOF, CBTRN01C.cbl:163-183) not reproduced | each diagnostic emitted once | `DailyTransactionReader` iterates records once | yes — verified the quirk exists in source and is absent in target; disclosed as LOW in the PR |
| PR #115: chunk-level commits (per 20) vs legacy no-boundary | documented, strictly safer | `.chunk(20)` :114 | yes |

**Undocumented deviations found:** (a) `cbtrn01Job` omits CBTRN01C's success-path DISPLAYs (record dump :168-169, `'SUCCESSFUL READ OF XREF'` etc.) — target emits failure diagnostics only; LOW, folded into F-4. (b) `cbtrn01Job` does NOT have cbtrn02Job's absent-file leniency — a missing feed fails the step; this is actually *more* faithful to CBTRN01C (open error → `Z-ABEND-PROGRAM`), noted for completeness. (c) `BatchJobService.java:143` `orElseThrow` — see F-5. (d) COBOL's `'START/END OF EXECUTION OF PROGRAM CBTRN02C'` banners not emitted — trivial display-only gap.

## 5. Test evidence

Command (from `spring-boot/`):

```
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -q test \
  -Dtest='DailyPostingJobIntegrationTest,PostDailyParityTest,WaitStepTaskletTest,BatchJobIntegrationTest#dailyReadersContinueAfterBlankLine+expirationFailureOverwritesEarlierOverlimitFailure'
```

Surefire counts (per-class `Tests run` lines):

| Class | Run | Failures | Errors | Skipped |
|---|---|---|---|---|
| DailyPostingJobIntegrationTest | 13 | 0 | 0 | 0 |
| PostDailyParityTest | 3 | 0 | 0 | 0 |
| WaitStepTaskletTest | 5 | 0 | 0 | 0 |
| BatchJobIntegrationTest (2 selected methods) | 2 | 0 | 0 | 0 |
| **Total** | **23** | **0** | **0** | **0** |

`BatchJobIntegrationTest.launchesEveryCobolBatchJob` also exercises `cbtrn01Job`/`cbtrn02Job` but launches all streams' jobs, so it was left out of the scoped count.

## 6. Findings

| Sev | Title | Evidence | Recommendation |
|---|---|---|---|
| MEDIUM | F-1 — FR-S14-09 spec defect: doc claims reject `0109 'UNABLE TO UPDATE ACCOUNT'` → DALYREJS; the COBOL actually sets reason 109 with literal `'ACCOUNT RECORD NOT FOUND'` **inside the post branch after the validate/reject decision already ran**, so it is never written to DALYREJS and never counted | `S14_functional_requirement.md` FR-S14-09 row and §5 catalogue row vs `app/cbl/CBTRN02C.cbl:211-215` (branch), `:554-558` (109 + literal); correct behavior shipped in `BatchJobService.java:156-169` and PR #115's disclosure | Fix the FR doc §4/§5 0109 row (message text + "→ DALYREJS" claim) to match source; implementation needs no change |
| LOW | F-2 — FR-S14-03: no test asserts a standalone `0102 OVERLIMIT TRANSACTION` reject; the 102 branch only executes where 103 overwrites it | `BatchJobService.java:678-680` exercised via `DailyPostingJobIntegrationTest.java:257-274` but outcome never asserted; `BatchJobIntegrationTest.java:161` asserts only the *absence* of 0102 | Add a feed case: over-limit projection + unexpired account → expect `0102` reject line |
| LOW | F-3 — FR-S14-12 IO-error → step-failure path has no test, and `cbtrn02Job` lacks the `Abend999JobListener` that peer jobs (cbact01-04, cbcus01) register to surface exit code 999 | Cbtrn02JobConfiguration.java:36-39 (no `.listener` on job) vs Cbact01JobConfiguration.java:44; no FAILED-path test in `DailyPostingJobIntegrationTest` | Either register `Abend999JobListener` for parity with sibling jobs or note the omission in the runbook; add an unreadable/malformed-feed test asserting step FAILED |
| LOW | F-4 — FR-S14-16: `validateDaily`'s account-not-found diagnostic (`'ACCOUNT <id> NOT FOUND'`, CBTRN01C.cbl:174-178) is implemented but untested; CBTRN01C's success-path DISPLAYs are dropped without a documented decision | `BatchJobService.java:129-131`; test coverage only `orphanCbtrn01JobDiagnosesWithoutPosting` (card path, DailyPostingJobIntegrationTest.java:384-400) | Add an orphan-job test with a card→missing-account fixture; document the success-DISPLAY omission |
| INFO | F-5 — vanish-window nuance: if the account row disappears between `validate()`'s read and the re-read at `BatchJobService.java:143`, `orElseThrow` fails the step instead of taking the COBOL 109 dead-end; the `:161` `existsById` guard only covers the later window | `BatchJobService.java:672` vs `:143` vs `:161-169` | Acceptable as-is (narrow window, same-thread JPA); note it in the runbook if ops ever hit a FAILED posting under concurrent deletes |
| INFO | F-6 — display-only chatter not ported: `'START/END OF EXECUTION OF PROGRAM CBTRN02C'` banners (CBTRN02C.cbl:192, :233) and CBTRN01C success DISPLAYs | not present in `BatchJobService`/`Cbtrn02JobConfiguration` | No action; SYSOUT banner parity was presumably out of contract |

*UNVERIFIED items: none — every FR row was traced to code and test evidence on the audited branch.*
