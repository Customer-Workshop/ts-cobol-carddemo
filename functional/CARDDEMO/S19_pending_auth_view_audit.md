# S-19 Pending Authorization View — Independent Audit

- **Stream**: S-19 (pending_auth_view) — COPAUS0C list / COPAUS1C detail+fraud toggle / COPAUS2C AUTHFRDS journal
- **Audited branch**: `devin/audit-s19-java` (checkout of `devin/1789516557-carddemo-java-engagement`)
- **HEAD sha**: `410e5d0f826ccd3a6a2f4ea04eee673f64c240d8` (S-20 wave merge; S-19 shipped earlier in `f0abd1a`, PR #125)
- **Auditor**: independent auditor (did not perform the migration)
- **Date**: 2026-09-16
- **Scope**: `functional/CARDDEMO/S19_functional_requirement.md`, `S19_pending_auth_view_analysis.md`, `S19_pending_auth_view_migration_plan.md`; Java port under `spring-boot/` vs. `app/app-authorization-ims-db2-mq/{cbl,cpy,bms,ddl}` and `app/cpy/`.

## Verdict: **FAIL**

One HIGH finding: the list screen's `Credit Lim:`/`Cash Lim:` fields are sourced from `pending_auth_summary` (the IMS snapshot) instead of the ACCTDAT account record the COBOL populates them from — the shipped fixture itself renders different numbers than the COBOL would, and the parity test asserts the wrong-source value. All 12 FRs are implemented and tested (46/46 scoped tests green), so the failure is a data-fidelity defect, not missing function.

## 1. Traceability matrix

| FR | Requirement | Implementation | Test(s) | Status |
|---|---|---|---|---|
| FR-S19-01 | Menu opt 11 routes to pending-auth screen | `MenuService.MAIN` option 11 `implemented=true` (`MenuService.java:48`), `UI_ROUTES` `COPAUS0C→/ui/pending-auth` (`:122`) | `PendingAuthFrParityTest.menuOption11IsImplementedAndRoutesToScreen_frS1901`, `MenuUiIntegrationTest.pendingAuthorizationRoutesToScreen_frS0114`, `MenuServiceTest` (`:111-112`) | PASS |
| FR-S19-02 | ENTER w/ acct id → context + summary + ≤5 rows | `PendingAuthService.browse/gather` (`PendingAuthService.java:59-186`); **but** `contextScreen` sources CREDLIM/CASHLIM from `pending_auth_summary` (`:349-350`) not ACCTDAT (`COPAUS0C.cbl:780-783`) | `listShowsContextSummaryAndFirstFiveRows_frS1902` | PARTIAL — wrong field source (finding F-1) |
| FR-S19-03 | Page fill, rows newest-first (9's-complement asc) | `fillForward` (`:221-232`) + `PendingAuthDetailRepository.findByIdAcctIdOrderByIdAuthDate9cAscIdAuthTime9cAsc` (`:19-20`) | `rowsDisplayNewestFirstComplementOrder_frS1903` | PASS |
| FR-S19-04 | PF8 → next ≤5 rows or 'already at the bottom' | `pageForward` (`:208-218`), `findByAcctIdAfterKey` (repo `:24-32`) | `pf8PagesForwardThroughDetails_frS1904` | PASS |
| FR-S19-05 | PF7 → previous page from 20-deep stack | `pageBackward` (`:190-204`), `findByAcctIdFromKey` inclusive reposition (repo `:47-55`), `MAX_PAGE_KEYS=20` (`PendingAuthPageState.java:24`) | `pf7PagesBackwardToStoredStartKey_frS1905` (asserts the COBOL PAGE-NUM quirk verbatim) | PASS |
| FR-S19-06 | 'S' on row → detail screen for that key | `enter()` SEL scan (`:105-121`) → `PendingAuthNavigation` → `PendingAuthUiController` redirect (`:83-88`) | `selectSNavigatesToDetail_frS1906` | PASS |
| FR-S19-07 | Detail render — all CIPAUDTY fields + statuses | `PendingAuthDetailService.view/screen` (`PendingAuthDetailService.java:55-65,:157-190`), `pending-auth-detail.html` | `detailShowsEveryCipaudtyField_frS1907` | PASS |
| FR-S19-08 | F5 mark fraud → AUTHFRDS row, flag 'F', 'AUTH MARKED FRAUD...' | `markFraud` (`:102-131`) + `AuthFraudService.journal` (`AuthFraudService.java:32-77`), `@Transactional` (`:101`) | `pf5MarksAuthorizationFraud_frS1908` | PASS |
| FR-S19-09 | F5 on flagged → journal 'R', flag cleared, 'AUTH FRAUD REMOVED...' | `markFraud` action flip (`:111-112`), segment `'R'` + rpt date (`:124-126`) | `pf5OnFlaggedAuthRemovesFraud_frS1909` | PASS |
| FR-S19-10 | F8 → next detail in GNP order | `next()` (`:69-96`), `findByAcctIdAfterKey` size-1 | `pf8ShowsNextAuthorization_frS1910` | PASS |
| FR-S19-11 | F3 → return to caller | `browse` PF3 → `"exit"` (`:75-77`) → `redirect:/menu` (`PendingAuthUiController.java:80-81`); detail PF3 → `redirect:/ui/pending-auth?acct=` (`:111-113`) | `pf3ReturnsToListAndMenu_frS1911` | PASS |
| FR-S19-12 | AUTHFRDS PK clash (-803) → UPDATE, still success | `AuthFraudService.journal` find-then-insert-or-update (`AuthFraudService.java:36-43`) | `fraudUpsertUpdatesExistingRowLikeDb2Minus803_frS1912` | PASS |

## 2. COBOL parity spot-checks

Messages / literals (verbatim compare, Java constant ↔ COBOL source):

| Item | COBOL | Java | Result |
|---|---|---|---|
| 'Please enter Acct Id...' | `COPAUS0C.cbl:269` | `CobolMessages.java:377` | MATCH |
| 'Acct Id must be Numeric ...' | `COPAUS0C.cbl:278` | `CobolMessages.java:378` | MATCH |
| 'Invalid selection. Valid value is S' | `COPAUS0C.cbl:328` | `CobolMessages.java:143` (TRANSACTION_SELECTION_INVALID) | MATCH |
| 'You are already at the top/bottom of the page...' | `COPAUS0C.cbl:381,:409` | `CobolMessages.java:137,:139` | MATCH |
| 'Invalid key pressed. Please see below...' | `CSMSG01Y.cpy:20-21` (via `COPAUS0C.cbl:248`,`COPAUS1C.cbl:196`) | `CobolMessages.java:10` | MATCH |
| ' System error while reading AUTH Summary: Code:' | `COPAUS0C.cbl:989` | `pendingAuthSummaryError` (`CobolMessages.java:419`) | MATCH (documented `EX` placeholder) |
| ' System error while reading AUTH Details: Code:' | `COPAUS0C.cbl:477` | `pendingAuthDetailsError` (`:422`) | MATCH |
| ' System error while reading Auth Summary/Auth Details/next Auth' | `COPAUS1C.cbl:456,:482,:511` (mixed case 'Auth') | reuses all-caps list-screen constants; `next Auth` variant missing entirely (`PendingAuthDetailService.java:83-85`) | MISMATCH (F-2) |
| ' System error while repos. AUTH Details: Code:' | `COPAUS0C.cbl:510` | none — `fillFrom` emits the reading-Details message (`PendingAuthService.java:242`) | MISMATCH (F-2) |
| ' System error while FRAUD Tagging, ROLLBACK||' | `COPAUS1C.cbl:545` | `pendingAuthFraudTagError` defined (`CobolMessages.java:427`) but dead code — never called | MISMATCH (F-3) |
| ' SYSTEM ERROR DB2: CODE:x, STATE: y' | `COPAUS2C.cbl:211` | `pendingAuthDb2Error` (`CobolMessages.java:432`) | MATCH |
| ' UPDT ERROR DB2: CODE:x, STATE: y' | `COPAUS2C.cbl:239` | none — upsert-update failure emits the INSERT-path text (`AuthFraudService.java:74-75`) | MISMATCH (F-4) |
| 'AUTH MARKED FRAUD...' / 'AUTH FRAUD REMOVED...' | `COPAUS1C.cbl:540,:543` | `CobolMessages.java:381,:382` | MATCH |
| 'ADD SUCCESS' / 'UPDT SUCCESS' | `COPAUS2C.cbl:201,:232` | `CobolMessages.java:383,:384` | MATCH |
| 'Already at the last Authorization...' | `COPAUS1C.cbl:284` | `CobolMessages.java:379-380` | MATCH |
| 'Account:..not found in XREF/ACCT/CUST file. Resp:000000013 Reas:000000000' | `COPAUS0C.cbl:836-844,:886-894,:937-945` | `pendingAuth*NotFound` (`CobolMessages.java:389-402`) | MATCH (fixed RESP 13 documented) |
| ' System error while reading XREF/ACCT/CUST file. Resp:..Reas:..' | `COPAUS0C.cbl:851-858,:901-908,:952-959` | `pendingAuth*Error` (`:404-416`), RESP 17/REAS 120 convention | MATCH (convention documented) |
| Footer list 'Type 'S' to View...' + 'ENTER=Continue F3=Back F7=Backward F8=Forward' | `COPAU00.bms:499-511` | `pending-auth.html:113,:115` | MATCH |
| Footer detail ' F3=Back F5=Mark/Remove Fraud F8=Next Auth' | `COPAU01.bms:292` | `pending-auth-detail.html:77` | MATCH |

Edits / derivations:

| Item | COBOL | Java | Result |
|---|---|---|---|
| Row date YYMMDD→MM/DD/YY, time HHMMSS→HH:MM:SS | `COPAUS0C.cbl:527-534` | `editDate/editTime` (`PendingAuthService.java:372-385`) | MATCH |
| A/D = 'A' iff resp '00' | `COPAUS0C.cbl:534-540`, `COPAUS1C.cbl:314-319` | `toRow`/`screen` (`PendingAuthService.java:365`, `PendingAuthDetailService.java:160,:169`) | MATCH |
| Decline-reason SEARCH ALL 'code-desc(16)', '9999-ERROR' at end | `COPAUS1C.cbl:53-66,:317-330` | `DECLINE_REASONS` + `declineReason` (`PendingAuthDetailService.java:35-39,:194-200`) — table values identical; Java trims the lookup key, COBOL compares raw X(4) | MATCH (trim nuance, F-8 INFO) |
| Amount pic `-zzzzzzz9.99`/`-zzzz9.99` floating-minus | `COPAUS0C.cbl:55-57` | `CobolFormat.editSuppressedAmount` (`CobolFormat.java:57-66`) | MATCH |
| Counts `9(03)` zero-suppressed to '000' | `COPAUS0C.cbl:58,:786-789` | `count()` (`PendingAuthService.java:452-454`) | MATCH |
| Card expiry MMYY→MM/YY | `COPAUS1C.cbl:333-336` | `cardExpiry` (`PendingAuthDetailService.java:203-208`) | MATCH |
| Fraud status 'F-'/'R-'+rpt date else '-' | `COPAUS1C.cbl:342-348` | `PendingAuthDetailService.java:161-164` | MATCH |
| 'Auth Code' field = PA-PROCESSING-CODE %06d | `COPAUS1C.cbl:332` | `authCode` (`:172`) | MATCH (mislabel inherited verbatim) |
| PA-FRAUD-RPT-DATE on segment = MMDDYY FORMATTIME DATESEP → 'MM/DD/YY' | `COPAUS2C.cbl:95-101` | `LocalDate.now().format("MM/dd/yy")` (`PendingAuthDetailService.java:125`) | MATCH |
| AUTH_TS = orig YYMMDD + `999999999 - PA-AUTH-TIME-9C` real timestamp | `COPAUS2C.cbl:103-110,:168-170` | `PendingAuthKey.realTimestamp` (`PendingAuthKey.java:41-57`) — hard-codes century 20YY vs DB2 'YY' window | MATCH for 2000s data (F-9 INFO) |
| FRAUD_RPT_DATE = CURRENT DATE | `COPAUS2C.cbl:190,:226` | `LocalDate.now()` (`AuthFraudService.java:40,:68`) | MATCH |
| Credit Limit/Cash Limit ← ACCTDAT `ACCT-CREDIT-LIMIT`/`ACCT-CASH-CREDIT-LIMIT` | `COPAUS0C.cbl:780-783` | `summary.getCreditLimit()`/`getCashLimit()` from `pending_auth_summary` (`PendingAuthService.java:349-350`) | **MISMATCH (F-1, HIGH)** |
| Summary metrics ← PA segment; MOVE ZERO when absent | `COPAUS0C.cbl:785-806` | `contextScreen` (`:347-354`) | MATCH |
| Acct-id edit: `ACCTIDI IS NOT NUMERIC` on full X(11) | `COPAUS0C.cbl:273` | `isNumeric` on the *trimmed* input (`PendingAuthService.java:431-433`) | MISMATCH — short numeric input accepted (F-5, LOW) |
| PF7 quirk: reposition refill re-runs WS-IDX=2 bump; PAGE-NUM lands back, stack slot clobbered | `COPAUS0C.cbl:362-368,:425-443` | `pageBackward` + `PageFill.state` (`PendingAuthService.java:190-204,:498-512`) | MATCH (quirk preserved, asserted by test) |
| Journal cust_id ← CDEMO-CUST-ID (COMMAREA) | `COPAUS1C.cbl:246` | `summary.getCustId()` — NULL when summary absent (`PendingAuthDetailService.java:114-117`) | MISMATCH (F-6, LOW) |
| CXACAIX by acct (first xref row wins), ACCTDAT, CUSTDAT read order | `COPAUS0C.cbl:817-959` | `cardXrefRepository.findByXrefAcctId` → `accountRepository` → `customerRepository` (`PendingAuthService.java:130-163`); takes `xrefs.get(0)` | MATCH |
| ACCSTAT field never populated | `COPAU00.bms:114` (no `ACCSTATO` move anywhere in `COPAUS0C.cbl`) | `""` constant (`PendingAuthService.java:345`) | MATCH (dead field in source) |

## 3. Stub/placeholder sweep

Searched the stream's implementation and test surface for `TODO`, `FIXME`, `not implemented`, `UnsupportedOperationException`, `@Disabled`/`@Ignore`, `assertTrue(true)`, commented-out assertions, and hardcoded happy-path returns across: `PendingAuthService`, `PendingAuthDetailService`, `AuthFraudService`, `PendingAuthController`, `PendingAuthUiController`, `PendingAuth*`/`AuthFraud` models, the three repositories, both templates, `PendingAuthFrParityTest`, `MenuServiceTest`, `MenuUiIntegrationTest` — **no hits**.

Placeholder-adjacent items that *are* real code, accounted for: the `'EX'` stand-in for IMS DIBSTAT on read failures (`PendingAuthService.java:170,:230,:242,:323`, `PendingAuthDetailService.java:83-85`, `PendingAuthController.java:89`), RESP 13/REAS 0 for NOTFND and RESP 17/REAS 120 for store errors (`CobolMessages.java:389-416`), and `"-00001"/"HY000"` as the synthesized SQLCODE/SQLSTATE (`AuthFraudService.java:75`) — all documented deviations in PR #125. One *unused* message constant (`pendingAuthFraudTagError`) is a dead stub for an unwired error path — finding F-3.

## 4. Documented deviations vs shipped code

Sources: analysis §5 boundary table (S19-B1..B8), plan §3, and wave PR #125 deviation list.

| Deviation / decision | Source | Shipped? | Compliant? |
|---|---|---|---|
| S19-B1 — MenuService flag flip `false→true` + `UI_ROUTES` `COPAUS0C→/ui/pending-auth` | analysis:122 | `MenuService.java:48,:122` | yes |
| S19-B2 — in-app navigation replaces XCTL COMMAREA ext | analysis:123 | `PendingAuthNavigation` + redirect `PendingAuthUiController.java:83-88` | yes |
| S19-B3 — PAUTSUM0/PAUTDTL1 → 2 JPA tables, complement-asc = newest-first, GE/GB→empty, other→error | analysis:124 | `V2601__pending_auth_tables.sql:26-57`, repo order-bys `PendingAuthDetailRepository.java:19-55` | yes |
| S19-B4 — LINK COPAUS2C → same-tx bean call | analysis:125 | `AuthFraudService.journal` called inside `markFraud` (`PendingAuthDetailService.java:117`) | yes |
| S19-B5 — AUTHFRDS INSERT(-803→UPDATE) → upsert on (card_num,auth_ts) | analysis:126 | `AuthFraudService.java:32-77`; PK `V2601:90` | yes |
| S19-B6 — reuse VSAM repos; `findByAcctId` xref direction | analysis:127 | `CardXrefRepository.java:10` | yes |
| S19-B7 — SYNCPOINT unit → `@Transactional` | analysis:128 | `PendingAuthDetailService.java:101` | yes |
| S19-B8 — 20-deep page-key stack → echoed `pageState`/keyset cursor | analysis:129 | `PendingAuthPageState` (`MAX_PAGE_KEYS=20`), hidden-input echo `pending-auth.html`, `?after=`/`dir=` REST (`PendingAuthController.java:51-104`) | yes |
| PR-deviation — `'EX'` placeholder for IMS status | PR #125 | `CobolMessages.java:419-427` | yes |
| PR-deviation — PF7/PF8 redisplay re-derives context+summary | PR #125 | `redisplay`→`readContext` (`PendingAuthService.java:248-273`) | yes |
| PR-deviation — context read failures stop at first failed read (no mid-procedure SEND cascade) | PR #125 | early returns in `gather`/`readContext` | yes |
| PR-deviation — `/ui/pending-auth` route prefix vs siblings unprefixed | PR #125 | `UI_ROUTES` (`MenuService.java:122`) | yes |

**Undocumented deviations shipped**: F-1 (limits sourced from summary, not ACCTDAT — the analysis field table `analysis.md:41` itself mis-attributes CREDLIM/CASHLIM to `PA-*`, and the code followed it), F-2 (detail-screen/next/reposition error texts), F-3 (fraud-tag error path unwired), F-4 (`UPDT ERROR DB2` variant absent — also missing from the FR doc's own catalogue), F-5 (short acct-id acceptance), F-6 (journal cust_id source).

## 5. Test evidence

Command (repo `spring-boot/`, `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`):

```
mvn -q test -Dtest='PendingAuthFrParityTest,MenuServiceTest,MenuUiIntegrationTest,ApiIntegrationTest'
```

Observed (per `target/surefire-reports/*.txt`):

| Class | Tests run | Failures | Errors | Skipped |
|---|---|---|---|---|
| `PendingAuthFrParityTest` | 12 | 0 | 0 | 0 |
| `MenuServiceTest` | 5 | 0 | 0 | 0 |
| `MenuUiIntegrationTest` | 11 | 0 | 0 | 0 |
| `ApiIntegrationTest` | 18 | 0 | 0 | 0 |
| **Total** | **46** | **0** | **0** | **0** |

Scoped run only, per instructions; the full suite is covered by a separate audit run. `PendingAuthExportJobIT`/`ImportJobIT`/`PurgeJobIT`/`AuthProcessing*` belong to S-20/S-22 and were excluded.

## 6. Findings

| Sev | Title | Evidence | Recommendation |
|---|---|---|---|
| HIGH | `Credit Lim:`/`Cash Lim:` read from `pending_auth_summary` instead of ACCTDAT | COBOL: `CREDLIMO←ACCT-CREDIT-LIMIT`, `CASHLIMO←ACCT-CASH-CREDIT-LIMIT` (`COPAUS0C.cbl:780-783`). Java: `summary.getCreditLimit()`/`getCashLimit()` (`PendingAuthService.java:349-350`). Shipped fixture already diverges: acct 1 ACCTDAT limit `2020.00`/`1020.00` (`spring-boot/src/test/resources/seed/ASCII/acctdata.txt:1`, CVACT01Y offsets) vs summary `11000.00`/`2500.00` (`PendingAuthDataSeeder.java:57`), and `frS1902` asserts `creditLimit="11000.00"` (`PendingAuthFrParityTest.java:95`) — the test pins the wrong source. Worse, an acct with *no* summary row shows `0.00` where COBOL still shows the ACCTDAT limits (unconditional moves at `:780-783` before the `FOUND-PAUT-SMRY-SEG` check). | Source both fields from the `Account` entity already fetched in `gather`/`readContext`; adjust `frS1902` expectations to the ACCTDAT values; fix the analysis field table (`analysis.md:41`) which mis-attributes the fields to `PA-CREDIT-LIMIT`/`PA-CASH-LIMIT`. |
| MEDIUM | Detail-screen/system error texts missing or wrong-case: `next Auth`, `repos. AUTH Details`, mixed-case `Auth Summary/Auth Details` | COBOL: `COPAUS1C.cbl:456` (`reading Auth Summary`), `:482` (`reading Auth Details`), `:511` (`reading next Auth`), `COPAUS0C.cbl:510` (`repos. AUTH Details`). Java: `next()` failure emits `reading AUTH Details` (`PendingAuthDetailService.java:83-85`); `fillFrom` (PF7 reposition) emits the same (`PendingAuthService.java:242`); `view()`/`read()` swallow `DataAccessException` and render an empty screen with no message at all (`PendingAuthDetailService.java:60-63,:147-154`). | Add the three missing message builders (exact casing per program) and surface them on the respective failure paths; keep `EX` as the status placeholder per the documented deviation. |
| MEDIUM | Fraud-tag failure path unreachable — `' System error while FRAUD Tagging, ROLLBACK||'` is dead code | `pendingAuthFraudTagError` defined `CobolMessages.java:427`, zero call sites; COBOL shows it on REPL failure (`COPAUS1C.cbl:540-556`). In Java a `detailRepository.save` failure inside `@Transactional markFraud` (`PendingAuthDetailService.java:126`) propagates to a 500; rollback semantics hold but the user sees no catalogued message. | Catch the REPL failure in `markFraud`, return `screen(record, pendingAuthFraudTagError("EX"))` (message only — the tx still rolls back via the exception or an explicit `TransactionAspectSupport` rollback). |
| LOW | `' UPDT ERROR DB2: CODE:x, STATE: y'` variant not ported | `COPAUS2C.cbl:239-246` emits it when the `-803→UPDATE` retry fails; Java's catch returns the INSERT-path `SYSTEM ERROR DB2` text regardless (`AuthFraudService.java:73-76`). The FR doc's own catalogue (FR §5) also missed this variant — doc gap plus code gap. | Track which leg failed in `journal` and emit the matching text; add the variant to the FR catalogue. |
| LOW | Acct-id edit accepts short numeric input the COBOL rejects | COBOL tests `ACCTIDI IS NOT NUMERIC` on the raw X(11) field — `'1'`+spaces/LOW-VALUES is not numeric → 'Acct Id must be Numeric ...' (`COPAUS0C.cbl:273-279`). Java trims then requires all digits (`PendingAuthService.java:63-65,:431-433`), so `'1'` is accepted and gathers acct 1. | Decide intended behavior; if verbatim parity, require 11 digits (pad or reject); document whichever is chosen. |
| LOW | Journal `cust_id` sourced from `pending_auth_summary`, not the session COMMAREA | COBOL moves `CDEMO-CUST-ID` (`COPAUS1C.cbl:246`) — the signed-in customer context, always present. Java reads `summary.getCustId()` (`PendingAuthDetailService.java:114-117`), writing NULL when the summary row is absent or its read throws (that `findById` is also outside the error-message path — a `DataAccessException` escapes to a 500). | Carry cust id in the navigation/session state (it is already looked up on the list screen) or fall back to the xref's cust id; wrap the summary read. |
| INFO | No cross-program session acct — COMMAREA `CDEMO-ACCT-ID` has no session equivalent | COBOL carries the last acct in the COMMAREA, so option 11 re-entered after another screen pre-gathers it (`COPAUS0C.cbl:203-217`). Java menu entry always lands on a blank map (`PendingAuthUiController.java:54-61`); only `?acct=` and the hidden `stateAcct` echo carry it. Systemic to the port, not S-19-specific. | Document as an accepted deviation, or hold a session-scoped last-acct attribute. |
| INFO | `declineReason` trims before lookup vs COBOL's raw X(4) compare | `PendingAuthDetailService.java:194-200` vs `SEARCH ALL` on `DECL-CODE = PA-AUTH-RESP-REASON` (`COPAUS1C.cbl:317-330`); a space-padded code resolves in Java, falls AT END→`9999-ERROR` in COBOL. | Compare the raw 4-char field; trim only for display if desired. |
| INFO | `auth_ts` century hard-coded `2000+YY` vs DB2 `TIMESTAMP_FORMAT 'YY'` window | `PendingAuthKey.java:46` vs `COPAUS2C.cbl:168-170`; identical for 2000-era auth dates (all plausible data), would diverge for pre-2000 rows. DB2's exact YY window not verified — flagged UNVERIFIED, low risk. | Optional: derive century from the seed window or document the assumption. |

No CRITICAL findings. GAP/PARTIAL trace rows: FR-S19-02 (PARTIAL → F-1).
