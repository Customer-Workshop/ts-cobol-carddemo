# COPAUS2C — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COPAUS2C — `app/app-authorization-ims-db2-mq/cbl/COPAUS2C.cbl`. Stream S-19, wave 1.
- Role: seam/write — fraud journal writer. Screenless LINKed subroutine: INSERT into `CARDDEMO.AUTHFRDS`; on -803 duplicate it retries as UPDATE; returns a status byte + message.

## 2. Trigger / caller contract
- EXEC CICS LINK from COPAUS1C MARK-AUTH-FRAUD (`COPAUS1C.cbl:248-252`), COMMAREA = WS-FRAUD-DATA.
- Linkage (`COPAUS2C.cbl:73-86`): WS-ACCT-ID 9(11), WS-CUST-ID 9(9), WS-FRAUD-AUTH-RECORD = CIPAUDTY (full detail), WS-FRD-ACTION X(1) ('F' report / 'R' remove implied by caller flag), WS-FRD-UPDATE-STATUS X(1) 88 S/F, WS-FRD-ACT-MSG X(50).
- Returns via `EXEC CICS RETURN` (:218).

## 3. Inputs and outputs
In: the full pending-auth detail record + acct/cust + action. Out: status S/F + message ('ADD SUCCESS' :201, 'UPDT SUCCESS' :232, or ' SYSTEM ERROR DB2: CODE:...STATE:...' :213).

## 4. Functional requirements owned (all cross-ref stream FR)
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COPAUS2C-01 | LINK w/ auth record | all 26 AUTHFRDS columns populated from CIPAUDTY + acct/cust | :112-142 | FR-S19-08 |
| COPAUS2C-02 | INSERT ok | status S, 'ADD SUCCESS' | :200-201 | FR-S19-08 |
| COPAUS2C-03 | INSERT -803 | UPDATE path retried, status S, 'UPDT SUCCESS' | :204-232 | FR-S19-12 |
| COPAUS2C-04 | INSERT other error | status F, ' SYSTEM ERROR DB2: CODE:...STATE:...' | :206-215 | FR-S19-08 |
| COPAUS2C-05 | UPDATE other error | status F + DB2 message | :234-243 | FR-S19-12 |
| COPAUS2C-06 | any call | AUTH_TS = real timestamp derived by un-complementing PA-AUTH-TIME-9C; FRAUD_RPT_DATE=CURRENT DATE | :103-110,:190 | FR-S19-08 |

## 5. Business rules and validations
Timestamp arithmetic: `WS-AUTH-TIME = 999999999 - PA-AUTH-TIME-9C` → formatted `YY-MM-DD HH.MI.SS.NNNNNN` via TIMESTAMP_FORMAT (:106-110,:168-170). Merchant name moved with length (:128-129). Action copied verbatim to AUTH_FRAUD (:137).

## 6. Data access and boundaries
- DB2 AUTHFRDS (S19-B5, DECIDED: `auth_frauds` JPA table, PK (card_num,auth_ts); save() with duplicate-key→update fallback preserved).
- LINK boundary S19-B4 (called only by COPAUS1C).

## 7. Error and edge behavior
-803 on INSERT is not an error — it's the update retry trigger; all other SQLCODEs return F with formatted message. No logging beyond the returned message (caller decides).

## 8. Hard-stop boundary
Writes only AUTHFRDS; never touches the IMS segments (the caller does the REPL). Not reachable except via LINK from COPAUS1C.

## 9. Demoted mechanics
EXEC SQL INCLUDE SQLCA/AUTHFRDS (:65-69) → JPA entity; COMMAREA → method args; EXEC CICS RETURN → return value.

## 10. Traceability
COPAUS2C-01..06 ↔ FR-S19-08/09/12 ↔ `AuthFraudServiceTests` (incl. duplicate-key upsert case).
