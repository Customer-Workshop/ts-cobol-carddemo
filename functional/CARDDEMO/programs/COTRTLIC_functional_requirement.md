# COTRTLIC — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COTRTLIC — `app/app-transaction-type-db2/cbl/COTRTLIC.cbl` (2098 lines). Stream S-21, wave 1.
- Role: entry/list — filtered, paged browse of TRANSACTION_TYPE (7 rows) that also flags a single row for update or delete, confirmed by F10. Inline writes on this screen (UPDATE/DELETE); add is delegated to COTRTUPC.

## 2. Trigger / caller contract
- CICS tran `CTLI` (`csd/CRDDEMOD.csd` DEFINE TRANSACTION + DB2TRAN row); XCTL'd from COADM01C option 5 (`app/cpy/COADM02Y.cpy:46-49`); `EIBCALEN=0` → init + ADMIN usertype required (`COTRTLIC.cbl:515-525`). Re-entered pseudo-conversationally.
- Outbound: PF3 → SYNCPOINT + XCTL `LIT-ADMINPGM` COADM01C (`:591-623`); F2 → XCTL `LIT-ADDTPGM` COTRTUPC (`:630-651`).

## 3. Inputs and outputs
Inputs (map CTRLIA / mapset COTRTLI, `bms/COTRTLI.bms`): TRTYPE X(2) filter (:89-93), TRDESC X(50) filter (:101-105), TRTSEL1..7 X(1) flags (:133..); FSET row echo TRTTYPn/TRTYPDn; AID.
Reads: TRANSACTION_TYPE via C-TR-TYPE-FORWARD (`:339-352`) / C-TR-TYPE-BACKWARD (`:355-368`) cursors; COUNT cross-check (`:1248-1266`); priming SELECT 1 (`:684-691`).
Writes: UPDATE ... WHERE TR_TYPE (`:1846-1893`), DELETE ... WHERE TR_TYPE (`:1900-1935`).
Outputs: 7 rows type+desc, PAGENO, ERRMSG; outgoing COMMAREA.

## 4. Functional requirements owned (all cross-ref stream FR)
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COTRTLIC-01 | entry | priming query ok → page 1; DB2 down → long-text send + out | :684-691 | FR-S21-02 |
| COTRTLIC-02 | Enter blank filters | page from start, ≤7 rows | :339-368 | FR-S21-02 |
| COTRTLIC-03 | type filter non-numeric | 'TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER' | :1111-1118 | FR-S21-03 |
| COTRTLIC-04 | desc filter | LIKE %trim% applied | :1155-1163 | FR-S21-03 |
| COTRTLIC-05 | cross-check | 'No Records found for these filter conditions' | :1248-1266 | FR-S21-03 |
| COTRTLIC-06 | F7/F8 | prev/next page via backward/forward cursors | :355-368 | FR-S21-04 |
| COTRTLIC-07 | page edges | 'No previous pages to display'/'No more pages to display'/'No more pages for these search conditions' | :1534-1548 | FR-S21-04 |
| COTRTLIC-08 | one D/U flag | row pending; F10 performs UPDATE or DELETE | :1021-1052,:666-678 | FR-S21-05 |
| COTRTLIC-09 | >1 action | 'Please select only 1 action' | :1021-1052 | FR-S21-06 |
| COTRTLIC-10 | bad action char | 'Action code selected is invalid' | :1021-1052 | FR-S21-06 |
| COTRTLIC-11 | F10 w/o pending / other changes | treated as ENTER | :666-678 | FR-S21-05 |
| COTRTLIC-12 | UPDATE result | 'HIGHLIGHTED row was updated' / +100 'Record not found. Deleted by others ?' / -911 'Deadlock. Someone else updating ?' | :1846-1893 | FR-S21-05 |
| COTRTLIC-13 | DELETE result | 'HIGHLIGHTED row deleted.Hit Enter to continue' / -532 'Please delete associated child records first:' | :1900-1935 | FR-S21-05 |
| COTRTLIC-14 | F2 | XCTL COTRTUPC | :630-651 | FR-S21-07 |
| COTRTLIC-15 | F3 | SYNCPOINT + XCTL COADM01C | :591-623 | FR-S21-12 |
| COTRTLIC-16 | invalid AID | 'Invalid key pressed' family | :575-583 | — |

## 5. Business rules and validations
AID gate ENTER/F2/F3/F7/F8/F10-conditional (:575-583). Action flags scanned row-wise; exactly one may be set; prompt text cycles 'Type U to update, D to delete any record' → 'Delete/Update HIGHLIGHTED row. Press F10...' (:239-248).

## 6. Data access and boundaries
- TRANSACTION_TYPE browse+write (S21-B3 — `transaction_types` repo + keyset pagination; SQLSTATEs → messages via `Db2ErrorFormatter`).
- Child-table delete guard via FK (S21-B4 — V2801 RESTRICT).
- Routes (S21-B1/B2 — menu flags, in-app nav).
- DSNTIAC format (S21-B7 — error formatter).

## 7. Error and edge behavior
Non-admin user cannot enter (:515-525); priming-query failure exits w/ long text (:684-691); F10 semantics per rows 11-13; filter edits precede action edits.

## 8. Hard-stop boundary
Maintains TRANSACTION_TYPE only; TRANSACTION_TYPE_CATEGORY untouched except through the FK guard; add/detail flow is COTRTUPC's; menu/auth = S-01.

## 9. Demoted mechanics
Scrollable cursors → keyset pages; SYNCPOINT → @Transactional; COMMAREA paging/filter state → session/request params; SEND-LONG-TEXT → error response.

## 10. Traceability
COTRTLIC-01..16 ↔ FR-S21-02..07,12 ↔ `TranTypeServiceTests`/`TranTypeControllerIT`.
