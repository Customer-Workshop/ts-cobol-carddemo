# CardDemo COBOL → Java — Module Sign-off (S-02 .. S-22)

- **Date**: 2026-09-16
- **Branch**: `devin/1789516557-carddemo-java-engagement` → merge target `devin/carddemo-java-test`
- **Scope**: 19 migrated streams (S-02..S-12, S-14..S-22). S-01 signed off earlier (`S01_SignonMenu_signoff.md`, PR #105 into the test branch). S-13 descoped — COCRDSEC source absent (B-011 DEFERRED).
- **Suite**: `mvn clean verify` → **693 tests, 0 failures** on the engagement HEAD (per FINAL_AUDIT §full-suite, run by the independent auditor; fix wave re-verified 693 green at merge of PR #135).
- **Independent audit**: `FINAL_AUDIT.md` + 20 per-stream audits (`S{NN}_*_audit.md`) — performed by sessions that did no migration work. Verdict: 19 PASS-with-findings, 1 FAIL (S-19) → S-19-F1 fixed in PR #135 (limits + acct status now read from the ACCTDAT account record per `COPAUS0C.cbl:780-783`).
- **UI evidence**: `evidence/ui/` — 72 screenshots, per-stream findings, `BUGS.md`, TRANREPT captured output; full-session recording attached to the testing session. 8 UI bugs found → all fixed (PR #135) or verified-faithful (TRANREPT EOF double-add, `CBTRN03C.cbl:197-204`).
- **Deployment check (new)**: fix wave booted `SPRING_PROFILES_ACTIVE=postgres` against a fresh PostgreSQL and seeded clean — closes BOOT-01 (V2602 realigns pending-auth columns/types; DataSeeder flushes types before categories).
- **Boundaries**: `04_boundary_register.md` — 75 DECIDED rows now IMPLEMENTED; B-011 DEFERRED (S-13); zero outside requests outstanding.
- **Doc-only findings** (logged in `06_decisions.md`, not fixed): S-04 Testcontainers claim, S-14 FR-S14-09 doc defect, S-15 last-account divergence kept (divergence from dead legacy code), S-09 empty-file STARTBR semantics unverifiable.
- **Fixture edit note**: fix wave corrected two invalid values in `app/data/ASCII/custdata.txt` (FICO 274→700, phone2 area 373→919) — the seeder's only input file; COBOL/copybook/JCL source untouched. Logged in `06_decisions.md`.

## Per-stream verdicts

| Stream | Name | Audit | Evidence | Wave PR(s) | Verdict |
|---|---|---|---|---|---|
| S-02 | Account View | S02_account_view_audit.md — PASS (1 LOW, 3 INFO) | evidence/ui/S02* + findings | #119 | PASS — sign off |
| S-03 | Account Update | S03_account_update_audit.md — PASS (1 MED, 2 LOW, 2 INFO) | evidence/ui/S03* | #128 | PASS — sign off (MED phone clause fixed #135) |
| S-04 | Card List | S04_card_list_audit.md — PASS (1 MED doc, 6 LOW, 3 INFO) | evidence/ui/S04* | #120 | PASS — sign off (U-select pad fixed #135) |
| S-05 | Card View | S05_card_view_audit.md — PASS (1 MED, 1 LOW, 3 INFO) | evidence/ui/S05* | #123 | PASS — sign off (returnUrl guard fixed #135) |
| S-06 | Card Update | S06_card_update_audit.md — PASS (1 LOW, 2 INFO) | evidence/ui/S06* | #126 | PASS — sign off |
| S-07 | Transaction List | S07_tran_list_audit.md — PASS (4 LOW, 2 INFO) | evidence/ui/S07* | #117 | PASS — sign off |
| S-08 | Transaction View | S08_tran_view_audit.md — PASS (2 LOW, 2 INFO) | evidence/ui/S08* | #121 | PASS — sign off (clipped fields fixed #135) |
| S-09 | Transaction Add | S09_tran_add_audit.md — PASS (1 MED unverifiable, 4 LOW, 2 INFO) | evidence/ui/S09* | #118 | PASS — sign off (MED → doc-only log) |
| S-10 | Reports | S10_reports_audit.md — PASS (1 LOW, 4 INFO) | evidence/ui/S10* + captured TRANREPT | #131 | PASS — sign off (double-add verified faithful) |
| S-11 | Bill Payment | S11_bill_pay_audit.md — PASS (2 LOW, 3 INFO) | evidence/ui/S11* | #122 | PASS — sign off |
| S-12 | User Admin | S12_user_admin_audit.md — PASS (3 LOW, 2 INFO) | evidence/ui/S12* | #124 | PASS — sign off |
| S-14 | Daily Posting | S14_daily_posting_audit.md — PASS (1 MED doc defect, 3 LOW, 2 INFO) | job parity tests + runbook | #115 | PASS — sign off |
| S-15 | Interest Calc | S15_interest_calc_audit.md — PASS (2 MED, 3 LOW, 3 INFO) | job parity tests | #130 | PASS — sign off (zero-pad fixed #135; dead-code divergence logged) |
| S-16 | Statements | S16_statements_audit.md — PASS (4 LOW, 2 INFO) | job parity tests | #129 | PASS — sign off |
| S-17 | Data Read/Verify | S17_data_read_verify_audit.md — PASS (2 LOW, 4 INFO) | job byte-fidelity tests | #114, #116 | PASS — sign off |
| S-18 | Branch Export/Import | S18_branch_export_import_audit.md — PASS (3 MED, 3 LOW, 4 INFO) | round-trip parity test | #113 | PASS — sign off (MEDs fixed #135: exit-999, zero-pad, PIC widths) |
| S-19 | Pending Auth View | S19_pending_auth_view_audit.md — FAIL→fixed | evidence/ui/S19* | #125 | PASS after #135 (HIGH fixed + error texts wired) |
| S-20 | Auth Processing | S20_auth_processing_audit.md — PASS (2 LOW, 5 INFO) | MQ + purge job tests | #132 | PASS — sign off |
| S-21 | Tran-Type Maintenance | S21_tran_type_maintenance_audit.md — PASS (2 MED, 3 LOW, 6 INFO) | evidence/ui/S21* | #127 | PASS — sign off (MEDs + UI-07/08 fixed #135) |
| S-22 | VSAM-MQ Demo | S22_vsam_mq_demo_audit.md — PASS (2 LOW, 2 INFO) | MQ consumer tests | #112 | PASS — sign off |

## Sign-off request

All 19 streams: tests green, evidence captured, independent audit complete, boundaries implemented or deferred-by-decision. **Merge `devin/1789516557-carddemo-java-engagement` → `devin/carddemo-java-test` on authorization.**
