# PAUDBUNL — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: PAUDBUNL — `app/app-authorization-ims-db2-mq/cbl/PAUDBUNL.CBL` (317 lines). Stream S-20, wave 2.
- Role: batch unload (data-transfer seam) — walks the whole pending-auth DB and writes two flat files: root records and key-prefixed child records.

## 2. Trigger / caller contract
- Job UNLDPADB: STEP0 IEFBR14 deletes prior outputs; STEP01 `EXEC PGM=DFSRRC00 PARM='DLI,PAUDBUNL,PAUTBUNL'` (`jcl/UNLDPADB.JCL`); PSB PAUTBUNL w/ PAUTBPCB PROCOPT=GOTP (`ims/PAUTBUNL.PSB`).
- DDs: OUTFIL1 LRECL=100 FB (root `OPFIL1-REC`), OUTFIL2 LRECL=206 FB (`ROOT-SEG-KEY` + `CHILD-SEG-REC`).

## 3. Inputs and outputs
In: IMS DB (sequential GN/GNP walk). Out: two flat files in segment order (root, then each parent's children); counters; RC16 on abend.

## 4. Functional requirements owned (all cross-ref stream FR)
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| PAUDBUNL-01 | job start | GN PAUTSUM0 unqualified; numeric PA-ACCT-ID guard before write | 2000-FIND-NEXT-AUTH-SUMMARY | FR-S20-12 |
| PAUDBUNL-02 | per root | OPFIL1-REC written; GNP loop over children | same para | FR-S20-12 |
| PAUDBUNL-03 | per child | OPFIL2-REC = root key + child segment written | GNP loop | FR-S20-12 |
| PAUDBUNL-04 | 'GB' end / 'GE' no children | clean termination of walk | status handling | FR-S20-12 |
| PAUDBUNL-05 | other status | RC16 abend | status handling | FR-S20-12 |

## 5. Business rules and validations
Root record written only when `PA-ACCT-ID` numeric. Children always written under the current parent key.

## 6. Data access and boundaries
IMS GN/GNP read-all (S20-B8 — JPA stream query + CSV export; 'GB'→end-of-stream, 'GE'→no children).

## 7. Error and edge behavior
Non-ok/non-end statuses → RC16. Output layout is positional (root 100B; child = 6B key prefix + 200B segment) — preserved as documented CSV layouts in target.

## 8. Hard-stop boundary
Read-only over the DB; no deletes/updates. Export sibling of DBUNLDGS (GSAM variant) and DFSURGU0 (ULU).

## 9. Demoted mechanics
DLI envelope, file-status, QSAM writes → Spring Resource writers.

## 10. Traceability
PAUDBUNL-01..05 ↔ FR-S20-12 ↔ `PendingAuthExportJobIT`.
