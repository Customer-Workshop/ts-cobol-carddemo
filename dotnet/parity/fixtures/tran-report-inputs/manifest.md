# Fixture: tran-report-inputs (wave 2, CBTRN03C)

Committed copies of the exact oracle inputs behind the `tran-report` and
`tran-report-eof` goldens, so the .NET parity tests replay oracle-produced
bytes instead of re-deriving fixtures in C#.

- **Capture method**: `scripts/capture-tran-report-inputs.sh --capture` —
  same WRSEED seeding used for the goldens (GnuCOBOL 3.1.2,
  `-fsign=ebcdic`); lookup KSDS files exported with RDUNLD2 as
  keyed-order LINE SEQUENTIAL unloads (the coexistence seam, never raw
  ISAM bytes).
- **Contents**:
  - `TRANFILE.dat` / `TRANFIL2.dat` — 132 × 350-byte record-sequential
    transactions (TRANFILE has trailing out-of-window records for
    SEM-B02; TRANFIL2 is all in-window).
  - `DATEPARM.txt` — one 80-byte record: window `2025-07-01` to `2025-08-31`.
  - `CARDXREF.unload` — 12 × 50-byte card xref records (CVACT03Y).
  - `TRANTYPE.unload` — 2 × 60-byte tran type records (CVTRA03Y).
  - `TRANCATG.unload` — 3 × 60-byte tran category records (CVTRA04Y).
- **Integrity**: `SHA256SUMS` covers exactly the committed data files;
  `capture-tran-report-inputs.sh` (default verify mode) proves a fresh
  seed+unload still reproduces them (drift/falsifiability check).
- **Immutability**: like the goldens, these fixtures are immutable once
  captured. If seeding ever needs to change, that is a new scenario with
  new goldens — never an edit here.
