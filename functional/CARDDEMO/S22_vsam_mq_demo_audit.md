# S-22 VSAM-MQ Request/Reply Demo — Independent Audit

- **Stream:** S-22 (vsam_mq_demo) — SUBTRANSACTION consumers COACCT01 / CODATE01 plus the shared `com.carddemo.queue.InProcessMqService` seam.
- **Audited branch:** `devin/audit-s22-java` on `devin/1789516557-carddemo-java-engagement`, HEAD `410e5d0f826ccd3a6a2f4ea04eee673f64c240d8`. Wave shipped as PR #112 (commit `e5feca5`).
- **Auditor:** independent auditor (did not perform the migration).
- **Date:** 2026-09-16
- **Scope:** `spring-boot/src/main/java/com/carddemo/queue/*`, `service/AcctInquiryConsumer`, `service/DateTimeConsumer`, `data/CobolFieldFormatter.signedDecimal`; tests `InProcessMqServiceTest`, `AcctInquiryConsumerTest`, `DateTimeConsumerTest`, `CobolFieldFormatterTest`, `MqRequestReplyIntegrationTest`; COBOL sources `app/app-vsam-mq/cbl/COACCT01.cbl`, `CODATE01.cbl`, `app/cpy/CVACT01Y.cpy`, `app/app-vsam-mq/csd/CRDDEMOM.csd`. S-20's `AuthProcessingService` (a consumer of this seam) is out of scope except where it exercises the seam contract.

## Verdict: **PASS with findings**

Every FR-S22 row is implemented and traced to named tests; all 32 scoped tests are green. Findings are LOW/INFO only: two error-catalogue paths have no in-process equivalent (unreachable except interrupt), and a defensive digit check diverges from the COBOL's raw-byte compare on malformed keys.

## 1. Traceability matrix

| FR | Requirement | Implementation | Test(s) | Status |
|---|---|---|---|---|
| FR-S22-01 | Trigger: RETRIEVE-equivalent resolves input queue name; consumer registered for queue | `MqTriggerListener.run` registers both consumers at startup (MqTriggerListener.java:38-44); `InProcessMqService.registerConsumer` binds handler to trigger queue (InProcessMqService.java:127-137) | `InProcessMqServiceTest.registeredConsumerDrainsThenExitsAndPutRetriggers`, `AcctInquiryConsumerTest.registeredConsumerDrainsRequestAndReplies`, `DateTimeConsumerTest.registeredConsumerDrainsRequestAndReplies`; every `MqRequestReplyIntegrationTest` method (startup registration via ApplicationRunner) | PASS |
| FR-S22-02 | GET: request present → envelope + payload; 5s wait | `InProcessMqService.poll` waits `getWaitMillis` then returns message or null (InProcessMqService.java:106-118); envelope `Message{msgId,correlId,replyTo,format,payload}` (Message.java:9) | `InProcessMqServiceTest.putPollRoundTripPreservesEnvelope`, `emptyPollWaitsThenReturnsNullForNoMsgAvailable`, `putAssignsMsgIdWhenAbsentLikeAQueueManager` | PASS |
| FR-S22-03 | `INQA` + key>0 + acct found → labeled `WS-ACCT-RESPONSE` text on `carddemo.reply.acct`, msgId/correlId echoed | `AcctInquiryConsumer.processRequest` (:68-93), `accountResponse` (:100-115), `putReply` echoes ids (:125-135) | `AcctInquiryConsumerTest.inqaWithKnownAccountRepliesLabeledRecordOnHardcodedQueue`, `replyIgnoresRequestReplyToQuirk`; `MqRequestReplyIntegrationTest.acctInquiryRoundTripOnSeededAccountOne`, `replyStillGoesToHardcodedQueueWhenRequestSuppliesReplyTo` | PASS |
| FR-S22-04 | NOTFND → `'INVALID REQUEST PARAMETERS ACCT ID : <key>'` | `AcctInquiryConsumer.java:85-88` | `AcctInquiryConsumerTest.unknownAccountRepliesInvalidParametersWithoutFunction`; `MqRequestReplyIntegrationTest.unknownAccountRepliesInvalidParameters` | PASS |
| FR-S22-05 | func≠'INQA' or key=0 → `'INVALID REQUEST PARAMETERS ACCT ID : <key>FUNCTION : <f>'` | `AcctInquiryConsumer.java:74,89-92` | `AcctInquiryConsumerTest.wrongFunctionRepliesInvalidParametersWithFunction`, `zeroKeyRepliesInvalidParameters`, `nonNumericKeyRepliesInvalidParametersWithKeyVerbatim`; `MqRequestReplyIntegrationTest.wrongFunctionRepliesInvalidParametersWithFunction` | PASS (but see F-02 for the non-digit-key divergence) |
| FR-S22-06 | Any request → `'SYSTEM DATE : MM-DD-YYYY SYSTEM TIME : HH:MM:SS'` on `carddemo.reply.date` | `DateTimeConsumer.processRequest` (:58-63), `putReply` (:66-76) | `DateTimeConsumerTest.anyPayloadGetsFormattedDateTimeReply`, `emptyPayloadStillReplies`; `MqRequestReplyIntegrationTest.dateRequestRoundTrip` | PASS |
| FR-S22-07 | Empty queue after 5s → loop ends; queues closed; return | `InProcessMqService.drain` exits after one empty poll with a locked double-check (:150-181); `close` no-op (:81-83); `put` respawns a dead drain (:96-101) | `InProcessMqServiceTest.drainHandlesEveryMessageThenExitsOnEmptyPoll`, `registeredConsumerDrainsThenExitsAndPutRetriggers`, `openIsIdempotentAndCloseIsANoOp` | PASS |
| FR-S22-08 | MQ/READ failure → `MQ-ERR-DISPLAY` record on `carddemo.error` | `AcctInquiryConsumer.putError` (:141-149) + read-fail path (:80-81) + put-fail path (:130-134); `DateTimeConsumer.putError` (:79-87); `MqErrorRecord.build` (:22-29) | `AcctInquiryConsumerTest.readFailureWritesErrorQueueThenTerminates`, `replyPutFailureWritesMqputErrToErrorQueueThenTerminates`; `DateTimeConsumerTest.replyPutFailureWritesMqputErrToErrorQueueThenTerminates`; `InProcessMqServiceTest.handlerFailureTerminatesTheDrainButQueueKeepsWorking` | PARTIAL — VSAM-READ and MQPUT failure rows shipped+tested; the `'INP MQGET ERR:'`, `MQOPEN`-fail and `RETRIEVE`-fail catalogue rows have no Java equivalent (see F-01) |

## 2. COBOL parity spot-checks

**Message catalogue** (every user-visible literal in the FR doc, compared verbatim):

| Text | COBOL | Java | Match |
|---|---|---|---|
| `'ACCOUNT ID : '` … `'GROUP ID : '` — all 11 labels of `WS-ACCT-RESPONSE` | `COACCT01.cbl:132-169` | `AcctInquiryConsumer.java:104-114` | verbatim |
| `'INVALID REQUEST PARAMETERS ACCT ID : ' + key` (NOTFND, no FUNCTION) | `COACCT01.cbl:429-435` | `AcctInquiryConsumer.java:87` | verbatim |
| `'INVALID REQUEST PARAMETERS ACCT ID : ' + key + 'FUNCTION : ' + func` (no space before FUNCTION — STRING DELIMITED BY SIZE) | `COACCT01.cbl:449-455` | `AcctInquiryConsumer.java:91` | verbatim |
| `'ERROR WHILE READING ACCTFILE'` (X(25) slot → truncated to `'ERROR WHILE READING ACCTF'`) | `COACCT01.cbl:442-443`, slot `PIC X(25)` at `:61` | `AcctInquiryConsumer.java:80` + `MqErrorRecord.java:31-37` | verbatim incl. 25-char truncation (asserted byte-exact in `AcctInquiryConsumerTest.java:146-150`) |
| `'MQPUT ERR'` w/ reply queue name (not input queue) in the queue slot | `COACCT01.cbl:495-498`; `CODATE01.cbl:399-402` | `AcctInquiryConsumer.java:132`; `DateTimeConsumer.java:73` | verbatim, tested (`AcctInquiryConsumerTest.java:155-172`, `DateTimeConsumerTest.java:72-87`) |
| `'SYSTEM DATE : ' + MM-DD-YYYY + 'SYSTEM TIME : ' + HH:MM:SS` (no space between fields) | `CODATE01.cbl:355-360` | `DateTimeConsumer.java:60-61` | verbatim, regex-pinned in tests |
| `'INP MQGET ERR:'` → error queue + terminate | `COACCT01.cbl:384-386`; `CODATE01.cbl:333-335` | — not present — | **GAP → F-01** |
| `'INP MQOPEN ERR'` / `'OUT MQOPEN ERR'` / `'ERR MQOPEN ERR'` | `COACCT01.cbl:250,284,319` | — not present (open only throws on blank name, InProcessMqService.java:188-191) — | **GAP → F-01** |
| `'MQCLOSE ERR'` handling | `COACCT01.cbl:571,593,616` | `close()` is a no-op that cannot fail (InProcessMqService.java:81-83) | documented deviation (plan §2) |
| `'CICS RETREIVE'` error path (`'RESP: <r1> <r2>END'`) | `COACCT01.cbl:200-209` | — not present (registration is static) — | **GAP → F-01** |

**Edit/validation rules:**

- Request layout `WS-FUNC X(4) + WS-KEY 9(11) + filler` (`COACCT01.cbl:109-112`) → `func = payload[0..4)`, `key = payload[4..15)` padded (AcctInquiryConsumer.java:70-73). Match.
- Guard `IF WS-FUNC = 'INQA' AND WS-KEY > ZEROES` (:393) → `INQA.equals(func) && key matches \d{11} && >0` (AcctInquiryConsumer.java:74). Functionally equal for all-digit input; **diverges for non-digit bytes — F-02**.
- `S9(10)V99` zoned-decimal rendering with sign overpunch (`{`/`A`-`I` pos, `}`/`J`-`R` neg) → `CobolFieldFormatter.signedDecimal` (CobolFieldFormatter.java:24-42), round-trip-tested against `CobolFieldReader`; the IT's expected record matches the seed bytes `acctdata.txt` acct 1 (`00000000001Y00000001940{…`) byte-for-byte.
- Date fields `X(10)` verbatim (`ACCT-OPEN-DATE` etc., `CVACT01Y.cpy:10-12`) → `ISO_LOCAL_DATE` + pad (AcctInquiryConsumer.java:117-119); seed dates are `YYYY-MM-DD` strings, so bytes match.
- `FORMATTIME MMDDYYYY DATESEP('-') TIME TIMESEP` (`CODATE01.cbl:347-353`) → `MM-dd-yyyy`/`HH:mm:ss` (DateTimeConsumer.java:33-35). Bare `TIMESEP` yields `:` per the CICS FORMATTIME reference — verified against IBM doc, and `WS-TIME PIC X(8)` (`CODATE01.cbl:37`) fits `HH:MM:SS` exactly.

**File/DB access semantics:**

- `EXEC CICS READ DATASET('ACCTDAT') RIDFLD(acct-id X(11))` (:396-404) → `AccountRepository.findById(Long.parseLong(key))` (AcctInquiryConsumer.java:77) per S22-B4/B-009; `NORMAL`→reply, `NOTFND`→invalid reply, `OTHER`→error queue + terminate — all three arms present (:83-88, :80-81).
- `SYNCPOINT` before each GET (:326-328) and `MQPMO-SYNCPOINT` on puts (:475-485) → per-message unit of work / at-most-once (InProcessMqService.java:44-45; documented S22-B5).
- `MQGMO-WAITINTERVAL 5000` (:337) → `DEFAULT_GET_WAIT_MILLIS = 5000` (InProcessMqService.java:55).
- Reply `MQMD-MSGID/CORRELID` echo (:469-470) → `new Message(request.msgId(), request.correlId(), …)` (:126); format `MQFMT-STRING` (:471) → `Message.FORMAT_STRING` (Message.java:12 — `'MQSTR'` 5 chars vs `X(8)` `'MQSTR   '`; in-process only, see F-04).
- `MQ-ERR-DISPLAY` fixed block `X(25)+X(2)+X(25)+X(2)+9(2)+X(2)+9(5)+X(2)+X(48) = 113` (:58-67) → `MqErrorRecord.build` (MqErrorRecord.java:22-29, `LENGTH=113`); layout asserted byte-exact in `AcctInquiryConsumerTest.java:146-150`.
- Full `X(1000)` buffers (`MQ-BUFFER`/`REPLY-MESSAGE`, :50/:107, `:467-468`) → `BUFFER_LENGTH=1000` + `pad` (:43,:127).
- `REPLYTOQ` captured into `SAVE-REPLY2Q` but unused (:370-371) → request `replyTo` ignored; replies go to `MqQueues.REPLY_ACCT`/`REPLY_DATE` only (quirk pinned in `AcctInquiryConsumerTest.java:82-89` and the IT `:55-65`).
- Hardcoded queue names `'CARD.DEMO.REPLY.ACCT'` (:198) / `'CARD.DEMO.REPLY.DATE'` (`CODATE01.cbl:147`) / `'CARD.DEMO.ERROR'` (:294) → `carddemo.reply.acct` / `carddemo.reply.date` / `carddemo.error` (MqQueues.java:11-23) — documented naming convention (plan §2).
- Error-queue self-failure → `DISPLAY MQ-ERR-DISPLAY` (:533-534, `:320`) → `log.error` (AcctInquiryConsumer.java:146-148; DateTimeConsumer.java:84-86) — documented demotion (FR doc §7).

**Exit/abend codes:** no abend codes exist in this stream; termination is `8000-TERMINATION` → close queues → `EXEC CICS RETURN` (`COACCT01.cbl:538-550`, `CODATE01.cbl:442-454`) → `MqProcessingException` stops the drain (`InProcessMqService.drain` :169-179); `MqProcessingException` javadoc cites 8000-TERMINATION. Match.

## 3. Stub/placeholder sweep

Searched all S-22 main + test files (`com/carddemo/queue/*`, `AcctInquiryConsumer`, `DateTimeConsumer`, `CobolFieldFormatter`, and the five test classes) for `TODO`, `FIXME`, `not implemented`, `UnsupportedOperationException`, `@Disabled`, `stub`, `placeholder` — **zero hits**. Read all five test files end-to-end: no commented-out or trivially-true assertions; every test makes concrete byte/value assertions. No hardcoded happy-path returns: `accountResponse` reads entity fields, `DateTimeConsumer` reads the clock, `putError` builds the real 113-char record. `date10` returning 10 spaces on null date mirrors `X(10) VALUE SPACES`, not a stub.

## 4. Documented deviations vs shipped code

| Deviation | Source | Shipped? | Evidence |
|---|---|---|---|
| S22-B1: in-process seam — `Map<String,BlockingQueue<Message>>`, `put`/`poll`/`registerConsumer`; OPEN=lookup, CLOSE=no-op, empty poll = MQRC-NO-MSG-AVAILABLE | analysis §5, plan §2/§3 | yes | InProcessMqService.java:61-63,75-137,150-181 |
| S22-B2: fixed reply/error queue names `carddemo.reply.acct|date|error`; `replyTo` ignored (quirk preserved verbatim) | analysis §5 | yes | MqQueues.java:11-23; AcctInquiryConsumer.java:125-135; DateTimeConsumer.java:66-76; tested |
| S22-B3: `registerConsumer(queue,handler)` at startup; drain-and-exit on one empty 5s poll | analysis §5 | yes | InProcessMqService.java:127-137,150-181; MqTriggerListener.java:38-44 |
| S22-B4: ACCTDAT read → `AccountRepository.findById`; NOTFND→invalid reply; other→error queue | analysis §5 (B-009) | yes | AcctInquiryConsumer.java:77-88 |
| S22-B5: per-message unit of work; at-most-once | analysis §5 | yes | InProcessMqService.java:44-45,169-179; `handlerFailureTerminatesTheDrainButQueueKeepsWorking` |
| Wave PR: FR-doc whitespace discrepancy — COBOL STRING has no filler → no space before `FUNCTION` / `SYSTEM TIME`; implemented per COBOL | PR #112 body | yes | AcctInquiryConsumer.java:91; DateTimeConsumer.java:60-61; asserted in tests |
| Wave PR: replies echo request msgId/correlId; full X(1000) payloads; X(25) error-msg truncation; MQPUT-fail names reply queue | PR #112 body | yes | cited above, all tested |
| Flyway V290x reserved, unused if no new tables | plan §4 | yes | `db/migration/` contains no V29xx file (max V2801) |
| S-20 seam contract: `put` with `replyTo` + bounded drain must work | plan §8 | yes | `InProcessMqServiceTest.requestReplyContractSupportsReplyToRoutingForS20` |
| **Undocumented:** `'INP MQGET ERR:'`, MQOPEN-fail, RETRIEVE-fail error paths not ported | — | missing | F-01 |
| **Undocumented:** `\d{11}` digit guard changes reply text for non-digit keys | — | missing | F-02 |

## 5. Test evidence

Command (from `spring-boot/`):

```
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -q test -Dtest='InProcessMqServiceTest,AcctInquiryConsumerTest,DateTimeConsumerTest,CobolFieldFormatterTest,MqRequestReplyIntegrationTest'
```

Observed (surefire):

| Class | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| com.carddemo.queue.InProcessMqServiceTest | 8 | 0 | 0 | 0 |
| com.carddemo.service.AcctInquiryConsumerTest | 9 | 0 | 0 | 0 |
| com.carddemo.service.DateTimeConsumerTest | 5 | 0 | 0 | 0 |
| com.carddemo.data.CobolFieldFormatterTest | 5 | 0 | 0 | 0 |
| com.carddemo.MqRequestReplyIntegrationTest | 5 | 0 | 0 | 0 |
| **Total** | **32** | **0** | **0** | **0** |

Consistent with PR #112's claim of 32 new tests. The full-suite run is covered by a separate audit, not this one.

## 6. Findings

| # | Severity | Title | Evidence | Recommendation |
|---|---|---|---|---|
| F-01 | LOW | MQGET-fail / MQOPEN-fail / RETRIEVE-fail error-catalogue rows have no Java equivalent | FR §5 catalogue rows vs `InProcessMqService.drain` (InProcessMqService.java:154-159) returning silently on `InterruptedException` — no `'INP MQGET ERR:'` record reaches `carddemo.error` — and `open` failing only on blank names (InProcessMqService.java:188-191). COBOL: `COACCT01.cbl:384-386,250,284,319,200-209`; `CODATE01.cbl:333-335`. Corresponding trace row FR-S22-08 marked PARTIAL. Unreachable in practice (in-process queues + constant names), so impact is documentation-fidelity only. | Record the dropped failure paths as a documented deviation in the analysis/plan, or have `drain` write `'INP MQGET ERR:'` to the error queue before exiting on non-timeout poll failure. |
| F-02 | LOW | Digit-guard divergence on non-numeric keys | `AcctInquiryConsumer.java:74` requires `key.matches("\d{11}")`; COBOL `WS-KEY PIC 9(11)` compares zone-ignored digit nibbles, so e.g. `'ABCDEFGHIJK'` (EBCDIC C1–D2 → digits 12345678912) satisfies `WS-KEY > ZEROES` (`COACCT01.cbl:393`), attempts the VSAM READ, misses → `'INVALID REQUEST PARAMETERS ACCT ID : ABCDEFGHIJK'` **without** `FUNCTION` (`:428-435`). Java instead replies `'…ABCDEFGHIJKFUNCTION : INQA'` (:91); `AcctInquiryConsumerTest.java:127-135` codifies the Java text. Same divergence for keys containing spaces or sign-zoned last bytes. | Either document as a deliberate hardening deviation, or route non-digit keys through the repository lookup so they take the NOTFND reply text verbatim. |
| F-03 | INFO | `Message.FORMAT_STRING` is `'MQSTR'` (5 chars), not the `MQMD-FORMAT X(8)` value `'MQSTR   '` | Message.java:12 vs `COACCT01.cbl:471`. Never leaves the process; cosmetic only. | Optional: pad to 8 for field-faithfulness. |
| F-04 | INFO | `MQ-MSG-COUNT` diagnostic not ported | `COACCT01.cbl:375` / `CODATE01.cbl:324` count processed messages; never displayed or sent — no user-visible effect. | None required; note only. |
