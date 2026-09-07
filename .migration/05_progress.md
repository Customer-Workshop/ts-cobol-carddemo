# 05 — Progress ledger (stream-by-stream, APPEND-ONLY)

Owned by `!mf_migration_setup`; every later playbook **appends** a row when a stream (or the
module) changes status. Never edit an earlier row. The current status of a stream is its
**latest** row.

## Schema

| Column | Definition |
|---|---|
| `Date` | ISO date |
| `Scope` | `MODULE` or the stream short name (`AccountView`) |
| `Status` | one of: `not started` · `analyzed` (STOP B passed) · `FRs generated` · `planned` (STOP C passed) · `in wave N` · `parity passed` · `ui tested` · `signed off` (STOP E passed) · `blocked` |
| `Playbook` | the `!mf_*` playbook that produced this row |
| `Artifact` | path of the artifact this row refers to |
| `Session` | Devin session URL |
| `Note` | one line |

## Ledger

| Date | Scope | Status | Playbook | Artifact | Session | Note |
|---|---|---|---|---|---|---|
| 2026-09-07 | MODULE | target state ingested (pending STOP A) | `!mf_ingest_target_state` | `functional/CardDemo/CardDemo_target_state.md` | (orchestrator child) | CORE, ONLINE, DATA/BOUNDARY profiled; BATCH, SUBTRANSACTION N/A |
| 2026-09-07 | MODULE | setup complete (pending STOP A) | `!mf_migration_setup` | `.migration/` | https://partner-workshops.devinenterprise.com/sessions/d1dc2eb1b45a4ec8973a2c08782d6970 | runbook verified: cobc, spring-boot verify/run, Docker PostgreSQL 16 on :5433 |
| 2026-09-07 | AccountView | not started | `!mf_migration_setup` | — | — | first stream, ONLINE, `CAVW` -> `COACTVWC`; owns `COSGN00C`, `COMEN01C`, `CSUTLDTC`; analysis starts after STOP A |
| 2026-09-07 | MODULE | STOP A passed | orchestrator | `.migration/06_decisions.md` D-0013..D-0018 | https://partner-workshops.devinenterprise.com/sessions/fab89be6d9da4ce29cbf4a58d5d9b83b | target state + setup confirmed; inventory next |
| 2026-09-07 | MODULE | inventory complete (pending STOP B) | `!mf_module_inventory_analysis` | functional/CardDemo/CardDemo_inventory.md | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | 33 programs = 27 stream + 5 shared + 1 unreachable; 23 streams (19 active, 4 blocked); 27 boundaries B-0001..B-0027 |
| 2026-09-07 | AccountView | not started | `!mf_module_inventory_analysis` | functional/CardDemo/CardDemo_inventory.md | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | S-01 catalogued: CAVW -> COACTVWC, hard stop XCTL -> COMEN01C; owns COSGN00C, COMEN01C, CSUTLDTC; awaiting STOP B confirmation |
| 2026-09-07 | AccountUpdate | not started | `!mf_module_inventory_analysis` | functional/CardDemo/CardDemo_inventory.md | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | S-02 ONLINE CAUP -> COACTUPC |
| 2026-09-07 | CardList | not started | `!mf_module_inventory_analysis` | functional/CardDemo/CardDemo_inventory.md | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | S-03 ONLINE CCLI -> COCRDLIC |
| 2026-09-07 | CardView | not started | `!mf_module_inventory_analysis` | functional/CardDemo/CardDemo_inventory.md | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | S-04 ONLINE CCDL -> COCRDSLC |
| 2026-09-07 | CardUpdate | not started | `!mf_module_inventory_analysis` | functional/CardDemo/CardDemo_inventory.md | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | S-05 ONLINE CCUP -> COCRDUPC |
| 2026-09-07 | TranList | not started | `!mf_module_inventory_analysis` | functional/CardDemo/CardDemo_inventory.md | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | S-06 ONLINE CT00 -> COTRN00C |
| 2026-09-07 | TranView | not started | `!mf_module_inventory_analysis` | functional/CardDemo/CardDemo_inventory.md | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | S-07 ONLINE CT01 -> COTRN01C |
| 2026-09-07 | TranAdd | not started | `!mf_module_inventory_analysis` | functional/CardDemo/CardDemo_inventory.md | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | S-08 ONLINE CT02 -> COTRN02C (+CSUTLDTC) |
| 2026-09-07 | TranReports | not started | `!mf_module_inventory_analysis` | functional/CardDemo/CardDemo_inventory.md | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | S-09 ONLINE->BATCH CR00 -> CORPT00C -> TRANREPT/CBTRN03C |
| 2026-09-07 | BillPay | not started | `!mf_module_inventory_analysis` | functional/CardDemo/CardDemo_inventory.md | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | S-10 ONLINE CB00 -> COBIL00C |
| 2026-09-07 | UserAdmin | not started | `!mf_module_inventory_analysis` | functional/CardDemo/CardDemo_inventory.md | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | S-11 ONLINE CA00/CU00-CU03 -> COADM01C, COUSR00C-03C |
| 2026-09-07 | CardSecurity | not started | `!mf_module_inventory_analysis` | functional/CardDemo/CardDemo_inventory.md | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | S-12 BLOCKED: CDV1 -> COCRDSEC source absent |
| 2026-09-07 | PendingAuthView | not started | `!mf_module_inventory_analysis` | functional/CardDemo/CardDemo_inventory.md | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | S-13 BLOCKED: menu opt 11 -> COPAUS0C out of module |
| 2026-09-07 | TranTypeMaint | not started | `!mf_module_inventory_analysis` | functional/CardDemo/CardDemo_inventory.md | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | S-14 BLOCKED: admin opts 5-6 -> COTRTLIC/COTRTUPC out of module |
| 2026-09-07 | DailyTranBackup | not started | `!mf_module_inventory_analysis` | functional/CardDemo/CardDemo_inventory.md | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | S-15 BATCH Control-M DAILY (utility + COBSWAIT) |
| 2026-09-07 | MonthlyInterest | not started | `!mf_module_inventory_analysis` | functional/CardDemo/CardDemo_inventory.md | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | S-16 BATCH Control-M MONTHLY INTCALC -> CBACT04C |
| 2026-09-07 | WeeklyDiscGrpRefresh | not started | `!mf_module_inventory_analysis` | functional/CardDemo/CardDemo_inventory.md | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | S-17 BATCH Control-M WEEKLY (depends on blocked S-18) |
| 2026-09-07 | WeeklyTranTypeDB2 | not started | `!mf_module_inventory_analysis` | functional/CardDemo/CardDemo_inventory.md | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | S-18 BLOCKED: MNTTRDB2/TRANEXTR JCL absent |
| 2026-09-07 | DailyPosting | not started | `!mf_module_inventory_analysis` | functional/CardDemo/CardDemo_inventory.md | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | S-19 BATCH CA-7 POSTTRAN -> CBTRN02C |
| 2026-09-07 | Statements | not started | `!mf_module_inventory_analysis` | functional/CardDemo/CardDemo_inventory.md | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | S-20 BATCH CA-7 CREASTMT -> CBSTM03A/CBSTM03B |
| 2026-09-07 | DataVerifyReads | not started | `!mf_module_inventory_analysis` | functional/CardDemo/CardDemo_inventory.md | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | S-21 BATCH CA-7 READACCT/READCARD/READCUST/READXREF |
| 2026-09-07 | BranchExportImport | not started | `!mf_module_inventory_analysis` | functional/CardDemo/CardDemo_inventory.md | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | S-22 BATCH JCL-only CBEXPORT/CBIMPORT |
| 2026-09-07 | RefDataRefresh | not started | `!mf_module_inventory_analysis` | functional/CardDemo/CardDemo_inventory.md | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | S-23 BATCH CA-7 utility chains (no module COBOL) |
| 2026-09-07 | MODULE | inventory complete (pending STOP B) | orchestrator | `functional/CardDemo/CardDemo_inventory.md` | https://partner-workshops.devinenterprise.com/sessions/7ecbe1218bce4789badd5af066218053 | 33 programs = 27 + 5 shared + 1 dead; 23 streams; B-0001..B-0027 registered |
| 2026-09-07 | MODULE | STOP B passed | orchestrator | `.migration/06_decisions.md` D-0019, D-0020 | https://partner-workshops.devinenterprise.com/sessions/fab89be6d9da4ce29cbf4a58d5d9b83b | S-01 AccountView confirmed ONLINE; stream analysis next |
