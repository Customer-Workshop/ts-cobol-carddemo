# CBACT03C — Card-Xref Read/Verify Program — Functional Requirements

## 1. Identity and role

- **Program**: `CBACT03C` (`app/cbl/CBACT03C.cbl`, ~178 lines) — step program of READXREF (`app/jcl/READXREF.jcl:22`, STEP05).
- **Stream**: S-17, wave 3. **Shared ownership**: none.
- **Role**: sequential KSDS verify pass over XREFFILE; DISPLAY each `CARD-XREF-RECORD` **twice** (parity quirk).

## 2. Trigger / caller contract

`EXEC PGM=CBACT03C`; no linkage, no PARM. Target: `readxrefJob` via `POST /api/admin/jobs/readxrefJob`. CEE3ABD only on error.

## 3. Inputs and outputs

| DD | Direction | Layout | Target |
|---|---|---|---|
| XREFFILE | in | KSDS, `CVACT03Y` 50B | `card_xrefs` table, `RepositoryItemReader` by `xrefCardNumber` |
| SYSOUT/SYSPRINT | out | DISPLAY dump | job log |

## 4. FR table (KEEP)

| FR | Rule | Cite |
|---|---|---|
| 01 | OPEN INPUT XREFFILE; status ≠ '00' → abend | open para |
| 02 | Read sequentially until '10' | GET-NEXT loop |
| 03 | DISPLAY `CARD-XREF-RECORD` twice per record: once inside `1000-XREFFILE-GET-NEXT` (`:96`) and once in the main loop (`:78`) — keep both | :78, :96 |
| 04 | Close on '10', GOBACK | close para |
| 05 | Any other status → display status, `CALL 'CEE3ABD' USING ABCODE(999) TIMING(0)` | :158 |

## 5. Business rules

None — pure verify/dump. The double display is a quirk, not a rule, but it is observable behavior and must be preserved for log-parity comparisons.

## 6. Data access / boundaries

`card_xrefs` repository read (S17-B2); log seam (S17-B6); exit code (S17-B4).

## 7. Error / edge behavior

Empty file → zero dump lines. Status '10' mid-loop is EOF, not error.

## 8. Hard-stop boundary

End of STEP05 — final job in the CA-7 S-17 chain (triggers WAITSTEP→OPENFIL, ca7:448/453 — out of scope).

## 9. Demoted mechanics

KSDS lifecycle → reader; DISPLAY → log; CEE3ABD → step failure.

## 10. Traceability

Stream FRs S17-FR-07, -09; boundaries S17-B2/B4/B6; plan wave 3.
