# COUSR00C — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COUSR00C — `app/cbl/COUSR00C.cbl`. Stream S-12, **wave 3**.
- Role: list/browse — pages through the USRSEC file 10 users at a time, lets the operator search by user id and select a row for update (COUSR02C) or delete (COUSR03C).

## 2. Trigger / caller contract
- CICS transaction `CU00` (`app/csd/CARDDEMO.CSD:449`); XCTL'd from COADM01C (admin option 01) with populated `CARDDEMO-COMMAREA`; `EIBCALEN = 0` bounces to COSGN00C (`:110-112`); first entry (`NOT CDEMO-PGM-REENTER`) performs the ENTER path and sends the screen (`:115-119`); re-entry receives the map and evaluates the AID (`:120-138`).
- Paging state carried in COMMAREA `CDEMO-CU00-INFO`: `USRID-FIRST`, `USRID-LAST`, `PAGE-NUM`, `NEXT-PAGE-FLG`, `USR-SEL-FLG`, `USR-SELECTED` (`:67-75`).

## 3. Inputs and outputs
Inputs: USRIDIN X(8), SEL0001..SEL0010 X(1), AID (ENTER/PF3/PF7/PF8), incoming COMMAREA.
Outputs: PAGENUM, 10 × (USRID X(8), FNAME X(20), LNAME X(20), UTYPE X(1)), ERRMSG X(78); outgoing COMMAREA with paging state and (on selection) `CDEMO-TO-PROGRAM` = COUSR02C/COUSR03C, `FROM-TRANID` = CU00, `FROM-PROGRAM` = COUSR00C, `PGM-CONTEXT` = 0 (`:192-199`, `:202-209`).

## 4. Functional requirements owned
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COUSR00C-01 | first entry | first 10 users from low-values, page 1 | :115-119, :216-228, :282-331 | FR-S12-01 |
| COUSR00C-02 | ENTER, USRIDIN filled, no selection | list from first key ≥ USRIDIN, page 1 | :218-228 | FR-S12-02 |
| COUSR00C-03 | ENTER, first SEL = U/u | XCTL COUSR02C with selected id | :151-199 | FR-S12-03 |
| COUSR00C-04 | ENTER, first SEL = D/d | XCTL COUSR03C with selected id | :200-209 | FR-S12-04 |
| COUSR00C-05 | ENTER, first SEL other | `Invalid selection. Valid values are U and D`, then list refresh | :210-228 | FR-S12-05 |
| COUSR00C-06 | several SEL filled | first non-blank row wins | :151-185 | FR-S12-06 |
| COUSR00C-07 | PF8, next page available | next 10 after USRID-LAST, page +1 | :258-278, :282-331 | FR-S12-07 |
| COUSR00C-08 | PF8, no next page | `You are already at the bottom of the page...` | :270-277 | FR-S12-08 |
| COUSR00C-09 | forward EOF | `You have reached the bottom of the page...`, NEXT-PAGE-NO | :308-323, :634-641 | FR-S12-09 |
| COUSR00C-10 | PF7, page > 1 | previous 10 before USRID-FIRST, page −1 | :236-256, :336-379 | FR-S12-10 |
| COUSR00C-11 | PF7, page ≤ 1 | `You are already at the top of the page...` | :248-255 | FR-S12-11 |
| COUSR00C-12 | backward EOF | `You have reached the top of the page...`, page = 1 | :362-372, :668-675 | FR-S12-12 |
| COUSR00C-13 | STARTBR NOTFND | `You are at the top of the page...` | :600-605 | FR-S12-13 |
| COUSR00C-14 | file error | `Unable to lookup User...` | :607-613, :641-647, :675-681 | FR-S12-14 |
| COUSR00C-15 | PF3 | return to COADM01C | :125-127 | FR-S12-15 |
| COUSR00C-16 | other AID | `Invalid key pressed. Please see below...` | :132-136 | FR-S12-16 |

## 5. Business rules and validations
ENTER: selection evaluation (`:151-215`) → search key derivation (`:218-222`) → page number reset to 0 → PROCESS-PAGE-FORWARD (`:227-228`). The selection branch does not stop the refresh; an invalid selection message survives only if no read message overwrites it. PF7 guard `PAGE-NUM > 1` (`:248`); PF8 guard `NEXT-PAGE-YES` (`:270`). Forward page: STARTBR at key (GTEQ default), skip one record when AID is PF8 (`:288-290`), fill up to 10, look-ahead READNEXT sets NEXT-PAGE (`:308-316`); page increments when at least one row read (`:319-322`). Backward page: STARTBR at USRID-FIRST, skip one when AID is PF7 (`:342-344`), fill rows 10→1 via READPREV, look-behind READPREV decides page −1 vs 1 (`:362-372`).

## 6. Data access and boundaries
USRSEC browse only (STARTBR/READNEXT/READPREV/ENDBR `:588-695`); no writes. Boundaries S12-B3 (stale rows), S12-B4 (FROM-PROGRAM to update/delete), S12-B5 (admin gate via shell).

## 7. Error and edge behavior
NOTFND on STARTBR → `You are at the top of the page...` with EOF set and no error flag (`:600-605`); ENDFILE on READNEXT/READPREV → bottom/top messages (`:634-641`, `:668-675`); OTHER → `Unable to lookup User...` with ERR flag (`:607-613`, `:641-647`, `:675-681`). Missing COMMAREA → sign-on (`:110-112`).

## 8. Hard-stop boundary
Delegates update/delete to COUSR02C/COUSR03C (same stream) and returns to COADM01C (S-01). Owns listing, search, paging and selection only.

## 9. Demoted mechanics
Header population, map send/receive, RETURN TRANSID (`:141-144`), ENDBR, `DISPLAY 'RESP:'`, cursor `-1` moves, row INITIALIZE/POPULATE unrolled paragraphs (`:380-560`).

## 10. Traceability
COUSR00C-01..16 ↔ FR-S12-01..16 ↔ `UserAdminServiceTests` (List*), `UserAdminIntegrationTests` (paging), `user-list.component.spec.ts`.
