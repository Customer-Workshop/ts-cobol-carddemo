# COUSR03C — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COUSR03C — `app/cbl/COUSR03C.cbl`. Stream S-12, **wave 2**.
- Role: read + delete — fetches a user by id for confirmation and deletes it on PF5.

## 2. Trigger / caller contract
- CICS transaction `CU03` (`app/csd/CARDDEMO.CSD:479`); XCTL'd from COADM01C (admin option 04) or from COUSR00C with `CDEMO-CU03-USR-SELECTED` populated (`:99-104`); `EIBCALEN = 0` bounces to COSGN00C (`:90-92`). First entry with a selected id performs the ENTER fetch immediately (`:96-105`).
- Return target: PF3 → `CDEMO-FROM-PROGRAM` when set, else COADM01C (`:111-118`); PF12 → COADM01C (`:123-125`).

## 3. Inputs and outputs
Inputs: USRIDIN X(8), AID (ENTER/PF3/PF4/PF5/PF12), incoming COMMAREA.
Outputs: FNAME X(20), LNAME X(20), USRTYPE X(1) display after fetch (`:163-167`), ERRMSG X(78).

## 4. Functional requirements owned
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COUSR03C-01 | entry with selected id | user fetched as if ENTER | :96-105 | FR-S12-37 |
| COUSR03C-02 | ENTER | blank → `User ID can NOT be empty...`; found → details + neutral `Press PF5 key to delete this user ...`; NOTFND → `User ID NOT found...`; OTHER → `Unable to lookup User...` | :145-169, :281-299 | FR-S12-38 |
| COUSR03C-03 | PF5 | blank → `User ID can NOT be empty...`; DELETE ok → fields cleared, green `User <id> has been deleted ...`; NOTFND → `User ID NOT found...`; OTHER → `Unable to Update User...` | :177-192, :314-335 | FR-S12-39 |
| COUSR03C-04 | PF3 / PF12 / PF4 / other AID | caller / COADM01C / clear / `Invalid key pressed. Please see below...` | :111-129, :341-356 | FR-S12-40 |

## 5. Business rules and validations
Only USRIDIN is validated (non-blank). PF5 performs READ then DELETE unconditionally (`:190-191`); a NOTFND on READ is repeated by DELETE, so the visible result is `User ID NOT found...`.

## 6. Data access and boundaries
USRSEC READ UPDATE (`:269-278`) + DELETE (`:307-312`). Boundary S12-B4 (caller return), S12-B5 (admin gate).

## 7. Error and edge behavior
DELETE OTHER path message is `Unable to Update User...` (`:332`) — preserved verbatim.

## 8. Hard-stop boundary
Returns to COUSR00C or COADM01C only.

## 9. Demoted mechanics
Header population, SEND/RECEIVE, RETURN TRANSID (`:134-137`), `DISPLAY 'RESP:'`, cursor `-1` moves, `WS-USR-MODIFIED` flag declared but unused (`:45-47`).

## 10. Traceability
COUSR03C-01..04 ↔ FR-S12-37..40 ↔ `UserAdminServiceTests` (FetchForDelete*/Delete*), `UserAdminIntegrationTests`, `user-delete.component.spec.ts`.
