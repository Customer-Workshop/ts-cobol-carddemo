# ADR-0005: Parity tests replay committed oracle input fixtures

## Status
Accepted (wave 2).

## Context
The wave-2 parity tests must prove `TranReportJob` reproduces the two
immutable `TRANREPT.rpt` goldens byte-for-byte. The report consumes five
inputs (transactions, three keyed lookups, date parm) that the oracle
harness materializes at run time by compiling and running WRSEED under
GnuCOBOL. Options considered:

1. Run WRSEED/GnuCOBOL from inside `dotnet test` to produce inputs.
2. Re-derive the inputs in C# test code.
3. Commit the oracle-produced input bytes as immutable fixtures and
   replay them.

## Decision
Option 3. `dotnet/parity/fixtures/tran-report-inputs/` holds the exact
bytes the golden runs consumed (captured once via
`capture-tran-report-inputs.sh --capture`, checksummed, immutable).
Tests stay hermetic (no cobc dependency, so they run wherever
`dotnet test` runs), and the fixtures are oracle-produced — option 2 is
forbidden by the migration rules (C#-re-derived fixtures could encode
the same misunderstanding as the port). Drift is covered two ways: the
script's default verify mode re-seeds and compares (run in CI-adjacent
parity checks whenever the harness changes), and `verify-goldens.sh`
still re-runs the full COBOL oracle on every CI run.

## Consequences
- Lookup unloads needed a keyed-order export utility for CARDXREF /
  TRANTYPE / TRANCATG: `tools/RDUNLD2.cbl` (harness-only, additive).
- If a future wave reseeds richer data, it captures a *new* fixture set
  and new goldens; this directory never changes.
