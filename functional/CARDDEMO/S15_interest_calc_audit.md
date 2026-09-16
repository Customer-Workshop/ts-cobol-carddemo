# S-15 Interest Calculation — Independent Audit

- **Stream:** S15 — interest_calc (INTCALC / CBACT04C + Control-M
  `MONTHLY-InterestCalculation` chain)
- **Audited branch:** `devin/audit-s15-java` (branched from
  `devin/1789516557-carddemo-java-engagement`)
- **HEAD sha:** `410e5d0f826ccd3a6a2f4ea04eee673f64c240d8`
- **Auditor:** independent auditor (did not perform the migration)
- **Date:** 2026-09-16
- **Scope:** `functional/CARDDEMO/S15_*` docs vs `spring-boot/` implementation and
  tests vs legacy source `app/cbl/CBACT04C.cbl`, `app/cpy/`, `app/jcl/`,
  `app/scheduler/CardDemo.controlm`. Wave PR: #130 ("S-15 Interest Calc:
  INTCALC chain parity", merged 2026-09-16).

## Verdict: **PASS with findings**

All 14 FRs trace to implementation and named tests; the scoped suite is green
(31/31). No CRITICAL or HIGH findings. Findings are MEDIUM/LOW/INFO — the most
material is an undocumented behavioral deviation: the COBOL's final-account
update is unreachable dead code, so legacy never updates the last account,
while the Java port does (F-01). Two verbatim-parity deltas on the interest
transaction description padding (F-02) and the dropped DEFAULT-fallback
diagnostics (F-03) follow.

## 1. Traceability matrix

| FR | Requirement | Implementation | Test name(s) | Status |
|---|---|---|---|---|
| FR-S15-01 | TCATBALF sequential sweep in (acct,type,cat) key order | `Cbact04JobConfiguration.cbact04BalanceReader` sorts `id.acctId/id.typeCode/id.categoryCode` ASC (`Cbact04JobConfiguration.java:67-80`); `AccountInterestReader` groups rows (`BatchStreamingReaders.java:127-151`) | `InterestCalcJobIntegrationTest.sweepsTcatbalInKeyOrderUpdatingEachAccountOnce_frS1501_frS1502` | PASS |
| FR-S15-02 | Account break → previous account updated, then new acct/xref read | `AccountInterestReader.flush()` (`BatchStreamingReaders.java:145-167`) → `accounts.findById` then `BatchJobService.calculateInterest` xref read (`BatchJobService.java:208-212`); `writeInterest` commits the previous group first (`BatchJobService.java:257-264`) | `InterestCalcJobIntegrationTest.sweepsTcatbalInKeyOrderUpdatingEachAccountOnce_frS1501_frS1502`; `missingAccountAbendsAtTheGroupBoundary` (parity) | PASS |
| FR-S15-03 | DISCGRP keyed read (ACCT-GROUP-ID + type + cat) | `BatchJobService.disclosure` builds `DisclosureGroup.Id` (`BatchJobService.java:657-660`) | `InterestCalcJobIntegrationTest.groupRateMissFallsBackToDefaultGroup_frS1503_frS1504`; `InterestCalcParityTest.groupRateMissFallsBackToDefaultGroup` | PASS |
| FR-S15-04 | Status-23 miss → retry with group 'DEFAULT' | `orElseGet` fallback to `acctGroupId="DEFAULT"` (`BatchJobService.java:661-666`) | same as FR-S15-03 | PASS |
| FR-S15-05 | 'DEFAULT' also absent → error → abend | `InterestAbendException("ERROR READING DEFAULT DISCLOSURE GROUP - …")` (`BatchJobService.java:217-226`) → step FAILED → exit 999 | `InterestCalcJobIntegrationTest.missingGroupAndDefaultRateAbends_frS1505`; `InterestCalcParityTest.missingGroupAndDefaultRateAbends` | PASS |
| FR-S15-06 | `interest = (TRAN-CAT-BAL × DIS-INT-RATE) / 1200`, accumulated | `BatchJobService.java:234-237` — multiply then `divide(1200, 2, RoundingMode.DOWN)` (COBOL truncation, no ROUNDED) | `InterestCalcJobIntegrationTest.interestIsBalanceTimesRateOver1200Truncated_frS1506`; `InterestCalcParityTest.interestTruncatesAtTwoDecimalsLikeCobolCompute`, `interestAtTheDocumentedCellIs15For1000At18` | PASS |
| FR-S15-07 | DIS-INT-RATE = 0 → no txn written | `signum() == 0 → continue` (`BatchJobService.java:229-231`) | `InterestCalcJobIntegrationTest.zeroRateCategoryWritesNoTransaction_frS1507`; `InterestCalcParityTest.zeroRateCategoryWritesNoTransaction` | PASS |
| FR-S15-08 | TRAN-RECORD: id=parmDate+9(06) suffix; '01'/'05'/'System'/'Int. for a/c <acct>'; amt; card; orig=proc=now | `BatchJobService.java:238-252` + `InterestTransactionIds.next()` (`InterestTransactionIds.java:28-35`) | `InterestCalcJobIntegrationTest.interestRowsCarryParmDateIdsAndLegacyFields_frS1508`; `InterestCalcParityTest.parmDateIdsIncrementTheSuffixAcrossTheRun`, `absentParmFallsBackToTheIdGenerator` | PASS — see F-02 (acct-id padding dropped in desc) |
| FR-S15-09 | `ACCT-CURR-BAL += total`; CYC-CREDIT=0; CYC-DEBIT=0; REWRITE | `BatchJobService.writeInterest` (`BatchJobService.java:257-264`) | `InterestCalcJobIntegrationTest.balanceAndCycleResetPerAccountIncludingLastAtEof_frS1509_frS1510` | PASS |
| FR-S15-10 | Final account updated at EOF | `AccountInterestReader` flushes the last group on `exhausted` (`BatchStreamingReaders.java:128-140,153-167`) → `writeInterest` | `InterestCalcJobIntegrationTest.balanceAndCycleResetPerAccountIncludingLastAtEof_frS1509_frS1510` (acct B) | PASS vs the FR as written — but see F-01: legacy never executes this (dead code), so Java deviates from literal COBOL |
| FR-S15-11 | Any file-status ≠ 00 → DISPLAY + CEE3ABD → step fails | `InterestAbendException` propagates to step FAILED; `Abend999JobListener` sets job exit `999` (`Abend999JobListener.java:11-15`); repository failures propagate unswallowed | `InterestCalcJobIntegrationTest.missingAccountAbends_frS1511`, `missingXrefAbends_frS1505a`, `missingGroupAndDefaultRateAbends_frS1505`; `InterestCalcParityTest.repositoryFailurePropagatesAsStepFailure`, `missingAccountAbendsAtTheGroupBoundary` | PASS |
| FR-S15-12 | 1400-COMPUTE-FEES — dead stub, do not implement | No fee path exists in `calculateInterest` (`BatchJobService.java:201-254`) | `InterestCalcJobIntegrationTest.feeComputeRemainsAStubWithNoEffect_frS1512` | PASS |
| FR-S15-13 | Interest txns as normal `transactions` rows (type 01, cat 05); COMBTRAN eliminated | `transactions.saveAll` in `writeInterest` (`BatchJobService.java:263`); no merge step exists | `InterestCalcJobIntegrationTest.interestRowsInsertDirectlyAsQueryableTransactions_frS1513`; `BatchJobIntegrationTest.interestWritesDistinctTransactionPerEligibleCategory` | PASS |
| FR-S15-14 | Monthly chain order documented: interest before reopen/wait | `BatchJobService.MONTHLY_INTEREST_CHAIN = [cbact04Job, waitStepJob]` (`BatchJobService.java:75-76`) + `docs/runbook/monthly-interest-chain.md`; `WaitStepTasklet` consumed (default 3600 cs, `WaitStepTasklet.java:23`) | `InterestCalcJobIntegrationTest.monthlyChainOrderIsDocumentedAndLaunchable_frS1514` | PASS |
| (FR-S15-05a, §8) | Card-less account → INVALID KEY → abend | `findByXrefAcctId(...).stream().findFirst().orElseThrow(InterestAbendException)` (`BatchJobService.java:208-212`) | `InterestCalcJobIntegrationTest.missingXrefAbends_frS1505a`; `InterestCalcParityTest.missingXrefAbendsBeforeAnyRateLookup` | PASS |

Auxiliary contract (S15-B4): `parmDate` required, `yyyymmddhh` + plausible
month/day/hour → `JobParametersInvalidException` before run
(`Cbact04JobConfiguration.java:49-65`). Tests:
`missingParmDateRejectsBeforeRun`, `malformedParmDateRejectsBeforeRun`. PASS —
stricter than legacy (JCL PARM is never validated in CBACT04C), explicitly the
decided S15-B4 contract.

## 2. COBOL parity spot-checks

Verbatim comparisons, `app/cbl/CBACT04C.cbl` (left) vs `spring-boot/` (right):

| Item | COBOL | Java | Result |
|---|---|---|---|
| 'ACCOUNT NOT FOUND: <id>' (ACCTFILE INVALID KEY) | `:375` displays `FD-ACCT-ID` PIC 9(11) — zero-padded | `InterestAbendException("ACCOUNT NOT FOUND: " + accountId + " - ACCTFILE read status 23")` — `BatchStreamingReaders.java:160-162` | MATCH (modulo padding — F-02; extra status-23 context acceptable per S15-B7 DISPLAY→message) |
| 'ACCOUNT NOT FOUND: <id>' (XREFFIL1 INVALID KEY) | `:397` displays `FD-XREF-ACCT-ID` 9(11) | `"ACCOUNT NOT FOUND: " + acctId + " - XREFFIL1 read status 23"` — `BatchJobService.java:208-212` | MATCH (same padding note) |
| 'DISCLOSURE GROUP RECORD MISSING' / 'TRY WITH DEFAULT GROUP CODE' | `:418-419` DISPLAY on INVALID KEY, then falls through to DEFAULT retry | none — `disclosure()` falls back silently (`BatchJobService.java:661-666`) | **GAP — F-03** |
| 'ERROR READING DEFAULT DISCLOSURE GROUP' | `:455` then abend | `BatchJobService.java:221-225` (message carried in the abend) | MATCH |
| 'Int. for a/c <acct>' TRAN-DESC | `:485-489` STRING of `ACCT-ID` PIC 9(11) → `'Int. for a/c 00000000001'` (`CVACT01Y.cpy:5`) | `"Int. for a/c " + account.getAcctId()` → `'Int. for a/c 1'` (`BatchJobService.java:243`) | **DELTA — F-02** |
| Interest formula `(TRAN-CAT-BAL × DIS-INT-RATE) / 1200`, truncated (no ROUNDED) | `:464-465` COMPUTE into `WS-MONTHLY-INT` S9(09)V99 | `multiply().divide(1200, 2, RoundingMode.DOWN)` — `BatchJobService.java:234-236` | MATCH (truncation verified at the 0.1250625→0.12 cell by test) |
| Per-category truncation then accumulate | `:464-467` ADD WS-MONTHLY-INT TO WS-TOTAL-INT | `total.add(interest)` per category — `:237` | MATCH |
| DIS-INT-RATE = 0 skips compute+write | `:214-216` | `signum()==0 → continue` — `:229-231` | MATCH |
| TRAN-ID = PARM-DATE X(10) + WS-TRANID-SUFFIX 9(06) | `:473-480` STRING DELIMITED BY SIZE; `:173` | `parmDate + "%06d"` — `InterestTransactionIds.java:30-31` | MATCH |
| TRAN-TYPE-CD '01' / TRAN-CAT-CD '05' / TRAN-SOURCE 'System' | `:482-484` (cat is 9(04) → '0005') | `BatchJobService.java:240-242` (cat = Integer 5) | MATCH (numeric; '0005' vs 5 storage nuance — F-05 note) |
| Merchant id 0, name/city/zip SPACES | `:491-494` | `0L`, `"",""`, `""` — `:245-248` | MATCH semantically; SPACES vs `''` storage delta — F-05 |
| Card = XREF-CARD-NUM via AIX acct-id read | `:393-398` READ KEY IS FD-XREF-ACCT-ID (first in AIX order) | `findByXrefAcctId(...).stream().findFirst()` — `:208-212` | MATCH for 1:1 data; unordered List pick — F-04 |
| orig/proc ts = current DB2-format timestamp | `:496-498` | `LocalDateTime.now()` for both — `:250-251` | MATCH |
| Account update: bal += total, CYC-CREDIT/DEBIT = 0, REWRITE | `:350-369` | `BatchJobService.writeInterest` — `:257-264` | MATCH |
| EOF final update | `:219-221` ELSE branch — **unreachable** (PERFORM UNTIL tests EOF before each iteration) | last group flushed and updated | **DEVIATION — F-01** |
| CEE3ABD ABCODE 999 | `:628-632` | exit status `"999"` via `Abend999JobListener.java:12-14` | MATCH |
| PARM 'yyyymmddhh' → PARM-DATE X(10) | `:176-180` (never validated) | `validateParmDate` — `Cbact04JobConfiguration.java:52-65` | MATCH + stricter validation (documented, S15-B4) |
| TCATBALF seq order (acct,type,cat) | `:28-32` KSDS SEQUENTIAL | `cbact04BalanceReader` sorts ASC acct/type/cat — `:67-80` | MATCH |
| 1400-COMPUTE-FEES stub | `:518-520` 'To be implemented' | not implemented | MATCH (faithful) |
| SYSTRAN(+1) GDG → COMBTRAN merge | `INTCALC.jcl` TRANSACT DD; `COMBTRAN.jcl` | direct `transactions` insert (`BatchJobService.java:263`) | MATCH per S15-B3 decision |

## 3. Stub/placeholder sweep

Searched the stream's shipped files (`Cbact04JobConfiguration.java`,
`BatchStreamingReaders.java`, `InterestTransactionIds.java`,
`InterestAbendException.java`, `Abend999JobListener.java`, the interest block of
`BatchJobService.java:196-264`, `InterestCalcParityTest.java`,
`InterestCalcJobIntegrationTest.java`, `BatchJobIntegrationTest.java`,
`docs/runbook/monthly-interest-chain.md`, `V2201__xref_acct_idx.sql`) for:
`TODO`, `FIXME`, "not implemented", `UnsupportedOperationException`,
`@Disabled`/ignored tests, `assertTrue(true)` / commented-out assertions,
hardcoded happy-path returns.

**None found** (grep over the listed files returns no matches). The only
deliberate non-implementation is `1400-COMPUTE-FEES`, which the source itself
leaves as 'To be implemented' (`CBACT04C.cbl:518-520`) and which is covered by a
no-effect test — a faithful stub, not a gap. `cbact04Processor` is a
pass-through by design (`Cbact04JobConfiguration.java:93-97`) — the reader does
the work, matching the chunk pipeline; not a stub.

## 4. Documented deviations vs shipped code

Plan §3 boundary decisions (all "DECIDED") + wave PR #130 stated deviations:

| # | Documented decision | Shipped? | Evidence |
|---|---|---|---|
| S15-B1 | JPA repositories for TCATBALF/ACCTFILE/DISCGRP; repo errors → step FAILED | yes | `Cbact04JobConfiguration.java:67-80`; `repositoryFailurePropagatesAsStepFailure` |
| S15-B2 | `findByXrefAcctId` + index on `card_xrefs.xref_acct_id`; empty → fail step | yes | `CardXrefRepository.java:10`; `V2201__xref_acct_idx.sql`; `BatchJobService.java:208-212` |
| S15-B3 | Direct `transactions` insert; COMBTRAN eliminated | yes | `BatchJobService.java:263`; runbook §"Documented job order" row 3 |
| S15-B4 | `parmDate` required, validated; drives TRAN-ID prefix; generator fallback for non-job callers | yes | `Cbact04JobConfiguration.java:52-65`; `InterestTransactionIds.java:28-35`; tests `missingParmDateRejectsBeforeRun`, `malformedParmDateRejectsBeforeRun`, `absentParmFallsBackToTheIdGenerator` |
| S15-B5 | Documented monthly order + exit codes in runbook; additive non-idempotent rerun | yes | `BatchJobService.java:75-76`; `docs/runbook/monthly-interest-chain.md` (order table, exit-code table, rerun section) |
| S15-B6 | Consume S-14 `WaitStepTasklet` | yes | `WaitStepTasklet.java:23` (default 3600 cs = SYSIN `00003600`); chain test launches `waitStepJob` |
| S15-B7 | Exception → step FAILED → non-zero exit (legacy abend 999) | yes | `InterestAbendException`; `Abend999JobListener.java:12-14`; exit `"999"` asserted by 3 job tests |
| S15-B8 | `disclosure_groups` seeded; 'DEFAULT' fallback preserved; missing DEFAULT → failure | yes | `DataSeeder.java:125-145` reads `seed/ASCII/discgrp.txt`; `BatchJobService.java:657-666,217-226` |
| PR dev-1 | `parmDate` required (not merely defaulted) | yes | validator above |
| PR dev-2 | Chain as documented order + admin launch, not embedded tasklet | yes | `MONTHLY_INTEREST_CHAIN`; chain test posts both jobs via `/api/admin/jobs/*` |
| PR dev-3 | Same-parm rerun → same TRAN-IDs, rows merge/overwrite (documented, "strictly-safer" vs legacy) | yes | `InterestTransactionIds` restarts suffix at 1; `transactions.saveAll` merges on PK |
| STOP C | Truncation (DOWN) not HALF_UP; missing xref/acct → fail not skip; legacy TRAN-ID derivation | yes | `BatchJobService.java:234-236,208-212`; `InterestTransactionIds` |

Undocumented deltas found (none recorded in plan §3/§10 risks, wave PR, or
runbook): F-01 (last-account update — legacy dead code), F-02 (acct-id padding
in TRAN-DESC and abend text), F-03 (dropped fallback DISPLAY diagnostics —
arguably covered by S15-B7's "DISPLAY diagnostics → log" wording, which was not
fully honored), F-04 (nondeterministic card pick), F-05 (SPACES vs `''`
merchant fields; '0005' vs 5 category storage).

## 5. Test evidence

Scoped run (this stream's classes only — not the full suite):

```
cd spring-boot
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  mvn -q test -Dtest='InterestCalcParityTest,InterestCalcJobIntegrationTest,BatchJobIntegrationTest'
```

Observed (surefire):

| Class | Tests run | Failures | Errors | Skipped |
|---|---|---|---|---|
| com.carddemo.batch.InterestCalcParityTest | 10 | 0 | 0 | 0 |
| com.carddemo.InterestCalcJobIntegrationTest | 14 | 0 | 0 | 0 |
| com.carddemo.BatchJobIntegrationTest | 7 | 0 | 0 | 0 |
| **Total** | **31** | **0** | **0** | **0** |

Build result: exit 0. PR #130 additionally reports `mvn clean verify` = 641
tests 0 failures for the full suite (not re-run here — the separate audit run
covers it).

## 6. Findings

| ID | Severity | Title | Evidence | Recommendation |
|---|---|---|---|---|
| F-01 | MEDIUM | Legacy never updates the last account — the EOF `ELSE → 1050-UPDATE-ACCOUNT` is unreachable dead code; Java updates the last account (undocumented deviation; FR/analysis mis-cite the dead branch as live) | `CBACT04C.cbl:188-222`: `PERFORM UNTIL END-OF-FILE = 'Y'` tests the condition before each iteration, so once `1000-TCATBALF-GET-NEXT` sets EOF at `:340` the loop exits — the `ELSE` at `:219-221` can never fire. Legacy therefore writes the last account's interest txns (`:463-514` still run) but never posts its balance/cycle update. Java flushes the final group (`BatchStreamingReaders.java:128-140,153-167`) and updates it (`BatchJobService.java:257-264`); the behavior is asserted by `balanceAndCycleResetPerAccountIncludingLastAtEof_frS1509_frS1510`. `S15_functional_requirement.md` FR-S15-10 and `S15_interest_calc_analysis.md:65-66` describe the dead branch as live | Java's behavior is the sensible fix (and what the FRs document); do NOT regress it — record the delta as an explicit deviation in the plan/runbook ("last account updated in target; legacy skipped it due to dead code at :219-221") and correct FR-S15-10's claim |
| F-02 | MEDIUM | Acct-id zero-padding dropped in TRAN-DESC and the 'ACCOUNT NOT FOUND' abend text | `CBACT04C.cbl:485-489` STRINGs `ACCT-ID` PIC 9(11) (`CVACT01Y.cpy:5`) → `'Int. for a/c 00000000001'`; `BatchJobService.java:243` emits `'Int. for a/c ' + getAcctId()` → `'Int. for a/c 1'`. Same for the padded `FD-ACCT-ID`/`FD-XREF-ACCT-ID` displays (`:375,:397`) vs `BatchStreamingReaders.java:161`, `BatchJobService.java:211`. Job tests use 11-digit `ACCT_A/B` (`InterestCalcJobIntegrationTest.java:59-60`) so they never see the delta | `"%011d"` the acct id in both message sites, or document the unpadded form as a deviation. User-visible on S-07/S-08 transaction screens |
| F-03 | LOW | 'DISCLOSURE GROUP RECORD MISSING' / 'TRY WITH DEFAULT GROUP CODE' diagnostics dropped — no log on the DEFAULT fallback | `CBACT04C.cbl:418-419` (DISPLAY on INVALID KEY) vs silent `orElseGet` at `BatchJobService.java:661-666`; plan S15-B7 says "DISPLAY diagnostics → log" but `BatchJobService`'s logger only covers CBEXPORT (`:559-565`) | Add an info/debug log in `disclosure()` on the fallback path to preserve the ops diagnostic |
| F-04 | LOW | XREF card pick is nondeterministic for multi-card accounts | `CardXrefRepository.findByXrefAcctId` returns an unordered `List` (`CardXrefRepository.java:10`); `BatchJobService.java:208` takes `stream().findFirst()`. Legacy `READ XREF-FILE KEY IS FD-XREF-ACCT-ID` (`CBACT04C.cbl:393-398`) deterministically returns the first record in AIX order | `findTopByXrefAcctIdOrderByXrefCardNumberAsc` (or equivalent ordering) so the card picked matches legacy deterministic order when >1 card maps to an account |
| F-05 | LOW | Merchant fields stored `''` vs legacy SPACES; category stored `5` vs `'0005'` | `CBACT04C.cbl:492-494` MOVE SPACES → `BatchJobService.java:246-248` sets `""`; `TRAN-CAT-CD` PIC 9(04) (`CVTRA05Y.cpy:7`) vs `Transaction.tranCategoryCode` Integer | Cosmetic storage-level delta; either pad/format on write for byte parity or accept and document |
| F-06 | INFO | Runbook mislabels the duplicate-id failure point on same-parm reruns | `docs/runbook/monthly-interest-chain.md:68-69` says "legacy abends on the duplicate KSDS write" — but `TRANSACT-FILE` is `ORGANIZATION SEQUENTIAL` (`CBACT04C.cbl:53-55`); CBACT04C itself would write duplicate TRAN-IDs to SYSTRAN fine; the duplicate-key failure would surface downstream in COMBTRAN's REPRO into the TRANSACT KSDS | Doc nit only — adjust the runbook sentence |
| F-07 | INFO | `WS-TRANID-SUFFIX` overflow semantics differ | COBOL `9(06)` counter wraps/errors past 999999 (`CBACT04C.cbl:173,474`); Java `long` in `InterestTransactionIds.java:20` never wraps | Irrelevant at real volumes; note only |
| F-08 | INFO | Per-account atomicity window differs (documented as "chunk-transactional") | Legacy writes interest txns first and the account REWRITE at the next break — a mid-abend could persist txns without the balance update. Java commits account+txns atomically per `chunk(1)` (`Cbact04JobConfiguration.java:111`) | Strictly safer; already implied by S15-B3 "chunk-transactional" — no action |

*Every GAP/DELTA/PARTIAL row above appears in this table. Nothing marked
UNVERIFIED: all claims cite file:line on both sides.*
