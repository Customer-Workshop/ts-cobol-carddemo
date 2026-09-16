# CBPAUP0C — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: CBPAUP0C — `app/app-authorization-ims-db2-mq/cbl/CBPAUP0C.cbl` (386 lines). Stream S-20, wave 2.
- Role: batch write — expiry purge of pending-authorization details (and emptied summaries) in IMS DBPAUTP0, driven by SYSIN parms, checkpointed, counter-logged.

## 2. Trigger / caller contract
- Job CBPAUP0J: `EXEC PGM=DFSRRC00 PARM='BMP,CBPAUP0C,PSBPAUTB'` (`jcl/CBPAUP0J.jcl:3-4`); CA-7 triggered SCHID=030 (`app/scheduler/CardDemo.ca7:43-51`).
- SYSIN `PRM-INFO` (:98-105): `P-EXPIRY-DAYS` 9(2), `P-CHKP-FREQ` X(5), `P-CHKP-DIS-FREQ` X(5), `P-DEBUG-FLAG` Y/N. Card image `00,00001,00001,Y`.

## 3. Inputs and outputs
In: SYSIN parms; IMS DB via GN PAUTSUM0 (:223) + GNP PAUTDTL1 (:255). Out: SYSOUT DISPLAY counters — 'TOTAL SUMMARY READ', 'SUMMARY REC DELETED', 'TOTAL DETAILS READ', 'DETAILS REC DELETED' (:171-178), CHKP progress (:365-370); RC16 on ABEND (:380-384).

## 4. Functional requirements owned (all cross-ref stream FR)
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| CBPAUP0C-01 | job start | parms accepted/defaults (expiry 5, chkp 5, disp 10); start banner logged | :186-205 | FR-S20-09 |
| CBPAUP0C-02 | main loop | per summary → per detail expiry check | :140-168 | FR-S20-09 |
| CBPAUP0C-03 | expired detail (YYDDD diff ≥ expiryDays) | detail DLET; parent counters decremented (approved or declined by resp) | :277-330 | FR-S20-09 |
| CBPAUP0C-04 | detail DLET fail | 'AUTH DETAIL DELETE FAILED' + status → abend | :316-330 | FR-S20-09 |
| CBPAUP0C-05 | summary counts ≤0 | summary DLET | :156-158,:335-352 | FR-S20-09 |
| CBPAUP0C-06 | summary DLET fail | 'AUTH SUMMARY DELETE FAILED' → abend | :344-352 | FR-S20-09 |
| CBPAUP0C-07 | every chkpFreq summaries | CHKP id RMAD+ctr; every dispFreq → 'CHKP SUCCESS: AUTH COUNT - ' display | :160-165,:355-370 | FR-S20-10 |
| CBPAUP0C-08 | read failure | status + counters displayed → abend RC16 | :237-238,:267-269,:380-384 | FR-S20-09 |
| CBPAUP0C-09 | EOJ | final CHKP + totals display, RC0 | :168-178 | FR-S20-10 |

## 5. Business rules and validations
Expiry test: `WS-AUTH-DATE = 99999 − PA-AUTH-DATE-9C` (YYDDD), `WS-DAY-DIFF = CURRENT-YYDDD − WS-AUTH-DATE`, delete when `≥ WS-EXPIRY-DAYS` (:280-284). Summary delete condition is `PA-APPROVED-AUTH-CNT <= 0 AND PA-APPROVED-AUTH-CNT <= 0` (:156) — verbatim double-check of the approved counter (preserved; see stream open question 1).

## 6. Data access and boundaries
- IMS GN/GNP/DLET/CHKP (S20-B7 — Spring Batch job over shared pending-auth repos; chunk commit = CHKP; ExecutionContext stores progress for restart).
- SYSIN parms → JobParameters (`expiryDays`, `chkpFreq`, `chkpDispFreq`, `debug`).

## 7. Error and edge behavior
Any non-'  ' DL/I status on read/delete → counters dumped + RC16 (:237,:267,:318,:344,:380). Debug flag toggles per-row DISPLAYs (:220,:252,:307).

## 8. Hard-stop boundary
Deletes only pending-auth data; no other tables/queues touched. Not responsible for reply/online flows.

## 9. Demoted mechanics
DFSRRC00 BMP envelope → `BatchJobService` launch; CHKP id → chunk-size + ExecutionContext; DISPLAY → job log; ACCEPT SYSIN → JobParameters; PRM-INFO filler fields ignored.

## 10. Traceability
CBPAUP0C-01..09 ↔ FR-S20-09..10 ↔ `PendingAuthPurgeJobIT`.
