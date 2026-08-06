# Traceability matrix

This matrix is the contract for later phases. Transaction IDs are taken from `app/csd/CARDDEMO.CSD`; the CSD and `CBADMCDJ.jcl` definitions override names inferred from program filenames. `no scheduler definition supplied` is intentional when no Control-M dependency was found.

## Online

| CICS transaction ID | BMS map | COBOL program | Function | Legacy data stores | Target modern component (React route/page) | Target REST endpoint(s) | Target backend service/module | Phase | Optional-module flag |
|---|---|---|---|---|---|---|---|---|---|
| CC00 | COSGN00 / COSGN0A | COSGN00C | Signon | USRSEC | /sign-in | POST /api/v1/auth/sessions | authService | 1 | no |
| CM00 | COMEN01 / COMEN1A | COMEN01C | Main menu | COMMAREA | /menu | GET /api/v1/menu | navigationService | 1 | no |
| CAVW | COACTVW / CACTVWA | COACTVWC | Account view | ACCTDAT,CUSTDAT,CARDDAT,CXACAIX,CARDAIX | /accounts/:accountId | GET /api/v1/accounts/:accountId | accountService | 1 | no |
| CAUP | COACTUP / CACTUPA | COACTUPC | Account update | ACCTDAT,CUSTDAT,CXACAIX,CARDAIX,CARDDAT | /accounts/:accountId/edit | PATCH /api/v1/accounts/:accountId | accountService | 1 | no |
| CA00 | COADM01 / COADM1A | COADM01C | Admin menu | COMMAREA | /admin | GET /api/v1/admin/menu | adminNavigationService | 1 | no |
| CCLI | COCRDLI / CCRDLIA | COCRDLIC | Card list | CARDDAT,CARDAIX,CXACAIX,CUSTDAT | /cards | GET /api/v1/cards | cardService | 1 | no |
| CCDL | COCRDSL / CCRDSLA | COCRDSLC | Card detail | CARDDAT,CARDAIX,ACCTDAT,CUSTDAT | /cards/:cardNumber | GET /api/v1/cards/:cardNumber | cardService | 1 | no |
| CCUP | COCRDUP / CCRDUPA | COCRDUPC | Card update | CARDDAT,CARDAIX | /cards/:cardNumber/edit | PATCH /api/v1/cards/:cardNumber | cardService | 1 | no |
| CT00 | COTRN00 / COTRN0A | COTRN00C | Transaction list | TRANSACT | /transactions | GET /api/v1/transactions | transactionQueryService | 1 | no |
| CT01 | COTRN01 / COTRN1A | COTRN01C | Transaction detail | TRANSACT,TRANTYPE,TRANCATG | /transactions/:transactionId | GET /api/v1/transactions/:transactionId | transactionQueryService | 1 | no |
| CT02 | COTRN02 / COTRN2A | COTRN02C | Transaction add | TRANSACT,CXACAIX,CCXREF | /transactions | POST /api/v1/transactions | transactionService | 1 | no |
| CB00 | COBIL00 / COBIL0A | COBIL00C | Bill payment | ACCTDAT,CXACAIX,TRANSACT | /bill-payments | POST /api/v1/bill-payments | paymentService | 1 | no |
| CR00 | CORPT00 / CORPT0A | CORPT00C | Transaction reports | DATEPARM | /reports/transactions | POST /api/v1/reports/transactions | reportService | 1 | no |
| CU00 | COUSR00 / COUSR0A | COUSR00C | List users | USRSEC | /admin/users | GET /api/v1/admin/users | userAdminService | 1 | no |
| CU01 | COUSR01 / COUSR1A | COUSR01C | Add user | USRSEC | /admin/users | POST /api/v1/admin/users | userAdminService | 1 | no |
| CU02 | COUSR02 / COUSR2A | COUSR02C | Update user | USRSEC | /admin/users/:userId | PATCH /api/v1/admin/users/:userId | userAdminService | 1 | no |
| CU03 | COUSR03 / COUSR3A | COUSR03C | Delete user | USRSEC | /admin/users/:userId | DELETE /api/v1/admin/users/:userId | userAdminService | 1 | no |

`CDV1` is a CSD-defined resource transaction without a matching base COBOL/BMS business flow in the supplied tree. `CCT1`–`CCT4` and `CCDM` are additional `CBADMCDJ` resource entries and are infrastructure definitions rather than rows with a distinct base program.

## Batch

| JCL job | COBOL/utility program | Target modern job name | Trigger/schedule | Target service |
|---|---|---|---|---|
| ACCTFILE | IDCAMS | define/load account KSDS | no scheduler definition supplied | accountLoadJob |
| CARDFILE | IDCAMS | define/load card KSDS and AIX | no scheduler definition supplied | cardLoadJob |
| CBADMCDJ | IDCAMS/CICS | install CICS resources | no scheduler definition supplied | cicsResourceJob |
| CBEXPORT | CBEXPORT | export VSAM masters | no scheduler definition supplied | migrationExportJob |
| CBIMPORT | CBIMPORT | import interchange data | no scheduler definition supplied | migrationImportJob |
| CLOSEFIL | CICS operator step | close files | Control-M daily/weekly chain predecessor | fileControlJob |
| COMBTRAN | SORT/utility | combine transaction generations | Control-M monthly: after INTCALC | transactionBatchJob |
| CREASTMT | CBSTM03A | create statements/HTML | no scheduler definition supplied | statementBatchJob |
| CUSTFILE | IDCAMS | define/load customer KSDS | no scheduler definition supplied | customerLoadJob |
| DALYREJS | IDCAMS | define reject GDG | no scheduler definition supplied | rejectSetupJob |
| DEFCUST | IDCAMS | define customer resources | no scheduler definition supplied | datasetSetupJob |
| DEFGDGB | IDCAMS | define backup GDGs | no scheduler definition supplied | datasetSetupJob |
| DEFGDGD | IDCAMS | define reference GDGs | no scheduler definition supplied | datasetSetupJob |
| DISCGRP | IDCAMS | refresh disclosure KSDS | Control-M weekly: CLOSEFIL → DISCGRP → WAITSTEP → OPENFIL | disclosureRefreshJob |
| DUSRSECJ | IDCAMS/IEBGENER | load security data | no scheduler definition supplied | securityLoadJob |
| ESDSRRDS | IDCAMS | define ESDS/RRDS examples | no scheduler definition supplied | datasetSetupJob |
| FTPJCL | FTP | send generated data | no scheduler definition supplied | transferJob |
| INTCALC | CBACT04C | calculate interest | Control-M monthly: CLOSEFIL → INTCALC → COMBTRAN → WAITSTEP → OPENFIL | interestBatchJob |
| INTRDRJ1 | internal reader | submit generated JCL | no scheduler definition supplied | jobSubmissionJob |
| INTRDRJ2 | internal reader | submit generated JCL | no scheduler definition supplied | jobSubmissionJob |
| OPENFIL | CICS operator step | reopen files | Control-M weekly chains after WAITSTEP | fileControlJob |
| POSTTRAN | CBTRN02C | post/reject daily transactions | no scheduler definition supplied | transactionBatchJob |
| PRTCATBL | IDCAMS/SORT | report category balances | no scheduler definition supplied | reportBatchJob |
| READACCT | CBACT01C | diagnostic account conversion | no scheduler definition supplied | diagnosticJob |
| READCARD | CBACT02C | diagnostic card read | no scheduler definition supplied | diagnosticJob |
| READCUST | CBCUS01C | diagnostic customer read | no scheduler definition supplied | diagnosticJob |
| READXREF | CBACT03C | diagnostic xref read | no scheduler definition supplied | diagnosticJob |
| REPTFILE | IDCAMS | define report GDG | no scheduler definition supplied | datasetSetupJob |
| TCATBALF | IDCAMS | define/load category balance KSDS | no scheduler definition supplied | referenceLoadJob |
| TRANBKP | REPROC/IDCAMS | backup transaction KSDS | Control-M daily: CLOSEFIL → TRANBKP → WAITSTEP | transactionBackupJob |
| TRANCATG | IDCAMS | define/load category KSDS | no scheduler definition supplied | referenceLoadJob |
| TRANFILE | IDCAMS | prepare daily transaction PS | no scheduler definition supplied | transactionInputJob |
| TRANIDX | IDCAMS | define transaction AIX/path | no scheduler definition supplied | datasetSetupJob |
| TRANREPT | CBTRN03C via TRANREPT proc | produce transaction report | no scheduler definition supplied | reportBatchJob |
| TRANTYPE | IDCAMS | define/load type KSDS | no scheduler definition supplied | referenceLoadJob |
| TXT2PDF1 | text-to-PDF utility | convert report text to PDF | no scheduler definition supplied | presentationJob |
| WAITSTEP | COBSWAIT | wait/barrier step | Control-M daily/weekly/monthly chains | fileControlJob |
| XREFFILE | IDCAMS | define/load card xref KSDS | no scheduler definition supplied | xrefLoadJob |

## Legacy programs without an equivalent in the two tables

These components are implementation details or diagnostic utilities, not silently dropped business flows:

| Program | Reason |
|---|---|
| `CBACT01C`, `CBACT02C`, `CBACT03C`, `CBCUS01C` | Diagnostic/read-and-format jobs covered by `READACCT`, `READCARD`, `READXREF`, and `READCUST`. |
| `CBSTM03B` | Called file-I/O subroutine folded into the statement job. |
| `COBSWAIT` | Wait utility represented by `WAITSTEP`. |
| `CSUTLDTC` | Date-validation utility folded into API validation/date adapter. |

Every other base COBOL program appears in the online table or is invoked by a batch row above. The optional-module programs are tracked in `inventory.md` and are not base-app equivalents; their target contracts are deferred until optional scope is selected.

## Discrepancies found

- The root README contains **24 online rows**, not 17. Seventeen rows map directly to base CSD transaction/program definitions above; the remaining README rows refer to optional-module or narrative components (`COPAU*`, `COTRT*`, `COACCT01`, `CODATE01`) not present in base `app/cbl`/`app/bms`.
- The CSD does declare transaction IDs, including `CAUP`, `CAVW`, `CA00`, `CB00`, `CCDL`, `CCLI`, `CCUP`, `CC00`, `CM00`, `CR00`, `CT00`, `CT01`, `CT02`, `CU00`, `CU01`, `CU02`, and `CU03`; the previous “no separate transaction ID” statement was incorrect.
- All 38 JCL jobs are listed above. Jobs such as `CLOSEFIL`, `OPENFIL`, `WAITSTEP`, `TRANBKP`, `DISCGRP`, `INTCALC`, `COMBTRAN`, and `MNTTRDB2 → TRANEXTR` have Control-M chain evidence; all other rows explicitly say `no scheduler definition supplied`.
- `POSTTRAN.jcl` invokes `CBTRN02C`, which opens `TRANFILE` with `OUTPUT`; a target implementation must preserve replacement semantics unless a later compatibility decision changes it.
- `ASCII/cardxref.txt` has 50 records at 36 bytes each because it omits the 14-byte `CVACT03Y` filler; `CARDXREF.PS` and the copybook are 50 bytes. The Phase 1 loader must right-pad those ASCII records. The other ASCII fixtures retain their declared record widths.
