# Legacy asset inventory

This inventory is based on the COBOL, copybook, BMS, JCL, CSD, Control-M, assembler, and fixture files under `app/`. Dataset names below are DD-resolved names from JCL/CSD, not working-storage variable names.

Field-level BMS positions, lengths, attributes, literals, and layout notes are in [BMS screen specifications](bms-screens.md).

## COBOL programs (`app/cbl/`, 31 files)

| Program | Type | Purpose | Files/tables read and written | CALL/XCTL/LINK targets |
|---|---|---|---|---|
| CBACT01C | batch | Reads every account VSAM record and writes three diagnostic representations (packed, array, variable-block). | ACCTFILE read; OUTFILE, ARRYFILE, VBRCFILE write | COBDATFT, CEE3ABD |
| CBACT02C | batch | Sequentially reads and displays card master records for file verification. | CARDFILE read | CEE3ABD |
| CBACT03C | batch | Sequentially reads and displays card/account/customer cross-reference records. | XREFFILE read | CEE3ABD |
| CBACT04C | batch | Calculates monthly interest from category balances and disclosure rates, writes interest transactions, and updates accounts. | TCATBALF read/rewrite; XREFFILE read; ACCTFILE read/rewrite; DISCGRP read; TRANSACT write | CEE3ABD |
| CBCUS01C | batch | Sequentially reads and displays customer master records. | CUSTFILE read | CEE3ABD |
| CBEXPORT | batch | Exports customer, account, xref, card, and transaction VSAM records into one tagged interchange file. | CUSTFILE, ACCTFILE, XREFFILE, CARDFILE, TRANSACT read; EXPFILE write | CEE3ABD |
| CBIMPORT | batch | Validates tagged interchange records and recreates customer, account, xref, transaction, and card sequential outputs, recording malformed records separately. | EXPFILE read; CUSTOUT, ACCTOUT, XREFOUT, TRNXOUT, CARDOUT, ERROUT write | CEE3ABD |
| CBSTM03A | batch | Groups transactions by card and produces fixed-width plain-text and HTML statements. | TRNXFILE, XREFFILE, CUSTFILE, ACCTFILE read through CBSTM03B; STMTFILE, HTMLFILE write | CBSTM03B, CEE3ABD |
| CBSTM03B | batch subroutine | Performs open/read/close operations on the four statement input files through a linkage area. | TRNXFILE, XREFFILE, CUSTFILE, ACCTFILE read | — (called by CBSTM03A) |
| CBTRN01C | batch | Reads daily transactions, resolves card/account/customer references, and displays the resulting records for diagnostic validation. | DALYTRAN, XREFFILE, ACCTFILE, CUSTFILE, CARDFILE, TRANFILE read | CEE3ABD |
| CBTRN02C | batch | Validates daily transactions, writes accepted postings and category-balance updates, and writes rejected records with reason trailers. | DALYTRAN read; XREFFILE read; ACCTFILE/TCATBALF I-O; TRANFILE output; DALYREJS output | CEE3ABD |
| CBTRN03C | batch | Reads posted transactions and lookup files to produce a dated transaction report. | TRANFILE, CARDXREF, TRANTYPE, TRANCATG, DATEPARM read; TRANREPT write | CEE3ABD |
| COACTUPC | online CICS | Searches and updates account/customer details, preserving old/new values through pseudo-conversational confirmation. | ACCTDAT read/update; CUSTDAT read/update; CARDDAT/CARDAIX/CXACAIX read | COCRDUPC, COCRDLIC, COCRDSLC, COMEN01C; CICS XCTL |
| COACTVWC | online CICS | Searches and displays account, customer, and linked card information without rewriting master data. | ACCTDAT, CUSTDAT, CARDDAT, CARDAIX, CXACAIX read | COCRDLIC, COCRDUPC, COCRDSLC, COMEN01C; CICS XCTL |
| COADM01C | online CICS | Displays the administrator menu and routes authorized users to user-management functions. | No VSAM data access in visible program | COUSR00C, COMEN01C; CICS XCTL |
| COBIL00C | online CICS | Reads an account balance, confirms a full-balance bill payment, posts a payment transaction, and rewrites the account. | ACCTDAT read/update; CXACAIX read; TRANSACT browse/write | CICS XCTL to COMMAREA target |
| COBSWAIT | utility | Converts a centisecond wait parameter and calls the assembler wait routine. | No data files | MVSWAIT |
| COCRDLIC | online CICS | Lists cards selected by account or customer criteria and routes to card detail/update. | CARDDAT/CARDAIX/CXACAIX/CUSTDAT read | COCRDSLC, COCRDUPC, COMEN01C; CICS XCTL |
| COCRDSLC | online CICS | Reads and displays one card with its account/customer context. | CARDDAT, CARDAIX, ACCTDAT, CUSTDAT read | COCRDLIC, COCRDUPC, COMEN01C; CICS XCTL |
| COCRDUPC | online CICS | Fetches a card, validates changed card fields, and rewrites CARDDAT after confirmation. | CARDDAT read/update; CARDAIX read | COCRDLIC, COCRDSLC, COMEN01C; CICS XCTL |
| COMEN01C | online CICS | Displays the main menu and dispatches selected account, card, transaction, report, and payment functions. | No master file read in menu flow | COACTVWC, COACTUPC, COCRDLIC, COCRDSLC, COCRDUPC, COTRN00C, COTRN01C, COTRN02C, CORPT00C, COBIL00C; CICS XCTL |
| CORPT00C | online CICS | Collects report criteria and starts the transaction report job/flow. | DATEPARM/report context; no direct master rewrite | CBTRN03C; CSUTLDTC; CICS START/XCTL |
| COSGN00C | online CICS | Authenticates a user against the security file and establishes signed-on COMMAREA context. | USRSEC read | COMEN01C; CICS XCTL |
| COTRN00C | online CICS | Lists transactions using browse/navigation criteria and routes to transaction view/add. | TRANSACT browse/read | COTRN01C, COTRN02C, COMEN01C; CICS XCTL |
| COTRN01C | online CICS | Displays one selected transaction and its lookup descriptions. | TRANSACT read; TRANTYPE/TRANCATG lookup | COTRN00C, COTRN02C, COMEN01C; CICS XCTL |
| COTRN02C | online CICS | Validates and adds a transaction from the transaction-add map. | CXACAIX/CCXREF read; TRANSACT browse/write | CSUTLDTC; CICS XCTL |
| COUSR00C | online CICS | Lists user-security records for administration. | USRSEC browse/read | COUSR01C, COUSR02C, COUSR03C, COADM01C; CICS XCTL |
| COUSR01C | online CICS | Validates and adds a user-security record. | USRSEC read/write | COUSR00C, COADM01C; CICS XCTL |
| COUSR02C | online CICS | Fetches and updates a user-security record. | USRSEC read/update | COUSR00C, COADM01C; CICS XCTL |
| COUSR03C | online CICS | Fetches and deletes a user-security record after confirmation. | USRSEC read/delete | COUSR00C, COADM01C; CICS XCTL |
| CSUTLDTC | utility | Validates/converts a date through the source date utility interface. | No data files | CEEDAYS |

## BMS maps (`app/bms/`, 17 files)

| Map | Mapset | Title/literals | Fields and attributes (summary) | Program |
|---|---|---|---|---|
| CACTUPA | COACTUP | Account Update | Account/customer editable fields; numeric/date fields `UNPROT,FSET`; message `ERRMSG` `ASKIP,BRT,FSET`; PF5/PF12 labels | COACTUPC |
| CACTVWA | COACTVW | Account View | Account search `ACCTSID`; account/customer display fields default-protected; `ERRMSG`/`INFOMSG` | COACTVWC |
| COADM1A | COADM01 | Administrator Menu | Menu option fields and PF-key footer | COADM01C |
| COBIL0A | COBIL00 | Bill Payment | Account ID, current balance, confirmation, error/message fields | COBIL00C |
| CCRDLIA | COCRDLI | Card List | Account/card/customer search and list fields; protected output and selectable rows | COCRDLIC |
| CCRDSLA | COCRDSL | Card Detail | Card number, account, CVV/name/expiry/status display fields | COCRDSLC |
| CCRDUPA | COCRDUP | Card Update | Search fields plus editable name/expiry/status; PF5/PF12 fields; error message | COCRDUPC |
| COMEN1A | COMEN01 | Main Menu | Menu selection and common header/footer | COMEN01C |
| CORPT0A | CORPT00 | Transaction Reports | Start/end date and report selection fields | CORPT00C |
| COSGN0A | COSGN00 | Signon | User ID/password and signon message | COSGN00C |
| COTRN0A | COTRN00 | Transaction List | Account/card filters, browse controls, transaction rows | COTRN00C |
| COTRN1A | COTRN01 | Transaction View | Transaction detail output fields | COTRN01C |
| COTRN2A | COTRN02 | Transaction Add | Account/card key, type/category/source/amount/date/merchant fields; confirmation/error | COTRN02C |
| COUSR0A | COUSR00 | User List | User search/list and PF actions | COUSR00C |
| COUSR1A | COUSR01 | Add User | User identity/role fields and confirmation | COUSR01C |
| COUSR2A | COUSR02 | Update User | User search/edit fields and confirmation | COUSR02C |
| COUSR3A | COUSR03 | Delete User | User key/details and confirmation | COUSR03C |

## Copybooks (`app/cpy/`, 30 files)

The table records the source `COPY` includers, including optional-module programs where they use a base copybook. Record length is the fixed source layout length; `variable` means a work area or screen/state structure rather than a persisted fixed record.

| Copybook | Category | Defines | Record length | Including programs |
|---|---|---|---:|---|
| `COADM02Y.cpy` | BMS screen structure | Administrator menu option literals and screen data names | — | COADM01C |
| `COCOM01Y.cpy` | COMMAREA state | Common CARDDEMO COMMAREA fields: caller/next program, transaction, user and map context | variable | COACTUPC, COACTVWC, COADM01C, COBIL00C, COCRDLIC, COCRDSLC, COCRDUPC, COMEN01C, COPAUS0C, COPAUS1C, CORPT00C, COSGN00C, COTRN00C, COTRN01C, COTRN02C, COTRTLIC, COTRTUPC, COUSR00C, COUSR01C, COUSR02C, COUSR03C |
| `CODATECN.cpy` | IBM-supplied utility | COBDATFT date conversion input/output record | variable | CBACT01C |
| `COMEN02Y.cpy` | BMS screen structure | Main-menu option and output fields | variable | COMEN01C |
| `COSTM01.CPY` | BMS screen structure | Statement output line and statement formatting work areas | variable | CBSTM03A |
| `COTTL01Y.cpy` | constants-lookup | Common screen title literals | — | COACTUPC, COACTVWC, COADM01C, COBIL00C, COCRDLIC, COCRDSLC, COCRDUPC, COMEN01C, COPAUS0C, COPAUS1C, CORPT00C, COSGN00C, COTRN00C, COTRN01C, COTRN02C, COTRTLIC, COTRTUPC, COUSR00C, COUSR01C, COUSR02C, COUSR03C |
| `CSDAT01Y.cpy` | COMMAREA state | Current date/time and formatted timestamp work areas | variable | COACTUPC, COACTVWC, COADM01C, COBIL00C, COCRDLIC, COCRDSLC, COCRDUPC, COMEN01C, COPAUS0C, COPAUS1C, CORPT00C, COSGN00C, COTRN00C, COTRN01C, COTRN02C, COTRTLIC, COTRTUPC, COUSR00C, COUSR01C, COUSR02C, COUSR03C |
| `CSLKPCDY.cpy` | COMMAREA state | Account-update lock/key and CICS file-control state | variable | COACTUPC |
| `CSMSG01Y.cpy` | constants-lookup | Common informational and error message literals | — | COACTUPC, COACTVWC, COADM01C, COBIL00C, COCRDLIC, COCRDSLC, COCRDUPC, COMEN01C, COPAUS0C, COPAUS1C, CORPT00C, COSGN00C, COTRN00C, COTRN01C, COTRN02C, COTRTLIC, COTRTUPC, COUSR00C, COUSR01C, COUSR02C, COUSR03C |
| `CSMSG02Y.cpy` | constants-lookup | Abend/file-error message literals and work fields | — | COACTUPC, COACTVWC, COCRDLIC, COCRDSLC, COCRDUPC, COPAUS0C, COPAUS1C, COTRTUPC |
| `CSSETATY.cpy` | COMMAREA state | Common validation flag-setting procedures and flags | variable | COACTUPC, COTRTUPC |
| `CSSTRPFY.cpy` | COMMAREA state | Stored PF-key and navigation state | variable | COACTUPC, COACTVWC, COCRDLIC, COCRDSLC, COCRDUPC, COTRTLIC, COTRTUPC |
| `CSUSR01Y.cpy` | COMMAREA state | Signed-on user identity and authorization data | variable | COACTUPC, COACTVWC, COADM01C, COCRDLIC, COCRDSLC, COCRDUPC, COMEN01C, COSGN00C, COTRTLIC, COTRTUPC, COUSR00C, COUSR01C, COUSR02C, COUSR03C |
| `CSUTLDPY.cpy` | IBM-supplied utility | Date edit/conversion procedures used by online validation | variable | COACTUPC |
| `CSUTLDWY.cpy` | IBM-supplied utility | Date utility working storage and century/date decomposition | variable | COACTUPC, COTRTUPC |
| `CUSTREC.cpy` | data record layout | Statement customer record layout | 500 | CBSTM03A |
| `CVACT01Y.cpy` | data record layout | Account master record (`ACCOUNT-RECORD`) | 300 | CBACT01C, CBACT04C, CBEXPORT, CBIMPORT, CBSTM03A, CBTRN01C, CBTRN02C, COACCT01, COACTUPC, COACTVWC, COBIL00C, COCRDSLC, COCRDUPC, COPAUA0C, COPAUS0C, COTRN02C |
| `CVACT02Y.cpy` | data record layout | Card master record (`CARD-RECORD`) | 150 | CBACT02C, CBEXPORT, CBIMPORT, CBTRN01C, COACTVWC, COCRDLIC, COCRDSLC, COCRDUPC, COPAUS0C, COTRTLIC |
| `CVACT03Y.cpy` | data record layout | Card/customer/account cross-reference record | 50 | CBACT03C, CBACT04C, CBEXPORT, CBIMPORT, CBSTM03A, CBTRN01C, CBTRN02C, CBTRN03C, COACTUPC, COACTVWC, COBIL00C, COCRDSLC, COCRDUPC, COPAUA0C, COPAUS0C, COTRN02C |
| `CVCRD01Y.cpy` | BMS screen structure | Card screen work area and attention-key conditions | variable | COACTUPC, COACTVWC, COCRDLIC, COCRDSLC, COCRDUPC, COTRTLIC, COTRTUPC |
| `CVCUS01Y.cpy` | data record layout | Customer master record | 500 | CBCUS01C, CBEXPORT, CBIMPORT, CBTRN01C, COACTUPC, COACTVWC, COCRDSLC, COCRDUPC, COPAUA0C, COPAUS0C |
| `CVEXPORT.cpy` | data record layout | Tagged export interchange record | 500 | CBEXPORT, CBIMPORT |
| `CVTRA01Y.cpy` | data record layout | Transaction-category balance record | 50 | CBACT04C, CBTRN02C |
| `CVTRA02Y.cpy` | data record layout | Disclosure-group interest-rate record | 50 | CBACT04C |
| `CVTRA03Y.cpy` | data record layout | Transaction-type lookup record | 60 | CBTRN03C |
| `CVTRA04Y.cpy` | data record layout | Transaction-category lookup record | 60 | CBTRN03C |
| `CVTRA05Y.cpy` | data record layout | Posted transaction record | 350 | CBACT04C, CBEXPORT, CBIMPORT, CBTRN01C, CBTRN02C, CBTRN03C, COBIL00C, CORPT00C, COTRN00C, COTRN01C, COTRN02C |
| `CVTRA06Y.cpy` | data record layout | Daily transaction input record | 350 | CBTRN01C, CBTRN02C |
| `CVTRA07Y.cpy` | constants-lookup | Transaction report header and report formatting literals | variable | CBTRN03C |
| `UNUSED1Y.cpy` | constants-lookup | Unused legacy customer/user-shaped data structure; no active COPY includers | variable | — |

## JCL jobs (`app/jcl/`, 38 files)

| Job | Utility/program and actual work | Meaningful inputs → outputs | Stream placement |
|---|---|---|---|
| ACCTFILE | IDCAMS DEFINE/REPRO account KSDS | `ACCTDATA.PS` → `ACCTDATA.VSAM.KSDS` | Initial master load |
| CARDFILE | IDCAMS DEFINE/REPRO card KSDS/AIX | `CARDDATA.PS` → card KSDS and AIX | Initial master load |
| CUSTFILE | IDCAMS DEFINE/REPRO customer KSDS | `CUSTDATA.PS` → `CUSTDATA.VSAM.KSDS` | Initial master load |
| XREFFILE | IDCAMS DEFINE/REPRO xref KSDS/AIX/path | `CARDXREF.PS` → xref KSDS/AIX | Initial master load |
| DISCGRP | IDCAMS DEFINE/REPRO disclosure KSDS | `DISCGRP.PS` → `DISCGRP.VSAM.KSDS` | Weekly disclosure refresh |
| TCATBALF | IDCAMS DEFINE/REPRO category balance KSDS | `TCATBALF.PS` → `TCATBALF.VSAM.KSDS` | Interest input refresh |
| TRANCATG | IDCAMS DEFINE/REPRO category KSDS | `TRANCATG.PS` → category KSDS | Reference load |
| TRANTYPE | IDCAMS DEFINE/REPRO type KSDS | `TRANTYPE.PS` → type KSDS | Reference load |
| TRANFILE | IDCAMS DEFINE/REPRO transaction input | `DALYTRAN.PS.INIT` → `DALYTRAN.PS` | Daily input preparation |
| TRANBKP | REPROC/IDCAMS backup and redefine | `TRANSACT.VSAM.KSDS` → `TRANSACT.BKUP(+1)`; recreates transaction KSDS | Control-M daily chain |
| POSTTRAN | CBTRN02C | `DALYTRAN.PS`, xref/account/category VSAM → transaction KSDS, `DALYREJS(+1)` | After daily input; no scheduler definition in supplied Control-M |
| TRANIDX | IDCAMS alternate index/path | `TRANSACT.VSAM.KSDS` → transaction AIX/path | After transaction KSDS definition |
| INTCALC | CBACT04C | category/xref/account/disclosure VSAM → transaction output/account/category updates | Control-M monthly chain |
| COMBTRAN | SORT | transaction backup/current datasets → combined transaction dataset | Control-M monthly chain |
| CREASTMT | CBSTM03A | transaction/xref/account/customer VSAM → statement and HTML GDG outputs | JCL supplied; no scheduler definition |
| TRANREPT | PROC `TRANREPT`: REPROC, SORT, CBTRN03C | transaction KSDS → sorted GDG, `TRANREPT(+1)` report | Submitted from CICS/proc |
| CLOSEFIL | SDSF/Control-M operator step | CICS close commands; no application dataset output | Control-M daily/weekly pre-step |
| OPENFIL | SDSF/Control-M operator step | CICS open commands; no application dataset output | Control-M chain post-step |
| WAITSTEP | COBSWAIT | wait parameter → elapsed delay | Control-M chain barrier |
| DUSRSECJ | IEFBR14/IEBGENER/IDCAMS | inline security records → `USRSEC.PS` → `USRSEC.VSAM.KSDS` | Security initialization |
| DEFGDGB | IDCAMS | defines base GDGs for application backups/reports | Dataset setup |
| DEFGDGD | IDCAMS/IEBGENER | defines DB2/reference GDGs and copies PS generations | Dataset setup |
| ESDSRRDS | IDCAMS | defines/loads `USRSEC.VSAM.ESDS` and `.RRDS` | VSAM format examples |
| READACCT | CBACT01C | account KSDS → packed/array/VB PS diagnostics | Diagnostic batch |
| READCARD | CBACT02C | card KSDS → SYSOUT diagnostic display | Diagnostic batch |
| READCUST | CBCUS01C | customer KSDS → SYSOUT diagnostic display | Diagnostic batch |
| READXREF | CBACT03C | xref KSDS → SYSOUT diagnostic display | Diagnostic batch |
| REPTFILE | IDCAMS | defines report GDG | Report setup |
| PRTCATBL | IDCAMS/SORT | category balance KSDS → report and backup generation | Reporting utility |
| DALYREJS | IDCAMS | defines daily reject GDG used by POSTTRAN | Reject setup |
| CBEXPORT | CBEXPORT | master VSAM files → `EXPORT.DATA` | Migration/export utility |
| CBIMPORT | CBIMPORT | `EXPORT.DATA` → imported PS outputs and error output | Migration/import utility |
| FTPJCL | FTP | local report/export datasets → remote FTP target from SYSIN | Transfer utility |
| TXT2PDF1 | text-to-PDF utility | statement/report text → PDF output | Presentation utility |
| INTRDRJ1 | internal reader submitter | generated JCL stream → JES internal reader | Job submission utility |
| INTRDRJ2 | internal reader submitter | generated JCL stream → JES internal reader | Job submission utility |
| CBADMCDJ | IDCAMS/CICS definitions | CSD map/program/transaction definitions → CICS resource group | CICS deployment |
| DEFCUST | IDCAMS | defines customer-related VSAM resources | Dataset setup |


## Procedures, controls, catalog, CSD, and scheduler

- `app/proc/REPROC.prc` is the parameterized IDCAMS `REPRO INFILE(FILEIN) OUTFILE(FILEOUT)` procedure used by transaction backup/report jobs; it is not an application program.
- `app/proc/TRANREPT.prc` runs the transaction backup, sorts the backup by transaction card number, then invokes `CBTRN03C` with the sorted GDG, lookup KSDSs, date parameter, and report GDG.
- `app/ctl/REPROCT.ctl` contains the same `REPRO` control statement for procedure substitution. `app/ctl/` also contains DB2/authorization controls in optional modules.
- `app/catlg/LISTCAT.txt` is an IDCAMS catalog listing showing PS input datasets and the corresponding VSAM clusters; it is evidence for dataset names/types, not executable application logic.
- `app/csd/CARDDEMO.CSD` defines base CICS FILE resources (`ACCTDAT`, `CARDDAT`, `CARDAIX`, `CCXREF`, etc.), programs, transactions, mapsets, and dataset names. `app/csd/CARDDEMO.CSD` declares real IDs such as `CAVW`, `CAUP`, `CCLI`, `CCDL`, `CCUP`, `CB00`, `CT00`, `CT01`, `CT02`, `CU00`–`CU03`, and `CR00`.
- `app/scheduler/CardDemo.controlm` contains four relevant chains: daily `CLOSEFIL → TRANBKP → WAITSTEP → OPENFIL`; weekly transaction-type `MNTTRDB2 → TRANEXTR`; weekly disclosure refresh `CLOSEFIL → DISCGRP → WAITSTEP → OPENFIL`; and monthly interest `CLOSEFIL → INTCALC → COMBTRAN → WAITSTEP → OPENFIL`. `app/scheduler/CardDemo.ca7` is a Control-M export showing a `CLOSEFIL` trigger for optional `CBPAUP0J`; it is not evidence that all JCL jobs have schedules.

## Assembler (`app/asm/`)

| Routine | Calling convention and behavior |
|---|---|
| `COBDATFT` | Caller passes the address of `CODATECN-REC` in the first argument register (`R1`). Input type 1 is `YYYYMMDD`; type 2 is `YYYY-MM-DD`; output type selects the requested conversion. Incompatible type/separator combinations produce `INVALID INPUT`; return code is `R15=0`. |
| `MVSWAIT` | First argument points to a fullword delay value; routine loads it and calls `ASMWAIT`, restores registers, and returns `R15=0`. |

## Optional modules

- `app/app-authorization-ims-db2-mq/`: `COPAUS0C`/`COPAUS1C`/`COPAUS2C` online summary/detail/process flows (`COPAU00`/`COPAU01` maps), `COPAUA0C` MQ authorization processor, `CBPAUP0C` expiry purge, and IMS load/unload utilities (`PAUDBLOD`, `PAUDBUNL`, `DBUNLDGS`). Depends on IMS DBD/PSB, DB2 declarations, and IBM MQ request/reply resources.
- `app/app-transaction-type-db2/`: `COTRTUPC`/`COTRTLIC` online DB2 transaction-type/category maintenance, `COBTUPDT` table maintenance, `CREADB21` database creation/load, `TRANEXTR` extraction, and `MNTTRDB2` refresh. Depends on DB2 DDL/DCL/control members.
- `app/app-vsam-mq/`: `COACCT01` and `CODATE01` VSAM/MQ request-reply examples plus `CRDDEMOM.csd`; depends on VSAM and IBM MQ, not IMS/DB2.

## Data files (`app/data/`)

| File | Encoding | LRECL / records | Layout | VSAM type |
|---|---|---:|---|---|
| `AWS.M2.CARDDEMO.ACCDATA.PS` | EBCDIC fixed | 300 / 50 | `CVACT01Y` | PS source for KSDS |
| `AWS.M2.CARDDEMO.ACCTDATA.PS` | EBCDIC fixed | 300 / 50 | `CVACT01Y` | PS source for `ACCTDATA.VSAM.KSDS` |
| `AWS.M2.CARDDEMO.CARDDATA.PS` | EBCDIC fixed | 150 / 50 | `CVACT02Y` | PS source for `CARDDATA.VSAM.KSDS`; 7,500 bytes divide exactly by the declared 150-byte RECLN |
| `AWS.M2.CARDDEMO.CARDXREF.PS` | EBCDIC fixed | 50 / 50 | `CVACT03Y` | PS source for xref KSDS |
| `AWS.M2.CARDDEMO.CUSTDATA.PS` | EBCDIC fixed | 500 / 50 | `CVCUS01Y` | PS source for customer KSDS |
| `AWS.M2.CARDDEMO.DALYTRAN.PS` | EBCDIC fixed | 350 / 300 | `CVTRA06Y` | PS input |
| `AWS.M2.CARDDEMO.DALYTRAN.PS.INIT` | EBCDIC fixed | 350 / 1 | `CVTRA06Y` | PS seed |
| `AWS.M2.CARDDEMO.DISCGRP.PS` | EBCDIC fixed | 50 / 51 | `CVTRA02Y` | PS source for disclosure KSDS |
| `AWS.M2.CARDDEMO.EXPORT.DATA.PS` | EBCDIC fixed | 500 / 500 | CBEXPORT interchange record | PS export |
| `AWS.M2.CARDDEMO.TCATBALF.PS` | EBCDIC fixed | 50 / 50 | `CVTRA01Y` | PS source for category KSDS |
| `AWS.M2.CARDDEMO.TRANCATG.PS` | EBCDIC fixed | 60 / 18 | `CVTRA04Y` | PS source for category KSDS |
| `AWS.M2.CARDDEMO.TRANTYPE.PS` | EBCDIC fixed | 60 / 7 | `CVTRA03Y` | PS source for type KSDS |
| `AWS.M2.CARDDEMO.USRSEC.PS` | EBCDIC fixed | 80 / 10 | security copybook in `CSUSR01Y` | PS source for security KSDS |
| `ASCII/acctdata.txt` | ASCII fixture | 300 / 50 records | `CVACT01Y` | fixture for account KSDS |
| `ASCII/carddata.txt` | ASCII fixture | 150 data bytes + newline / 50 | `CVACT02Y` | fixture for card KSDS |
| `ASCII/cardxref.txt` | ASCII fixture | 36 data bytes + newline / 50 records | `CVACT03Y`; ASCII omits its trailing 14-byte filler | fixture for xref KSDS; loader must right-pad 14 spaces |
| `ASCII/custdata.txt` | ASCII fixture | 500 data bytes + newline / 50 | `CVCUS01Y` | fixture for customer KSDS |
| `ASCII/dailytran.txt` | ASCII fixture | 350 data bytes + newline / 300 | `CVTRA06Y` | sequential fixture |
| `ASCII/discgrp.txt` | ASCII fixture | 50 data bytes + newline / 51 | `CVTRA02Y` | fixture for disclosure KSDS |
| `ASCII/tcatbal.txt` | ASCII fixture | 50 data bytes + newline / 50 | `CVTRA01Y` | fixture for category KSDS |
| `ASCII/trancatg.txt` | ASCII fixture | 60 data bytes + newline / 18 | `CVTRA04Y` | fixture for category KSDS |
| `ASCII/trantype.txt` | ASCII fixture | 60 data bytes + newline / 7 | `CVTRA03Y` | fixture for type KSDS |
