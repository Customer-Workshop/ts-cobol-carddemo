# COBTUPDT — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COBTUPDT — `app/app-transaction-type-db2/cbl/COBTUPDT.cbl` (237 lines). Stream S-21, wave 2.
- Role: batch write — applies a fixed-layout maintenance file (add/update/delete) to CARDDEMO.TRANSACTION_TYPE under the DB2 batch runtime (IKJEFT01/DSN).

## 2. Trigger / caller contract
- Job MNTTRDB2 (`jcl/MNTTRDB2.jcl`): `IKJEFT01 DSN SYSTEM(DAZ1) RUN PROGRAM(COBTUPDT) PLAN(CARDDEMO)`; Control-M WEEKLY (`app/scheduler/CardDemo.controlm:27-62`).
- Input DD INPFILE; output SYSOUT messages; RC4 on abend (`9999-ABEND` :230-233).

## 3. Inputs and outputs
In: INPFILE fixed 53-byte records — `INPUT-TYPE` X(1) + `INPUT-TR-NUMBER` X(2) + `INPUT-TR-DESC` X(50) (`:39-46,:71-77`). Out: DB2 writes; console/status text; RC0/RC4.

## 4. Functional requirements owned (all cross-ref stream FR)
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COBTUPDT-01 | 'A' record | INSERT TRANSACTION_TYPE | :109-129 | FR-S21-13 |
| COBTUPDT-02 | 'U' record | UPDATE; +100 → 'No records found.' abend | :109-129 | FR-S21-13 |
| COBTUPDT-03 | 'D' record | DELETE (FK RESTRICT surfaces -532 → abend) | :109-129 | FR-S21-13 |
| COBTUPDT-04 | '*' record | skipped as comment | :109-129 | FR-S21-13 |
| COBTUPDT-05 | other type | 'ERROR: TYPE NOT VALID' + abend RC4 | :129,:230-233 | FR-S21-13 |
| COBTUPDT-06 | EOF | clean EOJ RC0 | file-status handling | FR-S21-13 |

## 5. Business rules and validations
Action code is single-char positional; records applied in file order; each write is its own unit (no multi-record atomicity).

## 6. Data access and boundaries
- TRANSACTION_TYPE writes (S21-B5 — Spring Batch `tranTypeMaintJob`; INPFILE → Spring Resource file param; per-record item processing; RC4 → Job FAILED w/ exit code 4).
- Child FK guard shared (S21-B4).

## 7. Error and edge behavior
Update-of-missing-key and invalid type codes are hard failures (RC4), not skips — preserves ops-visible failure semantics.

## 8. Hard-stop boundary
Applies the file only; no extract (TRANEXTR) or online paths here.

## 9. Demoted mechanics
IKJEFT01/DSN → `BatchJobService` launch; FD/file-status → FlatFileItemReader; DISPLAY → job log; ABEND RC4 → FAILED.

## 10. Traceability
COBTUPDT-01..06 ↔ FR-S21-13 ↔ `TranTypeMaintJobIT`.
