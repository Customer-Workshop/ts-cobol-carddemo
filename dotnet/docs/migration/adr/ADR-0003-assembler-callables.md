# ADR-0003: Assembler callables (COBDATFT, MVSWAIT, CEE3ABD)

## Status
Accepted.

## Decision
The recompile-to-run oracle cannot execute z/OS assembler.
- `CEE3ABD` (LE abend): stubbed (`dotnet/parity/stubs/CEE3ABD.cbl`, RC=99 +
  STOP RUN). Abend paths are exercised only as failure diagnostics.
- `COBDATFT` (date reformat, called by CBACT01C): **deferred** — the CBACT01C
  wave must either port it from `app/asm/COBDATFT.asm` semantics with
  micro-parity tests, or scope its golden to avoid the DATFT path; decision
  recorded in that wave's PR.
- `MVSWAIT` (STIMER wait, called by COBSWAIT): **out of scope** — no data
  output, scheduler utility.
