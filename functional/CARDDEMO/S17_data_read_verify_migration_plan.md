# S-17 — Data Read / Verify Jobs — Migration Plan

## 1. Goal and scope

Port the four READ* verify jobs (READACCT/CBACT01C, READCARD/CBACT02C, READCUST/CBCUS01C, READXREF/CBACT03C) and the COBDATFT assembler date-edit to Java 21 / Spring Boot 3.4.5 / Spring Batch on Postgres, per `functional/CARDDEMO/CardDemo_target_state.md`. Deliverables: four `JobConfiguration` classes (one per JCL job), `util/DateEditService`, fixed/variable flat-file writers under `carddemo.batch.output-dir`, and job-order config for the CA-7 chain.

## 2. Baseline position

Greenfield: `spring-boot/` contains **no** S-17 job config (grep `cbact01|cbact02|cbact03|cbcus01|readacct|DateEdit` → only `Cbact04JobConfiguration`, a different program). Reusable baseline idioms to copy: `CbexportJobConfiguration`/`CbimportJobConfiguration` (job/step shape, `spring.batch.job.enabled=false`), `RepositoryItemReader`/`RepositorySequenceReader`, `BatchJobLauncherService` + `BatchAdminController` launch seam, `carddemo.batch.output-dir` output-dir seam (`application.properties:13`), `CobolFieldReader` sign semantics for zoned-decimal writes.

## 3. Target-state mapping

| Legacy | Target |
|---|---|
| JCL job READACCT | `Cbact01JobConfiguration` → bean `readacctJob` |
| JCL jobs READCARD/READCUST/READXREF | `Cbact02JobConfiguration`/`Cbcus01JobConfiguration`/`Cbact03JobConfiguration` → `readcardJob`/`readcustJob`/`readxrefJob` |
| KSDS seq reads | `RepositoryItemReader` sorted by PK (pageSize 50, baseline idiom) |
| `CALL 'COBDATFT'` | `util/DateEditService` — value-object in/out, semantics per program FR |
| DISPLAY → SYSOUT | SLF4J log lines preserving record dump shape |
| PS outputs (PSCOMP/ARRYPS/VBPS) | `FlatFileItemWriter` fixed-width / custom variable-length writer, `shouldDeleteIfExists` |
| `CALL 'CEE3ABD' ABCODE 999` | step failure → process exit 999-equivalent (B-004) |
| CA-7 chain CLOSEFIL→…→OPENFIL | documented job-order config: `readacctJob → readcardJob → readcustJob → readxrefJob` |

## 4. Boundary decision table

| ID | Decision |
|---|---|
| S17-B1 | Job-order config + explicit launches; no scheduler engine port |
| S17-B2 | JPA readers, PK sort; no stored procedures |
| S17-B3 | `DateEditService` port, preserving silent-error contract |
| S17-B4 | Step `ExitStatus`/`SystemExit` mapping for 999 |
| S17-B5 | Flat files in output dir; recreate per run |
| S17-B6 | Log seam; keep double-display quirk (CBACT03C, CBCUS01C) |
| S17-B7 | Pad short layouts with spaces to LRECL — INFERRED, confirm at recon |

## 5. Data / persistence

- No new tables required — all four inputs map to existing entities (`accounts`, `cards`, `card_xrefs`, `customers`).
- **Reserved Flyway range V240x** — expected unused; hold for contingencies (e.g. a job-run audit table if ops asks for one).

## 6. Phase-0 scaffolding deltas

- New: `com.carddemo.util.DateEditService`, `batch/Cbact01JobConfiguration`, `batch/Cbact02JobConfiguration`, `batch/Cbact03JobConfiguration`, `batch/Cbcus01JobConfiguration`, a `VariableLengthLineWriter` (VBPS 12/39-char records), `ZonedDecimalFieldFormatter` companion to `CobolFieldReader` for overpunch writes.
- Register the four job names in `BatchJobLauncherService`'s job map so `POST /api/admin/jobs/{name}` resolves them.
- Add `readacct → readcard → readcust → readxref` to the job-order config/docs.

## 7. Waves

| Wave | Programs / repo area | FR docs | Boundary seams | Strict edge |
|---|---|---|---|---|
| 1 | `util/DateEditService`, flat-file writers, reader seam, exit-code seam — `spring-boot/src/main/java/com/carddemo/{util,batch,data}` | COBDATFT FR | S17-B3, B4, B5 | — |
| 2 | `Cbact01JobConfiguration` + readacct step — `batch/` | CBACT01C FR | S17-B2, B5, B6, B7 | needs wave-1 DateEditService + writers |
| 3 | `Cbact02`, `Cbcus01`, `Cbact03` configs — `batch/` | CBACT02C/CBCUS01C/CBACT03C FRs | S17-B2, B6 | needs wave-1 seams only |

## 8. Per-program FRs

`functional/CARDDEMO/programs/{CBACT01C,CBACT02C,CBACT03C,CBCUS01C,COBDATFT}_functional_requirement.md` — written this pass.

## 9. Testing and verification

- Unit: `DateEditService` truth table (S17-FR-12); formatter round-trips COMP-3/zoned fields.
- Job tests (H2): seed each table, run each job, assert dump-line counts (1× for cards, 2× for xref/customer) and golden-file byte compare of PSCOMP/ARRYPS/VBPS incl. constants and `2525.00` substitution.
- Failure test: forced repository error → FAILED + exit code.
- Recon: run all four jobs against the same fixture data on legacy output reference (JCL-captured SYSOUT/datasets where available) and diff logs/files.

## 10. Sign-off gate

Four jobs green on recon fixtures; golden files byte-equal after documented pad decision; log parity incl. double-dump quirk; exit-code mapping verified; no `app/` changes; `.migration/` untouched.

## 11. Risks

As in analysis §7 — layout padding inference (S17-B7), DateEditService tail bytes, double-display parity, COMP-3/zoned mixing, unenforceable close-window ordering, greenfield volume.

## 12. Effort

~1 session for the doc-level waves: wave 1 ≈ half a session (DateEditService + writers), wave 2 ≈ half (CBACT01C is the only real logic), wave 3 ≈ short (three near-identical configs). External waits: none.

## 13. Validation

Plan covers every inventoried program and boundary; wave order = topological sort; every FR traceable; no unresolved crossing left unflagged.
