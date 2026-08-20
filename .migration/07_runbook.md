# 07_runbook — how to build/run locally (every command executed in the setup session)

## Compile a COBOL program (verified 2026-08-20, GnuCOBOL 3.1.2)
```bash
cd ~/repos/ts-cobol-carddemo
mkdir -p /tmp/cobtest
cobc -I app/cpy -fsign=EBCDIC -x -o /tmp/cobtest/CBACT01C app/cbl/CBACT01C.cbl   # -> COMPILE_OK
```
If a copybook fails on tab indentation, expand tabs first:
```bash
mkdir -p /tmp/cpyfix && for f in app/cpy/*; do expand -t 4 "$f" > /tmp/cpyfix/$(basename "$f"); done
cobc -I /tmp/cpyfix -fsign=EBCDIC -x -o /tmp/cobtest/CBACT01C app/cbl/CBACT01C.cbl   # verified OK
```
Note `-fsign=EBCDIC`: ASCII datasets in `app/data/ASCII` use zoned-decimal overpunch signs.

## Toolchain facts (verified this session)
- `cobc` = GnuCOBOL 3.1.2 at `/usr/bin/cobc`
- `java -version` = OpenJDK 21.0.11 (default on PATH)
- CICS/DB2/IMS/MQ are NOT runnable locally; online programs compile-check only.

## Backend/DB/CI runbook
TBD — added by Phase 0 scaffolding after STOP A/STOP C decide the stack. Do not guess.
