# S-11 Bill Payment — Independent Audit

- **Stream:** S-11 Bill Payment (ONLINE), program COBIL00C / transaction CB00
- **Audited branch:** `devin/1789516557-carddemo-java-engagement` at HEAD `410e5d0f826ccd3a6a2f4ea04eee673f64c240d8` (checked out as `devin/audit-s11-java`)
- **Auditor:** independent auditor (did not perform the migration)
- **Date:** 2026-09-16
- **Scope:** FR-S11-01..20 traceability to implementation and named tests; verbatim COBOL parity for the message catalogue, edit rules, file/DB access semantics and exit codes; stub/placeholder sweep; documented deviations vs shipped code; scoped test run for this stream only.

## Verdict: **PASS with findings**

Every FR-S11 row is implemented and covered by at least one named test; the scoped suite (5 classes, 64 tests) is green. Findings are LOW/INFO only — no CRITICAL/HIGH, no wholly unimplemented FR.

## 1. Traceability matrix

| FR | Requirement | Implementation | Test(s) | Status |
|---|---|---|---|---|
| FR-S11-01 | Blank Acct ID → `Acct ID can NOT be empty...`, cursor Acct ID, nothing read | `BillingService.enter` (spring-boot/src/main/java/com/carddemo/service/BillingService.java:81-84) | `BillingServiceTest.blankAccountIdRejectedBeforeAnyRead_frS1101` (:97), `BillPaymentUiIntegrationTest.enterWithBlankAccountIdRedisplaysVerbatimError_frS1101` (:87) | PASS |
| FR-S11-02 | Confirm not in {Y,y,N,n,blank} → `Invalid value. Valid values are (Y/N)...`, cursor Confirm, no lookup, prior balance kept | `BillingService.enter` (:89-93), prior-balance echo via `reject` (:212-215) | `BillingServiceTest.invalidConfirmRejectedBeforeAnyRead_frS1102` (:106), `BillPaymentUiIntegrationTest.enterWithInvalidConfirmKeepsTheDisplayedBalance_frS1102` (:103) | PASS |
| FR-S11-03 | Confirm `N`/`n` → screen cleared, cursor Acct ID, no lookup, no payment | `BillingService.enter` (:85-88) → `BillPaymentScreen.blank()` (BillPaymentScreen.java:21-23) | `BillingServiceTest.declineNClearsScreenWithoutAnyRead_frS1103` (:116), `BillPaymentUiIntegrationTest.enterWithDeclineNClearsTheScreen_frS1103` (:120) | PASS |
| FR-S11-04 | No account with typed key → `Account ID NOT found...`, cursor Acct ID | `BillingService.processEnter` (:102-113), as-typed `\d{11}` mask (:41) + `findForUpdate` | `BillingServiceTest.unknownAccountReadsAsNotFound_frS1104` (:127), `.accountKeyComparedAsTyped_frS1104` (:134), `BillingIntegrationTest.unkeyedAccountIdReadsAsNotFound_frS1104` (:99) | PASS |
| FR-S11-05 | Account store error → `Unable to lookup Account...`, cursor Acct ID | `BillingService.processEnter` (:103-108) → `BILL_ACCOUNT_LOOKUP_FAILED` | `BillingServiceTest.accountStoreErrorSurfaced_frS1105` (:147) | PASS |
| FR-S11-06 | Balance shown as `+9999999999.99` (14 chars, explicit sign) | `BillingService.formatBalance` (:200-210) | `BillingServiceTest.balanceRendersSignedFourteenCharEdit_frS1106` (:156), `BillPaymentUiIntegrationTest.enterWithBlankConfirmShowsBalanceAndPrompt_frS1106_frS1108` (:138) | PASS |
| FR-S11-07 | Balance ≤ 0 → `You have nothing to pay...`, balance displayed, no payment | `BillingService.processEnter` (:116-119) | `BillingServiceTest.nonPositiveBalanceHasNothingToPay_frS1107` (:167), `BillingIntegrationTest.nothingToPayWhenBalanceNotPositive_frS1107` (:110) | PASS |
| FR-S11-08 | Confirm blank + balance > 0 → `Confirm to make a bill payment...`, cursor Confirm, nothing written | `BillingService.processEnter` (:120-123) | `BillingServiceTest.blankConfirmPromptsBeforeWriting_frS1108` (:183), `BillingIntegrationTest.blankConfirmPromptsAndWritesNothing_frS1108` (:124), UI test (:138) | PASS |
| FR-S11-09 | Confirm Y; no xref → `Account ID NOT found...`; other xref error → `Unable to lookup XREF AIX file...` | `BillingService.processEnter` (:125-136), `CardXrefRepository.findByXrefAcctId` ordered by card number (CardXrefRepository.java:9-11) | `BillingServiceTest.xrefMissReadsAsAccountNotFound_frS1109` (:193), `.xrefStoreErrorSurfaced_frS1109` (:202), `BillingIntegrationTest.missingXrefReadsAsAccountNotFound_frS1109` (:140) | PASS |
| FR-S11-10 | New id = max TRAN-ID + 1, `%016d`; empty file → `0000000000000001`; browse error → `Unable to lookup Transaction...` | `TransactionIdGenerator.nextId` (TransactionIdGenerator.java:14-22); error path `BillingService.processEnter` (:137-143) | `BillingServiceTest.emptyTransactionFileAllocatesOne_frS1110` (:212), `.transactionIdIsHighestKeyPlusOne_frS1110` (:218), `.transactionIdBrowseErrorSurfaced_frS1110` (:227), `BillingIntegrationTest.confirmedPaymentWritesAndZeroesBalance_frS1111_frS1112_frS1115` (:68) | PASS |
| FR-S11-11 | Record: `02`/`0002`/`POS TERM`/`BILL PAYMENT - ONLINE`/amount=balance/card=first/merchant `999999999`/`BILL PAYMENT`/`N/A`/`N/A`/ts=now `.000000` | `BillingService.billPayment` (:176-196) + TRAN-AMT truncation (:44-46,:144) | `BillingServiceTest.storedTransactionCarriesTheBillPaymentRecord_frS1111` (:237), `.timestampsAreInjectedClockAtSecondPrecision_frS1111` (:256), `.tranAmtDropsTheHighOrderDigitAboveOneBillion_frS1111` (:267), `BillingIntegrationTest.confirmedPaymentWritesAndZeroesBalance_...` (:68-96) | PASS |
| FR-S11-12 | Success → fields cleared, cursor Acct ID, green `Payment successful.  Your Transaction ID is <id>.` | `BillingService.processEnter` (:170-173) + `CobolMessages.billPaymentSuccess` (CobolMessages.java:463-465) + `.message-line.info` green (carddemo.css:140-142) | `BillingServiceTest.confirmedPaymentClearsFieldsAndShowsGreenSuccess_frS1112` (:281), `BillingIntegrationTest.confirmedPaymentWritesAndZeroesBalance_...` (:75-78), `BillPaymentUiIntegrationTest.confirmedPaymentZeroesBalanceAndShowsGreenSuccess_frS1112` (:153) | PASS |
| FR-S11-13 | TRAN-ID already exists → `Tran ID already exist...`, nothing persisted | `BillingService.processEnter` (:150-156) — `EntityExistsException`/`ConstraintViolationException` + `setRollbackOnly` | `BillingServiceTest.duplicateTranIdAbortsBeforeTheBalanceUpdate_frS1113` (:294), `BillingIntegrationTest.duplicateTranIdRollsBackAtomically_frS1113_d1` (:157) | PASS |
| FR-S11-14 | Other write failure → `Unable to Add Bill pay Transaction...`, nothing persisted | `BillingService.processEnter` (:157-161) + `setRollbackOnly` | `BillingServiceTest.genericWriteErrorSurfaced_frS1114` (:305) | PASS |
| FR-S11-15 | `ACCT-CURR-BAL −= TRAN-AMT` rewritten; rewrite miss → `Account ID NOT found...`, other → `Unable to Update Account...` | `BillingService.processEnter` (:162-169) — subtract + `saveAndFlush`; all update failures → `BILL_ACCOUNT_UPDATE_FAILED` | `BillingServiceTest.balanceIsRewrittenMinusThePayment_frS1115` (:315), `.accountUpdateErrorSurfaced_frS1115` (:323), `BillingIntegrationTest.confirmedPaymentWritesAndZeroesBalance_...` (:79-80) | PARTIAL — the rewrite-NOTFND → `Account ID NOT found...` outcome has no Java mapping (Finding 1; unreachable under the same-tx FOR UPDATE lock) |
| FR-S11-16 | PF3 → caller screen (main menu) | `UiController.submitBillPayment` (UiController.java:707-708) → `redirect:/menu` | `BillPaymentUiIntegrationTest.pf3ReturnsToTheMenu_frS1116` (:177) | PASS |
| FR-S11-17 | PF4 → all fields + message cleared, cursor Acct ID, no server call to business logic | `UiController.submitBillPayment` (:710-711) → `BillPaymentScreen.blank()` | `BillPaymentUiIntegrationTest.pf4ClearsFieldsAndMessage_frS1117` (:186) | PASS |
| FR-S11-18 | Other AID → `Invalid key pressed. Please see below...`, screen redisplayed unchanged | `UiController.submitBillPayment` (:713-716) → `BillPaymentScreen.preserved` (BillPaymentScreen.java:27-31) + `INVALID_KEY_PRESSED` (CobolMessages.java:10) | `BillPaymentUiIntegrationTest.unmappedFunctionKeyRedisplaysUnchanged_frS1118` (:205) | PASS (INFO Finding 4: `cursorField` returned null vs "unchanged") |
| FR-S11-19 | No session → routed to sign-on; API → 401 | `SecurityConfig` (:43-48 `anyRequest().authenticated()`; entry point :60-68 redirects UI, 401 for `/api/`) | `BillPaymentUiIntegrationTest.unsignedEntryBouncesToSignon_frS1119` (:74), `BillingIntegrationTest.unauthenticatedApiCallGets401_frS1119` (:185) | PASS |
| FR-S11-20 | `CDEMO-CB00-TRN-SELECTED` prefill → Acct ID filled + ENTER run immediately | `UiController.billPayment` (:686-693) — `?accountId=` → `billingService.enter` | `BillPaymentUiIntegrationTest.preSelectedAccountRunsEnterImmediately_frS1120` (:224) | PASS |

Supporting trace: menu option 10 → `COBIL00C` → `/bill-payment` — `MenuService` option row (:47) + `UI_ROUTES["COBIL00C"]` (:120); covered by `BillPaymentUiIntegrationTest.menuOptionTenRoutesToTheBillPayScreen` (:242) and `MenuServiceTest` option-10 route assertion (MenuServiceTest.java:110).

## 2. COBOL parity spot-checks

### Message catalogue (verbatim literals)

| Message | COBOL source | Java source | Result |
|---|---|---|---|
| `Acct ID can NOT be empty...` | app/cbl/COBIL00C.cbl:161 | CobolMessages.java:299 `BILL_ACCOUNT_EMPTY` | MATCH |
| `Invalid value. Valid values are (Y/N)...` | COBIL00C.cbl:187 | CobolMessages.java:116-117 `TRANSACTION_CONFIRM_INVALID` | MATCH |
| `Account ID NOT found...` | COBIL00C.cbl:361, :392, :425 | CobolMessages.java:33 `ACCOUNT_NOT_FOUND` | MATCH |
| `Unable to lookup Account...` | COBIL00C.cbl:368 | CobolMessages.java:300-301 `BILL_ACCOUNT_LOOKUP_FAILED` | MATCH |
| `You have nothing to pay...` | COBIL00C.cbl:201 | CobolMessages.java:31 `BILL_NOTHING_TO_PAY` | MATCH |
| `Confirm to make a bill payment...` | COBIL00C.cbl:237 | CobolMessages.java:32 `BILL_CONFIRM` | MATCH |
| `Unable to lookup XREF AIX file...` | COBIL00C.cbl:432 | CobolMessages.java:302-303 `BILL_XREF_LOOKUP_FAILED` | MATCH |
| `Transaction ID NOT found...` | COBIL00C.cbl:456 | CobolMessages.java:73 `TRANSACTION_NOT_FOUND` (retained, unreachable — A1) | MATCH |
| `Unable to lookup Transaction...` | COBIL00C.cbl:463, :492 | CobolMessages.java:118-119 `TRANSACTION_ADD_LOOKUP_FAILED` | MATCH |
| `Tran ID already exist...` | COBIL00C.cbl:536 | CobolMessages.java:120 `TRANSACTION_DUPLICATE` | MATCH |
| `Unable to Add Bill pay Transaction...` | COBIL00C.cbl:543 | CobolMessages.java:304-305 `BILL_TRANSACTION_ADD_FAILED` | MATCH |
| `Unable to Update Account...` | COBIL00C.cbl:399 | CobolMessages.java:306-307 `BILL_ACCOUNT_UPDATE_FAILED` | MATCH |
| `Payment successful.  Your Transaction ID is <id>.` (two spaces before "Your") | COBIL00C.cbl:527-530 (`'Payment successful. '` + `' Your Transaction ID is '` + TRAN-ID + `'.'`) | CobolMessages.java:463-465 `billPaymentSuccess` | MATCH |
| `Invalid key pressed. Please see below...` | app/cpy/CSMSG01Y.cpy:20-21 | CobolMessages.java:10 `INVALID_KEY_PRESSED` | MATCH |

### Edit rules and derivations

| Rule | COBOL cite | Java cite | Result |
|---|---|---|---|
| CURBAL `PIC +9999999999.99` — sign, 10 zero-filled int digits, `.`, 2 decimals (truncated) | COBIL00C.cbl:56 | `BillingService.formatBalance` (:200-210) | MATCH |
| Account key as-typed X(11) → 9(11), `"123"` ≠ `"00000000123"` | COBIL00C.cbl:170-171 | mask `\d{11}` (:41) + `Long.parseLong` + `findForUpdate` (:102-104) | MATCH (outcome-equal; non-\d{11} short-circuits before the read — documented R3) |
| `TRAN-ID` = max key + 1 as `9(16)`; ENDFILE → zeros + 1 | COBIL00C.cbl:216-217, :487-488 | `TransactionIdGenerator.nextId` (TransactionIdGenerator.java:14-22, `%016d`) | MATCH |
| `TRAN-AMT` `S9(09)V99` move — high-order digit truncated ≥ 1e9 | COBIL00C.cbl:223 | `balance.remainder(TRAN_AMT_MODULUS)` (:46, :144) | MATCH |
| New balance = old − TRAN-AMT | COBIL00C.cbl:234 | `account.setAcctCurrBal(balance.subtract(amount))` (:162) | MATCH |
| Constants `02`/`2`/`POS TERM`/`BILL PAYMENT - ONLINE`/`999999999`/`BILL PAYMENT`/`N/A`/`N/A` | COBIL00C.cbl:220-229 | `billPayment` (:178-188) | MATCH (`TRAN-CAT-CD` stored Integer 2, re-rendered `%04d` — Finding 5) |
| Timestamp `yyyy-MM-dd HH:mm:ss.000000`, MS6 zeroed | COBIL00C.cbl:249-267; app/cpy/CSDAT01Y.cpy:42-55 | `LocalDateTime.now(clock).truncatedTo(SECONDS)` (:192-194); Clock bean CardDemoApplication.java:13-16 | MATCH |
| Green success line (`DFHGREEN` to ERRMSGC) | COBIL00C.cbl:526 | `messageStyle="info"` (:172-173) → `.message-line.info{color:#0f0}` (carddemo.css:140-142); default red `:27-31` | MATCH |
| PF3 → `CDEMO-FROM-PROGRAM` default `COMEN01C` | COBIL00C.cbl:128-135 | `redirect:/menu` (UiController.java:707-708; `/menu` is COMEN01C's surface, :140-144) | MATCH |
| PF4 → CLEAR-CURRENT-SCREEN / INITIALIZE-ALL-FIELDS | COBIL00C.cbl:136-137, :552-566 | `BillPaymentScreen.blank()` (:710-711; BillPaymentScreen.java:21-23) | MATCH |
| Other AID → `CCDA-MSG-INVALID-KEY` redisplay | COBIL00C.cbl:138-141 | `BillPaymentScreen.preserved` + `INVALID_KEY_PRESSED` (:713-716) | MATCH |
| `EIBCALEN = 0` → XCTL `COSGN00C` | COBIL00C.cbl:107-109 | SecurityConfig entry point (:60-68): UI → `/signon`, `/api/` → 401 | MATCH |
| READ ACCTDAT UPDATE + REWRITE | COBIL00C.cbl:343-354, :379-385 | `findForUpdate` `@Lock(PESSIMISTIC_WRITE)` (AccountRepository.java:23-25) + `saveAndFlush` (:163-164) inside one `TransactionTemplate` (:94) | MATCH |
| READ CXACAIX by `XREF-ACCT-ID`, first card | COBIL00C.cbl:408-418 | `findByXrefAcctId` `ORDER BY xrefCardNumber` + `findFirst` (CardXrefRepository.java:9-11; BillingService.java:127-128) | MATCH |
| STARTBR HIGH-VALUES / READPREV / ENDBR for max key | COBIL00C.cbl:212-215, :441-505 | `findTopByOrderByTranIdDesc` (TransactionIdGenerator.java:16) | MATCH (digit `tran_id` varchar order = numeric; documented in plan §4) |
| WRITE TRANSACT DUPKEY/DUPREC → duplicate message | COBIL00C.cbl:510-547 | `entityManager.persist` + `flush` (:150-161): `EntityExistsException`/`ConstraintViolationException` → duplicate; other → add-failed | MATCH (PR documents why `persist` not `saveAndFlush` — merge would hide DUPREC) |

### BMS surface parity

`app/bms/COBIL00.bms:57-59` label `Enter Acct ID:` → templates/bill-payment.html:17; `:75-77` `Your current balance is:` → :24; `:108-113` `Do you want to pay your balance now. Please confirm:` + `(Y/N)` literal → :29-33; `ACTIDIN` LEN 11 (:60-64) → `maxlength="11"` (:18); `CONFIRM` LEN 1 (:115-119) → `maxlength="1"` (:30); `CURBAL` LEN 14 ASKIP (:78-81) → read-only span + hidden FSET echo (:25-26); `ERRMSG` LEN 78 RED BRT (:126-130) → `.message-line` red default (carddemo.css:27-31); footer `ENTER=Continue  F3=Back  F4=Clear` (:131-136) → :35,37-39; header `CB00`/`COBIL00C`/`mm/dd/yy`/`hh:mm:ss` (:9-49, POPULATE-HEADER-INFO :321-338) → layout attrs (:3) + `UiController.addHeaderFields` (:92-97, `MM/dd/yy`, `HH:mm:ss`).

### Exit/abend codes

CICS RESP outcomes all map: NORMAL→continue, NOTFND→`Account ID NOT found...`, other→`Unable to lookup ...`, ENDFILE→zeros, DUPKEY/DUPREC→`Tran ID already exist...`, WRITE/REWRITE other→`Unable to Add/Update...` (see tables above; BillingService.java:102-173). REWRITE-NOTFND is the only RESP outcome without a Java mapping (Finding 1). XCTL exits: `COSGN00C` (EIBCALEN=0) → session guard; `COMEN01C` (PF3 default) → `/menu`. No ABEND/exit-code constants exist in this program beyond the message paths above.

## 3. Stub/placeholder sweep

Searched in the stream's implementation and test files (`BillingService.java`, `BillingController.java`, `BillPaymentRequest.java`, `BillPaymentScreen.java`, `UiController.java` bill-payment handlers, `CobolMessages.java` S-11 block, `bill-payment.html`, `BillingServiceTest.java`, `BillingIntegrationTest.java`, `BillPaymentUiIntegrationTest.java`):

- `TODO`, `FIXME`, `not implemented`, `UnsupportedOperationException`, `@Disabled`, `@Ignore` → **none found**.
- Commented-out assertions (`//`/`/*` preceding `assert`) and trivially-true assertions (`assertTrue(true)`, `assertThat(true)`) → **none found**.
- Hardcoded happy-path returns → none; every service path reads/writes real repositories; `BillPaymentScreen.blank()`/`preserved` are screen-state constructors, not stubs.
- Note (not a stub): `MenuService` option 10 marked `implemented` (:47) and routed to `/bill-payment` (:120) — flag flip from plan §1/§6 shipped.

## 4. Documented deviations vs shipped code

| Deviation | Source | Shipped? | Evidence |
|---|---|---|---|
| D1 — post-confirm steps block at first failure; insert + balance rewrite atomic (source falls through) | FR doc §11; plan S11-B2 | yes | `TransactionTemplate` + `status.setRollbackOnly()` (BillingService.java:94-95,154,158,166); `BillingIntegrationTest.duplicateTranIdRollsBackAtomically_frS1113_d1` verifies nothing persisted |
| A1 — `Transaction ID NOT found...` unreachable; constant retained | FR doc §11 | yes | `CobolMessages.java:73` `TRANSACTION_NOT_FOUND`; no Java condition maps to it (same as legacy) |
| A2 — PF3 returns to `/menu` | FR doc §11 | yes | UiController.java:707-708 |
| A3 — `CDEMO-CB00-TRN-SELECTED` exposed as `?accountId=` | FR doc §11 | yes | UiController.java:686-693 |
| A4 — timestamps use server local clock, second precision | FR doc §11 | yes | `Clock.systemDefaultZone()` bean (CardDemoApplication.java:13-16); `truncatedTo(SECONDS)` (BillingService.java:192) |
| S11-B1 — 23505 → `Tran ID already exist...`; other → `Unable to Add Bill pay Transaction...` | plan §3 | yes | BillingService.java:150-161 |
| S11-B2 — `SELECT ... FOR UPDATE` + atomic rewrite | plan §3 | yes | AccountRepository.java:23-25 (`@Lock(PESSIMISTIC_WRITE)` `findForUpdate`); lock inside the service transaction |
| S11-B3 — id = max+1 `%016d`; collision → B1 duplicate | plan §3 | yes | TransactionIdGenerator.java:14-22; collision path exercised by `duplicateTranIdRollsBackAtomically_frS1113_d1` |
| S11-B4 — PF3 → `/menu` | plan §3 | yes | UiController.java:707 |
| S11-B5 — `?accountId=` prefill + immediate ENTER | plan §3 | yes | UiController.java:686-693 |
| S11-B6 — injected `java.time.Clock`, `.000000` micros | plan §3 | yes | CardDemoApplication.java:13-16; BillingService.java:52,192-194 |
| `BillPaymentResponse` → `BillPaymentScreen`; API rejects return HTTP 200 with the message | wave PR #122 | yes | BillPaymentScreen.java:10-17; BillingController.java:15-18 |
| `EntityManager.persist` + `flush` instead of `saveAndFlush` (true INSERT so DUPREC surfaces) | wave PR #122 | yes | BillingService.java:150-153 |
| Integration tests on per-class H2 instead of Testcontainers Postgres (sibling-stream convention) | wave PR #122 | yes | BillingIntegrationTest.java:36-41 + javadoc :30-32 |
| `ApiIntegrationTest`/`MenuServiceTest` baseline assertions updated for new contract | wave PR #122 | yes | ApiIntegrationTest.java:254-258; MenuServiceTest.java:110 (both pass) |

Undocumented deviations found: none beyond the findings in §6 (all are LOW/INFO edge deltas, listed there).

## 5. Test evidence

Command (repo root → spring-boot):

```
cd spring-boot && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  mvn -q test -Dtest='BillingServiceTest,BillingIntegrationTest,BillPaymentUiIntegrationTest,ApiIntegrationTest,MenuServiceTest'
```

Observed JUnit counts (surefire reports, this run):

| Class | Run | Failures | Errors | Skipped |
|---|---|---|---|---|
| com.carddemo.service.BillingServiceTest | 22 | 0 | 0 | 0 |
| com.carddemo.BillingIntegrationTest | 7 | 0 | 0 | 0 |
| com.carddemo.BillPaymentUiIntegrationTest | 12 | 0 | 0 | 0 |
| com.carddemo.ApiIntegrationTest (shared API surface, contains /api/billing checks) | 18 | 0 | 0 | 0 |
| com.carddemo.service.MenuServiceTest (shared, contains option-10 route check) | 5 | 0 | 0 | 0 |
| **Total** | **64** | **0** | **0** | **0** |

Full-suite run intentionally skipped per audit scope (covered by a separate audit run).

## 6. Findings

| # | Severity | Title | Evidence | Recommendation |
|---|---|---|---|---|
| 1 | LOW | FR-S11-15 rewrite-NOTFND → `Account ID NOT found...` outcome unmapped | COBIL00C.cbl:390-395 maps REWRITE NOTFND to `Account ID NOT found...`; BillingService.java:163-169 maps every update failure to `BILL_ACCOUNT_UPDATE_FAILED` ("Unable to Update Account...") | Catch the entity-missing outcome (`EntityNotFoundException`/`ObjectOptimisticLockingFailureException`) → `ACCOUNT_NOT_FOUND`, or document the outcome as unreachable like A1 (row is held under FOR UPDATE in the same transaction) |
| 2 | LOW | Non-numeric stored `tran_id` max key → uncaught `NumberFormatException` (HTTP 500) instead of `Unable to lookup Transaction...` | TransactionIdGenerator.java:21 `Long.parseLong(currentId)` uncaught; BillingService.java:138-143 catches only `DataAccessException`. COBIL00C.cbl:216-217 has no abend path — garbage digits degrade into arithmetic, not a 500 | Catch the parse failure in the generator (or the service) and surface `TRANSACTION_ADD_LOOKUP_FAILED` |
| 3 | INFO | `accountId` longer than 11 chars → `Account ID NOT found...`; legacy `X(16)` → `X(11)` move truncates and can match on the first 11 bytes | `\d{11}` mask, BillingService.java:41,102 vs COBIL00C.cbl:118-119 (`CDEMO-CB00-TRN-SELECTED` X(16) → `ACTIDINI` X(11)). Reachable only via direct URL/API input (UI `maxlength="11"`); prefill path documented unused (A3) | Acceptable; document the bound, or truncate to 11 before the mask for byte-parity on the prefill path |
| 4 | INFO | Invalid-AID redisplay drops cursor state (`cursorField` = null → no focus target); legacy leaves the cursor unchanged | BillPaymentScreen.java:27-31 `preserved` returns `cursorField=null`; catalogue row FR-S11-18 / CSMSG01Y.cpy:20-21 leaves cursor "unchanged" | Cosmetic — pass the prior cursor field through (or default `accountId`) if strict parity of cursor placement is wanted |
| 5 | INFO | `TRAN-CAT-CD` persisted as `Integer 2`, not the `9(04)` byte form `0002` | BillingService.java:180 `setTranCategoryCode(2)`; Transaction.java:15 `Integer tranCategoryCode`; COBIL00C.cbl:221 `MOVE 2 TO TRAN-CAT-CD` (PIC 9(04), CVTRA05Y.cpy) | V2-baseline column decision; read surfaces re-render `%04d` (TransactionService.java:144) so observable parity holds — noted, no action needed |
