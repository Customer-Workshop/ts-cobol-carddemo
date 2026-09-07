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
