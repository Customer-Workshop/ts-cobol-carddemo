# S-16 Statement Generation — Independent Audit

- **Stream:** S16 — Statement Generation (CREASTMT chain; CBSTM03A + CBSTM03B)
- **Audited branch:** `devin/audit-s16-java`, cut from
  `devin/1789516557-carddemo-java-engagement` @ `410e5d0f826ccd3a6a2f4ea04eee673f64c240d8`
- **Auditor:** independent auditor (did not perform the migration)
- **Date:** 2026-09-16
- **Scope:** FR-S16-01..15 traceability to `spring-boot/` implementation and named
  tests; COBOL parity spot-checks vs `app/cbl/CBSTM03A.CBL`, `app/cbl/CBSTM03B.CBL`,
  `app/cpy/COSTM01.CPY`, `app/cpy/CUSTREC.cpy`, `app/cpy/CVACT01Y.cpy`,
  `app/cpy/CVACT03Y.cpy`, `app/jcl/CREASTMT.JCL`; stub/placeholder sweep;
  documented-deviation compliance vs wave PR #129; scoped JUnit run.

## Verdict: **PASS with findings**

All 15 FRs are implemented and covered by named tests; the scoped run is green
(16 tests, 0 failures). Findings are LOW/INFO only: four factual errors in the
analysis/FR docs about the shipped JCL, a fidelity gap in the ported error
messages, one dead repository read, and a half-verified cap-removal test.

## 1. Traceability matrix

| FR | Requirement | Implementation | Test name(s) | Status |
|---|---|---|---|---|
| FR-S16-01 | All transactions read keyed (card, txn order) | `BatchJobService.statementFor` → `TransactionRepository.findByTranCardNumberOrderByTranIdAsc` (`BatchJobService.java:282-283`; `TransactionRepository.java:9`). Preload mechanism eliminated per S16-B2 (documented) | `StatementParityTest.frS16_07_08_13_detailLinesTotalAndCardIdSortOrder` | PASS |
| FR-S16-02 | One statement per XREFFILE row, zero-txn cards included | `Cbstm03JobConfiguration.cbstm03Reader` sweeps `card_xrefs` (`Cbstm03JobConfiguration.java:36-42`); legacy driver loop `CBSTM03A.CBL:317-330` | `StatementParityTest.frS16_01_02_10_oneStatementPerXrefRowInCardOrderIncludingZeroTxnCards` | PASS |
| FR-S16-03 | CUSTFILE keyed by xref cust-id; ACCTFILE keyed by xref acct-id | `BatchJobService.statementFor` `customers.findById(xref.getXrefCustId())` / `accounts.findById(xref.getXrefAcctId())` (`BatchJobService.java:277-280`); legacy `CBSTM03A.CBL:319-322`, keys `CVACT03Y.cpy:6-7` | `frS16_04_05_06_psHeaderBlockMatchesStLineLayoutsByteForByte` (name/addr/FICO asserted from cust/acct rows) + `frS16_12_missingCustomerRecordFailsTheStep` | PASS |
| FR-S16-04 | ST-LINE0 asterisk 'START OF STATEMENT'; name + addr lines | `statementPlain` `:302-309` vs `CBSTM03A.CBL:86-100`, writes `:458-491` | `frS16_04_05_06_…` (asserts all 5 lines byte-for-byte) | PASS |
| FR-S16-05 | 'Basic Details', 'Account ID :', 'Current Balance :' `9(9).99-`, 'FICO Score :' | `statementPlain` `:310-318` vs `CBSTM03A.CBL:103-119`; `CobolFieldFormatter.trailingSign(bal,9,2,false)` (`CobolFieldFormatter.java:54-75`) | `frS16_04_05_06_…` | PASS |
| FR-S16-06 | 'TRANSACTION SUMMARY' + 'Tran ID/Tran Details/Tran Amount' column line | `statementPlain` `:320-325` vs `CBSTM03A.CBL:122-131` | `frS16_04_05_06_…` | PASS |
| FR-S16-07 | ST-LINE14 `id X(16) \| details X(49) \| $ Z(9).99-` | `statementPlain` `:328-334` vs `CBSTM03A.CBL:132-137`, `6000-WRITE-TRANS :675-679` | `frS16_07_08_13_…` | PASS |
| FR-S16-08 | 'Total EXP:$Z(9).99-' + 'END OF STATEMENT' frame | `statementPlain` `:336-340` vs `CBSTM03A.CBL:138-146`, total accumulate `CBSTM03A.CBL:429` + writes `:433-437` | `frS16_07_08_13_…`, `frS16_01_02_10_…` (zero total) | PASS |
| FR-S16-09 | HTML DOCTYPE/table skeleton + bank literals + Basic Details + Transaction Summary + 'End of Statement' | `statementHtml` `:353-452` vs `CBSTM03A.CBL:148-233`, writes `:506-721`, `:439-454` | `frS16_09_htmlBlockMatchesTheLiteralSkeleton`, `frS16_09_htmlDetailRowsUseTheF2f2f2Columns`, `BatchJobIntegrationTest.statementHtmlEscapesFreeTextFields` | PASS |
| FR-S16-10 | Statements in XREFFILE order; txns in TRXFL (card,id) order | Reader sorts `xrefCardNumber` ASC (`Cbstm03JobConfiguration.java:37-41`) = KSDS key order; per-card `OrderByTranIdAsc` | `frS16_01_02_10_…` (file-order assertion), `frS16_07_08_13_…` | PASS |
| FR-S16-11 | 51-card/10-txn caps dropped — target streams all rows | No cap in target (per-card `List<Transaction>` query); defect intentionally not preserved | `frS16_11_streamsMoreThan51CardsWithoutLoss` | PASS (see F-5: >10 txns/card untested) |
| FR-S16-12 | File-op RC ≠ 00/04 → DISPLAY + CEE3ABD → step fails | `orElseThrow(IllegalStateException)` in `statementFor` (`:277-280`) → step FAILED | `frS16_12_missingCustomerRecordFailsTheStep` | PASS (see F-3: message text not verbatim) |
| FR-S16-13 | DELDEF01+SORT+REPRO+delete produce sorted TRXFL | Eliminated per S16-B2 — replaced by repository query sorted card+id | `frS16_07_08_13_…` (scrambled inserts emitted 101,105,109) | PASS |
| FR-S16-14 | CREASTMT before TXT2PDF1 + wait + reopen; documented order | `BatchJobService.STATEMENT_CHAIN = [cbstm03Job, waitStepJob]` (`BatchJobService.java:88`); `WaitStepJobConfiguration.java:24`; admin launch `BatchAdminController.java:10` | `frS16_14_chainOrderIsDocumentedAndLaunchableThroughTheAdminEndpoint` | PASS |
| FR-S16-15 | `PARM='12'` ignored — no observable effect | No parm consumed by `cbstm03Job` (no `JobParameter` read anywhere in the step) | `frS16_15_parmHasNoEffectAndRerunOverwritesOutputs` | PASS (see F-1: the JCL actually carries no PARM) |

No GAP rows. FR-S16-03 has no dedicated test name but is proven by the
header-block assertions (customer/account fields resolve through the xref keys)
plus the missing-customer failure test — recorded as PASS, not PARTIAL, because
the observable result is fully asserted.

## 2. COBOL parity spot-checks

All `spring-boot` paths below are under
`spring-boot/src/main/java/com/carddemo/`; COBOL paths are repo-relative.

| Item | COBOL evidence | Java evidence | Result |
|---|---|---|---|
| ST-LINE0 `ALL '*'`(31) + 'START OF STATEMENT'(18) + `ALL '*'`(31) = 80 | `CBSTM03A.CBL:86-89` | `"*".repeat(31)+"START OF STATEMENT"+"*".repeat(31)` `BatchJobService.java:302` | identical bytes |
| ST-NAME = STRING first/middle/last `DELIMITED BY ' '` (first word each, single-space joined) | `CBSTM03A.CBL:462-469` | `firstWord()`×3 joined `BatchJobService.java:775-779,791-795` | identical, incl. empty-middle double space |
| ST-ADD1/ADD2 = full X(50) MOVE; ST-ADD3 = STRING addr3/state/country/zip `DELIMITED BY ' '` | `CBSTM03A.CBL:470-481`; `CUSTREC.cpy:11-14` | `pad(addr1,50)`/`pad(addr2,50)`/`statementAddress3()` `:306-309,783-788` | identical |
| 'Account ID         :' + X(20) + 40 spaces; `MOVE ACCT-ID` (9(11) → X(20) left-justified) | `CBSTM03A.CBL:107-110,483`; `CVACT01Y.cpy:5` | `pad("%011d",20)` `:298,314` | identical |
| 'Current Balance    :' + `9(9).99-` + 47 spaces; source `ACCT-CURR-BAL S9(10)V99` truncates high digit on move | `CBSTM03A.CBL:111-115,484`; `CVACT01Y.cpy:7` | `trailingSign(bal,9,2,false)+" ".repeat(47)` `:315-317`; low-digit truncation `CobolFieldFormatter.java:61-63` | identical |
| 'FICO Score         :' + X(20); `MOVE CUST-FICO-CREDIT-SCORE` (9(03)) | `CBSTM03A.CBL:116-119,485`; `CUSTREC.cpy:22` | `pad("%03d",20)` `:299-300,318` | identical |
| 'Basic Details' centered 33+X(14)+33; 'TRANSACTION SUMMARY ' 30+X(20)+30 | `CBSTM03A.CBL:103-106,122-125` | `:311-312,320-321` | identical |
| Column header `'Tran ID         '`(16) + `'Tran Details    '…`(51) + `'  Tran Amount'`(13) | `CBSTM03A.CBL:128-131` | `pad("Tran ID",16)+pad("Tran Details",51)+"  Tran Amount"` `:323-325` | identical |
| ST-LINE14 detail `X(16)+' '+X(49)+'$'+Z(9).99-` (TRNX-DESC X(100)→49 truncate; TRNX-AMT S9(09)V99) | `CBSTM03A.CBL:132-137,676-679`; `COSTM01.CPY:28-29` | `pad(id,16)+' '+pad(desc,49)+'$'+trailingSign(amt,9,2,true)` `:329-332` | identical |
| ST-LINE14A `'Total EXP:'`(10)+56 spaces+'$'+Z(9).99- | `CBSTM03A.CBL:138-142,433-436` | `:336-338` | identical |
| ST-LINE15 `'*'`(32)+'END OF STATEMENT'(16)+`'*'`(32) | `CBSTM03A.CBL:143-146,437` | `:339` | identical |
| HTML skeleton record order L01..L80 incl. `<h3>Statement for Account Number: `+L11-ACCT X(20)+`</h3>` | `CBSTM03A.CBL:148-216`, writes `:506-534` | `:360-373` | identical order + literals |
| Bank literals 'Bank of XYZ' / '410 Terry Ave N' / 'Seattle WA 99999' | `CBSTM03A.CBL:167-172` (L16-L18) | `:376-378` | verbatim |
| HTML name/addr lines `'<p…>'+field DELIMITED BY '  '+'  '+'</p>'` | `CBSTM03A.CBL:560-592` | `htmlField(pad(f,w))+"  </p>"` `:383-391,800-803` | identical semantics |
| HTML Basic Details `'<p>Account ID         : '`+X(20) / `Current Balance`/`FICO Score` | `CBSTM03A.CBL:613-633` | `:401-403` | identical |
| HTML detail rows L58/L61/L64 `<p>`+X(16)/X(49)/Z(9).99-+`</p>` | `CBSTM03A.CBL:681-721` | `:422-435` | identical |
| HTML trailer LTRS+L10+`<h3>End of Statement</h3>`+LTDE+LTRE+L78+L79+L80 | `CBSTM03A.CBL:439-454` | `:437-445` | identical |
| Statement driver = xref sweep; keyed cust/acct reads precede output | `CBSTM03A.CBL:317-330` | reader + `statementFor` `:276-285` | equivalent |
| Transaction order within card = TRXFL (card,tran-id) via `SORT FIELDS=(263,16,CH,A,1,16,CH,A)` | `CREASTMT.JCL:53` + `CBSTM03A.CBL:416-432` | `findByTranCardNumberOrderByTranIdAsc` `TransactionRepository.java:9` | equivalent |
| Error catalogue 'ERROR READING CUSTFILE'/'ACCTFILE' + RC → CEE3ABD | `CBSTM03A.CBL:382-386,406-410,921-923` | `IllegalStateException` `:277-280` → step FAILED | equivalent outcome; message text not verbatim (F-3) |
| 'Running JCL : …' + 'DD Names from TIOT:' diagnostics | `CBSTM03A.CBL:265-288` | not ported (demoted mechanics, FR §7) | acceptable |
| PARM ignored — `PROCEDURE DIVISION` has no USING PARM | `CBSTM03A.CBL:262` | no `JobParameter` consumed | identical (see F-1: no PARM exists in JCL) |
| Output files `STATEMNT.PS` LRECL 80 / `STATEMNT.HTML` LRECL 100, `DISP=(NEW,…)` overwrite | `CREASTMT.JCL:87-96` | `service.output("STATEMNT.PS"/"STATEMNT.HTML")` + `shouldDeleteIfExists` `Cbstm03JobConfiguration.java:52-71` | equivalent |

## 3. Stub/placeholder sweep

Searched `Cbstm03JobConfiguration.java`, `BatchJobService.java`,
`BatchStreamingWriters.java`, `CobolFieldFormatter.java`,
`StatementParityTest.java`, `BatchJobIntegrationTest.java` for:
`TODO`, `FIXME`, "not implemented", `UnsupportedOperationException`,
`@Disabled`/`@Ignore`, `assertTrue(true)`, `assertDoesNotThrow`,
commented-out assertions — **none found**. All 9 `StatementParityTest`
methods and all 7 `BatchJobIntegrationTest` methods contain real assertions
(51 `assert*` calls in `StatementParityTest` alone); no happy-path-only or
trivially-true assertions observed — every test asserts concrete expected bytes
hand-derived from the COBOL layouts, not values echoed from the implementation.
`CardStatement.card` is fetched but never consumed (`BatchJobService.java:281`)
— a dead repository read, not a stub (F-4).

## 4. Documented deviations vs shipped code

| Deviation (plan S16-Bx / wave PR #129) | Decision | Shipped code | Compliant? |
|---|---|---|---|
| S16-B1 VSAM → repositories | `CardXref/Customer/Account/Transaction` repositories | `statementFor` uses `customers`/`accounts`/`transactions` repos `:276-284` | yes |
| S16-B2 dataset chain → sorted repository read; TRXFL eliminated | sorted read replaces SORT→REPRO→KSDS | `findByTranCardNumberOrderByTranIdAsc` + sorted `cbstm03Reader` | yes |
| S16-B3 STATEMNT.PS + STATEMNT.HTML flat files, rerun overwrite | `DualStatementWriter`, `shouldDeleteIfExists` | `Cbstm03JobConfiguration.java:52-71`; verified by `frS16_15` | yes |
| S16-B4 no JobParameter (PARM vestigial) | none required | job runs with arbitrary params, output identical | yes |
| S16-B5 documented statement job order + exit codes | `STATEMENT_CHAIN` + admin launch | `BatchJobService.java:78-88` | yes |
| S16-B6 skip PDF (TXT2PDF1 external); PS+HTML parity is the contract | no PDF writer | none exists | yes |
| S16-B7 consume S-14 `WaitStepTasklet` | `waitStepJob` in chain | `WaitStepJobConfiguration.java:24`; launched in `frS16_14` | yes |
| S16-B8 exception → step FAILED (DISPLAY → log) | `orElseThrow` | `:277-280`; verified by `frS16_12` | yes |
| S16-B9 CBSTM03B not ported | repositories stand in | no CBSTM03B class exists | yes |
| Wave PR: HTML values escaped after fixed-width padding; >100-byte escaped lines left unpadded rather than entity-truncated | documented in PR #129 | `escapeHtml` `BatchJobService.java:758-763`, emit rule `:449` | yes — shipped as documented |

No undocumented deviations found in the shipped stream code. Note: the wave PR
documented the HTML escaping; the migration plan/FR docs do not mention it —
code-comment and PR documentation only.

## 5. Test evidence

```
cd spring-boot
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  mvn -q test -Dtest='StatementParityTest,BatchJobIntegrationTest'
```

Observed (surefire `target/surefire-reports/`):

| Test class | Tests run | Failures | Errors | Skipped |
|---|---|---|---|---|
| `com.carddemo.StatementParityTest` | 9 | 0 | 0 | 0 |
| `com.carddemo.BatchJobIntegrationTest` | 7 | 0 | 0 | 0 |
| **Total** | **16** | **0** | **0** | **0** |

Also launches `cbstm03Job` end-to-end and asserts `STATEMNT.PS`/`STATEMNT.HTML`
existence inside `BatchJobIntegrationTest.launchesEveryCobolBatchJob`.

## 6. Findings

| Sev | Title | Evidence | Recommendation |
|---|---|---|---|
| LOW | **F-1** Analysis/FR docs claim `PARM='12'` on STEP040 — the shipped JCL has no PARM at all | `app/jcl/CREASTMT.JCL:79` reads `EXEC PGM=CBSTM03A,COND=(0,NE)` (no PARM); both zip copies identical (`samples/m2/mf/CardDemo_runtime.zip` `catalog/jcl/CREASTMT.JCL:79`, `samples/m2/unikix/…/migrated_app/jcl/CREASTMT:85`). Claims at `S16_statements_analysis.md:23,41-43`, `S16_functional_requirement.md:21,31,52` | Correct the docs: the parm is absent, not merely vestigial (the "never read" conclusion still holds — `CBSTM03A.CBL:262` has no USING PARM). Code unaffected |
| LOW | **F-2** Analysis claims SORT input `TRANSACT.BKUP(+1)` with an INCLUDE `TRAN-PROC-TS < current` filter and a "330B" recut — actual JCL reads `TRANSACT.VSAM.KSDS`, has no INCLUDE/OMIT, and `OUTREC` produces 328 bytes | `CREASTMT.JCL:45` (`SORTIN TRANSACT.VSAM.KSDS`), `:53-54` (no INCLUDE; `1:263,16,17:1,262,279:279,50` = 16+262+50 = 328); claims at `S16_statements_analysis.md:20-22,58`, `S16_functional_requirement.md:28` | Fix doc claims. No code impact — the target includes all transactions for the card, matching actual JCL behavior |
| LOW | **F-3** Ported error message is not verbatim: COBOL emits `DISPLAY 'ERROR READING CUSTFILE'` + `DISPLAY 'RETURN CODE: ' <file-status>` + `DISPLAY 'ABENDING PROGRAM'` then CEE3ABD; Java throws a single `IllegalStateException("ERROR READING CUSTFILE RC: cust <id>")` — file-status RC absent, `RETURN CODE:`/`ABENDING PROGRAM` lines not logged | `CBSTM03A.CBL:383-385,407-409,921-923` vs `BatchJobService.java:277-280` | Keep the step-failure semantics; if message parity matters for ops tooling, emit the RC and 'ABENDING PROGRAM' lines in the exception/log per S16-B8's "DISPLAY → log" contract |
| LOW | **F-4** `statementFor` performs a `cards.findById()` read whose result (`CardStatement.card`) is never consumed — an extra keyed read CBSTM03A never does | `BatchJobService.java:281`; neither `statementPlain` (`:295-341`) nor `statementHtml` (`:353-452`) calls `statement.card()` | Drop the read and the record component, or document why it is fetched |
| INFO | **F-5** FR-S16-11 covers two dropped caps; the test exercises only the >51-card cap. >10 transactions per card is covered by construction (per-card `List`, no array) but has no explicit test | `StatementParityTest.java:413-427` (adds 55 cards, no card has >10 txns) | Add one assertion or a card with >10 transactions to close the coverage half |
| INFO | **F-6** `CREASTMT.JCL:90` contains a corrupted DD line (`SPACE=(CYL,(1,1),RLSE), 00,RECFM=FB), ATA.VSAM.KSDS`) — legacy source defect, not introduced by the migration; harmless to the audit since target recreates the outputs | `app/jcl/CREASTMT.JCL:87-91` | Record as legacy defect; no target action |
