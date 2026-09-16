# COUSR01C — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COUSR01C — `app/cbl/COUSR01C.cbl`. Stream S-12, **wave 1**.
- Role: create — adds a regular or admin user record to USRSEC.

## 2. Trigger / caller contract
- CICS transaction `CU01` (`app/csd/CARDDEMO.CSD:459`); XCTL'd from COADM01C (admin option 02); `EIBCALEN = 0` bounces to COSGN00C (`:78-80`); first entry sends an empty screen with cursor on FNAME (`:83-87`); re-entry evaluates the AID (`:88-104`).

## 3. Inputs and outputs
Inputs: FNAME X(20), LNAME X(20), USERID X(8), PASSWD X(8) (dark), USRTYPE X(1), AID (ENTER/PF3/PF4).
Outputs: ERRMSG X(78) (green on success), cleared fields after a successful add; outgoing COMMAREA on PF3 (`CDEMO-TO-PROGRAM` = COADM01C, `FROM-PROGRAM` = COUSR01C, `:166-178`).

## 4. Functional requirements owned
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COUSR01C-01 | ENTER, a field blank | first blank in order FNAME → LNAME → USERID → PASSWD → USRTYPE yields `First Name can NOT be empty...` / `Last Name can NOT be empty...` / `User ID can NOT be empty...` / `Password can NOT be empty...` / `User Type can NOT be empty...`, cursor on that field | :118-147 | FR-S12-17 |
| COUSR01C-02 | ENTER, all filled, new key | WRITE USRSEC; fields cleared; green `User <id> has been added ...` | :153-159, :240-259 | FR-S12-18 |
| COUSR01C-03 | ENTER, duplicate key | `User ID already exist...` | :260-266 | FR-S12-19 |
| COUSR01C-04 | ENTER, other WRITE error | `Unable to Add User...` | :267-273 | FR-S12-20 |
| COUSR01C-05 | PF3 | return to COADM01C | :93-95 | FR-S12-21 |
| COUSR01C-06 | PF4 | fields + message cleared, cursor FNAME | :96-97, :279-295 | FR-S12-22 |
| COUSR01C-07 | other AID (incl. PF12) | `Invalid key pressed. Please see below...` | :98-102 | FR-S12-23 |

## 5. Business rules and validations
Blank = `SPACES OR LOW-VALUES` on each input (`:118-147`); no format, length or domain check on USRTYPE beyond non-blank (hint text `A=Admin, U=User` only). No upper-casing. Record fields copied verbatim (`:154-158`).

## 6. Data access and boundaries
USRSEC WRITE keyed by SEC-USR-ID (`:240-248`). Boundary S12-B1: target `UserType` enum admits only A/U — other codes fall into the OTHER path message. Password stored hashed (approved S-01 deviation).

## 7. Error and edge behavior
DUPKEY/DUPREC → `User ID already exist...` (`:260-266`); OTHER → `Unable to Add User...` (`:267-273`). Footer advertises `F12=Exit` but PF12 is handled as an invalid key (`:98-102`).

## 8. Hard-stop boundary
No outbound XCTL other than the return to COADM01C.

## 9. Demoted mechanics
Header population, SEND/RECEIVE, RETURN TRANSID (`:107-110`), `DISPLAY 'RESP:'`, cursor `-1` moves.

## 10. Traceability
COUSR01C-01..07 ↔ FR-S12-17..23 ↔ `UserAdminServiceTests` (Add*), `UserAdminIntegrationTests`, `user-add.component.spec.ts`.
