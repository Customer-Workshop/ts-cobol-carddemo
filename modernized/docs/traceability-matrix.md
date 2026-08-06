# Traceability matrix

The target names in this document are the contract for later phases. Online rows are verified against `app/cbl`, `app/bms`, and the README inventory; optional README rows whose source assets are absent are called out below.

## Online

| CICS transaction ID | BMS map | COBOL program | Function | Legacy data stores | Target modern component (React route/page) | Target REST endpoint(s) | Target backend service/module | Phase | Optional-module flag |
|---|---|---|---|---|---|---|---|---|---|
| CC00 | COSGN00 | COSGN00C | Signon | usrsec | /sign-in | POST /api/v1/auth/sessions | authService | 0 | no |
| CM00 | COMEN01 | COMEN01C | Main menu | COMMAREA | /menu | GET /api/v1/menu | navigationService | 0 | no |
| CAVW | COACTVW | COACTVWC | Account view | account,cardxref,customer | /accounts/:accountId | GET /api/v1/accounts/:accountId | accountService | 1 | no |
| CAUP | COACTUP | COACTUPC | Account update | account,cardxref,customer | /accounts/:accountId/edit | PATCH /api/v1/accounts/:accountId | accountService | 1 | no |
| CCLI | COCRDLI | COCRDLIC | Card list | card | /cards | GET /api/v1/cards | cardService | 1 | no |
| CCDL | COCRDSL | COCRDSLC | Card view | card,account,customer | /cards/:cardNumber | GET /api/v1/cards/:cardNumber | cardService | 1 | no |
| CCUP | COCRDUP | COCRDUPC | Card update | card | /cards/:cardNumber/edit | PATCH /api/v1/cards/:cardNumber | cardService | 1 | no |
| CT00 | COTRN00 | COTRN00C | Transaction list | transaction | /transactions | GET /api/v1/transactions | transactionQueryService | 1 | no |
| CT01 | COTRN01 | COTRN01C | Transaction view | transaction | /transactions/:transactionId | GET /api/v1/transactions/:transactionId | transactionQueryService | 1 | no |
| CT02 | COTRN02 | COTRN02C | Transaction add | transaction,cardxref | /transactions/new | POST /api/v1/transactions | transactionService | 1 | no |
| CR00 | CORPT00 | CORPT00C | Transaction reports | transaction | /reports/transactions | POST /api/v1/reports/transactions | reportService | 1 | no |
| CB00 | COBIL00 | COBIL00C | Bill payment | account,cardxref,transaction | /bill-payments | POST /api/v1/bill-payments | paymentService | 1 | no |
| CA00 | COADM01 | COADM01C | Admin menu | COMMAREA | /admin | GET /api/v1/admin/menu | adminNavigationService | 1 | no |
| CU00 | COUSR00 | COUSR00C | List users | usrsec | /admin/users | GET /api/v1/admin/users | userAdminService | 1 | no |
| CU01 | COUSR01 | COUSR01C | Add user | usrsec | /admin/users/new | POST /api/v1/admin/users | userAdminService | 1 | no |
| CU02 | COUSR02 | COUSR02C | Update user | usrsec | /admin/users/:userId/edit | PATCH /api/v1/admin/users/:userId | userAdminService | 1 | no |
| CU03 | COUSR03 | COUSR03C | Delete user | usrsec | /admin/users/:userId | DELETE /api/v1/admin/users/:userId | userAdminService | 1 | no |

## Batch

| JCL job | COBOL/utility program | Target modern job name | Trigger/schedule | Target service |
|---|---|---|---|---|
| ACCTFILE | IDCAMS | account-master-refresh | scheduled/weekly | dataLoadService |
| CARDFILE | IDCAMS | card-master-refresh | scheduled/weekly | dataLoadService |
| CUSTFILE | IDCAMS | customer-master-refresh | scheduled/weekly | dataLoadService |
| DISCGRP | IDCAMS | disclosure-group-refresh | Control-M weekly chain | dataLoadService |
| TRANFILE | IDCAMS | transaction-master-load | scheduled | dataLoadService |
| TRANCATG | IDCAMS | transaction-category-load | scheduled | dataLoadService |
| TRANTYPE | IDCAMS | transaction-type-load | scheduled | dataLoadService |
| XREFFILE | IDCAMS | card-account-customer-xref-load | scheduled | dataLoadService |
| POSTTRAN | CBTRN02C | post-daily-transactions | scheduled after daily input | transactionBatchService |
| INTCALC | CBACT04C | calculate-interest | Control-M monthly: CLOSEFIL → INTCALC | interestBatchService |
| CREASTMT | CBSTM03A | create-statements | scheduled monthly | statementBatchService |
| TRANREPT | CBTRN03C | transaction-report | submitted from CICS / proc | reportBatchService |

## Legacy programs without an equivalent in the two tables

These are infrastructure/read-conversion utilities rather than user-facing transaction or business batch flows; they are retained as migration tooling or explicitly deferred, not silently dropped.

| Program(s) | Reason |
|---|---|
| `CBACT01C`, `CBACT02C`, `CBACT03C`, `CBCUS01C` | Diagnostic/read-and-print or conversion preparation jobs; no standalone target business route. |
| `CBEXPORT`, `CBIMPORT` | Branch/export-import migration utilities; target is a migration command, not runtime application behavior. |
| `CBTRN01C`, `CBTRN03C` | Source/report batch variants; `CBTRN03C` is represented by `TRANREPT`; `CBTRN01C` is a daily-file preparation flow whose exact JCL trigger needs confirmation. |
| `CBSTM03B`, `COBSWAIT`, `CSUTLDTC` | Called subroutine/date utility/wait utility; folded into statement/date/job-runner modules. |
| `COADM01C`, `COCRDLIC`, `COCRDSLC`, `COCRDUPC`, `COUSR00C`, `COUSR01C`, `COUSR02C`, `COUSR03C` | Included in admin/card online scope by route family above where source and BMS are present; no separate transaction ID is declared in source CSD, so route contract is provisional. |


## Discrepancies found

- README claims **17 online rows**, but several named programs/maps are not present in base `app/`: `COPAU00`, `COPAU01`, `COPAUA0C`, `COTRTUP`, `COTRTLI`, `CODATE01`, `COACCT01` and their maps. They exist only as narrative references or optional-module documentation; they are not base source assets.
- README lists `CREADB21`, `TRANEXTR`, `CBPAUP0J`, and `MNTTRDB2` batch rows, but no corresponding base JCL files/programs exist under the requested base trees; `TRANEXTR` appears in Control-M metadata and `MNTTRDB2` is referenced by conditions.
- README lists `OPENFIL` after the disclosure-group chain; the XML includes it in that folder, while the weekly transaction-type folder separately chains `MNTTRDB2 → TRANEXTR`.
- README says `POSTTRAN` processes transactions; source `POSTTRAN.jcl` invokes `CBTRN02C`, which opens `TRANFILE` with `OUTPUT`, so its exact replacement/refresh semantics must preserve the source rather than infer append behavior.
- README calls `CBSTM03A` “Produce transaction statement”; source calls `CBSTM03B` and writes both statement and HTML output, so statement generation has two artifacts.

