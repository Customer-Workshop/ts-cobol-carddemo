# 05_progress — stream ledger (append/update rows only)

Schema: | Stream | Type | Entry point | Status (not started / analyzed / planned / in wave N / signed off) | Last update | Notes |

Java-engagement note: code baseline = PR #83 port (Java 21/Spring Boot 3.4.5, H2, REST-only,
batch job configs) adopted into `devin/1789516557-carddemo-java-engagement`. "Baseline" below
means an implementation exists in the adopted port; every stream still gets its artifact set,
parity pass, and sign-off under this engagement. Prior .NET-engagement statuses do NOT carry.

| Stream | Type | Entry point | Status | Last update | Notes |
|---|---|---|---|---|---|
| S-01 Sign-on + menu shell | ONLINE | CC00/CM00/CA00 | SIGNED OFF — MERGED to devin/carddemo-java-test (PR #105) | 2026-09-16 | Audit PASS w/ 4 LOW + 4 INFO findings (none blocking); 69 tests green; Postgres smoke live-verified; evidence in functional/CARDDEMO/evidence/s01/ |
| S-02 Account View | ONLINE | CAVW | baseline ported — pre-STOP C | 2026-09-15 | AccountController/AccountViewService in baseline |
| S-03 Account Update | ONLINE | CAUP | baseline ported — pre-STOP C | 2026-09-15 | AccountUpdateController/Service in baseline |
| S-04 Card List | ONLINE | CCLI | planned — Java docs merged (PR #107) | 2026-09-16 | CardController (list) in baseline; boundaries S04-B1..B4 decided; Flyway V120x |
| S-05 Card View | ONLINE | CCDL | baseline ported — pre-STOP C | 2026-09-15 | CardController (view) in baseline |
| S-06 Card Update | ONLINE | CCUP | baseline ported — pre-STOP C | 2026-09-15 | CardController (update) in baseline |
| S-07 Transaction List | ONLINE | CT00 | baseline ported — pre-STOP C | 2026-09-15 | TransactionController (list) in baseline |
| S-08 Transaction View | ONLINE | CT01 | baseline ported — pre-STOP C | 2026-09-15 | TransactionController (view) in baseline |
| S-09 Transaction Add | ONLINE | CT02 | baseline ported — pre-STOP C | 2026-09-15 | TransactionController (add) + TransactionIdGenerator; CSUTLDTC parity gap: date validation is LocalDate.parse, not the Lillian/mask port (S09-B4) |
| S-10 Reports (online→batch) | ONLINE+BATCH | CR00 + TRANREPT | baseline ported — pre-STOP C | 2026-09-15 | ReportController + Cbtrn03JobConfiguration; CSUTLDTC shared (same gap) |
| S-11 Bill Payment | ONLINE | CB00 | planned — Java docs merged (PR #107) | 2026-09-16 | BillingController/Service in baseline; boundaries S11-B1..B6 decided; Flyway V190x |
| S-12 User Admin | ONLINE | CU00–CU03 | planned — Java docs merged (PR #107) | 2026-09-16 | AdminUserController/Service in baseline; boundaries S12-B1..B5 decided; S01-B5 closed (Postgres users = single writer); Flyway V200x |
| S-13 Card Detail (security) | ONLINE | CDV1 | BLOCKED — descoped | 2026-09-15 | COCRDSEC source absent (B-011); menu route registered disabled; needs an outside request, deferred per owner instruction |
| S-14 Daily posting chain | BATCH | Control-M DAILY | baseline ported — pre-STOP C | 2026-09-15 | Cbtrn02JobConfiguration (POSTTRAN) + Cbtrn01JobConfiguration (orphan CBTRN01C bonus); COBSWAIT/MVSWAIT seam not yet ported (B-002) |
| S-15 Interest calc chain | BATCH | Control-M MONTHLY | baseline ported — pre-STOP C | 2026-09-15 | Cbact04JobConfiguration (INTCALC); shares the wait-step seam |
| S-16 Statement creation | BATCH | CREASTMT | baseline ported — pre-STOP C | 2026-09-15 | Cbstm03JobConfiguration (CBSTM03A); verify CBSTM03B sub-flow coverage at analysis |
| S-17 Data read/verify jobs | BATCH | READ*.jcl | planned — Java docs merged (PR #106) | 2026-09-16 | Greenfield batch (no baseline jobs); CA-7 SCHID=030 chain documented; S17-B1..B7 decided (B7 pad-to-LRECL proposed); DateEditService for COBDATFT; Flyway V240x |
| S-18 Branch export/import | BATCH | CBEXPORT/CBIMPORT | planned — Java docs merged (PR #106) | 2026-09-16 | Baseline jobs structurally correct; S18-B1..B7 decided (B6 text-format deviation, B7 legacy CARDOUT gap no-carry); deltas: 132-col error layout, per-type stats; Flyway V250x |
| S-19 Pending auth view (ext) | ONLINE | CPVS/CPVD | not started | 2026-09-15 | IMS/DB2/MQ extension; boundaries B-005/B-006 decided -> JPA/Postgres |
| S-20 Auth processing + purge (ext) | SUBTRANSACTION+BATCH | CP00 (MQ) + CBPAUP0J | not started | 2026-09-15 | B-007 -> in-process queue seam; PAUDBLOD/PAUDBUNL/DBUNLDGS IMS segments -> tables |
| S-21 Tran-type maintenance (ext) | ONLINE+BATCH | CTLI/CTTU + MNTTRDB2 | not started | 2026-09-15 | DB2 extension -> Postgres tables + JPA (B-006) |
| S-22 VSAM-MQ demo (ext) | SUBTRANSACTION | CDRA/CDRD (MQ) | not started | 2026-09-15 | MQ request/reply demo -> in-process queue seam (B-007) |

Operating mode (2026-09-16, owner): sequential plan order is the spine; independent streams run in parallel — one child per stream, reconciled on the engagement branch; Flyway ranges pre-allocated per stream (S-02→V100x … S-22→V290x) to prevent migration collisions; STOP C/E batched across streams. Doc-authoring children: S-04/11/12 MERGED (PR #107), S-17/18 MERGED (PR #106); running: S-02/05/07/08, S-09/03/06, S-10/14/15/16, S-19..22.

Cross-cutting gaps vs target state (tracked for Phase 0/1):
- ~~Persistence is H2~~ — DONE (S-01 W1): Postgres profile + docker-compose + Flyway V1-V3.
- ~~No web UI~~ — in progress per stream (S-01 done: sign-on + both menus; each online stream adds its screens).
- ~~No CI workflow~~ — DONE (S-01 W1): `.github/workflows/ci.yml` gate green.
- CSUTLDTC Lillian/mask date validation inlined as LocalDate.parse — port the utility (S09-B4, S-09 owns).
- COBSWAIT wait-step seam and COBDATFT date-edit utility not ported (B-002 S-14 owns, B-003 S-17 owns).
