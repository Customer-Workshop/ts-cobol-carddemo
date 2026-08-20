# S-01 Sign-on + Menu Shell — Stream Functional Requirements (`!mf_stream_fr_generation`)

Status: complete (2026-08-20). Derived from `S01_SignonMenu_analysis.md` and source. Language: English (source labels are English).
Encoding note: source is ASCII; no transcoding needed; cites are raw line numbers.

## 1. Purpose and scope
Authenticate a CardDemo user against the user security file and present the role-appropriate menu (main menu for regular users, admin menu for administrators), dispatching the selected option to the owning function. Process type ONLINE. Triggers: CICS transactions CC00 (sign-on), CM00 (main menu), CA00 (admin menu). Hard stop: any transfer out of COSGN00C/COMEN01C/COADM01C to a route program (other streams). Exclusions: all 11 main-menu and 6 admin route target programs.

## 2. Actors and preconditions
- Actor: any terminal user with a record in the USRSEC file (`AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS`, keyed by 8-char user ID, seeded by DUSRSECJ — `app/jcl/DUSRSECJ.jcl:62-64`).
- Precondition: USRSEC file open in the CICS region; user record has type 'A' (admin) or 'U' (regular) (`app/cpy/CSUSR01Y.cpy:22`, `app/cpy/COCOM01Y.cpy:27-28`).

## 3. Surface specification
### Sign-on screen COSGN0A (`app/bms/COSGN00.bms`, fields `app/cpy-bms/COSGN00.CPY`)
| Field | Label (verbatim) | I/O | Len/PIC | Edits |
|---|---|---|---|---|
| USERID | `User ID     :` (bms:155) | INPUT | X(8), IC cursor (bms:156) | mandatory (`COSGN00C.cbl:118-122`); upper-cased (`:132`) |
| PASSWD | `Password    :` (bms:174) | INPUT | X(8), DRK non-display (bms:175) | mandatory (`:123-127`); upper-cased (`:135`) |
| ERRMSG | — | OUTPUT | X(80) | messages per error catalogue |
| Header | `Tran :`/`Date :`/`Prog :`/`Time :`/`AppID:`/`SysID:` (bms:33-88) | OUTPUT | — | populated `COSGN00C.cbl:177-204` |
Instruction text: `Type your User ID and Password, then press ENTER` (bms:149).

### Main menu screen COMEN1A (`app/bms/COMEN01.bms`)
| Field | I/O | Edits |
|---|---|---|
| OPTION | INPUT X(2) | right-justify, blank→zero (`COMEN01C.cbl:117-124`); numeric, 1..11 (`:127-134`) |
| OPTN001–012 | OUTPUT | 11 rows built from `app/cpy/COMEN02Y.cpy` route table (`:262-303`); slot 12 unused |

### Admin menu screen COADM1A (`app/bms/COADM01.bms`)
Same shape; OPTION 1..6 (`COADM01C.cbl:131-138`); options from `app/cpy/COADM02Y.cpy`.

## 4. Functional requirements (KEEP)

| ID | Flow | Business trigger | Observable result | Program(s) | Cite | Boundary | Covering test |
|---|---|---|---|---|---|---|---|
| FR-S01-01 | Sign-on validation | User ID blank | Message `Please enter User ID ...`, cursor on User ID, no authentication attempted | COSGN00C | COSGN00C.cbl:118-122 | — | TBD |
| FR-S01-02 | Sign-on validation | Password blank (User ID present) | Message `Please enter Password ...`, cursor on Password | COSGN00C | :123-127 | — | TBD |
| FR-S01-03 | Authentication | Credentials entered; no matching user record | Message `User not found. Try again ...`, cursor on User ID | COSGN00C | :247-251 | S01-B4 | TBD |
| FR-S01-04 | Authentication | User exists; password mismatch | Message `Wrong Password. Try again ...`, cursor on Password | COSGN00C | :241-246 | S01-B4 | TBD |
| FR-S01-05 | Authentication | User exists; password matches; user type 'A' | Admin menu displayed; session carries user id + type | COSGN00C→COADM01C | :223-234 | S01-B6 | TBD |
| FR-S01-06 | Authentication | User exists; password matches; user type 'U' | Main menu displayed; session carries user id + type | COSGN00C→COMEN01C | :223-240 | S01-B6 | TBD |
| FR-S01-07 | Authentication | Security file unavailable/other error | Message `Unable to verify the User ...` | COSGN00C | :252-256 | S01-B4 | TBD |
| FR-S01-08 | Sign-on exit | User presses PF3 on sign-on | Plain-text farewell `Thank you for using CardDemo application...`; session ends | COSGN00C | :88-90; CSMSG01Y.cpy:18-19 | — | TBD |
| FR-S01-09 | Case handling | User ID / password entered in lower case | Treated as upper case (authentication succeeds for correct credentials regardless of case) | COSGN00C | :132-136 | — | TBD |
| FR-S01-10 | Menu display | Regular user authenticated | Main menu lists the 11 numbered options from the route catalogue, in order | COMEN01C | COMEN01C.cbl:262-303; COMEN02Y.cpy:21-86 | — | TBD |
| FR-S01-11 | Menu selection | Option not numeric, zero, or > 11 | Message `Please enter a valid option number...`, menu redisplayed | COMEN01C | :127-134 | — | TBD |
| FR-S01-12 | Authorization | Regular user ('U') selects an admin-only option | Message `No access - Admin Only option... `; no transfer | COMEN01C | :136-143 | — | TBD |
| FR-S01-13 | Menu dispatch | Valid, permitted option selected | Control transfers to the owning function with the user context | COMEN01C | :177-187 | S01-B1 | TBD |
| FR-S01-14 | Feature availability | Option 11 (Pending Authorizations) selected and function not installed | Message `This option <name> is not installed...` (red) | COMEN01C | :148-168 | S01-B2 | TBD |
| FR-S01-15 | Coming-soon options | Option whose target is a DUMMY placeholder selected | Message `This option <name> is coming soon ...` (green) | COMEN01C | :169-176 | — | TBD |
| FR-S01-16 | Menu exit | PF3 on main or admin menu | Return to sign-on screen | COMEN01C, COADM01C | COMEN01C.cbl:96-98; COADM01C.cbl:100-102 | S01-B3 | TBD |
| FR-S01-17 | Admin menu display | Admin authenticated | Admin menu lists the 6 numbered options from the admin catalogue | COADM01C | COADM01C.cbl:229-256; COADM02Y.cpy:22 | — | TBD |
| FR-S01-18 | Admin selection | Option not numeric, zero, or > 6 | Message `Please enter a valid option number...` | COADM01C | :131-138 | — | TBD |
| FR-S01-19 | Admin dispatch | Valid admin option selected | Control transfers to the owning admin function with user context | COADM01C | :140-149 | S01-B1 | TBD |
| FR-S01-20 | Invalid key | Any AID key other than ENTER/PF3 on any of the three screens | Message `Invalid key pressed. Please see below...`; screen redisplayed | all 3 | COSGN00C.cbl:91-94; COMEN01C.cbl:99-102; COADM01C.cbl:103-106; CSMSG01Y.cpy:20-21 | — | TBD |

## 5. Validation and error catalogue
| Code/message | Trigger | Cite | Blocking? | Resulting state |
|---|---|---|---|---|
| `Please enter User ID ...` | blank user id | COSGN00C.cbl:120 | blocking | stay on sign-on |
| `Please enter Password ...` | blank password | :125 | blocking | stay on sign-on |
| `User not found. Try again ...` | READ RESP=13 (NOTFND) | :249 | blocking | stay on sign-on |
| `Wrong Password. Try again ...` | password mismatch | :242 | blocking | stay on sign-on |
| `Unable to verify the User ...` | READ RESP other | :254 | blocking | stay on sign-on |
| `Please enter a valid option number...` | option out of range | COMEN01C.cbl:131; COADM01C.cbl:135 | blocking | stay on menu |
| `No access - Admin Only option... ` | 'U' picks admin option | COMEN01C.cbl:140 | blocking | stay on menu |
| `This option <name> is not installed...` | COPAUS0C absent | COMEN01C.cbl:163-167 | blocking | stay on menu |
| `This option <name> is coming soon ...` | DUMMY target | COMEN01C.cbl:172-176 | warning-style | stay on menu |
| `Invalid key pressed. Please see below...` | unmapped AID | CSMSG01Y.cpy:20-21 | blocking | redisplay |
| `Thank you for using CardDemo application...` | PF3 at sign-on | CSMSG01Y.cpy:18-19 | terminal | session end |
All message text is source-proven (working-storage literals or copybooks); no external message table.

## 6. Field and data derivations
- Session identity: `CDEMO-USER-ID` ← upper-cased screen USERID (`COSGN00C.cbl:132-134`); `CDEMO-USER-TYPE` ← `SEC-USR-TYPE` of the USRSEC record (`:227`).
- Menu option rows: `"<nn>. <name>"` from `CDEMO-MENU-OPT-NUM`/`-NAME` (`COMEN01C.cbl:269-272`), admin likewise (`COADM01C.cbl:236-239`).
- Header date/time from system clock (`COSGN00C.cbl:179-196`); AppID/SysID from runtime environment (`:198-204`) — in the target, application/environment config.

## 7. Mechanics (demoted, cited)
Pseudo-conversational RETURN TRANSID loops (COSGN00C.cbl:98-102 etc.); COMMAREA re-enter flag (`CDEMO-PGM-CONTEXT`, COCOM01Y.cpy:29-31); XCTL plumbing and return routing via `CDEMO-TO-PROGRAM` (COMEN01C.cbl:196-203); PGMIDERR HANDLE CONDITION (COADM01C.cbl:77-79); option right-justify/zero-fill mechanics (COMEN01C.cbl:117-124); screen ERASE/CURSOR handling. Hard stop: everything after a successful dispatch XCTL.

## 8. Acceptance criteria (Given/When/Then) — one per FR
- FR-S01-01: Given the sign-on screen, When ENTER with blank User ID, Then show `Please enter User ID ...` and remain signed off.
- FR-S01-02: Given User ID filled and Password blank, When ENTER, Then show `Please enter Password ...`.
- FR-S01-03: Given no user `XXXX`, When signing on as `XXXX`, Then show `User not found. Try again ...`.
- FR-S01-04: Given user with password P1, When signing on with P2≠P1, Then show `Wrong Password. Try again ...`.
- FR-S01-05/06: Given valid credentials of an admin/regular user, When ENTER, Then the admin/main menu is shown with the user's context.
- FR-S01-07: Given the security store unreachable, When signing on, Then show `Unable to verify the User ...`.
- FR-S01-08: Given the sign-on screen, When PF3, Then farewell text and session end.
- FR-S01-09: Given valid credentials typed lower-case, When ENTER, Then sign-on succeeds.
- FR-S01-10/17: Given a fresh menu display, Then exactly 11 (main) / 6 (admin) options render in catalogue order.
- FR-S01-11/18: Given the menu, When option `0`, `99`, or `AB` entered, Then valid-option error.
- FR-S01-12: Given a 'U' user on the main menu, When choosing an 'A'-flagged option, Then access-denied message and no navigation.
- FR-S01-13/19: Given a permitted option, When ENTER, Then the target function opens with user context intact.
- FR-S01-14: Given Pending Authorizations not installed, When option 11, Then not-installed message.
- FR-S01-15: Given a DUMMY option, When selected, Then coming-soon message.
- FR-S01-16: Given a menu, When PF3, Then the sign-on screen is shown.
- FR-S01-20: Given any of the 3 screens, When an unmapped key, Then invalid-key message.

## 9. Traceability matrix
FR-S01-01..09 → COSGN00C → cites §4 → tests TBD (assigned in migration plan) → verification cases TBD.
FR-S01-10..16,20 → COMEN01C (16,20 also COADM01C) → cites §4 → TBD.
FR-S01-17..19 → COADM01C → cites §4 → TBD.

## 10. Program index
| Program | Role | Requirements |
|---|---|---|
| COSGN00C | sign-on entry/validate | FR-S01-01..09, 20 |
| COMEN01C | main-menu dispatch | FR-S01-10..16, 20 |
| COADM01C | admin-menu dispatch | FR-S01-16..20 |

## 11. Open questions and assumptions
1. **Password handling**: source compares clear-text (`COSGN00C.cbl:223`); target will store hashed passwords — behavioral deviation (equivalent outcomes, different storage) to confirm at STOP C.
2. Admin-only flagging on the main menu exists in the table (`CDEMO-MENU-OPT-USRTYPE`) but all 11 current options are 'U'-accessible per `COMEN02Y.cpy`; FR-S01-12 is reachable only if the catalogue changes — kept because the guard is live code.
3. USRSEC seeding parity (S01-B5) assumed via data import in Phase 1.
4. Slot OPTN012 on COMEN1A is dead rendering capacity (route count 11) — documented, not a requirement.
