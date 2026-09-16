# DBUNLDGS — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: DBUNLDGS — `app/app-authorization-ims-db2-mq/cbl/DBUNLDGS.CBL` (366 lines). Stream S-20, wave 2.
- Role: batch unload (data-transfer seam) — same GN/GNP walk as PAUDBUNL but writes to **GSAM** files through a 3-PCB PSB (`DLIGSAMP`: PAUTBPCB PROCOPT=GOTP + PASFLDBD/PADFLDBD PROCOPT=LS, `ims/DLIGSAMP.PSB`). QSAm file I/O is commented out — output goes via DL/I ISRT on the GSAM PCBs.

## 2. Trigger / caller contract
- Job UNLDGSAM: `EXEC PGM=DFSRRC00 PARM='DLI,DBUNLDGS,DLIGSAMP'` (`jcl/UNLDGSAM.JCL`); output DDs PASFILOP/PADFILOP.

## 3. Inputs and outputs
In: IMS DB read (GOTP). Out: two GSAM datasets — summary stream (PASFILOP) + detail stream (PADFILOP), same record images as PAUDBUNL's files.

## 4. Functional requirements owned (all cross-ref stream FR)
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| DBUNLDGS-01 | job start | GN PAUTSUM0 walk begins | 2000-* paras | FR-S20-12 |
| DBUNLDGS-02 | per root | ISRT to PASFLPCB | GN/GNP+ISRT paras | FR-S20-12 |
| DBUNLDGS-03 | per child | ISRT to PADFLPCB (key|child rec) | GNP loop | FR-S20-12 |
| DBUNLDGS-04 | 'GB'/'GE' | clean end | status handling | FR-S20-12 |
| DBUNLDGS-05 | other status | RC16 abend | status handling | FR-S20-12 |

## 5. Business rules and validations
Identical to PAUDBUNL: numeric-acct-id guard on root write; children under current parent.

## 6. Data access and boundaries
IMS GN/GNP read + GSAM ISRT (S20-B8 — folds into the same target export step as PAUDBUNL; the QSAM-vs-GSAM distinction vanishes in a Spring Resource CSV writer). The two writers are kept as one target export with two output files.

## 7. Error and edge behavior
Same status protocol as PAUDBUNL ('  ' ok / 'GB' end / 'GE' no children / other→RC16).

## 8. Hard-stop boundary
Export variant only; in target it is the same job as PAUDBUNL exercised through a different writer — one Java implementation satisfies both FR sets.

## 9. Demoted mechanics
3-PCB PSB and GSAM ISRT → file writer; DLI envelope → Spring Batch step.

## 10. Traceability
DBUNLDGS-01..05 ↔ FR-S20-12 ↔ `PendingAuthExportJobIT` (same implementation as PAUDBUNL; GSAM path verified by layout equality test).
