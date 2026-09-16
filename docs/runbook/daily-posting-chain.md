# Daily posting chain — ops runbook (target)

Replaces the scheduler-driven daily posting chain. The CA-7 SCHID 030 listing is
authoritative for the posting chain (`app/scheduler/CardDemo.ca7`):
`CLOSEFIL → CBPAUP0J → POSTTRAN → WAITSTEP → OPENFIL`.
The Control-M folder `DAILY-TransactionBackup` shows a different half —
`CLOSEFIL → TRANBKP → WAITSTEP → OPENFIL` with no POSTTRAN — and is treated as
the file-swap variant, not a competing chain (documented discrepancy; Control-M's
TRANBKP step is not implemented — approved deviation, see below).

## Documented job order (S14-B1)

| Order | Legacy step | Target job | Launch |
|---|---|---|---|
| 1 | CLOSEFIL — close online VSAM files | — | **No-op in target** (Postgres is always on). The order matters instead: run posting after the online cutover window, not concurrently with it. |
| 2 | CBPAUP0J — CICS file-control utility | — | Out of stream scope. |
| 3 | POSTTRAN (`STEP15 EXEC PGM=CBTRN02C`) | `cbtrn02Job` | `POST /api/admin/jobs/cbtrn02Job` (+ optional `dailyFile` param = the DALYTRAN drop) |
| 4 | TRANBKP (Control-M view only) | — | **Eliminated** (S14-B9): it was an IDCAMS REPRO + delete/define of the TRANSACT KSDS. Postgres has no define/delete cycle; backups are managed DB backups outside this stream. |
| 5 | WAITSTEP (`EXEC PGM=COBSWAIT`, SYSIN `00003600`) | `waitStepJob` | `POST /api/admin/jobs/waitStepJob` (+ optional `waitCentiseconds`; default `3600` = 36.00 s, matching the committed SYSIN card) |
| 6 | OPENFIL — reopen online files | — | **No-op in target.** |

The same order is also kept machine-readable in code as
`BatchJobService.DAILY_POSTING_CHAIN` = `[cbtrn02Job, waitStepJob]` (in-scope
steps only; the close/open bracket does not exist in target).

`cbtrn01Job` (validate-only sweep, CBTRN01C) is an **orphan utility** — no JCL
executes it upstream and it is deliberately not in the chain. Launch manually:
`POST /api/admin/jobs/cbtrn01Job?dailyFile=<path>`.

## Exit-code contract (COBOL RC → job outcome)

| Legacy | Target | Meaning |
|---|---|---|
| RC 0 | `COMPLETED`, no exit description | Clean run, zero rejects. |
| RC 4 (`WS-REJECT-COUNT > 0`, CBTRN02C.cbl:229-231) | `COMPLETED` + exit description `REJECTS:<n>` | Rejects were written to `cbtrn02-rejects.txt` in the job output dir. **Non-blocking** — the chain continues (no COND gating on POSTTRAN). |
| Abend 999 via CEE3ABD (file-status ≠ 00) | `FAILED` | Step failure; stop the chain and investigate before rerunning. |

The launch response carries `status`, `exitCode`, and `exitDescription`, so ops
can drive the chain on the same signals the scheduler conditions used.

## File contract

| Legacy DD | Target |
|---|---|
| `DALYTRAN` (PS, 350B, CVTRA06Y) | `dailyFile` job parameter; default `app/data/ASCII/dailytran.txt`. An **absent file is an empty feed** — the run completes with zero records (S14-B7). |
| `DALYREJS(+1)` (GDG, 430B FB) | `cbtrn02-rejects.txt` under `carddemo.batch.output-dir`; one 430-byte record per reject = 350B input record + `9(04)` reason + `X(76)` description. Recreated per run (rerun-safe). |
| `TRANFILE`, `ACCTFILE`, `XREFFILE`, `TCATBALF` (KSDS) | `transactions`, `accounts`, `card_xrefs`, `transaction_category_balances` tables (JPA). |

## Rerun / restart semantics

- Rerunning POSTTRAN over the same feed is **non-idempotent by design**
  (source-faithful): balances re-apply. In target the `transactions` row keyed
  by TRAN-ID is overwritten rather than duplicated — legacy abends on the
  duplicate-key KSDS write (status 22 → CEE3ABD); the target completing is a
  documented, strictly-safer deviation. Treat rerun as an ops action
  (fix-forward), not a routine retry.
- Spring Batch restart/checkpoint metadata replaces JCL restart: a failed job
  is relaunched via the same endpoint. Chunk commits roll back per chunk —
  finer-grained than the legacy no-transaction-boundary behavior (documented
  deviation, strictly safer).
- The wait step is interruptible; interrupting it fails the step.
- `waitCentiseconds` honors the SYSIN-card contract: first 8 bytes, leading
  digits; blank/garbage → 0 (near-instant return). Keep the production default
  at 3600 — it exists to give file-control operations settle time.

## Reason codes written to `cbtrn02-rejects.txt`

| Code | Description | Trigger |
|---|---|---|
| 0100 | `INVALID CARD NUMBER FOUND` | card not in `card_xrefs` |
| 0101 | `ACCOUNT RECORD NOT FOUND` | xref points at a missing account |
| 0102 | `OVERLIMIT TRANSACTION` | `cycCredit − cycDebit + amt > creditLimit` |
| 0103 | `TRANSACTION RECEIVED AFTER ACCT EXPIRATION` | orig date after `acct_expiration_date` (blank expiry counts as expired) |
| 0109 | — *(not emitted)* | COBOL sets reason 109 on account REWRITE INVALID KEY *after* the reject branch already ran, so it is never written (CBTRN02C.cbl:211-215, 554-559). Target preserves the dead end: a vanished account skips its update silently and the transaction still posts. |

Reason precedence per record is last-write-wins in source order: card → account
→ overlimit → expiry, so an overlimit *and* expired transaction reports 0103.
