# Golden: interest-calc (CBACT04C, INTCALC.jcl equivalent)

Monthly interest calculation over tran-cat balances: rate lookup in
DISCGRP (incl. status-23 DEFAULT-group fallback and a zero-rate group),
`(bal * rate) / 1200` per category, account REWRITE with cycle-credit/
debit reset, and one output transaction per interest posting.

- **Inputs:** WRSEED fixtures; `PARM_DATE=2025-07-31` via DRVACT04
  (halfword-length-prefixed JCL PARM shim)
- **Files:**
  - `ACCTFILE.post.unload` — keyed order, byte-exact, INCLUDING
    untouched accounts 9-12 (missed-REWRITE detector)
  - `TCATBAL.post.unload` — CBACT04C opens TCATBALF INPUT-only, so this
    equals the seed unload by design (regression trap: any port that
    "helpfully" resets category balances fails here)
  - `TRANSACT.dat` — 350-byte fixed records; TRAN-ID = PARM date +
    suffix; timestamps from the frozen clock

- **Oracle level:** recompile-to-run (GnuCOBOL 3.1.2, `-std=ibm -fsign=ebcdic`, estate sources unmodified, VSAM KSDS mapped to GnuCOBOL indexed/BDB files)
- **Frozen clock:** `faketime -f "2025-08-01 09:00:00"` (programs call `FUNCTION CURRENT-DATE`)
- **Seeding:** generated COBOL writer `tools/WRSEED.cbl` (12 accounts, varied non-zero balances incl. negatives; accounts 9-12 have no tran-cat rows; zero-rate group and DEFAULT-fallback group both represented; 132 transactions across 4 cards)
- **Capture method:** `scripts/run-scenarios.sh`; keyed-order unloads via `tools/RDUNLOAD.cbl` (parity surface is the unload, never the physical ISAM file)
- **Falsifiability:** verified — changing PARM date/clock or seed values changes the outputs byte-wise
- **Known residual dialect risks (recompile-to-run level):** IBM intermediate-precision (`ARITH`), `NUMPROC/TRUNC` semantics, true-EBCDIC collation of keys, and physical VSAM behavior are approximated by GnuCOBOL; see `docs/migration/adr/ADR-0002-oracle-level.md`
- Goldens are immutable. A failing parity run means the .NET port (or a new estate bug to preserve) — never regenerate these files to pass.
