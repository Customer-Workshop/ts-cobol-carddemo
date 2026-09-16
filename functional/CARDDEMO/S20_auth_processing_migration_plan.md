# S-20 Authorization Processing + Purge — Migration Plan (`!mf_stream_migration_plan`)

Stream: S-20 (MQ consumer + batch, greenfield). Analysis: `S20_auth_processing_analysis.md`; FR: `S20_functional_requirement.md`.
Target: `spring-boot/` — Java 21, Spring Boot 3.4.5, Spring Batch, Postgres 16, Flyway.

## 1. Goal and scope

Port the pending-auth producer chain: CP00/COPAUA0C queue consumer (parse→resolve→decide→reply→persist), CBPAUP0J expiry purge job, and the load/unload data-transfer utilities. 5 programs + 1 IBM utility step, 2 waves.

## 2. Target-state mapping

| Legacy | Target |
|---|---|
| COPAUA0C/CP00 (MQ trigger) | `AuthProcessingService` registered as `InProcessMqService` consumer of `carddemo.pauth.request` |
| MQ reply | reply via envelope `replyTo` queue (`carddemo.pauth.reply` default) |
| WRITEQ TD CSSL + CCPAUERY | SLF4J structured log (`level`, `subsystem`, `location`, `code1/2`, `eventKey`) |
| PAUTSUM0/PAUTDTL1 | shared `pending_auth_*` JPA entities (S-19 or this stream, first-lander owns) |
| CBPAUP0J BMP | `PendingAuthPurgeJob` via `BatchJobService` |
| LOADPADB/UNLDPADB/UNLDGSAM/DBPAUTP0 | CSV import/export steps (`PendingAuthImportJob`/`PendingAuthExportJob`) + pg_dump for DB-level |
| SYSIN PRM-INFO | JobParameters |

## 3. Boundary decision table (S20-B1..B8 — DECIDED in analysis §5)

| Decision | Seam | Error/idempotency | Owner | Lead time | Routing cutover |
|---|---|---|---|---|---|
| In-process request/reply queues | S20-B1/B2/B3 | envelope carries msgId/correlId/replyTo; bounded poll (500/run, 5s wait); per-msg tx | wave 1 (consumes `queue/InProcessMqService` — **owned by S-22**; this stream creates it if S-22 hasn't landed) | S-22 seam or stub | consumer auto-starts with app |
| Pending-auth JPA entities | S20-B4 | upsert summary + insert detail in same tx; 'II'→merge/ignore on import | wave 1 | V2601 (S-19) or V2701 here | schema lands with first of S-19/S-20 |
| VSAM reuse | S20-B5 | existing repos | wave 1 | none | n/a |
| Error log | S20-B6 | log fields preserved; no storage | wave 1 | none | n/a |
| Purge job | S20-B7 | chunk commit = CHKP; JobParameters; counters logged; RC16→job FAILED | wave 2 | none | admin job launch |
| Import/export utilities | S20-B8 | dup-tolerant import; documented CSV layouts | wave 2 | none | n/a |

## 4. Data and persistence

- Entities `PendingAuthSummary`/`PendingAuthDetail` — shared with S-19 (module property); Flyway V2701 only if S-19's V2601 hasn't landed.
- No new logical tables for S-20 beyond the shared pair; purge/import/export operate on them.
- Request/reply CSV layouts verbatim (`CCPAURQY`/`CCPAURLY`) — parsed to records, reserialized identically.

## 5. Phase 0 scaffolding deltas

`queue/` package + `InProcessMqService` if S-22 hasn't landed (see ownership note); `batch/` job config additions.

## 6. Waves

| Wave | Deliverables | Depends on | Tests |
|---|---|---|---|
| 1 | `AuthProcessingService` (full COPAUA0C port: parse, resolve, decide, reply, persist, log, bounded loop) + pending-auth entities if not yet landed + MQ seam (if not landed) | shared entities | consumer ITs: decision matrix, reply shape, persistence, error paths, 500-cap |
| 2 | `PendingAuthPurgeJob` (CBPAUP0C) + import/export utilities (PAUDBLOD/PAUDBUNL/DBUNLDGS parity, incl. both unload writers documented as one export) | wave 1 | job ITs: expiry edge, count-zero summary delete, chunk-commit cadence, dup-tolerant import, export layout |

## 7. Per-program FR generation

`programs/COPAUA0C_functional_requirement.md`, `programs/CBPAUP0C_functional_requirement.md`, `programs/PAUDBLOD_functional_requirement.md`, `programs/PAUDBUNL_functional_requirement.md`, `programs/DBUNLDGS_functional_requirement.md` — all written. DFSURGU0 (DBPAUTP0 ULU step) is an IBM utility, not a ported program — covered as a boundary row, no FR file.

## 8. Testing and verification

- Consumer: queue-fixture tests (enqueue N requests, assert replies + persisted rows + counters).
- Purge: job-parameter matrix + expired/not-expired/emptied-summary cases; restart check via chunk commits.
- Import/export: golden-file round trip (export → import → export identical).

## 9. Sign-off gate

Per `.migration/05_progress.md`: FR-S20-nn all green; queue + jobs exercised end-to-end; evidence under `functional/CARDDEMO/evidence/`.

## 10. Risks

1. Entity ownership race with S-19 — sequence wave-1s or let first-lander own (analysis §6). MEDIUM.
2. Summary-delete double-approved-count quirk — preserved verbatim; product may want the declined check. LOW.
3. Julian expiry arithmetic at year boundary. LOW.
4. The MQ seam is shared S-20/S-22 — ownership recorded in S-22 docs; interim stub acceptable in wave 1. LOW.

## 11. Effort and sequencing

2 waves; wave 1 ~1 session (biggest program of the four streams), wave 2 ~half session. Sequence against S-19 for entity ownership; independent of S-22 (may carry the seam stub).

## Validation

(1) scope = analysis pin; (2) every boundary decided with owner+lead time; (3) FRs map to waves+tests; (4) Flyway V270x reserved; (5) no `.migration/`/`app/` writes.
