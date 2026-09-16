# COPAUA0C — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COPAUA0C — `app/app-authorization-ims-db2-mq/cbl/COPAUA0C.cbl` (1026 lines). Stream S-20, wave 1.
- Role: entry/orchestration — MQ-triggered authorization processor. No screen; drains the request queue (≤500 msgs, 5s GET wait), decides each auth, replies, persists summary + detail, logs errors.

## 2. Trigger / caller contract
- CICS tran `CP00` started by the MQ trigger monitor; `EXEC CICS RETRIEVE INTO(MQTM)` yields queue name + trigger data (`:233-239`). Terminates via `EXEC CICS RETURN` (:226/9000-TERMINATE).
- Queue contract: request = `AWS.M2.CARDDEMO.PAUTH.REQUEST` (from MQTM-QNAME), reply = `MQMD-REPLYTOQ` (README `AWS.M2.CARDDEMO.PAUTH.REPLY`), correlId echoed.

## 3. Inputs and outputs
In: 18-field CSV request `CCPAURQY` (UNSTRING ',' :354-372; amount via NUMVAL). Out: 6-field CSV reply `CCPAURLY` (card,tran,authid,resp,reason,approved-amt :729-735) via `MQPUT1` (:758); CSSL error records `CCPAUERY` via `WRITEQ TD` (:975+); IMS writes: PAUTSUM0 REPL-or-ISRT (:840-848), PAUTDTL1 path ISRT (:905-915). VSAM reads: CCXREF (:477), ACCTDAT (:525), CUSTDAT (:573).

## 4. Functional requirements owned (all cross-ref stream FR)
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COPAUA0C-01 | trigger start | RETRIEVE MQTM; on failure error+log+terminate | :233-240 | FR-S20-01 |
| COPAUA0C-02 | open request queue | MQOPEN INPUT-SHARED; err → 'REQ MQ OPEN ERROR' log | :255-282 | FR-S20-01 |
| COPAUA0C-03 | GET w/ 5s wait | message or NO-MSG-AVAILABLE → drain end; other → M003 critical log | :389-429 | FR-S20-01 |
| COPAUA0C-04 | msg received | CSV parse; correlId + replyTo captured | :354-372,:410-414 | FR-S20-01 |
| COPAUA0C-05 | decide | approved '00'/amt or declined '05' + reason map (3100 unknowns, 4100 insuf-fund, 4200 card-not-active, 4300 acct-closed, 5100 card-fraud, 5200 merch-fraud, 9000 other) | :666-727 | FR-S20-03 |
| COPAUA0C-06 | reply | 6-field CSV to replyTo, correlId echoed, expiry 50, non-persistent; put failure → M004 log | :729-775 | FR-S20-04 |
| COPAUA0C-07 | persist | summary upsert (REPL if found else ISRT with xref acct/cust + acct limits; counters moved per decision) | :800-848 | FR-S20-05 |
| COPAUA0C-08 | persist | detail ISRT: complement key from now, request fields + decision fields, match 'P'/'D', fraud space | :856-915 | FR-S20-06 |
| COPAUA0C-09 | run bound | stop at queue drain or >500 msgs; SYNCPOINT per msg | :40,:334-342 | FR-S20-08 |
| COPAUA0C-10 | critical errors | CSSL record w/ location/subsystem/codes/event-key; run continues or terminates per severity | :975-1002 | FR-S20-07 |

## 5. Business rules and validations
Limit test prefers summary limits (`PA-CREDIT-LIMIT − PA-CREDIT-BALANCE`) when the summary segment exists, else `ACCT-CREDIT-LIMIT − ACCT-CURR-BAL` (:666-674). Approved adds to approved count/amt + credit balance and zeroes cash balance; declined adds to declined count/amt (:815-828). XREF read by card num yields acct+cust ids (:477-512).

## 6. Data access and boundaries
- MQ in/out (S20-B1/B2/B3 — `InProcessMqService` consumer + reply; bounded loop preserved).
- IMS PAUTSUM0/PAUTDTL1 writes (S20-B4 — shared JPA entities; per-message `@Transactional`).
- VSAM reads (S20-B5 — existing repos).
- Error sink (S20-B6 — SLF4J structured record).

## 7. Error and edge behavior
Every IMS/MQ error path builds a CCPAUERY record w/ location code (M003/M004/I002/I003/I004) + card as event-key; critical marks terminate paths. No error reply is sent to the requester — errors are log-only plus (for MQ failures) termination. 5600-READ-PROFILE-DATA is a stub (`CONTINUE`, :646-648) — reserved extension point, preserved as no-op hook.

## 8. Hard-stop boundary
Owns the request→reply→persist cycle only; screen viewing (S-19), fraud journalling (S-19 COPAUS2C), purge (CBPAUP0C same stream wave 2), and the demo queues (S-22) are out.

## 9. Demoted mechanics
MQ connect/open/close + CMQ* copybooks → service API; SCHD/TERM (:293-304) → n/a; SYNCPOINT → tx; WRITEQ TD → logging; RETURN TRANSID → consumer loop end.

## 10. Traceability
COPAUA0C-01..10 ↔ FR-S20-01..08 ↔ `AuthProcessingServiceTests`/`AuthProcessingQueueIT`.
