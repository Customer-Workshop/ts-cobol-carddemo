# 02 — Shop conventions: encoding, naming, where things live, how to read them

Probed from the source under `app/` on 2026-09-07 (`!mf_migration_setup`). Cites are
`path:line`. Anything not cited here must be re-derived from `app/`, not from a prior-run doc.

## 1. Encoding (read this first)

| Artifact | Encoding | Evidence / how verified |
|---|---|---|
| `app/cbl/*`, `app/cpy/*`, `app/cpy-bms/*`, `app/bms/*`, `app/jcl/*`, `app/proc/*`, `app/csd/CARDDEMO.CSD` | **pure 7-bit ASCII** — no accented or non-ASCII bytes anywhere | `grep -lP '[^\x00-\x7F]' -r app/cbl app/cpy app/cpy-bms app/bms app/jcl app/csd app/proc` -> no files. No LATIN-1 / mojibake risk for this module; `iconv` conversion not needed. |
| Screen labels and messages | English, ASCII | `app/bms/*.bms`, `app/cpy/CSMSG01Y.cpy:1-8` (`'Thank you for using CardDemo application...'`) |
| `app/data/ASCII/*.txt` | ASCII fixed-width records; **signed numerics use zoned-decimal overpunch** (`{` = +0, `}` = -0, `A–I` = +1..+9, `J–R` = -1..-9) | `app/data/ASCII/acctdata.txt:1` `...00000001940{00000020200{...`; decode as in `spring-boot/src/main/java/com/carddemo/data/CobolFieldReader.java` (`signedDecimal`) |
| `app/data/EBCDIC/*.PS` | EBCDIC (IBM037), fixed-length PS images of the VSAM/PS datasets | file names `AWS.M2.CARDDEMO.*.PS`; the reference module decodes USRSEC with `Cp037` |
| COBOL numeric storage | `COMP` (binary), `COMP-3` (packed), zoned decimal, signed/unsigned | `README.md:57` |
| Compile flag consequence | GnuCOBOL must be run with `-fsign=EBCDIC` so ASCII data overpunch signs decode | blueprint note; `07_runbook.md` |
| Indentation | source is column-formatted (Area A at col 8). Two copybooks contain **tab characters** and must be re-indented before `cobc` can read them | `grep -lP '\t' app/cpy/*` -> `CSLKPCDY.cpy`, `CUSTREC.cpy` |
| Line endings | LF | `git ls-files --eol` shows `i/lf` |
| Sequence numbers | some copybooks carry cols 1–6 sequence numbers (`000700*...`) | `app/cpy/CSMSG02Y.cpy:1-4` |

## 2. Program-name prefixes (all 8 chars, upper case)

| Prefix / pattern | Denotes | Examples |
|---|---|---|
| `CO…C` | CICS **online** program (last char `C` = COBOL) | `COSGN00C` sign-on, `COMEN01C` main menu, `COADM01C` admin menu, `COACTVWC` account view, `COACTUPC` account update, `COCRDLIC/COCRDSLC/COCRDUPC` cards, `COTRN00C/01C/02C` transactions, `COBIL00C` bill pay, `CORPT00C` reports, `COUSR00C..03C` user admin |
| `CB…C` / `CB…` | **batch** COBOL program | `CBACT01C..04C` account jobs, `CBCUS01C`, `CBTRN01C..03C`, `CBSTM03A/B` statements, `CBEXPORT/CBIMPORT` |
| `CS…` | **common/shared** utility program | `CSUTLDTC` date validation (called) |
| `COBSWAIT`, assembler `MVSWAIT`, `COBDATFT` | utilities (wait, date format) | `app/cbl/COBSWAIT.cbl`, `app/asm/*.asm`, `app/maclib/*.mac` |
| Mapset / map | same stem as the program minus the trailing `C` | program `COACTVWC` -> mapset `COACTVW` (`app/bms/COACTVW.bms`), symbolic map `app/cpy-bms/COACTVW.CPY` |
| Transaction ids | 4 chars: `C` + 1–2 letter subject + 2 digits/letters | `CC00` sign-on, `CM00` main menu, `CA00` admin menu, `CAVW` account view, `CAUP` account update, `CCLI/CCDL/CCUP` cards, `CT00/01/02` transactions, `CB00` bill pay, `CR00` reports, `CU00..03` users, `CDV1` -> `COCRDSEC` (defined in CSD, **no source in `app/cbl`**) |

Authoritative trancode -> program map: `app/csd/CARDDEMO.CSD` (`DEFINE TRANSACTION(x) … PROGRAM(y)`), 18 transactions, 18 programs, 16 mapsets, 8 files.

## 3. Copybook prefixes (`app/cpy/`)

| Prefix | Content | Examples |
|---|---|---|
| `CV…Y` | **record layouts** for VSAM datasets ("data-structure for … entity") | `CVACT01Y` ACCOUNT-RECORD (RECLN 300), `CVACT02Y` CARD-RECORD, `CVACT03Y` CARD-XREF-RECORD, `CVCUS01Y` CUSTOMER-RECORD, `CVCRD01Y` card work fields, `CVTRA01Y..07Y` transaction/type/category/disclosure/balance records, `CVEXPORT` |
| `CS…Y` | **common/shared** working storage | `CSDAT01Y` date fields, `CSMSG01Y` common messages, `CSMSG02Y` abend work area, `CSUSR01Y` SEC-USER-DATA (USRSEC record), `CSUTLDPY`/`CSUTLDWY` date-utility procedure/working storage, `CSSETATY` attribute setting, `CSSTRPFY` PF-key strobe (copied inside PROCEDURE DIVISION), `CSLKPCDY` lookup codes (tabs!) |
| `CO…Y` | **online** shared structures | `COCOM01Y` `CARDDEMO-COMMAREA` (`CDEMO-FROM-TRANID`, `CDEMO-TO-PROGRAM`, `CDEMO-USER-ID`, `CDEMO-USER-TYPE` `A`/`U`, `CDEMO-PGM-CONTEXT` 0=enter/1=re-enter, customer/account/card carry-over), `COTTL01Y` screen titles, `COMEN02Y` / `COADM02Y` menu option tables |
| other | `CUSTREC.cpy` (tabs), `CODATECN.cpy` (date conversion), `COSTM01.CPY` (statement), `UNUSED1Y.cpy` | |
| `app/cpy-bms/*.CPY` | BMS **symbolic maps** generated from `app/bms/*.bms` (`…I`/`…O` field groups) | `COACTVW.CPY` |
| IBM-supplied | `DFHBMSCA`, `DFHAID`, `DFHCOMMAREA/EIB` — **not in the repo**; stub for off-host syntax checks | `app/cbl/COACTVWC.cbl:221-222` |

## 4. Online program protocol (how to read a `CO…C`)

- Pseudo-conversational: `EIBCALEN = 0` -> first entry, send map; else `MOVE DFHCOMMAREA(1:EIBCALEN) TO CARDDEMO-COMMAREA` and dispatch on the AID key / `CDEMO-PGM-CONTEXT` (`app/cbl/COMEN01C.cbl:82-86`).
- Navigation is `EXEC CICS XCTL PROGRAM(...) COMMAREA(...)` (`COACTVWC.cbl:349`); the stream's hard stop is the XCTL back to `COMEN01C`. `EXEC CICS RETURN TRANSID(...) COMMAREA(...)` ends the task (`:402`).
- File access: `EXEC CICS READ FILE(...) RIDFLD(...) RESP(WS-RESP-CD) RESP2(WS-REAS-CD)` (`COACTVWC.cbl:727,776,826`); **return-code protocol** is the CICS `RESP` value: `0` normal, `13` NOTFND, others -> error text built from `' returned RESP '`/`',RESP2 '` literals (`:97-102`). Messages are placed in `WS-RETURN-MSG PIC X(75)` then `CCARD-ERROR-MSG` (`:117,388`) and must be preserved verbatim in the target.
- Screen I/O: `EXEC CICS SEND MAP(...)`/`RECEIVE MAP(...)` (`:583,611`); field attributes via `DFHBMSCA`, PF keys via `DFHAID` + `CSSTRPFY`.
- Role check: `CDEMO-USRTYP-ADMIN`/`-USER` from `SEC-USR-TYPE` (`app/cpy/CSUSR01Y.cpy:22`, `COCOM01Y.cpy:26-28`).
- Called utilities: `CALL 'CSUTLDTC'` (date validation), batch `CALL 'COBDATFT'` assembler (`CBACT01C.cbl:231`), abends via `CALL 'CEE3ABD'`.

## 5. Datasets (CSD `DEFINE FILE` -> DSNAME)

| CICS file | DSNAME | Record copybook | Key |
|---|---|---|---|
| `ACCTDAT` | `AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS` | `CVACT01Y` | `ACCT-ID PIC 9(11)` |
| `CARDDAT` | `AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS` | `CVACT02Y` | card number `X(16)` |
| `CARDAIX` | `…CARDDATA.VSAM.AIX.PATH` | (AIX on CARDDAT by account) | |
| `CCXREF` | `AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS` | `CVACT03Y` | card number |
| `CXACAIX` | `…CARDXREF.VSAM.AIX.PATH` | (AIX on CCXREF by `XREF-ACCT-ID`) | |
| `CUSTDAT` | `AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS` | `CVCUS01Y` | `CUST-ID PIC 9(09)` |
| `TRANSACT` | `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS` | `CVTRA05Y` | transaction id |
| `USRSEC` | `AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS` | `CSUSR01Y` | `SEC-USR-ID X(08)` |

Sample data: `app/data/ASCII/{acctdata,carddata,cardxref,custdata,dailytran,discgrp,tcatbal,trancatg,trantype}.txt`; USRSEC only in EBCDIC (`app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS`). Catalog listing: `app/catlg/LISTCAT.txt`.

## 6. Batch / scheduler layout (recorded for later streams; N/A for Account View)

- JCL in `app/jcl/*.jcl|.JCL` (job name = member name, e.g. `POSTTRAN.jcl`, `INTCALC.jcl`); PROCs in `app/proc/*.prc`; IDCAMS control in `app/ctl/REPROCT.ctl`; GDG definitions `DEFGDGB/DEFGDGD.jcl`.
- Scheduler: Control-M XML `app/scheduler/CardDemo.controlm` — folders `DAILY-TransactionBackup`, `WEEKLY-TransactionTypesDBRefresh`, `WEEKLY-DisclosureGroupsRefresh`, `MONTHLY-InterestCalculation`; job chaining via `INCOND`/`OUTCOND` named `<Folder>-<Job>`; CA-7 equivalent `CardDemo.ca7`.
- Stored procedures / DB2: none in the core module (`app/app-transaction-type-db2/` is an out-of-scope extension). No SP families exist to inherit.
- Existing shop scripts: `scripts/local_compile.sh`, `scripts/run_full_batch.sh`, `run_posting.sh`, `run_interest_calc.sh` (mainframe-oriented; not used off-host — see `07_runbook.md`).

## 7. Target-side conventions (summary; authority is `01_target_state.md`)

- Legacy `app/` and reference `spring-boot/` are **read-only**. Target code goes to `backend/` and `frontend/` only.
- Entity fields keep the COBOL field name in camelCase (`ACCT-CURR-BAL` -> `acctCurrBal` -> column `acct_curr_bal`); DTO fields are business English (`currentBalance`).
- Legacy message literals are copied **verbatim** (including double spaces, e.g. `Account Filter must  be a non-zero 11 digit number`).
- Mechanical drift checks: repo skill `.agents/skills/carddemo-target-state-conformance/SKILL.md` — run before opening any PR touching `backend/` or `frontend/`.
- No human names or emails in commits or artifacts.
