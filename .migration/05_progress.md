# 05_progress — stream ledger (append/update rows only)

Schema: | Stream | Type | Entry point | Status (not started / analyzed / planned / in wave N / signed off) | Last update | Notes |

Java-engagement note: code baseline = PR #83 port (Java 21/Spring Boot 3.4.5, H2, REST-only,
batch job configs) adopted into `devin/1789516557-carddemo-java-engagement`. "Baseline" below
means an implementation exists in the adopted port; every stream still gets its artifact set,
parity pass, and sign-off under this engagement. Prior .NET-engagement statuses do NOT carry.

| Stream | Type | Entry point | Status | Last update | Notes |
|---|---|---|---|---|---|
| S-01 Sign-on + menu shell | ONLINE | CC00/CM00/CA00 | SIGNED OFF — MERGED to devin/carddemo-java-test (PR #105) | 2026-09-16 | Audit PASS w/ 4 LOW + 4 INFO findings (none blocking); 69 tests green; Postgres smoke live-verified; evidence in functional/CARDDEMO/evidence/s01/ |
| S-02 Account View | ONLINE | CAVW | planned — Java docs merged (PR #108) | 2026-09-16 | AccountController/AccountViewService in baseline; S02-B1/B2 decided; Flyway V100x unused |
| S-03 Account Update | ONLINE | CAUP | planned — Java docs merged (PR #109) | 2026-09-16 | AccountUpdateController/Service in baseline; S03-B1..B5 decided; lookup/validate seams + 24-edit ladder + FOR UPDATE locks planned; Flyway V110x unused |
| S-04 Card List | ONLINE | CCLI | planned — Java docs merged (PR #107) | 2026-09-16 | CardController (list) in baseline; boundaries S04-B1..B4 decided; Flyway V120x |
| S-05 Card View | ONLINE | CCDL | planned — Java docs merged (PR #108) | 2026-09-16 | CardController (view) in baseline; S05-B1..B4 decided; Flyway V130x unused |
| S-06 Card Update | ONLINE | CCUP | planned — Java docs merged (PR #109) | 2026-09-16 | CardController (update) in baseline; S06-B1..B4 decided; deviations D1-D3 kept (CVV preserve/omit, no day-clamp); Flyway V140x unused |
| S-07 Transaction List | ONLINE | CT00 | planned — Java docs merged (PR #108) | 2026-09-16 | TransactionController (list) in baseline; S07-B1..B5 decided; cursor/peek paging quirks documented; Flyway V150x unused |
| S-08 Transaction View | ONLINE | CT01 | planned — Java docs merged (PR #108) | 2026-09-16 | TransactionController (view) in baseline; S08-B1..B4 decided; verbatim-key lookup fix planned; Flyway V160x unused |
| S-09 Transaction Add | ONLINE | CT02 | planned — Java docs merged (PR #109) | 2026-09-16 | TransactionController (add) in baseline; S09-B1..B6 decided; owns DateValidationService (CSUTLDTC Lillian/mask port, S-10 second caller); PF5 copy-last seam new; Flyway V170x unused |
| S-10 Reports (online→batch) | ONLINE+BATCH | CR00 + TRANREPT | planned — Java docs merged (PR #111) | 2026-09-16 | ReportController + Cbtrn03JobConfiguration; S10-B1..B8 decided; report screen missing (Thymeleaf page is the real work); consumes S-09's DateValidationService; Flyway V180x |
| S-11 Bill Payment | ONLINE | CB00 | planned — Java docs merged (PR #107) | 2026-09-16 | BillingController/Service in baseline; boundaries S11-B1..B6 decided; Flyway V190x |
| S-12 User Admin | ONLINE | CU00–CU03 | planned — Java docs merged (PR #107) | 2026-09-16 | AdminUserController/Service in baseline; boundaries S12-B1..B5 decided; S01-B5 closed (Postgres users = single writer); Flyway V200x |
| S-13 Card Detail (security) | ONLINE | CDV1 | BLOCKED — descoped | 2026-09-15 | COCRDSEC source absent (B-011); menu route registered disabled; needs an outside request, deferred per owner instruction |
| S-14 Daily posting chain | BATCH | Control-M DAILY | planned — Java docs merged (PR #111) | 2026-09-16 | S14-B1..B9 decided; owns wait-step tasklet seam (S-15/S-16 consume); RC=4 + 430B reject trailer contract; CA-7 SCHID 030 authoritative for posting (Control-M disagrees); Flyway V210x |
| S-15 Interest calc chain | BATCH | Control-M MONTHLY | planned — Java docs merged (PR #111) | 2026-09-16 | S15-B1..B8 decided; consumes S-14 wait seam; FLAG for STOP C: legacy abends on missing xref/acct vs baseline silent-skip; Flyway V220x |
| S-16 Statement creation | BATCH | CREASTMT | planned — Java docs merged (PR #111) | 2026-09-16 | S16-B1..B9 decided; CBSTM03B demoted to repositories (file-handler, not a program); drop 51x10 preload caps (legacy defect); vestigial PARM='12' not carried; Flyway V230x |
| S-17 Data read/verify jobs | BATCH | READ*.jcl | planned — Java docs merged (PR #106) | 2026-09-16 | Greenfield batch (no baseline jobs); CA-7 SCHID=030 chain documented; S17-B1..B7 decided (B7 pad-to-LRECL proposed); DateEditService for COBDATFT; Flyway V240x |
| S-18 Branch export/import | BATCH | CBEXPORT/CBIMPORT | planned — Java docs merged (PR #106) | 2026-09-16 | Baseline jobs structurally correct; S18-B1..B7 decided (B6 text-format deviation, B7 legacy CARDOUT gap no-carry); deltas: 132-col error layout, per-type stats; Flyway V250x |
| S-19 Pending auth view (ext) | ONLINE | CPVS/CPVD | planned — Java docs merged (PR #110) | 2026-09-16 | Greenfield: IMS PAUTSUM0/PAUTDTL1 + DB2 AUTHFRDS -> JPA; S19-B1..B8 decided; opt-11 flag flips; Flyway V260x |
| S-20 Auth processing + purge (ext) | SUBTRANSACTION+BATCH | CP00 (MQ) + CBPAUP0J | planned — Java docs merged (PR #110) | 2026-09-16 | Greenfield: MQ consumer + purge/utility jobs -> Spring Batch; shares pending_auth entities w/ S-19; consumes S-22's InProcessMqService; S20-B1..B8 decided; Flyway V270x |
| S-21 Tran-type maintenance (ext) | ONLINE+BATCH | CTLI/CTTU + MNTTRDB2 | planned — Java docs merged (PR #110) | 2026-09-16 | Reuses existing transaction_types/categories entities; V280x adds FK RESTRICT parity only; S21-B1..B7 decided; admin opts 5/6 flag flip |
| S-22 VSAM-MQ demo (ext) | SUBTRANSACTION | CDRA/CDRD (MQ) | planned — Java docs merged (PR #110) | 2026-09-16 | Owns shared `queue/InProcessMqService` (S-20 consumes; first-lander creates); S22-B1..B5 decided; no new tables; Flyway V290x unused |

Operating mode (2026-09-16, owner): sequential plan order is the spine; independent streams run in parallel — one child per stream, reconciled on the engagement branch; Flyway ranges pre-allocated per stream (S-02→V100x … S-22→V290x) to prevent migration collisions; STOP C/E batched across streams. Doc-authoring children: ALL MERGED — S-04/11/12 (PR #107), S-17/18 (PR #106), S-02/05/07/08 (PR #108), S-09/03/06 (PR #109), S-19..22 (PR #110), S-10/14/15/16 (PR #111). Next: batched STOP C for all 15 streams, then wave fan-out.

Cross-cutting gaps vs target state (tracked for Phase 0/1):
- ~~Persistence is H2~~ — DONE (S-01 W1): Postgres profile + docker-compose + Flyway V1-V3.
- ~~No web UI~~ — in progress per stream (S-01 done: sign-on + both menus; each online stream adds its screens).
- ~~No CI workflow~~ — DONE (S-01 W1): `.github/workflows/ci.yml` gate green.
- CSUTLDTC Lillian/mask date validation — port planned as `service/DateValidationService` (S-09 owns; S-10 second caller); currently inlined as LocalDate.parse in baseline.
- COBSWAIT wait-step seam and COBDATFT date-edit utility not ported (B-002 S-14 owns, B-003 S-17 owns).
