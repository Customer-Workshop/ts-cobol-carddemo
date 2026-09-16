# CardDemo COBOL → Java — Final Independent Audit (S-02 .. S-22)

- **Audited branch**: `devin/1789516557-carddemo-java-engagement`, HEAD `410e5d0f826ccd3a6a2f4ea04eee673f64c240d8`
- **Auditor**: independent (did not perform the migration). Per-stream audits were executed by 20 independent auditor sessions — one per stream — none of which participated in the migration work; this rollup and the full-suite run were done by the orchestrating auditor.
- **Date**: 2026-09-16
- **Scope**: streams S-02 through S-22. S-01 was already audited and signed off (`S01_SignonMenu_audit.md`, `S01_SignonMenu_signoff.md`); S-13 is descoped (source absent). For each stream: every FR traced to implementation + named test; verbatim COBOL spot-checks (messages, edit rules, file/DB semantics, exit codes) against `app/cbl/`, `app/cpy/`, `app/bms/`; stub/placeholder sweep; documented deviations (plan boundary tables + wave PRs) checked against shipped code; scoped test run per stream.
- **Per-stream reports**: `functional/CARDDEMO/S{NN}_*_audit.md` in this directory (20 files).

## Rollup verdict

| Stream | Name | Verdict | Scoped tests | HIGH | MED | LOW | INFO |
|---|---|---|---|---|---|---|---|
| S-02 | Account View | PASS with findings | 43/43 | 0 | 0 | 1 | 3 |
| S-03 | Account Update | PASS with findings | 47/47 | 0 | 1 | 2 | 2 |
| S-04 | Card List | PASS with findings | 53/53 | 0 | 1 | 6 | 3 |
| S-05 | Card View | PASS with findings | 64/64 | 0 | 1 | 1 | 3 |
| S-06 | Card Update | PASS with findings | 45/45 | 0 | 0 | 1 | 2 |
| S-07 | Transaction List | PASS with findings | 28/28 | 0 | 0 | 4 | 2 |
| S-08 | Transaction View | PASS with findings | 28/28 | 0 | 0 | 2 | 2 |
| S-09 | Transaction Add | PASS with findings | 63/63 | 0 | 1 | 4 | 2 |
| S-10 | Reports | PASS with findings | 31/31 | 0 | 0 | 1 | 4 |
| S-11 | Bill Pay | PASS with findings | 64/64 | 0 | 0 | 2 | 3 |
| S-12 | User Admin | PASS with findings | 84/84 | 0 | 0 | 3 | 2 |
| S-14 | Daily Posting | PASS with findings | 23/23 | 0 | 1 | 3 | 2 |
| S-15 | Interest Calc | PASS with findings | 31/31 | 0 | 2 | 3 | 3 |
| S-16 | Statements | PASS with findings | 16/16 | 0 | 0 | 4 | 2 |
| S-17 | Data Read/Verify | PASS with findings | 15/15 | 0 | 0 | 2 | 4 |
| S-18 | Branch Export/Import | PASS with findings | 5/5 | 0 | 3 | 3 | 4 |
| S-19 | Pending Auth View | **FAIL** | 46/46 | **1** | 2 | 3 | 3 |
| S-20 | Auth Processing | PASS with findings | 41/41 | 0 | 0 | 2 | 5 |
| S-21 | Tran Type Maintenance | PASS with findings | 25/25 | 0 | 2 | 3 | 6 |
| S-22 | VSAM/MQ Demo | PASS with findings | 32/32 | 0 | 0 | 2 | 2 |
| **Total** | | **19 PASS-with-findings, 1 FAIL** | 784 scoped assertions | **1** | **14** | **52** | **59** |

Scoped runs intentionally overlap on shared test classes (menu, shell, seeder) — the 784 figure is a sum of per-stream runs, not a unique-test count.

## Full suite (run by this audit)

```
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
cd spring-boot && mvn clean verify
```

Observed on the audited HEAD: **Tests run: 693, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS** (~50 s). The suite is fully green on the engagement branch.

## Blocking finding (1)

**S-19-F1 (HIGH) — `Credit Lim:`/`Cash Lim:` sourced from the wrong table.** The list screen's credit/cash limits are read from `pending_auth_summary` (the IMS snapshot) instead of the ACCTDAT account record the COBOL populates them from (`COPAUS0C.cbl:780-783` → `ACCT-CREDIT-LIMIT`/`ACCT-CASH-CREDIT-LIMIT` vs `PendingAuthService.java:349-350`). The shipped fixture already diverges (acct 1: ACCTDAT `2020.00`/`1020.00` vs summary `11000.00`/`2500.00`), and the parity test pins the wrong-source value (`PendingAuthFrParityTest.java:95`). Accounts with no summary row show `0.00` where COBOL still shows the ACCTDAT limits. Root cause is upstream: the analysis field table itself mis-attributes the fields (`S19_pending_auth_view_analysis.md:41`). Undocumented, user-visible data-fidelity defect — S-19 should not sign off until fixed. See `S19_pending_auth_view_audit.md` §6.

## MEDIUM findings (14)

| Stream | Finding |
|---|---|
| S-03 | Phone "all-blank" test misreads the third COBOL clause — Java accepts blank area+prefix with a typed line part |
| S-04 | FR/plan claim Testcontainers Postgres 16 seeded from `carddata.txt`; shipped tests run H2 with 10 synthetic cards (doc overstates infra fidelity) |
| S-05 | `internalRoute` same-origin guard accepts a leading `\` — `returnUrl` `/\evil.example` passes `startsWith("/")` |
| S-09 | PF5 on an empty file preserves typed data fields; COBOL wipes them (interacts with F-2 — the FR doc's expected empty-file outcome may itself be wrong vs real CICS STARTBR NOTFND) |
| S-14 | FR-S14-09 spec defect: doc claims reject `0109 'UNABLE TO UPDATE ACCOUNT'` → DALYREJS; the COBOL actually updates the account first |
| S-15 | Legacy never updates the last account (EOF `ELSE` dead code); Java does — a divergence *from* a source bug, needs a documented decision |
| S-15 | Acct-id zero-padding dropped in `TRAN-DESC` and the 'ACCOUNT NOT FOUND' abend text |
| S-18 | 999-equivalent exit code not emitted — neither job registers `Abend999JobListener`, failed run reports generic FAILED |
| S-18 | Display-numeric fields exported space-padded-right, not zero-padded — contradicts the stream contract |
| S-18 | Amount text widths truncate at PIC bounds, silently corrupting large values on round-trip |
| S-19 | Detail-screen error texts missing or wrong-case (`next Auth`, `repos. AUTH Details`, mixed-case `Auth Summary/Auth Details`) |
| S-19 | Fraud-tag failure path unreachable — `' System error while FRAUD Tagging, ROLLBACK'` constant is dead code |
| S-21 | Filter-change turn silently discards posted row flags |
| S-21 | PF5 AID gate omits CONFIRM_DELETE ('9') — `validAid` allows PF5 only for N/X/8/7/6 |

## Cross-cutting themes

- **Doc drift is the most common finding class** (~a third of LOW/INFO items): FR/plan cite coordinates off by lines, test-name suffixes (`_frS04NN`) that are ordinals not FR ids, PR descriptions over- or mis-claiming behavior, and stale premises (S-05/S-06 justify missing navigation on "S-04 has no UI" — it now does). Code is mostly right; the paper trail around it needs a cleanup pass.
- **Error-path coverage is systematically thinner than happy-path coverage**: unreachable/mocked-only store-error tests, unported error-message variants (S-16 `ERROR READING CUSTFILE`, S-17 error DISPLAYs, S-19 three error texts, S-20 MQ open/close codes, S-22 MQGET/MQOPEN rows), and several "store failure → generic 500" instead of the catalogued message.
- **COMMAREA-as-session-context is not fully ported**: cross-program echoes (last acct id pre-fill, filter echo on return) are absent in S-04/S-19 and noted systemically.
- **Seed/test fidelity vs docs**: multiple streams document Testcontainers+Postgres seeding but ship H2 fixtures (S-04, S-05); S-21 flags a real-vs-test `trantype.txt` seed-layout difference (UNVERIFIED).
- **Faithfully-ported source quirks were preserved** where required — S-19's PF7 page-stack clobber, S-08's `DELIMITED BY SPACE` truncation — a good sign the streams optimized for parity, not plausibility.

## UNVERIFIED items (none blocking)

- S-09: real CICS STARTBR-on-empty-file semantics cannot be confirmed from this checkout (affects whether the FR doc's expected empty-file outcome is itself wrong).
- S-17: GnuCOBOL differential check for binary layouts was not runnable in this environment.
- S-21: production `trantype.txt` seed slicing path not fully traced.

## Test evidence

- Full suite: `mvn clean verify` → **693 tests, 0 failures, 0 errors, 0 skipped** (see above).
- Per-stream scoped runs: 20/20 green; counts in the rollup table and in each stream audit's §5.

## Method

20 independent auditor sessions, one per stream, each: read the stream's FR + analysis + migration-plan docs; built a per-FR traceability matrix (implementation class/method + named test); verbatim-compared every catalogued message and sampled derivations/edit rules/file-DB semantics/exit codes against `app/cbl`, `app/cpy`, `app/bms`; swept the stream's code and tests for stubs, disabled tests, and trivially-true assertions; reconciled the plan's boundary decisions and the stream's wave-PR deviation list against shipped code; ran the stream's scoped tests. The orchestrating auditor ran `mvn clean verify` on the engagement HEAD and wrote this rollup. No application code was modified by this audit.
