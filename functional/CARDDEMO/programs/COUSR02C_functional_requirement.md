# COUSR02C — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COUSR02C — `app/cbl/COUSR02C.cbl`. Stream S-12, **wave 2**.
- Role: read + update — fetches a user by id and rewrites name/password/type.

## 2. Trigger / caller contract
- CICS transaction `CU02` (`app/csd/CARDDEMO.CSD:469`); XCTL'd from COADM01C (admin option 03) or from COUSR00C with `CDEMO-CU02-USR-SELECTED` populated (`:99-104`); `EIBCALEN = 0` bounces to COSGN00C (`:90-92`). First entry with a selected id performs the ENTER fetch immediately (`:96-105`).
- Return target: PF3 → `CDEMO-FROM-PROGRAM` when set, else COADM01C (`:111-119`); PF12 → COADM01C (`:124-126`).

## 3. Inputs and outputs
Inputs: USRIDIN X(8), FNAME X(20), LNAME X(20), PASSWD X(8) (dark), USRTYPE X(1), AID (ENTER/PF3/PF4/PF5/PF12), incoming COMMAREA.
Outputs: populated fields after fetch (`:166-171`), ERRMSG X(78) (neutral prompt, red errors, green success).

## 4. Functional requirements owned
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COUSR02C-01 | entry with selected id | user fetched as if ENTER | :96-105 | FR-S12-24 |
| COUSR02C-02 | ENTER, USRIDIN blank | `User ID can NOT be empty...` | :146-151 | FR-S12-25 |
| COUSR02C-03 | ENTER, found | fields populated; neutral `Press PF5 key to save your updates ...` | :152-172, :334-339 | FR-S12-26 |
| COUSR02C-04 | ENTER, not found | `User ID NOT found...` | :340-345 | FR-S12-27 |
| COUSR02C-05 | ENTER, READ error | `Unable to lookup User...` | :346-352 | FR-S12-28 |
| COUSR02C-06 | PF5/PF3, a field blank | order USRIDIN → FNAME → LNAME → PASSWD → USRTYPE, messages as COUSR01C-01 | :180-209 | FR-S12-29 |
| COUSR02C-07 | PF5/PF3, nothing changed | red `Please modify to update ...` | :217-243 | FR-S12-30 |
| COUSR02C-08 | PF5/PF3, ≥1 change | REWRITE; green `User <id> has been updated ...` | :236-237, :360-376 | FR-S12-31 |
| COUSR02C-09 | PF5/PF3, REWRITE NOTFND / OTHER | `User ID NOT found...` / `Unable to Update User...` | :340-345, :377-389 | FR-S12-32 |
| COUSR02C-10 | PF3 | attempt save, then return to caller regardless of outcome | :111-119 | FR-S12-33 |
| COUSR02C-11 | PF5 | save, stay | :122-123 | FR-S12-34 |
| COUSR02C-12 | PF12 | return to COADM01C, no save | :124-126 | FR-S12-35 |
| COUSR02C-13 | PF4 / other AID | clear fields + message / `Invalid key pressed. Please see below...` | :120-121, :127-130, :395-411 | FR-S12-36 |

## 5. Business rules and validations
Fetch clears the four editable fields before READ (`:158-163`). Save re-reads the record (READ UPDATE `:322-331`) and compares FNAME/LNAME/PASSWD/USRTYPE to the stored values, setting USR-MODIFIED when any differs (`:219-234`); only then REWRITE (`:236-237`). USERID is the key and is not updatable.

## 6. Data access and boundaries
USRSEC READ UPDATE + REWRITE. Boundary S12-B2: the stored password is hashed, so the fetch cannot echo it (`:168` in source) — target returns blank PASSWD; "modified" for the password = supplied value does not verify against the stored hash. S12-B1 applies to USRTYPE codes outside A/U (`Unable to Update User...`). S12-B4: caller return via `from` route param.

## 7. Error and edge behavior
READ NOTFND / OTHER (`:340-352`); REWRITE NOTFND / OTHER (`:377-389`). PF3 performs UPDATE-USER-INFO and then RETURN-TO-PREV-SCREEN unconditionally (`:112-119`): whatever UPDATE-USER-INFO sent (validation message, `Please modify to update ...`, success) is superseded by the XCTL, so PF3 always leaves the screen; a valid change is saved, an invalid one is silently dropped. The target reproduces this: PF3 issues the save command and navigates back regardless of the outcome.

## 8. Hard-stop boundary
Returns to COUSR00C or COADM01C only.

## 9. Demoted mechanics
Header population, SEND/RECEIVE, RETURN TRANSID (`:135-138`), `DISPLAY 'RESP:'`, cursor `-1` moves.

## 10. Traceability
COUSR02C-01..13 ↔ FR-S12-24..36 ↔ `UserAdminServiceTests` (FetchForUpdate*/Update*), `UserAdminIntegrationTests`, `user-update.component.spec.ts`.
