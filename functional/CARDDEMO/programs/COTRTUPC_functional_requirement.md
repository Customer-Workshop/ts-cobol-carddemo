# COTRTUPC — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COTRTUPC — `app/app-transaction-type-db2/cbl/COTRTUPC.cbl` (1702 lines). Stream S-21, wave 1.
- Role: detail/validate/write — single TRANSACTION_TYPE record add, update, and delete, driven by a 15-state change-action byte with confirm (F4/F5) and cancel-restore (F12).

## 2. Trigger / caller contract
- CICS tran `CTTU` (`csd/CRDDEMOD.csd`); entered from COADM01C option 6 or F2-XCTL from COTRTLIC (`COTRTLIC.cbl:630-651`). `CDEMO-TO-PROGRAM` determines PF3 target (COADM01C or COTRTLIC) (`:429-460`).
- Re-entered pseudo-conversationally; HANDLE ABEND → ABEND-ROUTINE sends ABEND-DATA + `ABEND ABCODE` (`:348-350,:1675-1697`).

## 3. Inputs and outputs
Inputs (map CTRTUPA / mapset COTRTUP, `bms/COTRTUP.bms`): TRTYPCD X(2) (:84-87), TRTYDSC X(50) (:94-97); AID.
Reads: SELECT by TR_TYPE.
Writes: INSERT / UPDATE / DELETE on TRANSACTION_TYPE (`:1544-1649`).
Outputs: field echo, INFOMSG, ERRMSG, conditional fkeys (F4/F5/F6/F12 in DRK when applicable, :111-135).

## 4. Functional requirements owned (all cross-ref stream FR)
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COTRTUPC-01 | entry | state init; 'No input received' on empty submit | :160-196 | FR-S21-08 |
| COTRTUPC-02 | search code invalid | state K — required/numeric/non-zero/zero-pad edits fail | :820-842,:907-972 | FR-S21-08 |
| COTRTUPC-03 | search miss | state X 'No record found for this key in database'; F5→create-new (R) | :160-196,:524-533 | FR-S21-08 |
| COTRTUPC-04 | search hit | state S record shown | :296-327 | FR-S21-08 |
| COTRTUPC-05 | edit desc | required/≤50 alphanum; unchanged compare 'No change detected with respect to values fetched.' | :758-764,:783-811,:849-901 | FR-S21-08 |
| COTRTUPC-06 | F5 save (state N) | UPDATE; +100→INSERT; -911 'Could not lock record for update'; else 'Error updating: TRANSACTION_TYPE Table. SQLCODE:'; success 'Changes committed to database' (state C) / 'Changes unsuccessful' (F) | :1544-1589 | FR-S21-09 |
| COTRTUPC-07 | F4 delete | S→9 confirm→8 DELETE; -532 'Please delete associated child records first:' (state 6); success state 7 | :524-533,:1638-1649 | FR-S21-10 |
| COTRTUPC-08 | F12 cancel | restores originals; 'Update was cancelled'/'Delete was cancelled' (state B) | :524-533,:988-1008 | FR-S21-11 |
| COTRTUPC-09 | F3 | SYNCPOINT + XCTL CDEMO-TO-PROGRAM; 'PF03 pressed.Exiting' | :429-460 | FR-S21-12 |
| COTRTUPC-10 | invalid AID | 'Invalid key pressed' | :160-196 | — |
| COTRTUPC-11 | review-new (V) | 'Changes validated.Press F5 to save' (state N) | :160-196 | FR-S21-09 |

## 5. Business rules and validations
State byte TTUP-CHANGE-ACTION drives transitions (:296-327): edits always run before state checks; delete requires confirm stage; cancel restores fetched values; lock/contention surfaces as dedicated messages.

## 6. Data access and boundaries
- TRANSACTION_TYPE read/write (S21-B3 — repo + messages per SQLSTATE; the +100→INSERT upsert is preserved).
- Child-table FK guard (S21-B4 — -532→message).
- Return routing (B-012 — CDEMO-TO-PROGRAM).

## 7. Error and edge behavior
'No change detected' short-circuits the save prompt; F6 = add/new-entry reset; ABEND routine reports ABCODE before dying.

## 8. Hard-stop boundary
Owns single-record lifecycle only; list/paging is COTRTLIC; batch apply is COBTUPDT; category children are guard-only.

## 9. Demoted mechanics
Pseudo-conversational state byte → server session attribute; SYNCPOINT → @Transactional; map re-sends → Thymeleaf re-render w/ state; HANDLE ABEND → exception mapper.

## 10. Traceability
COTRTUPC-01..11 ↔ FR-S21-08..12 ↔ `TranTypeMaintServiceTests`/`TranTypeMaintControllerIT`.
