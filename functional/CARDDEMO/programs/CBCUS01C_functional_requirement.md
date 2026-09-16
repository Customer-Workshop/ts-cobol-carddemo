# CBCUS01C — Customer File Read/Verify Program — Functional Requirements

## 1. Identity and role

- **Program**: `CBCUS01C` (`app/cbl/CBCUS01C.cbl`, ~178 lines) — step program of READCUST (`app/jcl/READCUST.jcl:21`, STEP05).
- **Stream**: S-17, wave 3. **Shared ownership**: none.
- **Role**: sequential KSDS verify pass over CUSTFILE; DISPLAY each `CUSTOMER-RECORD` **twice** (parity quirk; paragraphs use `Z-` prefixes, functionally identical to CBACT02C/03C).

## 2. Trigger / caller contract

`EXEC PGM=CBCUS01C`; no linkage, no PARM. Target: `readcustJob` via `POST /api/admin/jobs/readcustJob`. `Z-ABEND-PROGRAM` → CEE3ABD on error.

## 3. Inputs and outputs

| DD | Direction | Layout | Target |
|---|---|---|---|
| CUSTFILE | in | KSDS, `CVCUS01Y` 500B | `customers` table, `RepositoryItemReader` by `custId` |
| SYSOUT/SYSPRINT | out | DISPLAY dump | job log |

## 4. FR table (KEEP)

| FR | Rule | Cite |
|---|---|---|
| 01 | OPEN INPUT CUSTFILE; status ≠ '00' → abend | open para |
| 02 | Read sequentially until '10' | GET-NEXT loop |
| 03 | DISPLAY `CUSTOMER-RECORD` twice per record: once inside GET-NEXT (`:96`) and once in the main loop (`:78`) — keep both | :78, :96 |
| 04 | Close on '10', GOBACK | close para |
| 05 | Any other status → `Z-DISPLAY-IO-STATUS`, `Z-ABEND-PROGRAM` → `CALL 'CEE3ABD' USING ABCODE(999) TIMING(0)` | :158 |

## 5. Business rules

None — pure verify/dump. Double-display quirk preserved.

## 6. Data access / boundaries

`customers` repository read (S17-B2); log seam (S17-B6); exit code (S17-B4).

## 7. Error / edge behavior

Empty file → zero dump lines, normal end.

## 8. Hard-stop boundary

End of STEP05; chain triggers READXREF (ca7:421).

## 9. Demoted mechanics

KSDS lifecycle → reader; DISPLAY → log; CEE3ABD → step failure.

## 10. Traceability

Stream FRs S17-FR-07, -10; boundaries S17-B2/B4/B6; plan wave 3.
