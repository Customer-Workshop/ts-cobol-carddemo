# Estate Profile — AWS CardDemo (mainframe fork)

Source: `main` branch of Cognition-Partner-Workshops/aws-mainframe-modernization-carddemo.
Migration lives on the long-lived `dotnet-migration` branch under `dotnet/`; COBOL is never modified.

## Five axes

| Axis | Finding |
| --- | --- |
| Dialect / compiler | IBM Enterprise COBOL source (z/OS conventions: `PIC S9 COMP-3`, `CEE3ABD` LE abend service, JCL PARM linkage). No GnuCOBOL config committed. Batch sources compile under GnuCOBOL 3.1.2 with `-std=ibm -fsign=ebcdic`. |
| Runtime model | Mixed: 14 batch programs (`CB*`, `CSUTLDTC` callable) driven by JCL, and 17 CICS/BMS pseudo-conversational online programs (`CO*`) with COMMAREA state and BMS maps under `app/bms/`. |
| Data stores | VSAM KSDS (accounts, cards, customers, xrefs, tcatbal, discgrp, lookups) + flat sequential files (transaction journal, reports) + GDG usage in JCL. No DB2/SQL anywhere (`EXEC SQL` absent), so there is no DAL to read — copybooks are authoritative. |
| Character encoding | Shipped data exists in both `app/data/ASCII` and `app/data/EBCDIC`. ASCII exports retain EBCDIC sign overpunch on zoned signed fields (`{`, `}`, A–R), so GnuCOBOL needs `-fsign=ebcdic`. |
| Orchestration | JCL under `app/jcl/` (e.g. `INTCALC.jcl` → CBACT04C, `TRANREPT.jcl` → CBTRN03C, `POSTTRAN.jcl` → CBTRN02C). Oracle harness translates each modeled job to a shell script with DD-name environment mappings. |

## Coexistence seam

No RDBMS exists in the estate, so the seam is the **legacy data files**: keyed-order
sequential unloads of the VSAM datasets plus the flat batch outputs (transaction
journal, 133-byte report lines). The oracle gates on those surfaces — never on
physical ISAM/VSAM file bytes. The eventual RDBMS is the *target* seam and will be
introduced by later waves behind `CardDemo.Persistence`.

## Oracle level

Estate cannot run as z/OS locally → **recompile-to-run** (GnuCOBOL `-std=ibm`) for
batch, **data + COMMAREA level only** for CICS modules (screen parity is never
claimed). See `dotnet/parity/` and ADR-0001 for residual dialect risks.

## Non-COBOL callables

| Module | Called by | Decision |
| --- | --- | --- |
| `COBDATFT.asm` (date format) | CBACT01C | **Defer**: CBACT01C golden scenarios must avoid the DATFT path or stub it; port decision belongs to the CBACT01C wave (ADR-0003). |
| `MVSWAIT.asm` (STIMER wait) | COBSWAIT | **Defer/stub**: utility wait program, no data output; out of scope for parity. |
| `CEE3ABD` (LE abend) | all batch | **Stubbed** in harness (`dotnet/parity/stubs/CEE3ABD.cbl`) — sets RC=99 and stops. |
