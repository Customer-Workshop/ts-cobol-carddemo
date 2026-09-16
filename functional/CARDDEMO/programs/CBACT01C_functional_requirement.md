# CBACT01C — Account Read/Verify Job Program — Functional Requirements

## 1. Identity and role

- **Program**: `CBACT01C` (`app/cbl/CBACT01C.cbl`, ~430 lines) — step program of JCL job READACCT (`app/jcl/READACCT.jcl:32`, STEP05).
- **Stream**: S-17, wave 2. **Shared ownership**: none — sole caller chain is READACCT STEP05.
- **Role**: sequential KSDS verify pass over ACCTFILE; dumps each record + labelled fields to SYSOUT; writes three output datasets (PSCOMP FB 107, ARRYPS FB 110, VBPS VB 84); calls COBDATFT to reformat the reissue date.

## 2. Trigger / caller contract

- Invoked by JCL `EXEC PGM=CBACT01C` — no linkage section, no PARM. Target: `readacctJob` launched via `POST /api/admin/jobs/readacctJob`.
- Called utility: `CALL 'COBDATFT' USING CODATECN-REC` (`CBACT01C.cbl:231`) — see COBDATFT program FR.

## 3. Inputs and outputs

| DD | Direction | Layout | Target |
|---|---|---|---|
| ACCTFILE | in | KSDS, `CVACT01Y` 300B | `accounts` table, `RepositoryItemReader` by `acctId` |
| OUTFILE (PSCOMP) | out | FB 107, `OUT-ACCT-REC` ~106B + pad | `ACCTDATA.PSCOMP` |
| ARRYFILE (ARRYPS) | out | FB 110, `ARR-ARRAY-REC` ~105B + pad | `ACCTDATA.ARRYPS` |
| VBRCFILE (VBPS) | out | VB 84; records 12B (VB1) + 39B (VB2) | `ACCTDATA.VBPS` variable lines |
| SYSOUT | out | DISPLAY lines | job log |

## 4. FR table (KEEP)

| FR | Rule | Cite |
|---|---|---|
| 01 | OPEN INPUT ACCTFILE, OUTPUT OUTFILE/ARRYFILE/VBRCFILE; status ≠ '00' → abend path | opens ~CBACT01C.cbl:317/334/352/370 |
| 02 | Read ACCTFILE sequentially until '10'; other status → display + abend 999 | main loop; status checks |
| 03 | DISPLAY raw `ACCOUNT-RECORD`, then labelled per-field lines, per record | per-record DISPLAYs |
| 04 | MOVE `ACCT-REISSUE-DATE` → CODATECN-INP-DATE; set TYPE='2', OUTTYPE='2'; CALL COBDATFT; MOVE first 10 bytes of CODATECN-0UT-DATE → `OUT-ACCT-REISSUE-DATE` | CBACT01C.cbl:231 area |
| 05 | If `ACCT-CURR-CYC-DEBIT` = 0, substitute `2525.00` into `OUT-ACCT-CURR-CYC-DEBIT` | :236-237 |
| 06 | Write OUT-ACCT-REC → OUTFILE (status '00'/'10' tolerated, else abend) | write + status checks |
| 07 | Build ARR-ARRAY-REC: id + 5 occurs of (curr-bal display, cyc-debit COMP-3) using constants 1005.00/1525.00/-1025.00/-2500.00; write → ARRYFILE | :256-260 |
| 08 | Write VB1 (12B: id+status) then VB2 (39B: id+curr-bal+credit-limit+reissue-YYYY from source date) → VBRCFILE | variable write para |
| 09 | On '10': close all four files, GOBACK | close ~:388 |
| 10 | Any unhandled status → `CALL 'CEE3ABD' USING ABCODE(999) TIMING(0)` | :410 |

## 5. Business rules

- Constants injected are demo-fixture semantics, not derivations: `2525.00` zero-debit substitute; array constants `1005.00/1525.00/-1025.00/-2500.00`.
- `OUT-ACCT-CURR-CYC-DEBIT` is COMP-3 (6B) while sibling money fields are zoned display (12B) — keep the mixed encoding.
- VB2's year comes from the source `YYYY-MM-DD` field's first 4 chars, not the COBDATFT output.

## 6. Data access / boundaries

- `accounts` repository read (S17-B2); three flat files in output dir (S17-B5); log seam (S17-B6); DateEditService (S17-B3); exit code (S17-B4).

## 7. Error / edge behavior

- File-status '10' at first read (empty file) → clean close, no outputs beyond headers of an empty file (legacy writes zero records; both files created empty via DD).
- Short layouts vs LRECL: pad with spaces to 107/110 (INFERRED, S17-B7).
- COBDATFT error output is ignored — no COERMSG check exists; preserve.

## 8. Hard-stop boundary

Program ends at file close + GOBACK; nothing downstream of STEP05 inside this job.

## 9. Demoted mechanics (do not migrate literally)

- IEFBR14 PREDEL step → writer `shouldDeleteIfExists`.
- VSAM/QSAM open/close/status words → Spring Batch reader/writer lifecycle.
- DISPLAY → log lines.

## 10. Traceability

Stream FRs S17-FR-01..07; boundaries S17-B1..B7; migration plan `S17_data_read_verify_migration_plan.md` wave 2.
