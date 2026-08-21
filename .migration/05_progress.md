# 05_progress — stream ledger (append/update rows only)

Schema: | Stream | Type | Entry point | Status (not started / analyzed / planned / in wave N / signed off) | Last update | Notes |

| Stream | Type | Entry point | Status | Last update | Notes |
|---|---|---|---|---|---|
| S-01 Sign-on + menu shell | ONLINE | CC00/CM00/CA00 | in wave 2 | 2026-08-21 | STOP C approved (incl. hashed-password deviation); FR + plan + program FRs in functional/CARDDEMO/; boundaries S01-B1..B6 DECIDED. Wave 1 landed: users table + EF migration, IUserRepository, idempotent USRSEC seed import (10 users, hashed), SessionContext/JWT plumbing, menu route-registry config; parity tests green. Wave 2 landed: COSGN00C equivalent — SignInService + POST /api/v1/auth/signin (FR-S01-01..07,09) and Angular sign-on screen with JWT auth service/interceptor and /menu, /admin placeholders (FR-S01-08); xUnit unit+Testcontainers and ng specs green |
| S-02 Account View | ONLINE | CAVW | not started | 2026-08-20 | |
| S-03 Account Update | ONLINE | CAUP | not started | 2026-08-20 | |
| S-04 Card List | ONLINE | CCLI | not started | 2026-08-20 | |
| S-05 Card View | ONLINE | CCDL | not started | 2026-08-20 | |
| S-06 Card Update | ONLINE | CCUP | not started | 2026-08-20 | |
| S-07 Transaction List | ONLINE | CT00 | not started | 2026-08-20 | |
| S-08 Transaction View | ONLINE | CT01 | not started | 2026-08-20 | |
| S-09 Transaction Add | ONLINE | CT02 | not started | 2026-08-20 | shared CSUTLDTC |
| S-10 Reports | ONLINE+BATCH | CR00 + TRANREPT | not started | 2026-08-20 | crosses B-001 |
| S-11 Bill Payment | ONLINE | CB00 | not started | 2026-08-20 | |
| S-12 User Admin | ONLINE | CU00–CU03 | not started | 2026-08-20 | |
| S-13 Card Detail (security) | ONLINE | CDV1 | BLOCKED | 2026-08-20 | COCRDSEC source absent (B-011) |
| S-14 Daily posting chain | BATCH | Control-M DAILY | not started | 2026-08-20 | shared COBSWAIT |
| S-15 Interest calc chain | BATCH | Control-M MONTHLY | not started | 2026-08-20 | |
| S-16 Statement creation | BATCH | CREASTMT | not started | 2026-08-20 | CBSTM03A→CBSTM03B |
| S-17 Data read/verify jobs | BATCH | READ*.jcl | not started | 2026-08-20 | COBDATFT asm (B-003) |
| S-18 Branch export/import | BATCH | CBEXPORT/CBIMPORT | not started | 2026-08-20 | |
| S-19 Pending auth view (ext) | ONLINE | CPVS/CPVD | not started | 2026-08-20 | IMS/DB2/MQ extension |
| S-20 Auth processing + purge (ext) | SUBTRANSACTION+BATCH | CP00 (MQ) + CBPAUP0J | not started | 2026-08-20 | MQ broker pending (B-007) |
| S-21 Tran-type maintenance (ext) | ONLINE+BATCH | CTLI/CTTU + MNTTRDB2 | not started | 2026-08-20 | DB2 extension |
| S-22 VSAM-MQ demo (ext) | SUBTRANSACTION | CDRA/CDRD (MQ) | not started | 2026-08-20 | MQ extension |

(Initialized empty by !mf_migration_setup 2026-08-20. Rows added by
!mf_module_inventory_analysis after STOP B scoping.)
