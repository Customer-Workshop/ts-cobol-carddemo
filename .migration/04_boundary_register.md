# 04_boundary_register — module-wide boundary register (append-only)

Schema: | ID | Date found | Found by | Class | Description | Cite | Streams affected | Required action | Status (OPEN/DECIDED/DEFERRED) | Decision ref |

| ID | Date | Found by | Class | Description | Cite | Streams | Required action | Status | Decision |
|---|---|---|---|---|---|---|---|---|---|
| B-001 | 2026-08-20 | inventory | Online→batch hand-off | CORPT00C submits TRANREPT JCL via CICS TDQ `JOBS` (internal reader) | app/cbl/CORPT00C.cbl:517 | S-10 | Design async job-launch seam | OPEN | — |
| B-002 | 2026-08-20 | inventory | Non-COBOL callee | MVSWAIT assembler wait | app/cbl/COBSWAIT.cbl:37; app/asm/MVSWAIT.asm | S-14,S-15 | Re-spec or replace with scheduler-native wait | OPEN | — |
| B-003 | 2026-08-20 | inventory | Non-COBOL callee | COBDATFT assembler date edit | app/cbl/CBACT01C.cbl:231; app/asm/COBDATFT.asm | S-17 | Re-spec in .NET | OPEN | — |
| B-004 | 2026-08-20 | inventory | LE runtime | CEE3ABD abend service in 7 batch programs | app/cbl/CBACT01C.cbl:410 | batch streams | Map to exit-code/exception contract | OPEN | — |
| B-005 | 2026-08-20 | inventory | IMS DL/I | CBLTDLI calls in auth extension | app/app-authorization-ims-db2-mq/cbl/PAUDBLOD.CBL:244 | S-19,S-20 | Decide IMS data target strategy | OPEN | — |
| B-006 | 2026-08-20 | inventory | DB2 SQL | EXEC SQL in tran-type + auth extensions | app/app-transaction-type-db2/cbl/COTRTLIC.cbl:304 | S-19,S-20,S-21 | Map to PostgreSQL/EF Core | OPEN | — |
| B-007 | 2026-08-20 | inventory | MQ | MQOPEN/MQGET/MQPUT1/MQCLOSE | app/app-authorization-ims-db2-mq/cbl/COPAUA0C.cbl:262,400,758; app/app-vsam-mq/cbl/COACCT01.cbl:233 | S-20,S-22 | Choose MQ broker (deferred at STOP A) | OPEN | — |
| B-008 | 2026-08-20 | inventory | Scheduler | Control-M INCOND/OUTCOND chains + CA-7 | app/scheduler/CardDemo.controlm:4-27 | all batch | Preserve completion contract via exit codes | OPEN | — |
| B-009 | 2026-08-20 | inventory | Persistence | VSAM KSDS/AIX → PostgreSQL mapping | app/csd/CARDDEMO.CSD (FCT) + IDCAMS JCL | all | Schema derivation in Phase 1 | OPEN | — |
| B-010 | 2026-08-20 | inventory | Dataset hand-off | PS/GDG files (export/import, statements, reports, TXT2PDF) | app/jcl/TXT2PDF1.JCL:24 | S-10,S-16,S-18 | Dataset mapping policy per target state | OPEN | — |
| B-011 | 2026-08-20 | inventory | Absent module | COCRDSEC CSD-defined but source missing | app/csd/CARDDEMO.CSD:211,388 | S-13 | Obtain source or descope (user decision) | OPEN | — |
| B-012 | 2026-08-20 | inventory | Dynamic routing | XCTL PROGRAM(CDEMO-TO-PROGRAM) return routing | app/cbl/COACTVWC.cbl:350 | all online | Map to navigation/state seam | OPEN | — |
| S01-B1 | 2026-08-20 | S-01 analysis | B5 cross-program switch | XCTL from menus to 10 core route programs w/ CARDDEMO-COMMAREA | app/cbl/COMEN01C.cbl:185; app/cbl/COADM01C.cbl:146 | S-01 (+ all route streams) | Target routing: Angular nav + per-stream API; "not installed" until migrated | DECIDED | Feature-flagged route registry; per-stream flag flips at that stream's merge (plan §3) |
| S01-B2 | 2026-08-20 | S-01 analysis | B5 availability probe | XCTL COPAUS0C guarded by INQUIRE PROGRAM | app/cbl/COMEN01C.cbl:148-159 | S-01, S-19 | Map to feature-flag/route availability | DECIDED | Same registry; flag default off until S-19 (plan §3) |
| S01-B3 | 2026-08-20 | S-01 analysis | B5 inbound return routing | Route programs XCTL back via CDEMO-TO-PROGRAM | app/cbl/COMEN01C.cbl:202 | S-01 (+ all online) | Stable return route in menu shell | DECIDED | Angular router returnUrl/nav state; routes /signin,/menu,/admin (plan §3) |
| S01-B4 | 2026-08-20 | S-01 analysis | B4 data-access leaf | CICS READ USRSEC KSDS by user id; RESP 0/13/other | app/cbl/COSGN00C.cbl:211-219 | S-01, S-12 | VSAM, target-owned → EF Core repository, no SP; Postgres users table Phase 1 | DECIDED | Target-owned Postgres users table + EF Core repository, no SP; wave 1 (plan §3) |
| S01-B5 | 2026-08-20 | S-01 analysis | B10 shared data contract | USRSEC written by S-12 + DUSRSECJ batch, read by S-01 | app/jcl/DUSRSECJ.jcl:62-64 | S-01, S-12 | Single-writer decision at S-12 migration; seed parity meanwhile | DECIDED | Deferred: dependency=S-12 plan; impact=seed import from USRSEC ASCII meanwhile; re-entry=S-12 migration plan (plan §3) |
| S01-B6 | 2026-08-20 | S-01 analysis | B10 shared data contract | CARDDEMO-COMMAREA copybook shared by all online programs | app/cpy/COCOM01Y.cpy:19 | all online | Session-state seam ported once by S-01; module property | DECIDED | SessionContext record + JWT claims, ported once in wave 1, owner S-01 (plan §3) |

(Initialized empty by !mf_migration_setup 2026-08-20. First pass populated by
!mf_module_inventory_analysis; decisions recorded by !mf_boundary_resolution only.)
