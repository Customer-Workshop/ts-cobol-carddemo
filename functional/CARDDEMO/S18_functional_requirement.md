# S-18 — Branch Export / Import — Functional Requirements

## 1. Purpose and scope

Migrate the on-demand branch export/import pair — CBEXPORT (serialize all five VSAM master files into a single multi-type export file) and CBIMPORT (split an export file back into per-entity outputs plus an error file) — onto the baseline `cbexportJob`/`cbimportJob` Spring Batch jobs. The stream refines existing baseline code to documented parity: record format, ordering, stats, error file, and the deliberately-changed items (DB writes instead of normalized PS files; text record format instead of binary-packed). Scope: docs under `functional/CARDDEMO/`; code in `spring-boot/`.

## 2. Actors and preconditions

- **Actor**: batch operator launching jobs explicitly via `POST /api/admin/jobs/cbexportJob` and `/cbimportJob` (both are `NOTIFY=&SYSUID` manual submits on the mainframe; neither is in Control-M or CA-7).
- **Preconditions**: for export, all five tables populated; for import, an export file present — job parameter `inputFile` (default: `<output-dir>/EXPORT.DATA`); output dir writable.

## 3. Surface specification

| Legacy surface | Target surface |
|---|---|
| CBEXPORT.jcl STEP01 IDCAMS delete/define cluster | writer `shouldDeleteIfExists` — no cluster DDL in target |
| CBEXPORT.jcl STEP02 PGM=CBEXPORT | `cbexportJob` → `cbexportStep` |
| 5 input KSDS DDs | five `RepositoryItemReader`s (PK sort) via `RepositorySequenceReader` |
| EXPFILE `EXPORT.DATA` KSDS 500B | `<output-dir>/EXPORT.DATA` flat file, 500-char lines |
| CVEXPORT 500-byte record (binary COMP/COMP-3 inside data) | target text fixed-width 500-char record (S18-B6 deviation, decided) |
| CBIMPORT.jcl PGM=CBIMPORT | `cbimportJob` |
| CUSTOUT/ACCTOUT/XREFOUT/TRNXOUT/CARDOUT PS outputs | entity writes via JPA repositories (S18-B3 decision) |
| ERROUT `IMPORT.ERRORS` FB 132 | `<output-dir>/CBIMPORT.errors` — target keeps legacy 132-col error record layout |
| finalize DISPLAY stats | per-type + total counts logged at step end (StepExecutionListener) |
| CEE3ABD abend | step failure → exit code |

**Export record contract (target format)**: `type(1) + timestamp(26,'yyyy-MM-dd HH:mm:ss.SSSSSS') + seq(%09d) + '0001' + 'NORTH' + pad(data,455) = 500 chars`, data section = text-form fields in CVEXPORT field order. Timestamp microseconds vs legacy literal `.00` — width-compatible (26), value different (S18 analysis R4).

## 4. Functional requirements (KEEP)

| FR | Flow | Trigger | Result | Program | Cite | Boundary | Test |
|---|---|---|---|---|---|---|---|
| S18-FR-01 | Export writes sections in fixed order C(customer) → A(account) → X(xref) → T(transaction) → D(card), each section in PK order | `cbexportJob` | EXPORT.DATA with records grouped by type in order | CBEXPORT | CBEXPORT.cbl section drivers; CBEXPORT.jcl:49-58 | S18-B1/B2 | re-sequence check: type sequence is C*A*X*T*D* |
| S18-FR-02 | Every record carries header: rec-type, 26-char timestamp, zero-padded seq, '0001', 'NORTH' | per record | uniform 45-char header | CBEXPORT | CVEXPORT.cpy header; CBEXPORT.cbl:172-195 | S18-B6 | header regex per line; seq increments 1..N |
| S18-FR-03 | Per-type payload = the entity's fields in CVEXPORT layout order, text-encoded | per record | 455-char data section | CBEXPORT | CVEXPORT.cpy redefines | S18-B6 | golden record compare per type |
| S18-FR-04 | Log per-type counts + grand total at job end | finalize | stats lines in job log | CBEXPORT | CBEXPORT.cbl:563-573 | — | count lines equal table counts |
| S18-FR-05 | Read export file sequentially; dispatch each record by type C/A/X/T/D | `cbimportJob` | entity persisted per record | CBIMPORT | CBIMPORT.cbl EVALUATE | S18-B1/B3 | exported set round-trips to equal DB rows |
| S18-FR-06 | Unknown/other record type → write legacy-layout error record `timestamp|type|seq|message` padded to 132, increment error count | per bad record | line in CBIMPORT.errors | CBIMPORT | CBIMPORT.cbl 2700-para; ERROUT LRECL=132 | S18-B3 | injected bad-type record produces one 132-char line |
| S18-FR-07 | Short record (<500 chars) → error record, count, continue | per record | error line; job continues | CBIMPORT | baseline `importRecord` short-record check | S18-B3 | truncated fixture → error file + job completes |
| S18-FR-08 | Validation paragraph is a no-op stub logging "No validation errors detected" | finalize | fixed log lines | CBIMPORT | CBIMPORT.cbl:449-452 | — | log contains the stub lines |
| S18-FR-09 | Abend parity: file open/read/write failure → step failure exit code | IO failure | FAILED + non-zero exit | both | CBEXPORT.cbl:579; CBIMPORT.cbl:481-484 | S18-B4 | missing inputFile → FAILED |
| S18-FR-10 | `inputFile` JobParameter selects the export file to consume (replaces EXPFILE DD override) | launch param | reads named file | CBIMPORT | CBIMPORT.jcl:28-29 DD | S18-B1 | run with explicit param |
| S18-FR-11 | Export run regenerates the file wholesale (legacy STEP01 delete/define) | rerun | prior EXPORT.DATA replaced | CBEXPORT | CBEXPORT.jcl:24-38 | S18-B1 | rerun yields fresh file |
| S18-FR-12 | 'D' card records are produced by export and consumed by import (writes cards) — covers legacy's unallocated CARDOUT DD gap | end-to-end | cards round-trip | both | CBEXPORT 5700-para; CBIMPORT.jcl (no CARDOUT) | S18-B7 | 'D' section present; cards saved |

## 5. Error catalogue

| Condition | Legacy | Target |
|---|---|---|
| File-status error any step | CEE3ABD abend | step FAILED → exit code |
| Unknown record type | 132-byte error record + count | same, in error file |
| Record < 500 | (implicit in layout) | error record `RECORD IS SHORTER THAN 500 CHARACTERS` semantics preserved |
| Missing input file | JCL allocation fail / abend | job FAILED at reader open |
| Missing CARDOUT DD | **latent legacy defect** (unresolved) | N/A — card writes go to DB |

## 6. Field/data derivations

- Type letters: C=customer, A=account, X=xref, T=transaction, D=card (CBEXPORT type constants; CVEXPORT redefines).
- Export per-type field order follows the CVEXPORT REDEFINES; numerics are text-encoded in target (`BigDecimal` plain string, `Long` zero-padded where legacy PIC is numeric display) — locked in wave 2 implementation doc, not byte-faithful to legacy binary.
- Import is the mirror: parse `substring(45,500)` against the same field order, save entity.
- Error record layout: `ERR-TIMESTAMP(26) | ERR-RECORD-TYPE(1) | ERR-SEQUENCE(7) | ERR-MESSAGE(50) | pad → 132` (CBIMPORT WS ERROUT layout).

## 7. Mechanics

- `spring.batch.job.enabled=false`; explicit launch; unique `run.id` per run.
- Export: item-per-entity readers, `PassThroughLineAggregator`, `shouldDeleteIfExists`.
- Import: `FlatFileItemReader`, processor returns entity-or-null, writer delegates to repositories + error `FlatFileItemWriter`.

## 8. Acceptance criteria

- **Given** populated tables **when** `cbexportJob` runs **then** `EXPORT.DATA` exists with sections in C/A/X/T/D order, every line exactly 500 chars, header contract per FR-02, and stats lines match table counts.
- **Given** an EXPORT.DATA from a just-run export **when** `cbimportJob` runs **then** every record is dispatched, entities equal the exported set, and the error file is empty.
- **Given** an export file with an injected `Q` record and a 300-char record **when** import runs **then** both land in `CBIMPORT.errors` at 132 chars and the job completes.
- **Given** a missing input file **when** `cbimportJob` is launched **then** the step fails with the 999-equivalent exit.
- **Given** a rerun of export **when** it completes **then** only the fresh EXPORT.DATA exists (delete-if-exists semantics).

## 9. Traceability

FR→program/boundary columns above; waves in `S18_branch_export_import_migration_plan.md`; program detail in `programs/CBEXPORT_functional_requirement.md` and `programs/CBIMPORT_functional_requirement.md`.

## 10. Program index

| Program | Job | FRs | Program FR doc |
|---|---|---|---|
| CBEXPORT | cbexportJob | 01–04, 09, 11 | programs/CBEXPORT_functional_requirement.md |
| CBIMPORT | cbimportJob | 05–10, 12 | programs/CBIMPORT_functional_requirement.md |

## 11. Open questions

- S18-B7: whether to also emit a normalized `CARDOUT`-style file for symmetry with the other four outputs (default: no — DB write only; legacy DD was missing anyway).
- Whether `CBIMPORT.errors` keeps baseline `RECORD n: ...` text or the legacy 132-col layout (FR-06 specifies legacy layout; change requires baseline edit).
