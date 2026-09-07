# 04 — Boundary register (module-wide, APPEND-ONLY)

Owned by `!mf_migration_setup`; rows are appended by `!mf_stream_analysis` and resolved by
`!mf_boundary_resolution`. Never rewrite or delete a row; to change a row's state add a new row
with the same `Id` and a later date. Resolution notes live in
`functional/CardDemo/boundaries/<Id>.md`.

## Schema

| Column | Definition |
|---|---|
| `Id` | `B-<nnnn>`, sequential across the module, never reused |
| `Date` | ISO date the row was written |
| `Stream` | stream that found it (e.g. `AccountView`) |
| `Kind` | `PROGRAM` (calls/XCTLs into another stream's program) · `DATASET` (reads/writes data owned elsewhere) · `SCHEDULER` (job dependency) · `EXTERNAL` (MQ, DB2, IMS, FTP, SSO, other system) · `SHARED-UTIL` (common utility) · `DATA-TARGET` (schema/ownership question) |
| `Legacy side` | program / dataset / job on the legacy side, with `path:line` cite |
| `Other side` | what is on the other side of the boundary (stream, system, team) |
| `Direction` | `IN` · `OUT` · `BOTH` |
| `State` | `OPEN` · `PROPOSED` · `DECIDED` · `IMPLEMENTED` · `RETIRED` |
| `Decision ref` | `D-<nnnn>` in `06_decisions.md` once decided |
| `Note` | one line; details in `functional/CardDemo/boundaries/<Id>.md` |

## Register

| Id | Date | Stream | Kind | Legacy side | Other side | Direction | State | Decision ref | Note |
|---|---|---|---|---|---|---|---|---|---|
| B-0000 | 2026-09-07 | (setup) | — | — | — | — | RETIRED | — | Register initialized by `!mf_migration_setup`; no boundaries analysed yet (Account View analysis pending `!mf_stream_analysis`) |
| B-0001 | 2026-09-07 | (inventory) | DATASET | `ACCTDAT` VSAM KSDS `app/csd/CARDDEMO.CSD:1-2`; `app/cbl/COACTVWC.cbl:776` | S-01/02/10 read, S-02/10 REWRITE; batch S-16/19/20/21/22 | BOTH | OPEN | — | classify only (undecided); details `functional/CardDemo/CardDemo_inventory.md` §8 |
| B-0002 | 2026-09-07 | (inventory) | DATASET | `CUSTDAT` `CARDDEMO.CSD:50-52`; `COACTVWC.cbl:826` | S-01/02 read, S-02 REWRITE; batch S-20/21/22 | BOTH | OPEN | — | classify only |
| B-0003 | 2026-09-07 | (inventory) | DATASET | `CARDDAT` `CARDDEMO.CSD:25-26`; `COCRDLIC.cbl:1129` | S-03/04/05; batch S-21/22 | BOTH | OPEN | — | classify only |
| B-0004 | 2026-09-07 | (inventory) | DATASET | `CCXREF` `CARDDEMO.CSD:37-39`; `COTRN02C.cbl:611` | S-08 read; batch S-16/19/20/21/22 | BOTH | OPEN | — | classify only |
| B-0005 | 2026-09-07 | (inventory) | DATASET | `TRANSACT` `CARDDEMO.CSD:76-77`; `COTRN02C.cbl:713` | S-06/07 read, S-08/10 WRITE; batch S-09/15/16/19/20/22 | BOTH | OPEN | — | classify only |
| B-0006 | 2026-09-07 | (inventory) | DATASET | `USRSEC` `CARDDEMO.CSD:88-89`; `COSGN00C.cbl:211` | S-01 sign-on read; S-11 CRUD | BOTH | OPEN | — | classify only |
| B-0007 | 2026-09-07 | (inventory) | DATASET | `CXACAIX` AIX path over CCXREF `CARDDEMO.CSD:63-65`; `COACTVWC.cbl:727` | S-01/02/08/10; `INTCALC.jcl:32` | IN | OPEN | — | classify only; alternate-index access pattern |
| B-0008 | 2026-09-07 | (inventory) | DATASET | `CARDAIX` AIX path over CARDDAT `CARDDEMO.CSD:13-14`; `COCRDSLC.cbl:742` | S-04/05 | IN | OPEN | — | classify only |
| B-0009 | 2026-09-07 | (inventory) | PROGRAM | CICS XCTL `COSGN00C` -> `COMEN01C`/`COADM01C` `app/cbl/COSGN00C.cbl:231,236` | menus (all online streams) | OUT | OPEN | — | classify only; transaction switch with COMMAREA |
| B-0010 | 2026-09-07 | (inventory) | PROGRAM | menu dispatch `COMEN01C.cbl:185` and return `XCTL PROGRAM(CDEMO-TO-PROGRAM)` `COACTVWC.cbl:349` | S-01 hard stop <-> S-02..S-10 | BOTH | OPEN | — | classify only; Account View hard stop |
| B-0011 | 2026-09-07 | (inventory) | PROGRAM | `COMEN01C` opt 11 -> `COPAUS0C` `COMEN01C.cbl:147-158`; `COMEN02Y.cpy:89` | authorization extension (out of module) | OUT | OPEN | — | classify only; runtime INQUIRE guard |
| B-0012 | 2026-09-07 | (inventory) | PROGRAM | `COADM01C` opts 5,6 -> `COTRTLIC`/`COTRTUPC` `COADM01C.cbl:146`; `COADM02Y.cpy:49,53` | tran-type DB2 extension (out of module) | OUT | OPEN | — | classify only; no runtime guard |
| B-0013 | 2026-09-07 | (inventory) | PROGRAM | `CDV1` -> `COCRDSEC` `app/csd/CARDDEMO.CSD:211,388` | absent module (no source) | OUT | OPEN | — | classify only; HIGH risk absent module |
| B-0014 | 2026-09-07 | (inventory) | SHARED-UTIL | `CALL 'CSUTLDTC'` `COTRN02C.cbl:393,413`; `CORPT00C.cbl:392,412` | S-01 owner (customer decision) | IN | OPEN | — | classify only; S-01 does not itself call it |
| B-0015 | 2026-09-07 | (inventory) | EXTERNAL | LE `CEEDAYS` `CSUTLDTC.cbl:116`; `CEE3ABD` e.g. `CBACT01C.cbl:410` | z/OS Language Environment | OUT | OPEN | — | classify only |
| B-0016 | 2026-09-07 | (inventory) | SHARED-UTIL | `COBSWAIT` -> `MVSWAIT` asm `COBSWAIT.cbl:38`; `WAITSTEP.jcl:22` | all scheduler chains | IN | OPEN | — | classify only; assembler utility |
| B-0017 | 2026-09-07 | (inventory) | EXTERNAL | `COBDATFT` asm `CBACT01C.cbl:231`; `app/asm/COBDATFT.asm` | S-21 | OUT | OPEN | — | classify only; assembler utility |
| B-0018 | 2026-09-07 | (inventory) | EXTERNAL | `CORPT00C` WRITEQ TD `JOBS` -> INTRDR `EXEC PROC=TRANREPT` `CORPT00C.cbl:93,517-518` | JES / batch S-09 (`TRANREPT.jcl:59`) | OUT | OPEN | — | classify only; online->batch hand-off |
| B-0019 | 2026-09-07 | (inventory) | SCHEDULER | Control-M DAILY INCOND/OUTCOND `app/scheduler/CardDemo.controlm:4-24` | Control-M | BOTH | OPEN | — | classify only |
| B-0020 | 2026-09-07 | (inventory) | SCHEDULER | Control-M MONTHLY `CardDemo.controlm:65-91` | Control-M | BOTH | OPEN | — | classify only |
| B-0021 | 2026-09-07 | (inventory) | SCHEDULER | Control-M WEEKLY `MNTTRDB2`/`TRANEXTR` (no JCL) + DISCGRP INCOND `CardDemo.controlm:27-62` | Control-M / DB2 extension | IN | OPEN | — | classify only; HIGH: scheduled jobs without JCL |
| B-0022 | 2026-09-07 | (inventory) | SCHEDULER | CA-7 triggers `CardDemo.ca7:43,70,340-421,468,495` incl. `CBPAUP0J` -> `POSTTRAN` | CA-7 / authorization extension | BOTH | OPEN | — | classify only; POSTTRAN scheduled in CA-7 only |
| B-0023 | 2026-09-07 | (inventory) | DATASET | batch PS/GDG hand-offs `POSTTRAN.jcl:31,38`; `INTCALC.jcl:41`; `TRANREPT.jcl:33,55,80`; `CREASTMT.JCL:48-96`; `CBEXPORT.jcl:63`; `CBIMPORT.jcl:29-57` | upstream feeds / downstream consumers | BOTH | OPEN | — | classify only |
| B-0024 | 2026-09-07 | (inventory) | DATASET | batch-only VSAM `TCATBALF`, `DISCGRP`, `TRANTYPE`, `TRANCATG` `POSTTRAN.jcl:42`; `INTCALC.jcl:28,36`; `TRANREPT.jcl:70,72` | S-23 ref-data jobs | BOTH | OPEN | — | classify only |
| B-0025 | 2026-09-07 | (inventory) | EXTERNAL | `TXT2PDF1` IKJEFT1B + external load lib `TXT2PDF1.JCL:24`; `FTPJCL.JCL:30` | external tools | OUT | OPEN | — | classify only |
| B-0026 | 2026-09-07 | (inventory) | EXTERNAL | sign-on authentication via USRSEC (RACF substitute) `COSGN00C.cbl:211-236` | target identity/auth | IN | OPEN | — | classify only |
| B-0027 | 2026-09-07 | (inventory) | DATA-TARGET | COMMAREA contract `app/cpy/COCOM01Y.cpy` shared by all online programs | target session/state model | BOTH | OPEN | — | classify only |
| B-0001 | 2026-09-07 | AccountView | DATASET | `READ DATASET('ACCTDAT') INTO ACCOUNT-RECORD RIDFLD(ACCT-ID 9(11))` `app/cbl/COACTVWC.cbl:776-784`; RESP NORMAL/NOTFND/OTHER `:786-819` | VSAM KSDS `CARDDEMO.CSD:1-2`; writers S-02/S-10/batch | OUT | OPEN | — | Refines B-0001; class B4; physical layer VSAM KSDS; read-only in S-01; contract RESOLVED, decision UNDECIDED; `functional/CardDemo/AccountView_analysis.md` §5 |
| B-0002 | 2026-09-07 | AccountView | DATASET | `READ DATASET('CUSTDAT') INTO CUSTOMER-RECORD RIDFLD(CUST-ID 9(09))` `COACTVWC.cbl:826-834`; RESP `:836-868` | VSAM KSDS `CSD:50-52`; writers S-02/batch | OUT | OPEN | — | Refines B-0002; class B4; VSAM KSDS; read-only; contract RESOLVED, decision UNDECIDED; analysis §5 |
| B-0007 | 2026-09-07 | AccountView | DATASET | `READ DATASET('CXACAIX') INTO CARD-XREF-RECORD RIDFLD(ACCT-ID)` `COACTVWC.cbl:727-735`; RESP `:737-769` | VSAM AIX PATH over CCXREF `CSD:63-65` (base `:37-39`) | OUT | OPEN | — | Refines B-0007; class B4; physical layer VSAM AIX path; **UNRESOLVED**: AIX UNIQUEKEY/first-row semantics for multi-card accounts not in repo — plan-stop blocker; analysis §4.4, §5 |
| B-0006 | 2026-09-07 | AccountView | DATASET | `READ DATASET('USRSEC') INTO SEC-USER-DATA RIDFLD(WS-USER-ID X(08))` `app/cbl/COSGN00C.cbl:207-219`; RESP NORMAL(pwd match/mismatch)/NOTFND/OTHER `:221-257` | VSAM KSDS `CSD:88-89`; CRUD by S-11 | OUT | OPEN | — | Refines B-0006; class B4; VSAM KSDS; read-only in S-01; contract RESOLVED, decision UNDECIDED; analysis §5 |
| B-0026 | 2026-09-07 | AccountView | EXTERNAL | plaintext 8-char password byte-compare, no lockout/audit, user type from record `COSGN00C.cbl:221-239`; inputs upper-cased `:130-131` | target session-cookie auth + BCrypt upgrade-on-login (STOP A); real SSO deferred | IN | OPEN | — | Refines B-0026; class B11; contract RESOLVED, integration decision UNDECIDED (plan stop); re-entry condition for SSO deferral must be named; analysis §5 |
| B-0009 | 2026-09-07 | AccountView | PROGRAM | `XCTL PROGRAM('COMEN01C') COMMAREA(CARDDEMO-COMMAREA)` `COSGN00C.cbl:236-239`; excluded sibling `:231-234` -> COADM01C | menu shell (in S-01); admin menu (excluded) | OUT | OPEN | — | Refines B-0009; class B5; COMMAREA FROM-TRANID/PROGRAM, USER-ID, USER-TYPE, PGM-CONTEXT=0; RESOLVED for USRTYP=U; admin branch stays legacy |
| B-0010 | 2026-09-07 | AccountView | PROGRAM | dispatch `XCTL PROGRAM(CDEMO-MENU-OPT-PGMNAME(WS-OPTION)) COMMAREA` `COMEN01C.cbl:177-188`; hard stop `XCTL PROGRAM(CDEMO-TO-PROGRAM) COMMAREA` `COACTVWC.cbl:349-352` (always 'COMEN01C', `:333-339`); menu exit `COMEN01C.cbl:196-203` | S-01 opt 1 live; opts 2..10 excluded legacy routes; opt 11 out-of-module | BOTH | OPEN | — | Refines B-0010; class B5; RESOLVED for option 1 + hard stop; **UNDECIDED**: routing facade for 9 excluded options (hide/stub/legacy link) — plan stop; analysis §5 |
| B-0027 | 2026-09-07 | AccountView | DATA-TARGET | `COPY COCOM01Y` `COSGN00C.cbl:48`, `COMEN01C.cbl:50`, `COACTVWC.cbl:211`; writers `COSGN00C:222-229`, `COMEN01C:180-183`, `COACTVWC:283-288,340-347`; extended by `WS-THIS-PROGCOMMAREA` `COACTVWC.cbl:213-216` | target HTTP session + per-page state; inherited by all online streams | BOTH | OPEN | — | Refines B-0027; class B10; fields listed analysis §4.6; contract RESOLVED; UNDECIDED: reproduce or fix the user-id wipe on entry (`COACTVWC.cbl:283-286`) |
| B-0014 | 2026-09-07 | AccountView | SHARED-UTIL | `PROCEDURE DIVISION USING LS-DATE X(10), LS-DATE-FORMAT X(10), LS-RESULT X(80)` `app/cbl/CSUTLDTC.cbl:83-88`; RETURN-CODE = severity `:97-100`; result layout `app/cpy/CSUTLDWY.cpy:60-84`; no call site in S-01 | callers S-08 `COTRN02C.cbl:393,413`, S-09 `CORPT00C.cbl:392,412`, S-02 via `CSUTLDPY.cpy:293-296` | IN | OPEN | — | Refines B-0014; class B2; port once in S-01 wave 1 (D-0020); callers branch on SEV-CD '0000' and MSG-NUM '2513' (`COTRN02C.cbl:398-401`); interface RESOLVED |
| B-0015 | 2026-09-07 | AccountView | EXTERNAL | `CALL "CEEDAYS" USING WS-DATE-TO-TEST, WS-DATE-FORMAT, OUTPUT-LILLIAN, FEEDBACK-CODE` `CSUTLDTC.cbl:116-120`; feedback mapped `:122-149` | z/OS Language Environment (absent) | OUT | OPEN | — | Refines B-0015; class B11; **UNRESOLVED**: full CEEDAYS feedback-code catalogue (msg numbers such as 2513) not in repo — blocker for S-08/S-09 parity of the ported utility, not for S-01 screens |
| B-0028 | 2026-09-07 | AccountView | PROGRAM | pseudo-conversational `RETURN TRANSID`: `COSGN00C.cbl:98-102` (CC00), `COMEN01C.cbl:107-110` (CM00), `COACTVWC.cbl:402-406` (CAVW, WS-COMMAREA) | CICS terminal task model -> stateless HTTP + session | BOTH | OPEN | — | New; class B5; PGM-CONTEXT=1 marks re-entry; RESOLVED |
| B-0029 | 2026-09-07 | AccountView | EXTERNAL | BMS `SEND MAP`/`RECEIVE MAP`/`SEND TEXT` `COSGN00C.cbl:110-114,151-157,164-169`; `COMEN01C.cbl:215-221,227-232`; `COACTVWC.cbl:583-590,611-616,878-883,897-902`; RESP on RECEIVE never tested | 3270/BMS -> Angular 18 + Material screens (analysis §3) | BOTH | OPEN | — | New; class B11; symbolic maps `app/cpy-bms/COSGN00.CPY`, `COMEN01.CPY`, `COACTVW.CPY`; DFHBMSCA/DFHAID absent (IBM); RESOLVED |
| B-0030 | 2026-09-07 | AccountView | EXTERNAL | `ASSIGN APPLID/SYSID` `COSGN00C.cbl:198-204`; `FUNCTION CURRENT-DATE` headers `COSGN00C.cbl:178-195`, `COMEN01C.cbl:240-256`, `COACTVWC.cbl:425-445`; EIB fields | CICS system services -> app config + server clock | OUT | OPEN | — | New; class B11; RESOLVED |
| B-0031 | 2026-09-07 | AccountView | EXTERNAL | `HANDLE ABEND LABEL(ABEND-ROUTINE)` `COACTVWC.cbl:264-266`; `ABEND-ROUTINE` SEND ABEND-DATA (`CSMSG02Y.cpy:21-29`), `HANDLE ABEND CANCEL`, `ABEND ABCODE('9999')` `:917-937` | CICS abend protocol -> generic 500 handler | OUT | OPEN | — | New; class B11; reads only, no UOW; RESOLVED |
| B-0032 | 2026-09-07 | AccountView | PROGRAM | `INQUIRE PROGRAM(...) NOHANDLE` + XCTL/not-installed message `COMEN01C.cbl:147-167` for option 11 | COPAUS0C out-of-module (see B-0011, S-13) | OUT | OPEN | — | New; class B5; EXCLUDED route in S-01, must render as unavailable in target menu; RESOLVED as excluded |
| B-0033 | 2026-09-07 | AccountView | DATA-TARGET | menu option table `COPY COMEN02Y` `COMEN01C.cbl:51`; `app/cpy/COMEN02Y.cpy:19-98` (11 entries, OCCURS 12) | target static route table owned by menu shell; later online streams append | BOTH | OPEN | — | New; class B10; RESOLVED |
