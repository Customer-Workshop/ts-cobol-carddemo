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
