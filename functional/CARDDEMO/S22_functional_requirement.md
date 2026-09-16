# S-22 VSAM-MQ Request/Reply Demo — Stream Functional Requirements (`!mf_stream_fr_generation`)

Stream: S-22 (SUBTRANSACTION consumers). Analysis: `S22_vsam_mq_demo_analysis.md`. Plan: `S22_vsam_mq_demo_migration_plan.md`.
Program FRs: `programs/COACCT01_*.md`, `programs/CODATE01_*.md`.

## 1. Purpose and scope

Two demo request/reply consumers proving the queue seam: an account inquiry (request `INQA`+acct-id → labeled account summary from ACCTDAT) and a date/time echo (any request → system date/time). Both run as trigger-started queue drainers: read until the input queue empties, reply on the program's hardcoded reply queue, log protocol failures onto the error queue.

This stream also delivers the **shared** `queue/InProcessMqService` consumed by S-20 (and any later MQ use).

## 2. Actors and preconditions

- External requester puts a 1000-byte request (`func X(4) + key 9(11) + filler`) on the program's trigger queue with msgId/correlId; reads the program's hardcoded reply queue.
- Preconditions: `InProcessMqService` running; `accounts` table seeded.

## 3. Surface specification

- **Queues**: trigger-named input queue (from MQTM-equivalent consumer registration); replies on `carddemo.reply.acct` / `carddemo.reply.date`; errors on `carddemo.error`.
- **Message envelope**: `{msgId, correlId, replyTo, format=STRING, payload}`; reply echoes request msgId+correlId.
- **Poll semantics**: 5s wait per GET; queue empty → consumer returns (bounded loop, not a daemon); per-message unit of work (syncpoint).

## 4. Functional requirements (KEEP)

| ID | Flow | Business trigger | Observable result | Program | Cite | Boundary | Covering test |
|---|---|---|---|---|---|---|---|
| FR-S22-01 | trigger | consumer registered for queue X | RETRIEVE-equivalent resolves input queue name | both | COACCT01.cbl:191-210 | S22-B3 | consumer start test |
| FR-S22-02 | get | request present | msg w/ envelope + payload | both | :334-388 | S22-B1 | poll test |
| FR-S22-03 | acct reply | func='INQA', key>0, acct found | labeled `WS-ACCT-RESPONSE` text on `carddemo.reply.acct`, msgId/correlId echoed | COACCT01 | :393-435 | S22-B2,B4 | reply test |
| FR-S22-04 | acct miss | NOTFND | 'INVALID REQUEST PARAMETERS ACCT ID : <key>' reply | COACCT01 | :428-435 | S22-B2,B4 | reply test |
| FR-S22-05 | bad func | func≠'INQA' or key=0 | 'INVALID REQUEST PARAMETERS ACCT ID : <key> FUNCTION : <f>' reply | COACCT01 | :448-456 | S22-B2 | reply test |
| FR-S22-06 | date reply | any request | 'SYSTEM DATE : MM-DD-YYYY SYSTEM TIME : HH:MM:SS' on `carddemo.reply.date` | CODATE01 | :343-361 | S22-B2 | reply test |
| FR-S22-07 | drain | empty queue after 5s | loop ends; queues closed; return | both | :377-378,:538-549 | S22-B3 | drain test |
| FR-S22-08 | error path | MQ/READ failure | `MQ-ERR-DISPLAY` record put on `carddemo.error` | both | :501-536 | S22-B1 | error-queue test |

## 5. Validation and error catalogue

| Condition | Result | Cite |
|---|---|---|
| MQ open fail (input/reply/error) | error-queue write or DISPLAY + terminate | COACCT01.cbl:246-321 |
| MQGET non-empty-fail | 'INP MQGET ERR:' to error queue + terminate | :380-387 |
| VSAM READ other | 'ERROR WHILE READING ACCTFILE' to error queue + terminate | :439-445 |
| MQPUT fail | 'MQPUT ERR' to error queue + terminate | :492-498 |
| MQCLOSE fail | 'MQCLOSE ERR' handling | :552-620 |

## 6. Field and data derivations

- `INQA` key = 11-digit account id; read `ACCTDAT` → labeled fields (labels verbatim in `WS-ACCT-RESPONSE` :130-169).
- CODATE01 reply: FORMATTIME `MMDDYYYY` + `TIME` w/ '-' date sep and ':' time sep (:347-353).

## 7. Mechanics (demoted, cited)

MQ connect/open/close/copybooks → `InProcessMqService` API; MQTM → consumer registration; SYNCPOINT → per-message tx; 5s MQGMO-WAIT → poll timeout; MQCC/MQRC codes → Java exceptions; DISPLAY diagnostics → logging.

## 8. Acceptance criteria (Given/When/Then)

- FR-S22-03: Given acct row + 'INQA'+id request, When consumer runs, Then labeled reply lands on `carddemo.reply.acct` w/ echoed ids.
- FR-S22-04/05: Given unknown acct / bad func, Then the documented INVALID reply lands.
- FR-S22-06: Given any request on the date queue, Then the formatted system date/time reply lands.
- FR-S22-07: Given drained queue, Then consumer exits cleanly after one 5s empty poll.

## 9. Traceability matrix

| FR | Program FR | Java surface |
|---|---|---|
| FR-S22-01..05,07,08 | COACCT01-nn | `AcctInquiryConsumer` + `InProcessMqService` |
| FR-S22-06..08 | CODATE01-nn | `DateTimeConsumer` + `InProcessMqService` |

## 10. Program index

| Program | Role | Program FR |
|---|---|---|
| COACCT01 | acct inquiry consumer | programs/COACCT01_functional_requirement.md |
| CODATE01 | date/time consumer | programs/CODATE01_functional_requirement.md |

## 11. Open questions and assumptions

1. Request `replyTo` is ignored in source (hardcoded reply queue) — preserved; seam still carries `replyTo` in the envelope so S-20's replyTo-driven replies work.
2. Requesters external to the app — for the demo, tests enqueue directly; no UI.
