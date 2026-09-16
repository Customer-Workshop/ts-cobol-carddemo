# COBSWAIT — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COBSWAIT — `app/cbl/COBSWAIT.cbl` (41 lines). Stream S-14, wave 1;
  **shared — S-14 owns the port, S-15 (and the weekly chains) inherit it**
  (`CardDemo_inventory.md:142`).
- Role: scheduler wait-step driver — reads a centisecond count from SYSIN and
  sleeps that long via the MVSWAIT assembler routine, giving CICS file
  close/open (CLOSEFIL/OPENFIL) wall-clock settle time between batch steps.

## 2. Trigger / caller contract
- Executed as `//WAIT EXEC PGM=COBSWAIT` of job WAITSTEP
  (`app/jcl/WAITSTEP.jcl`), present in every scheduler chain between the last
  write step and OPENFIL (Control-M controlm:14-19, :81-85; CA-7 SCHID 030/031/
  032 WAITSTEP entries).
- Parameter: SYSIN one card, `00003600` = "VALUE IN CENTISECONDS"
  (WAITSTEP.jcl:7-8) → 36.00 s.
- Exits RC 0 via STOP RUN after MVSWAIT returns.

## 3. Inputs and outputs
Inputs: SYSIN card `PARM-VALUE` 9(8) display → `MVSWAIT-TIME 9(8) COMP`.
Outputs: none (elapsed time only). `DISPLAY` traces are diagnostic-only.

## 4. Functional requirements owned

| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COBSWAIT-01 | SYSIN parm | `ACCEPT PARM-VALUE FROM SYSIN`, converted to binary centiseconds | :36-37 | FR-S14-14 |
| COBSWAIT-02 | always | `CALL 'MVSWAIT'` — blocks for the given duration | :38 | FR-S14-14 |
| COBSWAIT-03 | return | STOP RUN, RC 0 | :39-41 | FR-S14-14 |
| COBSWAIT-04 | missing/short SYSIN | ACCEPT defaults blanks→0 → near-instant return (garbage-tolerant) | :36 | FR-S14-14 edge |

## 5. Business rules and validations

None — pure delay. The business value is the *chain ordering* it creates, not
the computation.

## 6. Data access and boundaries

- S14-B4 (module B-002): `CALL 'MVSWAIT'` (assembler, centisecond count) → Java
  `WaitStepTasklet` executing `Thread.sleep(waitCentiseconds × 10)`; parameter
  `waitCentiseconds` on the job/schedule, default 3600.
- No file/DB access; no shared state.

## 7. Error and edge behavior

- No error paths — MVSWAIT has no feedback channel; interruption on target =
  step failure (InterruptedException → step FAILED).
- Duration must survive interruption cleanly; zero/blank parm = immediate
  success (matches ACCEPT default).

## 8. Hard-stop boundary

STOP RUN. No downstream edges inside the program.

## 9. Demoted mechanics

SYSIN ACCEPT plumbing; binary conversion move; DISPLAY lines (kept as log
trace in target at debug level).

## 10. Traceability

COBSWAIT-01..04 → FR-S14-14 → `WaitStepTasklet` (new) → unit test asserting
elapsed ≥ duration and RC-equivalent success; integration launch via
`POST /api/admin/jobs` chain order.
