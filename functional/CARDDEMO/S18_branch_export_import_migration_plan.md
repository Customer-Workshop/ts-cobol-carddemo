# S-18 — Branch Export / Import — Migration Plan

## 1. Goal and scope

Bring the baseline `cbexportJob`/`cbimportJob` to documented parity with CBEXPORT/CBIMPORT legacy behavior: section order and record contract, per-type stats, legacy-shaped error file, `inputFile` job parameter, abend→exit-code mapping — while keeping the two deliberate deviations (text record format, DB writes instead of normalized PS files).

## 2. Baseline position

Exists and works: `CbexportJobConfiguration` (5 `RepositoryItemReader`s + `RepositorySequenceReader` → `BatchJobService.exportRecord(item, ++sequence)` → `FlatFileItemWriter` → `service.output("EXPORT.DATA")`, `shouldDeleteIfExists`); `CbimportJobConfiguration` (`FlatFileItemReader` on `inputFile` param default `EXPORT.DATA` → `importRecord` → `CBIMPORT.errors` writer); launch via `POST /api/admin/jobs/{cbexportJob,cbimportJob}`.

## 3. Target-state mapping

| Legacy | Target |
|---|---|
| IDCAMS STEP01 delete/define `EXPORT.DATA` | writer `shouldDeleteIfExists` (baseline ✓) |
| 5 KSDS seq reads C→A→X→T→D | `RepositorySequenceReader` over PK-sorted repository readers (baseline ✓) |
| CVEXPORT 500B binary-packed record | text 500-char record `type+ts26+seq9+'0001'+'NORTH'+data455` (baseline ✓ — S18-B6 deviation kept) |
| Finalize DISPLAY counts | `StepExecutionListener` afterStep count lines (baseline: partial — add per-type stats) |
| CBIMPORT type dispatch → 5 PS outputs | processor dispatch → JPA `save()` per entity (baseline ✓ — S18-B3 decision) |
| ERROUT FB 132 | `CBIMPORT.errors` — needs legacy-layout error records (baseline: `RECORD n: <text>` — delta) |
| "No validation errors detected" stub | fixed log lines at finalize (delta — add) |
| CEE3ABD | step failure → exit code (B-004) |
| Manual submit / `NOTIFY=&SYSUID` | `POST /api/admin/jobs/...`, `inputFile` param (baseline ✓) |

## 4. Boundary decision table

| ID | Decision |
|---|---|
| S18-B1 | Flat `EXPORT.DATA` under output dir; no keying/GDG; filename fixed |
| S18-B2 | PK-sorted repository readers, C→A→X→T→D (baseline correct) |
| S18-B3 | Normalized outputs → Postgres `save()`; ERROUT → flat error file with legacy 132-col layout |
| S18-B4 | Step failure → exit code |
| S18-B5 | Explicit launch only; `inputFile` param = DD override |
| S18-B6 | **Text format kept**: export is a target-internal transport; byte-faithfulness to legacy binary is not required. Document the format as the stream contract |
| S18-B7 | CARDOUT DD gap does not carry (cards → DB). Recorded as legacy defect; no action |

## 5. Data / persistence

- No new tables — import writes into existing `customers`/`accounts`/`card_xrefs`/`transactions`/`cards`.
- **Reserved Flyway range V250x** — expected unused; contingency only (e.g. an export-manifest table if ops wants run metadata).

## 6. Phase-0 scaffolding deltas

- `BatchJobService`: per-type counters in export/import paths; error-record formatter emitting `timestamp|type|seq|message` → 132 chars; validation-stub log lines; stats logged at step end.
- `CbimportJobConfiguration`: wire `inputFile` JobParameter (already present — verify); keep short-record guard.
- Optional: `jobName`-level `ExitCodeMapper` for the 999 equivalence if not already global.

## 7. Waves

| Wave | Programs / repo area | FR docs | Boundary seams | Strict edge |
|---|---|---|---|---|
| 1 | Record-contract spec + counters/stats + error-layout formatter — `service/`, `batch/` | CVEXPORT contract (this plan §3) | S18-B1, B3, B6 | — |
| 2 | CBEXPORT parity pass — `batch/CbexportJobConfiguration`, `BatchJobService.exportRecord` | CBEXPORT FR | S18-B2, B4, B5 | needs wave-1 format locked |
| 3 | CBIMPORT parity pass — `batch/CbimportJobConfiguration`, `BatchJobService.importRecord` | CBIMPORT FR | S18-B1, B3, B4, B5, B7 | needs wave-2 export verified |

## 8. Per-program FRs

`functional/CARDDEMO/programs/{CBEXPORT,CBIMPORT}_functional_requirement.md` — written this pass.

## 9. Testing and verification

- Unit: `exportRecord` field ordering/padding per type; `importRecord` dispatch incl. unknown/short records.
- Job tests (H2): export fixture → assert 500-char lines, C/A/X/T/D section order, seq continuity, stats lines; import that file → entity counts equal source; injected `Q` record + truncated record → two 132-char error lines.
- Round-trip test: export then import on a copy schema → tables equal pre-export state.
- Failure test: missing `inputFile` → FAILED.
- Recon: counts reconciled to legacy stats semantics (per-type + total + errors).

## 10. Sign-off gate

Round-trip green end-to-end; error file matches legacy layout; stats logged; deviations (S18-B3/B6/B7) documented in the stream docs and agreed; no `app/` or `.migration/` changes.

## 11. Risks

As in analysis §7 — text-vs-binary contract is the headline deviation; CARDOUT legacy defect noted; timestamp microseconds drift; baseline error-format change must not break existing consumers (none found).

## 12. Effort

~1 session total: wave 1 + 2 ≈ half a session (format + export parity), wave 3 ≈ half (import parity + round-trip harness).

## 13. Validation

All programs/boundaries covered; waves topological; FRs traceable; deviations decided, defects flagged.
