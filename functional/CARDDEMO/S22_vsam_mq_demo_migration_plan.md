# S-22 VSAM-MQ Request/Reply Demo — Migration Plan (`!mf_stream_migration_plan`)

Stream: S-22 (SUBTRANSACTION, greenfield). Analysis: `S22_vsam_mq_demo_analysis.md`; FR: `S22_functional_requirement.md`.
Target: `spring-boot/` — Java 21, Spring Boot 3.4.5. No persistence changes (Flyway V290x reserved but likely unused).

## 1. Goal and scope

Port the two MQ demo consumers and — more importantly — stand up the **shared in-process MQ seam** both this stream and S-20 depend on. 2 programs, 1 wave.

## 2. Target-state mapping

| Legacy | Target |
|---|---|
| IBM MQ queues + MQOPEN/GET/PUT/CLOSE | `com.carddemo.queue.InProcessMqService` — `ConcurrentHashMap<String, BlockingQueue<Message>>`; `Message{msgId, correlId, replyTo, format, payload}` |
| MQTM trigger (CICS RETRIEVE) | `registerConsumer(String triggerQueue, Consumer<Message>)` at startup; a service wrapper spawns the drain loop |
| MQGET 5s wait | `queue.poll(5, TimeUnit.SECONDS)` → empty = MQRC-NO-MSG-AVAILABLE |
| MQPUT / MQPUT1 | `put(queueName, message)` (both open-once and one-shot styles collapse to the same call) |
| `CARD.DEMO.REPLY.ACCT`/`CARD.DEMO.REPLY.DATE`/`CARD.DEMO.ERROR` | named queues `carddemo.reply.acct`, `carddemo.reply.date`, `carddemo.error` |
| COACCT01 | `AcctInquiryConsumer` (registered on `carddemo.request.acct`) |
| CODATE01 | `DateTimeConsumer` (registered on `carddemo.request.date`) |
| ACCTDAT read | `AccountRepository` |
| SYNCPOINT per msg | per-message try/commit scope |

## 3. Boundary decision table (S22-B1..B5 — DECIDED in analysis §5)

| Decision | Seam | Error/idempotency | Owner | Lead time | Routing cutover |
|---|---|---|---|---|---|
| In-process MQ service | S22-B1 | poll timeout=empty; put failures→exception; at-most-once | **S-22 (this stream)** | none | available at startup |
| Fixed reply/error queue names | S22-B2 | preserved verbatim | this wave | none | n/a |
| Consumer registration | S22-B3 | drain-and-exit loop | this wave | none | n/a |
| Account read | S22-B4 | miss→INVALID reply; other→error queue | this wave | none | n/a |
| Per-msg unit of work | S22-B5 | n/a (read-only program) | this wave | none | n/a |

## 4. Data and persistence

None new — no Flyway migration needed. V290x range reserved for any future demo tables; skipped if unused (recorded in plan, not a gap).

## 5. Phase 0 scaffolding deltas

New package `com.carddemo.queue` — the only truly new infrastructure across S-19..S-22.

## 6. Waves

| Wave | Deliverables | Depends on | Tests |
|---|---|---|---|
| 1 | `InProcessMqService` + `Message` envelope + `MqTriggerListener` registration; `AcctInquiryConsumer`; `DateTimeConsumer`; named-queue config | — | seam unit tests (put/poll/correlId/timeout/error queue), consumer ITs (reply text, invalid params, drain exit) |

## 7. Per-program FR generation

`programs/COACCT01_functional_requirement.md`, `programs/CODATE01_functional_requirement.md` — written.

## 8. Testing and verification

- Seam tests: enqueue→poll round trip, 5s timeout→empty, correlId/msgId propagation, error-queue write on simulated failure.
- Consumer tests: INQA happy path + NOTFND + bad func; CODATE01 format check vs `MM-DD-YYYY`/`HH:MM:SS`.
- Contract test for S-20's usage: `put` w/ `replyTo` and bounded-loop drain = the seam must serve both.

## 9. Sign-off gate

Per `.migration/05_progress.md`: FR-S22-nn green; seam contract documented in code (`InProcessMqService` javadoc) — that IS the hand-off artifact for S-20.

## 10. Risks

1. Seam contract must satisfy S-20's replyTo-routed + MQPUT1 style — design the API once (contract in §2) or S-20 will fork it. MEDIUM.
2. If S-20 lands first it creates the seam — keep the contract identical (this doc is the reference). LOW.

## 11. Effort and sequencing

1 wave, ~half session. Land before or parallel with S-20 wave 1; whichever lands first owns `InProcessMqService` (this doc's contract is authoritative).

## Validation

(1) scope = analysis pin; (2) boundaries decided w/ owner+lead time; (3) FRs → wave+tests; (4) Flyway V290x reserved/unused; (5) no `.migration/`/`app/` writes.
