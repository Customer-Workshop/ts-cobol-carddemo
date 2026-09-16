# CBACT02C — Card File Read/Verify Program — Functional Requirements

## 1. Identity and role

- **Program**: `CBACT02C` (`app/cbl/CBACT02C.cbl`, ~178 lines) — step program of READCARD (`app/jcl/READCARD.jcl:22`, STEP05).
- **Stream**: S-17, wave 3. **Shared ownership**: none.
- **Role**: sequential KSDS verify pass over CARDFILE; DISPLAY each `CARD-RECORD` to SYSOUT once.

## 2. Trigger / caller contract

`EXEC PGM=CBACT02C`; no linkage, no PARM. Target: `readcardJob` via `POST /api/admin/jobs/readcardJob`. No calls besides CEE3ABD on the error path.

## 3. Inputs and outputs

| DD | Direction | Layout | Target |
|---|---|---|---|
| CARDFILE | in | KSDS, `CVACT02Y` 150B | `cards` table, `RepositoryItemReader` by `cardNumber` |
| SYSOUT/SYSPRINT | out | DISPLAY dump | job log |

## 4. FR table (KEEP)

| FR | Rule | Cite |
|---|---|---|
| 01 | OPEN INPUT CARDFILE; status ≠ '00' → abend | open para |
| 02 | Read sequentially until '10' | GET-NEXT loop |
| 03 | DISPLAY `CARD-RECORD` once per record in the main loop (the in-read DISPLAY is commented out at CBACT02C.cbl:96 — single dump, do NOT duplicate) | :78, :96 |
| 04 | Close file on '10', GOBACK | close para |
| 05 | Any other status → display status, `CALL 'CEE3ABD' USING ABCODE(999) TIMING(0)` | :158 |

## 5. Business rules

None — pure verify/dump. Fields not transformed.

## 6. Data access / boundaries

`cards` repository read (S17-B2); log seam (S17-B6); exit code (S17-B4).

## 7. Error / edge behavior

Empty file → zero dump lines, normal end. Unparseable row data never occurs (dump is of the record image, not typed fields).

## 8. Hard-stop boundary

End of STEP05; chain continues to READCUST via CA-7 trigger (ca7:394) — job-order config in target.

## 9. Demoted mechanics

KSDS open/read/status → reader lifecycle; DISPLAY → log; CEE3ABD → step failure.

## 10. Traceability

Stream FRs S17-FR-07, -08; boundaries S17-B2/B4/B6; plan wave 3.
