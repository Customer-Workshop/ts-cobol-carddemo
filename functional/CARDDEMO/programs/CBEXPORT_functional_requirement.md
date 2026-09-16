# CBEXPORT — Branch Data Export Program — Functional Requirements

## 1. Identity and role

- **Program**: `CBEXPORT` (`app/cbl/CBEXPORT.cbl`, ~582 lines) — step program of CBEXPORT.jcl STEP02 (`app/jcl/CBEXPORT.jcl:43`); STEP01 (`:24-38`) is IDCAMS delete/define of the export cluster.
- **Stream**: S-18, wave 2. **Shared ownership**: producer of the CVEXPORT contract shared with CBIMPORT.
- **Baseline**: `CbexportJobConfiguration` (`cbexportJob`) + `BatchJobService.exportRecord` — exists; this stream refines parity.
- **Role**: read all five master KSDS files sequentially and write one multi-type export file.

## 2. Trigger / caller contract

`EXEC PGM=CBEXPORT`; no linkage, no PARM; manual submission (`NOTIFY=&SYSUID`). Target: `POST /api/admin/jobs/cbexportJob`.

## 3. Inputs and outputs

| DD | Direction | Layout | Target |
|---|---|---|---|
| CUSTFILE | in | KSDS `CVACT…`/`CVCUS01Y` 500B | `customers` repository |
| ACCTFILE | in | KSDS `CVACT01Y` 300B | `accounts` repository |
| XREFFILE | in | KSDS `CVACT03Y` 50B | `card_xrefs` repository |
| TRANSACT | in | KSDS `CVTRA05Y` 350B | `transactions` repository |
| CARDFILE | in | KSDS `CVACT02Y` 150B | `cards` repository |
| EXPFILE | out | `EXPORT.DATA` KSDS 500B (`CVEXPORT.cpy`) | `<output-dir>/EXPORT.DATA` flat file |
| SYSOUT | out | finalize stats | job log |

## 4. FR table (KEEP)

| FR | Rule | Cite |
|---|---|---|
| 01 | Open 5 inputs + EXPFILE; any open/write status error → abend | open paras; :579 |
| 02 | Write sections in order C → A → X → T → D, each file read fully sequentially | section drivers 2200/3200/4200/5200/5700 |
| 03 | Header per record: rec-type X(1), timestamp X(26) `YYYY-MM-DD HH:MM:SS.00` from ACCEPT DATE/TIME, seq counter, '0001', 'NORTH' | :172-195 |
| 04 | Single sequence counter across all records/types | seq field increment |
| 05 | Per-type payload = entity fields in CVEXPORT redefined order | CVEXPORT.cpy redefines |
| 06 | On finish: DISPLAY per-type counts + grand total | :563-573 |
| 07 | Any file-status failure → `CALL 'CEE3ABD'` | :579 |
| 08 | Recreate output each run (STEP01 delete/define in JCL → delete-if-exists) | CBEXPORT.jcl:27-38 |

## 5. Business rules

- `BRANCH-ID` and `REGION-CODE` are constants `'0001'`/`'NORTH'` — config in target.
- Export contains **all** rows of all five files — no filtering, no joins.

## 6. Data access / boundaries

- 5 repository readers (S18-B2); export flat file (S18-B1); record format (S18-B6 — target text format, deviation vs legacy binary); launch seam (S18-B5); abend→exit code (S18-B4).

## 7. Error / edge behavior

- Empty table → zero records of that type; sections still contiguous.
- Mid-run failure → whole file regenerated on rerun (no partial-append semantics).

## 8. Hard-stop boundary

Ends after D-section + stats; the dataset is the downstream contract to CBIMPORT.

## 9. Demoted mechanics

- IDCAMS delete/define → writer truncate semantics.
- COMP/COMP-3 binary packing → text field encoding (S18-B6).
- KSDS keyed write → sequential flat-file append; key metadata dropped (S18-B1).

## 10. Traceability

Stream FRs S18-FR-01..04, 09, 11; boundaries S18-B1/B2/B4/B5/B6; plan wave 2.
