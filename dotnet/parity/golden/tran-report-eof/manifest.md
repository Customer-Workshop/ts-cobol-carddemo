# Golden: tran-report-eof (CBTRN03C, all transactions in-window)

Same report with every transaction inside the date window, so the
report reaches EOF and exercises the final page-total + grand-total
path that `tran-report` (by the SEM-33 quirk) never reaches.

- **Inputs:** WRSEED `TRANFIL2.dat` (132 in-window txns), same DATEPARM
- **Files:** `TRANREPT.rpt` — 133-byte fixed records, line-exact

- **Oracle level:** recompile-to-run (GnuCOBOL 3.1.2, `-std=ibm -fsign=ebcdic`, estate sources unmodified, VSAM KSDS mapped to GnuCOBOL indexed/BDB files)
- **Frozen clock:** `faketime -f "2025-08-01 09:00:00"` (programs call `FUNCTION CURRENT-DATE`)
- **Seeding:** generated COBOL writer `tools/WRSEED.cbl` (12 accounts, varied non-zero balances incl. negatives; accounts 9-12 have no tran-cat rows; zero-rate group and DEFAULT-fallback group both represented; 132 transactions across 4 cards)
- **Capture method:** `scripts/run-scenarios.sh`; keyed-order unloads via `tools/RDUNLOAD.cbl` (parity surface is the unload, never the physical ISAM file)
- **Falsifiability:** verified — changing PARM date/clock or seed values changes the outputs byte-wise
- **Known residual dialect risks (recompile-to-run level):** IBM intermediate-precision (`ARITH`), `NUMPROC/TRUNC` semantics, true-EBCDIC collation of keys, and physical VSAM behavior are approximated by GnuCOBOL; see `docs/migration/adr/ADR-0002-oracle-level.md`
- Goldens are immutable. A failing parity run means the .NET port (or a new estate bug to preserve) — never regenerate these files to pass.
