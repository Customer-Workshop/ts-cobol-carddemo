# CODATE01 — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: CODATE01 — `app/app-vsam-mq/cbl/CODATE01.cbl` (524 lines). Stream S-22, wave 1.
- Role: seam consumer — date/time request/reply demo over MQ. Same drain-loop skeleton as COACCT01; the request payload is ignored entirely and the reply is the formatted system date/time.

## 2. Trigger / caller contract
- CICS tran `CDRD` (`csd/CRDDEMOM.csd`); MQ-trigger start, `EXEC CICS RETRIEVE INTO(MQTM)` → input queue (:140-147). `REPLY-QUEUE-NAME = 'CARD.DEMO.REPLY.DATE'` (:147); `ERROR-QUEUE-NAME = 'CARD.DEMO.ERROR'` (:243).
- Exits via `EXEC CICS RETURN` (:453) after drain.

## 3. Inputs and outputs
In: request buffer (layout `WS-FUNC` X(4)+`WS-KEY` 9(11)+filler — received but not inspected, :109-112); `EXEC CICS ASKTIME`+`FORMATTIME` MMDDYYYY('-' sep)/TIME(: sep) (:343-353).
Out: reply `SYSTEM DATE : MM-DD-YYYY` + `SYSTEM TIME : HH:MM:SS` (:355-360) via MQPUT w/ msgId/correlId echo (:373-390); error records to error queue (:405-440).

## 4. Functional requirements owned (all cross-ref stream FR)
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| CODATE01-01 | trigger | RETRIEVE → input queue; fail → error + terminate | :140-159 | FR-S22-01 |
| CODATE01-02 | open | three queues opened; fail → error + terminate | :171-271 | FR-S22-02 |
| CODATE01-03 | GET | 5s wait; process / drain / 'INP MQGET ERR:' | :286-337 | FR-S22-02,07 |
| CODATE01-04 | any request | 'SYSTEM DATE : MM-DD-YYYY SYSTEM TIME : HH:MM:SS' reply w/ echoed ids | :343-390 | FR-S22-06 |
| CODATE01-05 | MQPUT fail | 'MQPUT ERR' → error queue + terminate | :392-402 | FR-S22-08 |
| CODATE01-06 | drain end | close queues, RETURN | :442-523 | FR-S22-07 |

## 5. Business rules and validations
None — request content is ignored; every message gets the same formatted reply.

## 6. Data access and boundaries
- MQ lifecycle (S22-B1/B2/B3 — `InProcessMqService`; same envelope/timeout/hardcoded-reply contract as COACCT01).
- No datastore access.

## 7. Error and edge behavior
Identical error-queue + terminate protocol to COACCT01 (:405-440,:456-523).

## 8. Hard-stop boundary
Pure echo service; owns nothing beyond its reply formatting.

## 9. Demoted mechanics
ASKTIME/FORMATTIME → `LocalDateTime.now()` + formatters; MQ mechanics → service API; DISPLAY → logging.

## 10. Traceability
CODATE01-01..06 ↔ FR-S22-01,02,06,07,08 ↔ `DateTimeConsumerTests`.
