# CardDemo — Reverse-Engineering Discovery Baseline

Automated **application-inventory baseline** for the CardDemo mainframe application,
generated directly from source (COBOL, Copybooks, JCL/PROC, BMS, CICS CSD, DB2
DDL/DCLGEN, IMS DBD/PSB, VSAM IDCAMS). These are the discovery-phase artifacts
required before Business Rule Extraction (BRE) and modernization.

## Artifacts

| # | Report | File |
| --- | --- | --- |
| — | Interactive dashboard (all reports) | [`index.html`](./index.html) |
| 1 | Inventory Report with Lines of Code | [`reports/01_inventory_loc.md`](./reports/01_inventory_loc.md) |
| 2 | Program-to-Program Dependencies / Call Chain | [`reports/02_program_to_program.md`](./reports/02_program_to_program.md) |
| 3 | Program-to-File / Database Dependencies (CRUD) | [`reports/03_program_to_file_db_crud.md`](./reports/03_program_to_file_db_crud.md) |
| 4 | File-to-File Mapping (PF to LF) | [`reports/04_pf_to_lf_mapping.md`](./reports/04_pf_to_lf_mapping.md) |
| 5 | File (PF) to Field Mapping | [`reports/05_pf_to_field_mapping.md`](./reports/05_pf_to_field_mapping.md) |
| 6 | Missing Source / Obsolete Report | [`reports/06_missing_obsolete.md`](./reports/06_missing_obsolete.md) |
| 7 | Grouping & Sequencing | [`reports/07_grouping_sequencing.md`](./reports/07_grouping_sequencing.md) |
| BRE | Business Rule Extraction example (CBTRN02C) | [`BRE-CBTRN02C.md`](./BRE-CBTRN02C.md) |

## Regenerate

```bash
python3 discovery-reports/analyze.py   # parse source  -> data.json
python3 discovery-reports/render.py     # data.json     -> reports/*.md + index.html
```

`analyze.py` uses the Python standard library only. It is fixed-format aware
(COBOL indicator column, JCL comment/continuation), resolves CICS navigation via
the menu copybooks and the CSD, and resolves indirect batch invocations (IMS
`DFSRRC00`, DB2 `IKJEFT01 RUN PROGRAM`).

## How each artifact is derived

- **Inventory / LOC** — every member classified by type; fixed-format aware line counts.
- **Call chain** — COBOL `CALL`, CICS `XCTL/LINK`, menu copybooks, JCL `EXEC PGM` + indirect.
- **CRUD** — COBOL file verbs + `OPEN` mode, CICS file control, embedded DB2 SQL.
- **PF → LF** — VSAM base cluster (KSDS) → alternate index (AIX)/PATH from IDCAMS `DEFINE`.
- **PF → Field** — copybook record layouts: PICTURE, USAGE, byte length and offset.
- **Missing / Obsolete** — referenced-but-absent members; source present but never invoked.
- **Grouping & Sequencing** — functional domains + batch execution run book.
