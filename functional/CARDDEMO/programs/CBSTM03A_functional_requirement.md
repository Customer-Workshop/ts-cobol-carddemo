# CBSTM03A — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: CBSTM03A — `app/cbl/CBSTM03A.CBL` (~930 lines). Stream S-16, wave 1.
- Role: statement writer — preloads all extract transactions, then emits one
  text + one HTML statement per card-xref row. Demonstrates mainframe
  control-block addressing (PSA→TCB→TIOT walk), ALTER/GO TO dispatch, and
  delegated file I/O through subroutine CBSTM03B.

## 2. Trigger / caller contract
- Executed as STEP040 of job CREASTMT (`app/jcl/CREASTMT.JCL`) —
  `EXEC PGM=CBSTM03A,PARM='12'`; **PARM is never read** (no USING clause —
  vestigial). Chain context: Control-M/CA-7 statement chain.
- Calls: `CBSTM03B` for every file operation (×15 sites, :351-909);
  `CEE3ABD` on error (:922).
- RC 0; abend on any M03B return ≠ '00'/'04'.

## 3. Inputs and outputs
Inputs (all via CBSTM03B): TRXFL KSDS seq (key card X(16)+id X(16), 350B —
  built by CREASTMT STEP010/020); XREFFILE KSDS seq (statement driver);
  CUSTFILE/ACCTFILE KSDS random keyed.
Outputs: STMT-FILE (STATEMNT.PS, 80B); HTML-FILE (STATEMNT.HTML, 100B);
  SYSOUT DISPLAY (TIOT/DD dump + errors).

## 4. Functional requirements owned

| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| CBSTM03A-01 | startup | TIOT walk DISPLAYs job/step + allocated DD names; STMT/HTML files opened output | :265-288, :290-292 | FR-S16-12 diag |
| CBSTM03A-02 | first-phase | TRXFL open + read-all into `WS-TRNX-TABLE` (51 cards × 10 txns) via ALTER dispatch | :296-315, :725-756, :818-854 | FR-S16-01/11 |
| CBSTM03A-03 | per XREFFILE rec | keyed CUSTFILE + ACCTFILE gets via CBSTM03B | :319-322 | FR-S16-03 |
| CBSTM03A-04 | per card | header: ST-LINE0 star frame + name/addr lines (STRING-built) | :458-495 | FR-S16-04/05 |
| CBSTM03A-05 | per txn | ST-LINE14 detail + accumulate total | :416-432, :675-720 | FR-S16-07 |
| CBSTM03A-06 | per card | 'Total EXP:' + 'END OF STATEMENT'; HTML closing block | :434-456 | FR-S16-08/09 |
| CBSTM03A-07 | any M03B RC≠00/04 | 'ERROR ...' DISPLAY + CEE3ABD abend | :733-920 | FR-S16-12 |
| CBSTM03A-08 | EOF xref | close all files; RC 0 | :331-337 | — |
| CBSTM03A-09 | PARM present | no effect (never read) | :261 PROC ENTRY | FR-S16-15 |

## 5. Business rules and validations

Statement = per card-xref row (not per account): customer+account resolved per
card. Zero-transaction cards still emit a statement (empty summary, total 0).
Transactions appear in TRXFL order (sorted card+id upstream). **Hard caps**:
51 cards / 10 txns per card — overrun is unchecked memory corruption; target
must NOT preserve (documented defect removal).

## 6. Data access and boundaries

- S16-B1: all reads through CBSTM03B handler → repositories in target.
- S16-B2: TRXFL extract → sorted `transactions` read; prep steps eliminated.
- S16-B3: STATEMNT.PS + STATEMNT.HTML → flat files per run dir.
- S16-B8: CEE3ABD → exception → step FAILED.
- S16-B9: CBSTM03B call seam — demoted to repositories, not ported.

## 7. Error and edge behavior

- M03B RC '04' tolerated on every call; anything else abends.
- Missing xref cust/acct rows: READ-K miss → RC≠00 → abend (strict, same as
  S-15 pattern).
- Cards missing from the preload table (>51) or >10 txns — silent corruption;
  target streams.

## 8. Hard-stop boundary

Both output files closed at end of xref sweep. TXT2PDF1 is a separate chain
step.

## 9. Demoted mechanics

TIOT/PSA/TCB control-block walk; ALTER-TO dispatch; M03B linkage plumbing;
88-level HTML line selection; array counters (all replaced by clean Java
control flow — none of this survives as logic).

## 10. Traceability

CBSTM03A-01..09 → FR-S16-01..13,15 → `Cbstm03JobConfiguration` +
`BatchJobService.statement*` + `DualStatementWriter` → golden-file tests.
