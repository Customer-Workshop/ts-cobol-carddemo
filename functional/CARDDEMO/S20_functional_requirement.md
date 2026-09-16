# S-20 Authorization Processing + Purge — Stream Functional Requirements (`!mf_stream_fr_generation`)

Stream: S-20 (SUBTRANSACTION consumer + BATCH jobs). Analysis: `S20_auth_processing_analysis.md`. Plan: `S20_auth_processing_migration_plan.md`.
Program FRs: `programs/COPAUA0C_*.md`, `programs/CBPAUP0C_*.md`, `programs/PAUDBLOD_*.md`, `programs/PAUDBUNL_*.md`, `programs/DBUNLDGS_*.md`.

## 1. Purpose and scope

Two halves: (a) **online auth processor** — drain the pending-auth request queue: parse the 18-field request, resolve card→acct→cust, apply the credit-limit decision, reply on the reply queue, and persist the authorization (summary counters/limits upsert + detail insert); (b) **batch maintenance** — purge expired authorizations (CBPAUP0J) and the load/unload data-transfer jobs.

## 2. Actors and preconditions

- Upstream authorization requesters put CSV messages on `carddemo.pauth.request` with a `replyTo` queue + correlId.
- CA-7 schedule / operator run for purge; JCL operator runs for load/unload.
- Preconditions: pending-auth tables exist (V2601 shared w/ S-19 or V2701 if S-20 lands first); VSAM-derived account/card-xref/customer data loaded.

## 3. Surface specification

- **Request queue** (`CCPAURQY`): 18 comma-delimited fields, 1000-byte buffer, trigger-queue name via MQTM.
- **Reply** (`CCPAURLY`): `cardnum,tranid,authidcode,respcode,respreason,approvedamt` to request's replyTo w/ correlId.
- **Error log** (`CCPAUERY`): structured log event w/ level (I/W/C), subsystem (A/C/I/D/M/F), location, code-1/2, message, event-key.
- **Batch surface CBPAUP0J**: JobParameters `expiryDays` (default 5), `chkpFreq` (default 5), `chkpDispFreq` (default 10), `debug` (Y/N); SYSOUT = job log counters; RC16 on abend.
- **Utility surfaces**: LOADPADB = import step (root file + child file); UNLDPADB = export step (root file + key|child file); UNLDGSAM = same export via the alternate writer; DBPAUTP0 = DB-level export.

## 4. Functional requirements (KEEP)

| ID | Flow | Business trigger | Observable result | Program | Cite | Boundary | Covering test |
|---|---|---|---|---|---|---|---|
| FR-S20-01 | consume | message on request queue | parsed 18-field request | COPAUA0C | :233-239,:354-372 | S20-B1,B3 | consumer IT |
| FR-S20-02 | resolve | request card num | xref→acct→cust fetched; any miss → decline '3100' path | COPAUA0C | :448-608,:704-708 | S20-B5 | lookup tests |
| FR-S20-03 | decide | txn amt vs available | approved '00'/amt=txn or declined '05'/amt=0; reason per cause map (3100/4100/4200/4300/5100/5200/9000) | COPAUA0C | :666-727 | — | decision matrix test |
| FR-S20-04 | reply | after decision | 6-field CSV on replyTo w/ correlId | COPAUA0C | :729-760 | S20-B2 | reply IT |
| FR-S20-05 | persist summary | after reply | PAUTSUM0 counters/limits upserted (REPL or ISRT) | COPAUA0C | :800-848 | S20-B4 | repo IT |
| FR-S20-06 | persist detail | after reply | PAUTDTL1 row w/ complement key, match status P/D | COPAUA0C | :856-905 | S20-B4 | repo IT |
| FR-S20-07 | error log | any critical | CSSL-style structured log w/ event key | COPAUA0C | :975-1002 | S20-B6 | log capture |
| FR-S20-08 | bounded run | queue drain or 500 msgs | run ends; SYNCPOINT per msg | COPAUA0C | :40,:334-342 | S20-B3 | bounded consumer test |
| FR-S20-09 | purge | job run | details older than expiryDays deleted; emptied summaries deleted; counters logged | CBPAUP0C | :140-178,:277-330 | S20-B7 | job IT |
| FR-S20-10 | purge checkpoint | every chkpFreq summaries | progress commit + periodic display | CBPAUP0C | :160-165,:355-370 | S20-B7 | chunk-commit test |
| FR-S20-11 | load | import files | summary+detail rows inserted; II-dup tolerated; counts logged | PAUDBLOD | 2100/3100 paras | S20-B8 | import IT |
| FR-S20-12 | unload | job run | 100B root + 206B 'key|child' CSV equivalents written | PAUDBUNL/DBUNLDGS | 2000/3000 paras | S20-B8 | export IT |

## 5. Validation and error catalogue

| Condition | Result | Cite |
|---|---|---|
| MQ open/read failure | CSSL error 'REQ MQ OPEN ERROR'/'M003', critical | COPAUA0C.cbl:275-282,:415-429 |
| Reply put failure | 'M004 FAILED TO PUT ON REPLY MQ', critical | :763-775 |
| IMS summary GU fail | 'I002 IMS GET SUMMARY FAILED', critical | :632-639 |
| IMS summary write fail | 'I003 IMS UPDATE SUMRY FAILED', critical | :838-846 |
| IMS detail insert fail | 'I004 IMS INSERT DETL FAILED', critical | :900-908 |
| Purge bad parm | non-numeric expiry → default 5; blank freqs → defaults | :196-205 |
| Purge fatal | ABEND, RC16 + counters dumped | :237-238,:380-384 |
| Load dup ('II') | tolerated, counted | PAUDBLOD.CBL 2100/3100 |
| Load other status | RC16 abend | PAUDBLOD.CBL 9999-ABEND |

## 6. Field and data derivations

- Available amount: summary exists → `PA-CREDIT-LIMIT − PA-CREDIT-BALANCE`; else `ACCT-CREDIT-LIMIT − ACCT-CURR-BAL`; decline when txn > available (:666-674).
- Detail key: `auth_date_9c = 99999 − YYDDD(now)`, `auth_time_9c = 999999999 − HHMMSSmmm(now)` (:870-873).
- Approved: resp '00', reason '0000', approved-amt = txn amt; declined: '05', reason from cause map (:704-723), approved-amt = 0.
- On approve: `approved_auth_cnt+1`, `approved_auth_amt+=amt`, `credit_balance+=amt`, `cash_balance=0`; on decline: `declined_auth_cnt+1`, `declined_auth_amt+=amt` (:815-828).
- Purge expiry: `99999 − auth_date_9c` = YYDDD of auth; `CURRENT-YYDDD − authYYDDD ≥ expiryDays` → delete detail; when `approved_auth_cnt ≤ 0` (twice-coded — the second compare should read declined; **source preserves the double-approved check** :156) → delete summary.

## 7. Mechanics (demoted, cited)

MQOPEN/GET/PUT1/MQCLOSE → `InProcessMqService`; MQTM RETRIEVE → consumer registration metadata; SYNCPOINT → per-message tx boundary; WRITEQ TD → SLF4J; DFSRRC00 BMP/DLI envelopes → Spring Batch steps; SCHD/TERM PSB → n/a; IMS CHKP → Spring chunk commit + ExecutionContext id; DISPLAY counters → job log.

## 8. Acceptance criteria (Given/When/Then)

- FR-S20-01..04: Given a well-formed request on the queue, When the consumer runs, Then the reply lands on the reply queue with matching correlId and the CSV layout.
- FR-S20-03: Given card over limit, Then decline '05'/'4100'; card unknown → '05'/'3100'.
- FR-S20-05/06: Given approved request, Then summary counters move and a detail row exists with complement key + match 'P'.
- FR-S20-08: Given 501 queued requests, Then exactly 500 processed this run.
- FR-S20-09: Given an auth older than expiryDays and a summary whose counts reach 0, Then detail and summary are deleted and counters logged.
- FR-S20-11/12: Given load files, Then import tolerates dups; Given data, export produces root and child files in the documented layouts.

## 9. Traceability matrix

| FR | Program FR | Java surface |
|---|---|---|
| FR-S20-01..08 | COPAUA0C-nn | `AuthProcessingService` + `queue/InProcessMqService` |
| FR-S20-09,10 | CBPAUP0C-nn | `PendingAuthPurgeJob` |
| FR-S20-11,12 | PAUDBLOD/PAUDBUNL/DBUNLDGS-nn | import/export steps |

## 10. Program index

| Program | Role | Program FR |
|---|---|---|
| COPAUA0C | MQ auth processor | programs/COPAUA0C_functional_requirement.md |
| CBPAUP0C | expiry purge | programs/CBPAUP0C_functional_requirement.md |
| PAUDBLOD | initial load | programs/PAUDBLOD_functional_requirement.md |
| PAUDBUNL | unload (QSAM) | programs/PAUDBUNL_functional_requirement.md |
| DBUNLDGS | unload (GSAM) | programs/DBUNLDGS_functional_requirement.md |

## 11. Open questions and assumptions

1. CBPAUP0C deletes a summary when `PA-APPROVED-AUTH-CNT <= 0 AND PA-APPROVED-AUTH-CNT <= 0` (:156) — the declined counter is never tested; preserving verbatim (a real never-delete-summaries-with-declines quirk) and flagging for product review.
2. `P-DEBUG-FLAG` gates extra DISPLAY diagnostics — mapped to a debug job parameter.
3. The request format has no version field; malformed CSV → parse failure → error log, no reply (source has no error-reply path on UNSTRING failure).
