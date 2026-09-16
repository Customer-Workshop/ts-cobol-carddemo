# CBIMPORT — Branch Data Import Program — Functional Requirements

## 1. Identity and role

- **Program**: `CBIMPORT` (`app/cbl/CBIMPORT.cbl`, ~487 lines) — single-step job `CBIMPORT.jcl` (`app/jcl/CBIMPORT.jcl:22`, STEP01).
- **Stream**: S-18, wave 3. **Shared ownership**: consumer of the CVEXPORT contract produced by CBEXPORT.
- **Baseline**: `CbimportJobConfiguration` (`cbimportJob`) + `BatchJobService.importRecord` — exists; refine parity.
- **Role**: read an export file, dispatch each record by type to the matching output, route unknown/short records to an error file.

## 2. Trigger / caller contract

`EXEC PGM=CBIMPORT`; no PARM. Target: `POST /api/admin/jobs/cbimportJob` with `inputFile` JobParameter (default `<output-dir>/EXPORT.DATA`).

## 3. Inputs and outputs

| DD | Direction | Layout | Target |
|---|---|---|---|
| EXPFILE | in | `EXPORT.DATA` 500B records | `inputFile` flat file |
| CUSTOUT | out | FB 500 `CUSTDATA.IMPORT` | `customers` table writes |
| ACCTOUT | out | FB 300 `ACCTDATA.IMPORT` | `accounts` table writes |
| XREFOUT | out | FB 50 `CARDXREF.IMPORT` | `card_xrefs` table writes |
| TRNXOUT | out | FB 350 `TRANSACT.IMPORT` | `transactions` table writes |
| CARDOUT | out | **DD missing from CBIMPORT.jcl** — latent legacy defect (S18-B7) | `cards` table writes |
| ERROUT | out | FB 132 `IMPORT.ERRORS` | `CBIMPORT.errors` flat file |
| SYSOUT | out | stats + validation stub | job log |

## 4. FR table (KEEP)

| FR | Rule | Cite |
|---|---|---|
| 01 | Open EXPFILE + outputs; open error → abend | open paras; :481-484 |
| 02 | Read sequentially until '10'; EVALUATE `EXPORT-REC-TYPE` → C/A/X/T/D write paragraphs | EVALUATE |
| 03 | 'C' → customer record layout → CUSTOUT; 'A' → account → ACCTOUT; 'X' → xref → XREFOUT; 'T' → transaction → TRNXOUT; 'D' → card → CARDOUT | per-type paras |
| 04 | `OTHER` type → 2700 para: write `ERR-TIMESTAMP(26)\|ERR-RECORD-TYPE(1)\|ERR-SEQUENCE(7)\|ERR-MESSAGE(50)` padded to 132 → ERROUT; increment error count | 2700 para |
| 05 | `3000-VALIDATE-IMPORT` is a stub: DISPLAY "No validation errors detected" — preserve as no-op log | :449-452 |
| 06 | Finalize: DISPLAY per-type + total + error counts | :465-478 |
| 07 | Any file-status failure → `CALL 'CEE3ABD'` | :481-484 |

## 5. Business rules

- No field transformation: type layouts map back to the normalized record layouts verbatim.
- The "validation" claimed in comments does not exist — only type dispatch + error capture.
- Target import writes entities to Postgres (S18-B3 decision) rather than normalized flat files — the CARDOUT gap therefore does not carry.

## 6. Data access / boundaries

- Export flat file in (S18-B1); record contract (S18-B6); entity writes + error file (S18-B3); abend→exit code (S18-B4); launch/`inputFile` seam (S18-B5); CARDOUT defect (S18-B7).

## 7. Error / edge behavior

- Unknown types do not fail the job — they are counted and logged to ERROUT; job completes.
- Short records (<500 chars in target format) → error record + continue.
- Missing input file → step failure (allocation-error equivalent).

## 8. Hard-stop boundary

Ends after all records processed + stats; nothing downstream.

## 9. Demoted mechanics

- KSDS/PS open/write → reader/writer + repositories.
- EVALUATE dispatch → processor switch.
- Validation stub → fixed log lines.

## 10. Traceability

Stream FRs S18-FR-05..10, 12; boundaries S18-B1/B3/B4/B5/B6/B7; plan wave 3.
