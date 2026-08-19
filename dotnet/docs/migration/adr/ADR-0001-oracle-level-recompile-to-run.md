# ADR-0001: Recompile-to-run oracle level and residual dialect risks

## Status
Accepted (user decision: estate cannot run as z/OS locally).

## Decision
Batch parity is verified by recompiling mainframe sources unchanged under
GnuCOBOL 3.1.2 (`-std=ibm -fsign=ebcdic`), running them against seeded ISAM/
sequential files with a frozen clock (`faketime 2025-08-01 09:00:00`) and PARM
date `2025-07-31` injected by a driver that builds the halfword-length-prefixed
LINKAGE area (GnuCOBOL has no JCL PARM). VSAM KSDS → GnuCOBOL ISAM; JCL DD
names → environment variables (`DD_<name>`). CICS modules will be verified at
data + COMMAREA level only; screen parity is never claimed.

## Residual risks at this level
- Intermediate arithmetic precision: GnuCOBOL intermediates may differ from IBM
  ARITH(COMPAT) 15-digit truncation on chained COMPUTEs.
- NUMPROC/TRUNC compiler-option behaviors are not reproduced.
- Key collation is ASCII, not true EBCDIC (digit/letter order inverts); modeled
  keys are digits-only, keeping orderings identical, but future alphanumeric
  keys must be re-checked.
- Physical VSAM behaviors (CI splits, AIX upgrade sets) are not modeled;
  parity gates only on keyed-order unloads and report files, never physical files.

Each golden manifest re-states the risks that apply to that scenario.
