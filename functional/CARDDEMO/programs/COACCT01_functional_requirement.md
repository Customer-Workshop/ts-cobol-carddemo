# COACCT01 — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COACCT01 — `app/app-vsam-mq/cbl/COACCT01.cbl` (620 lines). Stream S-22, wave 1.
- Role: seam consumer — account-inquiry request/reply over MQ: drains the trigger queue, reads ACCTDAT by acct id, replies labeled text on the hardcoded reply queue, writes protocol errors to the error queue.

## 2. Trigger / caller contract
- CICS tran `CDRA` (`csd/CRDDEMOM.csd` PROGRAM+TRANSACTION), MQ-trigger-monitor started; `EXEC CICS RETRIEVE INTO(MQTM)` → `MQTM-QNAME` = input queue (`:191-198`). Hardcoded `REPLY-QUEUE-NAME = 'CARD.DEMO.REPLY.ACCT'` (:197); `ERROR-QUEUE-NAME = 'CARD.DEMO.ERROR'` (:294).
- Exits via `EXEC CICS RETURN` (:549) after queue drain.

## 3. Inputs and outputs
In: request = `WS-FUNC` X(4) + `WS-KEY` 9(11) + X(985) filler (:109-112) via MQGET 5s wait (:334-360); account record via CICS READ ACCTDAT RIDFLD acct-id (:396-404).
Out: reply via MQPUT on output handle — `WS-ACCT-RESPONSE` labeled text (:130-169,:426-435) or 'INVALID REQUEST PARAMETERS ...' (:429-456); error records via MQPUT on error handle (:501-536).

## 4. Functional requirements owned (all cross-ref stream FR)
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COACCT01-01 | trigger | RETRIEVE → input queue name; fail → error-queue write + terminate | :191-210 | FR-S22-01 |
| COACCT01-02 | open | error/output/input queues opened INPUT-SHARED/OUTPUT; fail → error + terminate | :222-321 | FR-S22-02 |
| COACCT01-03 | GET | 5s wait; ok → process; NO-MSG → drain end; other → 'INP MQGET ERR:' + terminate | :334-388 | FR-S22-02,07 |
| COACCT01-04 | 'INQA'+key>0, found | reply = labeled account text; msgId/correlId echoed | :393-435,:462-498 | FR-S22-03 |
| COACCT01-05 | NOTFND | 'INVALID REQUEST PARAMETERS ACCT ID : <key>' reply | :428-435 | FR-S22-04 |
| COACCT01-06 | func≠'INQA' or key=0 | 'INVALID REQUEST PARAMETERS ACCT ID : <key> FUNCTION : <f>' reply | :448-456 | FR-S22-05 |
| COACCT01-07 | VSAM other-fail | 'ERROR WHILE READING ACCTFILE' → error queue + terminate | :437-445 | FR-S22-08 |
| COACCT01-08 | MQPUT fail | 'MQPUT ERR' → error queue + terminate | :492-498 | FR-S22-08 |
| COACCT01-09 | drain end | close all open queues, RETURN | :538-620 | FR-S22-07 |

## 5. Business rules and validations
Request = func + key only; 'INQA' is the sole function. READ keylength = 11. Reply body is positional labeled text, not a copybook on the requester side.

## 6. Data access and boundaries
- MQ lifecycle (S22-B1/B2/B3 — `InProcessMqService`: named queues incl. hardcoded reply+error; poll 5s; msgId/correlId echo; replyTo ignored per source).
- ACCTDAT read (S22-B4 — `AccountRepository`; NOTFND→invalid-params reply).
- SYNCPOINT per iteration (S22-B5 — per-message unit).

## 7. Error and edge behavior
Error queue gets the full `MQ-ERR-DISPLAY` block (para/return-msg/cond/reason/queue-name :58-67); close failures also route through 9000-ERROR (:552-620); RETRIEVE failure still tries the error queue first (:200-210).

## 8. Hard-stop boundary
Reads ACCTDAT only; no writes to any datastore; owns none of the queues' other users (S-20 shares the seam, different queue names).

## 9. Demoted mechanics
MQ call mechanics (MQOPEN/MQGET/MQPUT/MQCLOSE + CMQ* blocks) → service API; DISPLAY diagnostics → logging; buffer sizing fixed 1000.

## 10. Traceability
COACCT01-01..09 ↔ FR-S22-01..05,07,08 ↔ `AcctInquiryConsumerTests`/`MqSeamIT`.
