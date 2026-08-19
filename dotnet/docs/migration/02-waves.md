# Wave Plan

One wave = one module = one PR into `dotnet-migration`. Waves only ADD files;
wave 0 owns all shared code. Status values: pending / in-progress (+ session) /
PR open (+ link) / merged.

| Wave | Module(s) | Programs | DAG prerequisites | Status |
| --- | --- | --- | --- | --- |
| 0 | Shared kernel + parity foundation | decoders, primitives, harness | — | PR open (this PR) |
| 1 | Interest calculation | CBACT04C | wave 0 | pending |
| 2 | Transaction detail report | CBTRN03C | wave 0 | pending |
| 3 | Transaction posting | CBTRN02C | wave 0 | pending |
| 4 | Daily transaction verify | CBTRN01C | wave 0 | pending |
| 5 | Sequential file readers | CBACT02C, CBACT03C, CBCUS01C | wave 0 | pending |
| 6 | Account reader | CBACT01C | wave 0 + COBDATFT decision (ADR-0003) | pending |
| 7 | Statements | CBSTM03A + CBSTM03B | wave 0 | pending |
| 8 | Export/Import | CBEXPORT, CBIMPORT | wave 0 | pending |
| 9 | Date utility | CSUTLDTC | wave 0 | pending |
| 10+ | CICS online modules (COMMAREA-level parity) | COSGN00C → menus → business screens | wave 0 + waves 1–5 data services | pending |

First module proposed and accepted: **CBACT04C (wave 1)** — leaf batch module,
numeric aggregation + file updates, compiles under GnuCOBOL. Wave 2 (CBTRN03C)
is also DAG-ready (goldens already recorded by the foundation).
