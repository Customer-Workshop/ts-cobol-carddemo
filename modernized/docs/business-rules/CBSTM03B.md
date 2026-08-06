# CBSTM03B business-rule specification

## Purpose and trigger

`CBSTM03B` is a COBOL linkage subroutine called by `CBSTM03A`; it centralizes sequential/random file operations for statement generation.

## Inputs and outputs

The caller passes `LK-M03B-AREA`:

| Field | PIC / representation | Length |
|---|---|---:|
| LK-M03B-DD | `PIC X(08)` | 8 |
| LK-M03B-OPER | `PIC X(01)`; `O` open, `C` close, `R` read, `K` keyed read, `W` write, `Z` rewrite declared | 1 |
| LK-M03B-RC | `PIC X(02)` file status returned | 2 |
| LK-M03B-KEY | `PIC X(25)` | 25 |
| LK-M03B-KEY-LN | `PIC S9(4)` display signed | 4 |
| LK-M03B-FLDT | `PIC X(1000)` data buffer | 1000 |

Local file record layouts are exact FD slices: `TRNXFILE` key `FD-TRNX-CARD PIC X(16)` + `FD-TRNX-ID PIC X(16)` + data `PIC X(318)`; `XREFFILE` card `PIC X(16)` + data `PIC X(34)`; `CUSTFILE` ID `PIC X(09)` + data `PIC X(491)`; `ACCTFILE` ID `PIC 9(11)` + data `PIC X(289)`.

## Validation and error rules (dispatch order)

1. Evaluate DD name. `TRNXFILE`, `XREFFILE`, `CUSTFILE`, and `ACCTFILE` dispatch to separate paragraphs; any other DD falls through to `GOBACK` without an operation.
2. In each dispatch paragraph, `O` executes `OPEN INPUT`, `R` executes `READ` into `LK-M03B-FLDT`, and `C` executes `CLOSE`; the two-byte VSAM status is moved to `LK-M03B-RC`.
3. The linkage declares `K`, `W`, and `Z`, but the visible dispatch paragraphs implement only open/read/close. Do not implement keyed/write/rewrite behavior without resolving this source gap.

## Calculations

No business arithmetic occurs. The subroutine copies native two-byte file status, including EOF (`10`) and missing-key statuses, back to the caller.

## Control flow and failure handling

This is batch linkage, not CICS: no PF keys, COMMAREA, or screen. It does not abend itself; `CBSTM03A` decides whether a returned status is fatal.

## Test cases

| # | Concrete input | Expected output |
|---:|---|---|
| 1 | DD `TRNXFILE`, operation `O`, then operation `R` with a 350-byte transaction record | Opens input; `LK-M03B-RC` is `00`; first 350 bytes are copied to `LK-M03B-FLDT`. |
| 2 | DD `XREFFILE`, operation `R`, key buffer containing card `0500024453765740` | Reads the xref record and returns its two-byte status; caller receives the 50-byte record in the buffer. |
| 3 | DD `CUSTFILE`, operation `C` after no open | Returns the native close status; this routine does not convert it into a message. |
| 4 | DD `UNKNOWN`, operation `O` | Immediate `GOBACK`; caller's prior return-code bytes are not guaranteed to change. |
| 5 | DD `ACCTFILE`, operation `R` at EOF | Returns status `10`; caller interprets it as end-of-file. |
