# COBDATFT — Assembler Date Reformat Utility — Functional Requirements

## 1. Identity and role

- **Program**: `COBDATFT` (`app/asm/COBDATFT.asm`, 84 lines) — Assembler subroutine, the one real Assembler boundary in the estate (module register B-003, DECIDED: port to a Java date-format utility).
- **Stream**: S-17, wave 1. **Shared ownership**: module property once ported; the only caller today is CBACT01C (`CBACT01C.cbl:231`).
- **Role**: reformat a date string between `YYYYMMDD` and `YYYY-MM-DD` inside a fixed parameter block.

## 2. Trigger / caller contract

- Invoked via `CALL 'COBDATFT' USING CODATECN-REC`.
- Parameter block (`app/maclib/COCDATFT.mac` DSECT, mirrored by `app/cpy/CODATECN.cpy`):
  - `COINTYPE` CL1 — input type: `'1'` = `YYYYMMDD`-in, `'2'` = `YYYY-MM-DD`-in
  - `COINPDT` CL20 — input date in the first bytes
  - `COOUTYPE` CL1 — output type: `'1'` = `YYYY-MM-DD`-out, `'2'` = `YYYYMMDD`-out
  - `COOUTDT` CL20 — output date area
  - `COERMSG` CL38 — error message area
- Target: `com.carddemo.util.DateEditService` — a plain Java method taking a value object `{ inType, inDate, outType }` and returning `{ outDate, errorMessage }`, used by the S-17 verify jobs and available module-wide.

## 3. Inputs and outputs

| Param | In | Out |
|---|---|---|
| COINTYPE | '1' or '2' | — |
| COINPDT | date text | — |
| COOUTYPE | '1' or '2' | — |
| COOUTDT | — | reformatted date (first 8 or 10 bytes; rest of area untouched) |
| COERMSG | — | `INVALID INPUT` (12 chars) on any rejected combination |

## 4. FR table (KEEP)

| FR | Rule | Cite (asm) |
|---|---|---|
| 01 | `COINTYPE='1'` (in `YYYYMMDD`): if `COINPDT+4 == '-'` → error; if `COOUTYPE='2'` → error; else `COOUTDT = YYYY-MM-DD` built from `COINPDT` year(4)+month(+4,2)+day(+6,2) | VALIDIN1 |
| 02 | `COINTYPE='2'` (in `YYYY-MM-DD`): if `COOUTYPE='1'` → error; else `COOUTDT = YYYYMMDD` built from `COINPDT` year(1-4)+month(+5,2)+day(+8,2) | VALIDIN2 (dash-position check present but commented out — do NOT enforce it) |
| 03 | Any other `COINTYPE` → error | falls through to GOTOERR |
| 04 | Error = `COERMSG ← 'INVALID INPUT'`; routine still returns R15=0 | GOTOERR + EXITL (`SR R15,R15`) |
| 05 | Success and error both return normally — callers detect failure only via COERMSG | EXITL |

## 5. Business rules

- The routine only reformats when `intype == outtype` code (`'1'→'1'` adds dashes, `'2'→'2'` strips them); "convert to the other code" is an error. This inverted-looking contract is literal and must be preserved.
- Only the needed leading bytes of the 20-byte output are written; the remainder keeps prior content (spaces in practice for CBACT01C).

## 6. Data access / boundaries

- Pure function — no data access. Boundary: B-003 (assembler → Java util). Caller seam S17-B3.

## 7. Error / edge behavior

- Non-numeric input is **not** checked — digit validation does not exist; pass bytes through verbatim.
- Input shorter than expected → whatever bytes occupy the offsets are copied (padding from the 20-byte field).

## 8. Hard-stop boundary

Returns to caller; own no files, opens nothing.

## 9. Demoted mechanics

- Register/R15 conventions → normal Java return; error channel becomes the `errorMessage` result field.
- The 84-line asm is small enough to port as a single method + unit table.

## 10. Traceability

Stream FR S17-FR-12; boundaries S17-B3, module B-003; plan wave 1.
