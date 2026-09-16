# COADM01C — Program Functional Requirements (`!mf_program_fr_generation`)

## 1. Identity and role
- Program: COADM01C — `app/cbl/COADM01C.cbl`. Stream S-01, **wave 3**.
- Role: router/dispatcher — admin menu display, option validation, dispatch. Shared shell for admin streams (S-12, S-21), owner S-01.

## 2. Trigger / caller contract
- CICS transaction `CA00` (`app/csd/CARDDEMO.CSD:327`); XCTL'd from COSGN00C for user type 'A' with populated `CARDDEMO-COMMAREA`; `EIBCALEN = 0` bounces to COSGN00C (`COADM01C.cbl:80-85`); pseudo-conversational re-entry (`:111-114`).
- Route catalogue: `app/cpy/COADM02Y.cpy` — 6 entries, `CDEMO-ADMIN-OPT-COUNT = 6`.

## 3. Inputs and outputs
Inputs: OPTIONI X(2) of map COADM1A/mapset COADM01; AID; incoming COMMAREA.
Outputs: admin option rows `"<nn>. <name>"` (`:229-256`); ERRMSGO; header fields; outgoing COMMAREA on dispatch (FROM-TRANID='CA00', FROM-PROGRAM='COADM01C').

## 4. Functional requirements owned
| ID | Trigger | Observable result | Cite | Stream FR |
|---|---|---|---|---|
| COADM01C-01 | admin menu entered | 6 catalogue options listed in order | :229-256 | FR-S01-17 |
| COADM01C-02 | option non-numeric / 0 / >6 | `Please enter a valid option number...` | :131-138 | FR-S01-18 |
| COADM01C-03 | valid option | transfer to owning admin function with user context | :140-149 | FR-S01-19 |
| COADM01C-04 | PF3 | return to sign-on screen | :100-102 | FR-S01-16 |
| COADM01C-05 | unmapped AID | `Invalid key pressed...` | :103-106 | FR-S01-20 |

## 5. Business rules and validations
Blocking: numeric/range check → DUMMY guard (`:141`, coming-soon idiom shared with main menu) → dispatch. No admin-type gate (only admins reach CA00 via COSGN00C routing).

## 6. Data access and boundaries
No file/table access. Boundaries: S01-B1 (route registry — admin targets COUSR00C..03C/COTRTLIC/COTRTUPC flagged unmigrated), S01-B3 (return routes), S01-B6 (session consumer). PGMIDERR HANDLE CONDITION (`:77-79`) → registry treats unknown target as not-installed.

## 7. Error and edge behavior
Missing COMMAREA → bounce to sign-on (`:80-85`); DUMMY targets show coming-soon; XCTL failure trapped by PGMIDERR (technical, mapped to not-installed idiom).

## 8. Hard-stop boundary
Delegates all admin business function (user admin, tran-type maintenance) to route targets; owns listing, validation, and navigation only.

## 9. Demoted mechanics
XCTL/CDEMO-TO-PROGRAM plumbing (`:163-170`); map send/receive; header formatting; option zero-fill (`:121-128`).

## 10. Traceability
COADM01C-01..05 ↔ FR-S01-16..20 ↔ parity tests (wave 3).
