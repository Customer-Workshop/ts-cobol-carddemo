# COMEN01C — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COMEN01C — `app/cbl/COMEN01C.cbl`. Stream S-01, **wave 3**.
- Role: router/dispatcher — main menu display, option validation, authorization gate, dispatch. Shared shell (all non-admin online streams navigate through it), owner S-01.

## 2. Trigger / caller contract
- CICS transaction `CM00` (`app/csd/CARDDEMO.CSD:399`); normally XCTL'd from COSGN00C with `CARDDEMO-COMMAREA` populated (user id/type); `EIBCALEN = 0` bounces to COSGN00C (`COMEN01C.cbl:76-81`). Re-entered pseudo-conversationally (`:107-110`).
- Route catalogue: `app/cpy/COMEN02Y.cpy` — 11 entries (num, name, pgmname, usrtype), `CDEMO-MENU-OPT-COUNT = 11`.

## 3. Inputs and outputs
Inputs: OPTIONI X(2) of map COMEN1A/mapset COMEN01; AID key; incoming COMMAREA (`COCOM01Y.cpy:19-31`).
Outputs: OPTN001O–OPTN012O menu rows `"<nn>. <name>"` (`:262-303`); ERRMSGO; header fields; outgoing COMMAREA on dispatch (FROM-TRANID='CM00', FROM-PROGRAM='COMEN01C').

## 4. Functional requirements owned
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COMEN01C-01 | menu entered | 11 catalogue options listed in order | :262-303 | FR-S01-10 |
| COMEN01C-02 | option non-numeric / 0 / >11 | `Please enter a valid option number...` | :127-134 | FR-S01-11 |
| COMEN01C-03 | 'U' user picks 'A'-flagged option | `No access - Admin Only option... `, no transfer | :136-143 | FR-S01-12 |
| COMEN01C-04 | valid permitted option | transfer to owning function with user context | :177-187 | FR-S01-13 |
| COMEN01C-05 | option 11 target COPAUS0C not installed | `This option <name> is not installed...` (red) | :148-168 | FR-S01-14 |
| COMEN01C-06 | DUMMY placeholder target | `This option <name> is coming soon ...` (green) | :169-176 | FR-S01-15 |
| COMEN01C-07 | PF3 | return to sign-on screen | :96-98 | FR-S01-16 |
| COMEN01C-08 | unmapped AID | `Invalid key pressed...` | :99-102 | FR-S01-20 |

## 5. Business rules and validations
Option normalization: right-justify, blanks→'0' (`:117-124`, mechanics). Validation order (blocking): numeric/range → admin-only gate → availability (COPAUS0C / DUMMY) → dispatch. All blocking except COMEN01C-06 styled green (informational).

## 6. Data access and boundaries
No file/table access. Boundaries: S01-B1 (DECIDED feature-flagged route registry — migrated targets navigate, unmigrated show the not-installed idiom), S01-B2 (DECIDED flag for COPAUS0C, default off), S01-B3 (DECIDED router return routes), S01-B6 (session contract consumer).

## 7. Error and edge behavior
Missing COMMAREA → bounce to sign-on (`:76-81`); catalogue currently has no 'A'-flagged rows so COMEN01C-03 is reachable only via catalogue change (kept: live guard; test with fixture); OPTN012 slot dead (count=11).

## 8. Hard-stop boundary
Delegates all business function to route targets (other streams). Owns only listing, validation, authorization gate, availability messaging, and navigation.

## 9. Demoted mechanics
XCTL/CDEMO-TO-PROGRAM plumbing (`:196-203`); send/receive map plumbing; header formatting; option zero-fill.

## 10. Traceability
COMEN01C-01..08 ↔ FR-S01-10..16,20 ↔ parity tests (wave 3).
