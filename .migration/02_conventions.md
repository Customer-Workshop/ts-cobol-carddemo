# 02_conventions — source conventions (probed from source)

## Encoding
- COBOL/copybook/JCL source files: **ASCII**, fixed-format (verified: `app/cbl/COSGN00C.cbl`
  reads clean; no iconv conversion needed).
- Data: `app/data/EBCDIC/**` is EBCDIC; `app/data/ASCII/**` is ASCII with **zoned-decimal
  overpunch signs** — compile/decode with `-fsign=EBCDIC` (verified by successful compile).
- Copybook quirk: some committed copybooks are **tab-indented** (e.g. `app/cpy/CSLKPCDY.cpy`);
  GnuCOBOL 3.1.2 accepted them directly in this session, but if a compile fails, expand tabs to
  spaces into a scratch dir first (see 07_runbook).

## Program-name prefixes (probed from `app/cbl`)
| Prefix | Meaning | Examples |
|---|---|---|
| `CO...C` | CICS online programs (screens) | COSGN00C (sign-on), COMEN01C (main menu), COADM01C (admin menu), COACTVWC/COACTUPC (account view/update), COCRD* (card list/detail/update), COTRN* (transaction list/view/add), COBIL00C (bill pay), CORPT00C (reports), COUSR* (user admin) |
| `CB...` | Batch programs | CBACT01C-04C (account file jobs), CBCUS01C (customer), CBTRN01C-03C (transaction posting chain), CBSTM03A/B (statements), CBEXPORT/CBIMPORT (branch export/import) |
| `CS...` | Shared subroutines | CSUTLDTC (date validation) |
| `COBSWAIT` | batch wait utility (COBOL wrapper of MVSWAIT) | — |

## Where things live
- COBOL: `app/cbl` · copybooks: `app/cpy` (data) and `app/cpy-bms` (symbolic maps)
- BMS maps: `app/bms` · CSD (transaction/program defs): `app/csd/CARDDEMO.CSD`
- JCL: `app/jcl` · PROCs: `app/proc` · control cards: `app/ctl` · ASM: `app/asm` (MVSWAIT, COBDATFT)
- Scheduler: `app/scheduler/CardDemo.controlm` (Control-M XML), `CardDemo.ca7`
- Data: `app/data/{ASCII,EBCDIC}` · catalog defs: `app/catlg`
- Optional extensions: `app/app-authorization-ims-db2-mq` (IMS+DB2+MQ, has `dcl/` DCLGEN and
  `ddl/`), `app/app-transaction-type-db2` (DB2, DCLGEN+DDL), `app/app-vsam-mq` (MQ+VSAM)

## Runtime protocols
- Online: pseudo-conversational CICS; COMMAREA (`CVCRD01Y` etc. in `app/cpy`) carries state;
  XCTL between screens; trancodes in CSD (CC00 sign-on, CM00 menu, CA00 admin...).
- Batch: JCL steps EXEC PGM=...; condition codes drive Control-M INCOND/OUTCOND; GDGs for
  generations; VSAM KSDS with AIX for keyed access.
- Return-code protocol for shared subroutines: linkage area result codes (see CSUTLDTC).
