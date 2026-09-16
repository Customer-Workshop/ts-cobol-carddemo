# PAUDBLOD — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: PAUDBLOD — `app/app-authorization-ims-db2-mq/cbl/PAUDBLOD.CBL` (369 lines). Stream S-20, wave 2.
- Role: batch load (data-transfer seam) — initial/refresh load of the pending-auth DB: reads a root file then a child file, inserting PAUTSUM0 then PAUTDTL1 rows under the keyed parent.

## 2. Trigger / caller contract
- Job LOADPADB: `EXEC PGM=DFSRRC00 PARM='BMP,PAUDBLOD,PSBPAUTB'` (`jcl/LOADPADB.JCL`); PSB PSBPAUTB w/ PAUTBPCB load procopt.
- FILE-CONTROL: INFILE1 = `PAUTDB.ROOT.FILEO` X(100) root records; INFILE2 = `PAUTDB.CHILD.FILEO` = `ROOT-SEG-KEY` S9(11) COMP-3 + `CHILD-SEG-REC` X(200).

## 3. Inputs and outputs
In: two sequential flat files (root records then key|child records). Out: IMS inserts; DISPLAY counters; RC16 on abend.

## 4. Functional requirements owned (all cross-ref stream FR)
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| PAUDBLOD-01 | each root rec | ISRT PAUTSUM0 unqualified; 'II' dup tolerated; other → RC16 | 2000-READ-ROOT / 2100 ISRT | FR-S20-11 |
| PAUDBLOD-02 | each child rec | GU PAUTSUM0 WHERE ACCNTID=key then ISRT PAUTDTL1; 'II' tolerated | 3000/3100 paras | FR-S20-11 |
| PAUDBLOD-03 | EOJ | counters displayed, RC0 | counters/DISPLAY paras | FR-S20-11 |
| PAUDBLOD-04 | fatal DL/I status | abend RC16 | 9999-ABEND | FR-S20-11 |

## 5. Business rules and validations
Parent must exist before its children (GU before ISRT); duplicates are non-fatal ('II' skipped, counted). `PRM-INFO` block present but unused.

## 6. Data access and boundaries
IMS ISRT/GU (S20-B8 — JPA inserts w/ dup-ignore; files → Spring Resource CSV inputs).

## 7. Error and edge behavior
'II' → tolerate; '  ' → ok; anything else → RC16 abend. Missing parent on GU → fatal.

## 8. Hard-stop boundary
Utility only; no decision logic, no reads other than the parent GU.

## 9. Demoted mechanics
BMP envelope, PCB mask `PAUTBPCB`, file-status checks → step plumbing.

## 10. Traceability
PAUDBLOD-01..04 ↔ FR-S20-11 ↔ `PendingAuthImportJobIT`.
