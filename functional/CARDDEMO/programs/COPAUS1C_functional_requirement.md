# COPAUS1C — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COPAUS1C — `app/app-authorization-ims-db2-mq/cbl/COPAUS1C.cbl`. Stream S-19, wave 2.
- Role: read/detail + write toggle — displays one pending authorization (all CIPAUDTY fields + merchant block) and marks/unmarks it as fraud, coordinating the AUTHFRDS journal write (via LINK COPAUS2C) and the PAUTDTL1 REPL flag flip under one unit of work.

## 2. Trigger / caller contract
- CICS transaction `CPVD` (`csd/CRDDEMO2.csd`); entered by XCTL from COPAUS0C carrying `CDEMO-CPVD-*` (acct id + selected auth key) (`COPAUS0C.cbl:313-321`); re-entered pseudo-conversationally (`COPAUS1C.cbl:202`).
- Outbound: LINK COPAUS2C w/ WS-FRAUD-DATA COMMAREA (:248-252); XCTL CDEMO-TO-PROGRAM on PF3 (:367-368).

## 3. Inputs and outputs
Inputs (map COPAU1A / mapset COPAU01, `bms/COPAU01.bms`): no free-form fields; AID key only. Footer `F3=Back F5=Mark/Remove Fraud F8=Next Auth` (:292).
Reads: IMS GU PAUTSUM0 WHERE ACCNTID (:439-443), GNP PAUTDTL1 WHERE PAUT9CTS=key (:465-469), GNP next (:488-496); writes REPL PAUTDTL1 (:525).
Outputs: all CIPAUDTY fields per COPAU1A labels (:84-283) — card #, auth date/time, resp+reason, auth code, amount, POS, source, MCC, expiry, type, tran id, match/fraud status, merchant name/id/city/state/zip; ERRMSG; outgoing COMMAREA.

## 4. Functional requirements owned (all cross-ref stream FR)
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COPAUS1C-01 | ENTER w/ acct+key | summary+detail read, all fields rendered | :210-227,:439-496 | FR-S19-07 |
| COPAUS1C-02 | ENTER w/o context | ERR-FLG, screen unchanged | :222-225 | FR-S19-07 |
| COPAUS1C-03 | F5, flag≠'F' | action=report; LINK COPAUS2C; on success REPL flag→'F', SYNCPOINT, 'AUTH MARKED FRAUD...' | :228-264,:525-558 | FR-S19-08 |
| COPAUS1C-04 | F5, flag='F' | action=remove; journal 'R', flag→'R', 'AUTH FRAUD REMOVED...' | :236-241,:540-546 | FR-S19-09 |
| COPAUS1C-05 | LINK normal but FRD-UPDT-FAILED | WS-FRD-ACT-MSG shown, ROLLBACK | :253-258 | FR-S19-08 |
| COPAUS1C-06 | LINK abnormal | ROLLBACK, screen re-sent | :258-262 | FR-S19-08 |
| COPAUS1C-07 | F8 | next detail in GNP order (or EOF) | :488-522 | FR-S19-10 |
| COPAUS1C-08 | F3 | XCTL CDEMO-TO-PROGRAM | :367-368 | FR-S19-11 |
| COPAUS1C-09 | IMS other-status on read/repl | ' System error while ... ' + status (read) / '...FRAUD Tagging, ROLLBACK||' + status (repl) | :456-522,:548-556 | FR-S19-07/08 |

## 5. Business rules and validations
MARK-AUTH-FRAUD (:228-264): read auth; flag 'F'→REMOVE action else REPORT; stash record + acct/cust in WS-FRAUD-DATA; LINK; on WS-FRD-UPDT-SUCCESS REPL the segment (flag already flipped by PA-FRAUD-* 88-moves) and SYNCPOINT. Fraud-flag moves use CIPAUDTY 88-levels PA-FRAUD-CONFIRMED 'F'/PA-FRAUD-REMOVED 'R' (CIPAUDTY.cpy:45-47).

## 6. Data access and boundaries
- IMS GU/GNP/REPL (S19-B3, DECIDED: JPA read + save in the fraud transaction).
- LINK COPAUS2C (S19-B4, DECIDED: `AuthFraudService` bean, same tx).
- SYNCPOINT/ROLLBACK (S19-B7, DECIDED: `@Transactional` — journal insert + flag save commit or roll back together).
- Return routing B-012.

## 7. Error and edge behavior
ENTER without numeric acct/selected key → error flag (:222-225); LINK or REPL failure rolls back both the journal and flag; F8 at last detail leaves EOF state (no explicit message in source — details list navigation ends quietly); 'F5' on a just-failed fraud action re-reads the record first (state re-fetched each pass).

## 8. Hard-stop boundary
Owns only the detail view and the fraud toggle; list/paging belongs to COPAUS0C; journal SQL belongs to COPAUS2C; authorization creation belongs to S-20.

## 9. Demoted mechanics
PSB SCHD/TERM (:575-584) → n/a; SYNCPOINT paragraphs (:558) → @Transactional; `DISPLAY 'RPT DT:'` (:528) → log; COMMAREA re-entry → session state.

## 10. Traceability
COPAUS1C-01..09 ↔ FR-S19-07..11 ↔ `PendingAuthDetailServiceTests`/`AuthFraudFlowIT`.
